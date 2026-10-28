package com.example.englishlearning.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * 应用薄荷色板的唯一权威源（原先每个屏各自私有重声明同一批值，F1-03 会再加第三份副本；
 * 只活在一个屏里的颜色对全站不可见，于是本应一致的屏之间会慢慢长出细微 hex 差异）。
 *
 * ## 七个中性令牌：组合期读取（Task 3，深色生效的主切换）
 *
 * 它们不再是 `= AppPalette.X` 的静态值，而是 `@Composable @ReadOnlyComposable get()` 读取
 * Task 2 的 `LocalAppColors` / `LocalDomainColors`。中性令牌读点全在组合期内，自动获得深色取值；
 * 组合期之外的读取（`Canvas` 的 `DrawScope` lambda、`graphicsLayer`/`Painter` lambda、
 * 返回 `Color` 的非组合辅助函数）需就地提读，口径见计划 Task 3 Step 4。`MintTint` 原先的裸 hex
 * `Color(0xFFDDF7E8)` 也顺手消失，改成读 `tint`（浅套取值本就相同）。深浅两套的实际取值由
 * `AppColors.kt` 的 `AppColorLight` / `AppColorDark` 决定，切换点由 Task 4 的装配注入，
 * 本文件不再持有中性 hex。
 *
 * ## 四个 `AppleMint*` 品牌令牌：保持静态
 *
 * 三处**顶层** `Brush.linearGradient` 在组合期之外求值，读不了组合期 getter，按持有渐变的符号名指代：
 * `AiFeatureGradient`、`AiHeaderGradient`、`AppleMintGradient`（不写 `文件:行号`——行号随改动漂移，
 * 注释却看起来仍然权威；符号名可直接 grep）。把品牌令牌改成 `@Composable get()` 会让这三处
 * 当场编译不过。另外按 spec §2.1「品牌渐变保持现状不动」与决策 2026-10-18 裁决 3，
 * 品牌渐变浅深两套同三段、模式不变（渐变上的文字统一取 `onBrand` #04241C），静态本身也是正确语义。
 *
 * ## 命名陷阱：`MintOutline` 读 `outlineSoft`，不是 `outline`
 *
 * `MintOutline` 名字像 spec §2.1 的 `outline`（轻松档 = `#D3E7DC`），实际承载的是**分隔线**
 * （改值前是 `AppPalette.Separator`），故映射到 `outlineSoft`。若按字面映射到 `outline`，
 * 全站每一条细分隔线会突然变成粗薄荷线。spec 的 `outline`（`#D3E7DC`）**不建 `Mint` 别名**：
 * 它只由新组件（`IconBadge` 描边、卡片主描边）直接读 `appColors().outline`。
 */

internal val MintBackground: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalAppColors.current.background

internal val MintSurface: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalAppColors.current.surface

internal val MintTint: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalAppColors.current.tint

/** AiSpeech 域色：唯一读 `LocalDomainColors` 的中性令牌（改值前取 `DomainColors.AiSpeech.base`）。 */
internal val MintPrimary: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalDomainColors.current.getValue(AppDomain.AiSpeech).base

internal val MintPrimaryDark: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalAppColors.current.text1

/** 分隔线族 → `outlineSoft`（见文件头「命名陷阱」）。 */
internal val MintOutline: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalAppColors.current.outlineSoft

internal val MintTextMuted: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalAppColors.current.text2

/**
 * 启动页 / 锁定页用的品牌渐变三段 + 浅色端点：保持静态 `Color(0x…)`，
 * 原因与三处顶层 `Brush.linearGradient` 站点见文件头。
 */
internal val AppleMintStart: Color = Color(0xFFA8F3C8)
internal val AppleMintMiddle: Color = Color(0xFF5DDFB4)
internal val AppleMintEnd: Color = Color(0xFF35B9B5)
internal val AppleMintLight: Color = Color(0xFFD9FFF0)

/**
 * 品牌**药片**渐变（`BrandMintGradientStops`）的末端，与启动页渐变末端的 [AppleMintEnd] **不是同一枚**：
 * 药片三段是 `Start → Middle → 本枚`，启动页三段是 `Start → Middle → End`，两个渐变视觉不同。
 * Task 26 Step 3（AC-R-02）把这枚裸 hex 从 `components/glass/GlassButton.kt` 移到这里——它属品牌不变族
 * （浅深两套同值，静态度量见文件头），所以落在主题静态源而不是 `AppColorSet`。取值逐字未变（`0xFF65D3B9`）。
 */
internal val AppleMintPillEnd: Color = Color(0xFF65D3B9)
