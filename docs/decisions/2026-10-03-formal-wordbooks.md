# 正式词书资源与跨词书进度决策

## 背景

应用需要将用户提供的十本 Excel 词书转换为可离线使用的正式词书资源，并在词书切换时提供整本进度与重复词迁移提示。

## 决策

1. 正式词书统一采用 `book.json` + `manifest.json` 的现有 WordBookPackage 格式，并由 `WordBookPackageParser` 在运行时再次校验。
2. 词条 ID 使用 `<wordBookId>:<normalizedLemma>` 命名空间，避免不同词书之间互相覆盖。
3. 预置词书通过 assets staging、parser 校验、原子发布接入；用户导入词书优先，预置资源作为兜底。
4. 十本资源的实际稳定 ID 以 `metadata.json` 为准，其中博士词书使用 `doctoral-english`，不使用计划早期草案中的 `doctor-english`。
5. 整本进度只统计目标词书中存在 `card_review_states` 的唯一 card ID；跨词书迁移只复制复习状态与幂等审计记录，不复制 `learning_events`，也不删除源词书状态。
6. 迁移匹配使用 `trim + casefold + 合并连续空格` 后的完全相同 lemma；目标词书出现同 lemma 多候选时不自动迁移。

## 已验证资源

截至 2026-10-04，metadata 共 10 本，且每本 `metadata.totalWords == book.json.cards.size`、card ID 唯一并带正确词书前缀：

- cet4: 4308
- cet6: 4634
- doctoral-english: 8537
- graduate-english: 4581
- ielts: 5215
- junior-high-school: 1732
- kaoyan-english: 4815
- primary-school: 816
- senior-high-school: 3214
- toefl: 7438

## 验收边界

- 不提交原始 Excel、密钥或用户数据库。
- 真机验收不得运行 `connectedDebugAndroidTest`，只允许 `adb install -r -t`，不得卸载或清除用户数据。
