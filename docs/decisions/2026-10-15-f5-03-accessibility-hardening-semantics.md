# 2026-10-15 F5-03 无障碍收口与发音四控语义决策

## 背景

阶段 5 代码面仅剩 F5-03（无障碍与发音播控）存在真缺口。两路子代理审计（AppScreen 族 + 外围屏族）
结论合成为：blocker = 发音「播放/暂停/重播/速度」四控在 Provider 接口与播放层完全缺失、
测试标签字面量当 contentDescription、低对比主 CTA；major = Switch 无标签、词书卡选中态/滑动删除
无替代语义、展开组件无状态播报、48dp 触控多点不达标、36 处固定高度对动态字体不友好、
ClickableText 无逐词语义、深色 Compose 未落地。本决策锁定实现计划
`docs/superpowers/plans/2026-10-15-stage-5-accessibility-hardening.md` 的口径。

需求源：`docs/specs/03` F3-02「统一暴露播放、暂停、重播、速度和错误状态」与缓存键条款（:26/:30）、
`docs/specs/05` F5-03（动态字体、深色、小屏、TalkBack、播控、离线文字状态）。

## 决定

### 1. 范围切分：深色模式归 UI 重设计轮

Compose 侧深色完全未落地（全仓无 `isSystemInDarkTheme`/`darkColorScheme`，night 资源仅闪屏）。
结构性换肤与硬编码色（MintXxx 系）的 token 化按既有裁决归接下来的 UI 视觉重设计轮
（用户策略：先功能后美化）。本轮只做：浅色内的对比度定点修复 + 语义/触控/动态字体适配。
F5-03 的深色与 TalkBack 条款在 AC 矩阵与验证文档中如实标注「代码侧完成，设备走查归 QA 第三轮」。

### 2. 发音四控语义

- **播放** = 既有 `speak*`；**重播** = 再次 `speak`（系统 TTS 用 QUEUE_FLUSH 天然打断；
  音频文件路径重走播放，合成缓存命中不重推理）。
- **暂停/继续/停止** 上收到 `PronunciationProvider` 接口：
  `pausePlayback(): Boolean`、`resumePlayback(): Boolean`、`stopPlayback()`，
  默认实现「不支持」（返回 false / 空操作），旧 fake 与未支持引擎零破坏。
- `AudioPlayer` 增加当前会话句柄：`pauseCurrentPlayback/resumeCurrentPlayback/stopCurrentPlayback`。
  **停止 = 以 `Played` 恢复挂起的 play 续程并释放播放器**（用户主动停止视为一次正常结束）；
  UI 侧靠既有代次计数器（`*PronunciationRequest`）+ 停止时先自增，防止迟到的 Played 文案覆盖 Idle 呈现。
- `PronunciationRouter` 记录 `activeProvider`（每次发起 speak 时设置；stop 后清空），
  pause/resume/stop 全部委托它；无活动会话时返回 false。该语义全部 JVM 可测（fake provider 矩阵）。
- **语速 = 播放侧变速**：系统 TTS 走 `setSpeechRate`，音频文件走 `MediaPlayer.playbackParams.setSpeed`
  （minSdk 26，均可用）。AI 语音请求参数不变 → 合成字节不变 →
  **`PronunciationCacheKey` 不纳入 rate**。spec 03 :30「缓存键为规范化文本、语音参数与模型版本」中
  的「语音参数」按「生成侧参数」解释：providerId/model/voice/accent/规范化文本 已在键内；
  本地变速不产生新资产。此为对口径的澄清而非偏离，记录于此。
- 语速持久化：`speech_preferences` 加列 `speechRate REAL NOT NULL DEFAULT 1.0`
  （列名按本表既有惯例取驼峰，与 v27 `autoSpeakOnCardReady` 及全库 `ADD COLUMN` 先例一致；
  早期草稿写过 `speech_rate`，Task 8 派发前订正——Room 默认列名即字段名，蛇形名会迫使实体加
  `@ColumnInfo(name=...)` 并成为该表唯一的异构列），
  **Room v31→v32 非破坏迁移**（照 v27 `autoSpeakOnCardReady` 加列先例：`@ColumnInfo(defaultValue)` +
  `ALTER TABLE ADD COLUMN`，禁 destructive）。读写两侧 clamp 到 0.5–2.0；
  UI 为四档循环按钮 {0.75x, 1.0x, 1.25x, 1.5x}，落点语音设置页（与「学习时自动发音」同族 Row）。
- **读改写口径（Task 8 派发前取证并固化）**：`SpeechSettingsViewModel` 的 `select()` 与 `addMiMoPreset()`
  是用**当前 UI 状态**手工构造 `SpeechPreference(...)`（不是读 stored 后 copy），
  因此偏好每加一个字段都必须同时补：这两处显式带上现值、`reload()` 把 stored 映射进 `SpeechSettingsUiState`。
  否则用户换引擎/建预设会把新字段静默复位（`:78` 的既有注释正是同一坑的历史教训）。
- `PronunciationStatus` 新增 `Paused`（文案「已暂停播放，再次点按可继续。」）。
- **已接受限制**：系统 TTS 的 `Played` 是「提交成功」粒度（引擎无逐句完成回调接入），
  暂停按钮仅在 Playing/Paused 窗口呈现；TTS 播完后暂停不可达属引擎限制，
  真机 TalkBack 表现归设备 QA 第三轮取证。
- **平台事实与暂停按钮呈现（Task 7 取证，Task 9 消费）**：
  `android.speech.tts.TextToSpeech` 在 compileSdk 34/35/36 的 `android.jar` 中**没有公开 `pause()/resume()`**
  （只有 `stop()`；javap + 编译错误双向取证），因此 `AndroidTextToSpeechEngine.pause()/resume()` 如实返回 `false`。
  由此修正 §2 早先「靠返回值自然降级」的隐含假设：**UI 不得只依赖 `pausePlayback()` 返回值决定按钮呈现**——
  按计划的状态机，系统 TTS 下点「暂停」会落 `else → Idle`，按钮组随之消失，而朗读仍在继续且用户失去可用的「停止」。
  裁决=词卡/详情/阅读页三处的**暂停-继续按钮以「选中引擎 ≠ SystemTts」为呈现条件**（能力判断走 UI 侧引擎选择，
  非 provider 返回值）；系统 TTS 下只呈现「停止」。若后续把系统 TTS 改为 `synthesizeToFile()` + `MediaPlayer` 播放，
  播控落回 `AudioPlayer` 一侧，届时解除该呈现条件即可，接口签名不变。
  该呈现收窄已登记 QA 第三轮（TalkBack 下「只有停止按钮」的可理解性）。
- **播控失败的状态机口径（Task 9 评审后裁决）**：`onPauseResume` 在「暂停/继续未被引擎接受」时
  **保持原状态不变**（仍 `Playing` 就继续显示 `Playing`，仍 `Paused` 就继续显示 `Paused`），
  **不得**落到 `Idle`——落 `Idle` 会让整组控件（含唯一可用的「停止」）在朗读仍在继续时消失，
  与上一条「停止始终可达」的立论相悖。计划原文五处 `else -> PronunciationStatus.Idle` 按本条修正；
  「停止」按钮的呈现条件因此只取决于 `Playing || Paused`，与暂停是否可用无关。
  已知限制（同条裁决登记）：词形发音恒走 Kokoro（`activeProvider` 与选中引擎无关），
  故「选中引擎 ≠ SystemTts」这一呈现条件会让系统 TTS 用户在词卡上看不到本可工作的暂停——
  按字面执行、代价可接受，留待将来引入 provider 能力探测时解除。
- **一次会话句柄约定（Task 7 修复的既有缺陷）**：`AndroidAudioPlayer` 只跟踪一个当前会话，
  新的 `play()` 在安装新句柄前必须先用与「用户停止」同一口径的 `retireCurrentSession()` 收口旧会话
  （旧续程以 `Played` 立即返回、旧 `MediaPlayer` 立即 release）。
  否则「暂停中播放下一句」会让上一次的 `speak()` 永久挂起（学习流卡在 Playing）并泄漏上一个播放器。
  该保护只有设备可证（JVM 无法加载 `MediaPlayer`），取证在 connected `AndroidAudioPlayerControlsTest`。
  同一身份守卫必须覆盖 `onPrepared` 的**整个回调体**（`preparing = null; active = mp; mp.start()`）：
  迟到的 prepared 回调一律整体丢弃，否则旧播放器会把已 release 的死句柄顶替进 `active` 并在主线程抛 `IllegalStateException`；
  该时序在 JVM 与设备上均不可稳定取证，归代码审查约束。
- 四控 UI 落点 = 学习流词卡、词卡详情、阅读页三处（`PronunciationControls` 形态的暂停/继续+停止
  按钮组随状态呈现；重播=原喇叭/发音按钮再点）。收藏浮层/查词浮点等一次性浏览发音不加控件组，
  Provider 边界已统一暴露能力即满足 F3-02 首条。

### 3. 源码契约固化（JVM，门禁内防回归）

复用 `RestoreNoBypassContractTest` 的剥注释源码扫描样板，新增三个契约（先红后绿，随各自修复任务落地）：

1. **禁 tag 当描述**：`ui` 包内所有 `contentDescription = "…"` 纯字面量必须含中文字符
   （含 `$` 插值的模板串豁免）；禁 `contentDescription = tag` 变量直赋。
2. **禁低对比组合**：`buttonColors(… containerColor = MintPrimary … contentColor = Color.White …)`
   同调用内共存即违规。修复色统一 `AppPalette.OnMintPrimary = Color(0xFF174C44)`
   （≈4.6:1，与 PillButton 品牌渐变深字同源，PillButton 顺手引用该 token）。
3. **禁裸 `Color.Red`**：main 源码零出现；错误文字用新 token `AppPalette.ErrorText`
   （= `DomainColors.Danger.deep` 值 0xFFB30000，白底 7.2:1），成功文字用 `AppPalette.SuccessText`
   （0xFF1B5E20）。

设置徽标（`accent.base` 底白字）改 `accent.deep` 底白字：七域中六域 ≥5:1，
AiSpeech.deep≈4.1:1 为临界值，徽标信息与同行标题重复属装饰性冗余，终值归重设计轮 token 化。

### 4. 语义补强口径

- Switch 四处照仓库唯一有标签样板（AppScreen 设置行 `semantics { contentDescription = 标题 }`）补标签。
- 词书卡选中态：Row 语义 `stateDescription = 已选/未选`（contentDescription 保留中文动作文案）。
- 滑动删除：`SwipeToDismissBox` 语义 `customActions(label = "删除词书")` 直接触发 `onDelete`
  （手势语义不变、confirmValueChange 恒 false 的「滑不删、弹确认」红线不动——为 TalkBack 提供无手势替代路径）。
- 底部导航 `TabSlot` 与学习记录 `Tab`：`role = Role.Tab` + `stateDescription` 选中态。
- 展开组件（许可 NoticeCard、阅读历史版本组）：`stateDescription = 已展开/已收起`。
- Slider：contentDescription 动态带上当前值（「每日新词目标，当前 N 词」）。
- liveRegion：词卡 `submitting`/发音状态文字、**阅读页发音状态文字**与**词卡详情页发音状态文字**一律挂在**常驻容器**上
  （Task 6 判例：live region 必须一直存在于组合中、靠子节点进出触发播报；把 `liveRegion`
  配在随文案进出组合的瞬时节点上，节点不存在时没有任何东西可宣告，等于没配。
  早期草稿的「瞬时节点 + 尽力而为」口径作废，改按容器形状统一实现，瞬时子节点自身不得再带 `liveRegion`）。
  详情页一项由 Task 9 修复轮复评补入（N-4：`CardDetailScreen` 的状态文字是三种详情宿主共用的瞬时 `Text`，
  与阅读页同形同病；容器 tag 用 `card_detail_status_live_region`，`card_detail_pronunciation_message`
  仍留在瞬时子节点上，与阅读页同一套断言形状）。
  代价=容器空态时外层 `spacedBy` 会多出已知间距（归 UI 重设计轮）；真机 TalkBack 播报仍归设备 QA 取证。
- 揭晓焦点：释义由隐藏→可见的**转变**时 `FocusRequester.requestFocus()`（初始即已揭晓不抢焦点），
  全仓首个焦点顺序改动，`runCatching` 防未挂载异常。

### 5. 触控与动态字体口径

- 48dp：小触控件加 `Modifier.minimumInteractiveComponentSize()`（发音喇叭两处、字母栏）；
  文字链接「‹/← 返回/返回」加 `defaultMinSize(48.dp)`；TodayPlan 46dp 按钮并入高度改造。
- 动态字体：按钮/卡片容器固定 `height(N.dp)` → `heightIn(min = N.dp)`（逐点清单见计划 Task 11）；
  Canvas 图表保留固定高（绘制几何），但日历单元 `height(34.dp)` → `heightIn(min = 34.dp)`；
  chip 行加 `horizontalScroll`；`maxLines` 处补 `overflow = TextOverflow.Ellipsis`。
- ClickableText 逐词读屏不在本轮改造（重设计轮行为变更）：本轮给整篇正文节点保留可读文本
  （现状 Text 本体即可读），高词点击维持视觉+手势路径；记录为已知限制。

### 6. 测试与门禁口径

- 门禁仍是 JVM 单测全绿（基线只增不减；计划写下时为 1396，Task 1–7 全部落地后实测 **1450**，以每任务实测值为准）；
  契约/Router/Provider/状态/VM 全部 JVM TDD 先红。
- Compose 语义断言进 androidTest（connected），按 `android-connected-qa-loop` 的
  `adb install -r` + `am instrument` 实跑（禁 connectedDebugAndroidTest）；
  **设备口径更正**：计划原文写的 `emulator-5576` 本机当前没起，实际一律锁 `ANDROID_SERIAL=emulator-5574`
  （API 30）。真机 `bf353dda` 只做 `adb install -r` 供用户手工走查，**绝不在真机跑 `am instrument`**。
  `AppScreenTest` 已知 8 个 back/导航族 flaky 以基线对照法判定，不当回归。
- Room v31→v32 迁移测试加进 `AppDatabaseMigrationTest`（androidTest，带旧行回填断言）。
- 收口时矩阵 AC5-03 行升级为「代码侧完成 + connected 语义断言 + 设备走查待 QA 第三轮」口径，
  新增 `docs/verification/f5-03-accessibility-hardening-2026-10.md`。

## 影响

- 接口扩面全部走默认实现，第三方 provider（MiMo/OpenAI 兼容/Kokoro）按各自播放路径覆写；
  `SpeechPreference`/Entity/双 mapper/VM/设置页链路一处加字段全线打通（照 autoSpeak 先例）。
- 不改数据库破坏性路径、不新增网络/权限/依赖；API Key 相关面零接触。
- 深色/硬编码色 token 化/ClickableText 逐词语义 → UI 重设计轮任务书需带上这三条遗留。
