# 决策：UI 令牌对齐冻结原型（2026-10-29）

## 背景

用户提供了已冻结的 HTML 原型（412×915 手机壳，39 屏），要求应用界面按原型逐屏落地，
并明确「原型与文字描述冲突时以原型为准」、设计目录只读。原型是纯 CSS 令牌 +
组件类，应用侧是 Compose + 既有 `AppColorSet` / `AppType` / `AppShape` / `AppMotion`。
本决策确定两套 token 的逐值映射、兼容策略和已知偏差，后续批 1–4 的逐屏迁移都以此为准。

## 决定

### 1. 令牌权威源

- 视觉取值只认原型 `assets/app.css` 的 `:root` 与 `html[data-theme='dark']` 两块。
- 应用侧权威源仍是 `ui/theme/AppColors.kt`（运行时）与 `AppPalette.kt`（组合期外静态浅套），
  两者必须逐值同步；文档表格是副本，不作权威。
- 原型没有的 token 不新增；原型有而应用缺的补上，名字按语义而非 CSS 变量名照抄。

### 2. 中性色映射（浅 / 深）

| 原型 | 浅 | 深 | Kotlin 字段 | 说明 |
|---|---|---|---|---|
| `--bg` | `#F1F4EF` | `#101814` | `background` | 保留字段名 |
| `--surface` | `#FFFFFF` | `#161F1A` | `surface` | |
| `--surface-2` | `#F7F9F5` | `#1A2420` | `surface2`（新） | 分段底、静态芯片底 |
| `--tint` | `#E8F2EA` | `#1C2A24` | `tint` | 贴纸底、软卡片 |
| `--tint-2` | `#DCEBE1` | `#22322B` | `tint2` | |
| `--line` | `#DDE5DD` | `#2A3931` | `outline` | 卡片/输入框 1px 边 |
| `--line-soft` | `#EEF2ED` | `#202D27` | `outlineSoft` | 行分隔线 |
| `--ink` | `#15211C` | `#E9F2EC` | `text1` | |
| `--ink-2` | `#55665D` | `#A5B8AE` | `text2` | |
| `--ink-3` | `#65756C` | `#93A69C` | `text3` | |
| `--brand` | `#6CCBAB` | `#6FD9B6` | `brand`（新） | 进度条、装饰点 |
| `--brand-deep` | `#1A7A63` | `#7FE3C1` | `brandDeep`（新） | 强调文字、选中态、开关 |
| `--brand-tint` | `#E4F4EC` | `#1D322B` | `brandTint`（新） | 选中芯片底、横幅 |
| `--strong` | `#1F4739` | `#22332C` | `strongSurface` | 保留字段名 |
| `--strong-hi` | `#2A5A49` | `#2B4038` | `strongSurfaceHigh`（新） | 主按钮按压 |
| `--on-strong` | `#E9F2EC` | `#E9F2EC` | `onStrong` | |
| `--on-strong-2` | `rgba(233,242,236,.74)` | `rgba(233,242,236,.72)` | `onStrongMuted`（新） | 强卡次级文字 |
| `--danger` | `#A5291F` | `#FF8177` | `error` | 语义沿用「错误/危险」 |
| `--danger-tint` | `#FDEEEC` | `#34211F` | `dangerTint`（新） | 危险按钮底、错误提示底 |
| `--success` | `#1B5E20` | `#6BD98A` | `success` | |
| `--again` | `#B3573F` | `#E39086` | `feedbackAgain`（新） | 反馈三档 |
| `--fuzzy` | `#A8792A` | `#EFCB92` | `feedbackFuzzy`（新） | |
| `--known` | `#1A7A63` | `#7FE3C1` | `feedbackKnown`（新） | |

域色沿用原型 `--learn/--review/--library/--reading/--ai` 的 base/deep；深套 `deep = base`。
`--ai` 与 `--brand` 同值但语义独立，保留 `AppDomain.AiSpeech` 两条映射。

### 3. 兼容字段（临时）

以下旧字段在批 0 保留，供尚未迁移的屏编译，批 1–3 逐屏替换，批 4 删除：

| 旧字段 | 批 0 取值 | 去向 |
|---|---|---|
| `surfaceGlass` | 浅 `#E0FFFFFF` / 深 `#E0161F1A`（surface 88%） | 导航药片、玻璃面板改用 `surface` + 透明度/模糊 |
| `tint3` | `brandTint` | 选中容器改用 `brandTint` |
| `onMint` | `brandDeep` | 薄荷底文字改用 `brandDeep` 或 `text1`（按原型语义） |
| `onBrand` | 浅 `#04241C` / 深 `#04241C`（品牌渐变上的墨，原型渐变同值） | 渐变上文字保持 |
| `errorFill` | 浅 `#FF3B30` / 深 `#FF6B61` | 危险实心填充改用 `error` + `dangerTint` 组合 |

兼容字段在契约测试中显式列为「迁移期」，且每批批末统计剩余引用数，只减不增。

### 4. 排印

- 原型字族：正文/UI 用系统 sans；英文标题、词形、数字用 **Newsreader**（SIL OFL 1.1，可变字重 200–800）。
- 应用侧用静态 TTF 覆盖 400/500/550/600/650 五档（Android 资源字体不支持 woff2 与可变轴），
  文件名 `newsreader_regular/medium/semibold/mediumbold/bold.ttf` 等，登记 `docs/third-party-notices.md` 与许可页。
- 六档语义保留名字、换成原型度量；新增原型专用档（Eyebrow/Caption/Meta/BodyLg/Lemma/StatNum/Prose/ProseZh/NavLabel/StrongTitle/ArticleTitle/PlanNum）。
- 行高按原型写入（正文 1.6、标题 1.25、词形 1.05、文章正文 1.72、中文 1.75、说明 1.55）；
  批 0 之前「不写 lineHeight」的旧口径作废，由本决策替代。

### 5. 形状 / 间距 / 动效

- 圆角：10（小控件）/14（按钮、输入、44 徽标）/20（卡片、64 徽标）/26（对话框、底部面板、强卡、空状态大图）/999（药片）。
- 间距只用 4 的倍数：4/8/12/16/20/24/32/40；页边距 20dp，窄屏（<480dp）18dp；段间距 24dp。
- 动效：160ms（fast）/220ms（normal）/300ms（slow）；缓动 `cubic-bezier(.2,.7,.3,1)` 与
  `cubic-bezier(.4,0,.8,.3)`；系统「移除动画」时时长归零（沿用 `AppMotion.durationMs`）。
- 触控下限：原型按钮最小 46dp、反馈键 50dp；无障碍下限仍取 48dp 包裹，不靠 padding 凑。

### 6. 已知偏差（记录，不静默改原型）

| 项 | 实测 | 处理 |
|---|---|---|
| `--fuzzy #A8792A` 压白色 `--surface` | 3.87:1，低于正文 4.5:1 | 先按原型落地；契约测试记录实测并标注待用户裁决是否调深（建议同色相加深至 ≥4.5） |
| `--ink-3 #65756C` 压 `--bg #F1F4EF` | 4.39:1，略低于 4.5 | 只用于 11–13sp 辅助文字；记录实测，批 4 QA 复核 |
| 原型无「设置」域色 | — | `AppDomain.Settings` 暂由 `text3` 派生，批 2 屏迁移按原型逐处核对 |
| AI 参数 / 流式 / 语音音色 / 生图参数 | 超出允许请求协议 | 按用户裁决 B：画完整版面，禁用未接入控件并标注「暂未接入」 |

## 影响

- 批 0 一次性改变全站底色、墨色、强调色与字号；所有既有界面立刻换肤，但布局不动。
- 契约测试需同步改写（15 个文件引用旧 hex）；这是预期改动，不是回归。
- 批 1–4 才逐屏对齐结构；批 0 完成时应用外观「换色换字」，不等于界面已对齐原型。
- 回滚点：批 0 开工前的 checkpoint 提交（见当日报告 §4）。
