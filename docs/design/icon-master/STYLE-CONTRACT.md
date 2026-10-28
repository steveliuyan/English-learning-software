# 图标母版风格合同（冻结版）

本文件是 `icon-master/` 全部母版的**唯一风格权威**。任何新一批图标都从这里派生，
不允许在当轮命令里重新描述风格、重新推配色。

## 为什么需要这个文件

前两批母版的漂移是可以定位到具体原因的，不是随机的：

| 批次 | 用的提示词 | 结果 |
| --- | --- | --- |
| 第 1 批（`icon-master__01…12`） | 一份完整的多段式风格合同，深笔画统一一支墨青 | 形态统一，但你要的"分色"没有 |
| 第 2 批前段（`icon-color-calm__01…12`） | 同一份完整合同，只把颜色换成各域色系 | **基准版**，你说"可以了" |
| 第 2 批后段（`icon-color-calm__13…28`） | 合同被压缩成一句话，写在临时命令里、未存盘 | 面块数、高光位置、描边权重、留白比例全部重新采样 → 形态开始和基准不像 |
| 第 3 批 | 合同重新改写措辞、配色重新推 | 形态与颜色双重漂移 |

所以"失忆"的准确定位是：**没有任何一份可复用的锁定规范**。本文件补上它。

## 冻结口径：基准 = calm 分色档前 12 枚

以 `icon-color-calm__01-book.png` … `icon-color-calm__12-download.png` 为全组基准。
这 12 枚原图一个像素都不改、文件名不改。它们的共同特征就是下面这份合同的产物。

## 冻结的提示词模板

以下英文段落**逐字冻结**。派生新枚时只能替换 `ICON SUBJECT` 之后的部分与色值，
不得改写、缩写、重排、增删任何一句。

```
STYLE CONTRACT (identical across the whole set):
Soft flat illustration, calm low-stress learning-app aesthetic. Rounded friendly geometry. Each icon is built from two or three overlapping SOLID color masses plus exactly one subtle inner highlight facet, with a thin rounded stroke used only as a secondary accent line - never as the entire icon.

COLOR RULE (the point of this batch): every icon in the set uses a DIFFERENT hue family, so the twelve read as twelve clearly distinct entries instead of one uniform mint family. All colours stay desaturated, gentle and low-stress - no neon, no pure black, no gradient mesh. The accent stroke and the darkest mass of each icon are a DARKER SHADE OF THAT ICON'S OWN hue (never a shared teal ink), so the hue identity survives when the icon is shrunk to 17 px. Soft even lighting from the top left. Crisp vector-clean edges. No photorealism, no 3D render, no glow, no drop shadow of the icon onto the background.

FRAMING: a single icon, centered, occupying about 70% of the frame, on a plain very light neutral off-white background (#F6F9F6), generous even padding.

NO text, NO letters, NO numbers, NO labels, NO logos, NO watermark, NO border, NO rounded-square tile behind the icon - the icon floats directly on the flat background.

ICON SUBJECT:
<这句逐枚替换>

THIS ICON'S COLOUR FAMILY: main mass <base> , lighter inner facet <light> , deepest accent and stroke <deep> . Both the facet and the darkest mass belong to this same hue family.
```

### 三个色位，不是两个

这是第 2 批后段漂移最明显的一处：那一批只给了 `base` 和 `deep` 两个色位，写的是
"exactly two solid color planes"，于是亮面缺口、图标整体偏平。合同要求三个色位齐备：

| 色位 | 作用 |
| --- | --- |
| `base` | 主面（最大块实色） |
| `light` | 唯一一处内高光面 |
| `deep` | 最暗面 + 全部描边 |

### 数字化的判据（v3：以基准实测分布标定）

合同里的定性描述都换算成可以量的项，逐枚要过。

**标定方法：先量基准 8 枚（`01 02 03 04 05 06 09 12`，即 calm 批里未经重出的原图），
把实测分布当成"合格"的定义，再据此设放行区间。**
第一版判据是凭直觉写的（主体 66–72%、四角 ±3、`light` 恰好 1 处连通），
拿基准批自己回测会大面积不合格 —— 那不是基准不合格，是判据写错了，因此重标定如下。

| 项 | 判据（放行区间） | 基准实测 | 说明 |
| --- | --- | --- | --- |
| 背景纸色 | 四角像素**每通道**与 `#F6F9F6` 之差 ≤ 8 | 1–4 | 必须按通道差判，不能按欧氏距离：#F3F6F3 每通道只差 3，欧氏距离却是 5.2，会把基准自己判死 |
| 留白 | 四边中最小一边 ≥ 10% 画布 | 12.5–27.6% | |
| 主体尺寸 | 非背景包围盒**长边** 24–78% 画布 | 27.3–74.6% | 原写"66–72%"不成立：基准实测窄到 27%（麦克风）、宽到 75%（书堆），竖长件与横宽件本来就不同形 |
| 主面存在 | `base` 簇 ≥ 25% 非背景像素 | 34.2–73.9% | |
| 亮面存在 | 「抬亮带」像素 ≥ 2.5%，且最大连通块 ≥ 2% 非背景像素 | 5.1–53.3% / 3.5–48% | 抬亮带 = 亮度 ≥ 目标 `base` 亮度 +12 且 ≤ 目标 `light` 亮度 +26。这是**有无检测，不规定大小** —— 合同原文是 "subtle"，基准自身就从 5% 跨到 53%，无法用统一大小约束 |
| 暗色存在 | `deep` 簇 8–46% 非背景像素 | 12–42% | 低于 8% = 描边退化成发丝线必须重出；高于 46% = 描边吃掉面块 |
| 色相一致 | 三簇均值与目标色板的 RGB 欧氏距离：`base` ≤ 42、`light` ≤ 45、`deep` ≤ 45 | `dB` 3.5–40 / `dD` 2.4–33.3 | 2px 描边与抗锯齿必然混色，不允许要求零偏差 |
| 域内色 | 离三个目标色都 > 48 的像素 ≤ 36% 非背景像素 | 0.3–30.9% | 超过就是混进了八域之外的第八种颜色 |
| 禁用项 | 无文字、无外框、无圆角底板、无渐变网格、无投影 | — | 模板里已硬性禁止，出图后目检 |

**已废弃的两条判据**（保留记录，防止再被写回来）：

| 废弃项 | 原判据 | 废弃原因（基准实测） |
| --- | --- | --- |
| 内高光连通数 | `light` 簇恰好 1 处连通区域 | 基准实测 10–114 处。抗锯齿过渡像素被切成碎块，这条量的是像素级噪声，不是造型 |
| 光照方向 | `light` 簇质心在 `base` 质心左上方 | 基准 8 枚只有 5 枚成立。质心受块面形状支配，不受光向支配，不能当门槛 |

### `ICON SUBJECT` 的写法（血泪教训）

三个色位写在末尾的 `COLOUR FAMILY` 行里，但模型是否真的把三块都画出来，
主要由 `ICON SUBJECT` 那句是否点了名决定。写得太笼统就会出现"只有 `base` + `deep`、亮面没了"
（`25 trash` 首轮亮面 0.2%）或"描边细成发丝"（`16 star` 首轮 `deep` 6.4%）：

| 反例 | 正例 |
| --- | --- |
| "A waste bin with a lid, a handle and two ribs." | "... The bin body is the main solid mass; **a clearly visible inner highlight facet sits on the upper left of the body**; the lid, the ribs and the outline are the deeper accent colour." |
| "A five point star with rounded tips, one facet filled darker." | "... one **clearly visible** inner highlight facet in the upper left arm; one opposite arm filled with the deeper accent colour; every edge carries the deeper accent colour **as a clear rounded outline, not a hairline**." |

即：**每个色位都要在 `ICON SUBJECT` 里被具体点名并指定位置**，不能只靠末尾那行色值。

## 七域色板（唯一配色来源）

色值全部取自 `ui/theme/AppColors.kt` 的轻松档 `DomainLight`，`light` 是该域 `base` 抬亮一档
的派生值（不是新增 token，只用于母版生成）。

| 域 | 落点用途 | `base` | `light` | `deep` |
| --- | --- | --- | --- | --- |
| Learn 学习 | 词书、进度图表、翻译 | `#7BB4E6` | `#CDE6F7` | `#175A93` |
| Library 词库 | 书堆、列表、上传（备份导出） | `#B7A0D9` | `#E4DAF2` | `#65478A` |
| Review 复习 | 时钟、星标、提醒铃 | `#EBC68B` | `#F7E7CB` | `#8A5712` |
| Settings 设置 | 文档、齿轮、头像、信息、盾牌 | `#A6B0AC` | `#DCE4E0` | `#4A5551` |
| AiSpeech 语音 | 麦克风、火花、图片、播放三键、下载 | `#6CCBAB` | `#CFEDE2` | `#1A7A63` |
| Reading 阅读 | 喇叭、书签、报纸 | `#E297A4` | `#F6DCE0` | `#9C3B4F` |
| Danger 危险 | 相机、搜索、警告、删除 | `#DD8B81` | `#F7DCD6` | `#9E2B24` |

一枚图标用哪个域，由它将来在界面里的落点决定，不由造型决定。
七域之外的第八种颜色一律不允许出现。

## 派生新枚的固定步骤

1. 从色板表取该枚所属域的 `base` / `light` / `deep`。
2. 复制上面的冻结模板，只替换 `ICON SUBJECT` 那一段和三个色位。
3. 把同域基准母版作为参考图喂进生成（`--image`），让面块数、高光位置、描边权重、
   留白这四个最容易漂移的项直接跟着基准走，只让主题形状变化。
4. 出图后按「数字化的判据」逐项量，任何一项不过就重出那一枚，不许放宽判据。
5. 提示词存进 `prompts/`，不留在临时目录。

## 变更流程

改本文件 = 改全组风格。必须一次改完、写清改的是哪一项、并说明已出的哪些枚需要重出。
只在当轮命令里临时改措辞是不允许的 —— 那正是上一批漂移的成因。

## 执行记录：2026-10-28 v2（28 枚全量对齐）

### 做了什么

1. 13–28 共 16 枚按本合同的冻结模板 + 同域参考图全部重出（`prompts/13…28`）。
2. 对 28 张 PNG 逐枚采样量色，与七域色板比对，发现 **4 枚色族整体错位**：
   `07 spark` 是金色、`08 chart` 是紫色、`10 shield` 是偏冷蓝灰、`11 upload` 是薄荷绿。
   前三枚属于基准批里就存在的错色（对不上任何域的色板），已按同域参考图重出，
   旧图移入 `archive-drift/`；`11 upload` 的域归属本身有冲突，当时留待裁决（**已于 v3 裁决为 Library 词库，见下节**）。
3. 修色后复量：`07 / 08 / 10` 三簇齐全，主面 Δ 分别为 12.5 / 10.7 / 4.5，落回正常区间。

### 本轮的实际判定口径（诚实记录）

- **已全部量化并跑完**：上面 9 项判据全部写进审计脚本，对 28 枚逐枚量完，结果在
  `icon-full-audit.json`（几何 / 占比 / 色距）、`icon-lift-audit.json`（亮面存在性）、
  `icon-verdict.json`（放行判定）。**当前 28 枚全部通过**（首轮为 27 通过 / 1 不通过，
  不通过的那枚是 `11 upload`，定稿 Library 后已复测通过，见下节）。
- **标定过程中的三次纠错**（都记在这里，因为它们是判据本身的错，不是母版的错）：
  1. 四角"`#F6F9F6` ±3"用欧氏距离实现 → 基准 8 枚里 7 枚被判死。改为按通道差 ≤8，基准实测 1–4。
  2. "主体占比 66–72%" → 基准实测 27.3–74.6，跨度极大。改为长边 24–78%。
  3. "`light` 簇恰好 1 处连通" → 基准实测 10–114 处（抗锯齿碎块）。改为亮度抬亮带的存在性检测。
- **7 / 08 / 10 的重出判据**：这三枚是基准批里本来就配错色的（金色 / 紫色 / 冷蓝灰，对不上任何域），
  按同域参考图重出后复量，主面 Δ 为 12.5 / 10.7 / 4.5，落回基准区间。
- **`08 chart` / `16 star` / `25 trash` 的二次重出**：这三枚在第一轮判定里分别卡在
  `light` 3.3% / `deep` 6.4% / `light` 0.4%，重写 `ICON SUBJECT`（明确要求"clearly visible"的
  亮面与"clear rounded outline, not a hairline"的描边）后重出，现为
  `light` 3.3%(抬亮带) / `deep` 23.3% / 抬亮带 3.6%，全部落回基准区间。

### `11 upload` 的域归属冲突（已裁决：Library 词库）

| 出处 | 记的域 | 色 |
| --- | --- | --- |
| 本目录 `README.md` 登记表（旧） | Review 复习 | 琥珀 `#EBC68B` |
| `SettingsScreen.kt:147`（导入词库包入口） | Library 词库 | 紫 `#B7A0D9` |
| `SettingsScreen.kt:392`（导入 `.wbpack`） | Library 词库 | 紫 `#B7A0D9` |
| `BackupHubScreen.kt:97`（从旧设备恢复） | Settings 设置 | 中性灰 `#A6B0AC` |

**先纠一条自己写错的依据**：裁决时我写的是「代码唯一传 Library」，实际是 3 个调用点、
2 个 Library + 1 个 Settings。结论不变（用户裁定走 B 档 = 词库紫），但依据要改准确：

1. 主语义是「把外部内容导进来」（导入词库包 / 导入 `.wbpack`），2 / 3 个调用点、且是
   设置页数据区的两个主入口都传 `AppDomain.Library`；备份中心那个「恢复」是同一动作的
   另一场景。
2. 母版定的是**主语义域**，运行态 tint 始终由调用点决定 —— 同一字形在不同页面染不同域色
   是工程既有口径（`Download` 同样如此：`SettingsScreen:407` 是 Library、
   `BackupHubScreen:75` 是 Settings）。母版不需要、也不可能同时表示两个域。

- 定稿文件即 `icon-color-calm__11-upload.png`；实测 `base` 52.2% / `deep` 35.1% /
  域外色 9.0% / `dB` 11.2（簇主色 `#BDA3E2` 对 `#B7A0D9`），9 项判据全过。
- 废弃物已归档，不再留在正式目录：旧薄荷绿 → `archive-drift/icon-color-calm__11-upload.png`，
  琥珀候选 → `archive-drift/icon-color-calm__11-upload.review-rejected.png`，
  琥珀提示词 → `prompts/rejected/11-upload-review-amber.txt`。
- **教训**：域归属散落三处（README 登记表、`SettingsScreen.kt`、`BackupHubScreen.kt`）
  会让母版和运行态各走一边。登记表的「功能域」列只表示**母版配色所用的主语义域**，
  不是运行态 tint 的清单；两者混为一谈就会得出「代码唯一传 X」这类错结论。

## 执行记录：2026-10-28 v3（`11 upload` 定稿 + 全量复测）

1. `audit/audit-full.ps1` 与 `audit-lift.ps1` 里 `11 upload` 一行由 `Review` / `pending`
   改为 `Library` / `fix`；两个脚本与新增的 `verdict.ps1`、`make-montage.ps1` 的输出
   默认落到 `audit/` 目录，不再写临时目录（复跑只需按 full → lift → verdict → montage
   顺序执行，不传参）。
2. 重跑三脚本：**28 / 28 通过**，无一枚落在基准区间外。
3. 同步物：`README.md` 登记表与批次表、`prompts/`（二选一收为一份）、
   评审页 11 卡片（A/B 待定态改为定稿）、`icon-color-calm__overview-grid.png` 重刷。
4. 至此 calm 分色档的母版阶段收口：28 枚母版、提示词、审计三条链路都是绿的。
   下一阶段是把 28 枚映射回 `AppIcons.kt` 的 24×24 矢量字形（见 `README.md` 映射表）。

### v3 的收口遗留（下一阶段开工前必须先定）

- **多调用点字形的「母版主语义域」只复核了 `11 upload`**。复核时把 28 枚的调用点全部扫了一遍，
  结论是「一个字形被多个页面染成不同域色」是**常态而非错误**（设置页与阅读访问页都按**页面域**
  给字形上色，例如 `Speaker` 在 `SettingsScreen:132` 染 AiSpeech、`Document` 在
  `ReadingAccessScreen:352` 染 Reading）。这类不算冲突，母版按主语义域定色即可。
- **真正对不上的只有 `12 download`**：登记为 AiSpeech 语音，但代码里只有
  `SettingsScreen.kt:407`（导出词库 → Library）与 `BackupHubScreen.kt:75`（导出学习数据包
  → Settings）两个调用点，**既没有 AiSpeech 调用点，语义上也与语音无关**。
  它是「基准 8 枚」之一（用户已认可、像素未改动），所以不擅自改色，留作下一阶段第一个裁决项。
- 复核口径（已定）：母版「功能域」列 = 该字形的主语义域；运行态 tint = 调用点传入的域；
  一个字形允许多域出现，不要求母版与每个调用点同域。

## 执行记录：2026-10-27 v4（`12 download` 裁决：保留基准原图）

### 裁决

用户就「`12 download` 登记为 AiSpeech 但无 AiSpeech 调用点」给出裁决：**沿用现有这张母版**，
即在 `icon-color-calm__12-download.png` 上定稿，不重出、不改主语义域。

### 核实过程（先验字节，再落文档）

- 用户附件 `icon-color-calm__12-download-1.png` 与工程内 `icon-color-calm__12-download.png`
  **字节完全相同**：SHA256 均为 `5B39B0BCDBD3D0097441E52AFEFC115614ED6E3ECA2DEF3B9F8836BEAE94243D`，
  长度均为 678129 字节。→ 无需替换文件，正式目录不动。
- 该枚为基准 8 枚之一（`01 02 03 04 05 06 09 12`），像素自 v2 起未改动，仍在 `base` 批次。
- 实测偏高的两项（已放行，非缺陷）：`base` 簇主色 `#8FCCBF` 对目标 `#6CCBAB` 的 `dB` 为 40
  （阈值 42，是 28 枚里最高的）；暗色簇 `deep` 42%，处于 8–46% 的上沿。原因是这枚的
  深笔面积大、采样点与相邻面块混色，属基准图既有特征。

### 定稿口径（与 `11 upload` 的区别，避免下次混淆）

| 项 | `11 upload`（v3） | `12 download`（v4，本次） |
| --- | --- | --- |
| 冲突性质 | 域归属登记错（Review ↔ Library），代码无 Review 调用点 | 域归属登记与语义不符，但**图本身已被用户认可** |
| 处理 | 按主语义重出为 Library 紫 | **不重出**，保留基准原图 |
| 主语义域 | Library 词库（`base` `#B7A0D9`） | AiSpeech 语音（`base` `#6CCBAB`，沿用登记） |
| 运行态 tint | 调用点决定 | 调用点决定（`SettingsScreen.kt:407` → Library，`BackupHubScreen.kt:75` → Settings） |

- **教训**：「找不出调用点」只说明运行态 tint 与母版主语义域不同步，不足以单独推翻一张
  已获认可的母版。母版阶段的价值在「确定图标语言」，不是校正代码的 tint 清单；
  裁决权在用户，文档负责把「认可过的那张」与「为什么保留」记清楚。
- 至此 28 枚母版的域归属与像素全部收敛，无待裁决项；审计口径不变（28 / 28 通过）。

