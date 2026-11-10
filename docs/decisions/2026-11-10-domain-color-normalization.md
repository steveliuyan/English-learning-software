# 2026-11-10 功能域色归一（饱和度 / 明度同档）

用户当批要求（承接 m04281「颜色饱和度对齐一下，太杂也不好」，本条为 m04665「都做」的第①项）：
把七个功能域的强调色从「各域各调出来的彩度与明度」收敛成**同一档**，让入口图标、域色文字、
渐变容器在全站看起来是一套东西，而不是七个设计师各调一个。

本决策是本仓库**唯一一处按后续裁决覆盖 spec §2.1 冻结原型域色表**的地方；其余 28 枚中性 token 仍逐字对原型。
决定、理由、被取代的旧值、以及验收口径都在下面。

## 1. 结论（权威取值，已落 `ui/theme/AppColors.kt`）

色相**逐域保留**一枚（即该域原浅色 `base` 的色相；AiSpeech 保持品牌原色相），
饱和度与明度统一档：

| 档 | 饱和度 S | 明度 L | 适用 |
|---|---|---|---|
| 浅套 `base` | 48% | 61% | 浅色图形/渐变起点 |
| 浅套 `deep` | 65% | 29% | 浅色填充、浅底上的文字与字形 |
| 深套 `base`（`deep = base`） | 58% | 74% | 深色容器与深色字形 |
| `Settings` | 6% | 同各档 L | 有意近中性，只对齐明度、不给彩度 |

| 域 | 浅 `deep` / `base` | 深（`deep = base`） | 旧浅 `deep` / `base` | 旧深 |
|---|---|---|---|---|
| AI | `#1A7A63` / `#6CCBAB` | `#6FD9B6` | 同左（不动） | `#6FD9B6`（不动） |
| 学习 | `#1A4D7A` / `#6C9FCB` | `#96BFE3` | `#175A93` / `#7BB4E6` | `#84C4EE` |
| 复习 | `#7A551A` / `#CBA76C` | `#E3C696` | `#8A5712` / `#EBC68B` | `#EFCB92` |
| 词库 | `#411A7A` / `#926CCB` | `#B596E3` | `#65478A` / `#B7A0D9` | `#C6A9E4` |
| 阅读 | `#7A1A2B` / `#CB6C7C` | `#E396A4` | `#9C3B4F` / `#E297A4` | `#EDA8B4` |
| 危险 | `#7A241A` / `#CB766C` | `#E39F96` | `#9E2B24` / `#DD8B81` | `#E39086` |
| 设置 | `#464E4B` / `#96A29D` | `#B9C1BD` | `#4A5551` / `#A6B0AC` | `#B4BEBA` |

同时落地的两个消费点：`ui/theme/AppColors.kt` 的 `DomainLight` / `DomainDark`（运行时权威），
与 `ui/theme/AppPalette.kt` 的 `object DomainColors`（组合期外静态浅套兼容源，只有浅套）。

## 2. 为什么以 AiSpeech 为锚、三值不动

`AiSpeech` 的浅 `base`/`deep` **逐字等于**品牌 token `brand #6CCBAB` / `brandDeep #1A7A63`，
深套 `base` 逐字等于深 `brand #6FD9B6`。品牌渐变（`mint-start/mid/end`）、进度条、装饰点、
`feedbackKnown` 全挂在这三枚上——它们是**不可动的锚**。

于是归一的档位是**从锚反推出来的**，不是拍脑袋：
`hsl(159.8, 48%, 61%)` 恰好就是 `#6CCBAB`，`hsl(165.6, 65%, 29%)` 恰好就是 `#1A7A63`。
也就是说「同一档」= 品牌那套的 S/L；其余六域把色相换上自己的，S/L 交出去。
深套因 `#6FD9B6` 无法用整数 S/L 精确复现（L 64.3），故深套 AiSpeech 保留原 hex，
其余六域按 S 58 / L 74 落。

## 3. 深套明度为什么钉在 L 74（不能再压）

`IconBadge` 的 duotone 图标对深套域色有两条相反方向的约束，把明度夹出一个**下界**：

1. 「白字形压域色 < 3:1」——域色必须足够亮（深色底上的实心图形档需要白/浅字形）；
2. 「深墨 `onBrand #04241C` 压 30% 墨稀释后的底幅 ≥ 3:1」——域色又不能被稀释到底幅过亮。

Library（紫，三原色里相对亮度最暗的一域）是最紧的：
L 70 时白字形 2.92 / 墨压底 3.42（过），L 68 白字形 3.18（不合格），L 65 墨压底 2.88（不合格）。
L 74 是同时满足两条且留出余量的最低档。首次落地时误取 L 65，`:app:testDebugUnitTest` 当场红两条
（`IconBadgeContrastContractTest` 的 `dark_entry_ink_paired_with_domain_colors_clears_3_to_1` 与
`accent_layer_clears_3_to_1_against_the_duotone_fill_in_both_theme_sets`），改到 L 74 后 2610 例全绿。

## 4. Settings 只对齐明度、不给彩度

`AppDomain.Settings` 是**有意**的近中性域（旧值 S≈6~7%），它在界面上承担的是「设置不是某个学习动作」的语义。
归一保留这条语义：S 钉 6%，只把 L 拉到与其余六域同档（61 / 29 / 74）。
给它补彩度会把它变成第八个「功能区」，与 `2026-10-31-settings-as-fifth-tab.md` 的分层冲突。

## 5. 归一后的对比度复算（WCAG 相对亮度）

浅套（底 = 白 `#FFFFFF` 字形 / 域色深压浅 `tint #E8F2EA` / 域色深压 `bg #F1F4EF`）：

| 域 | 白字压 `base` | 白字压 `deep` | 白字压渐变亮端（deep 混 18% base） | `deep` 压 `tint` | `deep` 压 `bg` |
|---|---|---|---|---|---|
| AI | 1.95 | 5.24 | 4.28 | 4.57 | 4.72 |
| 学习 | 2.82 | 8.80 | 7.03 | 7.68 | 7.93 |
| 复习 | 2.26 | 6.68 | 5.36 | 5.83 | 6.02 |
| 词库 | 4.02 | 12.70 | 10.40 | 11.08 | 11.45 |
| 阅读 | 3.51 | 10.45 | 8.60 | 9.11 | 9.42 |
| 危险 | 3.31 | 10.02 | 8.17 | 8.75 | 9.04 |
| 设置 | 2.64 | 8.57 | 6.80 | 7.47 | 7.72 |

深套（`deep = base`）：`onBrand #04241C` 字形压域色 **6.62（词库）–10.03（复习）**、
域色压深 `tint #1C2A24` **6.00（词库）–9.09（复习）**。

结论与归一前同一口径：
- **浅套 `base` 上仍然禁白字**（1.95–4.02，只有词库过 3:1），原有硬规则不变；
- 浅 `deep` 实底白字全部 ≥5.24，渐变亮端白字最低 4.28（AI），全部过图形非文本 3:1、也过正文 4.5；
- 浅 `deep` 压 `tint`/`bg` 全部 ≥4.57（最低 AI），过正文档；
- 深套 `onBrand` 字形与域色压深底全部 ≥6.00。

## 6. 玻璃浮层的真机口径（同批产生的验收改动）

归一过程中 `GlassPanelThemeInkTest` 的两条「玻璃容器合成像素」腿在真机上红了
（期望 `#FFF2F2F1` / `#FF151D18`，实测 `#FFE9E9E9` / `#FF1A221E`）。取证实测结论：

- **不是域色回归**：`surfaceGlass`（浅 `0xE0FFFFFF` / 深 `0xE0161F1A`）与 `Scrim` 都不在改动集合里；
  关掉 `Modifier.blur(8.dp)`（`GlassOverlay.frosted`）后，面板主体像素与期望只差 1。
- 真机 GPU 的 frosted 模糊 + 12% 面板透明度会把**页底非均匀内容**渗进玻璃；
  原取样带贴行内左侧 12–18dp，正压在编辑页卡片文字的左旁灰晕上。
- 处置：两条半透明浮层腿改用**行内右侧内边距带**（`bandRightInsideTag`，inset 24dp / 宽 6dp），
  并把这两条腿的容差定为 `GLASS_OVERLAY_TRANSLUCENCY_TOLERANCE = 16`，
  同时加**最近邻判据**（实测必须更接近本套玻璃合成、且离另一套更远；两套间距 ~221 档，判别力不受影响）。
  不透明面板腿仍是 `GLASS_CHANNEL_TOLERANCE = 5`。
  这带来一个附带发现：左带下「浅套选中行正文墨压玻璃」实测 4.32:1（底色被污染 `#E9E9E9`）会假红，
  右带干净底 `#F1F2F1` 下是 4.67:1。

## 7. 明确留在原位、不在本批范围

- `feedbackAgain #B3573F` / `feedbackFuzzy #A8792A` / `feedbackKnown #1A7A63`（深 `#E39086` / `#EFCB92` / `#7FE3C1`）：
  它们表达**状态**而非功能区，另有一套语义与对比度口径（`again` 与危险色系刻意区分），下一批单独议。
- 28 枚中性 token：不受影响，仍逐字对 spec §2.1。
- 品牌渐变三端 `mint-start/mid/end` 与 `onBrand`/`onMint`：不动。

## 8. 契约与文档同步

- `app/src/test/java/com/example/englishlearning/ui/DesignTokenContractTest.kt`：
  `domainLightHex` / `domainDarkHex` 换新值；KDoc 改述为「域色这一张表**偏离** spec §2.1 原冻结原型域色表，
  是本仓库唯一按后续裁决覆盖 spec 的 token 家族」，失败信息指向本文件。
- `app/src/test/java/com/example/englishlearning/ui/PrototypeTokenContractTest.kt`：
  用例名 `domain colors stay on the frozen prototype domain table` → `domain colors stay on the normalized domain table`。
- 活文档（spec §2.1 域色表、`docs/design/visual-style-guide.md` §2.3）**直接换代归一后的 hex** 并加脚注指向本文件。
- 带日期的旧决策 / verification / handoff 属历史记录，**不改数字**，只在域色段加一行指向本文件的覆盖说明。

## 9. 追溯

- 取代：`docs/decisions/2026-10-29-ui-prototype-token-alignment.md` 中的域色取值段（其中的域色数字作废，
  其余 token 映射仍有效）、`docs/superpowers/specs/2026-10-17-ui-redesign-design.md` §2.1 域色表。
- 不取代：`2026-10-18-ui-redesign-contrast-and-icon-layer-rulings.md` 的对比度规则与图标三层结构豁免表
  （规则不变，只是被代入的域色值换了）。
- 验收：`:app:testDebugUnitTest` 2610 例全绿（含两处钉值表）；
  `:app:assembleDebug` 通过；真机 `bf353dda`（M2102J2SC / Android 13）connected 42 例绿（5 个域色消费方测试类）。
