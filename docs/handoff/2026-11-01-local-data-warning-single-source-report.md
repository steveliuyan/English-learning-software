# 2026-11-01 本机数据警告收源（U-6）工作报告

关联决策：`docs/decisions/2026-11-03-duplication-consolidation.md` §8（含本批实施记录）
关联计划：`docs/superpowers/plans/2026-11-03-duplication-consolidation.md`「续做」第 10 项
上一份报告：`docs/handoff/2026-11-03-duplication-consolidation-report.md`（重复项整合收口批）

## 1. 本次目标

用户选定「下一项整合 U-6（推荐）」：把「本机数据 / 卸载前导出」这条警告在四屏的四套措辞收成单一真源。

## 2. 完成了什么

- **先改文档再动代码**（AGENTS.md 的范围口径）：决策 §6 把 U-6 从「本批不动」挪出、新增 §8 写清
  四套原句、单一真源形状、许可页那五条为何不动、判据六条；计划文档「不做」删掉 U-6、补「续做」第 10 项。
- **新增唯一措辞源** `app/src/main/java/com/example/englishlearning/notices/LocalDataNotice.kt`：
  四枚事实（只保存在本机 / 无账户也不会自动上云 / 卸载·清除数据·设备损坏后可能无法恢复 / 导出备份是唯一留档途径）
  + 三枚整句（备份 hub、学习记录、导出声明尾部）+ 一枚指向许可页的出口句，全部 `const val`，整句用 `+` 拼。
- **三处生产改吃引用**：`ui/BackupHubScreen.kt`（页尾常驻提示条）、`ui/LearningRecordsScreen.kt`（页尾提示条 + KDoc）、
  `ui/BackupExportViewModel.kt`（`DEFAULT_DISCLOSURE` 尾部两句）。屏里不再手写中文警告。
- **新增闸** `app/src/test/java/com/example/englishlearning/notices/LocalDataNoticeSingleSourceTest.kt`（6 条）。
- **同批改掉的旧字面量闸**：`BackupHubLayoutContractTest` 第 ⑦ 组（整句 pin → 引用形状 pin，注释里的旧句同步换掉）、
  `BackupPageNamesFollowHubRowsTest`（两处举例已过期，改成指向单一真源）。
- 许可页 `thirdPartyPrivacyStatements()` 与 `NoticesPrivacyStatementTest` **一字未动**（那是完整政策那一头，本批让它当被指向的落点）。

## 3. 验证记录

- **RED（先跑，实况）**：6 条里 5 红 1 绿——
  源文件缺失 / 「屏必须恰一处吃引用」`expected 1 but was 0` / 「旧句含注释归零」`expected 0 but was 1` /
  「`ui/` 剥注释后无『卸载』字面量」点名 `BackupExportViewModel.kt`、`BackupHubScreen.kt`、`LearningRecordsScreen.kt` 各 1 处。
  绿的那条是反空转夹具（不依赖生产，本来就该绿）。棘轮**没有**点名 `ui/theme/AppThemePreference.kt`
  里那处引用 AGENTS.md 原话的 KDoc，正好证明「含注释归零」与「剥注释棘轮」两把尺各自生效、没互相污染。
- **GREEN 阶段的措辞纠偏**：第一版事实写成「…会一并删除」，拼成整句读出来才发现「设备损坏」不是「删除」而是「取不回」，
  改成 AGENTS.md 自己的「…后可能无法恢复」，闸里钉的那枚事实串同批改（不是先写代码后补测试）。
- **门禁（终局树）**：`gradle --offline --no-daemon --no-build-cache :app:assembleDebug :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin`
  **BUILD SUCCESSFUL**；JVM **307 类 / 2257 条 / 0 失败 / 0 跳过**（重算：python 汇总
  `app/build/test-results/testDebugUnitTest/*.xml` 的 `tests/failures/errors/skipped`；收口批 306/2251 + 本批 1 类 6 条）。
  detekt/ktlint 按仓库口径不作闸（存量基线债 1500+，非本批引入）。
- **connected（`emulator-5574`，`adb install -r` + `am instrument`，不用 `connectedDebugAndroidTest`）**：
  受影响三类 `BackupEntryUnificationTest` + `LearningRecordsScreenTest` + `LicensesScreenTest` → **OK (15 tests)**；
  相邻三类 `InlineNoticeTest` + `ProfileScreenTest` + `SettingsLicensesEntryTest` → **OK (12 tests)**。
  测试 APK `assembleDebugAndroidTest` 为 UP-TO-DATE（androidTest 源码本批未动，属预期）。
- **真机 `bf353dda`**：仅 `adb install -r`（Success，`dumpsys package` 的 `lastUpdateTime=2026-11-01 12:27:46`），
  未 instrument；页尾提示条由 30 字变 60 字，**用户目视**仍是待办（见 §5）。

## 4. Git 状态

- 分支 `stage-5-ui-r2-learning-path`，**未推送**（等用户说「推送」）。领先远端的枚数用
  `git rev-list --left-right --count origin/stage-5-ui-r2-learning-path...HEAD` 现算为准——本报告自身这枚提交也会让数字漂一。
- 本批两腿：代码腿 **`60c512a`**（新增源 + 三屏 + 两枚旧闸 + 新闸，7 文件 +273/-15），
  文档腿＝本报告这枚提交（决策 §6/§8 + 计划续做项）。
- 无未提交的遗留文件；QA 取证只在 `.git/qa-scratch/`（不入版本库）。

## 5. 未完成 / 遗留问题

- **`ReadingFeedScreen` 那句指向死名字**（本批盘点发现，登记未动）：屏上写「我的 · 开源许可与来源」，
  而那一页早已逐字改名「开源许可与隐私说明」。属 U-5 一族的漏网实例，**待用户拍板**；
  改法：那句副文案 + 同文件两行注释，`ReadingFeedScreenContractTest` 没钉这句所以不牵扯断言。
- **U-9**（本周完成两套可视化）：版面重排，大字号下同屏可见性未取证，仍未动。
- **U-13**（「提醒与发音」纯中转页）：拆它 = hub 台账 13→14 + connected `ReminderSpeechScreenTest` 4 条整条改测，仍未动。
- 真机目视三项：备份 hub 页尾那条由 31 字变 60 字、学习记录页尾同句（旧 29 字）、导出声明那段由 146 字变 189 字
  （长了 43 字，在 `verticalScroll` 容器内，无高度钉）。
- 沿账：Gradient 摘档、Task 42、`MyWordsScreenTest` 的 `font_scale 2.0` 债、QA 第三轮任务书、AI 缺口批。

## 6. 下一任接手须知

- 第一步：读决策 §8（含实施记录）。要再收任何「同一句话多处说」的项，照本批形状——
  事实写成 `const val` 枚，整句用 `+` 拼，屏只引用；闸钉**引用形状**而不是钉句子本体，句子本体由单一真源那条闸逐字钉，
  否则改一次措辞要改两处（`BackupHubLayoutContractTest` 这次就是这么拆的）。
- 两套扫描口径（本仓整合批既定，别混）：**旧句/旧名归零**按整份文件含注释扫（注释是下一批的抄写模板）；
  **「别再写出新的一枚」棘轮**用 `countMatches` 剥注释扫（合法引用 AGENTS.md 原话的 KDoc 不该被判红）。
- `Regex.escape(x)` 得到的是模式串，只能喂 `Regex(...)`，不能当 `String.contains` 的参数，否则判据空转（本批 ⑤ 夹具防的就是它）。
- 单一真源里的声明必须**写在一行**：闸按 `const val NAME = <同一行右值>` 解析，换行会让 ② 取不到而误判。
- connected 怪癖与设备口径统一记在 `android-connected-qa-loop` skill 与项目记忆，不在本文重复。

## 7. 用户确认过的决策

- 上一批末追问「下一步先做哪件」→ 用户选 **U-6**（否决「先推送」「先真机走查」「转 AI 缺口批」）。
- 本批沿用既有授权：不合 `main`、不推送（等「推送」二字）、真机只手工试用、不为截图触发付费 AI 调用。
- 「许可页那五条全文不动，只让三屏的短句子指回它」与「hub 那句按 AGENTS.md 把清除数据/设备损坏补全」
  是本批按 AGENTS.md 与阶段 5 Spec 作的技术裁量，已写在 §8，供用户复核。
