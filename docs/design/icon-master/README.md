# 图标视觉母版（calm 分色档）

本目录是「绘画感图标」的视觉母版：放大看的插画稿（1024×1024 PNG）。
**2026-10-30 起，这 28 枚去底后直接进运行态**（见下文「与工程的落点关系」）；
`ui/components/AppIcons.kt` 的 24×24 `ImageVector` 只剩小尺寸形态（行内徽标、底部导航、箭头符号）在用。

- 风格权威：`STYLE-CONTRACT.md`（冻结合同，任何新枚都从它派生）
- 逐枚提示词：`prompts/`（20 份：13–28 复刻批 + 07/08/10 修色批 + 11 定稿；被否的候选在 `prompts/rejected/`）
- 统一性审计：`audit/`（脚本 + 逐枚实测 JSON + 放行判定，可复跑）
- 运行态资源脚本：`tools/prepare-drawables.js`（去底 / 裁切 / 归一 / 降采样）
- 评审页：`docs/design/2026-10-28-icon-master-sheet.html`

## 文件

| 文件 | 说明 |
| --- | --- |
| `icon-color-calm__01-book.png` … `icon-color-calm__28-stop.png` | calm 分色档 28 枚母版，1024×1024 PNG |
| `icon-color-calm__overview-grid.png` | 全 28 枚 7×4 总览（1916×1188，随本轮 11 定稿一起刷新） |
| `icon-color-vivid__overview-grid.png` | 备选「高饱和」档总览（未采用，存档） |
| `icon-master__01-*.png` … `icon-master__12-*.png` | 上一版「统一墨色」母版（弃用，存档） |
| `archive-drift/` | 回溯存档：修色前的 07 / 08 / 10 / 11 旧图（`*.png`），二次重出前的 08 / 16 / 25 旧图（`*.prev.png`），11 被否的琥珀候选（`*.review-rejected.png`） |
| `prompts/` | 逐枚提示词；`prompts/rejected/` 存被否的候选（`11-upload-review-amber.txt`） |
| `audit/` | `audit-full.ps1` / `audit-lift.ps1` / `verdict.ps1` 三个审计脚本 + `make-montage.ps1` 总览图生成，`icon-full-audit.json` / `icon-lift-audit.json` 逐枚实测，`icon-verdict.json` 放行判定 |

## 批次

| 批次 | 枚数 | 说明 |
| --- | --- | --- |
| 基准 | 01–06、09、12 共 8 枚 | 你已认可的那一版原图，未改动 |
| 修色重出 | 07 spark、08 chart、10 shield 共 3 枚 | 原图色相对不上任何域，按同域基准图重出（旧图在 `archive-drift/`） |
| 复刻 | 13–28 共 16 枚 | 上一轮「合同被压缩成一句话」导致形态漂移，本轮按冻结合同 + 同域参考图全部重出 |
| 二次重出 | 08 chart、16 star、25 trash 共 3 枚 | 审计卡在亮面太小 / 描边偏细 / 亮面缺失，重写 `ICON SUBJECT` 点名三个色位后重出（旧图在 `archive-drift/*.prev.png`） |
| 定稿 | 11 upload | 域归属裁决为 **Library 词库**（主语义是导入词库：`SettingsScreen.kt` 两处传 `Library`；`BackupHubScreen.kt` 的恢复入口传 `Settings` 属另一场景），琥珀候选移入 `archive-drift/` |
| 裁决定稿 | 12 download | 用户裁决**沿用基准原图**：无 AiSpeech 调用点（只有 `SettingsScreen.kt:407` → Library、`BackupHubScreen.kt:75` → Settings），但图已被认可，故不重出、不改主语义域，像素未动 |

## 图标与颜色系对应

色系全部取自 `ui/theme/AppColors.kt` 的轻松档 `DomainLight` / `DomainDark`。
每枚图标的「主面 / 亮面 / 深笔」对应母版三色位 `base` / `light` / `deep`，`light` 是该域
`base` 抬亮一档的派生值（仅用于母版，不新增 token）。

| # | 文件 | `AppIcon` | 功能域 | base | light | deep | 批次 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 01 | book | `Book` | Learn 学习 | `#7BB4E6` | `#CDE6F7` | `#175A93` | 基准 |
| 02 | books | `BookOpen` | Library 词库 | `#B7A0D9` | `#E4DAF2` | `#65478A` | 基准 |
| 03 | clock | `Clock` | Review 复习 | `#EBC68B` | `#F7E7CB` | `#8A5712` | 基准 |
| 04 | document | `Document` | Settings 设置 | `#A6B0AC` | `#DCE4E0` | `#4A5551` | 基准 |
| 05 | microphone | `Mic` | AiSpeech 语音 | `#6CCBAB` | `#CFEDE2` | `#1A7A63` | 基准 |
| 06 | speaker | `Speaker` | Reading 阅读 | `#E297A4` | `#F6DCE0` | `#9C3B4F` | 基准 |
| 07 | spark | `Spark` | AiSpeech 语音 | `#6CCBAB` | `#CFEDE2` | `#1A7A63` | 修色 |
| 08 | chart | `Chart` | Learn 学习 | `#7BB4E6` | `#CDE6F7` | `#175A93` | 修色 |
| 09 | camera | `Camera` | Danger 危险 | `#DD8B81` | `#F7DCD6` | `#9E2B24` | 基准 |
| 10 | shield | `Shield` | Settings 设置 | `#A6B0AC` | `#DCE4E0` | `#4A5551` | 修色 |
| 11 | upload | `Upload` | Library 词库 | `#B7A0D9` | `#E4DAF2` | `#65478A` | 定稿 |
| 12 | download | `Download` | AiSpeech 语音 | `#6CCBAB` | `#CFEDE2` | `#1A7A63` | 基准（v4 裁决定稿） |
| 13 | search | `Search` | Danger 危险 | `#DD8B81` | `#F7DCD6` | `#9E2B24` | 复刻 |
| 14 | list | `List` | Library 词库 | `#B7A0D9` | `#E4DAF2` | `#65478A` | 复刻 |
| 15 | bookmark | `Bookmark` | Reading 阅读 | `#E297A4` | `#F6DCE0` | `#9C3B4F` | 复刻 |
| 16 | star | `Star` | Review 复习 | `#EBC68B` | `#F7E7CB` | `#8A5712` | 复刻 |
| 17 | translate | `Translate` | Learn 学习 | `#7BB4E6` | `#CDE6F7` | `#175A93` | 复刻 |
| 18 | image | `Image` | AiSpeech 语音 | `#6CCBAB` | `#CFEDE2` | `#1A7A63` | 复刻 |
| 19 | newspaper | `Newspaper` | Reading 阅读 | `#E297A4` | `#F6DCE0` | `#9C3B4F` | 复刻 |
| 20 | gear | `Gear` | Settings 设置 | `#A6B0AC` | `#DCE4E0` | `#4A5551` | 复刻 |
| 21 | person | `Person` | Settings 设置 | `#A6B0AC` | `#DCE4E0` | `#4A5551` | 复刻 |
| 22 | info | `Info` | Settings 设置 | `#A6B0AC` | `#DCE4E0` | `#4A5551` | 复刻 |
| 23 | bell | `Bell` | Review 复习 | `#EBC68B` | `#F7E7CB` | `#8A5712` | 复刻 |
| 24 | warning | `Warning` | Danger 危险 | `#DD8B81` | `#F7DCD6` | `#9E2B24` | 复刻 |
| 25 | trash | `Trash` | Danger 危险 | `#DD8B81` | `#F7DCD6` | `#9E2B24` | 复刻 |
| 26 | play | `Play` | AiSpeech 语音 | `#6CCBAB` | `#CFEDE2` | `#1A7A63` | 复刻 |
| 27 | pause | `Pause` | AiSpeech 语音 | `#6CCBAB` | `#CFEDE2` | `#1A7A63` | 复刻 |
| 28 | stop | `Stop` | AiSpeech 语音 | `#6CCBAB` | `#CFEDE2` | `#1A7A63` | 复刻 |

## 未出母版的 5 枚

`Back` / `ChevronRight` / `ChevronDown` / `Close` / `Check` 是纯描边的导航符号
（`StrokeOnlyIcons`），工程契约里按单层描边绘制，不需要母版，也不参与配色。

## 逐枚量色结果

28 张 PNG 采样的实测值记在评审页的「逐枚量色」表里。结论：

- 28 / 28 枚主面与亮面都落在本域色板的正常偏差内（`base` Δ ≤ 42）。
- 修正前有 4 枚色族整体错位：`07 spark`（金）、`08 chart`（紫）、`10 shield`（冷蓝灰）、`11 upload`（薄荷）。
  四枚现已全部按同域基准图重出并通过量色；`11 upload` 最终落在 Library 词库紫
  （实测 `#BDA3E2` 对 `#B7A0D9`，`base` Δ 11.2）。
- `deep` 簇的 Δ 普遍偏大（最高 44）属正常：描边只有 2px，采样点会和相邻面块混色。
- `12 download` 是唯一 `base` Δ 逼近阈值的（40 / 42），已由用户裁决沿用原图，不计为待修项。

## 统一性审计（v3，以基准实测分布标定）

审计脚本对 28 枚逐项量了 9 项判据（几何、三簇占比、亮面存在性、色距、域内色），
放行区间全部由基准 8 枚（`01 02 03 04 05 06 09 12`）的实测分布反推，明细见
`STYLE-CONTRACT.md` 的「数字化的判据（v3）」表。全部产物（三份 JSON + 总览图）
只需按顺序跑 `audit/` 里四个脚本即可复现，输出落在 `audit/` 目录：

```powershell
$a = 'H:\EnglishLearningSoftware\docs\design\icon-master\audit'
$m = 'H:\EnglishLearningSoftware\docs\design\icon-master'
& powershell -NoProfile -File "$a\audit-full.ps1"
& powershell -NoProfile -File "$a\audit-lift.ps1"
& powershell -NoProfile -File "$a\verdict.ps1"
& powershell -NoProfile -File "$a\make-montage.ps1" -Dir $m -Out "$m\icon-color-calm__overview-grid.png" -Cell 260 -Pad 12 -Cols 7
```

| 判定 | 枚数 | 说明 |
| --- | --- | --- |
| 通过 | 28 | 9 项几何 / 色彩 / 亮面对齐项全部落在基准区间内 |
| 不通过 | 0 | 11 upload 定稿为 Library 后，`base` 52.2% / `deep` 35.1% / 域外 9.0% / `dB` 11.2（阈值 42），全部落回基准区间 |

标定过程中被推翻的三条旧判据（详细原因见合同文件）：

1. 四角 `#F6F9F6` ±3：按欧氏距离会把基准 8 枚判死 7 枚，改为按通道差 ≤8（基准实测 1–4）。
2. 主体占比 66–72%：基准实测 27.3–74.6，改为长边 24–78%。
3. `light` 簇恰好 1 处连通：基准实测 10–114 处（抗锯齿碎块），改为亮度抬亮带的存在性检测。

二次重出的 3 枚（`08 chart` / `16 star` / `25 trash`）第一轮分别卡在亮面太小、描边偏细、亮面缺失，
重写 `ICON SUBJECT` 段落（明确要求可辨识的亮面与"not a hairline"的描边）后全部落回基准区间。

## 与工程的落点关系（2026-10-30 起：位图直落）

**结论先行**：28 枚母版现在**直接进运行态**，不再翻译成 24×24 矢量。
去底后的 256×256 PNG 落在 `app/src/main/res/drawable-xhdpi/app_icon_<name>.png`，
由 `ui/components/AppIllustrations.kt` 的映射表接进两处：`EntryIconBadge`（40dp 入口容器，位图 36dp `Fit`，
容器不铺底色）与 `InlineIconBadge`（30dp 行内徽标，位图 26dp，保留 `tint` 底）。完整口径见
`docs/decisions/2026-10-30-icon-illustration-bitmap-landing.md`。

只有 5 枚 `StrokeOnlyIcons` 和 17dp 行内小字形继续走矢量（几何仍是 `AppIcons.kt` 的三层路径）。

复现运行态资源（去底 → 裁切 → 归一 → 256 降采样）：

```powershell
$m = 'H:\EnglishLearningSoftware\docs\design\icon-master'
& "C:\Program Files\nodejs\node.exe" "$m\tools\prepare-drawables.js" batch $m "$m\..\..\..\app\src\main\res\drawable-xhdpi" 256 0.06
```

历史注记：2026-10-29 曾按「层到层」重绘三枚矢量样板（`Clock` / `Book` / `Gear`），
该路线经用户否决后作废，样板几何保留在 `AppIcons.kt` 供小尺寸使用。

| 母版元素 | 位图直落后的对应 |
| --- | --- |
| 主面 / 亮面 / 内高光 | 位图自身像素，不再分层重建 |
| 深色块面 / 局部实心 | 同上 |
| 深色细描边 | 同上 |

契约测试：`AppIllustrationContractTest`（枚举/文件/源码三面）取代原先的矢量重绘验收要求。

运行态证据（2026-10-30 实机摘取，见 `docs/design/illustrated-icons-runtime/`）：
`IconGalleryRenderTest` 输出的三容器 × 浅/深画廊图与 28 枚总览，另加模拟器真实界面四张：
`app-today-plan-bare-rows-light`（今日学习两条裸行）、`app-settings-entries-light-top` /
`-light-scrolled`（我的页入口浅色上下半屏）、`app-settings-entries-dark`（深色）。
复验命令（**只能 `am instrument`，禁用 `connectedDebugAndroidTest`**，该类会卸载 APK
清空设备本地数据）：

```powershell
$adb = 'C:\adb\platform-tools\adb.exe'
& $adb shell am instrument -w -e class com.example.englishlearning.ui.IconGalleryRenderTest `
  com.example.englishlearning.test/androidx.test.runner.AndroidJUnitRunner
& $adb pull /sdcard/Android/data/com.example.englishlearning/files/ 'C:\tmp\iconshots\files'
```

## 风格口径（摘要，全文见 `STYLE-CONTRACT.md`）

- 底：`#F6F9F6`（取自 `background`），与页面底色一致。
- 结构：2–3 块实色面 + 恰好一处内高光；描边只作同色族深色细线。
- 禁：渐变、投影、3D 斜面、外框底板、文字。
- 主体包围盒长边占画布 24–78%（基准实测跨度 27–75%），四边最小留白 ≥10%（基准实测 12.5–27.6%）。
