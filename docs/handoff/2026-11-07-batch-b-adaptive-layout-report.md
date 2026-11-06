# 2026-11-07 批 B（全面自适应布局）工作报告

分支：`stage-5-ui-r2-learning-path`（用户明令不合 main，推送逐批请示）
计划：`docs/superpowers/plans/2026-11-06-motion-and-adaptive-layout.md` §批 B
决策：`docs/decisions/2026-11-06-adaptive-window-layout.md`（含 §10 追加决定与 §10.4/§10.5 实测读数）

## 1. 本次目标

把批 B「全面自适应（含平板/折叠 + 侧边导航）」做完并收口：窗口类别基建与侧栏/两栏版面落地、
connected 断言的竖屏前提按受支持形态逐类处置、手机横屏按用户裁定从产品侧退场，
最后走三连门禁 + 两形态 connected 取证 + 本报告 + 分功能域提交推送。

## 2. 完成了什么

**口径先行（改文档在改码之前，AGENTS.md 硬要求）**

- `docs/specs/00-foundation-and-architecture.md`：非功能要求里那句「适配小屏」扩成 compact / medium /
  wide 三档窗口类别支持矩阵，每档给判定阈值、典型形态、版面承诺；补「进入 medium/wide 的前置是
  `smallestScreenWidthDp ≥ 600`」；补「三档同一套导航语义，只换摆法」；把不做的事写死（含**不做手机横屏版面**）。
- `docs/specs/05-release-quality-and-compliance.md`：F5-02 加「测量形态必须与承诺形态一致」，
  F5-03 的兼容性口径从「小屏」改按窗口类别矩阵。
- `docs/verification/perf-baseline-2026-10.md`：表头加形态限定块——现有阈值只在竖屏紧凑档成立，
  宽窗未重采前如实记「该形态无基线」。
- `docs/decisions/2026-11-06-adaptive-window-layout.md`：五条决定（不引 material3-window-size-class、
  判定面是内容宽度、高度不等于类别、侧栏是底栏的换摆法、宽档才降 hero）+ §8 基线读数 +
  §10「手机锁竖屏、大屏才自适应」追加决定与其 §10.4 处置、§10.5 不承诺清单。

**代码（三层：判定 → 摆法 → 方向）**

- 新增 `ui/AppWindowLayout.kt`：`AppWindowLayout` 宿主下发令牌 + `AppWindowLayoutPolicy` 纯函数分档
  （600/840 宽门、480 高门、720 内容列上限）+ `LocalAppWindowLayout` / `currentAppWindowLayout()`
  回落系统配置宽度。判定权收在自有纯函数，不依赖 material3 的 WindowSizeClass。
- 新增 `ui/AppSideNav.kt`：宽档侧边导航（与底栏同目的地、同 testTag、同选中语义）。
- `ui/AppScreen.kt`：tab 宿主层用 `BoxWithConstraints` 判一次类别并下发；底栏 ↔ 侧栏换摆法；
  内容列收 720dp 上限；慢加载屏的两栏资格按窗口类别分支。
- `ui/AppBottomBar.kt`：紧凑档保持底栏，其余档位交宿主决定。
- `ui/components/AppScreenHeader.kt`：`hero` 在宽窗降级为常规 `Title`（降级判定只读宿主令牌，组件不自量宽度）。
- 新增 `AppOrientation.kt`：`AppOrientationPolicy.applyTo(activity)` 读 `allow_landscape_layout` 资源 bool，
  手机档设 `SCREEN_ORIENTATION_PORTRAIT`、大屏档设 `SCREEN_ORIENTATION_UNSPECIFIED`；
  在 `MainActivity` 与 `launch/BrandLaunchActivity` 的 `onCreate` 各调用一次。
  Manifest 里 `screenOrientation` 与 `configChanges` 均为零处（跨过 600dp 阈值按系统默认重建 Activity）。
- 新增资源限定：`res/values/config.xml`（`allow_landscape_layout = false`）与
  `res/values-sw600dp/config.xml`（`= true`）。

**测试**

- 新增 JVM 契约：`ui/AppWindowLayoutContractTest.kt`（8 枚，穷举三档分档 + hero 降级只在宽档 +
  响亮守卫「页头 hero 降级判定恰一处」）、`AppOrientationPolicyContractTest.kt`（5 枚，含「Manifest 零处
  `screenOrientation`」归零钉）。
- 新增 connected：`androidTest/.../ui/AppAdaptiveLayoutTest.kt`（8 枚，紧凑/平板形两形态下的侧栏在场、
  内容列上限、hero 档位）。
- 改 connected 判据：`AppScreenHeaderTest`、`NotConfiguredCardTest` 的「必须长出第二行」改成形态限定 +
  换成形态无关的整段布局硬信号；`AppScreenOverlayContainerTest` 大字号三条加 `assumeTrue` 设备前置、
  `assertScaleTookEffect` 哨兵阈值从裸 px 改 dp 口径。
- 改既有契约：`AccessibilityStateSemanticsContractTest` 加「侧栏与底栏同 role/同选中态」1 枚、
  `AppBottomBarContractTest` 随档位口径调整。
- 一次性探针 `TmpHeaderWidthProbeTest` 跑完即删，不入库。

## 3. 验证记录

**三连门禁（本机 gradle 8.13-bin；`testDebugUnitTest` 用 `--rerun` 强制重跑，非 UP-TO-DATE）**

- `:app:assembleDebug`（BUILD SUCCESSFUL）→ `:app:testDebugUnitTest --rerun` → `:app:compileDebugAndroidTestKotlin`。
- 现算：`python .git/qa-scratch/tally.py .git/qa-scratch/gate-1107d.log` →
  `classes=324 tests=2398 failures=0 errors=0 skipped=0`；结果 XML mtime 02:26:55 全量刷新为现证。
- 对照式：2398 = 批 A 基线 2384 + 14（新增 `AppWindowLayoutContractTest` 8 + `AppOrientationPolicyContractTest` 5
  + `AccessibilityStateSemanticsContractTest` 侧栏 parity 1）。0 失败 0 跳过，基线只增不减。
- 中途一次门禁红：`AppWindowLayoutContractTest` 报 `页头的 hero 降级判定必须恰一处 expected:<1> but was:<0>` ——
  根因是撤销变异探针时用 `git checkout --` 连带抹掉了未提交的批 B 改动（见 §5 教训），恢复文件后复跑转绿。
- detekt/ktlint 未跑：本仓验收门禁是单测（`docs/decisions/2026-09-19-static-analysis-and-test-baseline.md`，
  存量债 1500+ 条非回归）。
- `bash scripts/secret-scan.sh` → exit 0。

**connected（emulator-5554，`adb install -r` 双 APK + `am instrument`，未用 `connectedDebugAndroidTest`）**

| 形态 | 造法 | 结果 | 日志（gitignored） |
|---|---|---|---|
| 手机竖屏 Compact 411dp | `wm size reset` + `wm density reset` | `OK (559 tests)` | `.git/qa-scratch/b-b3-compact-full-after.log` |
| 平板形 Wide（sw=800） | `wm size 2560x1600` + `wm density 320` → 1080×728dp | `OK (559 tests)` | `.git/qa-scratch/b-b3-wide-full-after.log` |
| 平板形 Wide（处置前） | 同上 | `Tests run: 559, Failures: 5`（3 枚设备前置 + 2 枚判据写死竖屏） | `.git/qa-scratch/b-b3-wide-full-ui.log` |
| 手机横屏 Medium（已退场形态） | `wm size 1920x1080` | `Tests run: 551, Failures: 35` —— 随 §10.2 从产品侧关闭，不逐条改造 | `.git/qa-scratch/b-baseline-landscape-ui.log` |

559 = 551 + 本批新增 `AppAdaptiveLayoutTest` 8 枚。

大字号双档 × 双形态四格（13 枚，`AppScreenHeaderTest` + `NotConfiguredCardTest` + `AppScreenOverlayContainerTest`）
与变异必红探针（`Tests run: 7, Failures: 2`）读数表已在决策 §10.4 落盘，本处不重复抄：
`.git/qa-scratch/b-b3-compact-fontscale1-skips.log`、`b-b3-compact-fontscale2.log`、`b-b3-wide-after-fix.log`、
`b-b3-wide-fontscale2-dp.log`、`b-b3-mustflag-compact.log`。

**设备侧专项取证**

- 方向锁定：`user_rotation 1` 强转时 `mCurrentAppOrientation=SCREEN_ORIENTATION_PORTRAIT`、显示 `rotation` 仍 0，
  截图 `b-b3-portrait-lock-while-user-rotation-1.png`；验完复原 `user_rotation 0` + `accelerometer_rotation 1`。
- 600dp 阈值跨越：运行中把窗口一次性抬到平板形，`mCurrentFocus` 窗口 token `2b21fa0 → 3b23a3d`（Activity 重建），
  左沿换侧栏、内容列收上限、hero 降级，今日计划读数零丢失（`b-b3-before-600dp-cross.png` / `b-b3-after-600dp-cross.png`）。
- 宽窗版面目视证据：`b-wide-1080dp-side-nav.png`。
- 宽窗页头「裁切」定性为读数假阳性：`layoutInput` 可用宽 1022dp（`weight(1f)` 吃满）、对照组 `fontScale=1.0`
  同样 `overflow=true`、截图 15 字整串一行无裁切（`b-b3-header-probe-wide.png`）；组件侧零改动，判据换成
  `getLineEnd` + 逐行右沿口径。
- 设备状态已复原：`wm size` / `wm density` 回 Physical（1080×1920 @420dpi），`font_scale = 1.0`。

## 4. Git 状态

分支 `stage-5-ui-r2-learning-path`，不合 main（用户令）。批 B 按功能域分五枚提交，前四枚：

| 提交 | 内容 |
|---|---|
| `8ff3469` | 口径先行：Spec 00 矩阵 + Spec 05 + perf-baseline 形态限定 + 决策册 + 计划册 |
| `489c14a` | 自适应版面：`AppWindowLayout` 令牌 / `AppSideNav` / 720dp 内容列 / 宽档 hero 降级 + 契约测试 |
| `991866a` | 方向策略：`AppOrientationPolicy` + `values`/`values-sw600dp` 资源限定 + 两枚 Activity 接线 + 契约测试 |
| `96bf2c6` | connected 取证：`AppAdaptiveLayoutTest` 8 枚 + 三处判据去竖屏几何依赖 |
| 第五枚 | 本报告（哈希不进本文，用下面命令现算远端 tip） |

上一批（批 A）远端 tip 起点：`827ebca`。`.git/qa-scratch/` 与 `.superpowers/sdd/` 全部 gitignored，截图与 log 不入库。
推送与核验（未 `ls-remote` 对上哈希不得宣称已备份）：

```bash
git rev-list --count origin/stage-5-ui-r2-learning-path..HEAD   # 推送前=4，推完=0
git -c credential.interactive=false ls-remote origin stage-5-ui-r2-learning-path
git rev-parse HEAD                                              # 两者哈希须一致
```

**本批未推送成功（异地备份未成立，收工时的实际状态）**：两次 `git push` 都回
`Failed to connect to github.com port 443 via 127.0.0.1`，`netstat | grep -c ':3067'` = 0 ——
git 全局代理 `http://127.0.0.1:3067` 指向的客户端当时没在监听，直连 443 也不通。
本批提交（上表五枚 + 这条补记所在的一枚）**全部留在本地分支上**，未做任何破坏性处置；
本地领先枚数现算 `git rev-list --count origin/stage-5-ui-r2-learning-path..HEAD`，别引用本文写死的数。
**代理恢复后重推并逐字核验哈希，才算备份完成。**

## 5. 未完成 / 遗留

- **本批 GitHub 异地备份未完成**：代理端口 `127.0.0.1:3067` 无监听（见 §4），本批提交全在本地。
  下一任接手第一件事就是 `push` + `ls-remote` 核对，未核对前不得宣称已备份。
- **真折叠屏内屏铰链形态未做设备验证**：本批改的是跨过 600dp 阈值的代码路径（重建后版面与数据），
  铰链传感与厂商分屏策略仍无法用现有 AVD 取证。
- **宽窗口性能基线未重采**：`docs/verification/perf-baseline-2026-10.md` 现记为「该形态无基线」，
  重采时要把 `wm size` + `wm density` 同层级登记，否则读数无法复算。
- **两栏版面按 flow 逐个接入**：未接入的屏在宽档保持上限宽度内单列，本批不承诺全部屏两栏。
- **用户目视（待做）**：① 平板/宽窗侧栏 + 720dp 内容列的实际观感；② 宽档 hero 降级为 24sp→Title 的层级读感；
  ③ 侧栏选中态与朗读（TalkBack）；④ 手机竖屏回归确认方向锁定后无异常。累计目视项仍挂在项目记忆
  `project-roadmap-state` 那一条上，本批新增以上四项。
- **教训（写死在这里，避免重演）**：撤销变异探针**不得用 `git checkout -- <文件>`** ——
  本批 `AppScreenHeader.kt` 的批 B 改动尚未提交，checkout 直接把它抹掉，靠 `.git/qa-scratch/mut/*.bak*`
  才复原（该文件是 CRLF，复原须按原换行写回）。探针回滚一律「反向 Edit」，或先把在途改动 stash/提交。
- 批 C（预加载特效）、批 D（页面转场）未开工；`docs/handoff/2026-10-15-qa-round3-task.md` 那份走查任务书至今未执行，
  批 D 需要它的取证通道。

## 6. 下一任接手须知

1. 接手第一步：读 `docs/decisions/2026-11-06-adaptive-window-layout.md` §10（含 §10.4 处置与 §10.5 不承诺）
   与 `docs/specs/00-foundation-and-architecture.md` 的窗口类别矩阵；台账 `.superpowers/sdd/2026-11-06-motion-and-adaptive/progress.md`。
2. 窗口类别只由 tab 宿主判一次并经 `AppWindowLayout` 下发；**组件内不要自量宽度**，也不要给
   `AppScreenHeader` 的豁免容器换 `EntryIconBadge`（会运行时抛 `require`）。
3. 方向策略的唯一落点是资源限定符 + `AppOrientationPolicy`；Manifest 里出现 `android:screenOrientation`
   就会红（`AppOrientationPolicyContractTest` 钉零）。
4. 不要手动接 `AppMotion` 的时长换算入口：平台自己吃 `animator_duration_scale`，接了就是双重缩放（批 A 已删）。
5. connected 跑法：`adb install -r` 双 APK + `am instrument -e package com.example.englishlearning.ui`，
   形态靠 `wm size`/`wm density` 造，跑完记得 `reset`；大字号那三条的前置写在用例里（`assumeTrue`），
   要全绿就 `adb shell settings put system font_scale 2.0`，验完回 1.0。
6. 本机 gradle 路径含哈希目录，发现命令要用 `*/`：
   `GRADLE=$(ls -1 "$HOME/.gradle/wrapper/dists/gradle-8.13-bin/"*/gradle-8.13/bin/gradle.bat | head -1)`；
   技能里那行少写哈希层会在部分机器上取不到路径（本会话实测踩过一次）。
7. 判据改写的口径：能用形态无关信号（`getLineEnd`、逐行右沿）就别用 `hasVisualOverflow`——
   单行 CJK 在 `LocalDensity` 覆盖下它恒真，不是丢字证据。

## 7. 用户确认过的决策

- 「全部都做，然后横屏也要做适配，就是不是要硬横过来哦」——要真自适应版面，不是允许旋转。
- 批 B 野心＝「全面自适应（含平板/折叠 + 侧边导航）」；批序 批 A 基建 → 批 B 自适应 → 批 C 预加载 → 批 D 转场。
- **手机锁竖屏，大屏才自适应**（本轮触发批 B 范围收窄的裁定）：`sw < 600dp` 一律竖屏，
  手机横屏那 32 枚红随形态退场、不逐条改造，产品侧如实记为「不做手机横屏版面」而不是「已修复」。
- 分支不合 main，推送逐批请示。
