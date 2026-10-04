# 全量词汇搜索、搜索历史与频率统计设计

## 1. 背景与目标

当前应用已有词书搜索能力，但入口和语义主要围绕词书页面。新增主页顶部的全量词汇搜索，满足以下目标：

- 搜索范围覆盖所有可用词汇，不受当前活动词书限制；
- 内置正式词书与用户导入词书统一搜索；
- 点击结果进入现有词卡详情，复用发音、生词本、重学和笔记能力；
- 本地记录搜索历史，并按规范化单词累计搜索次数；
- 在设置中提供“显示搜索次数”开关，关闭显示不等于删除统计数据。

本功能不上传搜索内容，不修改学习事件、FSRS 状态或今日计划。

## 2. 范围与非目标

### 2.1 本期范围

- 主页顶部搜索入口；
- 全量词书检索；
- 精确、前缀、包含匹配；
- 空输入时展示最近搜索；
- 无结果、加载失败和重试状态；
- 搜索结果按词卡进入详情；
- 搜索历史聚合、最近时间排序、累计次数；
- 设置开关控制搜索次数文案；
- 清空当前资料的搜索历史；
- Room 迁移、JVM 测试、Compose 测试、真机验收与数据库三件套取证。

### 2.2 非目标

- 不做网络词典或在线补全；
- 不把搜索记录写入学习事件；
- 不按搜索次数自动改变复习计划；
- 不在本期增加新的词典数据集；
- 不支持跨资料共享搜索历史；
- 不删除已有的词书页面搜索能力，必要时复用同一领域用例。

## 3. 用户行为与界面

### 3.1 主页入口

主页顶部提供搜索框或搜索入口。点击后进入全量搜索状态，搜索页面显示：

- 输入框；
- 最近搜索列表（空输入时）；
- 搜索结果列表；
- 加载、无结果、失败和重试状态。

搜索提交前规范化查询：去除首尾空格、连续空白合并、英文按 `Locale.ROOT` 转小写。空查询不写入历史，也不发起全量扫描。

### 3.2 结果项

每个结果至少显示：

- lemma；
- IPA（有则显示）；
- 中文释义；
- 所属词书；
- 生词本当前状态；
- 学习状态（若现有领域模型可提供）。

同一规范化 lemma 在多本词书中出现时，结果按词书保留上下文，不跨词书合并为一条结果。点击结果进入现有 `CardDetailScreen`。

结果排序：

1. lemma 完全匹配；
2. lemma 前缀匹配；
3. lemma 或短语包含匹配；
4. lemma 字典序；
5. 词书名称字典序。

搜索结果最多显示 30 条，避免一次渲染全部词库。

### 3.3 历史列表

历史按 `lastSearchedAt` 倒序排列，默认显示最近 20 条。每个规范化查询只保留一条聚合记录，展示用户最近一次输入形式与关联的代表词卡信息。

示例：

```text
ability
四级词书 · 搜索 3 次
```

搜索次数开关开启时，在历史项和已有历史的结果项显示次数；关闭时隐藏次数，但仍继续累计。历史项点击后重新执行该查询，并再次累计一次搜索。

提供“清空搜索历史”，只清除当前资料的搜索历史，不影响学习数据、词书数据、生词本或设置。

## 4. 数据模型与存储

新增 `VocabularySearchHistoryEntity`：

```kotlin
@Entity(
    primaryKeys = ["profileId", "normalizedQuery"],
    indices = [Index(value = ["profileId", "lastSearchedAt"])]
)
data class VocabularySearchHistoryEntity(
    val profileId: String,
    val normalizedQuery: String,
    val displayQuery: String,
    val searchCount: Int,
    val firstSearchedAt: Instant,
    val lastSearchedAt: Instant,
    val representativeWordBookId: String?,
    val representativeCardId: String?,
)
```

关键约束：

- `(profileId, normalizedQuery)` 唯一，重复搜索更新同一行；
- `searchCount >= 1`；
- 计数与最近时间在同一事务中更新；
- 搜索历史属于本地资料，不跨 profile 共享；
- 清空操作按 profile 删除。

新增 `VocabularySearchHistoryRepository`，提供：

```kotlin
suspend fun list(profileId: String, limit: Int): RepositoryResult<List<VocabularySearchHistory>>
suspend fun record(profileId: String, query: String, representative: SearchRepresentative?, at: Instant): RepositoryResult<Unit>
suspend fun find(profileId: String, normalizedQuery: String): RepositoryResult<VocabularySearchHistory?>
suspend fun clear(profileId: String): RepositoryResult<Unit>
```

搜索次数设置复用现有本地学习设置体系，新增类型化字段，例如 `showVocabularySearchCount: Boolean = true`。不得把该设置放入搜索历史表。

数据库升级为 v23，增加搜索历史表；迁移必须保留全部既有数据。新增 schema JSON 与 migration test。

## 5. 领域与 UI 架构

### 5.1 全量搜索用例

新增 `SearchVocabularyUseCase`，统一处理：

1. 查询可见词书：内置词书与用户导入词书；
2. 通过现有 `WordCardSource` 读取各词书卡片；
3. 对查询规范化并计算匹配等级；
4. 稳定排序并截断结果；
5. 查询历史次数并附加到结果；
6. 搜索成功后记录一次历史。

现有 `WordBookSearchViewModel` 不应复制全量扫描算法。若其语义与新用例一致，应改为调用同一领域用例；若保留浏览分页，则浏览与查询可使用不同 request，但必须共享规范化与匹配规则。

### 5.2 ViewModel

新增或扩展 `GlobalVocabularySearchViewModel`，状态至少包含：

- `Idle`；
- `Loading`；
- `Ready(history, results)`；
- `Empty`；
- `Failure`；
- `ClearingHistory` 或等价提交状态。

ViewModel 要求：

- 查询变化取消旧任务；
- 旧请求不得覆盖新查询结果；
- 空输入只加载历史，不触发词库扫描；
- 保存历史失败不能抹掉已显示的搜索结果，应显示可重试提示；
- profile 切换时清空旧状态，禁止泄露其他资料的历史。

### 5.3 生产接线

- `MainActivity` 通过 Hilt 提供搜索 ViewModel；
- `AppScreen` 在主页顶部接入入口；
- 主页搜索 overlay 的返回行为遵循现有 BackHandler 优先级；
- 结果点击设置 `selectedSearchCard`，复用现有词卡详情与发音接线；
- 设置页增加显示搜索次数开关，并通过现有设置 ViewModel 持久化。

## 6. 错误与边界

- 词书列表读取失败：显示搜索失败和重试；
- 单本词书内容损坏：本次搜索失败关闭，不展示不完整结果；
- 空查询：展示历史，不写历史；
- 查询超过最大长度：截断到现有查询上限；
- 重复点击提交：一次用户搜索只增加一次次数；
- 历史写入失败：保留结果并允许重试，不伪造计数；
- 清空失败：保留列表并展示错误，不显示成功；
- profile 切换或协程取消：取消旧任务，不写入错误 profile；
- 占位卡片 ID 不参与搜索结果。

## 7. 测试与验收

### 7.1 JVM 测试

先写测试再实现，至少覆盖：

- 全量搜索不受活动词书限制；
- 内置与导入词书均可搜索；
- 精确匹配优先于前缀和包含匹配；
- 查询规范化；
- 同 lemma 多词书结果不错误合并；
- 空输入不扫描、不记录；
- 搜索历史首次记录为 1 次；
- 重复搜索累计次数并更新时间；
- 历史按最近时间排序；
- 清空只影响当前 profile；
- 设置关闭只隐藏次数，不删除统计；
- 旧搜索请求不能覆盖新请求；
- 存储失败可重试。

变异验证至少包含：

- 把全量词书过滤为活动词书，测试必须失败；
- 去掉规范化，空白/大小写测试必须失败；
- 把累计更新改成覆盖 1，重复搜索测试必须失败；
- 把设置关闭实现成删除历史，设置持久化测试必须失败。

### 7.2 Compose 与真机

Compose focused 覆盖：

- 主页入口存在并可打开；
- 空输入显示历史；
- 搜索结果显示词书上下文；
- 两本不同词书的同 lemma 分开展示；
- 搜索次数开关控制文案；
- 点击结果进入词卡详情；
- 无结果、失败、重试和清空历史。

真机验收：

- `assembleDebug assembleDebugAndroidTest`；
- `adb install -r -t`，不卸载、不清除用户数据；
- 仅使用 `am instrument` 跑 focused class；
- 前后读取 `english-learning.db`、`db-wal`、`db-shm` 并逐字节比对；
- 不运行 `connectedDebugAndroidTest`。

## 8. 交付边界

本功能完成标准是主页入口、全量检索、历史和次数开关均可实际使用，且生产接线、错误路径、测试和真机证据全部闭合。只新增 DAO、枚举或按钮但未贯通用户流程，不视为完成。
