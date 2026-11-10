package com.example.englishlearning.screentranslation

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.englishlearning.R
import com.example.englishlearning.ai.DefaultTextProfileResolver
import com.example.englishlearning.ai.DefaultTextProfileResult
import com.example.englishlearning.language.domain.OcrChainOutcome
import com.example.englishlearning.language.domain.OcrImage
import com.example.englishlearning.language.domain.OcrProviderChain
import com.example.englishlearning.phototranslation.BlockTranslationPromptPolicy
import com.example.englishlearning.phototranslation.BlockTranslationResult
import com.example.englishlearning.phototranslation.BlockTranslationUseCase
import com.example.englishlearning.phototranslation.OverlayBlockPlanner
import dagger.hilt.android.AndroidEntryPoint
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 悬浮窗屏幕翻译的前台服务（F3-05）。
 *
 * 生命周期三条铁律：
 * 1. 只活一次会话：MediaProjection 授权令牌在 Android 14+ 只在本次会话有效，用户按停止或系统回收就整个结束；
 * 2. 截屏帧只在内存里过一遍（取指纹 → OCR → 丢弃），不写临时文件、不上传；
 * 3. 任何结束路径（用户停止 / 通知栏停止 / 系统 onStop 回调 / 出错）都走 [finish]，一次释放干净。
 */
@AndroidEntryPoint
class ScreenTranslationService : Service() {
    @Inject lateinit var ocrChain: OcrProviderChain

    @Inject lateinit var blockTranslation: BlockTranslationUseCase

    @Inject lateinit var controller: ScreenTranslationController

    @Inject lateinit var profileResolver: DefaultTextProfileResolver

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val gate = ScreenCaptureGate()

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var overlayView: ScreenTranslationOverlayView? = null
    private var controlBar: ScreenTranslationControlBar? = null
    private var captureJob: Job? = null
    private var frameWidth = 0
    private var frameHeight = 0
    private var mode = ScreenTranslationMode.Continuous
    private var finishing = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        if (intent?.action == ACTION_STOP) {
            finish(null)
            return START_NOT_STICKY
        }
        startInForeground()

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        @Suppress("DEPRECATION")
        val resultData = intent?.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
        mode =
            if (intent?.getStringExtra(EXTRA_MODE) == MODE_ONCE) {
                ScreenTranslationMode.Once
            } else {
                ScreenTranslationMode.Continuous
            }

        if (!Settings.canDrawOverlays(this)) {
            reportAndFinish(ScreenTranslationNotice.OverlayPermissionMissing)
            return START_NOT_STICKY
        }
        if (resultCode != Activity.RESULT_OK || resultData == null) {
            reportAndFinish(ScreenTranslationNotice.ProjectionDenied)
            return START_NOT_STICKY
        }
        if (projection != null) return START_NOT_STICKY

        startSession(resultCode, resultData)
        return START_NOT_STICKY
    }

    // ---- 会话 ----

    private fun startSession(
        resultCode: Int,
        resultData: Intent,
    ) {
        val manager = getSystemService(MediaProjectionManager::class.java)
        val mediaProjection = manager?.getMediaProjection(resultCode, resultData)
        if (mediaProjection == null) {
            reportAndFinish(ScreenTranslationNotice.ProjectionDenied)
            return
        }
        projection = mediaProjection
        mediaProjection.registerCallback(
            object : MediaProjection.Callback() {
                override fun onStop() {
                    // 系统或用户在系统 UI 里停掉了投屏：自己收摊，不留悬浮残影（AC3-13）。
                    scope.launch {
                        finish(ScreenTranslationNotice.ProjectionDenied)
                        stopSelf()
                    }
                }
            },
            Handler(Looper.getMainLooper()),
        )

        val size = screenSize()
        frameWidth = size.first
        frameHeight = size.second
        imageReader = ImageReader.newInstance(frameWidth, frameHeight, android.graphics.PixelFormat.RGBA_8888, 2)
        virtualDisplay =
            mediaProjection.createVirtualDisplay(
                "screen-translation",
                frameWidth,
                frameHeight,
                resources.displayMetrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface,
                null,
                null,
            )
        addOverlays()
        gate.reset()

        scope.launch {
            val modelName =
                when (val selected = profileResolver.select()) {
                    is DefaultTextProfileResult.Selected -> selected.profile.model
                    else -> null
                }
            controller.beginSession(mode, modelName)
            refreshControlBar()
            if (mode == ScreenTranslationMode.Continuous) {
                captureJob = scope.launch { pollLoop() }
            } else {
                // 单次模式：会话一开就自动截一帧翻译（用户语义就是「翻译当前屏」），
                // 覆盖层与悬浮条留到用户按「停止」——不自动结束，否则刚画上的译文立刻消失。
                // 走 Manual 触发：这是用户点出来的动作，不该被自动轮询的节流/单次闸口挡掉。
                translateOnce()
            }
        }
    }

    private suspend fun pollLoop() {
        while (currentCoroutineContext().isActive) {
            delay(POLL_INTERVAL_MILLIS)
            translateCurrentFrame(CaptureTrigger.Automatic)
        }
    }

    /** 手动「单次」：用户明确点的，不受节流约束。 */
    private fun translateOnce() {
        scope.launch { translateCurrentFrame(CaptureTrigger.Manual) }
    }

    private suspend fun translateCurrentFrame(trigger: CaptureTrigger) {
        if (finishing) return
        val bitmap = captureFrame() ?: return
        val now = SystemClock.elapsedRealtime()
        val fingerprint = ScreenFrameSampler.fingerprintOf(bitmap)
        val decision = gate.decide(mode, fingerprint, now, trigger)
        if (!decision.send) {
            bitmap.recycle()
            return
        }
        gate.markSent(fingerprint, now)

        val bytes = encodeJpeg(bitmap) ?: run {
            bitmap.recycle()
            return
        }
        val outcome = withContext(Dispatchers.Default) { ocrChain.recognize(OcrImage(bytes)) }
        when (outcome) {
            OcrChainOutcome.OcrUnavailable -> {
                bitmap.recycle()
                failSession(ScreenTranslationNotice.OcrUnavailable)
                return
            }
            is OcrChainOutcome.RecognitionFailed -> {
                bitmap.recycle()
                // 这一屏没读出字（多半是中文界面/空白页）：**不是**会话级错误，
                // 连续模式下用户切到要翻的页面下一轮就自己好了，所以只报一句、不停会话。
                reportNotice(ScreenTranslationNotice.OcrFailed)
                return
            }
            is OcrChainOutcome.Recognized -> handleRecognized(bitmap, outcome)
        }
    }

    private suspend fun handleRecognized(
        bitmap: Bitmap,
        outcome: OcrChainOutcome.Recognized,
    ) {
        val sources = OverlayBlockPlanner.sources(outcome.result.regions)
        val included = OverlayBlockPlanner.defaultIncludedIds(sources)
        if (included.isEmpty()) {
            bitmap.recycle()
            reportNotice(ScreenTranslationNotice.OcrFailed)
            return
        }
        if (sources.size > BlockTranslationPromptPolicy.maxBlocks) {
            bitmap.recycle()
            // 一屏块太多：不发请求（把 token 成本压在可预期范围内），让用户换屏或缩小窗口。
            reportNotice(ScreenTranslationNotice.TooManyBlocks)
            return
        }

        val includedSources = sources.filter { it.id in included }
        val result =
            blockTranslation.translate(
                blockTexts = includedSources.map { it.text },
                confirmedTextHosts = controller.confirmedTextHosts.value,
            )
        when (result) {
            is BlockTranslationResult.NeedsConfirmation -> {
                bitmap.recycle()
                controller.dispatch(ScreenTranslationEvent.ConfirmationRequired(result.host))
                finish(ScreenTranslationNotice.NeedsOutboundConfirmation)
            }
            is BlockTranslationResult.NotConfigured -> {
                bitmap.recycle()
                failSession(ScreenTranslationNotice.NotConfigured)
            }
            is BlockTranslationResult.Failed -> {
                bitmap.recycle()
                controller.dispatch(
                    ScreenTranslationEvent.Failure(ScreenTranslationNotice.TranslationFailed, countsAsRequest = true),
                )
                finish(null)
            }
            is BlockTranslationResult.Translated -> {
                drawOverlay(bitmap, sources, included, includedSources, result)
            }
        }
    }

    private fun drawOverlay(
        bitmap: Bitmap,
        sources: List<com.example.englishlearning.phototranslation.OverlayBlockSource>,
        included: Set<Int>,
        includedSources: List<com.example.englishlearning.phototranslation.OverlayBlockSource>,
        result: BlockTranslationResult.Translated,
    ) {
        val translationsBySourceId = linkedMapOf<Int, String>()
        includedSources.forEachIndexed { index, source ->
            val translation = result.translations[index + 1]
            if (!translation.isNullOrBlank()) translationsBySourceId[source.id] = translation
        }
        val blocks = OverlayBlockPlanner.plan(sources, included, translationsBySourceId)
        val view = overlayView
        if (view == null) {
            bitmap.recycle()
            failSession(ScreenTranslationNotice.OverlayPermissionMissing)
            return
        }
        val mapper =
            OverlayCoordinateMapper(
                captureWidth = bitmap.width,
                captureHeight = bitmap.height,
                viewWidth = view.width.toFloat().coerceAtLeast(1f),
                viewHeight = view.height.toFloat().coerceAtLeast(1f),
            )
        view.submit(bitmap, blocks, mapper)
        val drawn = view.lastDrawnBlocks
        val skipped = view.lastSkippedBlocks
        controller.dispatch(ScreenTranslationEvent.FrameTranslated(drawn, skipped))
        refreshControlBar()
        // 覆盖层里已经按需取样过背景色，这一帧位图可以立刻丢掉了。
        bitmap.recycle()
    }

    // ---- 截屏 ----

    private suspend fun captureFrame(): Bitmap? {
        val reader = imageReader ?: return null
        repeat(ACQUIRE_ATTEMPTS) {
            val image = reader.acquireLatestImage()
            if (image != null) {
                val bitmap = image.toBitmap(frameWidth, frameHeight)
                image.close()
                if (bitmap != null) return bitmap
            }
            delay(ACQUIRE_RETRY_MILLIS)
        }
        return null
    }

    private fun Image.toBitmap(
        width: Int,
        height: Int,
    ): Bitmap? =
        runCatching {
            val plane = planes[0]
            val buffer = plane.buffer
            buffer.rewind()
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * width
            val padded = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888)
            padded.copyPixelsFromBuffer(buffer)
            if (rowPadding == 0) {
                padded
            } else {
                Bitmap.createBitmap(padded, 0, 0, width, height).also { padded.recycle() }
            }
        }.getOrNull()

    private suspend fun encodeJpeg(bitmap: Bitmap): ByteArray? =
        withContext(Dispatchers.Default) {
            runCatching {
                ByteArrayOutputStream(JPEG_BUFFER).use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
                    stream.toByteArray()
                }
            }.getOrNull()
        }

    private fun screenSize(): Pair<Int, Int> {
        val windowManager = getSystemService(WindowManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && windowManager != null) {
            val bounds = windowManager.currentWindowMetrics.bounds
            return bounds.width() to bounds.height()
        }
        @Suppress("DEPRECATION")
        val metrics = android.util.DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager?.defaultDisplay?.getRealMetrics(metrics)
        return (metrics.widthPixels.takeIf { it > 0 } ?: DEFAULT_FRAME_SIZE) to
            (metrics.heightPixels.takeIf { it > 0 } ?: DEFAULT_FRAME_SIZE)
    }

    // ---- 悬浮控件 ----

    private fun addOverlays() {
        val windowManager = getSystemService(WindowManager::class.java) ?: return
        val overlay =
            ScreenTranslationOverlayView(this).apply {
                isClickable = false
                isFocusable = false
            }
        val overlayParams =
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                android.graphics.PixelFormat.TRANSLUCENT,
            )
        runCatching { windowManager.addView(overlay, overlayParams) }
            .onSuccess { overlayView = overlay }

        val bar =
            ScreenTranslationControlBar(
                context = this,
                onTranslateOnce = { translateOnce() },
                onStop = { finish(null) },
            )
        val barParams =
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                android.graphics.PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = BAR_MARGIN_PX
                y = BAR_MARGIN_PX * 3
            }
        bar.setOnTouchListener(bar.attachDrag(windowManager, barParams))
        runCatching { windowManager.addView(bar, barParams) }
            .onSuccess {
                controlBar = bar
                refreshControlBar()
            }
    }

    private fun refreshControlBar() {
        val snapshot = controller.state.value
        controlBar?.updateStatus(snapshot.sentRequests, snapshot.modelName)
    }

    // ---- 结束与清理 ----

    private fun failSession(notice: ScreenTranslationNotice) {
        controller.dispatch(ScreenTranslationEvent.Failure(notice))
        finish(notice)
    }

    /**
     * 「这一帧没翻了」——只把话放到界面上，**会话继续**：
     * 屏幕翻译的常态就是用户先在我们自己的中文界面上点开始、再切到要翻的页面，
     * 此时这一屏本来就没有英文；把它当会话级错误会把功能直接判死（真机走查暴露）。
     * 连续模式下一轮轮询就会重试，单次模式下用户还能按悬浮条上的「单次」再来一次。
     */
    private fun reportNotice(notice: ScreenTranslationNotice) {
        controller.dispatch(ScreenTranslationEvent.Failure(notice))
    }

    private fun reportAndFinish(notice: ScreenTranslationNotice) {
        controller.dispatch(ScreenTranslationEvent.Failure(notice))
        finish(notice)
    }

    /**
     * 唯一出口：释放 VirtualDisplay / ImageReader / MediaProjection、移除两枚悬浮控件、停前台与自身。
     * 幂等——重复调用只做第一次。
     */
    private fun finish(notice: ScreenTranslationNotice?) {
        if (finishing) return
        finishing = true
        captureJob?.cancel()
        captureJob = null
        val windowManager = getSystemService(WindowManager::class.java)
        overlayView?.let { view ->
            view.clear()
            runCatching { windowManager?.removeView(view) }
        }
        overlayView = null
        controlBar?.let { bar -> runCatching { windowManager?.removeView(bar) } }
        controlBar = null
        runCatching { virtualDisplay?.release() }
        virtualDisplay = null
        runCatching { imageReader?.close() }
        imageReader = null
        runCatching { projection?.stop() }
        projection = null
        controller.endSession(notice)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        finish(null)
        scope.cancel()
        super.onDestroy()
    }

    private fun startInForeground() {
        val open =
            packageManager.getLaunchIntentForPackage(packageName)?.let { intent ->
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            }
        val stop =
            PendingIntent.getService(
                this,
                1,
                Intent(this, ScreenTranslationService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE,
            )
        val notification =
            NotificationCompat
                .Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("屏幕翻译进行中")
                .setContentText("只在本次会话内截屏，画面不出本机；随时可停止。")
                .setOngoing(true)
                .setContentIntent(open)
                .addAction(0, "停止", stop)
                .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "屏幕翻译", NotificationManager.IMPORTANCE_LOW).apply {
                description = "悬浮窗屏幕翻译的会话状态与停止入口"
            },
        )
    }

    companion object {
        const val ACTION_START = "com.example.englishlearning.screentranslation.START"
        const val ACTION_STOP = "com.example.englishlearning.screentranslation.STOP"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        const val EXTRA_MODE = "mode"
        const val MODE_ONCE = "once"
        const val MODE_CONTINUOUS = "continuous"

        private const val CHANNEL_ID = "screen_translation"
        private const val NOTIFICATION_ID = 2401
        private const val POLL_INTERVAL_MILLIS = 1_000L
        private const val ACQUIRE_ATTEMPTS = 5
        private const val ACQUIRE_RETRY_MILLIS = 60L
        private const val JPEG_QUALITY = 80
        private const val JPEG_BUFFER = 512 * 1024
        private const val BAR_MARGIN_PX = 24
        private const val DEFAULT_FRAME_SIZE = 1080

        fun startIntent(
            context: Context,
            resultCode: Int,
            resultData: Intent,
            once: Boolean,
        ): Intent =
            Intent(context, ScreenTranslationService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
                .putExtra(EXTRA_RESULT_DATA, resultData)
                .putExtra(EXTRA_MODE, if (once) MODE_ONCE else MODE_CONTINUOUS)

        fun stopIntent(context: Context): Intent =
            Intent(context, ScreenTranslationService::class.java).setAction(ACTION_STOP)
    }
}
