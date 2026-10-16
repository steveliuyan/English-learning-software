package com.example.englishlearning.language.infrastructure

import android.content.Context
import android.media.MediaDataSource
import android.media.MediaPlayer
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * 基于 [MediaPlayer] 的一次性播放器，并暴露当前会话的暂停/继续/停止（决策 §2 四控）。
 *
 * 线程约定：[active]/[pending]/[preparing] 都是普通可变字段，**假设主线程串行访问**——
 * `MediaPlayer` 的回调投递到创建它的线程所绑定的 Looper，本类的实例由主线程创建并调用，
 * 因此这里不加锁，也不引入任何并发原语；若将来改到别的调度器上，必须先重做这一约定。
 *
 * 会话句柄约定：[active] 只在 `onPrepared` 之后挂载，prepared 之前的播放器记在 [preparing]，
 * 这样「点停止时声音还没起来」也一定有一个字段能被放掉，不会留下迟到开口的播放器。
 *
 * 单会话与抢占约定：本类只跟踪**一个**当前会话（决策 §2 的四控针对「正在播的那一段」）。
 * 再次 [play] 会覆盖三个句柄字段，所以安装新句柄前先用 [retireCurrentSession] 收口旧会话：
 * 旧续程以 [AudioPlaybackResult.Played] 立即返回、旧播放器立即 release，
 * 不会出现「暂停中点了下一个词 → 上一次的 speak 永远挂起、上一个 MediaPlayer 泄漏」。
 */
class AndroidAudioPlayer(private val context: Context) : AudioPlayer {
    private var active: MediaPlayer? = null
    private var pending: CancellableContinuation<AudioPlaybackResult>? = null
    private var preparing: MediaPlayer? = null

    override suspend fun play(bytes: ByteArray, format: String): AudioPlaybackResult =
        suspendCancellableCoroutine { continuation ->
            // 抢占收口：下面的赋值会无条件覆盖 active/preparing/pending。若上一段仍在暂停且从未结束，
            // 旧续程就不再被任何字段引用（stop 也找不到它），旧 speak 会永久悬挂、旧 MediaPlayer 永不 release。
            // 因此新会话安装前先按与「用户停止」同一口径把旧会话收口（详见类 KDoc 会话句柄约定）。
            retireCurrentSession()
            val player = MediaPlayer()
            preparing = player
            pending = continuation
            fun finish(result: AudioPlaybackResult) {
                if (active === player) active = null
                if (preparing === player) preparing = null
                if (pending === continuation) pending = null
                if (continuation.isActive) continuation.resume(result)
                player.release()
            }
            try {
                player.setDataSource(ByteArrayMediaDataSource(bytes))
                player.setOnPreparedListener { mp -> preparing = null; active = mp; mp.start() }
                player.setOnCompletionListener { finish(AudioPlaybackResult.Played) }
                player.setOnErrorListener { _, _, _ -> finish(AudioPlaybackResult.Failed); true }
                player.prepareAsync()
                continuation.invokeOnCancellation {
                    if (active === player) active = null
                    if (preparing === player) preparing = null
                    if (pending === continuation) pending = null
                    player.release()
                }
            } catch (_: Exception) {
                finish(AudioPlaybackResult.Failed)
            }
        }

    override fun pauseCurrentPlayback(): Boolean {
        val player = active ?: return false
        return runCatching { if (player.isPlaying) { player.pause(); true } else false }.getOrDefault(false)
    }

    override fun resumeCurrentPlayback(): Boolean {
        val player = active ?: return false
        return runCatching { if (!player.isPlaying) { player.start(); true } else false }.getOrDefault(false)
    }

    /**
     * 用户主动停止=一次正常结束：以 Played 收口挂起续程，防止 speak 悬挂。
     *
     * 已知代价（决策 §2，交 Task 9/QA 定文案）：`Played` 会让 UI 播报「发音已播放。」，
     * 而不是「已停止」——「已停止」属 Spec 可见文案变化，需要先改决策文档；
     * 本任务优先保证 `speak` 不悬挂（悬挂会把学习流卡在 Playing 态）。
     * UI 侧靠既有代次计数器（`*PronunciationRequest`）+ 停止时先自增，防止迟到的 Played 覆盖 Idle 呈现。
     */
    override fun stopCurrentPlayback() = retireCurrentSession()

    /**
     * 收口当前会话并以 [AudioPlaybackResult.Played] 解决挂起的续程，随后清空三个句柄字段。
     *
     * 供两处共用：用户点「停止」，以及新的 [play] 抢占尚未结束的旧会话——
     * 两者对使用者的语义一致（这一次发声到此为止），因此不引入第二种收口结果。
     * 句柄为 null 时（无会话）是纯空操作，可重复调用。
     */
    private fun retireCurrentSession() {
        pending?.let { continuation ->
            if (continuation.isActive) continuation.resume(AudioPlaybackResult.Played)
        }
        pending = null
        // active 与 preparing 通常指向同一个对象，去重后各放一次，避免重复 release。
        setOfNotNull(active, preparing).forEach { player -> runCatching { player.release() } }
        active = null
        preparing = null
    }

    private class ByteArrayMediaDataSource(private val bytes: ByteArray) : MediaDataSource() {
        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
            if (position >= bytes.size) return -1
            val count = minOf(size, bytes.size - position.toInt())
            bytes.copyInto(buffer, offset, position.toInt(), position.toInt() + count)
            return count
        }

        override fun getSize(): Long = bytes.size.toLong()
        override fun close() = Unit
    }
}
