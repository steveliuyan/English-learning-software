package com.example.englishlearning.language.infrastructure

import kotlin.test.Test
import kotlin.test.assertFalse

/**
 * `AudioPlayer` 三个播控成员的**裸对象**取证（决策 §2：默认实现「不支持」，未支持引擎零破坏）。
 *
 * 证伪对象有两个，缺一不可：
 * 1. 编译期——若 `pauseCurrentPlayback/resumeCurrentPlayback/stopCurrentPlayback` 不是接口默认成员，
 *    这个只实现 `play(...)` 的匿名对象就无法编译，等于强制所有既有 fake 与第三方播放器改代码；
 * 2. 运行期——三者的默认值必须是 false / 空操作，且 stop 不得抛（F3-02 的「未播放时安全无副作用」）。
 */
class AudioPlayerDefaultsTest {

    @Test
    fun `audio player implementing only play gets unsupported controls`() {
        val bare = object : AudioPlayer {
            override suspend fun play(bytes: ByteArray, format: String, rate: Float): AudioPlaybackResult = AudioPlaybackResult.Played
        }

        assertFalse(bare.pauseCurrentPlayback())
        assertFalse(bare.resumeCurrentPlayback())
        bare.stopCurrentPlayback()
    }

    @Test
    fun `unsupported controls stay unsupported when called repeatedly`() {
        val bare = object : AudioPlayer {
            override suspend fun play(bytes: ByteArray, format: String, rate: Float): AudioPlaybackResult = AudioPlaybackResult.Failed
        }

        // 默认实现不持有任何状态：反复调用结果稳定，stop 依旧空操作。
        repeat(2) {
            assertFalse(bare.pauseCurrentPlayback())
            assertFalse(bare.resumeCurrentPlayback())
            bare.stopCurrentPlayback()
        }
    }
}
