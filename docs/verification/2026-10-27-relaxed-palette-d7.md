# D7「轻松档」改色收口验收（2026-10-27）

改色理由与逐条裁决见 `docs/decisions/2026-10-27-relaxed-palette-rulings.md`；
设计侧来源是 `docs/design/2026-10-25-icon-system-design-sheet.html` §4.4「轻松档对看表」。
本文只记**可核验证据**：跑了什么、结果是什么、哪些没跑。

## 结论

- 全量 JVM 单测 **248 套 / 1819 用例 / 0 失败 / 0 错误 / 0 跳过**（下文命令实跑输出）。
- `:app:assembleDebug` 与 `:app:compileDebugAndroidTestKotlin` **BUILD SUCCESSFUL**。
- 真机/模拟器侧**未跑**（本机无设备，AC-R-11 的深浅两套观感核验仍欠着，见文末）。

## 命令（本机 gradle 不在 PATH，勿用 `./gradlew`）

```powershell
$G = "C:\Users\steveliuyan\.gradle\wrapper\dists\gradle-8.13-bin\5xuhj0ry160q40clulazy9h7d\gradle-8.13\bin\gradle.bat"
& $G :app:testDebugUnitTest --offline --no-daemon
& $G :app:assembleDebug :app:compileDebugAndroidTestKotlin --offline --no-daemon
```

用例数从结果 XML 现算（不抄文档里的数）：

```powershell
$x = Get-ChildItem "app\build\test-results\testDebugUnitTest\TEST-*.xml"
$t=0;$f=0;$e=0;$s=0; foreach($i in $x){ $d=[xml](Get-Content $i.FullName -Encoding UTF8)
  $t+=[int]$d.testsuite.tests; $f+=[int]$d.testsuite.failures; $e+=[int]$d.testsuite.errors; $s+=[int]$d.testsuite.skipped }
"suites=$($x.Count) tests=$t skipped=$s failures=$f errors=$e"
```

本次输出快照：`suites=248 tests=1819 skipped=0 failures=0 errors=0`。
该行同时把 `docs/verification/ui-redesign-2026-10.md` 的基线从「批 3：241 类 / 1788 用例」抬到
「D7：248 类 / 1819 用例」（只增不准减）。

## 对比度证据怎么复算

文档与 KDoc 里出现的比值都按 WCAG 2.x 相对亮度当场算，公式与 2026-10-18 决策文首段一致。
现场核一对：

```bash
awk 'function lin(v){v=v/255; return v<=0.03928 ? v/12.92 : ((v+0.055)/1.055)^2.4}
     function lum(h,r,g,b){r=strtonum("0x" substr(h,1,2));g=strtonum("0x" substr(h,3,2));b=strtonum("0x" substr(h,5,2));
       return 0.2126*lin(r)+0.7152*lin(g)+0.0722*lin(b)}
     function ratio(a,b,La,Lb,t){La=lum(a);Lb=lum(b);if(La<Lb){t=La;La=Lb;Lb=t}return (La+0.05)/(Lb+0.05)}
     BEGIN{printf "onBrand/gradient-end=%.3f\nonMint/tint=%.3f\ntext2/tint3=%.3f\n", ratio("04241C","35B9B5"), ratio("174C44","E9F4EE"), ratio("566B62","DEEAE4")}'
```

判定不靠本文数字：`DesignTokenContractTest` 从 `AppColors.kt` 的字面量现算正文 ≥4.5 / 图形 ≥3.0，
`IconBadgeContrastContractTest` 现算七域白字形压渐变亮端，`MaterialThemeAssemblyContractTest` 现算
`onErrorContainer` 两套。

## 本轮实际核过的关键配对（轻松档取值）

浅套：

| 配对 | 实测 | 门槛 |
| --- | --- | --- |
| `text1 #1F2B26` 压 `background #F6F9F6` | 13.83 | 4.5 |
| `text2 #566B62` 压 `surface` / `bg` / `tint` / `tint2` / `tint3` | 5.71 / 5.39 / 5.07 / 4.85 / 4.62 | 4.5 |
| `text3 #7C9188` 压 `surface` / `bg` / `tint` | 3.35 / 3.16 / 2.98（仍非正文） | 4.5 / 3.0 |
| `onMint #174C44` 压 `tint #E9F4EE` | 8.65 | 4.5 |
| `onBrand #04241C` 压品牌渐变末端 `#35B9B5` | 6.87 | 4.5 |
| `onMint` 压渐变末端 | 4.07（故渐变上用 `onBrand`） | 4.5 |
| 白字形压七域浅 `deep` 实底 | 5.24 ~ 7.75 | 3.0 |
| 白字形压七域渐变亮端（0.18） | 4.28 ~ 6.20（最差 AI `#298970`） | 3.0 |
| 白字形压七域浅 `base` 本体（禁配） | 1.43 ~ 2.31（AI 1.95 / 复习 1.62） | 3.0 |
| 域 `deep` 压 `tint`（浅底域文字） | 4.65 ~ 6.88 | 4.5 |
| `text1` 压 `errorFill #FF3B30`（不可用） | 4.13 | 4.5 |
| `onBrand` 压 `errorFill #FF3B30` | 4.65 | 4.5 |

深套：

| 配对 | 实测 | 门槛 |
| --- | --- | --- |
| `text1 #E9F2EC` 压 `background #111A16` | 15.53 | 4.5 |
| `text2 #9FB3AA` 压 `surface` / `bg` | 7.54 / 8.03 | 4.5 |
| `text3 #7F938A` 压 `surface` | 5.11 | 4.5 |
| `onMint #DFF6EC` 压 `tint #1B2A25` | 13.19 | 4.5 |
| `onBrand #04241C` 压七域深套域色 | 6.75 ~ 10.69（最差 AI） | 3.0 |
| `background #111A16` 压 `errorFill #FF6B61` | 6.36 | 4.5 |
| `text1` 压 `errorFill #FF6B61`（不可用） | 2.44 | 4.5 |

玻璃合成底：浅 `surfaceGlass #EFFFFFFF` src-over `#F6F9F6` = `#FEFFFE`；深套压 `#111A16` = `#161F1B`。
浅套域墨例外分支的实测：`AiSpeech.deep #1A7A63` 压 `#FEFFFE` 5.227、压页底 5.24（差 0.013，故保留分支）；
诱饵 `#1E987C` 压同底 3.589（不合格，正是它被选为诱饵的原因）。

## 改动清单（本轮）

实现：

- `ui/theme/AppColors.kt`：18 枚中性 × 2 套 + 7 域 × 2 套全部换成轻松档；文件头写明权威源与「不抄对比结论」的理由。
- `ui/theme/AppPalette.kt`：浅套静态镜像 + `DomainColors` 同步。
- `ui/components/IconBadge.kt`：`GRADIENT_START_FRACTION` 0.35 → 0.18，并注释「近乎纯色」的依据。
- `ui/theme/MintGlassTheme.kt`：`onErrorContainerInk` 浅套改 `set.onBrand`、深套 `set.background`。
- `ui/components/glass/GlassButton.kt`：浅套域墨例外分支保留，理由改述为「全房一致 + 余量」。
- `ui/mascot/AiMascot.kt`：`EyeColor` 随域色改 `0xFF1A7A63`（KDoc 已写明「刻意不引用 token」）。
- `ui/theme/MintPalette.kt` / `ui/components/AppScreen.kt` / `ui/screens/AiProfileSettingsScreen.kt` /
  `ui/screens/ScreenUnavailable.kt`：注释与 KDoc 的实测值、token 名口径同步。
- 图标结构口径「两层」→「三层」：`AppIcons.kt`、`AppScreenHeader.kt`、`InlineNotice.kt`、
  `AiLearningScreen.kt` 的措辞与 `AppIconsStructureContractTest` 的 `EXEMPT_HEADING = "三层结构豁免表"`
  两侧同名。

契约测试（改色必然连带，全部按「当前色 + 当场复算」重钉）：

- `DesignTokenContractTest`：hex 钉值表逐值更新，WCAG 复算门槛不动。
- `IconBadgeContrastContractTest`：新增 `gradientStartFraction = 0.18`，全处 `0.35` 字面量改读常量；
  渐变 alpha 端点改 `0x801A7A63` / `0xFF6CCBAB` 并加不透明断言；新增
  `gradient_start_fraction_is_pinned_to_the_relaxed_palette_value`（源码里恰 1 处 `0.18`）。
- `MaterialThemeAssemblyContractTest`：`onErrorContainerInk` 改 `set.onBrand`，并补两条诱饵
  （浅套 `text1`/`errorFill` <4.5、`onBrand`/`errorFill` ≥4.5）。
- `GlassPanelInkPairingContractTest`：诱饵改 `AppColorLight.text3` 与 `#1E987C`。
- `AppBottomBarContrastTest`：三条腿数字改浅 5.714 / 深 7.543、选中字形 4.651 / 8.741、标签 9.751 / 9.745。
- `InlineNoticeKindTest` / `NotConfiguredCardContractTest`：诱饵由域 `deep` 改域 `base`。
- `PillButtonInkTest`：把「域 `deep` <4.5」改成正向钉（`onMint` > 域 `deep`）+ `base` <4.5 的反向钉。
- `AiBadgeContrastFixTest`：已配置档判据翻成 `configuredRatio >= 4.5`。
- `RawColorLiteralRatchetTest`：见下节。

## 本轮抓到并修掉的一条红

全量跑第一遍时 `RawColorLiteralRatchetTest > no raw hex outside the seed` **红**：

```
AiMascot.kt: private val EyeColor: Color = Color(0xFF1A7A63)
```

原因是 `AiMascot.kt` 的 `EyeColor` 本来就在债务台账里（`RawColorSeedAllowlist` = `"mascot/AiMascot.kt" to 1`），
但**行级 allowlist `RawColorSeedSiteLines` 记的是旧 hex** `0xFF188F76`；改色后「同一债、换字面量」，
计数不用动，行内容必须跟着改。修法是一条行内容改字面量，**不是**加豁免、也不是把 `EyeColor` 改成读 token
（读 token 会让「任何主题下都有对比」这条产品约束失效，文件 KDoc 已写死）。
这条正是该测试文件 KDoc 里说的「行内容随书写形状走，不随债务走」。

## 没做的（不要当成已完成）

- **AC-R-11 真机核验**：本机无设备/模拟器，深浅两套的观感、玻璃合成、渐变亮端在真实屏上的表现均未目视。
  首次上模拟器时按 `docs/verification/ui-redesign-2026-10.md` 的按类 `am instrument` 跑法（**绝禁
  `connectedDebugAndroidTest`**，它清设备数据）。
- **三条呈现待拍项**（改的是 `buildAppImageVector` 色参数与容器档位，不动 hex）：
  D1 图标强调层取「域 base」还是「取墨」；D3 今日计划两行是否升到 40dp 徽标；
  D6 深套强调层与骨架同为 `onBrand` 是否糊成一块。
- **`RawColorLiteralRatchetTest` 的 `BRAND_LAUNCH` 台账**与 `DarkTokenConsumptionRatchetTest` 的 seed
  本轮未动，仍需在下次收口时按各自重算命令复核一遍（本轮全量跑通过，即当次形状成立）。
- 历史稿 `docs/design/2026-10-17-ui-redesign-mockups.html` 仍是旧色：它是留档，不回改；
  读它以 `AppColors.kt` 现值为准。
