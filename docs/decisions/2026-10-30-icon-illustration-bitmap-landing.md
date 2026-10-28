# 决策记录 · 插画母版位图直接进运行态（2026-10-30）

## 背景

2026-10-29 的结论是「把母版的绘画感翻译成 24×24 三层矢量」，并先做了 `Clock` / `Book` / `Gear`
三枚样板。三枚样板经真机与模拟器渲染后**被用户否决**（「不行，不好看」），随后用户指认他给的参考图
就是本项目自己产出的第二批（`docs/design/icon-master/icon-color-calm__01…28`，28/28 通过 v3 审计）。

技术根因不是「画得不够细」，而是渲染管线本身的上限：`buildAppImageVector` 只能给出三档明度
（底幅半透明 / 强调层不透明单色 / 2px 骨架），而母版的体积感来自「浅面 + 深面 + 内高光 + 圆角厚边」
四到五档层次加连续明暗过渡。把 1024 的母版压进 24 网格，无论怎么写路径都会塌回贴纸线框。
因此**「照着母版再画一遍矢量」这条路作废**，改为直接复用母版本身。

## 决定

1. **28 枚母版去底后作为位图资源直落**：`res/drawable-xhdpi/app_icon_<name>.png`，256×256 RGBA。
   资源名与 `icon-master/README.md` 映射表一一对应（`mic → app_icon_microphone`、`image → app_icon_image`、
   `BookOpen → app_icon_books`）。
2. **入口容器优先画位图**：`EntryIconBadge` 先查 `AppIllustrations.has(icon)`；有就直接画 36dp 位图，
   外层 40dp 容器**不铺底色、不描高光边**（位图自带域色与体积，再垫渐变底会从透明区透出来打架）。
3. **不新增第三档 `EntryIconTone`**：33 枚里能进 `EntryIconBadge` 的恰好就是有母版的那 28 枚
   （`33 − StrokeOnlyIcons`），所以「有母版 = 插画档」不需要用户再选一次；两档枚举退化为兜底分支，
   留给以后可能出现的无母版新字形。
4. **小尺寸也走位图，但比入口更收**：30dp `InlineIconBadge` 先查同一张表，命中就画 26dp 位图（容器留 2dp 光学边），
   并**保留 `tint` 底**——30dp 容器太薄，位图直接落在卡片上会失去「徽标」的定位感。首页「今日文章 / 词书」两行
   因此与设置页入口同口径；底部导航未选中态仍走 22dp 单色 `AppGlyph`，选中态拿到彩色字形，形成「选中即上色」的反馈。
   只剩 5 枚无母版豁免（`Back` / `ChevronRight` / `ChevronDown` / `Close` / `Check`）和 17dp 行内小字形仍是矢量。
5. **深色不为插画另生成一套**：位图直接落在深套 `surface` 上（模拟器实测可读），也不垫浅色底板——
   垫底板会让每一行都变成「白色小卡片」，比现有深色行更吵。浅套同样不垫底，透明区直接落在卡片白上。
6. **接受「同字形跨域复用时颜色随母版」**：位图带固定色，`Upload` 在 `BackupHubScreen`（原语域 Settings）
   仍显示母版的 Library 紫。这条取代了 2026-10-29 之前「母版定主语义域、运行态 tint 由调用点决定」的口径
   ——位图没有 tint，调用点已经无法改色，映射表即最终色。

## 预处理口径（可复现）

脚本：`docs/design/icon-master/tools/prepare-drawables.js`（`png.js` 是同目录的 PNG 编解码器，
纯 Node + zlib，无第三方依赖）。

```powershell
$m = 'H:\EnglishLearningSoftware\docs\design\icon-master'
& "C:\Program Files\nodejs\node.exe" "$m\tools\prepare-drawables.js" batch $m "$m\..\..\..\app\src\main\res\drawable-xhdpi" 256 0.06
```

1. **取底**：四边 8 点取样，实测母版底色 `rgb(243,247,243)`（`#F3F7F3`，与 `background` 的 `#F6F9F6`
   只差 2–3 个色阶，肉眼同色）。
2. **去底**：从边框四连通泛洪，RGB 欧氏距离 ≤14 判底色、置全透明；14–34 之间按距离线性给 alpha，
   得到一圈羽化边（否则 40dp 上会出现 1px 白牙齿）。母版自带的浅投影落在这一带，被一并吃掉。
3. **裁切与归一**：按 `alpha > 16` 求主体包围盒，短边居中补到正方形，四周留 6% 空白。
4. **降采样**：面积平均（alpha 预乘后再除回），1024 → 256。预乘是为了避免透明边缘被黑底污染成灰边。
5. **归一化到包围盒最大边**：`Mic`（286×770）与 `Stop`（512×523）在同一 40dp 容器里视觉重量一致；
   若按原画布归一，细长字形会明显偏小。

## 影响

- 新增 `res/drawable-xhdpi/app_icon_*.png` 共 28 张，972 KB。
- 新增 `ui/components/AppIllustrations.kt`：`AppIcon → R.drawable` 表 + `IllustratedIcon`（36dp，`Fit`）。
- `ui/components/IconBadge.kt`：`EntryIconBadge` 增加插画优先分支（36dp）；`InlineIconBadge` 增加同样的优先分支（26dp，
  保留 `tint` 底与 `AppShape.Inline`）；两者容器几何、`AppGlyph` 兜底档、`clip(AppShape.Icon)` 与 `size(40.dp)`
  计数全部不变（既有契约测试照旧通过）。
- 新增 `AppIllustrationContractTest`（6 例）：键集 == 33 − 5 豁免、28 个 id 互异非零、豁免枚返回 `null`、
  28 张 PNG 在盘且都是 256×256 RGBA、入口容器优先分支与兜底两档各就各位、行内徽标按 26dp 走同一条优先判定。
- 2026-10-29 的三枚矢量样板**保留不动**：底部导航未选中态与行内 17dp 字形仍用它们，
  只是不再出现在 40dp / 30dp 的插画位。
- 预览：`docs/design/2026-10-30-entry-icons-light.png`（浅套「我的」页）、
  `2026-10-30-entry-icons-dark.png`（深套同页）、`2026-10-30-app-drawables-sheet.png`（28 枚去底总览）。
- 不在本轮范围：深色专用插画母版、`AppIcons.kt` 其余 30 枚矢量几何。底部导航未选中态若也要插画，
  需先解决「彩色字形与单色字形并排」的视觉冲突，留待下一轮评审。
