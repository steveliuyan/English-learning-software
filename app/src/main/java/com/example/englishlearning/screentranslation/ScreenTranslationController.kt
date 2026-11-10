package com.example.englishlearning.screentranslation

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 会话状态的唯一持有者（F3-05）：前台服务写、界面读，双方都只经由 [dispatch] 走 Reducer，
 * 因此「已发请求数」「模型名」「错误分类」永远不会出现两份互相矛盾的副本。
 *
 * 出站确认按域名一次台账（F2-02）在**进程内**记忆：[confirmedTextHosts] 在会话结束（[endSession]）时清空，
 * 进程重启即失效——不落盘，避免跨界变成长期豁免。
 *
 * 为什么清在**结束**而不是**开始**：F3-05 的确认闭环天生要「确认完再重启一次会话」——MediaProjection
 * 的授权令牌在 Android 14+ 一次性，用户确认域名之后必须重新走一次系统授权才能继续。若在
 * [beginSession] 里清空，那次重启会把刚拿到的确认一并抹掉，于是又弹一次确认、又重启，死循环。
 */
@Singleton
class ScreenTranslationController
    @Inject
    constructor() {
        private val mutableState = MutableStateFlow(ScreenTranslationSnapshot())
        val state: StateFlow<ScreenTranslationSnapshot> = mutableState.asStateFlow()

        private val mutableConfirmedTextHosts = MutableStateFlow<Set<String>>(emptySet())
        val confirmedTextHosts: StateFlow<Set<String>> = mutableConfirmedTextHosts.asStateFlow()

        fun dispatch(event: ScreenTranslationEvent) {
            mutableState.value = ScreenTranslationReducer.reduce(mutableState.value, event)
        }

        fun confirmTextHost(host: String) {
            mutableConfirmedTextHosts.value = mutableConfirmedTextHosts.value + host
        }

        /** 放弃这次出站确认：把待确认的域名收掉（不写台账、不重开会话）。 */
        fun dismissPendingConfirmation() {
            dispatch(ScreenTranslationEvent.ConfirmationDismissed)
        }

        /** 会话开始。**不清**确认台账（见类 KDoc：确认后重启会话是正常路径）。 */
        fun beginSession(
            mode: ScreenTranslationMode,
            modelName: String?,
        ) {
            dispatch(ScreenTranslationEvent.Started(mode, modelName))
        }

        /** 会话结束：连同按域名的确认台账一起作废（下一次会话重新确认）。 */
        fun endSession(notice: ScreenTranslationNotice? = null) {
            mutableConfirmedTextHosts.value = emptySet()
            dispatch(ScreenTranslationEvent.Stopped(notice))
        }
    }
