# 全应用颜色与入口一致性审计（2026-10-25）

状态：**审计完成，处置待拍板**（本文件只记录现场与判定；不动代码、不改规格）
触发：用户真机指认三件事——①「查词在主页是紫色图标，点进去又变成绿色」；②「每个选项的颜色都比较多、比较杂」；
③「不是选项，是选项还有图标」（即：选项行与图标两处都要看）。
取证设备：真机 `bf353dda`（深色 · fontScale 1.0）。截图只落 `.git/qa-scratch/color-audit/`，不入库。

---

## 0. 结论（先读这段）

**三件事都不是错觉，三件都有实证根因；其中两件是 2026-10-22 已经拍板、但至今没实施的决策。**

| 用户看到的现象 | 根因 | 状态 |
|---|---|---|
| 选项行五颜六色（一页 4–5 个色相的色块） | 裁决 22-2「入口容器统一 `tint` 底、域色只染字形」未实施：`EntryIconBadge(Tint)` 仍按域色 `lerp(surface, deep, 0.16)` 上底 | **已拍板未实施** → Task 27 |
| 图标半圈 / 两枚图标长得一样 / 不像 | 裁决 22-1 第一步只修了 5 枚圆形图标（2026-10-25）；`Gear` 旋钮、`List` 圆点、`Book` 左页等识别特征仍只画在填充层，`Tint` 档整层不显示 | **已拍板、部分实施** → Task 27 剩余 |
| 主页紫色「查词」→ 目标页整屏薄荷绿 | 18 个二级页仍把 `MintPrimary` 当「通用主强调色」；而 `MintPrimary` 的定义是 **`AppDomain.AiSpeech` 的 base**（`#2EC99C`）——等于每一页都被涂成了「AI 语音」域色 | **新发现，未拍板** → 本文件第 5 节，需裁定 |

一句话：**不是配色方案错，是「域色 = 身份」这套编码只落地在入口徽章，没落到目标页；同时入口徽章自己还顶着一块一块的彩色底。**

---

## 1. 审计口径与重算命令

### 1.1 令牌契约

- 中性令牌 16 枚 + 七域色两套：`app/src/main/java/com/example/englishlearning/ui/theme/AppColors.kt`
  （`AppColorLight/AppColorDark`、`DomainLight/DomainDark`、`appColors()`、`appDomain()`）。
- 旧静态域名：`theme/MintPalette.kt`。**关键事实**（第 53–56 行）：`MintPrimary` 不是「旧的绿色」，
  它的取值是 `LocalDomainColors.current.getValue(AppDomain.AiSpeech).base`。
  同文件的 `MintPrimaryDark` 读 `text1`（中性墨）、`MintBackground/Surface/Outline/TextMuted/Tint`
  分别读中性令牌——**只有 `MintPrimary` 这一枚带色相**。所以统计口径必须把 `MintPrimary` 与
  `MintPrimaryDark` 分开算，否则会把大量本来就是中性墨的站点误算成「绿色」。
- 规格出处：`docs/superpowers/specs/2026-10-17-ui-redesign-design.md` §2.1（域色表 + 硬规则）；
  裁决出处：`docs/decisions/2026-10-22-ui-redesign-icon-legibility-and-entry-color.md`（22-1 / 22-2）。

### 1.2 重算命令（全部在本仓库根目录实跑，2026-10-25）

```bash
cd app/src/main/java/com/example/englishlearning/ui

# ① 每屏 MintPrimary（真正的「AI 绿」强强调）代码站点，剔除 import/注释
for f in *.kt components/*.kt; do [ -f "$f" ] || continue
  b=$(grep -E '\bMintPrimary\b' "$f" | grep -vE '^\s*(import|//|\*|/\*)' | wc -l)
  [ "$b" -gt 0 ] && printf "%3d  %s\n" "$b" "$f"
done | sort -rn
# 合计 71 处 / 18 个屏文件

# ② 按语义角色拆这 71 处
P='\bMintPrimary\b'; F=':[0-9]+:(import|\s*//|\s*\*|\s*/\*)'
grep -rnE "$P" --include=*.kt . | grep -vE "$F" | grep -cE 'containerColor = MintPrimary'      # CTA 填充 28
grep -rnE "$P" --include=*.kt . | grep -vE "$F" | grep -c 'CircularProgressIndicator(color = MintPrimary)'  # 4
grep -rnE "$P" --include=*.kt . | grep -vE "$F" | grep -cE '(focused[A-Za-z]*Color|cursorColor|selectedColor|checkedColor|checkedTrackColor|activeTrackColor|targetValue) = (if \([^)]*\) )?MintPrimary'  # 14
grep -rnE "$P" --include=*.kt . | grep -vE "$F" | grep -cE 'color = MintPrimary|tint = MintPrimary|color = if \([^)]*\) MintPrimary'  # 21
grep -rnE "$P" --include=*.kt . | grep -vE "$F" | grep -cE 'border\(1\.dp, (if \([^)]*\) )?MintPrimary|\.background\([^)]*MintPrimary|containerColor = if \([^)]*\) MintPrimary'  # 4

# ③ 令牌 vs 别名的总量
grep -rnE 'appColors\(\)|appDomain\(' --include=*.kt . | grep -vE '^./theme/' | wc -l   # 208
# Mint* 全族（含中性别名）代码站点：484（同口径逐文件相加）

# ④ 裸 hex（theme 外）
grep -rnE 'Color\(0x' --include=*.kt . | grep -v '^./theme/' | grep -vc '//'   # 1（mascot 眼睛）

# ⑤ 入口行与域色种类
for f in $(grep -rl 'EntryRow(' --include=*.kt . | sed 's|^\./||' | grep -v '^components/'); do
  printf "%s rows=%s domains=%s\n" "$f" "$(grep -c 'EntryRow(' "$f")" \
    "$(grep -oE 'domain = AppDomain\.[A-Za-z]+' "$f" | sort -u | wc -l)"
done

# ⑥ 页头
grep -ln 'AppScreenHeader(' *.kt components/*.kt | wc -l          # 8
grep -ln '"← 返回\|返回上一层' *.kt | wc -l                      # 21
```

### 1.3 未做 / 覆盖边界

- 只审 **颜色来源与入口/图标一致性**，不含字号、间距、圆角（属 AC-R-07 与批 4）。
- 未跑 TalkBack、未改对比度判据；本文件出现的比值一律来自规格既有实测，不在此复算。
- 真机只做手工目视与截图，未跑 instrumented（红线）。
- 拍译屏 56 处、ImageStudio 等屏的颜色站点只按静态代码统计，未逐屏截图。

---

## 2. 事实层：颜色来源的三种并存

| 来源 | 站点量 | 主题自适应 | 语义 |
|---|---|---|---|
| `appColors()` / `appDomain()` | **208** | 是 | 中性令牌 + 七域色 |
| `Mint*` 别名（`MintPalette.kt`） | **484** | 是（getter） | 6 枚中性 + **1 枚域色（AiSpeech）** |
| 裸 `Color(0x…)`（theme 外） | **1** | 否 | mascot 眼睛 |

结论：**没有裸 hex 泛滥**（RawColorLiteral 棘轮在起作用）。视觉上的「杂」来自第二层：
`MintPrimary` 这一枚别名把「AI 语音域色」冒充成了全站通用的「主色」。

`MintPrimary` 的 71 处用法按语义拆分：

| 语义角色 | 站点数 | 读法 |
|---|---|---|
| 主按钮填充（CTA） | 28 | 「这一页的主行动」 |
| 选中态 / 开关 / 焦点 / 光标 | 14 | 「你选了这一项」 |
| 文字、图标、尾部箭头、圆点 | 21 | 「强调」 |
| 进度圈 | 4 | 「进行中」 |
| 描边 / 底色 | 4 | 「被选中卡片」 |

也就是说：**「主行动」「选中」「进行中」「强调」这四种本来不同的语义，被同一枚 AI 绿承担了。**

---

## 3. 选项行（入口）颜色统计

`EntryRow` 的 `Tint` 档当前实现：`Modifier.background(lerp(surface, deep, 0.16f))`
（`components/IconBadge.kt:128`）——**每一行都得到一块属于自己的彩色底**。
裁决 22-2 要求改成统一 `appColors().tint` 底、域色只留字形描边；**该改动未落地**。

| 屏 | 入口行数 | 出现的域色种数 | 彩色块数（当前） | 有无「同组同色」问题 |
|---|---|---|---|---|
| 今日计划（学习 tab） | 7（查词 + 6 行入口组） | 5（Library/Learn/Reading/Review/Settings） | 7 | 有：同一张卡里 4 个色相 |
| 设置（4 组 9 行 + 4 SAF 子行） | 13 | 5（Learn/Review/AiSpeech/Settings/Library） | 13 | 分组内尚可，但整页 5 色 |
| 备份与恢复子页 | 4 | 2（Settings/Danger） | 4 | 无 |
| AI 学（未上线折叠行） | 1 | 1（AiSpeech） | 1 | 无 |
| AI 服务与密钥 | 12（10 主行 + 2 危险行） | 2（AiSpeech/Reading） | 12 | 见第 4.2「语义撞色」 |

真机截图（`.git/qa-scratch/color-audit/`）：

- `entry-group.png`：今日计划入口组——词书单词蓝、我的词粉、学习记录蓝、打卡与成就橙、两枚设置灰。
- `home.png`：设置页——学习提醒橙、默写纸蓝、AI 两行绿、许可灰、词书内容包紫、备份灰。

**判定（major · duplicate/semantic drift）**：同一屏 4–5 个色块并排，用户的「视觉疲劳」不是主观；
按 22-2 改完后，今日计划一屏的彩色块从 7 → **0**，只剩 6 枚细线色相。

---

## 4. 图标统计

### 4.1 同一枚字形在两档下不同（识别特征只在填充层）

`ui/components/AppIcon.kt` 只在 `Duotone` 分支画 `fillPaths`；入口弱档与底部导航走 `StrokeOnly`。
33 枚字形里，下列 **7 枚**的识别特征仍只写在填充层（`AppIcons.kt` 现盘核对）：

| 字形 | 只在填充层的特征 | `Tint` 档实际读到的形状 | 用在哪 |
|---|---|---|---|
| `List` | 左侧三枚方块圆点 | 三条横线 | 今日计划「调整词书与目标」 |
| `Gear` | 第三行上的旋钮圆 | 三条横线（第三条短） | 今日计划「学习工具与设置」、底导「设置」 |
| `Book` | 左页半幅 | 矩形 + 中缝 | 底导「学习」（未选中档）、设置页、今日计划 |
| `Image` | 日头圆 | 画框 + 山形 | **当前无生产站点（潜在）** |
| `Document` | 正文块 | 折角页轮廓 | 设置「生成默写纸」 |
| `Newspaper` | 报头横带 | 报面 + 两行线 | 底导「阅读」（未选中档） |
| `Bell` | 钟体 | 上半弧 + 一条横线 | **当前无生产站点（潜在）** |

真机上肉眼可见的只有 **Gear/List（相邻两行）**、**Book/Newspaper（底导未选中）**、**Document（设置一行）**；
`Image`/`Bell` 属「先修字形、免得日后挂上去才发现」。`Chart` 当前也无生产站点——正好给决策 22-1 第二步的
「打卡改 Chart」用。

已修（2026-10-25，`fb7def4`）：`Clock/Info/Search/Camera/Person` 五枚整圆——根因是 compose-ui 1.10
`PathParser` 丢弃「圆弧后隐式重复坐标组」，表现为半圈。

### 4.2 同形 / 不像

- `Star` 与 `Bookmark` 的骨架串是**同一轮廓、不同刻度**（`Star: M6.4 4h11.2v15.2L12 15.6l-5.6 3.6z`；
  `Bookmark: M6 4h12v16l-6-3-6 3z`，后者只是把前者等比放大约 7%）→
  「打卡与成就」与「我的词」在弱档下读起来是同一枚图标。
- `Star` 本身不是星形（是一个旗帜状闭合折线）。决策 22-1 第二步已定：打卡改回稿定稿形 `Chart`，
  `Clock` 让位给提醒行。
- `Gear` 与 `List` 在 `Tint` 档都是「三条横线」——今日计划最后两行**相邻两枚完全同形**（截图 `entry-group.png` 可见）。

### 4.3 底导

四枚 tab 选中态一律 `appDomain(AiSpeech)`（`AppBottomBar.kt:88`），不随 tab 变化。
属「全局 chrome 一个品牌色」的合理取舍，但与「域色 = 身份」并存时会被读成又一处不一致。
建议**不动**，在规则里写明「底导是全局导航，不参与域色编码」。

---

## 5. 目标页强调色统计（新发现，需拍板）

### 5.1 入口域色 → 目标页实际强调色

「页面主强调色」取该页的 CTA / 选中态主色。

| 目标页 | 入口徽章域色 | 页面自己在用的主强调 | 一致？ |
|---|---|---|---|
| 查词 `GlobalVocabularySearchScreen` | 词库 紫 `#BF5AF2` | `MintPrimary` = AI 绿 `#2EC99C`（8 处，含搜索框焦点/光标/主按钮/尾部箭头） | 否 |
| 词书单词 `WordBookWordsScreen` | 学习 蓝 `#0A84FF` | AI 绿（3 处） | 否 |
| 学习记录 `LearningRecordsScreen` | 学习 蓝 | AI 绿（2 处） | 否 |
| 打卡与成就 `CheckInScreen` | 复习 橙 `#FF9F0A` | `appDomain(Review)`（重试按钮、完成环） | 是 |
| 我的词 `MyWordsScreen` | 阅读 红 `#FF375F` | 无强调色（全中性，只读 `appColors()`） | 中性 |
| 学习工具与设置 `SettingsScreen` | 设置 灰 | 中性 + 域色行 | 是 |
| 调整词书与目标 / 学习设置 `LearningSetupScreen` | 设置 灰（今日计划入口）/ 学习 蓝（设置页入口） | AI 绿（`AppScreen.kt` 10 处：滑杆、开关、目标数字） | 否；且两个入口给了两种徽章色 |
| 学习提醒 `ReminderSettingsScreen` | 复习 橙 | 无令牌、无背景（Material 默认） | 否（第三套写法） |
| 生成默写纸 `WorksheetSettingsScreen` | 学习 蓝 | AI 绿（9 处） | 否 |
| 默写纸预览 `WorksheetPreviewScreen` | 学习 蓝 | AI 绿（2 处） | 否 |
| AI 服务与密钥 `AiProfileSettingsScreen` | AI 绿 | `appDomain(AiSpeech)` + 5 处 Reading 充当危险红 | 部分是（见 5.2） |
| 语音朗读 `SpeechSettingsScreen` | AI 绿 | `appDomain(AiSpeech)` | 是 |
| 开源许可 `LicensesScreen` | 设置 灰 | AI 绿（1 处） | 否 |
| 备份与恢复 `BackupHubScreen` | 设置 灰 | 中性 + Settings/Danger 行 | 是 |
| 阅读主页 `ReadingAccessScreen` | 阅读 tab | AI 绿（4 处，主 CTA） | 否 |
| 阅读历史 `ReadingHistoryScreen` | 阅读 | AI 绿（1 处） | 否 |
| 文章阅读 `ArticleReadingScreen` | 阅读 | AI 绿（2 处） | 否 |
| 粘贴文章 `ArticleImportScreen` | 阅读 | AI 绿（3 处） | 否 |
| 外刊 Feed `ReadingFeedScreen` | 阅读 | 已迁令牌（0 处） | 是（无强调） |
| AI 学 `AiLearningScreen` | AI tab | 品牌渐变 + AI 绿（3 处） | 是（同族） |
| AI 功能详情 `AiFeatureScreen` | AI | AI 绿（3 处） | 是（同族） |
| 长难句 / 拍译 / 生图 / 词问答 | AI | AI 绿（2/7/3/3 处）；但页内出站确认按钮硬传 `domain = Learn`（蓝） | 部分：**同一个弹窗在 AI 页上是蓝的** |
| 词卡 `WordCardScreen` | 学习 | AI 绿（7 处，三档反馈的「认识」） | 语义色（交通灯），可保留 |

### 5.2 语义撞色（red 既表示「阅读」又表示「危险」）

- `AiProfileSettingsScreen` 把 **`AppDomain.Reading`（红）** 当危险色用：清除已保存密钥、删除这套配置、
  表单错误与回执（5 处）。而备份子页的「删除全部学习数据」用的是 **`AppDomain.Danger`**。
- 同一个用户动作（删除/危险）在两屏是两种红；同一个红在两处是两种含义。
- 另：`PillButton` 默认 `domain = AiSpeech`，四个 AI 页的出站确认弹窗显式传 `AppDomain.Learn`——
  「确认发送」被涂成学习蓝，和所在页的绿也无关。

### 5.3 判定

| 严重度 | 类别 | 条目 |
|---|---|---|
| **major** | `semantic-drift` | 71 处 `MintPrimary` 把「AI 域色」当全站通用主强调：18 屏，跨屏可见 |
| **major** | `duplicate-component-pattern` | 入口徽章 `Tint` 仍按域上底（22-2 未实施）：一屏 7–13 个彩色块 |
| **major** | `mixed-library-style` | 危险红二义（Reading vs Danger）：AI 配置页 5 处 |
| **major** | `undocumented-exception` | 出站确认弹窗在 AI 页传 `Learn` 域色：4 屏 8 处 |
| **minor** | `typography-drift`（结构，不是字色） | 页头两套：8 屏 `AppScreenHeader`，21 文件旧「← 返回」 |
| **info** | 可接受 | 底导四 tab 同用 AiSpeech 品牌色；`WordCardScreen` 三档反馈用语义红/橙/绿 |

---

## 6. 页头与底色（「不完善」的另两处结构性证据）

- **页头**：`AppScreenHeader(` 8 处（今日计划 / 设置 / 我的词 / 打卡 / 阅读 Feed / 备份子页 / 卡片详情 / AppScreen）；
  旧「← 返回上一层」21 个文件。真机对比：`search.png`（旧页头，标题与返回同一行、字号偏大）
  vs 今日计划（新页头）。这条已登记为 Task 35。
- **底色**：6 个屏完全不设背景（`BackupExport/BackupRestore/BackupPairingExport/ReminderSettings/VocabularyRelearn/VocabularyScreen`），
  依赖宿主 surface；2 个用 `Scaffold(containerColor)`；其余各自 `Modifier.background(...)`。观感上不一定错，
  但「页面底色由谁定」目前没有单一规则。

---

## 7. 建议的收敛规则（供拍板，不是既成事实）

### R-A 入口层（已拍板，直接执行）

1. `EntryIconTone.Tint` 容器底 → `appColors().tint`（全站一档），域色只染字形描边。
2. **同组同色**：一组入口行共用该组域色；今日计划 6 行按「学习 / 记录 / 系统」归组。
3. `Gradient` 档退出入口行族，只留卡内主行。
4. 图标按 22-1 补描边特征；`Star` → `Chart`，`Clock` 归提醒行。

### R-B 目标页层（需拍板，三选一）

- **方案 1（推荐）· 域色做身份，薄荷做行动**
  页面身份元素（页头徽章、分组标题圆点、选中 chip、进度、Tab 选中）取 `appDomain(本页域)`；
  **主 CTA 全站保持品牌薄荷**。理由：CTA 的颜色回答「能不能点」，不回答「我在哪」；
  身份色回答「我在哪」。改动面：71 处里约 39 处（选中 14 + 文字/图标 21 + 进度 4）改域色，28 处 CTA 保留薄荷。
  视觉上「查词」会从「整屏绿」变成「紫色搜索框/紫色选中态 + 薄荷主按钮」，入口与页面同族但主行动仍统一。

- **方案 2 · 全域色**
  连主 CTA 也取本页域色（查词页紫色按钮、阅读页红色按钮、学习页蓝色按钮）。一致性最强，但：
  四张 tab 主页各有一个不同颜色的主按钮；评分/危险/确认三类动作的红色需重新分层；28 处 CTA 的
  前景对比要逐处复算（`onBrand` 压非薄荷底不成立）。

- **方案 3 · 单色**
  取消入口徽章的域色，全站只留薄荷 + 中性墨。最干净、最省维护，但会丢掉七域寻路
  （用户 2026-10-22 选 A 时明确要保留「域色 = 语义」），与 22-2 冲突，不建议。

### R-C 语义层（无论选哪个方案都要做）

1. 危险动作统一 `AppDomain.Danger` / `colors.error`，禁止再借 `Reading`。
2. 共享弹窗（出站确认、删除确认）的按钮域由宿主显式给「语义」而不是「域」：
   确认发送 = 品牌/主行动，取消 = 中性，删除 = Danger。
3. `PillButton` 的默认 `domain = AiSpeech` 改为必填或改中性命省——默认值是在替调用方做配色决定。

---

## 8. 下一步（按收益/成本排序）

| 序 | 动作 | 覆盖 | 成本 | 收益 | 依赖 |
|---|---|---|---|---|---|
| **1** | **Task 27 落地（22-1 + 22-2 + R-C）** | 图标 7 枚 + 入口 25 行 + 危险红 5 处 | 1 个席位批 | 用户指认的两件事当场消失；今日计划彩色块 7→0 | 无（决策已拍板） |
| **2** | 目标页域色（R-B 选定方案） | 18 屏 / 71 处 | 2–3 个席位批 + 对比度复算 | 入口→页面同族，「紫→绿」消失 | 需拍板 R-B |
| **3** | 页头收编（Task 35） | 21 文件 | 1 个席位批（机械） | 「不完善」的主观感最大的另一来源 | 无 |
| **4** | 底线色归属规则 | 6 屏 | 小 | 深色/过渡不闪 | 可与 3 合并 |

**不建议**把 2 塞进 Task 27：Task 27 是「已拍板的契约变更」，2 是「新契约」，按 AGENTS.md 要先改规格与决策再动码。

---

## 9. 验收判据（实施时用）

- 入口：今日计划一屏的「非中性底色块」= 0（用截图取色统计，阈值与 22-2 同口径）。
- 图标：`StrokeOnly` 下 7 枚字形与相邻字形两两不同（像素差 ≥ 定值），`Star≠Bookmark`。
- 目标页：抽 3 屏（查词 / 词书单词 / 阅读主页）比对入口徽章 `deep` 与页面身份色 `deep` 逐值相等。
- 危险：全站 `AppDomain.Reading` 的生产站点只剩「阅读」语义；`grep -rn 'AppDomain.Reading' --include=*.kt` 人工逐条过。
- 门禁：JVM 基线 241 类 / 1789 例只增不减；`verifyThirdPartyNotices`（76）、`verifyAcTraceabilityMatrix`（56）绿。
