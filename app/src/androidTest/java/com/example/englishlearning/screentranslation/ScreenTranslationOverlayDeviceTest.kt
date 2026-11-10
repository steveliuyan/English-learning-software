package com.example.englishlearning.screentranslation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.englishlearning.ai.AiPayloadKind
import com.example.englishlearning.ai.AiProfileSecretUseCase
import com.example.englishlearning.ai.DefaultTextProfileResolver
import com.example.englishlearning.ai.DefaultTextProfileResult
import com.example.englishlearning.ai.domain.AiCapability
import com.example.englishlearning.ai.domain.AiProfile
import com.example.englishlearning.ai.net.AiHttpRequest
import com.example.englishlearning.ai.net.AiHttpResponse
import com.example.englishlearning.ai.net.AiHttpResult
import com.example.englishlearning.ai.net.AiHttpTransport
import com.example.englishlearning.core.security.SecretReference
import com.example.englishlearning.core.security.SecretStore
import com.example.englishlearning.language.domain.OcrRegion
import com.example.englishlearning.phototranslation.BlockTranslationResult
import com.example.englishlearning.phototranslation.BlockTranslationUseCase
import com.example.englishlearning.phototranslation.OverlayBackgroundSampler
import com.example.englishlearning.phototranslation.OverlayBlock
import com.example.englishlearning.phototranslation.OverlayBlockPlanner
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * F3-05 的「覆盖层真的把译文画到块上」「首次发送前真的先停下来要确认」两条的**真机证据**。
 *
 * 为什么需要这一层：这两条此前只有 JVM 单测（排版求解、确认判定各自钉住）与「没有 AI Profile 时
 * 面板显示 NotConfigured」的走查记录——**链路本身**（真实 [BlockTranslationUseCase] → 真实
 * [OverlayBlockPlanner] → 真实 [ScreenTranslationOverlayView] 在真机 density 下排版并落像素）从来没有
 * 被一次性跑通过。而这条链路正是 F3-05 的全部价值所在，坏掉时用户看到的只是「开着会话、什么都没变」。
 *
 * 做法：**不动生产代码、不加 debug 注入点**，只把两个外部副作用换成测试假件——
 * [AiHttpTransport]（不出网）与 [SecretStore]（不碰 Keystore）。其余每一环都是产品里的那一份实现，
 * 因此在真机上跑出来的结论与产品行为同源，区别仅在于译文来自我们构造的响应体。
 *
 * 这一层不验的：真实模型的译文质量、真实 Keystore 的可用性、MediaProjection 授权弹窗本身
 * （后者只在人工走查里验，系统弹窗不可自动化）。
 */
@RunWith(AndroidJUnit4::class)
class ScreenTranslationOverlayDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context get() = instrumentation.targetContext

    private val captureWidth = 1080
    private val captureHeight = 2340

    /** 三块「英文原文」，几何取自一次 1080x2340 的截屏（白底黑字的典型网页）。 */
    private val regions =
        listOf(
            region(left = 80, top = 200, right = 1000, bottom = 320),
            region(left = 80, top = 400, right = 1000, bottom = 520),
            region(left = 80, top = 700, right = 1000, bottom = 820),
        )

    private val originalTexts =
        listOf(
            "The first sentence on this screen.",
            "The second sentence on this screen.",
            "The third sentence on this screen.",
        )

    private val translations = listOf("第一块译文", "第二块译文", "第三块译文")

    /**
     * 覆盖层的核心承诺：译文落在**原文所在的矩形**里，且矩形之外一个像素都不许改。
     *
     * 三处断言各有各的敌人：块数对不上 = 排版把块丢了；矩形内没有深色像素 = 底板画了字没画；
     * 矩形外出现像素 = 覆盖层盖住了不该盖的地方（这比错位更难被用户看懂）。
     */
    @Test
    fun aTranslatedScreenIsPaintedInsideTheBlockRectanglesAndNowhereElse() {
        val transport = RecordingTransport(blocksResponse(translations))
        val blocks = translateToBlocks(transport, confirmedHosts = setOf(HOST))

        assertEquals(3, blocks.size)

        val frame = whiteFrame()
        val mapper = OverlayCoordinateMapper(captureWidth, captureHeight, captureWidth.toFloat(), captureHeight.toFloat())
        val overlay =
            onMain {
                val view = ScreenTranslationOverlayView(context)
                view.measure(
                    View.MeasureSpec.makeMeasureSpec(captureWidth, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(captureHeight, View.MeasureSpec.EXACTLY),
                )
                view.layout(0, 0, captureWidth, captureHeight)
                view.submit(frame, blocks, mapper)
                view
            }

        assertEquals("三块都该画出来", 3, overlay.lastDrawnBlocks)
        assertEquals("不该有块被跳过", 0, overlay.lastSkippedBlocks)

        val painted =
            onMain {
                val target = Bitmap.createBitmap(captureWidth, captureHeight, Bitmap.Config.ARGB_8888)
                target.eraseColor(Color.WHITE)
                overlay.draw(Canvas(target))
                target
            }

        val mapped = mapper.mapBlocks(blocks)
        mapped.forEachIndexed { index, block ->
            val dark = darkPixelsIn(painted, block.left, block.top, block.right, block.bottom)
            assertTrue("第 ${index + 1} 块矩形里应该出现译文像素，实际深色像素 $dark", dark > 20)
        }

        // 块与块之间的空白带必须原样保留：覆盖层只该动块矩形内部。
        assertEquals(0, darkPixelsIn(painted, 0f, 1100f, captureWidth.toFloat(), 1200f))
        assertEquals(0, darkPixelsIn(painted, 0f, 1400f, captureWidth.toFloat(), captureHeight.toFloat()))

        // 原帧本身是白底：深色像素只可能来自覆盖层画的字。
        assertEquals(Color.WHITE, frame.getPixel(captureWidth / 2, 1500))
    }

    /**
     * 出站确认的前置条件：**没有确认过这个域名时，一个字节都不许发出去**。
     *
     * 这是「首次调用第三方 Endpoint 前展示域名」在服务链路里的落点——服务拿到 NeedsConfirmation
     * 就把域名丢进快照、停会话、等界面弹框；假传输的请求计数是 0 才说明这条纪律真的生效，
     * 而不是「先发了再补一个确认框」。
     */
    @Test
    fun anUnconfirmedHostStopsBeforeAnyBytesLeaveTheDevice() {
        val transport = RecordingTransport(blocksResponse(translations))
        val useCase = useCase(transport)

        val first = runBlocking { useCase.translate(originalTexts, confirmedTextHosts = emptySet()) }

        assertTrue("第一次必须停在确认上，实际 $first", first is BlockTranslationResult.NeedsConfirmation)
        first as BlockTranslationResult.NeedsConfirmation
        assertEquals(HOST, first.host)
        assertEquals(AiPayloadKind.Text, first.payloadKind)
        assertEquals("确认之前不许有任何出站请求", 0, transport.requests.size)

        val second = runBlocking { useCase.translate(originalTexts, confirmedTextHosts = setOf(HOST)) }

        assertTrue("确认之后必须真的翻译，实际 $second", second is BlockTranslationResult.Translated)
        assertEquals(1, transport.requests.size)

        // 上行只有文字：请求体里是这一屏识别出的三句话，没有图片字节。
        val request = transport.requests.single()
        originalTexts.forEach { text -> assertTrue("请求体该带上原文「$text」", request.body.contains(text)) }
        assertTrue("请求体不该出现图片字节", !request.body.contains("base64"))
        assertEquals("https://$HOST/v1/chat/completions", request.url)
    }

    /**
     * 真机可挂载性：按服务同样的参数把覆盖层加到 TYPE_APPLICATION_OVERLAY 上再加一次、拆一次。
     *
     * 只验「这台设备、这份权限状态下 addView 不抛」，不验窗口内容（那是上一条的事）。
     * 权限没开时这条 Assume 掉：它验的是权限**已开**时的挂载路径，与权限申请本身无关。
     */
    @Test
    fun theOverlayCanBeAttachedOnTopOfOtherAppsWhenThePermissionIsGranted() {
        assumeTrue(
            "需要「显示在其他应用上层」权限（adb shell appops set <pkg> SYSTEM_ALERT_WINDOW allow）",
            Settings.canDrawOverlays(context),
        )

        // addView 只是把 ViewRootImpl 挂上；isAttachedToWindow 要等主线程下一帧遍历才为真，
        // 所以用 attach 回调 + 闩锁等一次帧，而不是 addView 之后立刻读那个布尔值（会一直读到 false）。
        val attachedLatch = CountDownLatch(1)
        var windowManager: WindowManager? = null
        var overlay: ScreenTranslationOverlayView? = null
        onMain {
            val manager = context.getSystemService(WindowManager::class.java)
            val view = ScreenTranslationOverlayView(context)
            view.addOnAttachStateChangeListener(
                object : View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: View) {
                        attachedLatch.countDown()
                    }

                    override fun onViewDetachedFromWindow(v: View) = Unit
                },
            )
            val params =
                WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT,
                )
            manager.addView(view, params)
            windowManager = manager
            overlay = view
        }

        val attached =
            try {
                attachedLatch.await(3, TimeUnit.SECONDS)
            } finally {
                onMain { runCatching { overlay?.let { view -> windowManager?.removeView(view) } } }
            }

        assertTrue("悬浮覆盖层应该能真正挂到别的应用上面", attached)
    }

    // ---- 夹具与工具 ----

    private fun useCase(transport: AiHttpTransport): BlockTranslationUseCase =
        BlockTranslationUseCase(
            defaultTextProfile = DefaultTextProfileResolver { DefaultTextProfileResult.Selected(profile()) },
            secrets = AiProfileSecretUseCase(InMemorySecretStore()),
            transport = transport,
        )

    /** 走一遍真实链路：批量翻译 → 块清单 → 取舍 → 覆盖输入。 */
    private fun translateToBlocks(
        transport: AiHttpTransport,
        confirmedHosts: Set<String>,
    ): List<OverlayBlock> {
        val result = runBlocking { useCase(transport).translate(originalTexts, confirmedHosts) }
        val translated = result as BlockTranslationResult.Translated
        assertTrue("三块都该有译文，缺失 ${translated.missing}", translated.missing.isEmpty())

        val sources = OverlayBlockPlanner.sources(regions)
        val included = OverlayBlockPlanner.defaultIncludedIds(sources)
        assertEquals("白底三块都是高置信，默认全部覆盖", 3, included.size)
        return OverlayBlockPlanner.plan(sources, included, translated.translations)
    }

    private fun region(
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ): OcrRegion = OcrRegion(left = left, top = top, right = right, bottom = bottom, confidence = 0.92f, text = "text")

    private fun whiteFrame(): Bitmap =
        Bitmap.createBitmap(captureWidth, captureHeight, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }

    private fun profile(): AiProfile =
        AiProfile(
            profileId = PROFILE_ID,
            displayName = "走查用假服务",
            websiteUrl = "https://$HOST",
            endpoint = "https://$HOST/v1",
            model = "gpt-4o-mini",
            capabilities = setOf(AiCapability.Text),
            secretReference = SecretReference("ai-profile-$PROFILE_ID"),
        )

    /** 一次「合格」的批量翻译响应体：信封 + JSON 数组，形状与真实服务一致。 */
    private fun blocksResponse(translations: List<String>): String {
        val items = JSONArray()
        translations.forEachIndexed { index, translation ->
            items.put(JSONObject().put("id", index + 1).put("translation", translation))
        }
        return JSONObject()
            .put(
                "choices",
                JSONArray().put(
                    JSONObject().put("message", JSONObject().put("content", items.toString())),
                ),
            ).toString()
    }

    /** 截屏坐标系里的块矩形：深色像素数（按 [OverlayBackgroundSampler] 的同一把亮度尺）。 */
    private fun darkPixelsIn(
        bitmap: Bitmap,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
    ): Int {
        val l = left.toInt().coerceAtLeast(0)
        val t = top.toInt().coerceAtLeast(0)
        val r = right.toInt().coerceAtMost(bitmap.width - 1)
        val b = bottom.toInt().coerceAtMost(bitmap.height - 1)
        var count = 0
        var y = t
        while (y <= b) {
            var x = l
            while (x <= r) {
                if (OverlayBackgroundSampler.luminance(bitmap.getPixel(x, y)) < 0.3f) count++
                x++
            }
            y++
        }
        return count
    }

    private fun <T> onMain(block: () -> T): T {
        var result: T? = null
        instrumentation.runOnMainSync { result = block() }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    /** 假传输：不出网，把发出去的请求原样记下来。 */
    private class RecordingTransport(
        private val body: String,
    ) : AiHttpTransport {
        val requests = mutableListOf<AiHttpRequest>()

        override suspend fun send(request: AiHttpRequest): AiHttpResult {
            requests += request
            return AiHttpResult.Responded(AiHttpResponse(statusCode = 200, body = body))
        }
    }

    /** 假密钥库：不碰 Keystore，只回答「这个 Profile 有一个 Key」。 */
    private class InMemorySecretStore : SecretStore {
        private val secrets = mutableMapOf<String, CharArray>()

        init {
            secrets[SecretReference("ai-profile-$PROFILE_ID").alias] = "sk-device-walkthrough".toCharArray()
        }

        override fun save(
            reference: SecretReference,
            secret: CharArray,
        ): Result<Unit> {
            secrets[reference.alias] = secret.copyOf()
            return Result.success(Unit)
        }

        override fun read(reference: SecretReference): Result<CharArray> =
            secrets[reference.alias]?.copyOf()?.let { Result.success(it) }
                ?: Result.failure(IllegalStateException("missing secret"))

        override fun delete(reference: SecretReference): Result<Unit> {
            secrets.remove(reference.alias)
            return Result.success(Unit)
        }

        override fun has(reference: SecretReference): Result<Boolean> = Result.success(reference.alias in secrets)
    }

    private companion object {
        const val HOST = "api.openai.com"
        const val PROFILE_ID = "profile-device-walkthrough"
    }
}
