# 决策记录 · UI 重设计 · 功能入口图标从「线框 + 底色」升级为三层插画（2026-10-19）

## 背景

B 案 duotone 图标族落地后，用户对呈现再次提出诉求（原话）：

> 「你还要重新设一套图标，就是它每个选项图标，像你现在的图标就是特别简单的一个底色加一个用线框描出来的图案，
> 感觉这个差点意思啊，就特别单调。你像现在主流的 APP，它里面的那些图标都是有那种绘画的感觉」

复现结论（读源码确认，非猜测）：用户看到的功能入口图标全部走 `AppGlyphLayer.StrokeOnly` 单层描边
——`EntryIconBadge` 的 `EntryIconTone.Tint` 弱档（设置页、备份页、今日计划入口行、阅读入口）、
`InlineIconBadge`（底部导航、`NotConfiguredCard`）都在 22dp / 17dp 上只画骨架。
两层 duotone 的填充层只出现在 `EntryIconTone.Gradient` 实底档（设置页两行、`InlineNotice`）。
也就是说：33 枚里绝大部分字形在生产界面上只剩「一个 tint 底 + 一根线」，正是用户描述的样子。

## 决定

1. **`AppIconSpec` 增加第三层 `accentPaths`（实心强调层）**。三层分工：
   - `fillPaths`：半透明底幅（`DuotoneFillAlpha`，浅 0.42 / 深 0.30），表达体积；
   - `accentPaths`：**不透明**的实心色块，表达「画上去的那几笔」（书脊、页内文字、镜头高光、图表柱、灯芯……）；
   - `strokePaths`：2px 描边骨架，保持不变。
2. **渲染顺序 = 底幅 → 强调 → 骨架**。`AppGlyph` / `appImageVector` 增加两个可选实参：
   - `accent: Color?`：给值即画出强调层；缺省 `null` 时行为与上一版逐像素相同（既有调用点零改动）；
   - `bodyInk: Color?`：底幅墨色，缺省跟随 `tint`。
   不新增 `AppGlyphLayer` 枚举值：layer 是「画几层」的开关，accent 是「用第几支笔」，两件事不合并。
3. **`accentPaths` 是可选字段**（`= emptyList()`），常量为空的两组：
   - 5 枚豁免字形（开放折线，无可闭合区域）；
   - `Play` / `Pause` / `Stop` 三枚传输控件（已由 `fillPaths` 承担实心，强调层冗余）。
   其余 25 枚必须至少一条强调路径，由 `AppIconsStructureContractTest` 双向钉死。
4. **`EntryIconBadge` 两档的实际着色**（2026-10-28 裁决 D1 与 2026-10-29 裁决 D6 两次改钉后的现行口径）：
   - `Gradient` 实底档：**三层全画**。骨架与底幅同墨——浅套白、深套 `onBrand`；强调层浅套取
     `accentInkForGradient(域 deep)`（域 `deep` 再压暗 22%，`ACCENT_DARKEN_FRACTION`），深套取 `onBrand`。
   - `Tint` 弱档：底 = 共享中性 `tint`（Task 27 / 裁决 22-2），字形回到 `deep` **单层描边**
     （`AppGlyphLayer.StrokeOnly`）。本条 2026-10-19 原文「弱档也不再降级为单层描边」已被裁决 22-2
     就地改钉：弱档是浅底，22dp 字形上再铺半透明底幅只会糊成一坨，单层骨架 + 域 `deep` 的配对才是可读解。
   - **深套强调层不做「改取域 `base` / `light`」的替换（D6 结论 A）**。深套域色在深色下本身就是浅底，
     强调层改成域 `light` 时对底幅实测最低只有 2.31:1（`Review`），连纯白也只有 2.82:1，都过不了
     非文本 3:1；`onBrand` 是深色档里唯一满不透明的中性墨，得到的是「同一墨色的实心重述」。
     实测口径与两个反例的数值写在 `docs/decisions/2026-10-29-icon-vector-redraw-from-master.md` 的 D6 段。
   `AppGlyphLayer.StrokeOnly` 保留给指示性符号（`Back` / `ChevronRight` / `ChevronDown` / `Close` / `Check`）、
   `Tint` 弱档与 30dp 行内徽标。

## 对比与可读性

三层不改变任何「必需图形」的对比基线：骨架与强调都取域 `deep`（弱档）或白 / `onBrand`（实底档），
这两组配对已由 `docs/decisions/2026-10-18-ui-redesign-contrast-and-icon-layer-rulings.md` 与
`IconBadgeContrastContractTest` 按 WCAG 相对亮度逐域 × 逐主题复算并钉在非文本 3:1 以上（数值只写进断言消息，
本记录不抄，抄了就会随 token 漂移）。

新增的底幅（弱档 `base` @ 0.42 压 `tint`）是**装饰性面**，不承载识别特征：识别特征一律在骨架层
（裁决 22-1 的遗留要求），强调层只是同一识别特征的加粗重述。因此它不承担 3:1 义务，
但仍受「不得靠颜色单独表意」约束：三层同时存在时，灰度打印下骨架依然可读。

## 影响

- `AppIcons.kt`：`AppIconSpec` 加字段 + 25 枚各补强调几何；文件头「逐枚清单」的层列由「两层」改「三层」，
  豁免表标题写作「三层结构豁免表」。
- `AppIcon.kt`：`appImageVector` / `AppGlyph` / `buildAppImageVector` 增加 `accent` / `bodyInk`；
  `remember` 键追加这两个输入（换域色或换强调色必须重建，否则缓存会把旧色留在屏幕上）。
- `IconBadge.kt`：`Gradient` 档改走三层；`Tint` 档按裁决 22-2 回落单层（见决定 4 的改钉）；
  容器几何（40dp / `AppShape.Icon` / 渐变规则 / 1dp 玻璃高光）不动。
- 测试：`AppIconsStructureContractTest` 的层列词表加「三层」，新增强调层双向契约与段数守恒覆盖；
  `IconBadgeContrastContractTest` 的「弱档恰一处单层」改为「两档都不再单层」。
- 许可台账不变：强调层同样是本项目原创自绘，`app-authored-ui-icons` 的措辞与「不产生署名义务」结论不变。
- 不在本条范围：容器本身的重做（投影、双层高光、异形容器）、底部导航 22dp 非选中字形、启动图标。
