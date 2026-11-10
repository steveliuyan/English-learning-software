package com.example.englishlearning.ui

import androidx.lifecycle.ViewModel
import com.example.englishlearning.screentranslation.ScreenTranslationController
import com.example.englishlearning.screentranslation.ScreenTranslationSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/**
 * 悬浮窗屏幕翻译（F3-05）的界面壳。
 *
 * 这里**不持有任何会话逻辑**：状态归 [ScreenTranslationController]（前台服务写的也是同一份），
 * 界面只做三件事——读快照、把「已确认这个域名的发送」记进会话台账、把「停止」转成一次结束。
 *
 * 之所以要一层 ViewModel 而不是在屏里直接注入控制器：[PhotoTranslationScreen] 的 connected 测试
 * 用 createComposeRule()（无 Hilt）直接渲染，屏内一旦出现 hiltViewModel() 那批用例当场崩。
 *
 * 会话真正的启动走 ScreenTranslationService.startIntent（由界面侧的授权 launcher 发起），
 * 不经 ViewModel：MediaProjection 的 resultCode/resultData 是 Intent 级一次性凭证，
 * 多经一层只会让「谁能发起会话」这件事变模糊。
 */
@HiltViewModel
class ScreenTranslationViewModel
    @Inject
    constructor(
        private val controller: ScreenTranslationController,
    ) : ViewModel() {
        val state: StateFlow<ScreenTranslationSnapshot> = controller.state

        /** 用户在共享出站确认框里点了「确认发送」：记住这个域名，随后界面重新申请截屏授权并重开会话。 */
        fun confirmOutbound(host: String) {
            controller.confirmTextHost(host)
            controller.dismissPendingConfirmation()
        }

        /** 取消确认：域名台账不写，等确认的那条状态收掉，会话保持结束态。 */
        fun declineOutbound() {
            controller.dismissPendingConfirmation()
            controller.endSession(null)
        }

        /** 界面侧按下停止（服务端的 finish 也会走一遍 endSession，互为兜底）。 */
        fun stop() {
            controller.endSession(null)
        }
    }
