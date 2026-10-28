# 决策记录 · UI 重设计 D7：「轻松档」整体改色与连带重议（2026-10-27）

## 背景

批 1 的配色（2026-10-18 裁决 1–3 定口径、批 1 Task 2 落 token）是按「薄荷品牌 + iOS 高饱和域色」
调的。用户看过真机与设计稿后给出方向：功能入口要有绘画感与层次感，但**整体观感要「轻松、低压力」**，
不要 iOS 那种高饱和撞色。设计稿 `docs/design/2026-10-25-icon-system-design-sheet.html` 因此新增 §4.4
「轻松档对看表」（含 `SKINS.calm`），把同一套 token 名下的另一组取值摆出来对照。

本文钉的是「换到轻松档之后，原裁决里哪些数字与限制仍然成立、哪些必须改」。**逐对实测一律当场复算**
（同一 WCAG 2.x 相对亮度公式，见 2026-10-18 决策文首段），本文不抄「合格」结论；入库后判定完全由
`DesignTokenContractTest` 等契约测试从 `AppColors.kt` 的 hex 字面量现算，本文字节只是决策依据。

## 候选方案

**议题一 · 中性 18 枚要不要动。** 现状 `bg #F1FBF5` / `tint #DDF7E8` / `outline #ADE7D2` 三级都泛黄绿，
`text1 #1C1C1E` 是纯黑白，域色又是 iOS 亮彩——三件事叠在一起才是「压力感」的来源。
- A. 只降域色饱和、中性不动 —— 否。泛黄绿的页面底才是最大面积，改了域色仍会觉得「闷」。
- B. 中性整体去饱和、背景去黄绿偏中性灰绿（`#F6F9F6`），`tint` 系由 `#DDF7E8` 降到 `#E9F4EE`，
  文字墨从纯黑 `#1C1C1E` 改为带绿的 `#1F2B26`，强档卡底从 `#0C2A22` 抬到 `#1F4739` —— **采纳**（裁决 1）。

**议题二 · 域色怎么柔化而不破对比。** 浅底文字（`tint` 底上的域 `deep`）要求 ≥4.5，白字形压域容器
要求 ≥3:1（非文本）。
- A. 只调 `base`（亮色）不动 `deep` —— 否。`base` 变柔后 `deep` 若不动，域内两级会脱节，且柔化后的
  `base` 与旧 `deep` 明度差过大，渐变跨度过宽。
- B. `base` 与 `deep` 成对柔化、`deep` 同时再压深一档，深色套（deep=base）另给一组更暗的柔化亮色
  —— **采纳**（裁决 2）。实测：域 `deep` 压 `tint` 4.65–6.88 全过；白字形压浅 `deep` 实底 5.24–7.75；
  深套域色压 `tint` 配 `onBrand` 6.75–10.69。

**议题三 · `text2` 的两条禁配还留不留。** 2026-10-18 裁决 2 禁 `text2` 压 `tint2`/`tint3`（当时 4.15/4.10）。
轻松档 `tint2`/`tint3` 变浅（`#E4EFE9`/`#DEEAE4`），`text2` 也微调为 `#566B62`。
- A. 保留禁配条款 —— 否。实测已全部达标，留着等于让契约测试钉一条不成立的禁令（写测试时会立刻红）。
- B. 解除禁配，翻成正向钉：`text2` 压 `surface`/`bg`/`tint`/`tint2`/`tint3` 五档全过
  （5.71/5.39/5.07/4.85/4.62）—— **采纳**（裁决 3）。
  `text3 #7C9188` 的「非正文角色」（裁决 1）不动：压三档 3.35/3.16/2.98，仍不足 4.5。

**议题四 · 渐变亮端跨度。** `IconBadge.GRADIENT_START_FRACTION` 原为 0.35（`deep` 混 35% `base`）。
设计稿 §4.4 的目标是「近乎纯色的域色容器 + 一点内高光」。
- A. 保持 0.35 —— 否。跨度大，40dp 容器看着像渐变块，本身就在加视觉重量，与「轻松」相反。
- B. 降到 **0.18** —— **采纳**（裁决 4）。白字形压亮端实测 4.28–6.20（最差 AI `#298970`），
  仍远高于非文本 3:1；0.25 时最低 4.00、0.35 时最低 3.61，说明下调不会破图形下限。

**议题五 · M3 `onErrorContainer` 怎么定。** 浅套 `errorFill #FF3B30` 是亮红实底。
实测：`text1` 4.13（不足 4.5）、`onBrand #04241C` 4.65、`onMint` 2.75、白 3.55；深套 `#FF6B61` 上
`background #111A16` 6.36、`text1` 2.44。
- A. 两套统一取 `onBrand` —— 否。深套 `#FF6B61` 比浅套更亮，压 `onBrand` 的余量薄，且深色主题里
  浅色填充上用深墨在观感上突兀。
- B. 按模式分叉：浅套 `onBrand`、深套 `background` —— **采纳**（裁决 5）。这是 M3 角色表里唯一
  按模式分叉的一项，装配点与判据都写明在 `MintGlassTheme.appColorScheme` 与
  `MaterialThemeAssemblyContractTest` 里，避免下一个人以为漏了。

**议题六 · `GlassButton` 的浅套域墨例外分支留不留。** 该分支让浅套浅玻璃上的域墨退到域 `deep`
而不是 `background`（压合成底 `#FEFFFE` 时 `AiSpeech.deep` 5.227 vs 页底 5.24）。
- A. 删掉分支、统一走 `background` —— 否。两值差 0.013，删分支换不来可观测收益，却要改一处
  「全房一致」的既有约定。
- B. 保留分支，理由从「对比度临界」改成「全房取墨一致 + 余量」—— **采纳**（裁决 6）。

## 决定

1. **中性 18 枚整体换到轻松档**（浅套）：`background #F6F9F6`、`surface #FFFFFF`、`surfaceGlass #EFFFFFFF`、
   `tint #E9F4EE`、`tint2 #E4EFE9`、`tint3 #DEEAE4`、`outline #D3E7DC`、`outlineSoft #EBF1ED`、
   `text1 #1F2B26`、`text2 #566B62`、`text3 #7C9188`、`onMint #174C44`、`onBrand #04241C`、
   `error #B30000`、`errorFill #FF3B30`、`success #1B5E20`、`strongSurface #1F4739`、`onStrong #E9F2EC`；
   深套同步：`background #111A16`、`surface #17201C`、`surfaceGlass #E617201C`、`tint #1B2A25`、
   `tint2 #20302A`、`tint3 #25352E`、`outline #2B3A33`、`outlineSoft #202C26`、`text1 #E9F2EC`、
   `text2 #9FB3AA`、`text3 #7F938A`、`onMint #DFF6EC`、`error #FF8177`、`errorFill #FF6B61`、
   `success #6BD98A`、`strongSurface #22332C`、`onStrong #E9F2EC`。`onBrand` 深浅同值（品牌渐变两套同三段）。
   **品牌渐变 `AppleMintStart/Middle/End/Light/PillEnd` 五枚不动**（spec §2.1「品牌渐变保持现状不动」）。
2. **七域域色成对柔化**：浅套 `AiSpeech #1A7A63/#6CCBAB`、`Learn #175A93/#7BB4E6`、`Review #8A5712/#EBC68B`、
   `Library #65478A/#B7A0D9`、`Reading #9C3B4F/#E297A4`、`Danger #9E2B24/#DD8B81`、`Settings #4A5551/#A6B0AC`
   （前者 `deep`、后者 `base`）；深套 deep=base：`#6FD9B6`/`#84C4EE`/`#EFCB92`/`#C6A9E4`/`#EDA8B4`/`#E39086`/`#B4BEBA`。
   连带：吉祥物眼白改为 `AiMascot.EyeColor = #1A7A63`（原随旧亮色域值）。
3. **解除 2026-10-18 裁决 2 的 `tint2`/`tint3` 禁配**，改为正向钉：`text2` 压五档全过（5.71/5.39/5.07/4.85/4.62）。
   裁决 1（`text3` 非正文）、裁决 3（品牌渐变文字取 `onBrand`）**继续有效**，取值按新色重算
   （`text3` 压三档 3.35/3.16/2.98；`onBrand` 压渐变末端 6.87、`onMint` 压末端 4.07）。
4. **`GRADIENT_START_FRACTION` 由 0.35 降为 0.18**，由 `IconBadgeContrastContractTest` 钉字面量；
   白字形压亮端 4.28–6.20，浅 `base` 本体仍禁止作白字形底（AI 1.95、复习 1.62）。
5. **M3 `onErrorContainer` 按模式分叉**：浅套 `set.onBrand`（压 `errorFill` 4.65）、深套 `set.background`
   （压 `#FF6B61` 6.36）。浅套不得用 `text1`（4.13 不足），两套都必须由 `MaterialThemeAssemblyContractTest`
   钉到具体 token 名，不写裸色值。
6. **`GlassButton.domainInkOnLightSurface` 的浅套例外分支保留**，理由写明为「与全房取墨一致 + 余量」，
   不再表述为「对比度临界备用路径」。

## 影响

- **取值权威源**：`ui/theme/AppColors.kt`（`AppColorSet` 18 枚 × 2 套 + `DomainColors` 7 × 2 套）。
  `ui/theme/AppPalette.kt` 是浅套静态兼容源，逐值镜像；`ui/theme/MintPalette.kt` 的中性令牌为
  组合期 getter，随 `LocalAppColors` 走，无需改值（只改了 `MintOutline → outlineSoft` 一类的注释口径）。
- **实现同步点**：`ui/components/IconBadge.kt`（0.18）、`ui/theme/MintGlassTheme.kt`（`onErrorContainerInk`）、
  `ui/components/glass/GlassButton.kt`（例外分支注释）、`ui/mascot/AiMascot.kt`（`EyeColor`）、
  `ui/screens/AiProfileSettingsScreen.kt` 与 `ui/screens/ScreenUnavailable.kt`（KDoc 实测值）、
  `ui/components/AppScreen.kt`（`tint3` 注释）。
- **契约测试同步点**（这些测试钉的是「当前色 + 当场复算」，改色必然连带）：
  `DesignTokenContractTest`（hex 钉值表）、`MintTokenIsComposableContractTest`（4 枚 `AppleMint*` + `MintTint`）、
  `IconBadgeContrastContractTest`（0.18 字面量 + 渐变 alpha 端点 `0x801A7A63`/`0xFF6CCBAB` + 不透明断言）、
  `GlassPanelInkPairingContractTest`（诱饵改 `text3` / `#1E987C`）、`MaterialThemeAssemblyContractTest`（见裁决 5 + 两条诱饵）、
  `AppBottomBarContrastTest`、`InlineNoticeKindTest`、`NotConfiguredCardContractTest`、
  `PillButtonInkTest`、`AiBadgeContrastFixTest`。诱饵角色的选择原则：**诱饵必须是「看着像对的、实测不过」的那个**
  （如域 `base`、`text3`、`#1E987C`），不能是随手换个数。
- **文档同步点**：`docs/design/visual-style-guide.md` §2.2（token 表整表重写并注明权威源）、
  §2.3（域色表 + 白字形/亮端实测 + 解除禁配的段落）、§2.5（深浅对照表 + 裁决 5 说明）、
  §12（容器口径 0.18、三层结构口径）；`docs/design/2026-10-25-icon-system-design-sheet.html` §4.4 为设计侧来源。
- **命名口径**：图标结构在源码两侧文档里原写「两层」（2026-10-18 时点确实只有两层），2026-10-19 扩出
  `accentPaths` 后已统一为「三层」——豁免表标题为「三层结构豁免表」、逐枚清单为「逐枚清单」，
  `AppIcons.kt` 与 `AppIconsStructureContractTest` 必须同名（改词须两边一起改，测试会拦）。
  本文不改豁免成员（仍是 `Back`/`ChevronRight`/`ChevronDown`/`Close`/`Check` 五枚）。
- **重算命令**（本机勿用 `./gradlew`，gradle 不在 PATH）：
  `C:\Users\steveliuyan\.gradle\wrapper\dists\gradle-8.13-bin\5xuhj0ry160q40clulazy9h7d\gradle-8.13\bin\gradle.bat :app:testDebugUnitTest --offline --no-daemon`。
  2026-10-27 收口时全量 **1819 tests / 0 failures / 248 classes**，`:app:assembleDebug` BUILD SUCCESSFUL。
- **仍未拍定的三件（不影响本文生效，留待真机看）**：D1 图标强调层取「域 base」还是「取墨」；
  D3 今日计划两行是否升到 40dp 徽标；D6 深套强调层与骨架同为 `onBrand` 是否糊成一块。
  这三条属「图像呈现」，改的是 `buildAppImageVector` 的色参数与容器档位，不涉及 hex 取值。
- **不在本裁决范围**：真机/模拟器核验（AC-R-11，本机无设备）；深套 `onBrand` 的观感复核；
  `docs/design/2026-10-17-ui-redesign-mockups.html` 等历史稿仍是旧色——它们是**历史留档**，不回改，
  读它们时以 `AppColors.kt` 与本文为准。
