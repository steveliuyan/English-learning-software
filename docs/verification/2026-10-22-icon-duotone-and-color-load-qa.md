# 图标两层结构与入口配色 —— Design QA 报告（2026-10-22）

## Verdict: Fail（两枚 blocker 级用户可见缺陷；结论只对「今日计划 · 浅色 · fontScale 1.0 · 360dp 档真机」这一屏负责）

## Summary

用户在真机（Mi 10S `bf353dda`）上指出两件事：① 有些自绘图标「一半一半、生成不全、会变形」；② 入口行的彩色容器太多，一页颜色过杂造成视觉疲劳。
两侧证据都拿到了：视觉真源 = `docs/design/2026-10-17-ui-redesign-mockups.html`（1243 行，已定稿，本仓唯一的 source truth），
渲染实现 = 真机截图 `C:\Users\steveliuyan\qa-scratch\phone-2026-10-22.png`（1080×2340，仓库外，不入库）+ 该包对应的生产码 `0ad31c8`。
①的主因**不是**字形画错，而是**渲染档位把 B 案的两层结构砍掉了一层**：入口弱档与底导都走 `AppGlyphLayer.StrokeOnly`，
凡是「识别特征只写在 `fillPaths`」的字形（旋钮、圆点、书页、日头、报头带……）在这些落点上直接消失，用户看到的「生成不全」就是这一层缺失。
②里有一部分是实现放大（今日计划的入口行数从稿定 3 枚涨到 6 枚），但**多域色分档本身是稿子定的**，
所以收敛配色属契约变化，必须先改 spec §2.1 的域色用途表与视觉稿，再动码——本报告只给方案与代价，不擅自改色。

## Evidence Reviewed

- 设计契约：`docs/superpowers/specs/2026-10-17-ui-redesign-design.md`（§2.1 令牌、§9 AC-R-03/10/11）、
  `docs/decisions/2026-10-18-ui-redesign-icon-duotone.md`、`docs/decisions/2026-10-18-ui-redesign-contrast-and-icon-layer-rulings.md`（裁决 4 两层豁免表）
- 视觉真源：`docs/design/2026-10-17-ui-redesign-mockups.html` —— `.ic` / `.ic-tint` 两档容器（§入口行）、
  §6.2 三案对照（`.t-b` = 定稿 B 案）、§6.1 字形网格、§7② 今日计划、§7④ 设置
- 渲染实现：真机截图 + 3 张放大取样（`crop-col-a/b/c.png`、`crop-tabbar.png`，均在 `qa-scratch`，仓库外）
- 同源状态：同一屏、浅色、fontScale 1.0、演示数据（今日 0/12）；未覆盖深色档与大字号
- 源文件：`ui/components/{AppIcons,AppIcon,IconBadge,EntryRow,AppScreenHeader}.kt`、`ui/AppBottomBar.kt`、
  `ui/TodayPlanScreen.kt`、`ui/theme/AppColors.kt`
- 契约测试：`IconBadgeContrastContractTest`（28 组合 ≥3:1 + 容器几何钉值）、`AppIconsStructureContractTest`（枚数/顺序/豁免表）、
  `SharedComponentGeometryContractTest`
- 自动化：**未跑** `run-design-qa.mjs` —— 本仓无 `design.qa.yaml`、无 `DESIGN.md`、目标不是 Web 页面（Android Compose），
  插件的截图/ DOM 管线不适用；本次全部证据走人工裁剪放大管线（PowerShell + System.Drawing，NearestNeighbor 2.6×）

---

## Blockers

### B-1 弱档与底导走 StrokeOnly，「识别特征只在填充层」的字形直接缺件

- Severity: **blocker**（用户明确指认的症状，且命中全站主流落点）
- Evidence:
  - 层开关：`IconBadge.kt` 的 Tint 分支传 `layer = AppGlyphLayer.StrokeOnly`；`AppBottomBar.kt` 的 `tab.glyph` 调用同样传 StrokeOnly
    （重算：`grep -rn "AppGlyphLayer.StrokeOnly" app/src/main/java/com/example/englishlearning/ui/`）
  - 渲染端：`AppIcon.kt` 里 `if (layer == AppGlyphLayer.Duotone)` 才画 `fillPaths` → StrokeOnly 下填充层**一个像素都不出**
  - 真机取样：底导「设置」= 三条横线、**旋钮圆消失**，且第三条线在 x=14 就断，断口正是旋钮该在的位置；
    今日计划「学习工具与设置」（`Gear`）与「调整词书与目标」（`List`）在 22dp 下**读起来是同一枚图标**（三条线），
    `List` 的三枚圆点（fillPaths）同样不见
  - 视觉真源：稿 `.ic-tint svg { stroke: currentColor }` + `svg.g { fill:none; stroke:currentColor }` —— 稿里 `<circle>` 与内幅一律是**描边**，
    在任何档位都可见；§6.1 设置形（三条滑杆 + `circle cx=17 cy=17 r=2.5`）的圆是勾出来的，不是填出来的
    （重算：`grep -n "ic-tint\|svg.g {" docs/design/2026-10-17-ui-redesign-mockups.html`）
- Observed: 转录时把稿里的 `<circle>` / 内幅形状一律塞进 `fillPaths`，于是「弱档 + 底导」这条主路径上它们集体蒸发。
- Expected: 稿面口径 —— 识别性形状在任何档位都可见；`Clock`/`Search`/`Camera`/`Person`/`Info` 五枚已经是**双层同形**的正确先例
  （同一个圆既在 `strokePaths` 又在 `fillPaths`），`Gear`/`List`/`Book`/`Image` 没跟上。
- User/design impact: 「图标生成不全 / 一半一半」的直接来源；两行图标不可区分还会让「设置」与「列表」两类入口在扫读时混成一片。
- Recommended fix（**属范围变化，先改决策文档再动码**）：
  1. 给圆形加一枚**描边档**标记（例如 `CircleStroke(cx, cy, r)`），由 `AppIcon.kt` 解释成同一段圆弧、进 `strokePaths`；
     同步 `AppIcons.kt` 文件头那条「`CircleFill` 绝不允许出现在 `strokePaths`」的禁令与 `docs/decisions/2026-10-18-...-rulings.md` 的裁决 4。
     （不建议反过来「让弱档也画填充层」——`InlineIconBadge` 的注释已写明浅底上铺实心层会把字形糊成一坨。）
  2. 逐枚补形清单（凡 `fillPaths` 承载识别特征的都要核）：`Gear` 旋钮、`List` 三圆点、`Book` 左页/书脊、`Image` 日头、
     `Newspaper` 报头带、`Document` 正文块、`Translate` 小 A、`Upload`/`Download` 箭头头、`Play`/`Pause`/`Stop`、
     `Warning` 内三角、`Bell` 钟体、`Trash` 桶身、`Bookmark`/`BookOpen` 左页。
  3. 补一枚**新契约**堵住这个缺口：现有测试只钉容器尺寸/墨色/层开关，没有一条钉「StrokeOnly 下字形仍可区分」。
     最小可执行版：对上面这批字形断言「`strokePaths` 单独成形的节点数 ≥ 阈值」，并另开一枚 connected 像素测
     （同一枚字形在 Tint 与 Gradient 两档下的非背景像素占比差不得超过一个定值）。
- Verification: 改后重跑 `:app:testDebugUnitTest` 全量 + 底导/今日计划两屏真机截图逐枚目视（放大取样法同本报告），
  并确认 `AppIconsStructureContractTest` 与 `IconBadgeContrastContractTest` 双绿。

### B-2 `Star` 与 `Bookmark` 是同一轮廓，且 `Star` 完全不像星

- Severity: **blocker**（同屏出现三次同款「书签」，用户读作「变形」）
- Evidence: `AppIcons.kt` 里 `Star` 的两层同串 `M6.4 4h11.2v15.2L12 15.6l-5.6 3.6z`（矩形 + 底边凹口），
  `Bookmark` 是 `M6 4h12v16l-6-3-6 3z`（同一形状，只差 0.4–1.6 个单位的缩放）；
  真机取样：今日计划「生词本」「我的词」两枚粉色书签 + 「打卡与成就」一枚橙色书签 = **三枚同形**。
- Expected / 分类: 这是**源真值缺陷**，不是实现偏离 —— 稿 `#i-star-o` / `#i-star-f` 就是这个串（重算：
  `grep -n "i-star" docs/design/2026-10-17-ui-redesign-mockups.html`），稿 §7② 收藏行用的 `M6 4h12v16l-6-3-6 3z` 与它同族。
  实现逐字转录，忠实于稿；稿本身错了。→ `needs-design-decision`，但后果是 blocker 级用户可见。
- Recommended fix（两案，推荐 ①）：
  ① **换语义不换几何**：「打卡与成就」改回稿 §6.1 的定稿形 `Chart`（折线，注释里就写着「§6.1『学习记录』折线形；打卡页」），
     「学习记录」现在错挂 `Clock`（`Clock` 在决策清单里是「时刻与提醒行」）→ 让 `Chart` 去打卡与成就、`Clock` 归提醒行，
     `Star` 从今日计划撤下（决策清单本就写着它「批 2/3 计划未钉站点，用前先补决策文档清单」）。
     代价最小：只改两枚 `AppIcon` 具名实参，不动任何路径数据。
  ② 重绘 `Star` 为真五角星（稿 §6.1「AI 生成」那枚四芒星已被 `Spark` 占用，重绘要同时避免与 `Spark` 撞），
     属字形级创作 + 决策文档清单更新，成本明显高于 ①。
- Verification: 真机今日计划截图逐行核「七枚容器七种轮廓」，并补一条 JVM 契约：同一屏内入口行的 `AppIcon` 不得重复
  （今日计划现盘 `Learn` 域两枚、`Settings` 域两枚，重复本身可接受，**轮廓**重复才要拦）。

---

## Major issues

### M-1 `BookOpen` 左右页不等宽 + 书脊下垂凸角，22dp 下读作「画歪」

- Severity: major（源真值同病 → 归类「设计歧义/稿子缺陷」，需裁决）
- Evidence: 左页 x 4→13（9 单位）、右页 x 14→20（6 单位），两页底边都在 y=17，但书脊两侧各有一个 y=19 的下垂点
  （`a3 3 0 00-3-2` / `a3 3 0 013-2`）→ 放大取样里书脊下方挂着一枚小凸角，右页明显比左页窄。
  稿 §7② 词书单词行是**同一串坐标**（重算：`grep -n "M4 5h7a2 2" docs/design/2026-10-17-ui-redesign-mockups.html`）。
- Recommended fix: 左右页对称化（4→12 / 12→20），下垂凸角要么两侧都做且做成页底弧、要么去掉改平页底；
  改前先更新稿 §6.1 与决策清单（本仓口径：字形几何以稿为准，实现不得自行改形）。

### M-2 今日计划入口容器从稿定 3 枚涨到 6 枚，一页出现 5 个色相

- Severity: major（用户指认的「视觉疲劳」，实现放大 + 源真值本身偏多）
- Evidence: 稿 §7② 的「我的词汇」组只有 3 行（`--d-library` / `--d-learn` / `--d-reading`），
  今日文章卡 1 枚实底 `--d-reading`，连续与记录用 `--d-review` 环 → 稿面这一屏 4–5 个色相、3 枚彩色容器。
  现盘今日计划 6 枚 `EntryRow` 全 `EntryIconTone.Tint`：`Learn`(蓝) / `Reading`(粉) / `Learn`(蓝) / `Review`(橙) / `Settings`(灰) / `Settings`(灰)，
  加上薄荷品牌 CTA、工作量条的三段（薄荷/橙/蓝）与三枚同色图例点 → **同屏 5 个色相、9 个彩色块**（截图现算）。
  重算：`grep -c "EntryRow(" app/src/main/java/com/example/englishlearning/ui/TodayPlanScreen.kt`
- Observed: 两枚相邻粉书签（生词本 / 我的词）、两枚相邻灰三线（学习工具与设置 / 调整词书与目标）——
  色相重复 + 轮廓重复（见 B-1/B-2），是「乱」和「杂」同时发生的现场。
- Expected: 稿的分组口径（一组 3 枚、同组同域）；用户 2026-10-22 的新诉求（「底部颜色不能太杂乱」）是**契约变化**。
- Recommended fix: 见下一节配色收敛方案（需用户拍板，不得自行改色）。

---

## 配色收敛方案（`needs-design-decision`，等用户选）

现盘容器底色公式：`lerp(surface, deep, 0.16)`（= 稿 `color-mix(in srgb, var(--d) 16%, var(--surface))`），字形 = 域 `deep` 单层描边。
所以「杂」不是饱和色块，而是**六种中等明度的色相并列**；真正的品牌色（薄荷）反而被挤成其中一格。

- **方案 A（推荐）—— 同组同色 + 容器中性化**：入口容器底统一走 `AppColors.tint`（薄荷浅底，全站一档），
  域色只留在**字形描边**上，且只在「跨组识别」时出现（同组内不重复上色）。
  一页色相数从 5 降到 2（薄荷 + 中性），保留用户当初要的「图标有颜色」，灭掉色块并列。
  代价：`IconBadgeContrastContractTest` 里「`deep` 压 16% 混色底」那 28 个组合要按新配对（`deep` 压 `tint`）重算，
  `Settings` 域的 `deep #48484A` 压薄荷绿底可能跌破 3:1，需要给中性域另定墨色。
- **方案 B —— 每屏限 2 个域色**：今日计划收成 2 组（学习类=蓝、记录类=橙、系统类=灰），同组同色，其余降为 `Settings`。
  代价最小（只改具名实参），但「灰块」会变多，设置页尤其明显。
- **方案 C —— 降饱和不降数量**：容器 16% → 8–10%，字形墨 `deep` → `text1`，只留极淡色相做识别。
  代价：域色识别度下降，且 `deep → text1` 会让「域色 = 语义」这条编码失效，等于放弃域色体系。
- **方案 D —— 只留品牌色**：入口容器一律薄荷 `tint` + 品牌墨描边字形，域色只用于数据可视化（进度条、图例、打卡环）。
  最干净，也最像「全站一个颜色」，与用户「大部分应用的设置都有彩色图标」的原始诉求冲突最大。

无论选哪案，按 AGENTS.md 的口径都要：**先改 `docs/superpowers/specs/2026-10-17-ui-redesign-design.md` §2.1 的域色用途表 + 视觉稿，再动码**，
并同步 `RawColorLiteralRatchetTest` / `DarkTokenConsumptionRatchetTest` / `SharedComponentUniquenessRatchetTest` 三道棘轮（只减不增）。

---

## Design Debt

- **D-1 两档混用无口径**：全站 `EntryRow` 只有约一半显式传 `iconTone = Tint`，其余吃默认 `Gradient`
  （重算：`grep -rn "EntryRow(" app/src/main/java/com/example/englishlearning/ui/ | wc -l` 与
  `grep -rn "iconTone = EntryIconTone.Tint" app/src/main/java/com/example/englishlearning/ui/ | wc -l` 现算，本文件不抄死数）。
  同屏两档并列时，实底块与浅底块混排，正是「杂」的另一半。建议：把默认档定成弱档，实底只留给稿 §7② 那种「卡内主行」。
- **D-2 填充层在主路径上是死码**：`DuotoneFillAlpha`（浅 0.42 / 深 0.30）与整条 `fillPaths` 只在 `Gradient` 档与少数组件里出现。
  若 B-1 选「StrokeOnly 补形」，必须同步决策文档，否则「B 案 = 双层 duotone」名存实亡。
- **D-3 契约盲区**：现有图标契约钉尺寸/墨色/层开关/枚数，**没有任何一条钉「StrokeOnly 下字形仍可识别」**；
  `AppIconsStructureContractTest` 只保证「两层都在」，不保证「少一层还能认」。
- **D-4 取证缺口**：本报告只覆盖浅色 · fontScale 1.0 · 今日计划一屏。深色档（域色 `deep = base`，容器 16% 混色底在
  `#17201C` 上会更接近纯色块）与 `fontScale 2.0`（行高、容器与文字挤压）未测，AC-R-10/11 的真机六屏对照仍欠。

## Acceptable differences

- 演示数据（今日 0/12、生词本 2 个）与稿面示例数（4/10、34·12·8）不同 —— 属动态内容，已按 masking 处理，不计偏离。
- 「生词本」这一行是 22-A 正在摘除的老路由残留（截图装的是 10-19 的包），不计入现盘版面结论。
- 底导四 tab 骨架不动（用户 2026-10-17 已拍板），本报告的入口收敛全部限页内。

## Automation Artifacts

- 人工取样（仓库外，不入库）：`C:\Users\steveliuyan\qa-scratch\phone-2026-10-22.png`、`crop-col-a.png`、`crop-col-b.png`、
  `crop-col-c.png`、`crop-tabbar.png`、`crop-icons.ps1`
- JSON 报告：无（插件管线不适用 Android Compose，见 Evidence Reviewed）
- diff 图：无（无历史基线截图，本轮起可考虑把这两屏的取样定为基线）

## Verification Checklist

- [x] 视觉真源与渲染实现取自同一屏、同一主题档、同一状态
- [x] 五个 fidelity surface 逐面检查：排印（本轮未见新缺陷，字号档归批 1 移交账）、间距（入口行 56dp 下限有 connected 护栏）、
      颜色与 token（见配色方案）、图像与素材（icon style 一致性 = B-1/B-2/M-1）、文案（稿「我攒的词」vs 现盘「我的词」已在内容审计登记）
- [ ] 深色档与 `fontScale 2.0` 复测（D-4）
- [ ] B-1 修复后重跑 `:app:testDebugUnitTest` 全量 + 真机逐枚目视
- [ ] 配色方案落地前先改 spec §2.1 + 视觉稿，并核三道棘轮只减不增
- [ ] 设计负责人（用户本人）确认 B-2/M-1 的字形重绘与配色档位选择
