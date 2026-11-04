package com.example.englishlearning.ai

import java.util.concurrent.ConcurrentHashMap

/**
 * 会话级「文本出站已确认域名」台账（决策 2026-11-05 D-1/D-2）。
 *
 * 它存在的唯一理由：查词 AI 问答与长难句分析是**同一类高频、单句、无成本 surprise** 的文本调用，
 * 用户拍板这两屏改成「按域名确认一次」，其余四条腿（每日文章生成与「换一篇」、拍照翻译、
 * AI 生图出图、连接探活）保持逐次确认。既然「一次」要覆盖两屏，状态就必须住在两屏之外的一个
 * 共享对象里——住在任何一屏的 ViewModel 里，另一屏就看不见，用户会在两个页面各问一遍同一个域名。
 *
 * **三条设计约束，逐条对应一类会真实发生的事故：**
 *
 *  1. **只活在本进程**：没有 Context，因此也就没有 SharedPreferences、文件、Room、Keystore 任何一条
 *     落盘通道（这条「结构上做不到」由 `AiOutboundGrantStoreTest` 钉在源码上）。
 *     确认状态一旦跨重启存活，用户换了 Key、换了 Endpoint、恢复过一次备份之后，出站请求会继续
 *     静默豁免——而确认框那句「文本和密钥会发送给该服务」只在被点击的那一次成立过。
 *     进程被杀 / 应用重启后重新问一次，是这套语义**故意保留**的代价，不是缺陷。
 *  2. **读出去的那份是副本**：[confirmedHosts] 返回 [Set]，且是从内部集合复制出来的新集合。
 *     若把可变背衬原样递出去，任何一屏都能 `add` 一个没确认过的域名再拿去免确认出站，
 *     「用户点过确认」就此由代码代填。
 *  3. **写入只有一条路径**：只有 [confirm] 能入账，且拒收空白值；[clear] 是唯一的清账入口
 *     （拒绝确认、退出功能页、以及未来「换 Profile」这类兜底）。
 *
 * 键是 **host**，不是 profileId：确认框向用户陈述的对象是域名（「将发送给 api.example.com」），
 * 所以豁免的对象也必须是域名。同一份 endpoint 下换模型不该重新问，换 endpoint 必须重新问——
 * 这条由 `AiRequestPolicyTest` 的集合判定用例与两屏的 UI 用例共同钉住。
 *
 * 与 [requiredConfirmation] 的分工：域层只做「这个 host 在不在已确认集合里」的**无状态判定**，
 * 状态在本类。这样四条逐次腿可以照常传 `emptySet()`，不必为了「不记住」而另开一条代码分支。
 */
class AiOutboundGrantStore {
    private val confirmedHosts: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** 当前已确认的域名快照（副本；调用方改写这份副本不影响台账）。 */
    fun confirmedHosts(): Set<String> = confirmedHosts.toSet()

    /** 记账一个用户已经确认过的域名；空白值属于接线错误，响亮失败而不是静默入账。 */
    fun confirm(host: String) {
        require(host.isNotBlank()) { "出站确认台账只收真实域名：空白值入账等于给一次未确认的出站放行" }
        confirmedHosts.add(host)
    }

    /** 清空台账：拒绝确认、离开功能页时由调用方决定要不要用（决策 D-1 未要求这两屏主动清账）。 */
    fun clear() {
        confirmedHosts.clear()
    }
}
