# 底导回四栏，「我的」做功能中心，设置只作子页；AI 助学页去渐变

- **日期：** 2026-11-02
- **状态：** 已接受（用户拍板：直接按后给的参考做，不再做加法）
- **取代：** `docs/decisions/2026-10-31-settings-as-fifth-tab.md`（设置升为第五个一级 tab）

## 背景

上一批把底部导航从四栏扩成五栏（学习 / 阅读 / AI 助学 / 我的 / 设置），并把原「我的」页里的
设置分组搬成第五栏根页。用户在两处真机走查后否定了这次改动：

1. 「我的」与「设置」并排成两个一级 tab 在逻辑上重复——一般应用是「我的」里放设置，
   而不是「我的」和「设置」彼此并列；上一批是在旧版上「做加法」，不是按参考重做。
2. AI 助学页顶部仍保留品牌绿色渐变头卡与状态胶囊；参考（本项目唯一 UI 依据）里没有这块颜色，
   用户明确要求删掉。

用户同时确认了目标形状：**按参考重做「我的」入口页**（底导回四栏），
**AI 助学页整块删掉渐变，改普通页头 + 双列功能卡**。

## 参考依据

参考原型（只读）：

- `assets/app.js` 的 `TABS` 只有四栏：`学习 / 阅读 / AI 助学 / 我的`；
- `sMeHome()`：普通页头（eyebrow「我的」+ 名字 + 连续打卡/累计学过）+ 三格统计 +
  三个分组：**我的词汇**（收藏的词 / 生词本 / 易错词）、**学习**（学习记录 / 学习计划 / 词书）、
  **应用**（AI 服务 / 提醒与发音 / 设置 / 数据与备份 / 关于与许可）；
- `sMeSettings()`：只有「外观」（主题三态）与「背词」两节，并明确提示
  「这一页只放全局开关。提醒与发音、计划、词书、备份与许可都在「我的」里，不在这里重复出现。」
- `sAiHome()`：普通页头（eyebrow「AI 助学」、标题「把词用起来」、一句说明、右侧配置按钮）
  + 双列功能卡（可用在前，「还在打磨」在后）+ 页末提示；**没有任何渐变头卡或状态胶囊**。

## 决定

1. **底部导航回四栏**，顺序固定：`学习 / 阅读 / AI 助学 / 我的`。
   `AppTab` 删除 `SETTINGS`；底导删除齿轮格；`我的` 仍是第四格 `AppIcon.Person`。
2. **「我的」= 功能入口中心**（不是「只剩进度」，也不再是设置根页）：
   - 档案头：姓名 + 「连续打卡 N 天 · 累计学过 N 个词」；
   - 三格统计：已学词 → 词库（词书段）、连续天数 → 打卡、文章 → 阅读历史；
   - **我的词汇**：收藏的词 / 生词本 / 易错词 → 词库对应分段；
   - **学习**：学习记录 / 学习计划 / 词书 / 生成默写纸 / 词书内容包；
   - **应用**：AI 服务 / 提醒与发音 / 设置 / 数据与备份 / 关于与许可。
   - 分组标题、行标题、图标、域色与 testTag 只在 `ProfileGroups` 台账定义一处，渲染侧按 tag 取
     （沿用上一批「台账不是第二套真值」的纪律）。
3. **「设置」只作「我的」里的子页**，不再占底导：
   - 内容只保留「外观」（主题三态）+ 一句与参考同义的说明（提醒与发音、计划、词书、备份与许可
     都在「我的」里，不在这里重复出现）；
   - 原九行设置分发到「我的」对应行，不保留第二份同落点入口。
4. **词书内容包与提醒与发音各作一级子页**（参考没有这两件，但本应用已实现，不能砍）：
   - 词书内容包：导入 / 导出 `.wbpack`，保留原有 SAF 通路、忙碌禁用与结果提示；
   - 提醒与发音：一张二级导航页，两行分别进入既有的学习提醒页与语音朗读页
     （参考把两者合成一页；本应用是两套独立 ViewModel 与屏，先以导航页保真，不重写两屏）。
5. **AI 助学页**：
   - 删除 `AiHeaderGradient`、`AiHeaderCard`、白胶囊状态徽章及其语义；
   - 换成共享 `AppScreenHeader`：eyebrow「AI 助学」、标题「把词用起来」、参考同一句说明、
     右侧配置按钮（进入 AI 服务页）；
   - 正文按参考改双列功能卡：已实现三项在前，未实现四项收在「还在打磨」分组；
   - 页末保留参考的隐私提示；
   - 不再保留「查看词表」裸行与「AI 助学如何起作用？」说明书折叠行：参考没有这两块，
     词表入口在「学习」与「我的 → 词书」可达，说明书内容已在设置页与隐私提示里覆盖。
6. **学习页齿轮 / 阅读页与 AI 功能页的配置兜底**：由「切到设置 tab」改为打开「我的 → 设置」子页
   （`showSettings = true`），不再切 tab。

## 为什么是需求变化，不是放宽

- `AppTabTest`、`AppBottomBarTest`、`AppBottomBarContractTest` 原钉五栏，是被取代的期望；
  新判据钉四栏顺序、四枚字形与「设置不得回到底导」。
- `SettingsGroups` 的「4 组 9 行」台账随上一批一起退役；新台账是 `ProfileGroups` 的
  「我的词汇 / 学习 / 应用」三组，行数与落点逐条钉在 `ProfileHubLedgerTest`（原
  `SettingsNoDeveloperJargonTest` 改造）。设置子页自己的判据只钉「外观 + 说明 + 无重复入口」。
- AI 页原 `AiLearningLayoutContractTest` / `AiBadgeContrastFixTest` 的渐变与白胶囊判据是
  按旧设计写的；本批连同组件一起删除，不能留「测一个已不存在的形状」的空转用例。
  `AiFeatureScreen` 头部的品牌渐变徽标不在本批范围，相关判据保留。

## 影响

- 代码：`AppTab`、`AppBottomBar`、`AppScreen`（删 SETTINGS 分支；新增 `showSettings` /
  `showWordBookTransfer` / `showReminderSpeech` 三枚覆盖层与返回优先级）、`ProfileScreen`
  （重写为 hub）、`SettingsScreen`（重写为外观子页）、新增 `WordBookTransferScreen` /
  `ReminderSpeechScreen`、`AiLearningScreen`（去渐变、普通页头、双列卡）。
- 测试：`AppTabTest`、`AppBottomBarTest`、`AppBottomBarContractTest`、`ProfilePageContractTest`、
  `ProfileScreenTest`、`SettingsScreenTest`、`SettingsNoDeveloperJargonTest`、
  `SettingsLicensesEntryTest`、`MyWordsScreenContractTest`、`BackupEntryUnificationContractTest`、
  `AiLearningLayoutContractTest`、`AiBadgeContrastFixTest`、`AiLearningScreenTest`、
  `AiFeatureIconContractTest`、`AppScreenTest`、`AppScreenOverlayContainerTest`、
  `DarkTokenConsumptionRatchetTest`（摘 AI 页 seed）、`MintTokenIsComposableContractTest`
  （摘 `AiHeaderGradient` 持有符号）。
- 规格：本轮不改产品规格，只把「设置第五栏」的实现撤回；四栏是 `2026-09-23-ai-learning-tab-design.md`
  原本的导航形状。

## 风险

- 「我的」页行数从 4 行增至 15 行（3 组），小屏与大字号下必须整页可滚；`ProfileScreenTest`
  在 fontScale 2.0 下核对最后一组仍可滚到。
- 设置从 tab 变为子页后，学习页齿轮的返回会回到学习页而不是「我的」；这符合「从哪来回哪去」，
  但须在 connected 用例里逐条钉住。
- 删除 AI 页的「查看词表」后，词表入口仍有学习页与「我的 → 词书」两条；不新增第三条。
