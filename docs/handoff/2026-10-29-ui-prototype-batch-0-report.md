# 2026-10-29 UI 原型对齐批 0 工作报告

## 1. 本次目标

按用户要求把 Android 应用逐屏对齐到已冻结的 HTML 原型（39 屏，`assets/app.css` 为唯一令牌源），
本会话完成**批 0：令牌层与共享组件**——颜色 / 排印 / 形状 / 动效 / 字体 / 贴纸 / 共享组件，
验收口径是「JVM 契约全绿 + 共享组件样例可编译，不逐屏验收」。

原型目录只读，未做任何写入。令牌取值冲突时一律以 `assets/app.css` 为准。

## 2. 完成了什么

### 2.1 令牌层（`app/src/main/java/com/example/englishlearning/ui/theme/`）

- `AppColors.kt`：`AppColorSet` 按原型 `:root` / `html[data-theme='dark']` 逐值重写。新增
  `surface2 / brand / brandDeep / brandTint / dangerTint / feedbackAgain / feedbackFuzzy /
  feedbackKnown / strongSurfaceHigh / onStrongMuted`；兼容字段 `surfaceGlass / tint3 / onMint /
  onBrand / errorFill` 保留并逐条注明去向（批 1–3 逐屏替换，批 4 删除）。
- `AppPalette.kt`：组合期外的静态浅套与 `AppColorSet` 逐值同步。
- `AppType.kt`：六档语义保留名字、换成原型度量；新增 `Eyebrow / Caption / Meta / BodyLg /
  Lemma / StatNum / Prose / ProseZh / NavLabel / StrongTitle / ArticleTitle / PlanNum`；
  行高按原型写入（旧「不写 lineHeight」口径作废，见决策文档 §4）。
- `AppShape.kt`：10 / 14 / 20 / 26 / 14 / 10 / 20 / 药片 与两个旧档（Icon 12、Inline 9）逐条对位原型 `--r-*`。
- `AppMotion.kt`：160 / 220 / 300 与两条缓动，`Easing` 别名保留。
- `AppFont.kt`（新）+ `res/font/`：Newsreader 五档静态 TTF（400/500/550/600/650），
  `docs/third-party-notices.md` 补 SIL OFL 1.1 登记。
- `MintGlassTheme.kt`：M3 15 个排印角色与颜色角色改映到新 token。

### 2.2 贴纸

- 原型 `assets/icons/<name>.png` 的 32 枚 256×256 位图拷进 `res/drawable-nodpi/sticker_<name>.png`，
  本地打包、不联网取图。
- `components/StickerBadge.kt`（新）：`Sticker` 枚举（32 枚，资源名逐枚对位）、三档尺寸
  `Small(32/10) / Medium(44/14) / Large(64/20)`、`tint` 底、`ContentScale.Crop`、1dp 内描边
  （浅套 `text1` 5%、深套白 9%，用 token 透明档表达，不写裸十六进制）。

### 2.3 共享组件

- `Containers.kt`（重写）：新增 `AppCard`（`surface` + 1dp `outline` + 20 圆角 + 16 内距，原型 `.card`
  54 处）、`QuietCard`（只描边不铺底，`.card-quiet`）；`SoftCard` 改 `tint` 实底、不描边（`.card-soft`）；
  `StrongCard` 对齐 26 圆角 / 20 内距 / 右上径向薄荷光（`.strong-card`）；`BareRow` 改原型 `.row`
  度量（48dp 触控下限、上下 12、无横向内距、间隙 12、标题 15sp/550/-0.005em、副行 12sp `ink-3`、
  尾值 13sp `ink-3`、chevron 18dp、全宽 `line-soft` 顶线），旧的 `BareRowDividerIndent`（52dp 缩进）退役。
- `SegmentedControl.kt`（新）：`.seg` / `.seg-solid` 轨道 3dp 内距 + 2dp 间隙 + 药片 + `surface-2` 底 +
  1dp `line` 边；`selectable` + `selectableGroup`。
- `AppChip.kt`（新）：`.chip` 30dp 本体 / `.chip-quiet` / 选中 `brand-tint` + `brand-deep`；
  48dp 触控盒包裹，`toggleable`（等价 `aria-pressed`）。
- `AppSwitch.kt`（新）：`.switch` 46×28、拇指 20dp、内缩 3dp、行程 18dp、开态 `brand-deep`、
  `Role.Switch`、220ms 入场缓动。
- `PickRow.kt`（新）：`.pick` 22dp 标记（复选圆角 7 / 单选 50%）、1.5dp 描边、选中 `brand-deep` + 白勾、
  上下 12dp、间隙 12dp、`selectable`（Radio/Checkbox 两种角色）。
- `AppField.kt`（新）：`.field` 标签 12/600 + `.input` 1dp 边 / 14 圆角 / 12×14 内距 + 焦点 3dp
  `brand` 30% 薄荷环 + `.field-error` 取 `error`。自绘 `BasicTextField`，不用 M3 `OutlinedTextField`。
- `EmptyState.kt`（新）：`.empty` 84dp 贴纸（复用 `StickerBadge`，圆角 26）+ 上下 32 / 左右 16 +
  12dp 间隙 + 标题 15/600 + 说明 12/`ink-3`。
- `PlanBar.kt`（新，`PlanProgressBar`）：`.plan-bar` 高 10、2dp 间隙、轨道 14%、新学段 `brand`、
  复习段 62%、300ms 过渡、`progressBarRangeInfo`。
- `InlineNotice.kt`（重写）：几何改成原型 `.notice`——16×12 内距、14 圆角（`AppShape.Button`）、
  **无描边**、12dp 间隙、17dp 字形、12sp 文案；`NoticeInk` 从四字段收成三字段（去掉 border）；
  三态 token = Info `text2`/`tint`、Error `error`/`dangerTint`、Success `success`/`tint`；
  动作链接墨仍取 `text1`（R-10.1b），48dp 触控下限与单一链接配方（`InlineNoticeAction`）不动。
- `EntryRow.kt`：随 token 迁移（`onMint` → `brandDeep` 等）的取值面小改，几何不变。
- `AiFeatureScreen.kt` + `AiFeatureScreenTest.kt`：随 token 迁移把「按主题分档的墨」收敛为
  主题无关的 `onBrand`（`onMint` 现在是 `brandDeep` 别名，浅套压亮底仅 2.69:1，旧分档两种主题下都不达标）。

### 2.4 测试与文档

- 新增 `app/src/test/.../SharedComponentContractTest.kt`（12 条）：贴纸清单/资源/尺寸/内描边、
  四种承载面、裸行度量、分段、芯片、开关、选择行、字段、空状态、双段进度条、提示条。
- 新增 `PrototypeTokenContractTest.kt`：令牌逐值对位原型。
- 迁移旧契约到原型口径：`ContainerContractTest`（裸行几何 + 软卡圆角）、
  `SharedComponentGeometryContractTest`（提示条 16×12 / 12 / 无描边 / 17dp / `AppShape.Button`）、
  `InlineNoticeKindTest`（三字段纯表 + 三态对比度 + 被拒候选承重）、
  `SharedComponentUniquenessRatchetTest`（组件清单加 8 个文件 / 10 个组件名）、
  `DesignTokenContractTest` / `MaterialThemeAssemblyContractTest` / `AiBadgeContrastFixTest` /
  `EntryRowInkTest` / `PillButtonInkTest` / `GlassPanelInkPairingContractTest` /
  `WordRowSpeakerContractTest` / `AppBottomBarContrastTest` / `AccessibilityColorTokenContractTest` /
  `AiFeatureLayoutContractTest`（随 token 取值同步）。
- 文档：新增决策 `docs/decisions/2026-10-29-ui-prototype-token-alignment.md`、计划
  `docs/superpowers/plans/2026-10-29-ui-prototype-alignment.md`；更新
  `docs/superpowers/specs/2026-10-17-ui-redesign-design.md` 与 `docs/third-party-notices.md`。

## 3. 验证记录

### RED → GREEN

- 先写 `SharedComponentContractTest` 并**实测 12/12 失败**（失败原因逐条为「组件不存在 / 度量不符」，
  不是编译错误），再按测试实现组件。
- 实现后定向跑 6 个受影响的测试类：`41 tests completed`，2 条红（`AppCard` 内距写在默认形参上
  被函数体窗口漏掉；`errorFill` 当墨的「被拒候选」在深套反而达标）。两条都按以下方式修：
  取样窗口扩到「签名 + 函数体」；被拒候选只按浅套承重（深套亮红压深底本就达标，裁决现场在浅套）。
  复跑全绿。

### 全量门禁（本机 Gradle 8.13，`assembleDebug → testDebugUnitTest → compileDebugAndroidTestKotlin`）

```
BUILD SUCCESSFUL in 1m 9s
```

- XML 复核：`280 suites / 2070 tests / 0 failures / 0 errors / 0 skipped`
  — 对照基线 2058 = 本批新增 12 条，用例数只增不减。
- `app-debug.apk` 已产出（343,768,400 字节，含 Newsreader 字体与 32 张贴纸）。
- `:app:compileDebugAndroidTestKotlin` 通过（connected 未跑，按仓库口径需真机/模拟器，归批 4 走查）。
- detekt / ktlint 按 `docs/decisions/2026-09-19-static-analysis-and-test-baseline.md` 属存量债，
  本批不新增规则、不修存量；未跑增量比对。

### 实测对比度（WCAG 相对亮度当场复算，浅 / 深）

| 配对 | 比值 | 结论 |
|---|---|---|
| Info 提示：`text2` / `tint` | 5.32 / 7.16 | ≥4.5 |
| Error 提示：`error` / `dangerTint` | 6.34 / 6.26 | ≥4.5 |
| 回执提示：`success` / `tint` | 6.87 / 8.48 | ≥4.5 |
| 动作墨 `text1` / `tint` | 14.47 / — | ≥4.5 |
| 动作墨 `text1` / `dangerTint` | 14.70 / — | ≥4.5 |
| 芯片选中：`brandDeep` / `brandTint` | 4.60 / 8.85 | 浅套仅富余 0.10，别再调浅 |
| 分段未选中：`text2` / `surface2` | 5.75 / — | ≥4.5 |
| 分段选中：`text1` / `surface` | 16.59 / — | ≥4.5 |
| solid 段选中：`onStrong` / `strongSurface` | 9.10（深套 11.65） | ≥4.5 |
| 浅套强卡：`text1` / `tint2` | 13.44 | ≥4.5 |
| 原型 `--fuzzy` / `--surface` | **3.87** | **低于正文 4.5** |
| 原型 `--ink-3` / `--bg` | 4.39（深套 7.03） | **略低于 4.5** |

## 4. Git 状态

- 分支：`stage-5-ui-r2-learning-path`，基线 `1705fb1`。
- 本批改动 73 个路径（27 个修改 + 46 个新增：7 个新组件、`AppFont.kt`、32 张贴纸、`res/font/`、
  3 个新测试、2 份文档），按功能域分三次本地提交（令牌与组件 / 契约测试 / 文档），
  **未推送**——推送需单独授权（见 §7）。
- 未提交遗留：无。构建产物与 `app/build/` 不入库。

## 5. 未完成 / 遗留问题

1. **强卡浅套仍是 `tint2` + `text1`**，不是原型的深底。这是既有的用户评审口径（「浅套深墨实底太重」），
   比原型更晚；本批只对齐了圆角 / 内距 / 光斑，底色留原判。**需用户确认是否改回原型深底**。
2. `--fuzzy #A8792A` 压白 3.87、`--ink-3 #65756C` 压 `--bg` 4.39：原型即权威，先照原值落地并记录，
   **待用户裁决是否调深**（调深会偏离原型）。
3. 原型没有「设置」域色，`AppDomain.Settings` 仍由 `ink-3` 派生；批 1 屏迁移时逐处核对。
4. `InlineNotice` 的 Success 态原型无样张，暂以 `success` 墨 + `tint` 底承载既有完成回执；
   等原型补样张再对齐。
5. `InlineNotice` 的 Info 态在原型有 `.notice`（tint）与 `.notice-info`（surface-2）两档，本批固定取前者；
   若批 1 屏幕里出现后者的用法，需要给组件加一档，届时先问。
6. `SegmentedControl` 段项 48dp（原型 34dp）、`AppChip` 48dp 触控盒包 30dp 本体：均为无障碍下限优先，
   属**登记偏离**。
7. `AppSwitch` 拇指阴影用 `shadow(2.dp)` 近似原型 `0 1px 3px`（Compose 的 `shadow` 不接 spread 与颜色）。
8. 七枚新组件目前**尚无屏侧调用点**（批 1–4 逐屏接线）；`BareRow` / `InlineNotice` / `SoftCard` /
   `StrongCard` 已被既有屏消费，本批的取值改动会在这些屏上直接生效，**批 1 逐屏对照时要复核**。
9. 批 1–4（37 屏逐屏对齐、AI 未接入控件禁用态、fontScale 2.0 / TalkBack / 动效减弱 / 真机走查）未开始。

## 6. 下一任接手须知

- 先读：本报告 → `docs/superpowers/plans/2026-10-29-ui-prototype-alignment.md` →
  `docs/decisions/2026-10-29-ui-prototype-token-alignment.md` → 原型 `assets/app.css`。
- 门禁命令（**不要用 `./gradlew`**，wrapper 会联网）：
  ```bash
  V=$(grep -oE 'gradle-[0-9]+\.[0-9]+' gradle/wrapper/gradle-wrapper.properties | head -1)
  GRADLE=$(ls -1 "$HOME/.gradle/wrapper/dists/$V-bin/"*/$V/bin/gradle.bat | head -1)
  "$GRADLE" :app:assembleDebug :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin \
    --offline --no-daemon --no-build-cache 2>&1 \
    | grep -E 'FAILED|^e:|BUILD|tests completed' ; exit ${PIPESTATUS[0]}
  ```
  哈希子目录会变，所以用通配；版本必须匹配 `-bin`（本机另有 `gradle-8.13-all` 与 9.2.1）。
- 源码契约测试的取样窗口：**卡片内距写在默认形参上**，`functionBody` 必须从签名起取，
  只取 `{` 之后会把 `PaddingValues(16.dp)` 漏掉（本批踩过）。
- **Kotlin 块注释会嵌套**：KDoc 里写 `assets/icons/*.png` 会开一个未闭合的嵌套注释，
  编译器报 `Unclosed comment` 且后续代码全部失效。贴纸路径一律写 `assets/icons/<name>.png`。
- 裸 hex 有棘轮（`RawColorLiteralRatchetTest`）：内描边一类半透明色用 `colors.x.copy(alpha = ...)`
  或 `Color.White.copy(...)`，不要新写 `Color(0x…)`。
- 贴纸资源在 `res/drawable-nodpi/`，文件名必须与 `Sticker.asset` 逐枚一致（契约双向核验）。
- 原型目录绝对只读；原型未画到的状态先问用户，不要自行补。

## 7. 用户确认过的决策

1. 原型是唯一视觉权威；原型与文字描述冲突时以原型为准；设计目录只读，不得写入。
2. 原型未画到的状态先问用户，不自行补；文案保持原型原文，不改写、不编造指标。
3. 图片资源本地引用、不联网取图；贴纸从 `assets/icons` 拷进 `res/drawable`。
4. 不引入原型没有的权限、网络请求或第三方 SDK；UI 之外的逻辑（FSRS、备份、AI 协议）保持原样。
5. AI 配置页按方案 B：画完整版面，未接入控件禁用并标「暂未接入」。
6. 学习计划复查上限 10–80 可持久化，不改 FSRS。
7. 样例指标绑真实数据，没有真实来源的行隐藏。
8. 导航保持单宿主，屏幕拆到独立文件，零新增依赖。
9. 开工前把 211 个未提交改动按功能域做本地检查点提交，**先不推送**。
10. 交互元素要有可访问名称与焦点态；系统字体放大后布局不能破。

> 本批结束时**未推送远端**：按第 9 条口径，推送需用户单独授权后再执行。
