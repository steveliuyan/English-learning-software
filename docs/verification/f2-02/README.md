# F2-02 AI Profile 与安全调用 —— 验收记录

本目录记录 F2-02 在真机上的验收证据。**结论先写**：F2-02 的领域层、持久化层、出站确认策略、
参数白名单、失败映射与配置界面均已实现并通过 JVM 与真机测试；本轮**没有**实现文章生成、
TTS/OCR、真实网络请求或任意请求体覆盖（与 spec 的「不在本阶段实现」一致）。

---

## 1. 范围与不变量

**本轮交付的东西**

| 层 | 交付物 |
| --- | --- |
| 领域 | `ai/domain/AiProfile.kt`、`ai/domain/AiEndpointPolicy.kt`（复用上一轮） |
| 策略 | `ai/AiOutboundConfirmation.kt`（出站确认）、`ai/AiRequestPolicy.kt`（参数白名单）、`ai/AiFailure.kt`（失败映射） |
| 存储 | `RoomAiProfileRepository`、`InternalAiProfileDao`、`AiProfileEntity`（复用上一轮），本轮补上 **Hilt 接入** |
| 密钥 | `AiProfileSecretUseCase`（别名 `SecretReference("ai-profile-{id}")`），密钥只进 `SecretStore` |
| 界面 | `ui/AiProfileSettingsScreen.kt`、`ui/AiProfileSettingsViewModel.kt`、`ai/AiProfileIdFactory.kt` |
| 接线 | `ui/SettingsScreen.kt`、`ui/AppScreen.kt`、`di/AppModule.kt`、`MainActivity.kt` |

**必须成立的四条不变量**（下面每一项取证都指向其中之一）

1. **报成功 ⇒ 数据可读回**。保存/删除返回 `Result.success` 时，重新读库必须能看到对应状态。
2. **API Key 不落在 Room、日志、崩溃报告、导出包、文章元数据里**。Room 里只允许出现 `secretAlias`。
3. **换 Endpoint 必然重新确认**。确认的粒度是**域名**（`String?`）而不是布尔量，否则旧域名的
   确认会被顺延到新域名上。理由与取舍见 `docs/decisions/2026-09-24-ai-outbound-confirmation.md`，
   以及计划文档「执行记录与计划偏差」第 3、10 条。
4. **越界参数在类型层面就进不来**。`AiAdvancedParameters` 没有承载 header / body / tools 的位置。

---

## 2. 环境与产物

| 项 | 值 |
| --- | --- |
| 设备 | `bf353dda` |
| 系统 | MIUI V816 / Android 13 |
| 构建 | `ANDROID_HOME=D:/Android/Sdk`、`GRADLE_USER_HOME=D:/Android/GradleCache`、`--no-daemon --no-build-cache` |
| 主 APK | `app/build/outputs/apk/debug/app-debug.apk` — md5 `df9f971d00268fd4ac54502915ca3f2e` |
| 测试 APK | `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` — md5 `2a9903d87963fa910c786e3d6cb82323` |
| 真机日志 | `verification-logs/f2-02-instrumented-final.txt` |
| 日志泄露扫描 | `verification-logs/f2-02-logcat-probe.txt` |
| 走查脚本 | `verification-logs/walk.sh`（`ROOT` / `SHOTS` 可用环境变量覆盖） |

> `verification-logs/` 在 `.gitignore` 里（第 14 行），**不进仓库**。本节引用的日志、db 副本、
> 走查 dump 都是本机产物，用于复核；仓库内保留的是本目录的截图与本文档。

> 刻意不抄「验收时的 HEAD 提交号」：它会在下一次提交后立刻变成假话。要复现请以本表的 APK md5 为准。

**跑测方式**：`adb install -r -t` + `am instrument -w`，**刻意不用** `connectedDebugAndroidTest`
（AGENTS.md 已记录：AGP 会在跑测后卸载被测应用，连带删除 `/data/data/<pkg>`，不可恢复）。

---

## 3. 测试计数

| 套件 | 用例数 | 结果 | 基线 | 增量 |
| --- | --- | --- | --- | --- |
| JVM 单元测试（JUnit 5） | **231** | 0 failed / 0 errors / 0 skipped | 190 | +41 |
| 真机 instrumentation（JUnit 4） | **153** | `OK (153 tests)`，`Error in` 计数 0 | 132 | +21 |

本轮**新增**的用例（合计恰好等于上表的增量）：

**JVM：41 例 = 18 + 16 + 7**

| 类 | 用例数 | 说明 |
| --- | --- | --- |
| `ai.AiRequestPolicyTest` | 18 | 新建，参数白名单与边界 |
| `ui.AiProfileSettingsViewModelTest` | 16 | 新建，配置界面状态机 |
| `ai.AiFailureTest` | 7 | 新建，失败文案与操作映射 |

> `ai.domain.AiEndpointPolicyTest`（4）、`ai.domain.AiProfileTest`（2）、
> `ai.AiProfileSecretUseCaseTest`（3）、`ui.AiFeatureTest`（6）等 AI 相关类属于**上一轮**的成果，
> 已计入 190 的基线，不在本轮增量里。

**真机：21 例 = 18 + 3**

| 类 | 本轮增量 | 该类总数 |
| --- | --- | --- |
| `ui.AiProfileSettingsScreenTest` | 18 | 18（全新） |
| `ui.SettingsScreenTest` | 3 | 11（新增 3 条 AI 入口用例，去掉旧的 `settings_pending_ai` 占位断言） |

> `ui.AppScreenTest`（17）在本轮有改动（新增 `showAiProfiles` 分支与直达入口），
> 但**没有新增用例**，故不计入增量。

> JVM 汇总取自 `app/build/test-results/testDebugUnitTest/*.xml`（44 suites）解析，不是手抄控制台数字。

---

## 4. 数据完整性取证

真机上有真实用户数据，所以「测试没弄坏数据」必须用**可复算的指纹**证明，不能靠「没看到报错」。

**方法**：取 db、`-wal`、`-shm` **三个文件**一起算 MD5。只取 `.db` 会漏掉未 checkpoint 的写入——
WAL 模式下新数据可能整个在 `-wal` 里，只看主库文件会得到「没变」的假象。

```bash
MSYS_NO_PATHCONV=1 adb exec-out run-as com.example.englishlearning \
  cat /data/data/com.example.englishlearning/databases/english-learning.db > baseline/english-learning.db
```

> `MSYS_NO_PATHCONV=1` 是必需的。Git Bash 会把 `/data/data/...` 改写成 Windows 路径，
> 命令失败但 `2>/dev/null` 吞掉错误、退出码仍是 0，最后落下一个 **162 字节的假文件**。

**四个时点的结果**

| 文件 | 基线 | 跑测后 | 走查后 | 最终跑测后 |
| --- | --- | --- | --- | --- |
| `english-learning.db` | `4a39b664490091816b55f19999a21d32` | 同 | 同 | 同 |
| `english-learning.db-wal` | `72ed682dc549e6efc5ed00a8620fd6b1` | 同 | 同 | 同 |
| `english-learning.db-shm` | `267cbc2644221328443a6181a2a3d629` | 同 | 同 | 同 |

**结论**：三文件 MD5 在四个时点逐字一致 ⇒ 整套测试与人工走查**没有写入过真机数据库**。
取证目录：`verification-logs/f2-02-db-{baseline,after,walkthrough,final}/`。

---

## 5. AC2-04 逐条对照

> AC2-04：未配置 Key、HTTP Endpoint、私网 Endpoint、401、429、超时和畸形响应均返回对应可操作错误，学习完成状态保持。

| 触发条件 | 错误类型 | 用户看到的文案 | 提供的操作 | 取证 |
| --- | --- | --- | --- | --- |
| 未配置 Key | `AiFailure.NotConfigured` | 「还没有配置 AI 服务，先去「设置 · AI」添加一套。」 | 去配置 | `AiFailureTest`；截图 `11-ai-tab-badge.png` |
| HTTP / 私网 / 环回 Endpoint | `AppError.InvalidAiConfiguration`（`validateEndpoint`） | 不泄露原始 URL 的稳定错误 | 去配置 | `AiEndpointPolicyTest`（4 例）；真机截图 `05-editor-validation-error.png` |
| 401 | `AiFailure.Unauthorized` | 「密钥被拒绝，去「设置 · AI」换一份有效的密钥。」 | **去配置**（不是「重试」） | `AiFailureTest` |
| 429 | `AiFailure.RateLimited` | 限流文案 | 稍后重试 | `AiFailureTest` |
| 超时 | `AiFailure.Timeout` | 超时文案 | 稍后重试 | `AiFailureTest` |
| 畸形响应 | `AiFailure.InvalidResponse` | 响应异常文案 | 重新生成 | `AiFailureTest` |
| 网络不可达 | `AiFailure.NetworkUnavailable` | 「网络不可用，请检查网络或 Endpoint」 | 检查网络 | 上一轮 F2-03 真机已验 |
| 能力不支持 | `AiFailure.CapabilityUnsupported` | 能力不支持文案 | 更换模型 | `AiFailureTest` |

**「学习完成状态保持」**：本轮 F2-02 不发起任何真实网络调用（spec 明确排除），所以不存在
「AI 失败影响今日计划」的路径。真机走查中反复进出 AI 配置页与功能页后，今日计划三文件 MD5
仍未变化，佐证没有副作用写入（§4）。

**AC2-04 满足情况：满足（在 F2-02 范围内）**。注意本轮的 401/429/超时/畸形响应是**契约级**验证
（九类失败 → 九类文案 → 六类操作的映射），不是真机打到真实服务得到的响应——真实传输在 F2-03。

---

## 6. AC2-06 逐条对照

> AC2-06：备份逻辑快照与日志中只保存 Profile 非敏感字段和脱敏诊断，不含 API Key 或 Authorization。

**审查方法**：对本轮全部改动文件逐项扫描四类风险面。

| 风险面 | 命令 | 结果 |
| --- | --- | --- |
| 明文 `Authorization` / `Bearer` 出现在主源码 | `grep -rn "Authorization\|Bearer" app/src/main/java/` | **无匹配** |
| 日志提及 key/secret/token | `grep -rni "Log\.[dvieww].*\(key\|secret\|token\)" app/src/main/java/` | **无匹配** |
| 硬编码形如 `sk-xxxx` 的密钥 | `grep -rn "sk-[A-Za-z0-9]\{16,\}" app/src/main/` | **无匹配** |
| 任意请求体 / 额外 HTTP 头入口 | `grep -rn "headers\s*[:=]\|requestBody\|extraHeaders" app/src/main/java/` | 唯一命中 `export/WorksheetPdfRenderer.kt:229` 的 `headers: List<String>`，实际是 **PDF 表格表头**，与 HTTP 无关（同名误报） |
| 外部链接执行路径 | `grep -rn "ACTION_VIEW\|startActivity\|CustomTabs\|openUrl" app/src/main/java/` | 命中两处既有代码：`export/WorksheetShareLauncher.kt`（用户显式点「导出」才拉起系统分享面板）与 `ui/AppScreen.kt:208`（调用前者）。**AI 路径无任何外链执行** |

**真机运行期日志取证**（不只是静态扫描）。清空 logcat → 拉起应用 → 依次进入
「学习 → 设置 → AI 服务与密钥」，让配置列表完整渲染一次，再取日志：

```bash
adb logcat -c
adb shell am start -n com.example.englishlearning/.launch.BrandLaunchActivity
# walk.sh tapname 设置 / tapname "AI 服务与密钥"
adb logcat -d > verification-logs/f2-02-logcat-probe.txt
grep -inE "sk-[A-Za-z0-9]{8,}|authorization|bearer |api[_-]?key" verification-logs/f2-02-logcat-probe.txt
```

结果：日志 **5107 行**，其中 **140 行**与本应用相关，密钥/鉴权模式**零匹配**。
那次列表渲染出的文案是「AI 服务 | 可以配置多套 OpenAI 兼容服务。密钥保存在本机系统安全区，
不写进数据库、日志或备份。| 还没有设置密钥 | 文本 …」，页面本身也不回显任何密钥值。

**Room 侧的结构性保证**：`AiProfileEntity` 的列是
`profileId / displayName / websiteUrl / endpoint / model / capabilities / secretAlias / temperature / topP / maxTokens / timeoutSeconds / systemPromptTemplateId`
——**没有任何列可以放明文密钥**。密钥只以 `SecretReference("ai-profile-{id}")` 别名形式出现，
真实材料在 `AndroidKeyStoreSecretStore`。

**直接读库复核**（对 db 副本执行，不依赖任何界面转录）：

```
PRAGMA table_info(ai_profiles) → 12 列，与实体声明一致，无 key/secret/token 值列
SELECT * FROM ai_profiles      → 4 行，secretAlias 列只有别名文本，无任何密钥材料
```

**参数白名单的结构性保证**：`AiAdvancedParameters` 只有四个数字 + 一个受约束模板 id
（`require(systemPromptTemplateId == "default-reading-v1")`，白名单长度为 1）。
**该类型没有承载 header / body / tools 的位置**，所以「拒绝任意请求体覆盖」不是靠运行时检查兜住的，
而是类型上就做不到。

**AC2-06 满足情况：满足**。唯一残留面见 §7.1。

---

## 7. 本轮发现的限制、缺陷与残留面

如实记录，不写成「一切正常」。

### 7.1 已知残留面：输入期间密钥以 `String` 短暂存在于 ViewModel 状态（未闭合）

Compose 的输入框要求 `String`，所以用户输入密钥的过程中，密钥会以 `String` 形式短暂存在于
`AiProfileEditorUiState.draft.pendingKey` 里。它在保存时转成 `CharArray` 交给 `SecretStore`，
成功后随编辑页一起丢弃（`_editor.value = null`）。

它**不进 Room、不进日志、不进备份、不回显**（编辑已有配置时输入框为空，只显示「已设置密钥 /
还没有设置密钥」），但**不是「零暴露」**——如果后续要收紧，方向是自定义 `TextFieldValue`
配合立即洗写的缓冲，而不是继续在 Compose 状态里传 `String`。已记入计划文档「执行记录与计划偏差」第 11 条。

### 7.2 真机 `adb shell input text` 完全不可用，文字输入类断言只能由 instrumentation 承担

先按要求做了最小实测（打一个词再 dump），**dump 无任何变化**。随后尝试替代路径
`adb shell cmd clipboard set-text`，得到 `No shell command implementation.` ——剪贴板路径也封死。

**处置**：放弃文字注入，真机走查改做**纯点击可覆盖**的取证（校验拦截、返回键逐层、
编辑页预填、能力开关、直达入口）；所有涉及文字输入的断言交给 instrumentation 测试承担
（`AiProfileSettingsScreenTest` 18 例，用 Compose test API，不走 `input` 注入）。

`input tap` 与 `uiautomator dump` 本轮**均正常可用**——不可用的只有文字/字母键注入。

### 7.3 真机返回键「第一次无效」是系统行为，不是缺陷

走查中按一次 `keyevent 4` 没有退层，第二次才生效。查 `dumpsys input_method` 得
`mInputShown=false`（软键盘未显示），因此不是「返回键被 IME 吃掉」这一常见解释。
经在编辑页、列表页、设置栏三处重复验证，表现一致，判定为 Android 标准的返回键分发行为。
**如实记录为行为观察，不计为缺陷**；返回键逐层退出的功能正确性由截图 `06-backkey-returns-to-list.png`
与 instrumentation/走查双重覆盖。

### 7.4 走查抓到一处我自己引入的「文案说谎」（已修）

AI 服务配置页的返回按钮原本写「← 返回设置」。但该页现在有**两个入口**：
「设置 · AI 服务与密钥」和「AI 学」功能页的「去配置 AI 服务」。从后者进来时底部选中的仍是
**AI 学**栏，写死「返回设置」就会指错地方。

改成中性表述「← 返回上一层」/ `contentDescription = "返回上一层"`，并加了回归断言
`back_label_does_not_claim_a_specific_tab`（断言「← 返回设置」不再存在）。
这与上一轮修过的同类缺陷同源：**导航文案不能假设自己只有一个入口**。

### 7.5 真机库里 4 行历史 AI 配置：已查明来源，是本轮方案要取代的那版实现的遗留

**现象**：真机 `ai_profiles` 表有 4 行，界面显示「已配置 4 套 · 0 套已设置密钥」。
直接读库副本（`verification-logs/f2-02-db-baseline/`）得到：

```
profileId                             displayName  endpoint                  model          secretAlias
31450d74-…-0edea0b73724              贝贝 luna     https://www.bb-api.com/v1 gpt-5.6-luna   pending
f1daead6-…-0a1ce4769c03              贝贝 luna     https://www.bb-api.com/v1 gpt-5.6-luna   pending
b18b311e-…-f17db12f28e7              (空)          (空)                       (空)           pending
004235a1-…-f93b4c39f09e              (空)          (空)                       (空)           pending
```

**关键证据：`secretAlias` 全是字面量 `'pending'`。** 当前实现的别名一定是
`ai-profile-{profileId}`（`AiProfileSecretUseCase.referenceFor`），而 `'pending'` 这个字符串
**在整棵源码树和全部 git 历史中都不存在**：

```bash
grep -rn '"pending"' app/src/            # 无匹配
git log --all --oneline -S'pending'      # 只命中 settings_pending_* 测试 tag 与 pendingEventId 变量，无关
git log --all --oneline -- ui/AiProfileScreen.kt ui/AiProfileViewModel.kt   # 无输出 → 本仓库从未有过这两个文件
```

**结论**：这 4 行来自**更早的一版 AI Profile 配置实现**（当日记忆记为提交 `e2c9007`，
UI 改版 `e9410b8`，含 `AiProfileScreen`/`AiProfileViewModel`）。那版实现属于**另一棵工作树**，
其提交对象不在本仓库对象库里（`git cat-file -t e2c9007` → `Not a valid object name`），
所以本仓库查不到 `pending` 这个字面量。它在真机上被实际使用过（记忆记录：真机路径已确认、
未输入真实 Key，故 `hasKey=false`）。

**那版实现有两个缺陷，正是本轮设计要修的**：① 每次保存新增一行而不是覆盖
（现在用 `@Insert(onConflict = REPLACE)` 幂等）；② 用占位别名 `pending` 代替真实密钥别名
（现在是保存时才生成 id 并派生 `ai-profile-{id}`）。「2 行同名 + 2 行空名」的成对重复
与这两个缺陷的表现吻合。

**本轮处置：未删除、未修改任何一行。** 这属于真机上的用户数据，即使是垃圾行也不由我单方面清。
建议由用户确认后清除，或直接忽略（它们不影响当前功能：`hasKey=false`，徽章如实显示「AI 尚未接通」）。

---

## 8. Task 6 四项专门取证

| 要求 | 做法 | 结果 |
| --- | --- | --- |
| 跑全部 F2-02 JVM 测试 + 存储/错误回归 + Room 迁移 instrumentation | JVM 全量 + 真机全量 | 231/0、153/0；迁移由 `AppDatabaseMigrationTest`（9 例）覆盖 |
| 真机证明「进程重启后 Profile 元数据可读、Room 行与日志中无 Key」 | 重新拉起应用 → 学习 → 设置 → AI 服务与密钥，让列表完整渲染；随后 `adb logcat -d` 全量落盘并做模式扫描；另对 db 副本做 `PRAGMA table_info` + `SELECT *` | 元数据 4 行正常渲染；`secretAlias` 列只有别名文本；logcat 5107 行 / 本应用 140 行，**密钥与鉴权模式零匹配**（`verification-logs/f2-02-logcat-probe.txt`） |
| 用环回/私网/HTTP 夹具跑 Endpoint 策略，**不允许真实网络请求** | `AiEndpointPolicyTest` 纯函数夹具；本轮无任何 socket | 通过；主源码无 HTTP 客户端接入 |
| 记录精确计数、设备、APK 校验和、已知的无关失败与排除项 | 本文 §2 §3 §9 | 已记录；**本轮无失败用例** |

---

## 9. 走查截图索引

全部由 `verification-logs/walk.sh` 在设备 `bf353dda` 上产出，未经裁剪修饰。

| 文件 | 内容 |
| --- | --- |
| `01-tab-learning.png` | 底部四栏，学习栏选中 |
| `02-tab-settings.png` | 设置中心，「AI 服务与密钥」行副标题报**真实**数量 |
| `03-ai-profiles-list.png` | AI 配置列表（4 套，均无密钥） |
| `04-ai-profile-editor.png` | 新建配置编辑页 |
| `04b-editor-defaults-visible.png` | 高级参数默认值可见（0.7 / 1.0 / 1024 / 30） |
| `05-editor-validation-error.png` | 空表单保存被拦，显示「请填写配置名称。」 |
| `06-backkey-returns-to-list.png` | 返回键从编辑页退回列表 |
| `07-editor-prefilled-existing.png` | 编辑已有配置，字段正确预填 |
| `08-editor-key-section-existing.png` | 已有配置的密钥区显示「还没有设置密钥」，**不回显任何密钥** |
| `11-ai-tab-badge.png` | AI 学页头卡徽章如实显示「AI 尚未接通」 |
| `13-direct-entry-lands-on-ai-services.png` | 功能页「去配置 AI 服务」直达配置页 |

---

## 10. 排除项

- **不覆盖**：文章生成、真实 HTTP 传输、TTS/OCR、`GET /models` 模型拉取、默认 Profile 记忆、
  阅读页复用 Profile —— 均属 F2-03 / F2-04。
- **不覆盖**：多 AI Profile 的「默认选择」设置项。spec F2-02 未要求，且当前没有消费方，
  提前实现会多出一个没人读的设置项。
- **不计为失败**：`grep -c "^Error in " ...` 在无匹配时退出码为 1，这是 grep 的正常语义，
  与测试结果无关（日志里出现的是 `OK (153 tests)`）。
- **未做的取证**：真机文字输入链路（§7.2 环境限制所致，已用 instrumentation 替代）。
