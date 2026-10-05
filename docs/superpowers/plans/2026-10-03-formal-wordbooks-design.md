# 正式词书接入与跨词书进度计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 将用户提供的十本 Excel 词书转换为受现有校验保护的正式词书资源，接入词书选择、整本进度和跨词书重复词迁移提示。

**Architecture:** 离线脚本读取十本 Excel，统一解析为现有 WordBookPackage 目录格式并通过 Kotlin parser 校验；运行时沿用 Imported/Composite WordCardSource。跨词书匹配仅使用标准化后完全相同的 lemma，迁移只写目标 card_review_states 与幂等审计记录，不复制 learning_events，也不删除源词书状态。首页复用现有 AppScreen/TodayPlanScreen/学习设置状态，新增整本进度查询。

**Tech Stack:** Python 3.13 标准库 XML/ZIP（不依赖全局包）、Kotlin、Compose、Room、KSP、现有词书包 parser。

**Spec:** 本计划直接落实用户确认的正式词书方案；既有约束见 `AGENTS.md`、`docs/specs/00-foundation-and-architecture.md`、`docs/specs/01-vocabulary-learning-and-review.md`。

## Global Constraints

- 不运行 `connectedDebugAndroidTest`；真机若需验证只能 install -r，不清数据。
- `learning_events` append-only；跨词书迁移不复制历史事件。
- 每本词书独立维护进度；同 lemma 不默认共享 FSRS。
- 导入/预置资源必须通过现有 WordBookPackageParser；失败零写入。
- 重复词只按 `casefold + trim + 合并连续空格` 后的完全相同 lemma 匹配。
- 迁移不复制生词本；源词书状态保留；重复执行幂等。
- 不提交用户原始 Excel；只提交生成后的受校验资源或可复现转换脚本。

---

### Task 1: 固化 Excel 解析与转换工具

**Files:**
- Create: `tools/wordbooks/excel_to_wordbook.py`
- Create: `tools/wordbooks/README.md`
- Create: `tools/wordbooks/tests/test_excel_to_wordbook.py`
- Test input: `C:/Users/20212/Downloads/*.xlsx`（只读）

**Interfaces:**
- `normalize_lemma(value: str) -> str`
- `parse_workbook(path: Path, book_id: str, display_name: str, level: str) -> dict`
- `write_wordbook_directory(book: dict, output_dir: Path) -> None`
- CLI: `python excel_to_wordbook.py --input ... --book-id ... --display-name ... --level ... --output ...`

- [ ] 读取所有包含表头 `序号/单词/词性/音标/中文释义` 的工作表；跳过封面与说明页。
- [ ] 将列映射为 lemma、ipa、sense、derived、phrases、synonyms、rank；空例句使用稳定占位文本并在报告中记录，不猜测释义。
- [ ] 去除空行、重复表头；同一本内规范化 lemma 重复时保留首条并输出拒绝报告，不能静默覆盖。
- [ ] 生成 `book.json`、`manifest.json`，不生成图片引用；字段符合当前 formatVersion=1。
- [ ] 写测试覆盖规范化、工作表识别、字段映射、重复 lemma、无效行和可重复输出。
- [ ] 用十本实际文件生成十个输出目录，并记录实际词条数；不复制原始 Excel 到仓库。

### Task 2: 接入十本正式词书资源

**Files:**
- Modify: `app/src/main/assets/wordbooks/metadata.json`
- Modify: `app/src/main/java/com/example/englishlearning/learning/PlaceholderWordCardSource.kt` 或资源路由
- Modify: `app/src/main/java/com/example/englishlearning/wordbook/CompositeWordCardSource.kt`
- Modify: `app/src/main/java/com/example/englishlearning/di/AppModule.kt`
- Create: `app/src/main/java/com/example/englishlearning/wordbook/BundledWordBookInstaller.kt`（若采用 assets 目录安装）
- Create: `app/src/main/java/com/example/englishlearning/wordbook/BundledWordBookSource.kt`
- Test: 对应 JVM/Android 词书 parser/source tests

**Interfaces:**
- `BundledWordBookInstaller.ensureInstalled(bookId: String): Result<File>`
- `BundledWordBookSource : WordCardSource`

- [ ] 将十本生成目录放入 assets，稳定 ID 为 `doctor-english`, `toefl`, `ielts`, `graduate-english`, `kaoyan-english`, `cet6`, `senior-high-school`, `junior-high-school`, `primary-school`, `cet4`。
- [ ] 预置目录先 staging、再 parser 校验、最后原子发布到 app 私有目录；失败清理 staging。
- [ ] 更新 metadata 的 displayName、level、totalWords、dataVersion、sourcePolicy、attribution。
- [ ] Composite 保持“用户导入优先、预置资源兜底”，不再让占位词覆盖正式词书。
- [ ] 测试实际 `totalWords == cardIds.size`，且 cardId 具有词书命名空间。

### Task 3: 增加整本词书进度查询

**Files:**
- Modify: `app/src/main/java/com/example/englishlearning/core/storage/dao/InternalLearningEventDao.kt`
- Modify: `app/src/main/java/com/example/englishlearning/learning/LearningRecordRepository.kt` 或新增 `WordBookProgressRepository.kt`
- Modify: `app/src/main/java/com/example/englishlearning/ui/LearningSetupViewModel.kt`
- Modify: `app/src/main/java/com/example/englishlearning/ui/AppScreen.kt`
- Modify: `app/src/main/java/com/example/englishlearning/ui/TodayPlanScreen.kt`
- Test: `app/src/test/...` and Compose tests

**Interfaces:**
- `data class WordBookProgress(val learned: Int, val total: Int)`
- `suspend fun progress(profileId: String, wordBookId: String): RepositoryResult<WordBookProgress>`

- [ ] 已学数量定义为目标词书 card_review_states 中存在状态的唯一 cardId 数量，不把其他词书或仅浏览计入。
- [ ] 词书选择卡显示名称、等级、`已学/总词数`和进度条；首页当前词书区域同步显示。
- [ ] 学习反馈成功、切换词书、返回首页后刷新进度；加载失败显示明确错误，不显示伪造 0%。
- [ ] 添加零词书、全部学完、状态超过总数时的测试与上限保护。

### Task 4: 实现跨词书重复词预览与幂等迁移

**Files:**
- Create: `app/src/main/java/com/example/englishlearning/learning/WordBookProgressMigration.kt`
- Create: `app/src/main/java/com/example/englishlearning/learning/WordBookProgressMigrationService.kt`
- Create: `app/src/main/java/com/example/englishlearning/core/storage/entity/WordBookProgressMigrationEntity.kt`
- Create: `app/src/main/java/com/example/englishlearning/core/storage/dao/InternalWordBookProgressMigrationDao.kt`
- Modify: `app/src/main/java/com/example/englishlearning/core/storage/AppDatabase.kt`
- Modify: learning event DAO/repository transaction boundary
- Test: migration service and Room migration tests

**Interfaces:**
- `preview(profileId: String, sourceBookId: String, targetBookId: String): ProgressMigrationPreview`
- `migrate(profileId: String, sourceBookId: String, targetBookId: String, candidates: List<...>): ProgressMigrationResult`

- [ ] 匹配只使用标准化后完全相同 lemma；同 lemma 多候选时不自动迁移并报告 ambiguous。
- [ ] 仅复制源 review state 到目标 card；目标已有更新状态时保留目标，源更新时才覆盖。
- [ ] 审计记录主键包含 profile/source/target/sourceCard/targetCard；状态写入与审计同一事务。
- [ ] Room 版本按当前真实版本递增，补 migration 与 schema；原表数据不丢失。
- [ ] 测试取消、重复执行、目标已有状态、源事件不增加、生词本不变化、事务失败回滚。

### Task 5: 接入选择页弹窗与当天计划规则

**Files:**
- Create/Modify: `app/src/main/java/com/example/englishlearning/learning/SwitchWordBookUseCase.kt`
- Modify: `app/src/main/java/com/example/englishlearning/ui/LearningSetupViewModel.kt`
- Modify: `app/src/main/java/com/example/englishlearning/ui/AppScreen.kt`
- Modify: `app/src/main/java/com/example/englishlearning/ui/TodayPlanViewModel.kt`
- Test: ViewModel and Compose tests

- [ ] 选择新词书后先预览；有候选时弹窗显示数量，按钮为“暂不标记”和“标记为已学习”。
- [ ] “暂不标记”只切书；“标记为已学习”执行幂等迁移后再切书；任何失败不显示保存成功。
- [ ] 当天已有反馈时不静默替换已提交计划，明确显示次日生效；未开始时才允许用户确认重建。
- [ ] 保持 profile、旧计划、旧词书状态和历史记录不被删除。
- [ ] 测试旧文案不存在、弹窗按钮、取消、失败重试和返回首页刷新。

### Task 6: 生成资源、全量验证与交付

**Files:**
- Create/Modify: `docs/decisions/2026-10-03-formal-wordbooks.md`
- Append: `.workbuddy/memory/2026-10-03.md`

- [ ] 运行转换工具生成并校验十本词书；保存数量和重复报告。
- [ ] 运行 JVM 单元测试、assembleDebug、assembleDebugAndroidTest；不运行 connectedDebugAndroidTest。
- [ ] 运行 `git diff --check`，确认未包含原始 Excel、密钥或用户数据库。
- [ ] 若有真实设备验收，先做 db/wal/shm MD5 基线，安装只用 `adb install -r -t`，再比较 MD5。
- [ ] 形成可复核验收记录，标明未完成项而不虚报完成。
