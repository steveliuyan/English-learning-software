# 接续提示词 — UI 重设计批 2（2026-10-19 现场）

用法：整段复制到新对话作为第一条消息。新主对话接手后，第一件事是核验远端 tip 与工作树，再按「下一步顺序」推进。

---

接管 `H:\EnglishLearningSoftware` 的 Android 背词 App「UI 重设计」开发。你是控制者（controller），按 `superpowers:subagent-driven-development` 逐任务派发实现席 + 独立评审席 + 恰好一轮修复 + 一轮窄复评。

**先做这三步，别先读代码：**

1. 读 `AGENTS.md`、`docs/superpowers/plans/2026-10-17-ui-redesign.md`（Task 14 一节含 R-14.1／R-14.2／R-14.3／R-14.4 四条裁决，是批 2 当前的权威口径）、`docs/superpowers/specs/2026-10-17-ui-redesign-design.md`。
2. 读 SDD 台账 `.superpowers/sdd/2026-10-17-ui-redesign/progress.md` **最后 200 行**（恢复地图：哪些任务已关、教训、结转账）。台账第一行是它服务的计划文件；`Task N: complete` 行的任务不要再派发。
3. 核验现场：`git rev-parse --abbrev-ref HEAD` 应为 `stage-5-ui-r2-learning-path`，`git log --oneline -3` 应见 `42e9c14`（14A-i）／`4a42cb7`（R-14.4）／`920eabe`，`git status --porcelain` 应为空。JVM 基线 **226 类 / 1673 tests / 0 失败 / 0 跳过**，只准增。

**当前状态**：批 1 设计系统底座已关并推过远端（远端 `stage-5-ui-r1-design-system` tip = `f0b6d79`，批 1 收口在其内）。批 2 从该点起共 19 枚提交，**全部只在本地、未推送**（含 Task 12.5／13／14B／14A-i）。批 2 任务状态：

- 已关：Task 12.5、Task 13（词书选取屏）、Task 14B（今日计划屏，14B 走完了「实现→独立评审→修复轮→窄复评」全循环）。
- **14A-i 刚由控制者收口**（实现席第 74 次工具调用撞平台额度掉线，未写报告；控制者撤掉它留在薄壳里的未撤变异 `isDark=false` → `LocalIsDark.current`，复跑门控转绿，代写报告 `task-14a-i-report.md`）。**它的独立评审席还没走**，评审包已生成：`.superpowers/sdd/2026-10-17-ui-redesign/review-4a42cb7..42e9c14.diff`（1 commit / 69 KB）。
- 未做：14A-ii、Task 15（词卡屏）、16（词卡详情 `CardDetailScreen`）、17（学习壳 + duotone 底导）、18（批 2 收口目检 + connected 全清单）。

**下一步顺序（按此走，别自己重排）**：

1. **14A-i 独立评审席**：输入三件——brief `task-14a-i-brief.md`、报告 `task-14a-i-report.md`、评审包 diff；判据是「spec 合规 + 代码质量」双 verdict，并**要求评审席当席造变异**（本批两次栽在「只造直接改字面量的变异等于没测」）。重点疑点写在报告 §5：薄壳正确性只有源码门（文本匹配）背书、无设备证据；`RawColorLiteralRatchetTest` 站点行随书写形状改（计数不动）是否可接受；`ParameterDefaultStaticReadLedger` 清空后交叉取样是否仍非空转。
2. **14A-i 修复轮（如评审判需修）**：单席、不扩范围、一轮。
3. **14A-ii**：brief 已就绪 `task-14a-ii-brief.md`。`PillButtonTest` 那条禁用底断言期望 `#E5E5EA` 是 9 月遗留的陈旧值（生产从未渲染过；14A 后真实值是 `AppColorLight.tint2 #DCEAE1`），必须**基线取证在前、改判据在后**，并补三条取色用例（非品牌 Primary 浅套不得出现 base、AiSpeech Secondary 用 `onMint` 不用 `#188F76`、深色档 knockout 墨）。connected 只跑 `emulator-5574`。
4. Task 15 → 16 → 17 → 18，每席先 `bash <sdd-skill>/scripts/task-brief docs/superpowers/plans/2026-10-17-ui-redesign.md N` 取 brief。

**硬约束（违反即返工）**：

- 说中文。用户已拍板 **不合 main、先停在分支上**；`git push` **永不自动执行**，每批结束时向用户请示（R-12.2）。
- TDD 强制：先看 RED 再看 GREEN，且**必须造走「不动字面量的间接路径」的变异**（`.copy`／`merge`／裸构造器／把 tag 上提／把布尔位写死）。
- **`Agent` 工具 150 轮硬上限**，且子代理会撞「daily usage limit」掉线：派发必须窄范围，brief 里写「接近上限就停下写现场、以 BLOCKED 返回，别留半成品提交」；掉线时工作树里的未提交改动由控制者自己收口（先 `git status` + `git diff` 找出未撤的变异）。
- 门控串行、**禁后台**（并发 Gradle 撞 Windows 目录锁会伪报 `:app:testDebugUnitTest FAILED`）：
  `GRADLE="$HOME/.gradle/wrapper/dists/gradle-8.13-bin/5xuhj0ry160q40clulazy9h7d/gradle-8.13/bin/gradle.bat"`（哈希目录随版本变化，不符就从 `gradle/wrapper/gradle-wrapper.properties` 重新发现并强制 `-bin`，禁抓 9.x），
  `"$GRADLE" :app:assembleDebug :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin --no-daemon --no-build-cache --rerun-tasks > log 2>&1; echo exit=$?`，
  计数从 `app/build/test-results/testDebugUnitTest/*.xml` 汇总。
- connected **绝禁 `connectedDebugAndroidTest`**（清空设备数据）：`adb install -r` 两个 APK + `am instrument -e class <FQCN>`；instrumented 只在 `emulator-5574`；**真机 `bf353dda` 只手工试用，绝不在其上跑 instrument 或装测试包**。
- 棘轮是**双向精确相等**的债务台账（`RawColorLiteralRatchetTest`／`DarkTokenConsumptionRatchetTest`／`SharedComponentUniquenessRatchetTest`）：迁一处减一处，seed 减到 0 才删整行；禁止把新增固化进 seed，禁止删断言转绿。
- 安全红线：API Key 只在 Keystore，不进 Room/日志/剪贴板/导出包/截图；密钥与挑战码明文不入 git（放 `.git/qa-scratch/`）；禁 `fallbackToDestructiveMigration`；未获用户确认不得加账号/云同步/订阅/广告/远程代码执行；禁 force push、禁推其他远端、禁擅删远端分支；破坏性动作先请示。
- 提交：逐文件 `git add`（禁 `-A`/`.`），带身份参数
  `git -c user.name=steveliuyan -c user.email=2021250388@qq.com commit -m "type(scope): 中文摘要"`；不 `--amend`、不 `--no-verify`。
- 里程碑出 debug APK 时，用户允许装机试用：`adb -s bf353dda install -r app/build/outputs/apk/debug/app-debug.apk`（MIUI 会自动卸载 adb 装的第三方应用，装完立刻测）。

**待办账（下一席必须带上，别当它不存在）**：14B 复评留的 N-2（connected 采样串无 `maxLines`，大字号换行会让 0.45 高度阈值假红）、批 3 的 R-14.4 归属债（品牌墨 `AppPalette.OnMintPrimary` 是刻意主题无关读点，批 3 Task 26 Step 3 要么搬成 `MintPalette` 的 `internal val`，要么给「与主题无关的容器-墨对」新开逐站点注记表；不许在 14A 现场造第三张表）、`SpeakerButton.kt:138` 的 no-op `spacedBy`、`WordRow` 字面量相邻正则、浅套 text-2/tint 0.047 余量、`AppScreen.kt` 余 2 枚裸 hex + 3 处 `OnMintPrimary`、`MintBackground` 页底族、Task 18 目检清单（`ProfileCreationScreen` 页头 14dp→2dp、今日计划「常驻查词卡 + 入口组」连排节奏、Secondary 浅套容器与卡片同色的发丝线观感、`PhotoTranslationScreen` 无 connected 覆盖）。

**源码门的书写坑（本轮新学）**：判据按「剥注释后的文本」计数，所以**注释/KDoc 里不能内嵌被钉的那个精确调用形状**，否则说明文字自己撞红「恰一处」。
