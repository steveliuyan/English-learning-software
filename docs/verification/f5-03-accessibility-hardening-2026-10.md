# F5-03 无障碍与发音播控硬化验收记录（2026-10）

分支：`stage-5-f5-03-accessibility-hardening`（基线 main tip `4b3f1a6`，首提交 `a67dd67`）
决策口径：`docs/decisions/2026-10-15-f5-03-accessibility-hardening-semantics.md`
矩阵行：`docs/verification/ac-traceability-matrix.md` AC5-03（已升级为「代码侧完成」口径）

## 0. 结论口径（红线，不得越界）

- **AC5-03 = 代码侧完成 + connected 语义断言；设备 TalkBack 走查归 `docs/handoff/2026-10-15-qa-round3-task.md` 第三轮任务书。设备轮未完成前，本文档与矩阵一律不得宣称「AC5-03 全绿 / 已验证」。**
- 触控：**7/8 站点代码侧达成 48dp + 词书字母 Rail 显式豁免**（命中区 ≈24×13dp，纵向未铺满整槽；代偿=整槽内命中 + 整轨拖动）。**禁止**读作「触控全部达标」或「命中区铺满整槽」。
- 动态字体：护栏钉「不为 0 宽 + 链接可达 + 高度有下限」，**不宣称「大字号不破版」**；三处 `fillMaxWidth().horizontalScroll()` 取证为版面 no-op，**禁止**读作「头部溢出已由 T11 修复」。
- liveRegion 容器反证（Task 10 M-6「删容器必红」RED 留痕）**未跑**，收口口径=**静态闭合**（评审 §③ 已封掉四种伪修形状），本文档如实写「未跑反证、静态闭合」，不写「已 RED 取证」。

## 1. 三条静态禁令契约（决策 §3，JVM 门禁内防回归）

复用 `RestoreNoBypassContractTest` 的剥注释源码扫描样板；每条契约 = 合成用例（must-flag + must-not-flag 双向）+ 真实文件回归断言 + 响亮扫描守卫（目录存在 / ≥1 .kt / 含 *Screen.kt）。

| 禁令 | 内容 | 契约类（Task 1–5） | 提交 |
|---|---|---|---|
| 禁令1 | ui 包内 `contentDescription` 字面量必须含中文（0x4E00–0x9FFF，含 `$` 插值模板豁免）；禁把任何名字含 `tag`（忽略大小写）的变量直赋给 `contentDescription` | `AccessibilityLabelContractTest` | `1dc49e4`+`f488dda` |
| 禁令2 | 禁同一 `buttonColors(...)` 调用内 `containerColor = MintPrimary` + `contentColor = Color.White` 共存；修复色统一 `AppPalette.OnMintPrimary`（≈4.6:1）；仓库级新口径「薄荷系渐变/浅底一律不用白字」 | `AccessibilityContrastContractTest` | `339d3da`+`c710c38` |
| 禁令3 | main 源码禁裸 `Color.Red`；错误文字用 `AppPalette.ErrorText`（0xFFB30000，白底≈7.2:1），成功文字用 `AppPalette.SuccessText`（0xFF1B5E20） | `AccessibilityColorTokenContractTest` | `f2d8875` |

契约已知漏判面（终审与设备走查须知，均已在各类 KDoc 记录）：变量传色、`containerColor = MintPrimary.copy(...)` 等嵌套右括号、内联 `if`、非 `buttonColors` 形态（`background(...)` + `color = ...`）、`//`-in-string 截断。这些形态源码不可判 → 需设备像素取证。设置徽标 `accent.deep` 底白字七域中六域 ≥5:1，`AiSpeech.deep≈4.1:1` 为临界值，终值归重设计轮 token 化。

## 2. 发音四控语义与限制（决策 §2）

四控 = 播放 / 暂停-继续 / 停止 / 重播；落点 = 学习流词卡、词卡详情、阅读页三处；收藏浮层/查词浮点等一次性浏览发音不加控件组，Provider 边界已统一暴露能力即满足 F3-02 首条。

- **TTS 侧只能报「已请求」**：`android.speech.tts.TextToSpeech` 在 compileSdk 34/35/36 的 `android.jar` 中**没有公开 `pause()/resume()`**（只有 `stop()`；javap + 编译错误双向取证），故 `AndroidTextToSpeechEngine.pause()/resume()` 如实返回 `false`。connected 断言只证「委托发生」，**无法自动化证明真出声/真静音**。四控真出声冒烟（Task 9 评审 I-8）归里程碑真机安装 + QA 第三轮。
- **暂停按钮呈现条件 = 选中引擎 ≠ SystemTts**（UI 侧引擎判断，不信 provider 返回值）；系统 TTS 下只呈现「停止」。词形发音恒走 Kokoro（`activeProvider` 与选中引擎无关），故系统 TTS 用户在词卡上**看不到本可工作的暂停**——`pauseResumeSupported` 偏保守，属已知代价（决策 §2），留待 provider 能力探测解除。
- **播控失败保持原状态**（决策 §2，取代计划原文五处 `else -> PronunciationStatus.Idle`）：`onPauseResume` 在「暂停/继续未被引擎接受」时保持原状态（仍 Playing 显 Playing、仍 Paused 显 Paused），「停止」呈现只取决于 `Playing || Paused`。落 `Idle` 会让整组控件（含唯一可用的「停止」）在朗读仍在继续时消失。
- **`customAction` 无自动化断言**：滑动删除的 `customActions(label = "删除词书")` 直接触发 `onDelete`（只弹二次确认，不真删）；**Compose 测试框架无断言 customAction 可达性的 API**，禁止伪造断言 → 删除动作可达性归设备 QA 第三轮。
- **停止 = 以 `Played` 收口**挂起续程并释放播放器；UI 靠代次计数器 + 停止时先自增防迟到文案覆盖 Idle。系统 TTS `Played` 是「提交成功」粒度（引擎无逐句完成回调），播完后暂停不可达属引擎限制。
- 语速 = 播放侧变速（系统 TTS `setSpeechRate`、音频 `MediaPlayer.playbackParams.setSpeed`）；`PronunciationCacheKey` 不纳入 rate（本地变速不产生新资产，见决策 §2 口径澄清）。Room v31→v32 加列 `speechRate`（驼峰），UI 四档循环 {0.75x,1.0x,1.25x,1.5x}，读写两侧 clamp 0.5–2.0。Router 词路径忽略调用方传入 `rate`、改读持久化偏好（`PronunciationRouter.kt:56`）。

## 3. liveRegion 常驻容器判例（决策 §4）

判例：live region 必须**一直存在于组合中**、靠子节点进出触发播报；把 `liveRegion` 配在随文案进出组合的瞬时节点上，节点不存在时无任何东西可宣告 = 等于没配。早期草稿「瞬时节点 + 尽力而为」口径作废。

三个常驻容器（`liveRegion = LiveRegionMode.Polite` 只在容器上，瞬时子节点自身不得再带）：

| 容器 tag | 承载屏 | 瞬时子节点 tag | 提交 |
|---|---|---|---|
| `word_card_status_live_region` | 词卡（submitting + 发音状态） | `word_card_submitting` / `word_card_pronunciation_message` | `0567b2a`+`803ed97` |
| `article_status_live_region` | 阅读页发音状态 | `article_pronunciation_message` | `46ae7e9`+`022a3d2` |
| `card_detail_status_live_region` | 词卡详情发音状态 | `card_detail_pronunciation_message` | `1ad0cdd` |

代价：容器恒在组合中 → 外层 `spacedBy` 在空态会多给一份已知间距（词卡卡与反馈按钮间距 14→28dp，`spacedBy(14.dp)`），归 UI 重设计轮版面重排。真机 TalkBack「是否真的不打断当前朗读地播报」仍只有结构证据，归设备 QA。

## 4. 语义补强批（决策 §4）

- Switch 四处补中文 `contentDescription`（`ReminderSettings`/`ArticleReading`/`BackupExport`/`WorksheetSettings`）：`a51333f`（契约 `AccessibilitySwitchLabelContractTest`）。
- 选中态 / Tab 角色 / 展开态 / Slider 值语义（`AccessibilityStateSemanticsContractTest`，正向存在性契约）：词书卡 `stateDescription=已选/未选`、底部导航与学习记录 Tab `Role.Tab`、许可卡与阅读历史分组卡 `已展开/已收起`、每日目标 Slider 描述带当前值：`02c4ba7`。
- 揭晓焦点：释义由隐藏→可见转变时 `FocusRequester.requestFocus()`（初始即已揭晓不抢焦点），`runCatching` 防未挂载异常：`0567b2a`。释义节点 modifier 链形状 `focusRequester → focusable → testTag → semantics` 不得重排或删除（JVM 无感）。

## 5. 触控 48dp 批（决策 §5，Task 10）

**7/8 站点代码侧达成 48dp**：`42a43ae`（48dp 8 站点）→ `1ad0cdd`（N-4 详情页容器）→ `d62c36b`（N-1/N-2/N-3）。connected 尺寸断言 6 类全绿：`CardDetailScreenTest` / `WordCardScreenTest` / `WordBookWordsScreenTest` / `CheckInScreenTest` / `LearningRecordsScreenTest` / `LicensesScreenTest`。六站点走过「main 文件整批退回 `0c014cb` 观察 RED 32.0–36.19dp → checkout 还原转绿」。

**第 8 站点 = 词书字母 Rail，显式豁免（有据偏离，非漏改）**：
- 算术不可达：Rail 常驻 26 槽、`slotPixels = min(18dp, maxHeight/26)`，26×48dp=1248dp 超屏高，且相邻 48dp 方形目标必然重叠、会抢走相邻字母 tap（实测：加 `minimumInteractiveComponentSize()` 后既有用例 `tappingTheActualRailSlotUsesTheLetterClickAction` 转红）。
- 代偿护栏 `railLetterHitAreaStaysInsideItsSlotAndCoversItsCentre`：命中区覆盖槽心、不越出整槽、不与相邻字母重叠。**现状命中区 ≈24×13dp（槽 24×18dp），纵向并未铺满整槽**——该站本轮**零行为改动**，护栏钉的是既有性质、只防退化不推改善，**不得读成「已按整槽收口」**。整轨拖动（`pointerInput`）为等效输入路径。
- 真 48dp 需让轨道可滚动/分页/减少常驻数 = 信息架构级改动 → 归 UI 重设计轮（先改 Spec 再实施）。豁免依据见决策 §5 字母栏豁免注记（`a422039` 回写）。

## 6. 动态字体批与固定高保留清单（决策 §5，Task 11）

批：`a422039`（决策 §5 字母栏注记）→ `83bac4e`（brief 点名批：固定 `height(N.dp)`→`heightIn(min=N.dp)`、chip 行 `horizontalScroll`、`maxLines` 补 `overflow=Ellipsis`）→ `26440b1`（Ruling S-1 同形站点收口，含底栏 64dp、`Button`/`PillButton`/含文字容器）→ `c3b3097`（§E 携带：四头部 Row 可横向滚动 + `fontScale=2.0` 护栏 + T10 措辞订正）→ `b25f9be`（三处注释口径订正，纯注释）。`ui` 包 `heightIn` 收口共 48 站点。

### 6.1 Canvas / 固定高保留清单（普查 `grep -rn "\.height([0-9]" ui/ | grep -av heightIn`）

剩余裸 `height()` **21 处全部落在保留清单**，非 Spacer 只剩 4 处（逐条给保留理由，杜绝「无声差集」）：

| 站点 | 值 | 保留理由 |
|---|---|---|
| `AppBottomBar.kt:59` | `height(1.dp)` | 顶部分隔线，几何描边 |
| `BackupRestoreScreen.kt:114` | `height(280.dp)` | 扫码框固定几何 |
| `PhotoTranslationScreen.kt:382` | `height(280.dp)` | 相机预览固定画布高 |
| `TodayPlanScreen.kt:373` | `height(10.dp)` | 进度条轨道几何 |
| `CheckInScreen.kt:145` | `size(132.dp)` | Canvas 打卡环绘制几何（`size()` 方框，不属 `height()` 正则面） |

其余 17 处为 `Spacer` 间距装饰（随父容器排版，无文字节点），一并保留。

### 6.2 普查盲区账（正则 `\.height([0-9]` 看不见变量高度与 `size()` 方框，Task 11 评审 I-3）

以下 3 处同族站点**不在上述 21 处普查内**，必须单独登记，**不许被读成「普查清零」**：

| 站点 | 形态 | 处置 |
|---|---|---|
| `WordBookWordsScreen.kt:318` | `.height(slotHeight)`（变量高度） | 字母 Rail 槽高，豁免依据=决策 §5 字母栏注记；归重设计轮 |
| `AiFeatureScreen.kt:84` | `size(44.dp)` 含字徽标 | fontScale≥1.5 字形顶出底块 → 归重设计轮 + 设备走查 |
| `SettingsScreen.kt:395` | `size(32.dp)` 含字徽标 | 同上，fontScale 顶档破版 → 归重设计轮 + 设备走查 |

### 6.3 horizontalScroll 版面口径（Task 11 评审 I-1，L9）

`ui` 包现有 7 处 `horizontalScroll`，本轮 §E 新增头部 4 处中：

- **3 处为版面 no-op**（`fillMaxWidth()` 在前 → 无滚动范围；摘掉滚动护栏仍绿，留着只为钉修饰符顺序，护栏不钉其存在性）：`CheckInScreen.kt:74`、`FavoritesScreen.kt:68`、`WrongWordsScreen.kt:60`。真实修法（标题脱离 `weight(1f)`）归 UI 重设计轮 L-T11-1。**评审明确不许摘 `fillMaxWidth()`**（会把标题挤成 0 宽=新回归）。
- **1 处为不同形状但安全**：`LicensesScreen.kt:62` 是**不带 `fillMaxWidth()`** 的裸 `horizontalScroll`（标题无 `weight`）。**Ruling T11-B：禁止后续把它「统一」成带 `fillMaxWidth` 的形状**（会把不可滚动标题挤成 0 宽）。
- 另 3 处为既有（非本轮改造）：`WordBookWordsScreen.kt:204` 筛选 Chip 行（早已有）、`ArticleReadingScreen.kt:225`、`PhotoTranslationScreen.kt:503`。

`fontScale=2.0` 护栏钉的是「头部标题不为 0 宽 + 链接可达 + 高度有下限」；**不覆盖 3.0 档**（见 §9 设备 TODO-USER）。L-T11-1：`fontScale=2.0` 下 CheckIn 标题被 `weight(1f)` 压到 78.48dp、换行 4 行（可达但不美观），结构性修法归 UI 重设计轮。

## 7. connected 全清单结果（21 类 / 266 例，全绿）

设备：`emulator-5574`（AVD `xtool-s04-api30`，sdk=30）。**绝未使用 `connectedDebugAndroidTest`；真机 `bf353dda` 未做任何 install/instrument**。构建：`:app:assembleDebug :app:assembleDebugAndroidTest --no-daemon` → BUILD SUCCESSFUL，两包 `adb -s emulator-5574 install -r` → Success。逐类命令模板：

```
adb -s emulator-5574 shell am instrument -w -e class <FQCN> \
  com.example.englishlearning.test/androidx.test.runner.AndroidJUnitRunner
```

| # | 类（`com.example.englishlearning.` 前缀省略） | 末行原文 |
|---|---|---|
| 1 | `ui.WordCardRevealFocusTest` | `OK (4 tests)` |
| 2 | `ui.WordCardPronunciationControlsTest` | `OK (3 tests)` |
| 3 | `ui.CardDetailPronunciationControlsTest` | `OK (5 tests)` |
| 4 | `ui.ArticlePronunciationControlsTest` | `OK (5 tests)` |
| 5 | `ui.ArticleReadingScreenTest` | `OK (23 tests)` |
| 6 | `ui.CardDetailScreenTest` | `OK (25 tests)` |
| 7 | `ui.WordCardScreenTest` | `OK (15 tests)` |
| 8 | `ui.WordBookWordsScreenTest` | `OK (22 tests)` |
| 9 | `ui.AppScreenTest` | `OK (45 tests)`（双跑 45/45、45/45，flaky 未复现） |
| 10 | `ui.AppBottomBarTest` | `OK (6 tests)` |
| 11 | `ui.LearningRecordsScreenTest` | `OK (3 tests)` |
| 12 | `ui.LicensesScreenTest` | `OK (3 tests)` |
| 13 | `ui.ReadingHistoryScreenTest` | `OK (8 tests)` |
| 14 | `ui.CheckInScreenTest` | `OK (7 tests)`（实际 7，含 T11 新护栏） |
| 15 | `ui.SpeechSettingsScreenTest` | `OK (12 tests)` |
| 16 | `language.infrastructure.AndroidAudioPlayerControlsTest` | `OK (4 tests)` |
| 17 | `core.storage.AppDatabaseMigrationTest` | `OK (28 tests)`（含 v31→32） |
| 18 | `language.KokoroProductionPronunciationTest` | `OK (10 tests)` |
| 19 | `ui.TodayPlanScreenTest` | `OK (17 tests)`（既有红已修） |
| 20 | `ui.ReadingAccessScreenTest` | `OK (13 tests)`（既有红已修） |
| 21 | `ui.WordAiQaScreenTest` | `OK (8 tests)`（既有红已修） |

**合计 21 类 / 266 例，全部绿**（18 类首跑即绿 + AppScreenTest 双跑绿 + 3 类既有红经测试侧修复转绿）。AC5-03 触控证据链闭合：6 个尺寸护栏类均 connected 实跑绿。

### 7.1 三处既有红处置（只改测试，RED→GREEN）

签名同为「`assertExists().performClick()` 后回调计数 0 = 未滚先点」N-4 族；Task 11 评审用 BASE A/B（`a422039` 构 app + 同一份 test APK）证明逐条同样红、同一行 → 既有债务、非本阶段引入，且三个 Test 文件在 `main..HEAD` 零改动。处置=只改测试（在被点节点前补 `performScrollTo()`，照 `SpeechSettingsScreenTest`/`WordBookWordsScreenTest` 既有先例），**未放宽/删除/跳过任何断言、未改生产码**（提交 `908eeed`）。

| 类 | 改前 RED | 修复点 | 改后 GREEN |
|---|---|---|---|
| `TodayPlanScreenTest` | `Tests run: 17, Failures: 1`（`ready_offers_check_in_entry` `:148` `expected:<1> but was:<0>`） | `:146` `today_plan_open_check_in` 前补 `performScrollTo()` | `OK (17 tests)` |
| `ReadingAccessScreenTest` | `Tests run: 13, Failures: 1`（`unlocked...OfflineHistoryEntry` `:82`） | 新增 import；`:79` `reading_history_entry` 前补 `performScrollTo()` | `OK (13 tests)` |
| `WordAiQaScreenTest` | `Tests run: 8, Failures: 1`（`answeredShows...EnabledSaveButton` `:60`） | 新增 import；`:58` `word_qa_save_note` 前补 `performScrollTo()` | `OK (8 tests)` |

### 7.2 AppScreenTest flaky 双跑结论

dispatch 已知 back/导航族 flaky 本轮未复现：PASS1 `OK (45 tests)`、PASS2（同一 test APK、无源码改动）`OK (45 tests)`。首跑即全绿、无失败用例需逐条复跑判定，按 SP-B 基线对照法登记为「本轮无 red、无 flaky 触发」，断言未改。

## 8. 门禁三条命令原文与末行

```bash
GRADLE="C:/Users/steveliuyan/.gradle/wrapper/dists/gradle-8.13-bin/5xuhj0ry160q40clulazy9h7d/gradle-8.13/bin/gradle.bat"
"$GRADLE" :app:assembleDebug :app:testDebugUnitTest --no-daemon --no-build-cache
"$GRADLE" verifyAcTraceabilityMatrix verifyThirdPartyNotices --no-daemon
bash scripts/secret-scan.sh
```

- 命令 1 末行：`BUILD SUCCESSFUL in 16s`（管道 `grep -aE 'FAILED|^e:|BUILD|tests completed'` 无 FAILED/`e:` 命中；exit 0）。
- 命令 2 末行：`BUILD SUCCESSFUL in 12s`（矩阵改动后 `--rerun-tasks` 单跑 `verifyAcTraceabilityMatrix` 再次 `AC matrix covered 51 ids with evidence.` + `BUILD SUCCESSFUL`）。
- 命令 3 末行：`secret-scan exit=0`。

**单测计数（python 逐份累加 `app/build/test-results/testDebugUnitTest/*.xml`）**：203 suites，**tests=1467 / failures=0 / errors=0 / skipped=0**，XML maxMtime=2026-10-16T22:31:53（本次 `testDebugUnitTest` 因 JVM 输入自 HEAD 生产码未变而 UP-TO-DATE，`--no-build-cache` 不复用缓存但仍跳过 up-to-date 任务，BUILD SUCCESSFUL 为通过信号，计数取持久化 XML；1467 基线只增不减成立）。

## 9. 设备侧 TODO-USER 清单（归 QA 第三轮 + 里程碑真机安装）

- **TalkBack 六链关键流程**（选词书 / 开始学习 / 三档反馈 / 完成状态 / 历史文章 / 数据管理入口）+ 词卡「显示释义」揭晓按钮焦点顺序遗留专项。
- **四控真出声冒烟（I-8）**：真机暂停→静音、继续→复播、停止→文案回空。
- **系统 TTS 下「只有停止」的可理解性**（呈现收窄，决策 §2/§6）。
- **真机 1.25x→1.0x 降速取证**（系统 TTS 变速只有 JVM 序列证据）。
- **滑动删除 `customAction` 可达性**（无自动化 API）。
- **揭晓焦点播报**、liveRegion 容器空态 `spacedBy` 幽灵间距版面确认。
- **动态字体顶档不破版**：`fontScale` 逐档走查，**必须含 `fontScale = 3.0` 与真机 MIUI 档**（connected 护栏只覆盖 2.0 档；按几何推演 3.0 档三条 48dp 链会吃满 370.6dp 内容宽、标题很可能已 <48dp，HEAD 代码在该档会让护栏变红）。同时取证：`AiFeatureScreen.kt:84 size(44.dp)`、`SettingsScreen.kt:395 size(32.dp)` 两处含字徽标顶档破版；放大后喇叭药片（≈60dp）与音标行/相邻按钮的打架情况。
- **深色走查**（Compose 侧深色完全未落地，token 化归 UI 重设计轮）。
- 字母栏触控实际可达性（≈24×13dp 命中区在真手指下的可命中率）。

## 10. 交下来的账（不 silently drop）

- **深色 / 硬编码色 token 化**、**`ClickableText` 逐词读屏**、**对比度 `AiSpeech.deep≈4.1:1` 临界** → UI 重设计轮（决策 §1/§3/§5 显式移交）。
- **字母栏真 48dp** → UI 重设计轮（轨道可滚动/分页/减常驻数，信息架构级）。
- **`spacedBy` 幽灵间距**（liveRegion 常驻容器代价）→ UI 重设计轮版面重排。
- **`AccessibilityContractSources` 抽公共 fixture**（本轮为不碰 T6 类复制了 helper，`stripComments`+扫描守卫已第 5 份副本）→ 终审一次修批。
- **Favorites / WrongWords 零设备取证**：两屏至今无 connected UI 测试类，两处 `weight(1f)` 头部只有编译级保证（Task 10 M-8 对 L2 的升级措辞=纯静态改动、零设备取证）。
- **L-T11-1**：`fontScale=2.0` CheckIn 标题被压到 78.48dp 换 4 行（可达不美观），结构性修法归 UI 重设计轮。
- **L4 / L-T11-2 / D-8**：详情页 liveRegion 容器「删容器必红」RED 留痕**未跑**（需临时破坏生产码，与「不带生效探针」纪律冲突）；收口口径=静态闭合（评审 §③ 已封四种伪修）。
- **I-1 no-op**：3 处 `fillMaxWidth().horizontalScroll()` 版面 no-op，护栏不钉其存在性；验证文档不写「头部溢出已由 T11 收口」。
- **I-3 盲区两枚含字徽标**：`AiFeatureScreen:84` / `SettingsScreen:395`，归重设计轮 + 设备走查（§6.2）。
- **L9 / D-4**：喇叭药片 MIS 在 padding 内侧、随节点放大到 ≈60dp（可见放大 + 头部大字号溢出风险）；`LicensesScreen.kt:62` 裸 `horizontalScroll` 禁止统一成带 `fillMaxWidth` 形状。
- **L8**：`WordCardPronunciationControlsTest.kt:26-29` 同类过期注释（Task 10 M-8），不影响断言通过，属文档账。
- **AppScreenTest flaky**：已知 back/导航族 8 flaky 基线对照法判定，本轮双跑全绿未触发（§7.2），不当回归。
