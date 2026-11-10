# 2026-11-10 药片渐变归一（品牌渐变只给药片软化，启动页三段不动）

用户当批反馈（m05053 第②④项，boss 口述）：
「有些界面的弹窗不好看，比如说开始学的这个渐变颜色太深了，相对于其他颜色」、
「我的笔记那里也是……那个颜色太深了，就是饱和度很高，相对于其他颜色来说」。

两句话指的是**同一处实现**：`PillButton`（`ui/components/glass/GlassButton.kt`）在
`PillStyle.Primary` + `AppDomain.AiSpeech`（即**默认**组合）时用的品牌薄荷渐变
`BrandMintGradientStops`。「开始学习」CTA（`ui/TodayPlanScreen.kt`，tag
`today_plan_start_learning`）与详情页「我的笔记」区的「添加 / 管理」药片
（`ui/CardDetailScreen.kt`，tag `card_detail_notes_manage`）都走这条默认组合，
所以改一次两端同时见效。

## 1. 问题定量（为什么"相对偏深"）

改前三端是启动页 hero 那三段：`#A8F3C8 → #5DDFB4 → #65D3B9`。

| 端 | hex | HSL | 相对归一后浅套域色 `base`（S48/L61） |
|---|---|---|---|
| start | `#A8F3C8` | H146 S76 L81 | S 高 28 个点 |
| mid | `#5DDFB4` | H160 S67 L62 | S 高 19 个点 |
| pill-end（旧） | `#65D3B9` | H166 S56 L61 | S 高 8 个点 |

2026-11-10 的[域色归一](2026-11-10-domain-color-normalization.md)已把七域收到 S48/L61 同档，
全站只剩这一条渐变还停在 S56–S76。药片只有 48dp 高、底下直接是浅卡／浅底，
**小面积高饱和色块贴浅底**正是「跳出来抢焦」的观感来源——用户口中的「太深了、饱和度很高」。

## 2. 结论（权威取值，已落 `ui/theme/MintPalette.kt`）

药片改用**三枚药片专属令牌**，字段与语义不变（仍是 `BrandMintGradientStops`、仍是
`PillStyle.Primary` + AiSpeech 的 `gradient`、前景仍是 `onBrand`）：

| 端 | 新 hex | HSL | 旧 hex | 旧 HSL |
|---|---|---|---|---|
| `AppleMintPillStart` | `#C1EBD8` | H153 S51 L84 | `#A8F3C8` | H146 S76 L81 |
| `AppleMintPillMiddle` | `#94DBC5` | H161 S50 L72 | `#5DDFB4` | H160 S67 L62 |
| `AppleMintPillEnd` | `#73CEBC` | H168 S48 L63 | `#65D3B9` | H166 S56 L61 |

三条改法约束：色相逐端保留（146/160/166 → 153/161/168，同族同序）、饱和度收到 S48–S51、
明度抬到 L63–L84。**末段 L63 与归一后浅套 `base` 的 L61 同档**，这就是「不再比任何一枚域色深一档」的判据。

## 3. 对比度复算（WCAG 相对亮度，当场算不估）

| 端 | 压 `onBrand #04241C` | 压白字 `#FFFFFF` | 压 `onMint #174C44` |
|---|---|---|---|
| `#C1EBD8` | **12.67:1** | 1.30:1 | 7.50:1 |
| `#94DBC5` | **10.36:1** | 1.59:1 | 6.13:1 |
| `#73CEBC` | **8.85:1** | 1.86:1 | 5.24:1 |

- 正文 AA 4.5:1：三端压 `onBrand` 全部通过（最紧一端 8.85，余量充足）。
- 白字仍然全线不合格（1.30–1.86），所以 §2.1「品牌渐变上禁止白字、前景统一 `on-brand`」这条硬规则**不变**。
- 顺带说明 `onMint` 也合格（5.24–7.50），但药片当前就写 `onBrand`，**本次不换前景**——换前景会让
  「同一枚药片在浅/深两套换字色」多出一条无收益的分支。

## 4. 为什么**不**复用启动页那三段（即为什么不是整族改）

`AppleMintStart/Middle/End` 的消费点不止药片：
`ui/AppScreen.kt:107` 的 `AppleMintGradient`（`BrandLaunchSurface` 启动面、建档封面）、
`ui/AiFeatureScreen.kt:40` 的 `AiFeatureGradient`、`launch/BrandLaunchActivity.kt:42`。
那些是**整屏 hero 面**，深端 `#35B9B5` 的视觉重量是设计意图的一部分（用户在别处没有对它提意见）。
把整族软化会把品牌深端一起抬亮，既越出反馈范围，也会打破
`DesignTokenContractTest`（`on-brand` 压 `#35B9B5` ≥4.5）与
`MintTokenIsComposableContractTest`（四枚静态令牌逐字钉值）这两组既有契约。
所以走「**药片专属三令牌**」这条路，启动页三段逐字不动，爆炸半径最小。

## 5. 为什么**不**把药片改成 `PillStyle.Secondary` / 纯域色

`Secondary` 是 `surface` 底 + `outlineSoft` 描边，视觉上是「次按钮」；
「开始学习」是全站最高优先级的唯一主动作，降级成描边按钮等于改信息层级，超出「调颜色」的反馈。
纯域色填充（`palette.deep`）则丢掉品牌识别——药片渐变的用意就是让主动作一眼是品牌的。

## 6. 被取代的旧条目

- spec §2.1「品牌渐变保持现状不动」（2026-10-27 轻松档批、2026-10-29 原型对齐批两处措辞）：
  **仅对药片失效**，启动页/封面/区头渐变仍适用。spec 已在同段加 2026-11-10 脚注，不删旧句（保留沿革）。
- `PillButtonInkTest` 原注释「三端（`AppleMintStart/Middle` + 裸 hex 末端）」：裸 hex 已在
  Task 26 Step 3 移进主题静态源，本次三端全部令牌化。

## 7. 落地点与测试同步清单（改渐变必然要一起改的钉值）

主代码：
- `ui/theme/MintPalette.kt`：新增 `AppleMintPillStart/Middle/End` 三枚（`internal val`，浅深两套同值，
  属品牌不变族，故落主题静态源而非 `AppColorSet`）；原 `AppleMintPillEnd` 的值就地替换。
- `ui/components/glass/GlassButton.kt`：`BrandMintGradientStops = listOf(AppleMintPillStart, AppleMintPillMiddle, AppleMintPillEnd)`。

钉值测试（已同步，均已跑绿）：
- `app/src/test/.../ui/PillButtonInkTest.kt`：形状锚改为钉 `listOf(AppleMintPillStart, AppleMintPillMiddle, AppleMintPillEnd)`
  表达式，并逐枚钉三行 `val <token>: Color = Color(<hex>)`（比原「只钉末端」更强）。
- `app/src/androidTest/.../ui/glass/PillButtonTest.kt`：像素期望值改 `AppleMintPillStart`/`AppleMintPillEnd`（容差 18 不变）。
- `app/src/androidTest/.../ui/TodayPlanScreenTest.kt`：「开始学习」CTA 的三端期望值改
  `#C1EBD8`/`#94DBC5`/`#73CEBC`；空态「只许一枚品牌渐变」那对断言里，**正向**探针改成三端全查，
  **反向**探针从首端 `#C1EBD8` 改到中段/末端两枚饱和端。起因是设备实测：次出口（`PillStyle.Secondary`）
  的前景是 `onMint #1A7A63` 压白 surface，字形抗锯齿混色落在「白 → 深青」混色线上，其中一点
  (199,223,217) 距新首端 (193,235,216) 只有 13.45（< 18.0 阈值），而它距旧首端 `#A8F3C8` 有 40.6
  （所以这条反向断言此前一直绿）；中段/末端在同一条混色线上的最近距离是 47.8 / 77.7，且渐变一旦
  漏到次出口必然带上这两枚饱和端。**判据是「换一枚不与排版抗锯齿混淆的禁色」，18.0 阈值未放宽。**

不受影响（已核）：
- `MintTokenIsComposableContractTest` 的 `brandStaticHexes` 只核 `AppleMintStart/Middle/End/Light` 四枚、
  且 `assertEquals(4, brandStatics)`——新增三枚药片令牌不进这份清单。
- `DesignTokenContractTest` / `PrototypeTokenContractTest` 的 `onBrand` 对比断言压的是启动页深端 `#35B9B5`，未动。
- `RawColorLiteralRatchetTest`：`ui/theme/MintPalette.kt` 属 `RawHexThemeDefinitionFiles` 白名单，裸 hex 不触发棘轮。

## 8. 验收口径

- `:app:testDebugUnitTest`（含 PillButtonInkTest / MintTokenIsComposableContractTest /
  DesignTokenContractTest / RawColorLiteralRatchetTest / CardDetail* / WordRow*）全绿。
- connected：`PillButtonTest`（像素取色）与 `TodayPlanScreenTest`（CTA 三端）在设备上重跑通过。
- 观感判据（人眼）：药片末段与相邻域色按钮（如`复习`域 `palette.deep` 填充）并排时，
  不再是「明显更深更艳」的那一枚。
