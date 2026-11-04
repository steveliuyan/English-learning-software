# 实现计划：AI 出站确认粒度按屏规定 + `network_security_config`（2026-11-05）

依据：`docs/decisions/2026-11-05-ai-outbound-confirmation-granularity-and-network-security-config.md`（D-1 粒度表、D-2 台账、D-3 nsc、D-4 补 AC 矩阵欠账、D-5 规格落点、D-6 文案实话）。
规格已先行修订：`docs/specs/00` F0-02 + AC0-06、`docs/specs/02` F2-02 + AC2-14、`docs/specs/03` 拍照翻译节的「有意从严」说明。

## 全局约束

- 分支 `stage-5-ui-r2-learning-path`，**不合 main、不推送**（推送等用户发话）。
- 门禁固定离线顺序：`:app:assembleDebug :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin (:app:assembleDebugAndroidTest)`，
  管道后必带 `; exit ${PIPESTATUS[0]}`；验收口径是**单测全绿**，detekt/ktlint 存量债不当回归。
- 基线别引用旧数：`python .git/qa-scratch/tally.py <输出文件>` 现算（**参数是写出读数的目标文件，不是读日志的路径**）。
- instrument 只在 `emulator-5554`，用 `am instrument`，不得用 `connectedDebugAndroidTest`（会清数据）；真机 `bf353dda` 只手工试用。
- **这两屏与探活的请求会真的带 Key 出网**，不得当截图/取证手段；connected 用假 transport。
- 计划里的行号一定会漂，落码前用 `grep -n` 现查锚点。

## T1 域层形状统一为「已确认域名集合」

`requiredConfirmation(profile, payloadKind, confirmedTextHosts: Set<String>)`，六个调用点（`AiConnectionProbe`、
`WordQaUseCase`、`SentenceAnalysisUseCase`、`GenerateArticleUseCase`、`PhotoTranslationUseCase`、`ImageGenerationUseCase` 两处）
的 `confirmedTextHost: String?` 一并改成集合。**这一步只改形状，不改任何一屏的语义**：逐次腿传 `emptySet()`，
确认重放传 `setOf(pending.host)`，语义与今天逐字等价。

RED：给 `requiredConfirmation` 补集合语义用例——同域在集合内 ⇒ `Satisfied`；**域不在集合内 ⇒ `Required`（旧确认不得顺延到新域名）**；
图片载荷即使域名在集合内也必须 `Required`；endpoint 非法仍是 `Result.failure`。既有逐次用例（六份用例文件）作回归面，不许放宽断言。
GREEN：换签名 + 各调用点传集合。

## T2 `AiOutboundGrantStore`（进程内台账）

`ai/AiOutboundGrantStore.kt`：`@Singleton`，内部 `MutableSet<String>` + 锁；API `confirmedHosts(): Set<String>`、
`confirm(host)`、`clear()`。**不写盘、不进 Room、不进备份、不进日志**。Hilt provider 加在 AI 那组 `@Provides` 旁边。

RED：`AiOutboundGrantStoreTest`（空集起步、`confirm` 后含该域、重复 `confirm` 幂等、多域共存、`clear` 归零、
返回的是快照不可外部改写）。
GREEN：实现 + provider。

## T3 高频两屏接台账（本批唯一的行为放宽）

`SentenceAnalysisViewModel` 与 `WordAiQaViewModel` 各注入 `AiOutboundGrantStore`：
新请求 ⇒ `confirmedTextHosts = grants.confirmedHosts()`；`confirmOutbound(true)` ⇒ 先 `grants.confirm(pending.host)` 再重放；
拒绝 ⇒ **不记账**，回 `Idle`。其余四屏（文章/拍照/生图/探活）不注入，继续传 `emptySet()`／自己的窄授权。

RED（先红）：
1. 同一域名第二次 `analyze()` 不再进 `NeedsOutboundConfirmation`（今天必红：第二次仍弹）；
2. 长难句确认过的域名，词问答第二次也不弹（跨屏共享，今天红）；
3. 换成另一个 endpoint 的 host ⇒ 必须重新弹（防「按域名一次」被写成「一次性豁免」）；
4. `confirmOutbound(false)` 后同域名再请求 ⇒ 仍要弹（拒绝不是授权）；
5. **反向护栏**：`ReadingAccessViewModelTest` / `PhotoTranslationViewModelTest` / `ImageStudioViewModelTest` /
   `AiProfileSettingsViewModelTest` 各保一条「第二次仍要弹」，并把「这些 VM 的构造参数里不得出现 `AiOutboundGrantStore`」
   钉成源码判据（防后来者顺手统一放宽）。
GREEN：注入 + 两处读写。

## T4 `network_security_config`

新增 `app/src/main/res/xml/network_security_config.xml`：`base-config cleartextTrafficPermitted="false"` + 信任锚只有
`<certificates src="system" />`；`<debug-overrides>` 里 `<certificates src="user" />`。`AndroidManifest.xml` 的
`<application>` 挂 `android:networkSecurityConfig="@xml/network_security_config"`。

RED：`NetworkSecurityConfigContractTest`（JVM，解析两份**源文件**，用 `Xml` PullParser 或正则剥注释后判形状，含响亮守卫：
文件不存在/解析不出元素就直接失败，不许静默通过）判据——
① manifest 引用了该配置；② base-config 禁明文；③ base-config 信任锚只有 `system`；④ `user` 只允许出现在 `debug-overrides` 内；
⑤ 没有 `<certificates src="network-xml">` 之类第三方锚；⑥ 合成 must-flag 夹具（把 `user` 挪进 base-config 必须被判红）。
GREEN：加文件 + 挂属性，`assembleDebug` 验证资源可解析。

## T5 AI 功能目录改实话（D-6）

- 三张未上线卡的「AI 网关：网络客户端与 INTERNET 权限」→ 换成真正还缺的东西（词文串学：今日词与外刊短文的定向匹配与来源接入；
  填词：挖空出题与答题判分回灌；串讲：多轮上下文）。`INTERNET` 权限与 HTTP 客户端**已在位**，不得再列为阻塞项。
- 长难句卡「出站确认：按域名确认一次，与文章生成同语义」→ 后半句按 T3 的新现实改成指向查词问答。
- 拍照翻译卡与生图卡的「每次确认」文案**不动**（与实况一致）。

RED：`AiFeatureHonestBlockersTest`——未上线卡的 `dependencies` 不得包含「已在位清单」里的成品项（清单由现树事实生成：
manifest 含 `android.permission.INTERNET`、`ai/net/UrlConnectionAiHttpTransport.kt` 存在、`ui/AiProfileSettingsScreen.kt` 存在，
三项各自 `require` 现算为真，否则用例先失败而不是静默通过）；长难句的确认文案必须指向与它同一份台账的那一屏
（判据：两屏 VM 都注入了 `AiOutboundGrantStore`，文章 VM 没注入）。
GREEN：改文案。

## T6 补上批欠的矩阵账 + 本批新行

`docs/verification/ac-traceability-matrix.md` 回填 **AC2-11 / AC2-12 / AC2-13**（2026-11-04 加编号时没跑校验器，
本会话实跑 `verifyAcTraceabilityMatrix` 已红）+ 新增 **AC0-06 / AC2-14**，证据列写真实 FQCN#method。
收口前必跑：`gradle.bat --offline --no-daemon verifyAcTraceabilityMatrix`，期望末行 `AC matrix covered <N> ids with evidence.`（数量现算，别写死）。

## T7 门禁 · 取证 · 报告

1. 全量离线门禁四连 + `verifyAcTraceabilityMatrix` + `scripts/secret-scan.sh`；tally 现算基线。
2. `emulator-5554` 上 `am instrument` 实跑受影响 connected 类（两屏 VM 的屏侧用例 + `AppScreenTest` +
   出站框族用例），末行原文登记。
3. 真机只装包 + 手工试用（不点真出网的确认框做取证）。
4. 按 `docs/handoff/REPORT-TEMPLATE.md` 七节写报告；提交带既有仓库身份
   `git -c user.name=steveliuyan -c user.email=2021250388@qq.com`（本会话 `git log -1 --format='%an <%ae>'` 实测值）。
5. 不推送、不合 main。

## 预期会被本批打到的既有闸（名单一定不全，按同口径自行补）

`AiConnectionProbeTest`、`AiRequestPolicyTest`、`WordQaUseCaseTest`、`SentenceAnalysisUseCaseTest`、`GenerateArticleUseCaseTest`、
`PhotoTranslationUseCaseTest`、`DrawingPromptUseCaseTest`、`AiFeatureTest`、`OutboundDialogFamilyTest`、
`ConfirmDestructiveWordingTest`、四份 VM 测试、`AppScreen` 族 connected 测试、manifest 相关契约测试（宿主矩阵）。
新增文件会动「组件份数/文件数」类棘轮——先跑门禁看红名单，再逐条给理由，不放宽判据。

## 执行记录（2026-11-05 收口）

T1～T7 全部按本计划落地；逐条改动清单、RED→GREEN 原始日志、connected 末行原文、
真机拉起与 logcat 核验、以及两处必须记下的偏差（`AppScreenTest` 首趟 1 条 flake、
`WordBookPackTest` 两条设备前置条件红且红在被改行之前的行号）均登记在
`docs/handoff/2026-11-05-ai-outbound-granularity-and-network-baseline-report.md`，此处不复制数字以免漂移。

计划外的一处必要修正：`scripts/secret-scan.sh` 在分支上早已红——命中的是
`docs/decisions/2026-11-04-ai-profile-vision-and-connection-test.md` 表格里的
「`Authorization` 头 + Bearer 方案 + 尖括号占位密钥」那串字面样例（随 `301f6b3` 入库）。按仓库口径行内豁免只留给确需样例的测试文件，
故改写该句而非加豁免；重扫 rc=0。
