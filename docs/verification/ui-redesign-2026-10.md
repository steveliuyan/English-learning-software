# UI 重设计 · 批 1 验收证据（AC-R）

分支 `stage-5-ui-r1-design-system`。本文件是批 1（设计系统：令牌、图标、共享组件）的可核验证据表。

**口径**：
- 凡「共 N 处 / 多少枚」一律写成**可重算命令**，不在文档里固定绝对数（计划 §Global Constraints #16）。
  表内出现的当次实跑数字只作「本次输出快照」，判定以命令为准。
- 计数与用例数一律由命令现场产出；本文件不引用其它文档的陈旧计数。
- 截图、API Key、Keystore 材料、挑战码明文一律不入库（与 QR 证据同红线）。

## 门禁与基线（每次收口重跑）

重算命令（离线、无守护进程、无构建缓存）：

```bash
cd /h/EnglishLearningSoftware
rm -rf app/build/test-results/testDebugUnitTest
V=$(grep -oE 'gradle-[0-9]+\.[0-9]+' gradle/wrapper/gradle-wrapper.properties | head -1)
GRADLE=$(ls -1 "$HOME/.gradle/wrapper/dists/$V-bin/"*/"$V/bin/gradle.bat" | head -1)
"$GRADLE" :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest \
  :app:compileDebugAndroidTestKotlin verifyThirdPartyNotices --offline --no-daemon --no-build-cache \
  2>&1 | grep -E 'FAILED|^e:|BUILD|tests completed' ; exit ${PIPESTATUS[0]}
grep -h -o 'tests="[0-9]*" skipped="[0-9]*" failures="[0-9]*" errors="[0-9]*"' \
  app/build/test-results/testDebugUnitTest/TEST-*.xml \
  | awk -F'"' '{t+=$2;s+=$4;f+=$6;e+=$8} END{print "suites="NR" tests="t" skipped="s" failures="f" errors="e}'
git diff --name-only main..HEAD | grep -c lockfile   # 期望 0：依赖锁零变化
```

本次实跑输出（Task 12 修复轮 1 收口，`BUILD SUCCESSFUL`）：`suites=223 tests=1646 skipped=0 failures=0 errors=0`，
lockfile 变化数 `0`。

connected 侧只在 `emulator-5574` 上按类跑（**绝禁 `connectedDebugAndroidTest`**，它会清设备数据）：

```bash
export ANDROID_SERIAL=emulator-5574
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e class com.example.englishlearning.ui.<类名> \
  com.example.englishlearning.test/androidx.test.runner.AndroidJUnitRunner
```

2026-10-19 批 1 connected 清单（逐类实跑，合计 **27 用例 0 失败**）：
`AppScreenHeaderTest` OK (4)、`IconGalleryRenderTest` OK (3)、
`EntryRowTest` + `InlineNoticeTest` + `NotConfiguredCardTest` + `ConfirmDestructiveDialogTest` +
`OutboundConfirmDialogTest` OK (18)、`ThemeModeSwitchTest` OK (2)。
跑前先 `dumpsys user | grep State:` 确认 `RUNNING_UNLOCKED`（见「connected 环境债」）。

## 证据表

| AC-R | 口径 | 证据 | 状态 |
|---|---|---|---|
| AC-R-01 | `MaterialTheme` 装配点存在，`AppType` 六档钉值与规格 §2.2 一致（契约测试读常量，非截图） | `DesignTokenContractTest` + `MaterialThemeAssemblyContractTest`（JVM 源码级契约，夹具 `SourceRatchet.kt`） | 通过（本棒门禁 0 失败） |
| AC-R-02 | 全站 `ui/` 布局代码无裸 `Color(0xFF…)` 字面量 | `RawColorLiteralRatchetTest`：新增为 0，存量按 seed 台账「只减不增」，seed 枚数由该测试现场精确集合相等断言复算（清账归批 3） | 通过（新增 0；存量未清零，批 3 收） |
| AC-R-03 | 浅底/品牌渐变上的前景只允许 `on-mint`/`on-brand` 系，对比度按 WCAG 相对亮度计算 ≥4.5:1（图标按非文本 ≥3:1），禁止估 | `DesignTokenContractTest` 的允许配对与禁配表 + `IconBadgeContrastContractTest`（7 域 × 2 档 × 2 主题逐组合复算） | 通过 |
| AC-R-04 | 所有可交互控件高度 ≥48.dp（`heightIn`/`defaultMinSize`，F5-03 口径） | connected：`AppScreenHeaderTest` **OK (4)** + `EntryRowTest`/`InlineNoticeTest`/`NotConfiguredCardTest`/`ConfirmDestructiveDialogTest`/`OutboundConfirmDialogTest` 合计 **OK (18)** + `ThemeModeSwitchTest` **OK (2)**（只跑 `emulator-5574`，命令见上；批 1 connected 合计 **27 用例 0 失败**） | 通过（2026-10-19 复跑，环境根因见「connected 环境债」） |
| AC-R-05 | `verifyThirdPartyNotices` 通过，台账含 `material-symbols-icon-paths`（仅参考度量）、`app-authored-ui-icons`、`app-authored-ui-illustrations` 三条且 8 字段均非空，描述与 `AppIcons.kt` 实际入库内容一致 | `verifyThirdPartyNotices`（门禁内已跑）+ `docs/third-party-notices.md` 三条目；本棒把 `app-authored-ui-icons` 的「用途」字段补齐为含 `ui/components/AppIcons.kt`（路径数据与豁免表所在地）、`AppIcon.kt`（运行时组装）、`IconBadge.kt`（容器呈现）的实际使用位置 | 通过 |
| AC-R-05b | 除决策文档豁免表的 5 枚（Back/ChevronRight/ChevronDown/Close/Check，只描边不建填充层）外每枚两层；豁免枚不得进 ≥40dp 入口容器；清单以决策文档的 33 枚为准；全站 `\bIcons\.` 引用为 0；不新增 `material-icons-extended` | `AppIconsStructureContractTest`（结构 + 豁免表双向钉；禁令以 import 字符串形状钉）+ `SharedComponentUniquenessRatchetTest` 复述 `\bIcons\.` 用法口径 + lockfile 零变化；呈现侧 `IconGalleryRenderTest` connected **OK (3)**（2026-10-19），六张浅/深截图目视清单 ①–⑥ 全部见下表并已判 | JVM 与 connected 均通过；目视清单 ①–⑥ 已放行 |
| AC-R-06 | `AppTabTest` 与既有业务规则测试零修改通过；JVM 基线只增不减（每次重算，不得引用陈旧数） | 门禁命令 + 上面的 awk 重算。零修改核验三条（本次实跑：第 1、2 条为空，第 3 条只列出 `MainActivity.kt`）：<br>`git diff --name-only main..HEAD -- app/src/test/java/com/example/englishlearning/ui/AppTabTest.kt app/src/main/java/com/example/englishlearning/di app/src/main/java/com/example/englishlearning/learning`<br>`git diff --name-only main..HEAD -- 'app/src/test/java/**' \| grep -v '/ui/'`（测试侧改动应全在 `ui/` 下）<br>`git diff --name-only main..HEAD -- 'app/src/main/**' \| grep -v '/ui/'`（生产侧越出 `ui/` 只允许 `MainActivity.kt`，它是主题装配点） | 通过（本次实跑：AppTabTest/di/learning 零改动；测试侧改动全在 `ui/`；生产侧仅 `MainActivity.kt`） |
| AC-R-07 | `fontScale = 2.0` 的 connected 用例覆盖六屏重排后的标题与卡片 | 批 2/批 3 逐屏补（护栏口径沿用 `CheckInScreenTest`） | 未开始 |
| AC-R-08 | §3 每个共享组件有「全站唯一实现」的静态检查；被替换的私有份数降到 1 | `SharedComponentUniquenessRatchetTest`：四族（页头 / 错误提示条 / 未配置卡 / 词行）按形态规则逐族**精确集合相等**，seed 是债务台账（只减不增，接线一屏删一行）；批 1 组件文件按「点名文件必须存在 + 公开组件在 `ui/` 扫描面恰一处顶层定义」钉；预登记豁免（本棘轮不管「就地改既有组件取值面」，Task 14 改 `PillButton` 本体不受挡）由 `the_ratchet_blocks_a_second_component_but_never_an_in_place_value_edit` 双向证明 | 通过（存量私有份未清零，清零归批 2/批 3；见「本族规则形状的已知边界」） |
| AC-R-10 | 六屏 360dp 真机浅/深两套截图逐屏比对设计稿 | 批 2/批 3 逐屏补 | 未开始 |
| AC-R-11 | 玻璃层与域色容器在深色下文字可读性实测 | 批 2/批 3 逐屏补 | 未开始 |
| AC-R-12 | TalkBack 走查重做一遍 | 批 2/批 3 逐屏补 | 未开始 |

## 图标一览目视清单（AC-R-05b 的呈现侧，缺项不得放行）

呈现处只有一个：`app/src/androidTest/java/com/example/englishlearning/ui/IconGalleryRenderTest.kt`
（批 1 不把底导与业务屏换成 `AppGlyph`，真机上看到的仍是旧 Canvas 字形，故图标第一印象只能在这里取）。
三个视图各一个 `@Test`、每个 `@Test` 只 `setContent` 一次，浅色档先落 `ThemeMode.Light` 偏好再装宿主、
深色档只写 `ThemeMode.Dark` 靠 `rememberAppThemeMode()` 活链路重绘，同一次测试内两次 `captureToImage`。

产物写到应用外部文件目录（`getExternalFilesDir`，与 `SetupScreenScreenshotTest` 同处），路径打在
instrumentation 输出（`ICON_GALLERY_SAVED <绝对路径>`）。**API 30 上 shell 直接读 `Android/data` 被政策挡
（`Permission denied`），`adb pull` 取不到**，本仓实测可用的是 `su 0 cat` + 重定向到仓库外临时目录：

```bash
mkdir -p /tmp/ui-icons                      # 必须是仓库外目录
for f in appglyph entrybadge inlinebadge; do for m in light dark; do
  adb -s emulator-5574 exec-out \
    "su 0 cat /sdcard/Android/data/com.example.englishlearning/files/icon-gallery-$f-$m.png" \
    > /tmp/ui-icons/icon-gallery-$f-$m.png
done; done
ls -la /tmp/ui-icons                        # 六张字节数均非 0
```

看完即删，图片一律不入库（`git status` 必须保持干净）。排列口径：按 `AppIcon` 枚举序左到右、上到下，
每行 8 枚（枚数与清单本身由 `AppIconsStructureContractTest` 与决策文档钉，本文件不写绝对数）。

| # | 目视项 | 判据 | 本轮结论（2026-10-19，六张齐） |
|---|---|---|---|
| ① | `Spark` 伴星补回尾段后是四芒 | 第 2 行第 4 枚，不得读成带竖直尖刺的坏形 | 通过：浅/深两张都是「大四芒 + 右下一枚小四芒」，无竖直尖刺 |
| ② | `Book`（合上）与 `BookOpen`（摊开）在 24dp 下可区分 | 第 1 行第 7、8 枚；`Book` 左半填充不得与 `BookOpen` 左页填充读成同一枚 | 通过：`BookOpen` 读到中缝与两页外扩，`Book` 读到封面 + 书脊一条竖线，24dp 下不混淆 |
| ③ | `Camera` 镜头环在 17dp 行内不与机身墨迹糊成一团 | 第 3 行第 5 枚，看 InlineIconBadge 视图 | 通过：17dp 下机身、取景凸台、镜头环三者分离，环内留白未被填充填满 |
| ④ | `Trash` 桶身填充与骨架的同心差；`Spark`/`Download` 笔画出安全区后在容器内不裁 | `Trash` 第 4 行第 7 枚、`Download` 第 5 行第 1 枚，看 EntryIconBadge 视图 | 通过：`Trash` 填充明显内缩于骨架；`Download` 在容器内四边留白，`Spark` 伴星未被容器切到 |
| ⑤ | 豁免 5 枚（Back/ChevronRight/ChevronDown/Close/Check）在 17dp 与 24dp 下均可辨 | 它们只出现在 AppGlyph 与 InlineIconBadge 两个视图（EntryIconBadge 的 `require` 拦死，跳过关系由该用例的结构等式断言钉住） | 通过：两视图 5 枚齐全且形态互不混淆；EntryIconBadge 视图首枚即 `Search`（无豁免枚），与断言一致 |
| ⑥ | 深色档填充层（`DuotoneFillAlpha.Dark`）在深底容器上仍读得出层次 | 对比同一视图浅/深两张图的填充与描边层次 | 通过：三视图深底均铺到主题底色（`appColors().background`，本轮修的证据缺陷），描边与填充分层可读。**观察（非批 1 缺陷）**：域色里的灰色容器在深色下是这套里对比最低的一档，批 2 若把它用于承载正文级信息需回看 `IconBadgeContrastContractTest` 的实算值 |

机器可判的两条（已写进用例，不需要人眼）：非底色像素占比 > 0（防空绘制）；浅/深两轮整图像素哈希不相等
（证明「写 Dark」真的走到了装配点）。connected 读不到颜色，故本类不断言任何具体色值。

## 词行族规则形状的已知边界（Task 12 修复轮 1 的 Fix 3 裁决，正式登记）

- 现场：`ui/GlobalVocabularySearchScreen.kt` 里词行形态有两处，但按「顶层私有 `*Row*` 函数体窗口」的规则**只有一处落窗**
  （`GlobalSearchResultRow`：体内 lemma 词头 Text + 释义 Text）。
- 第二处是 `GlobalSearchHistory` 里一枚**无名内联 `Row { … }`**（写 `item.history.displayQuery` + 「搜索 N 次」），
  它不是顶层私有 `*Row*` 函数，结构上落在窗口规则之外——**规则看不见它，这是本族规则形状的已知边界，不是漏检的 bug 复现**。
- 处置：`SharedComponentUniquenessRatchetTest` 把该文件的落窗数钉成 1，并在 seed 台账里按**文件**计
  （接线时两枚变体在同一行里一并消失）。**不得**为了凑数把窗口放宽成「任何 `Row {`」——
  那会立刻误伤 `CardDetailScreen.kt` 的 `SenseRow` 与 `AppScreen.kt` 的 `DetailToggleRow`（两枚都不是词行），
  使词行族 seed 凭空变大。
- 兑现时机：批 3 接线该屏时，两处一起改调 `WordRow`；届时同步删 seed 行并复核这条边界是否仍在。

## 剥注释夹具的债（Task 12 修复轮 1 的 Fix 1，登记口径）

`app/src/test/java/com/example/englishlearning/ui/SourceRatchet.kt` 的 `stripComments` 现为**字符串感知**扫描实现：
字符串字面量（单行双引号串、三引号原始串、字符字面量）整段保留，只在字符串外剥行注释与块注释。
旧的正则版会把 SAF MIME 字面量里的「斜杠紧跟星号」当块注释起始，一路吞到下一条行尾注释的闭合序列，
把中间几十行真代码从棘轮视野里删掉（现场：`ui/BackupRestoreScreen.kt` 里裸用错误色渲染提示的那一处）。
两条钉：`stripComments_keeps_code_after_a_string_literal_that_contains_a_comment_marker`（合成输入，同时钉「真注释仍被剥」）
与 `notice_family_ledger_pins_the_backup_restore_site_that_comment_stripping_used_to_swallow`（真实文件锚 + seed 台账对齐）。
规则一律未放宽、seed 一律未加注（该文件本就在提示条族的台账里）。

## connected 环境债（曾挡下全部 compose 用例，2026-10-19 已定位并恢复）

修复轮 1 报「所有 compose connected 用例同报一条
`java.lang.RuntimeException: Unable to resolve activity for: Intent { … cmp=com.example.englishlearning.test/androidx.activity.ComponentActivity }`」。
**根因不在代码**：`app/build.gradle.kts` 与 `app/gradle.lockfile` 在本轮区间零改动，
`aapt2 dump xmltree --file AndroidManifest.xml app/build/outputs/apk/debug/app-debug.apk` 现场证明
`androidx.activity.ComponentActivity` 就在**应用包**清单里且 `exported=true`
（`debugImplementation(ui-test-manifest)` 供给的是应用包，测试包清单里 0 处属正常；
测试包 `instrumentation targetPackage` 也确实是 `com.example.englishlearning`）。真因在设备侧：
AVD 的临时 `/data` 镜像元数据加密挂载失败（logcat：`[libfs_mgr] skipping mount due to invalid magic, mountpoint=/data`
→ `vold: fscrypt_unlock_user_key 0 serial=0 token_present=0` → `StorageManagerService: …UserController.unlockUserCleared`），
用户 0 停在 `RUNNING_LOCKED`，于是所有 CE 存储里的包组件一律解析不到——连有 MAIN/LAUNCHER 过滤器的
`BrandLaunchActivity` 也用 `am start -n` 报 `Error type 3 … does not exist`、`monkey` 报 `No activities found to run`，
而 `dumpsys package` 的 Activity Resolver Table 里明明列着它。`sys.boot_completed=1` 照常置位，是假信号。

诊断与处置（已同步补进 `android-connected-qa-loop` 怪癖表）：

```bash
adb -s emulator-5574 shell dumpsys user | grep 'State:'      # 判据：RUNNING_LOCKED 即此症
# 无效尝试（别再走）：keyevent 82 / wm dismiss-keyguard / 上滑 / 唤醒 / 整机重启 / 加减 -read-only
emulator -avd xtool-s04-api30 -no-window -no-audio -no-boot-anim -no-snapshot -wipe-data -port 5574
adb -s emulator-5574 shell dumpsys user | grep 'State:'      # 期望 RUNNING_UNLOCKED
```

`-wipe-data` 在本仓判为非破坏性：五枚 AVD 的 `config.ini` 均写 `disk.dataPartition.path = <temp>`，
数据盘按设计每次开机即弃，无可丢的用户数据；真机 `bf353dda` 全程未碰（只按里程碑装过 debug 包，
未跑 `am instrument`、未装测试包）。恢复后按类实跑见上表 AC-R-04/05b 与门禁节，合计 27 用例 0 失败。

## 仍待用户目视（只有人眼能判的四项，本棒不替用户判定）

① 六档 `AppType` 未写 `lineHeight` 后正文是否偏紧（判「太紧」则触发批 2 在装配点补，并先把取值写进规格 §2.2）；
② 排印换档的整体观感（逐屏看有没有被撑破或挤扁的头部）；
③ M3 控件默认色由库紫转薄荷 + Card 圆角 20→18dp、`colorScheme.outline` 换成 `text2` 是否可接受；
④ 跟随系统的深色档与 `fontScale 2.0` 下底导与设置页不裁。
