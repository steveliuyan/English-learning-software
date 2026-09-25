# F2-03/F2-06 文章生成、多来源与阅读 —— 验收记录

**结论先写**：F2-03（真实 AI 生成 → 解析校验 → 落库 → 受控渲染）与 F2-06（三来源同一管线、
来源元数据、许可白名单、用户导入）已全部实现，并在真机上完成了**真实出网**的端到端走查：
生成成功路径、两条失败路径（超时、断网）、复用零请求、换一篇版本 +1 全部取证。
三个真机集成缺口（设备级配置选择、API 34+ 方法崩溃、阅读栏不可滚动）当场 TDD 修复。
已知限制见第 7 节，逐条如实列出，不含粉饰。

---

## 1. 环境与产物

| 项 | 值 |
| --- | --- |
| 设备 | `bf353dda`（MIUI V816 / Android 13 / API 33），装有用户真实学习数据 |
| 真实 AI 服务 | `www.bb-api.com`（用户自配，模型 `gpt-5.6-luna`），密钥由用户本人经投屏录入 |
| 构建 | `ANDROID_HOME=D:/Android/Sdk`、`GRADLE_USER_HOME=D:/Android/GradleCache`、`--no-daemon --no-build-cache` |
| 主 APK | `app-debug.apk` — md5 `ef69b77335f8d214941c0b3633c5104e` |
| 测试 APK | `app-debug-androidTest.apk` — md5 `45229551e71132c83df94ff53e6fe719` |

> 刻意不抄验收时的 HEAD 提交号（提交后即成假话），以 APK md5 为准。
> `verification-logs/` 在 `.gitignore` 里不进仓库；本目录截图与本文档进仓库。

**跑测方式**：`adb install -r -t`（就地升级，`/data/data` 保留）+ `am instrument -w`，
**禁用** `connectedDebugAndroidTest`（AGP 会卸载被测应用，用户数据不可恢复）。
数据完整性用三文件（db / `-wal` / `-shm`）MD5 多时点比对，全部 `MSYS_NO_PATHCONV=1`。

---

## 2. 测试计数

| 套件 | 用例数 | 结果 | 相对上一记录 |
| --- | --- | --- | --- |
| JVM 单元测试 | **391** | 0 failed / 0 errors | +2（设备级配置选择：`selectsTheFirstProfileWithAKey_regardlessOfTheLearningProfileId`、`reportsNoKeyWhenEveryDeviceProfileLacksAKey`） |
| 真机 instrumentation 全量 | **197** | OK | +1（`readyStateHistoryEntryIsReachableByScrolling`） |
| 其中 `ReadingAccessScreenTest` | 10 | OK | +1（滚动可达性，见 6.3） |
| `ArticleImportScreenTest`（新增文件） | 5 | OK | F2-06 Task F 批次新增 |

新增断言均按铁律做了变异测试：出站确认拒绝分支、`regenerate` 排除词传递、
`verticalScroll` 移除 → 对应测试逐一真红后恢复。

---

## 3. 端到端走查（Task 9 Step 5，真实出网）

按计划八项逐条，全部真机取证：

| # | 项目 | 结果 | 证据 |
| --- | --- | --- | --- |
| 1 | 生成前成本提示可见 | ✅ 「每次生成都会真实调用你配置的 AI 服务并可能产生费用」 | `01-reading-hub-cost-hint.png` |
| 2 | 确认框展示真实域名 | ✅ 「你的文本与该服务的密钥将发送给 www.bb-api.com，并可能产生费用。」 | `02-outbound-confirm-real-host.png` |
| 3 | 取消 → 不发请求零写入 | ✅ 界面回 Idle；库三文件 MD5 与基线一致（`t9-check2/check3`） | 库快照 `verification-logs/t9-check*.db*` |
| 4 | 确认 → 真实生成成功 | ✅ 《City Farms Bring New Hope to Urban Food Supply》标题/英文/中文渲染，来源行「AI 生成 · gpt-5.6-luna」 | `04-generated-article.png`；DB 行 `dd493434…` v1，英文 1394 字符、中文 458 字符 |
| 5 | 派生高亮与未覆盖词 | ✅ 正文高亮 obvious/ability/climate/generous 与 `coveredLemmas` 对应；未覆盖词 chips feature/reduce/achieve/benefit 与今日计划互斥 | `05-highlights-and-uncovered.png` |
| 6 | 退出重进复用、无新请求 | ✅ 同文秒开、内容逐字一致；三文件 MD5 不变 | `verification-logs/t9-db-reopen.db*` |
| 7 | 换一篇 → 版本 +1，旧版可读 | ✅ v2 行新增（《City Launches Green Housing Plan》），v1 行原样保留；历史页并列「第 2 版 / 第 1 版」 | `07-regenerated-article-v2.png`、`08-reading-history-two-versions.png`；DB 两行 `version=1/2` |
| 8 | 断网：缓存可读、生成报网络错误 | ✅ `svc wifi/data disable` 后已缓存文章完整可读；生成 8 秒内报「网络不可用，检查网络后重试。」 | `09-offline-network-error.png` |

**计划外收获（两条失败路径的真实取证）**：

- 「换一篇」两次在默认 30 秒超时被客户端掐断 → 横幅显示「等待超时，稍后再试。」，
  库三文件 MD5 不变（**失败不落库**在真机上被意外实证，`06-timeout-failure-banner.png`）。
- 复用判定同日同类型同长度生效（走查 6 与 7 共用同一 `articleId` 序列）。

### 三文件 MD5 时间线

主库文件在首次生成后被 checkpoint，此后保持 `0197cc72…` 不变；差异全部体现在 WAL：

| 时点 | 事件 | db | db-wal | db-shm |
| --- | --- | --- | --- | --- |
| T0 08:47–09:19 | 基线（生成前，articles rows=0，三个采样一致） | `71d22060…` | `d41d8cd…`(0 字节) | `b7c14ec6…` |
| T1 09:26 | **首次生成成功写入**（预期变化，来自真实生成） | `0197cc72…` | `b5f4102b…` | `876f5440…` |
| T2 09:28 | 退出重进（复用） | `0197cc72…` | 0 字节 | `b7c14ec6…` |
| T3/T4 09:29/09:31 | 两次「换一篇」超时（失败） | `0197cc72…` | 0 字节 | `b7c14ec6…` |
| T5 09:33 | 断网生成失败 + 离线阅读 | `0197cc72…` | 0 字节 | `b7c14ec6…` |
| T6 09:56 | 用户改配置 timeout 30→90（用户操作，非生成） | `0197cc72…` | `d1165f74…` | `e955b078…` |
| T7 09:58 | 换一篇成功写入 v2 行 | `0197cc72…` | `e92903b3…` | `1f3867a9…` |
| T8 10:20 | 走查收尾（重启应用、浏览历史） | `0197cc72…` | `e92903b3…` | `1f3867a9…` |

T2–T5 四个时点 WAL 均为 0 字节：复用、失败、离线读**在文件级证明零写入**。

---

## 4. 验收标准逐条（AC2-01 ~ AC2-06，含 F2-06 各条）

| AC | 内容 | 结论 | 验证层级 |
| --- | --- | --- | --- |
| AC2-01 | 未解锁时入口不可用并说明原因 | ✅ | 契约级 + 界面测试（`lockedStateShowsReasonAndDisablesArticleChoices`）；真机本日已解锁故走锁定态截图为 F2-01 既有取证 |
| AC2-02 | 生成成功含标题/英文/中文/派生高亮/未覆盖词 | ✅ | **真机实出网**（走查 4、5） |
| AC2-03 | 复用；换一篇新版本；旧版本可读 | ✅（存储级；见限制 7.2） | 真机走查 6、7 + JVM `reusesTheStoredArticleWithoutCallingTheAi` / `regeneratingSavesANewVersionAndKeepsTheOldOneReadable` |
| AC2-04 | 未配置/HTTP/私网/401/429/超时/畸形 → 可操作错误且不落库 | ✅（超时/断网真机实达；401/429/私网/畸形为契约级） | JVM `GenerateArticleUseCaseTest` 21 例（JDK HttpServer 打真实 HTTP）+ 真机超时横幅与断网横幅 |
| AC2-05 | 断网后已缓存可读、不可生成、不损坏今日计划 | ✅ | 真机走查 8；失败分支零写入（MD5） |
| AC2-06 | 只存非敏感字段与脱敏诊断 | ✅ | 库直查：`ai_profiles` 无密钥列（仅 `secretAlias`）；`articles.parameterSummary` 实值为 `model=gpt-5.6-luna temperature=0.7 top_p=1.0 max_tokens=1024 timeout_seconds=30 template=default-reading-v1`（v2 行记 90，与用户改配置时序自洽）——无 Key、无 Endpoint、无响应原文；泄露哨兵测试（endpoint 片段 assertDoesNotExist） |
| F2-06 三来源同管线 | 同一 `Article` 类型，展示链路无按来源分支 | ✅ | 契约级（类型系统）+ 三入口真机可见（`01`） |
| F2-06 来源白名单 | 白名单外 URL 拒绝且不发请求 | ✅ | 契约级（`ArticleSourceRegistry` + `FetchArticleUseCase` JVM 用例） |
| F2-06 署名与外链不自动打开 | 来源行展示、不自动跳转 | ✅ | AI 生成走查 4（来源行「AI 生成 · gpt-5.6-luna」）；外刊条目 JVM/界面测试 |
| F2-06 导入不联网且自负其责 | 导入界面含「你有权使用」声明 | ✅ | 界面测试 5 例 + `import_disclaimer` tag |
| F2-06 抓取/导入失败保留今日完成状态 | 失败分支零写库 | ✅ | JVM 用例（`plans.saveIfAbsentCalls == 0` 断言） |

---

## 5. 契约级验证说明（未真机触达、由 JVM/界面测试覆盖）

以下路径**真机端到端未触发**（触发它们需要人为制造对应故障或消耗多次真实调用），
由 JVM 层用 JDK 内置 `HttpServer` 打**真实 HTTP** 的用例覆盖：

- HTTP 401 → `Unauthorized`（横幅带「去配置」按钮，界面测试另有覆盖）；
- HTTP 429 → 限流文案；HTTP 5xx/畸形 JSON/截断响应 → `InvalidResponse`；
- 私网地址 Endpoint 被拒（`validateEndpoint`）与重定向不跟随；
- 响应体超 512KB → `ResponseTooLarge`；
- 同日复用判定的全部排除维度；`ArticleQualityPolicy` 七项保存前校验；
- 外刊抓取解析与白名单外 URL 拒绝；导入的不可信输入校验。

理由与风险：这些是纯函数/映射逻辑，输入输出确定，JVM 真实 HTTP 已覆盖传输行为；
真机复跑只会重复同一代码路径并消耗用户费用，边际价值低。

---

## 6. 真机走查发现并当场修复的缺口（均 TDD：先红测试再修）

1. **设备级配置选择语义**：`AiProfile.profileId` 是配置自身主键，与学习档案无关；
   原实现按学习 `profileId` 查配置，用户录好配置仍报「还没有配置」。
   → 改为遍历设备配置、选第一套存有密钥的（+2 JVM 用例，见第 2 节）。
2. **API 34+ 方法在 Android 13 崩溃**：`LocalDate.ofInstant` 真机 `NoSuchMethodError`
   （JVM 抓不到：桌面 JDK 17+ 有该方法）→ 改 `instant.atZone(zoneId).toLocalDate()`。
3. **阅读栏不可滚动**：Ready 态内容高于屏幕后根 Column 无 `verticalScroll`，
   「查看本地阅读历史」被折叠在屏外不可达（连「职场」类型按钮都看不见）。
   → +1 界面测试（`performScrollToNode`，RED：`no parent layout with a Scroll
   SemanticsAction`）→ 加 `verticalScroll(rememberScrollState())`（GREEN）→
   变异（移除修饰符）真红 → 恢复后真机复验：历史入口滚到可见且计数正确（`08`）。

## 7. 已知限制（如实列出）

1. **默认 `timeoutSeconds=30` 对文章生成偏紧**：LLM 生成 300 词 + 译文常超 30 秒，
   真机两次「换一篇」超时即实例。该值本就是用户可配项（5–120），验收中由用户调成
   90 后成功；是否上调默认值留待后续（见决策文档「影响」节）。
2. **历史页卡片不可点开旧版全文**：`ReadingHistoryScreen` 只列标题/日期/版本号，
   卡片无点击行为；「旧版本可读」在本轮为**存储级**证明（DB 行完好 + JVM 仓储用例）。
   从历史点开旧版需要阅读器支持按 `articleId` 加载，属新导航路径，未纳入本轮，
   已记入待办。
3. **成本护栏为有意偏差**：不设每日次数/历史容量上限，只有逐次提示 + 逐次域名确认
   （用户 2026-09-24 决定）。补上限不需改领域模型（决策文档第四节）。
4. **配置选择 V1 策略**：按保存顺序选第一套有密钥的配置；「默认 Profile 选择」
   （F2-02 残留）落地后替换。
5. **未做项**（沿用计划「不在本轮范围」）：端侧 TTS/OCR、真实词典数据、
   `GET /models` 与「测试连接」、图片（Vision）出站链路。

## 8. 数据完整性声明

本轮走查对用户真实数据的影响**限于计划内的文章写入**：`articles` 表新增 2 行
（v1、v2，同日同 `activeWordBookId`），`ai_profiles.timeoutSeconds` 由用户本人改为 90。
其余用户库内容（词书、复习状态、今日计划、事件）无任何写入——T2–T5、T8 时点的
三文件 MD5 逐字一致为证。测试 APK 的安装与 `am instrument` 均为就地升级，未卸载应用。
