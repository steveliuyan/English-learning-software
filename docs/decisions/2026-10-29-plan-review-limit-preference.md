# 决策：复习上限只存不排（2026-10-29）

## 背景

批 1 Task 12 把「学习设置屏」对齐到原型 `#/plan-edit`（学习计划）。原型这一屏画了一节**复习上限**：

```
section('复习上限', '<div class="card">' +
  '<span class="stat-num">' + S.planReview + '</span><span class="stat-cap">个 / 天</span>' +
  '<input class="range mt3" type="range" min="10" max="80" step="5" value="' + S.planReview + '" aria-label="每日复习上限">' +
  '<p class="caption mt2">到期词超过上限时，先复习最久没见的。</p></div>')
```

（`assets/app.js:574-577`，初值 `planReview: 30` 见 `assets/app.js:108`。）

这与既有规格直接对冲：`docs/specs/01-vocabulary-learning-and-review.md:28` 写的是
**「V1 不设置复习硬上限；当到期词数量很大时，显示总量、支持分批学习，但未完成的计划复习仍影响严格模式完成」**。

用户对这一轮的口径又是两条：
1. 冲突时**以原型文件为准**，原型画了的控件不许自己藏掉或删掉；
2. **UI 之外的逻辑（FSRS 排期、备份、AI 调用）保持原样**，只在必要时接上界面。

所以这里必须裁决：界面上出现了一枚原型有的滑杆，而它的语义（「到期词超过上限时先复习最久没见的」）
是一条会改变今日计划 `dueTarget` 生成规则的承诺。

## 候选方案

| 方案 | 内容 | 否决 / 采纳理由 |
|---|---|---|
| A | 这一节不接（屏上不放滑杆，或放了但 disabled） | 原型画了一枚可用滑杆，disabled 就是「画了但不给用」，与用户「按原型逐屏落地」对冲；A 也不是最小改动——批 3 的 AI 未接入控件禁用是用户另行拍板过的 B 画布，本屏没有对应授权。**否决** |
| B | **存而不排**：偏好落库、随备份走，今日计划的 `dueTarget` 生成逻辑一字不改 | 界面按原型逐值落地；FSRS / 排期零改动；代价是那一句 `.caption` 目前描述的是尚未生效的行为。**采纳（本决策）** |
| C | 真做上限：到期词 > 上限时只排最久没见的前 N 个 | 改的是 `GetOrCreateTodayPlanUseCase` / `dueTarget` 与严格模式完成判据，属逻辑改动，且必须先改 spec 01:28 再实施；用户没授权这一轮动排期。**否决（留待后续）** |

## 决定

1. **落库**：`learning_settings` 新增列 `dailyReviewLimit`（`INTEGER NOT NULL`，缺省 30），
   Room 版本 32 → **33**，`MIGRATION_32_33` 用
   `ALTER TABLE ... ADD COLUMN ... NOT NULL DEFAULT 30` 回填（照本仓 `speechRate` / `autoSpeakOnCardReady`
   的加列先例：隐式驼峰列名 + 非零默认，旧行不丢值）。schema 33 已导出。
2. **默认值的两处独立性**：领域默认 `LearningSettings.DEFAULT_DAILY_REVIEW_LIMIT = 30` 与实体默认 `30`
   同值而**不互相 import**——`core/storage/entity` 不依赖业务包是这一层既有约束（该目录零条
   `import com.example.englishlearning.*`）。同值这一条由 `AppDatabaseMigrationTest`（回填必须是 30）
   与 `LearningSetupViewModelTest`（未存过偏好时读到 30）两头钉住。
3. **刻度归屏侧**：10 / 80 / 步长 5 是**这枚控件**的度量，声明在 `AppScreen.kt`
   （`MIN_REVIEW_LIMIT` / `MAX_REVIEW_LIMIT` / `REVIEW_LIMIT_STEP`），领域层不写第二份；
   初值 30 归领域层，屏侧不写 `DEFAULT_REVIEW_LIMIT`（判据钉住为 0 次）。
4. **草稿 / 提交分离**：拖动只改屏上草稿（`LearningSetupViewModel.updateReviewLimit`），
   点保存才提交（`committedReviewLimit`）。开关是即时语义，滑杆不是——不提交就丢弃，
   与原型「改完点右上角保存」那句一致。取值一律 `coerceIn(10, 80)`，越界不写库。
5. **备份跟走**：`LearningSettingsRecord` 增字段 `dailyReviewLimit`，Kotlin 侧默认值 30。
   恢复侧两处 `Json` 都是 `ignoreUnknownKeys = false` 的严格档，**缺键只靠默认值放行**——
   没有默认值时，v33 之前的旧备份包会整包恢复失败（`BackupSnapshotBuilderTest` 钉住这两条）。
   导出侧 `encodeDefaults = true`，非默认值出得来（样本用 45）。
6. **排期不动**：`dueTarget`、今日计划快照、严格/宽松模式解锁判据一律保持原样。
   本决策**没有**引入复习硬上限，spec 01:28 的口径在行为层依然成立。

## 影响与遗留

- **那句文案已按路径 ② 收口（用户 2026-10-29 裁决）**：屏上原先照抄的原型那句
  「到期词超过上限时，先复习最久没见的。」描述的是尚未实现的行为——在本轮它是空头承诺。
  原型的 `S.planReview` 同样只做显示（`assets/app.js:1271` 的「我的」摘要里念一遍数值），
  mock 本身也没有裁剪到期队列，所以这句是**原型自带的空头承诺**，不是本仓改写出来的。
  裁决取路径 ②（保留偏好、把这句改成不承诺排期的说法），路径 ①（按方案 C 接排期，需先改 spec 01:28）
  留作后续批次。现文案：「当前版本先记下这个上限，今日复习仍会排完全部到期词。」
  这是对原型文案的**显式偏离**，登记在批 1 偏离台账第 10 行；真接进排期那天应回到原型原句。
  判据两条（同在 `LearningSetupLayoutContractTest.the review limit keeps the prototype number and range`）：
  新句逐字在 `ReviewLimitSection` 窗口内；旧承诺在剥注释后的整份 `AppScreen.kt` 里归零，防止改回去时漏掉半句。
- 「我的」页摘要（原型 `每日新增 N 个 · 复习上限 M 个`）本轮未接线：`SettingsScreen` 不在批 1 的 11 屏清单里，
  归批 2「我的」8 屏时逐处核对。
- 判据落点：`LearningSetupLayoutContractTest`（版面与刻度）、`LearningSetupViewModelTest`（草稿/提交/越界钳制）、
  `AppDatabaseMigrationTest.migrateV32ToV33_*`（回填与不丢行）、`BackupSnapshotBuilderTest`（导出带值 + 旧包可读）。
