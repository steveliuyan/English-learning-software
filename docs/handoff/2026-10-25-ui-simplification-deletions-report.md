# 2026-10-25 UI 简化批：冗余控件删除 工作报告

## 1. 本次目标

用户要求「把每个子页面、每个菜单的排版看一遍，判断是否好看、合理、容易上手，并看人机交互，不合理的列出来」，
在看过清单后拍板：**「先全删了这些功能，后面再细分」**。本批只执行**冗余控件删除**，
不授权功能下线；会移除用户内容、数据通路或已验收安全能力的项一律留待下一轮单独裁决。

依据与逐屏证据：`docs/verification/2026-10-25-ui-ux-audit.md`（35 屏覆盖清单 + A/B/C 三类项）。
裁决记录：`docs/decisions/2026-10-25-ui-simplification-deletions.md`。

## 2. 完成了什么

### 2.1 审查产出（新增文档）

- `docs/verification/2026-10-25-ui-ux-audit.md`：35 屏逐屏版面/交互事实盘点，A 类（纯删）14 项、
  B 类（设计取舍，需拍板）10 项、C 类（安全/可用性必修）若干；§8 记录本批执行状态与未执行项理由。
- `docs/decisions/2026-10-25-ui-simplification-deletions.md`：16 条删除 + 8 条保留项及理由 + 验证口径。

### 2.2 生产代码删除（14 个文件改动 + 1 个文件整删）

1. `ui/VocabularyScreen.kt` **整文件删除**：全仓零调用点（孤儿屏），「我的词」生词本段已取代。
2. `TodayPlanScreen.kt`：删两张计数卡（`RowScope.CountCard` 整个私有组件）与「已解锁：文章已解锁」独立句；
   数字仍在进度明细行与工作量图例各出现一次。
3. `TodayPlanScreen.kt`：删「学习工具与设置」入口行及 `onOpenLearningTools` 形参与实参；设置 tab 常驻，属重复入口。
4. `CheckInScreen.kt`：删「近 7 日完成率」条，保留同构的「本周学习」条与月历（`recentDays` 数据层未动）。
5. `WorksheetPreviewScreen.kt`：删顶部「← 返回修改」与同义副标题，副标题改页数；底部返回按钮保留。
6. `SettingsScreen.kt`：删 `settings_profile_card` 整卡；昵称并入页头副标题，词书名并入「词书与每日目标」行副标题。
7. `AiFeatureScreen.kt`：删未上线骨架页「还差什么」「会用到的数据」两卡（开发 backlog 不是用户信息），
   并移除随之失效的 `todayWordCount` / `dueWordCount` 两个形参；`AppScreen.kt` 调用点同步。
8. `AiProfileSettingsScreen.kt`：删「官网」表单字段（实体与校验保留，全仓无展示消费方）。
9. `SpeechSettingsScreen.kt`：删「更多语音厂商 · 待接入」折叠区（6 条全部不可点）。
10. `MyWordsScreen.kt`：删错词段排序说明句；结果框保留（它承载失败反馈，属功能）。
11. `LearningRecordsScreen.kt`：删行内「释义：」前缀；「反馈：」逐字保留（契约测试取证锚）。
12. `LicensesScreen.kt`：删「共收录 N 项」。
13. `WordAiQaScreen.kt`：删「个人笔记」问法 chip（`WordQaKind.entries.filter { it != WordQaKind.Personal }`），
    该 chip 实际走统一 `ask()` 发 AI 请求，与 `WordQaPromptKind` 注释冲突；本地笔记输入框保留。
14. `WordCardScreen.kt`：删第二行「到期复习 x/y / 今日新增 x/y」，保留「第 x / y 张」。

### 2.3 契约与 androidTest 同步（删除项逐条改判，不放宽判据）

- JVM（4 个类改法为「改期望值/换锚点」，不是放宽）：
  - `TodayPlanNumberAuthorityTest`：`AppType.Display` 顺序锚由已删的 `today_plan_unlock_status` 换到主 CTA
    `today_plan_start_learning`，判据强度不变；删除 `CountCard` 函数体窗口断言；入口 tag 白名单 6 项。
  - `TodayPlanEntryColorContractTest`：入口集合由 6 行改 5 行，`Settings` 组只剩「调整词书与目标」。
  - `WordListFormatTest`：屏名单去掉已删屏，消费方断言改锚 `MyWordsScreen`。
  - `SharedComponentUniquenessRatchetTest`：页头族/词行族 seed 与 `specWordRowFiles` 摘除 `VocabularyScreen.kt`。
- androidTest（6 个类）：所有「断言控件存在」的用例改为反向断言或改锚替代控件；
  `AiFeatureScreenTest` 随两卡删除移除 3 条用例（1 条依赖清单 + 2 条数据块）。
- 文档同步：spec §3 `WordRow` 证据列、计划 Task 14B 留名、`docs/verification/ui-redesign-2026-10.md` 孤儿屏销账。

## 3. 验证记录

```
"$GRADLE" :app:assembleDebug :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin \
  verifyThirdPartyNotices verifyAcTraceabilityMatrix --offline --no-daemon --no-build-cache
```
`BUILD SUCCESSFUL in 1m 32s`（五任务全绿）。

- JVM 单测：**243 类 / 1798 例 / 0 失败 / 0 错误 / 0 跳过**（与 Task 27 关账基线完全一致；
  本批只改断言与 seed，未增删用例类）。
- androidTest：编译通过（`compileDebugAndroidTestKotlin` 绿，仅既有 `SwipeToDismissBoxState` 弃用告警）。
  `@Test` 总数 **649 → 646（−3）**，下降全部来自 `AiFeatureScreenTest` 随「还差什么」「会用到的数据」
  两卡删除的三条用例（1 条依赖清单 + 2 条数据块）；其余 5 个文件只做改名/改断言，枚数不变。
- `verifyThirdPartyNotices`、`verifyAcTraceabilityMatrix`：通过。
- detekt/ktlint 未运行：本仓门禁是单测，静态分析为存量债基线
  （`docs/decisions/2026-09-19-static-analysis-and-test-baseline.md`）。

真机目视（`bf353dda`，只 `adb install -r` + 手工点按，未跑 instrument）：
- 今日计划：两张计数卡与「已解锁」独立句已不在；进度卡「0/12 项已完成 / 新增 0/10 · 复习 0/2」→ 工作量图例
  → 主 CTA → 「阅读文章」→ 解锁说明 → 入口组的读序成立，「学习工具与设置」行确已消失。
- 设置页：资料卡已删，页头副标题为「<昵称> · API Key 只保存在本机加密区。」；
  「词书与每日目标」行副标题「当前：六级英语词汇 · 换词书与目标」，信息未丢。
- AI 未上线骨架页（词文串学）：「还差什么」「会用到的数据」两卡已删，只剩「当前进度」+「现在可以做的」两按钮。
- 目视顺带发现（不在本批范围，留待细分）：骨架页删两卡后下半屏留白较多，可考虑把两按钮块上移或补一句
  轻量说明，属版面取舍而非缺陷。

复算命令（评审可直接重跑）：
```bash
python - <<'EOF'
import glob, xml.etree.ElementTree as ET
c=f=e=0
for p in glob.glob('app/build/test-results/testDebugUnitTest/*.xml'):
    r=ET.parse(p).getroot(); c+=int(r.get('tests',0)); f+=int(r.get('failures',0)); e+=int(r.get('errors',0))
print(len(glob.glob('app/build/test-results/testDebugUnitTest/*.xml')), c, f, e)
EOF
grep -rc '@Test' app/src/androidTest --include=*.kt | awk -F: '{s+=$2} END{print s}'
```

## 4. Git 状态

- 分支：`stage-5-ui-r2-learning-path`（不合 `main`，未推送——**用户尚未批准推送**）。
- 本批提交：`b162613`（代码 24 文件，`+158 / −565`，含 `VocabularyScreen.kt` 删除）+
  `42a433d`（文档 6 文件，`+434 / −1`，含审计、决策与本报文），外加一条登记提交（本节哈希与真机结果）。
- 工作树：提交后为干净；`.git/qa-scratch/` 内的门禁日志与设备截图按约定不入库。

## 5. 未完成 / 遗留问题

- **B 类（10 项）与 C 类（安全/可用性）未动**，等用户「后面再细分」逐项拍板。其中 C 类建议优先：
  备份恢复密码明文显示、四处危险动作缺确认、五屏缺滚动容器、外刊失败态/许可证空态缺重试、恢复页缺返回。
- B 类待裁决：备份两条导出合并、全文对照模式、词书字母栏/筛选条二选一、学习设置 4 个详解开关收进设置页、
  拍照翻译常驻 5 块收起/移位、词义详情配图回退块。
- 「卡片汤」（S2）：不建议靠删卡解决，需设计系统层面加「强卡/弱卡」两档，属下一轮。
- 真机目视：本批改动后需在 `bf353dda` 上手工试用首页/设置页/AI 骨架页（只 `adb install -r`，不跑 instrument）。
- 独立评审席：本批未另开评审席（改动为删除+契约跟随，门禁已覆盖源码形状）。

## 6. 下一任接手须知

- 先读：本报告 → `docs/decisions/2026-10-25-ui-simplification-deletions.md` →
  `docs/verification/2026-10-25-ui-ux-audit.md` §8（执行状态与未执行理由）。
- 删除项的实现细节以决策文档为准；**保留项不要顺手删**——理由逐条写在决策文档表格里。
- 改 `TodayPlanScreen` 入口组时注意：`rowTags` 闭合集合现为 1（组外查词）+ 1 + 1 + 5，改一处需同改
  `TodayPlanScreenTest` 与 `TodayPlanEntryColorContractTest`；`AppType.Display` 权威锚已换到主 CTA，
  不要把第二枚 Display 加到主操作下方。
- 已删的 `VocabularyScreen.kt` 在 KDoc 里仍有历史留名（「老页 `VocabularyScreen.kt:81-97`」）——
  那是文案溯源，不是死代码；不要再按路径去读文件。

## 7. 用户确认过的决策

- 2026-10-25：审查范围与判定标准（好看/合理/易上手/人机交互）。
- 2026-10-25：**「先全删了这些功能，后面再细分」** —— 批准本批 16 条删除立即执行，
  B/C 类与保留项留待后续细分时再裁决。
