# QA 第三轮（可自动取证组 + 目视清单合并）工作报告 · 2026-11-08

任务书：`docs/handoff/2026-10-15-qa-round3-task.md`（从未执行过的那一轮）。
本轮范围由用户裁定为「可自动组 + 目视清单合并」：能在 emulator-5554 上用 adb / connected 判的都归我跑，
判不了的一律收成一份可勾选走查册交人眼；TalkBack 朗读链与全部「出声类」专项按用户裁定出局（§7）。

任务书 §0 要求报告名为 `2026-10-15-qa-round3-report.md`；本仓库报告命名口径是**报告日**为前缀
（`android-repo-session-handoff` §4.1），故落在本文件名，内容对任务书逐条应答（§5）。

## 1. 本次目标

1. 执行任务书里所有可在模拟器上取证的项：`fontScale` 三档路线（含 3.0 顶档）、深色全页面、320dp 小屏、
   飞行模式离线六条、工作表预览屏的 connected 高度断言 + 取色核对（含深色与顶档）。
2. 把散在七份报告/决策/验证文档里的「待用户目视」项合并成一份可勾选走查册，并逐条核实哪些前提已失效。
3. 给 `AppScreenTest` back/导航族 flaky 补一份**同条件双跑**的复现条件登记（不改断言）。
4. 零生产码改动：本轮只加判据、取证、写册。

## 2. 完成了什么

**走查册**：[`docs/verification/visual-walkthrough-2026-11-08.md`](../verification/visual-walkthrough-2026-11-08.md)
共 35 格（A 组拍板 12 / B 组观感 15 / C 组出站粒度 4 / D 组真机 4），另有 E 组证据索引与 F 组「已剔除项 + 依据」。
重算：`grep -c '^- \[ \]' docs/verification/visual-walkthrough-2026-11-08.md` = 35。

**A1 定位到一条真实缺陷（未改码）**：`AppScreen.kt` 的 `showLicenses` 是 Ready 态二级屏里唯一缺「四件套」
（状态位 + `overlayOpen` + `BackHandler` + 挂载分支）的那一个。`grep -n showLicenses` 全站 4 个命中
（`:190`/`:1595`/`:1597`/`:1612`），`overlayOpen`（`:581-584`）不含它，也没有像 `showAbout`（`:714`）那样的
`BackHandler`，于是系统返回落到 `:587`「不在学习 tab 就回学习 tab」的低优先级处理器上。
五份形状一致（1.0/2.0/3.0、浅/深、320dp）：第一次返回无反应（焦点仍 `MainActivity`、节点数不变），
第二次返回直接退到桌面。处置方案与次生现象（`:1595` 打开许可页时顺手清 `showAbout`）都写进走查册 A1 等人拍板。

**A2 顶档七处破坏，其中一处是流程断裂**：3.0 档「显示释义」按钮落在视口之外而词卡本身不可滚动
（`fs300-wc-2-scrolled.png` 证明滑动零位移）→ 顶档用户点不到揭晓，回忆-揭晓流程走不完。
这条按 spec 05:29「关键流程支持动态字体」判为**不达标项**，不是观感取舍。其余六处（今日页计数行「0」掉形、
底栏「AI 助学」四格不等高、词卡词头尾字母被裁、三档反馈按钮撑破卡片、AI 助学页副标题吃掉首屏 +
功能卡标题折两行 + 分段标题被视口切半行、底栏图标越出导航栏容器压住内容）列为拍板项。

**两条判据盲区（本轮两次撞上，已写进走查册）**：`uiautomator` 的 `bounds` 是节点与父容器的**可视交集**，
被裁掉的节点永远「不越界」；Compose 的 `Text` 溢出裁剪也**不产生省略号**。所以 A2.3、A2.6③、A2.7 三处
在「越界 0 / 省略号 0 / 可点高<48dp 0」全绿的屏上被像素判据漏掉，只有截图与人眼看到。
结论：顶档屏必须图证 + 人眼双轨，判据绿不等于不破版。

**QA3-4 工作表预览屏两条 connected 判据（TDD 全程）**：
`WorksheetPreviewScreenTest.touchTargetsStayAtLeast48dpAtTopFontScale`（顶档 48dp 底线，unclipped 尺寸口径）
+ 新建 `WorksheetPreviewColorEvidenceTest` 三条取色核对（浅色 1.0 / 深色 1.0 / 浅色 3.0，`assertIsDisplayed()`
的裁剪口径与前者互补）。RED 用变异探针拿到：把 `heightIn(min = 52.dp)` 改成 `heightIn(min = 30, max = 38)`、
`containerColor` 从 `strongSurface` 改成 `surface` 后重构建，`Tests run: 4, Failures: 3`
（`Actual height is 38.095215.dp` ×2、`底 #FFFFFF / 墨 #E9F2EC 实测 1.14` ×2）；逐条反向 Edit 撤销变异，
`git diff --stat` 归零后重构建，末态 `OK (4 tests)`；收工前又按**最终代码**（含 §2 下一条的取样面护栏）
重构建测试 APK 复跑一次，同样 `OK (4 tests)`（`Time: 8.353`，读数逐位不变）——保证入库的码就是实跑过的码（§3 #16/#17）。

**取色核对的真实读数**（浅色 1.0 / 深色 1.0 / 浅色 3.0，AA 正文门槛 4.5）：
`#1F4739` 底 + `#E9F2EC` 墨 = **9.10**；`#22332C` + `#E9F2EC` = **11.65**；顶档同底同墨 **9.10**，
按钮底像素 130533→159431、墨像素 870→10391 —— 任务书 ③「按钮行长高不破版」由这两组数值与
`light-fs300.png` 共同支撑。读数另用 PIL 对整屏 PNG 独立复核，桶计数与测试内取样一致
（墨 870/870、10391/10391；深色底 130541/130541；浅档底 130885 整屏 vs 130533 按钮内，差额是页内其他
`strongSurface` 元素，方向正确）。

**本轮抓到并修掉一个取样面缺陷（测试侧，非生产）**：`captureToImage()` 在 instrumented 宿主里量到的
「墨」其实是按钮外沿一圈白色 focus indicator（约 1048px，与字号无关），而改用 `hasText(LABEL)` 未合并树取
`[0]` 又拿到 **Button 容器本身**（它把子节点文案并进自己的语义、深度优先先序排在前），于是「墨 == 底」。
第一版判据「标签墨与按钮底量到同一个颜色」当场把第二种情况报成红；修好取样口径后，把它换成一条更结构化、
不依赖取色结果的判据——**标签捕获面必须严格小于按钮捕获面**（两侧同尺寸即取样面重合，直接判红，
不给「墨其实是底」留空转的机会）。最终口径：底取按钮节点捕获面的众数不透明 ARGB，墨取**面积最小**的
匹配文案节点、并排除底色之后的众数不透明 ARGB，且候选墨桶须有 ≥200 像素。教训见 §6。

**离线、深色、小屏取证**：飞行模式 18 张（`off-*.png`）、深色 10 张（`dark-*.png`）、320dp 8 张（`s320-*.png`）、
三档路线 15/20/32 张（`fs{100,200,300}-*.png`，3.0 档含 AI 助学页补拍 5 张，1.0 档含收工基线留影
`fs100-99-final-state.png`）= 103 张，另加两张散图：`light-check.png`（深色路线前的浅色今日页对照，进度 0/23）
与 connected 复跑后的基线留影 `final-after-guard-rerun.png`，共 105 张 PNG + 同名 uiautomator dump，
全部落 `.git/qa-scratch/qa3/`（不入库）。
重算：`ls -1 .git/qa-scratch/qa3/*.png | wc -l` = 105；
逐前缀 `ls -1 {fs100,fs200,fs300,dark,s320,off}-*.png | wc -l` = 15/20/32/10/8/18。
客观判据（越界 / 省略号 / 可点但高 <48dp）在 fs200、fs300、dark、s320 末站均为 0；
fs100 唯一一条 `Apps list 47x16px` 是系统抽屉，不是应用控件。

**QA3-3 flaky 归因**：同条件双跑，一趟红一趟绿，复现条件与失败点已登记（§3.1），未改任何断言。

## 3. 验证记录

所有设备命令锁 `emulator-5554`（AVD `xtool-s04-api30`，API 30，1080x1920 @420dpi），
`adb` 用 `/c/adb/platform-tools/adb.exe`（唯一暴露 `exec-out` 的构建）。connected 走
`adb install -r` + `am instrument`，未用 `connectedDebugAndroidTest`。

| # | 命令（原文） | 输出末行 / 关键读数 |
|---|---|---|
| 1 | `V=$(grep -oE 'gradle-[0-9]+\.[0-9]+' gradle/wrapper/gradle-wrapper.properties \| head -1); GRADLE=$(ls -1 "$HOME/.gradle/wrapper/dists/$V-bin"/*/$V/bin/gradle.bat \| head -1)` | `.../gradle-8.13-bin/5xuhj0ry160q40clulazy9h7d/gradle-8.13/bin/gradle.bat` |
| 2 | `"$GRADLE" :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon --no-build-cache`（变异态） | `BUILD SUCCESSFUL`，日志 `.git/qa-scratch/qa3/build-mutated.log` |
| 3 | `adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk` + 两个类 4 个方法 `am instrument -w -e class …`（变异态） | `Tests run: 4, Failures: 3`（`worksheet-red.log`） |
| 4 | 反向 Edit 撤销两处变异 → `git diff --stat` | 空（生产码回到 HEAD） |
| 5 | `"$GRADLE" :app:assembleDebugAndroidTest --no-daemon --no-build-cache`（取样面修正后） | `BUILD SUCCESSFUL in 33s`，`gradle_exit=0` |
| 6 | `adb -s emulator-5554 install -r …androidTest…apk` → `adb root` → `rm -rf //sdcard//Android//data//…//worksheet-preview-color-evidence` → `am instrument -w -e class WorksheetPreviewScreenTest#touchTargetsStayAtLeast48dpAtTopFontScale,WorksheetPreviewColorEvidenceTest#…InLightTheme,…InDarkTheme,…AtTopFontScale com.example.englishlearning.test/androidx.test.runner.AndroidJUnitRunner` | `OK (4 tests)`，`Time: 5.719` |
| 7 | `adb -s emulator-5554 pull //sdcard//Android//data//…//worksheet-preview-color-evidence .git/qa-scratch/qa3/worksheet-evidence/` | `4 files pulled, 0 skipped`；`report.tsv` 三行为 §2 读数 |
| 8 | PIL 独立复核：`Counter(im.getdata()).most_common(8)` 逐张 | 桶计数与测试内取样逐位一致（§2） |
| 9 | `adb -s emulator-5554 unroot` → `shell id` | `restarting adbd as non root` → `uid=2000(shell)` |
| 10 | 三档路线与逐屏判据：`python .git/qa-scratch/qa3/{route.py,probe.py,walk.py}` | `route-fs{100,200,300}.log`、`probe-{dark,s320,fs300*}.log` 末站 `越界 0 / 省略号 0 / 可点但高<48dp 0`；`reaudit-all.log` 各 dump「干净」 |
| 11 | 飞行模式链路：`offline-airplane.py` → `offline-retry*.py` | `off-*.log`；复原行 `Active default network: 102`、`airplane_mode_on = 0` |
| 12 | `am instrument -w -e class com.example.englishlearning.ui.AppScreenTest …` 同条件连跑两趟（`font_scale=1.0`、TalkBack 关） | 趟 1 `Tests run: 64, Failures: 1`；趟 2 `OK (64 tests)` |
| 13 | AI 助学页顶档补拍：`python .git/qa-scratch/qa3/{fs300-ai.py,fs300-ai-scrolled.py}`（`font_scale` 3.0 → 测完回 1.0） | 5 组 PNG+XML；判据 `越界 0 / 省略号 0 / 可点<48dp 0`，滚动位移 `True`；两脚本末行 `restore font_scale -> 1.0` |
| 14 | 第三趟补拍用 here-doc 内联跑（`python -` + `f"{D}\\fs300-…"`）：路径里 `\f` 被 Python 当换页符，`ET.parse` 抛 `OSError` 中断，**该趟没走到复原行** | 收工前状态复核 `settings get system font_scale` = **3.0** → 手动 `put 1.0` + 重启应用，`fs100-99-final-state.png` 留影确认基线（浅色 / 1.0 / 今日页四栏正常） |
| 15 | `"$GRADLE" :app:assembleDebug :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin --no-daemon --no-build-cache --rerun-tasks`（整仓门禁，日志 `gate-qa3.log`） | `BUILD SUCCESSFUL in 5m 33s`、`61 actionable tasks: 61 executed`、`gradle_exit=0`；单测产物 332 份 XML（mtime 全部 01:34:54，本次新鲜）合计 **2467 条 / 0 失败 / 0 错误 / 0 跳过** |
| 16 | `"$GRADLE" :app:assembleDebugAndroidTest --no-daemon --no-build-cache`（取样面护栏加入后重构建测试 APK，日志 `build-testapk-guard.log`） | `BUILD SUCCESSFUL in 29s`、`gradle_exit=0`，APK mtime 01:35:47 |
| 17 | 双 APK `install -r` → `adb root` → 删设备上证据目录 → 同一选择器再跑 4 个方法（日志 `worksheet-green-guard.log`） | `OK (4 tests)`，`Time: 8.353`；`report.tsv` 三行读数与 #6/#7 **逐位不变**（9.10 / 11.65 / 9.10，底 130533/130541/159431、墨 870/870/10391）→ 新增的「标签捕获面严格小于按钮捕获面」护栏在真像素上不产生假红 |
| 18 | `adb -s emulator-5554 unroot` → `shell id` | 第一次**没生效**：`export PATH="$PATH:/c/adb/platform-tools"` 是**追加**，`which adb` 命中 `/c/Windows/adb`（1.0.26.29，不认 `unroot`，只打印 help、exit 1，`id` 仍 `uid=0(root)`）；改用绝对路径 `/c/adb/platform-tools/adb.exe`（1.0.41）后 `restarting adbd as non root` → `uid=2000(shell)`；`font_scale=1.0`、`wm size`/`density` Physical、`enabled_accessibility_services=null`，重启应用 `mCurrentFocus=…MainActivity` + `final-after-guard-rerun.png` 留影 |

### 3.1 AppScreenTest 双跑明细（QA3-3 归因）

条件：`emulator-5554`，`font_scale=1.0`，`enabled_accessibility_services=null`（TalkBack 关），
应用与测试 APK 均为 HEAD 生产码重构建，`adb` 非 root，数据保留（未 clear、未 wipe 重装）。
日志：`.git/qa-scratch/qa3/appscreen-double.log`。

- 趟 1（01:15:16 → 01:17:50，`Time: 149.162`）：`Tests run: 64, Failures: 1`
  —— 失败项 `startLearningOpensTheWordCardAndBackReturnsToTheTodayPlan`，
  `ComposeTimeoutException: Condition still not satisfied after 5000 ms` at `AppScreenTest.kt:2813`，
  等的条件是 `pressSystemBack()` 之后 `today_plan_summary` 节点出现。
- 趟 2（01:17:50 → 01:20:15）：`OK (64 tests)`，同一方法通过。

归因结论（只登记，不改断言）：

1. 本设备本条件下**不是 8 条族发**，而是单条 `startLearningOpensWordCard…` 间歇红，两趟 1 红 1 绿 ≈ 50%。
   SP-B 报告 §3 记的「整类 37 用例中 8 个 back/导航族失败」发生在 `emulator-5576`，且当时用例类是 37 条规模；
   现在类里 64 条、`emulator-5554` 上 8 条族全绿 → 那 8 条的复现条件已不可复用为本轮判据。
2. 失败点固定在 `pressSystemBack()` → 等 `today_plan_summary` 这一段（`AppScreenTest.kt:2810-2816`），
   与 2026-11-05「待办 1」记的 5 趟 1 红、C-5 记的 `Tests run: 64, Failures: 1` 是**同一条方法、同一个断言点**，
   本轮把复现率从「偶发」收窄到「约两趟一次」。
3. 判据性质：这是系统返回注入与 Compose 首帧的组合期抖动，不是 `showLicenses` 那类真实返回链缺陷
   （A1 的缺陷在 instrument 里不可见，因为 instrumented 宿主不走 `AppScreen` 的 tab 恢复路径）。
   下一棒若要根治抖动，方向是给 `pressSystemBack()` 后的等待加**可观测的中间态**，而不是把 5 秒放宽——
   本轮按任务书纪律「不改断言」，未动。

## 4. Git 状态

- 分支：`stage-5-ui-r2-learning-path`；开工 HEAD `7dde8b1`（批 C-7 报告补记），
  与 `origin/stage-5-ui-r2-learning-path` **零枚差**（`git rev-list --count origin/<branch>..HEAD` = 0，
  即批 C 的 7 枚在上一会话已推送，项目记忆里「未推送」的账已过时）。
- 本轮改动（分两枚，按功能域）：
  - `3b02445` `test(ui)`：`app/src/androidTest/.../WorksheetPreviewScreenTest.kt`（M，+1 顶档 48dp 护栏）
    + `app/src/androidTest/.../WorksheetPreviewColorEvidenceTest.kt`（新，3 条取色核对），2 files / +388
  - 第二枚 `docs(handoff)`：`docs/verification/visual-walkthrough-2026-11-08.md`（新，35 格走查册）
    + `docs/handoff/2026-11-08-qa-round3-report.md`（新，本文件）
- **生产码零改动**：`git status --porcelain app/src/main` 为空；变异探针的两处 Edit 已逐条反向撤销并核验。
- 证据一律在忽略区 `.git/qa-scratch/qa3/`（105 PNG + dump + 27 份日志 + 走查脚本），不入库。
- 推送：本轮两枚提交后本地领先远端 2 枚（重算：`git rev-list --count origin/stage-5-ui-r2-learning-path..HEAD`），
  **待用户点头再推**（沿用逐批请示口径）。

## 5. 未完成 / 遗留问题（对任务书逐条应答）

**§1 AC5-03 TalkBack 六链 + 词卡揭晓焦点专项** → **用户裁定不做**（原话「这个先不用做，我感觉没什么意义」）。

**§2 展示矩阵**

- 动态字体最大档核心路径不破版 → **不通过（已取证）**：3.0 七处，其中 A2.5 词卡「显示释义」不可达且不可滚
  = 流程断裂，按 spec 05:29 判不达标；处置需用户拍板（立缺陷修 / 认下「顶档不承诺」并改 spec 口径）。
  既有 connected 顶档护栏只覆盖 `fontScale = 2f`（收工前重算 `grep -rho "fontScale = [0-9]f" app/src/androidTest | sort | uniq -c`
  = `1f` 8 处、`2f` 24 处、`3f` 1 处；那唯一一处 3f 是本轮新增的 `WorksheetPreviewScreenTest:167`，另加
  `WorksheetPreviewColorEvidenceTest:106` 以 `3f` 入参跑顶档），3.0 档此前**没有任何自动化护栏**。
- 深色全页面 + D-2 复测 → **模拟器侧通过**：8 站取样均为 token 化深底 + 可读墨色，未见「白底压浅墨」；
  目视半边留走查册 A4，真机 `bf353dda` 未复测。
- 小屏 320dp → **通过**：8 站（`wm size 840x1600` @420dpi），判据全 0，测完 `wm size` 已复原 Physical。
- 新许可页 TalkBack 朗读 → **用户裁定不做**；但顺带定位到返回键真实缺陷（A1，等裁决）。

**§3 攒批**

- B3 API 26–29 BiometricPrompt 凭据降级分支 → **本轮未取**（不在用户裁定的可自动组内）。
  环境侧具备：本机注册 AVD 含 `xtool-s04-api26`；下一棒可直接接，预估需新建会话级设备时间。
- bf353dda 六项性能复测 → **本轮未做**：真机纪律是「只手工试用」，性能复测属里程碑动作，需用户点头装机。
- 飞行模式手工六条 → **通过（模拟器侧）**：18 张 `off-*.png` + 三份日志；
  任务书 §0/§1 的 `dumpsys connectivity | grep "Airplane mode"` 判据串在本 API 30 镜像不存在，
  改用 `settings get global airplane_mode_on`=1 且 `Active default network: none` 双证；测完已复原。
- B4 恢复页选 `.elpbak`（Salvage 版）包拒收 → **blocked（沿前两轮结论）**：模拟器 documentsui 文件选择器
  对 `adb input` 完全无响应（skill §5.1 实测），需真机侧试，属真机里程碑范围。
- B5 真实 AI Profile 端到端 → **blocked**：等用户真实 Endpoint + Key，未配置即无从谈起。
- T2 ML Kit 内容级抓包核验 → **blocked**：等用户 CA/可解密环境；用户已拍板项不擅自投入。
- AppScreenTest 8 条 back/导航族 flaky → **通过（归因登记，未改断言）**：见 §3.1。

**§3 阶段 5 F5-03 收口攒批（Task 12 追加）**

- I-8 四控真出声冒烟 / 系统 TTS「只有停止」的可理解性 / 真机 1.25x→1.0x 降速 /
  滑动删除 `customActions(label="删除词书")` 可达性 / 语速切换播报 / 揭晓焦点播报 / 退出路径不停发声
  → **用户裁定不做**（全部落在 TalkBack 与「出声类」）。
- 动态字体顶档不破版（含 3.0 与真机 MIUI 档）→ 3.0 **已取**（七处，§5 上一条）；
  MIUI 字体档需真机 → 走查册 D3。
- 放大药片打架、字母栏实际可达 → **只有真手指能判** → 走查册 A5/D1。
- 两处含字徽标顶档 → **前提失效**：两个锚点本轮实跑 `grep` 核实都已不存在原形状
  （`AiFeatureScreen` 的 44dp 盒现装 22dp `AppGlyph` 图标、dp 不随字号缩放；`SettingsScreen` 全文无定尺盒）。
  实质问题转为 A2.6/A2.7 并已补拍取证，见走查册 F 组。
- 深色走查 D-2 → 模拟器侧通过（同上）。
- 三屏零测试引用族 → **本批收口**：`FavoritesScreen`/`WrongWordsScreen` 已从生产树下线（只剩 ViewModel，
  `ls app/src/main/.../ui/` 实测无这两个 Screen 文件）；`WorksheetPreviewScreen` 现在有两条 connected 判据
  （`WorksheetPreviewScreenTest`、`WorksheetPreviewColorEvidenceTest`），任务书里「`grep` 命中 0 个文件」的
  宣称已不成立，取色读数见 §2。

**本轮新增遗留（不在任务书内，因取证才发现）**

1. A1 许可页返回缺陷（Ready 态唯一缺四件套的二级屏）——等用户裁决改法。
2. A2.5 顶档词卡流程断裂——等用户裁决「立缺陷」还是「改 spec 口径」。
3. 判据盲区两条（被裁节点不越界、Compose 溢出不产生省略号）——已写进走查册，建议下一棒同步进
   `android-connected-qa-loop` skill 的怪癖表。
4. AI 助学页顶档三处（A2.6）与底栏图标越出容器（A2.7）——新入册待拍板。

## 6. 下一任接手须知

1. **先读走查册**：`docs/verification/visual-walkthrough-2026-11-08.md`（35 格）。A 组 12 格是「要不要动码」的裁决，
   B/C/D 组不阻塞开工。走查册不替人眼判，也不替用户拍板。
2. **改 A1 的注意**：`AppScreen.kt:585-586` 有注释警告不要重排 `BackHandler` 声明顺序（后声明者优先级高），
   `:587` 就是那条「不在学习 tab 就回学习 tab」的低优先级处理器。
   补 `showLicenses` 四件套要走 TDD：先写「在许可页按系统返回应落回关于页」的红判据，再动生产码；
   `AppScreenProductionWiringTest` 会盯新 ViewModel 参数的接线。
3. **像素取色两条硬教训**（本轮实测，别重犯）：
   - `captureToImage()` 在 instrumented 宿主里会带上按钮外沿一圈白色 focus indicator（约 1048px，与字号无关），
     「取对比度最大的桶」会量到它而不是标签墨；
   - 未合并树里 `hasText("导出 PDF")` 的 `[0]` 是 **Button 容器**（它合并子节点文案、先序排在前），
     不是内部 `Text`——要取**面积最小**的匹配节点。
   对应的护栏是「标签捕获面严格小于按钮捕获面」（`WorksheetPreviewColorEvidenceTest.kt:133-137`），留着别删：
   它不看取色结果，只看取样面形状，所以「墨 == 底」这类缺陷一定会被它拦住。
4. **判据绿 ≠ 不破版**：`uiautomator` 的 bounds 已被父容器裁成视口交集，被裁节点永不越界；
   Compose 文本溢出不产生省略号。顶档屏一律图证 + 人眼双轨。
5. 设备状态收工已复原并留影（`fs100-99-final-state.png`）：`font_scale=1.0`、`wm size`/`density`
   Physical 1080x1920 @420dpi、主题浅色、飞行模式关、`adb` 非 root（`shell id` = `uid=2000(shell)`）、
   TalkBack 未开启（`enabled_accessibility_services=null`）。
   两条要如实知道的副作用：① 顶档补拍有一趟因 here-doc 路径转义中断，把 `font_scale` 留在 3.0，
   是收工前的状态复核抓回来复原的（§3 #14）——**复原行要放在异常也能走到的位置**，别学这一趟；
   ② 走查本身推进了学习进度（今日页 `2 / 23`、还剩 21 词），演示数据未清（`pm clear` 属破坏性动作，需用户确认）。
6. 门禁口径不变：JVM 单测全绿。本轮收工前**实跑过整仓门禁**（`--rerun-tasks`，
   `assembleDebug` → `testDebugUnitTest` → `compileDebugAndroidTestKotlin`）：
   `BUILD SUCCESSFUL in 5m 33s`，332 份 XML / 2467 条 / 0 失败 / 0 跳过（§3 #15）。
   本轮没新增 JVM 用例（改动只在 `androidTest` 与文档，生产码零改动），所以 2467 就是开工基线；
   下一棒若看到该数下降，先解释再判绿。
7. **`adb` 二进制有两个，PATH 顺序会要命**：`/c/Windows/adb` 是 1.0.26.29 的老货，不认 `unroot`
   （只打印 help 且 exit 1，设备悄悄留在 root 态）；能用的是 `/c/adb/platform-tools/adb.exe`（1.0.41）。
   写命令时**用绝对路径或把目录前置**（`export PATH="/c/adb/platform-tools:$PATH"`），
   追加式 `export PATH="$PATH:…"` 会被前者抢先命中；收尾务必核 `shell id` = `uid=2000(shell)`。
8. 记忆里的「C-4c+C-5 共 7 枚未推送」已过时，实际远端与本地同 tip（§4）。

## 7. 用户确认过的决策

1. TalkBack 朗读链与全部「出声类」专项本轮不做——原话「这个先不用做，我感觉没什么意义」（2026-11-07）。
2. 本轮范围 = 「可自动组 + 目视清单合并」：模拟器能判的我跑，判不了的收成一份走查册（2026-11-07 选项裁定）。
3. 沿用既有口径：分支不合 `main`；推送逐批请示；真机只手工试用（里程碑 `adb install -r`），
   instrumented 只在模拟器；证据与截图不入库；破坏性 git/设备动作先取得确认；
   变异探针撤销禁用 `git checkout --`（会抹掉在途未提交改动）。
