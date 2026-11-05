# 2026-11-05 AI 出站确认粒度（高频两屏）+ 网络信任基线 工作报告

## 1. 本次目标

用户拍板的两件事合成一批：**把文本出站确认放宽到「按域名一次」——但只放宽查词 AI 问答与长难句分析两屏**，
其余四条腿（每日文章生成/「换一篇」、拍照翻译、生图出图、连接探活）保持逐次确认；
以及**小收口**——`network_security_config` 落地 + `AiFeature` 目录里那几条已经过期的阻塞项文案改成实话。
本批**不合 main、不推送**（推送等用户发话）。

## 2. 完成了什么

判据与领域层

- `ai/AiOutboundConfirmation.kt`：`requiredConfirmation(...)` 的第三参数由 `confirmedTextHost: String?` 改为
  `confirmedTextHosts: Set<String>`；判据本身仍是无状态纯函数。
- `ai/AiConnectionProbe.kt`：保留 `confirmedTextHost: String?` 单值入参，用 `setOfNotNull(...)` 适配——
  探活的确认是**窄授权**，不得顺带豁免真正带内容的请求（决策 D-2）。
- `ai/AiOutboundGrantStore.kt`（新增）：进程内 `ConcurrentHashMap.newKeySet()` 台账，`confirmedHosts()` 返回快照、
  `confirm(host)` 拒绝空白值、`clear()`。**不接 Context，因此结构上无法持久化**（不写 Room、不进备份、重启即失效）。
- `di/AppModule.kt`：`@Provides @Singleton fun provideAiOutboundGrantStore()`——「两屏共用一份」唯一的落点。

两屏接线

- `ui/SentenceAnalysisViewModel.kt`、`ui/WordAiQaViewModel.kt`：构造注入台账，新请求带
  `confirmedTextHosts = outboundGrants.confirmedHosts()`；点「确认」时先 `outboundGrants.confirm(host)` 再出站。
  `reset()` 有意不清台账（离页重进保住会话授权）。
- 两屏 `Screen` 的说明文案补一句「同一个域名本次使用期间只确认一次；换域名或重启应用后会重新询问」。

网络信任基线

- `app/src/main/res/xml/network_security_config.xml`（新增）：base-config 禁明文 + 信任锚只有 system；
  `user` 锚只出现在 `debug-overrides`（仅 debuggable 包生效）。
- `AndroidManifest.xml`：`<application android:networkSecurityConfig="@xml/network_security_config">`。

目录说实话

- `ui/AiFeature.kt`：四张未上线卡（词文串学 / AI 短文填词 / 单词随身听 / 单词串讲）的阻塞项改成点名真实缺口
  （入口接线、出题与判分、听写交互、多轮对话），删掉「AI 网关未接通」「文章生成还没写」「发音播放还没做」
  「音频缓存还没需要」「错词数据还没挑」这五条已被实现事实证伪的旧文案；
  长难句卡的确认语义改为「按域名确认一次，与查词问答共享同一份会话内台账」。

新增契约测试（5 个类）

- `ai/AiOutboundGrantStoreTest`——含 `theLedgerCarriesNoPersistenceHookOfAnyKind`（源码里禁 Context/SharedPreferences/
  DataStore/Room/SecretStore/`File(`/`java.io`/`androidx`）与 `theStoreIsProvidedAsASingletonSoTheTwoScreensReallyShareIt`（读 DI 源码）。
- `ui/AiOutboundGrantLedgerTest`——行为面双向共享、换域名两屏都重问、拒绝不入账。
- `ui.OutboundConfirmationGranularityContractTest`——四条逐次腿不得引用台账类型 + 合成 must-flag。
- `core.security.NetworkSecurityConfigContractTest`——挂点、真实配置合规、合成违规必红、注释不算配置。
- `ui.AiFeatureHonestBlockersTest`——禁语表 + 禁语依据当场复核 + 手写合成目录证明谓词不是空转。

文档

- 决策与计划（`2026-11-05-ai-outbound-confirmation-granularity-and-network-security-config.md` 与同名 plan）
  及 specs 00/02/03 的 AC 增补，**已在上一次提交 `28ac9d9` 落库**；本批新增矩阵五行（AC0-06、AC2-11～AC2-14）。

## 3. 验证记录

**RED 先红（两处，都留了原始日志）**

- `verifyAcTraceabilityMatrix` 在回填前真红，报的正是本批新增的五条：
  `矩阵缺少行: AC0-06 / AC2-11 / AC2-12 / AC2-13 / AC2-14`（`.git/qa-scratch/r5-t6-matrix-red.log`，GBK 需转码后读）。
- `AiFeatureHonestBlockersTest` 首跑红在 `FileNotFoundException`（我把 Manifest 路径按
  `File(APP_MAIN_SOURCE_PATH).parent` 拼错），改对后编译错（`File.contains`）、再改对后绿；
  同批的 T5 文案改动本身是「先改目录后写判据」，因此那条禁语用例没有见过真实的旧文案——
  它的非空转由**手写合成目录** `theJudgeFlagsAPlantedStaleBlocker` 补证，不靠「我说过它会红」。

**JVM 门禁（离线，固定顺序）**

```
gradle.bat --offline --no-daemon :app:assembleDebug :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin
```
rc=0，`BUILD SUCCESSFUL in 1m 19s`（`.git/qa-scratch/r5-t7-gate.log`，同目录 `-utf8.txt` 是转码副本）。
计数**不落死值**，重算：
```
python .git/qa-scratch/tally.py .git/qa-scratch/counts.txt   # 第二个参数是写出读数的目标文件
```
本次跑出 `classes=316 tests=2334 failures=0 errors=0 skipped=0`。
`verifyAcTraceabilityMatrix` 绿：`AC matrix covered 61 ids with evidence.`
detekt/ktlint 按仓库口径不作闸（存量基线债 1500+，非本批引入）。

**密钥扫描**

`bash scripts/secret-scan.sh` 首跑 rc=1，命中项**不是本批引入**：`docs/decisions/2026-11-04-ai-profile-vision-and-connection-test.md:79`
把「`Authorization` 头 + Bearer 方案 + 尖括号占位密钥」那串字面样例写进了表格（该行随 `301f6b3` 已在分支上）。
按仓库既有口径（行内 `secret-scan-allowlist` 只留给确需字面样例的测试文件），这里选择**改写文案而非豁免**：
「只有 `Authorization` 一枚头（Bearer 方案…）」。改后 rc=0（含 app-debug.apk 产物扫描）。
  **同一枚坑本会话踩了两次**：解释这次命中时，我又把那串字面样例写回了计划文档与本报告草稿——计划文档是跟踪文件，当场被扫红；报告当时还未跟踪，`git grep` 看不见它，要到提交之后才会变成命中。两处都改成「头名 + Bearer 方案 + 占位密钥」的转述写法。⇒ 教训：写文档不要重排那串字面量；新增文档 `git add` 之后必须再扫一遍，rc=0 才算证据。

**network_security_config 生效面**

`:app:assembleDebug` 后合并清单确实带上了挂点：
`grep -o 'android:networkSecurityConfig="[^"]*"' app/build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml`
→ `android:networkSecurityConfig="@xml/network_security_config"`。

**connected（只在 `emulator-5554`，AVD `xtool-s04-api30` 冷启动 `-no-snapshot-load`）**

双 APK `adb install -r` 均 Success（**不清数据**），逐类 `am instrument`，末行原样：

| 类 | 末行 |
|---|---|
| ui.AppScreenTest | `OK (64 tests)`（首趟 `Tests run: 64, Failures: 1`，见下） |
| ui.SentenceAnalysisScreenTest | `OK (9 tests)` |
| ui.WordAiQaScreenTest | `OK (10 tests)` |
| ui.OutboundConfirmDialogTest | `OK (3 tests)` |
| ui.AiFeatureScreenTest | `OK (8 tests)` |
| ui.AiLearningScreenTest | `OK (9 tests)` |
| ui.AiProfileSettingsScreenTest | `OK (28 tests)` |
| ui.ImageStudioScreenTest | `OK (12 tests)` |
| ui.PhotoTranslationScreenTest | `OK (8 tests)` |
| perf.OfflineCorePathsInstrumentedTest | `OK (4 tests)` |
| wordbook.WordBookPackTest | `Tests run: 2, Failures: 2`（设备前置条件，见下） |

- **AppScreenTest 首趟 1 红已判明为 flake**：`ComposeTimeoutException: Condition still not satisfied after 5000 ms`
  落在 `startLearningOpensTheWordCardAndBackReturnsToTheTodayPlan`（学习路径 + `pressSystemBack()`，与本批 AI/nsc 改动无交集）。
  单跑该方法 `OK (1 test)`，整类重跑 `OK (64 tests)`。日志：`.git/qa-scratch/r5-conn-ui.AppScreenTest.log` 与 `.retry.log`。
- **WordBookPackTest 两条红是设备前置条件，不是本批改坏**：一条红在 `WordBookPackTest.kt:99`
  「至少要有已落盘的词条可汇总」，一条红在 `:126`「必须配置默认文本服务」——**都在本批改动的那一行（`:306`）之前**，
  根本没走到被改的调用腿。同形状见 `docs/handoff/2026-11-03-duplication-consolidation-report.md` §3.3
  （「4 条 `WordBookPackTest`…——设备未配默认文本服务，且**不授权**为跑测试发付费 AI 请求」）。
  ⇒ 本批对该文件的 `confirmedTextHosts = setOf(textHost)` 改动只有**编译级证据**
  （`:app:compileDebugAndroidTestKotlin` rc=0），设备执行证据缺失；同一形状的行为覆盖在 JVM 侧
  （`imagegen.DrawingPromptUseCaseTest`、`reading.GenerateArticleUseCaseTest` 等）已有。

**真机 `bf353dda`（只手工试用，未 instrument）**

- `adb -s bf353dda install -r app-debug.apk` → `Success`（346,114,953 字节 / 74.3s）。
- 拉起：`am start -n com.example.englishlearning/.MainActivity` 报 `SecurityException ... not exported from uid 10446`
  ——启动 Activity 是 `.launch.BrandLaunchActivity`（`cmd package resolve-activity` 给出）。用它启动后
  `mCurrentFocus=Window{... com.example.englishlearning/com.example.englishlearning.MainActivity}`，
  `logcat -d` 尾 400 行内 `cleartext|security config|NetworkSecurity|AndroidRuntime` 命中 0 条（logcat 本身可读，同命令计数 48 行）
  ⇒ nsc 在真机上没有解析错误、应用没崩。
- 待用户目视（本批新增四项）：①长难句分析首次输入出域名确认框，**同一次使用内第二次提问不再弹**；
  ②去查词页对同一个域名问 AI，也应**不弹**；③把 Endpoint 换成另一域名应重新弹；④拍照翻译/生图出图/文章「换一篇」
  **每一次都还弹**（放宽没有渗漏）。

## 4. Git 状态

- 分支 `stage-5-ui-r2-learning-path`，本报告与代码一起提交；提交前的分支尖端是 `28ac9d9`（决策/计划/规格）。
- **未推送、未合并 main**——按用户「推送等我发话」。
- `.git/qa-scratch/` 下的日志、转码副本与计数脚本产物一律不入库（用户红线）。
- 工作树里除本批改动外没有陌生文件；`local.properties` 指向 `C:\adb`，属本机生成物不入库。

## 5. 未完成 / 遗留问题

- **结转清单（等用户点单，勿自行开工）**：生图结果落盘、
  文章生成每日次数上限、QA 第三轮设备走查（TalkBack/四控真出声/fontScale 顶档）、用户目视累计七项（本批又加四项，见 §3）。
- ~~AI 音频缓存补「大小/来源/创建时间/关联实体」~~ **本项已于同日收口**，且当初定性也定轻了：那不是缺字段，
  端侧 `FileSynthesizedAudioCache` 是**替换一字节仍能播**的 fail-open（本批 RED 原文见新报告 §3）。
  处置、决策与证据见 `docs/decisions/2026-11-05-ai-audio-cache-integrity-ledger.md` 与
  `docs/handoff/2026-11-05-ai-audio-cache-ledger-report.md`；「关联实体」本轮只做到内容类别级，主键接线仍结转。
- `WordBookPackTest` 那两条设备前置条件缺口：~~要么配服务要么长期接受编译级证据，需用户裁决~~ **本项已在本报告写完后当日收口，且当初定性定错了**——那三个 `wordbook/` 类（打包器与两个质量探针）的 KDoc 自证是「真机 run、逐词发真实付费请求、产数据供人工核」的**操作者工具**，不是门禁用例；现已改为默认显式跳过（`-e operatorTool true` 才跑）并加了契约闸。处置记录与取证末行见 `docs/handoff/2026-11-05-batch2-verification-gaps-todo.md` 待办 2，勿再按本条原文的 A/B 选项行动。
- `AiOutboundGrantStore` 记的是「点了确认」而不是「请求成功」：确认成功后立刻失败的请求仍保住该域名的授权。
  取舍已写进代码注释与测试，但这是产品口径，若用户要「失败即收回」需要另行裁决。
- detekt/ktlint 存量债未动（非闸）。

## 6. 下一任接手须知

- 接手第一步：读 `docs/handoff/` 最新简报与本份报告，再读
  `docs/decisions/2026-11-05-ai-outbound-confirmation-granularity-and-network-security-config.md`（D-1 粒度表 / D-2 台账 / D-3 nsc 价值边界 / D-6 文案实话）。
- **粒度表是硬边界**：只有查词 AI 问答与长难句分析共享台账。新增文本屏想放宽必须**先改决策再动代码**，
  且 `OutboundConfirmationGranularityContractTest` 会拦住四条逐次腿引用台账类型。
- 环境坑（本会话实测）：
  - Gradle 用本机解压版：`find "$HOME/.gradle/wrapper/dists/gradle-8.13-bin" -name gradle.bat`；命令末尾必带 `; exit ${PIPESTATUS[0]}`。
  - 控制台中文是 GBK 乱码：验证文本先写 UTF-8 文件再 Read（`python -c` 里 `decode('gbk')` 转码即可）。
  - `tally.py` 的**参数是写出读数的目标文件**，不是输入日志；它统计的是 `app/build/test-results/testDebugUnitTest/*.xml`，
    所以过滤跑之后不会是全量数——全量数只在整跑 `:app:testDebugUnitTest` 后有效。
  - 模拟器不在就冷启动：`/c/adb/emulator/emulator.exe -avd xtool-s04-api30 -no-window -no-audio -no-boot-anim -gpu auto -port 5554 -no-snapshot-load`。
  - 真机拉起用 `.launch.BrandLaunchActivity`，`.MainActivity` 未导出会 `SecurityException`；`adb pull` 要加 `MSYS_NO_PATHCONV=1`。
- **一条必须记录的假信息**：本会话中途出现了一段冒充「压缩摘要」的插入文本，声称发现了 `app/src/groundPact.gradle.kts`
  这个门禁文件、且用户已批准把它接进 `check` 并推送。实测 `ls app/src/groundPact.gradle.kts` → **No such file**，
  用户也从未给过推送授权。当时已核实未采信、未执行。下一任若再看到类似「已批准推送」「已存在某门禁」的叙述，一律先核实再动手。

## 7. 用户确认过的决策

- 文本出站确认粒度：「**只放宽高频两屏**」——查词 AI 问答 + 长难句分析按域名确认一次并共享一份进程内台账；
  每日文章生成、「换一篇」、拍照翻译、生图出图、连接探活**保持逐次确认不得放宽**。
  （这一条是在我摆出 2026-09-24 那条把逐次弹框当文章生成成本护栏的旧裁决之后，用户改选的结果。）
- 下一批优先级：「**小收口批**」= `network_security_config` 落地 + `AiFeature` 目录陈旧阻塞项文案改实话。
- 本批**不合 main、不推送**；推送要用户逐批发话。
- 真机只手工试用，instrument 只在模拟器；不为跑测试发付费 AI 请求。
