# UI 重设计轮实现计划（3 批）

> **For agentic workers**: REQUIRED SUB-SKILL: 用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实施本计划。步骤用 checkbox（`- [ ]`）跟踪。

**Goal:** 把 33 屏「不统一、太乱、有些页面不该出现这些东西」的体验问题，一次解决到可发布水位——统一设计系统（浅/深两套 token + 装配点）+ 九个共享组件 + 33 枚 B 案双色层次图标 + 每屏只承担自己的职责。

**Architecture:** 三批串行。批 1 建底座：校准 token、建立唯一的 `MaterialTheme(...)` 装配点与主题三态、把 `Mint*` 令牌改成 CompositionLocal 读取（611 行读点里仅 3 处需就地提读，其余零重写即获得深色）、入库 `AppIcons.kt` 与 `IconBadge`、把九个共享组件建出来。批 2 用底座重排核心学习路径五屏。批 3 重排其余屏与入口分组（信息架构六条裁决），并用「棘轮测试」把私有实现的份数从 14/7/4/11 收敛到 1。
每批用「契约测试读源码常量 + 计算 WCAG 相对亮度」守门（JVM 单测无法渲染 Compose），需要像素证据的用例走 connected（仅模拟器）。

**Tech Stack:** Kotlin、Jetpack Compose（Material3）、Hilt、Room（本轮**不加列、不做迁移**）、`Context.getSharedPreferences`（框架 API，项目此前未使用）、JUnit5 + kotlin.test、`androidx.test` + `captureToImage`（androidTest）。

**Spec:** `docs/superpowers/specs/2026-10-17-ui-redesign-design.md`（B 案图标已于 2026-10-18 定稿，见 `docs/decisions/2026-10-18-ui-redesign-icon-duotone.md`）。执行者每任务先读 brief，再读 spec 的相关小节——冲突以 spec 为准，spec 与实测冲突时按本计划 Task 1 的裁决文档处理。

---

## Global Constraints（每任务隐含包含本节）

1. **不新增依赖**。`app/build.gradle.kts:163-165` 是 `dependencyLocking { lockAllConfigurations() }`；任何任务的 diff 若改动 `app/gradle.lockfile` 即判失败。图标以自绘路径数据内嵌，不引 `material-icons-extended`（`AppBottomBar.kt` 顶部注释已写明同一理由）。
2. **不改底部四栏骨架与 `AppTabTest` 钉住的顺序**（spec §1 不做的事 #4）。`AppTab` 四个条目、`contentDescription`、`stateDescription`、`testTag("app_tab_*")` 全部保持。
3. **不新增业务功能、不动业务规则**：FSRS 调度、每日计划不可变快照、严格/宽松解锁、备份认证与配对、删除级联确认、词书独立进度一律保持现状。
4. **不新增**账号、云同步、订阅、广告、社交、远程代码执行、任意 AI 请求覆盖（AGENTS.md）。
5. **主题三态落 `getSharedPreferences`，不进 Room、不进备份包**（spec §5.2）：显示偏好不是学习数据；`fileProvider`/备份代码路径零改动。
6. **颜色硬规则**（spec §2.1，F5-03 移交不得回潮）：
   - 浅薄荷底（`tint`/`tint-2`/`tint-3`/`mint-light`）与品牌渐变上**禁止白字**，前景取 `on-mint #174C44` 或 `on-brand #04241C`（渐变末端见 Task 2 的允许配对表）。
   - `ui/` 布局代码禁止新增裸 `Color(0xFF…)` 字面量；站点清单见 Task 2 的 seed allowlist，只减不增。
   - 破坏性操作用色全站一套：文字 `error`、填充 `error-fill`。
7. **对比度按 WCAG 相对亮度当场计算，禁止估**（spec §9 AC-R-03）。公式：`c = srgb/255`；`c ≤ 0.04045 ? c/12.92 : ((c+0.055)/1.055)^2.4`；`L = 0.2126R + 0.7152G + 0.0722B`；`ratio = (Lhi + 0.05) / (Llo + 0.05)`。正文 ≥4.5:1，图形/非文本 ≥3:1。
8. **触控下限 48dp**：`heightIn(min = 48.dp)` 或 `defaultMinSize(minHeight = 48.dp)`，禁止靠 padding 凑（spec §2.3）。
9. **`testTag` 与 semantics 保持**：重排一屏时，该屏既有 `testTag` 字符串与 `contentDescription`/`role`/`stateDescription` 不得改名或删除；新增控件才新增 tag。批 2/批 3 每屏开工前先 `grep -n 'testTag'` 该屏并把清单写进报告（spec §10.3）。
10. **JVM 单测基线只增不减**，且每次收口**重算**、不引用陈旧数。当前基线（本计划撰写时实测）：**206 suites / 1499 tests / 0 skipped / 0 failures / 0 errors**。重算命令：
    ```bash
    grep -h -o 'tests="[0-9]*" skipped="[0-9]*" failures="[0-9]*" errors="[0-9]*"' \
      app/build/test-results/testDebugUnitTest/TEST-*.xml \
      | awk -F'"' '{t+=$2;s+=$4;f+=$6;e+=$8} END{print "tests="t" skipped="s" failures="f" errors="e}'
    ```
11. **门控命令**（本机 Gradle 8.13-bin，禁用 `gradlew`；顺序不可颠倒，管道退出码必须显式取）：
    ```bash
    V=$(grep -oE 'gradle-[0-9]+\.[0-9]+' gradle/wrapper/gradle-wrapper.properties | head -1)
    GRADLE=$(ls -1 "$HOME/.gradle/wrapper/dists/$V-bin/"*/"$V/bin/gradle.bat" | head -1)
    "$GRADLE" :app:assembleDebug :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin \
      verifyThirdPartyNotices --offline --no-daemon --no-build-cache 2>&1 \
      | grep -E 'FAILED|^e:|BUILD|tests completed' ; exit ${PIPESTATUS[0]}
    ```
    门禁是**单测全绿**，不是 detekt/ktlint（存量约 1580 条为债，非回归，勿当回归修）。
12. **connected 用例**：只允许 `adb install -r` + `am instrument`，**绝禁 `connectedDebugAndroidTest`**（会清空设备数据）。instrumented 只在 `emulator-5574`；**绝不在真机 `bf353dda` 上跑 instrument 或安装测试包**。真机只做手工试用（里程碑 `adb install -r app/build/outputs/apk/debug/app-debug.apk`）。
13. **禁止 `Icons.`**：全站 `Icons.` 引用保持 0（`grep -rn '\bIcons\.' app/src/main/java | wc -l` 必须为 0）。
14. **提交纪律**：按功能域逐个 `git add` 文件，禁 `git add -A`/`.`；身份显式带上：
    ```bash
    git -c user.name=steveliuyan -c user.email=2021250388@qq.com commit -m "<type>(<scope>): 中文摘要"
    ```
    不用 `--amend`、不用 `--no-verify`。消息风格沿用历史：`type(scope): 中文描述`，type ∈ feat/fix/test/docs/chore。
15. **推送核验**：每批收口推送后必须
    ```bash
    git -c credential.interactive=false ls-remote origin <branch>   # 与 git rev-parse HEAD 比对
    ```
    哈希一致才算备份成功；未核验不得宣称已推送。禁 force push、禁删远端分支。
16. **文档规则**：范围/取舍变化先改 spec 或写 `docs/decisions/YYYY-MM-DD-<topic>.md` 再实施；文档禁写会自漂移的计数（"共 N 处"），要写就写成可重算的命令。
17. **安全红线（不因 UI 轮而松动）**：API Key 只在不进 Room、不进日志/导出包的 Keystore 支撑区；密钥/挑战码明文/配对 QR 截图不入库（放 `.git/qa-scratch/`）；`local.properties` 与发布 keystore 绝不入库、不进报告。

---

## 文件结构（本轮新建与主要改动）

新建（批 1）：

| 文件 | 职责 |
|---|---|
| `app/src/main/java/com/example/englishlearning/ui/theme/AppColors.kt` | `AppColorSet`（16 个中性 token 的一份取值）、`AppColorLight`/`AppColorDark`、`AppDomain` 枚举 + `DomainPalette` + 浅/深两套域色、`LocalAppColors`/`LocalDomainColors`/`LocalIsDark`、`appColors()`/`appDomain(AppDomain)` 读取器 |
| `app/src/main/java/com/example/englishlearning/ui/theme/MintGlassTheme.kt` | 唯一 `MaterialTheme(...)` 装配点：`MintGlassTheme(...) { … }` 内 `CompositionLocalProvider` + `MaterialTheme(colorScheme, typography, shapes)`；`appTypography()`、`appColorScheme()` |
| `app/src/main/java/com/example/englishlearning/ui/theme/AppThemePreference.kt` | `enum ThemeMode`、纯函数 `shouldUseDarkTheme`/`themeModeFromStorage`、`AppThemePreferences.read/write`（prefs `"ui_display_prefs"` / key `"theme_mode"`）、`rememberAppThemeMode()` |
| `app/src/main/java/com/example/englishlearning/ui/components/AppIcons.kt` | 33 枚 B 案图标的数据：逐枚清单钉在文件头注释，每枚 `AppIconSpec(strokePaths, fillPaths)` |
| `app/src/main/java/com/example/englishlearning/ui/components/AppIcon.kt` | 运行时用 `ImageVector.Builder`/`vector {}` 把 `AppIconSpec` 组装为可主题化的 `ImageVector`（描边骨架层 + 半透明填充层） |
| `app/src/main/java/com/example/englishlearning/ui/components/IconBadge.kt` | 两种呈现：入口层（≥40dp 容器 = 域色渐变 + 白色两层字形 + 1px 内高光）、行内层（30dp 容器 / 17dp 图形 = `tint` 底 + 域色 `deep` 单层描边） |
| `app/src/main/java/com/example/englishlearning/ui/components/AppScreenHeader.kt` | 返回 + 标题 + 副标题 + 右侧动作，全站唯一页头写法；`fontScale 2.0` 不裁字 |
| `app/src/main/java/com/example/englishlearning/ui/components/ConfirmDestructiveDialog.kt` | 级联条数 + 保留面 + 不可撤销 + 双按钮，措辞统一 |
| `app/src/main/java/com/example/englishlearning/ui/components/WordRow.kt` | lemma + 释义 + 状态/时间 + 右侧动作 |
| `app/src/main/java/com/example/englishlearning/ui/components/NotConfiguredCard.kt` | AI 未配置：一句现状 + 一个「去配置 ›」出口，绝不做只给文字的死屏 |
| `app/src/main/java/com/example/englishlearning/ui/components/InlineNotice.kt` | 错误/回执行统一样式（`InlineNoticeKind.Error` / `.Info`） |
| `app/src/main/java/com/example/englishlearning/ui/components/ScreenUnavailable.kt` | 整屏不可用态：原因 + 一个动作 |
| `app/src/main/java/com/example/englishlearning/ui/components/SpeakerButton.kt` | 喇叭药片（≥48dp）+ 暂停/继续/停止文案一处定义 |
| `app/src/main/java/com/example/englishlearning/ui/components/EntryRow.kt` | 彩色图标入口行：`IconBadge` + 主标题 + 副标题 + 状态或 `›` |
| `app/src/main/java/com/example/englishlearning/ui/components/OutboundConfirmDialog.kt` | 出站确认：按域名记忆、图片每次确认 |
| `app/src/test/java/com/example/englishlearning/ui/SourceRatchet.kt` | 共享测试夹具：`uiFiles(dir)`、`stripComments`、`countMatches`、`violations(label, rule, allowlist, scanDir)`（含目录缺失/零文件即失败的静默空扫守卫） |
| `app/src/test/java/com/example/englishlearning/ui/DesignTokenContractTest.kt` | 读源码 hex 断言 token 取值 + 钉死的允许配对表（AC-R-01/03） |
| `app/src/test/java/com/example/englishlearning/ui/MaterialThemeAssemblyContractTest.kt` | 装配点存在、`MintGlassTheme` 是 `MainActivity` 的唯一内容根、禁第二处 `MaterialTheme(` |
| `app/src/test/java/com/example/englishlearning/ui/ThemeModeDecisionTest.kt` | 三态 × 系统态 → 是否深色的纯函数全组合 |
| `app/src/test/java/com/example/englishlearning/ui/AppIconsStructureContractTest.kt` | 33 枚清单、两层结构、豁免表、`Icons.` 为 0 |
| `app/src/test/java/com/example/englishlearning/ui/IconBadgeContrastContractTest.kt` | 渐变端取 `deep` 混 35% `base`、白字形 ≥3:1、行内 ≥3:1、禁 `base` 本体作白字形底 |
| `app/src/test/java/com/example/englishlearning/ui/RawColorLiteralRatchetTest.kt` | 裸 hex 棘轮：seed 13 处只减不增 |
| `app/src/test/java/com/example/englishlearning/ui/SharedComponentUniquenessRatchetTest.kt` | 私有实现份数棘轮：页头 14、WordRow 7、NotConfigured 4、InlineNotice 11 → 0 |
| `app/src/test/java/com/example/englishlearning/ui/DarkTokenConsumptionRatchetTest.kt` | 静态 `AppPalette.`/`DomainColors.` 读点棘轮：按屏计数，只减不增 |
| `docs/verification/ui-redesign-2026-10.md` | AC-R-* 证据表（`verifyAcTraceabilityMatrix` 只吃 `AC[0-4]-\d{2}`，AC-R 不进矩阵，故单独成文） |

主要改动：`ui/theme/MintPalette.kt`（`Mint*` 变组合期读取器）、`ui/theme/AppPalette.kt`（取值校准 + 补齐缺失 token）、`ui/theme/AppType.kt`（接装配点）、`ui/AppScreen.kt`、`ui/AppBottomBar.kt`、`MainActivity.kt`（内容根换 `MintGlassTheme`）、批 2/批 3 的逐屏文件（每任务里点名）。

不动：`data/` Room schema 与迁移、`di/`、备份链路（除组件替换外）、`AppTabTest`、`app/gradle.lockfile`。

---

## 深色模式迁移策略（本计划的核心决策，先读这段再动手）

现状（Task 3 落地后实测口径，重算命令见下）：`ui/theme/MintPalette.kt` 定义七个 `internal val Mint*`，`ui/` 里对它们的读点是 **808 处出现 / 611 行非 import 读取 / 31 个文件**（`c64df0a` 后为 803 / 606 / 31）。读点绝大多数在组合期内，但**并非全部**位于 `@Composable` 函数体内——Task 3 现场撞出 3 个组合期之外的读取站点：两处 `Canvas` 的 `DrawScope` lambda（`CardDetailScreen.SpeakerGlyph`、`WordCardScreen.DetailSpeakerIcon`）和一处返回 `Color` 的非组合辅助函数（`TodayPlanScreen.WorkloadKind.color()`），处置是把读取提到组合期局部 `val` / 给表加 `@Composable`。组合期之外的既有读取另有：3 处顶层 `Brush.linearGradient(listOf(AppleMintStart, AppleMintMiddle, AppleMintEnd))`（`AiFeatureScreen.kt:47`、`AiLearningScreen.kt:56`、`AppScreen.kt:100`，读的是保持静态的 `AppleMint*`）和 1 处默认参数 `accent: DomainAccent = DomainColors.AiSpeech`（`glass/GlassButton.kt:47`，读保持静态的 `DomainColors`）。
重算命令（禁止把数写死后不再验）：
```bash
git grep -c -E '\bMint(Background|Surface|Tint|Primary|PrimaryDark|Outline|TextMuted)\b' -- app/src/main/java/com/example/englishlearning/ui \
  | grep -v 'MintPalette.kt'   # 出现次数；再排除 import 行即得读点行数
```

据此采用 Material 自己的做法——把 `Mint*` 从 `val` 改成读 `CompositionLocal` 的 `@Composable @ReadOnlyComposable get()`：

```kotlin
// MintPalette.kt 目标形状（保持名字与包内可见性，组合期内的读点一行不改）
internal val MintBackground: Color
    @Composable @ReadOnlyComposable get() = LocalAppColors.current.background
```

组合期内的读点因为在组合期内，自动获得深色取值，零重写；组合期之外的 3 处（`DrawScope` lambda 两处 + 非组合 `Color` 表一处）按 Step 4 的优先级就地提读。`AppleMint*`（品牌渐变三端 + `mint-light`）**保持静态 `val`**：品牌渐变模式不变（spec §2.1「品牌渐变保持现状不动」），且三处顶层 `Brush` 需要组合期外的静态读取——若改成组合读取会让这三个顶层 val 编译失败。

`AppPalette` 与 `DomainColors` **保持静态浅色源**，另建深色兄弟（`AppColorDark`、`DomainPalette` 深套），由 `LocalAppColors`/`LocalDomainColors` 在装配点选择；组合期代码改读 `appColors().textSecondary` / `appDomain(AppDomain.AiSpeech).deep`。这一步无法一次做完，因此用 `DarkTokenConsumptionRatchetTest` 把**按屏的静态读点计数**做成只减不增的棘轮：批 1 建表并记 seed，批 2/批 3 每屏重排时顺手迁完自己那屏，收口时 seed 应清零或仅剩确实与主题无关的站点（逐条写明理由）。
**读点数不写在计划里**（批 1 终审 D-I-3 订正）：原稿这里的「91 处 / 71 处 / 共 162 处」是撰写时的估算，与批 1 出厂台账不符。权威数是 `DarkTokenSeedAllowlist`，可重算：
`sed -n '/internal val DarkTokenSeedAllowlist/,/^)/p' app/src/test/java/com/example/englishlearning/ui/DarkTokenConsumptionRatchetTest.kt | grep -oE '"[^"]+" to [0-9]+' | awk -F'to ' '{s+=$2; n++} END{print "files="n" hits="s}'`（批 1 tip 打印 `files=32 hits=157`）。清零的拆族口径见「批 3 深色清零专项」。

`LocalIsDark` 存在的理由：玻璃层、阴影、`inset` 高光等少数需要按模式分支的视觉细节（spec §5.4），用它而不是到处 `isSystemInDarkTheme()`。

---

## 批 1 · 设计系统底座（Task 1–12）

分支：`stage-5-ui-r1-design-system`（从 `main` 切出；`main` tip 以 `git rev-parse main` 现场为准，勿写死）。

批 1 验收：AC-R-01、AC-R-02（新站点为 0）、AC-R-03、AC-R-04、AC-R-05、AC-R-05b、AC-R-06。批 1 结束时代码里**不应有屏引用尚未存在的组件**——九个共享组件在批 1 建出并有组件测试，批 2/批 3 才逐屏接入。

---

### Task 1: 先行文档裁决（两处 spec 缺陷 + 图标两层豁免表）

**Files:**
- Modify: `docs/superpowers/specs/2026-10-17-ui-redesign-design.md`（§2.1 硬规则段、§9 AC-R-03、§9 AC-R-05b）
- Create: `docs/decisions/2026-10-18-ui-redesign-contrast-and-icon-layer-rulings.md`
- Modify: `docs/design/visual-style-guide.md`（§2.3 与 §12 同步一句：`text-3` 不作正文前景）

**Interfaces:**
- Consumes: 无（纯文档）。
- Produces: Task 2 的允许配对表、Task 5/6 的两层豁免表——两者都必须在决策文档里存在具体条目，后续契约测试据此钉值。

- [ ] **Step 1: 复算三组比值并留痕**

  用 §Global Constraints #7 的公式（可临时写 Kotlin 脚本或 Python，放仓库外临时目录，不入提交）复算：
  - `text-3 #7A9088` 压 `surface #FFFFFF` / `bg #F1FBF5` / `tint #DDF7E8` = **3.40 / 3.22 / 3.00**
  - `text-2 #4E756A` 压 `tint-2 #DCEAE1` = **4.15**、压 `tint-3 #CDEDD9` = **4.10**（压 `tint #DDF7E8` 为 4.55，合格）
  - `on-mint #174C44` 压品牌渐变末端 `#35B9B5` = **4.07**；压渐变中段 `#5DDFB4` = 5.89；压 `mint-light #D9FFF0` = 9.06
  - `on-brand #04241C` 压渐变末端 = **6.87**；压渐变起点 = 12.81

  Expected: 四个数字与上面一致（若不一致，以复算值为准并改正本计划与决策文档——不要沿用陈旧值）。

- [ ] **Step 2: 写决策文档**

  `docs/decisions/2026-10-18-ui-redesign-contrast-and-icon-layer-rulings.md` 含背景/候选/决定/影响四段，三条裁决逐条写：

  1. **`text-3` 降级为「非正文角色」**：仅用于 ≥24sp 大数字/装饰性大字与纯图标描边，**不得作任何正文或标签前景**（实测 3.00–3.40，达不到 AA 正文 4.5）。需要第三级弱化文字时用 `text-2` + 降字号，不用 `text-3`。候选方案「把 `text-3` 调深到通过 4.5」被否：调深后与 `text-2` 视觉上不再成三级。
  2. **`text-2` 的可用底限于 `surface`/`bg`/`tint`**（5.15 / 4.87 / 4.55），**禁压 `tint-2`/`tint-3`**（4.15 / ≈4.0）。`tint-2`/`tint-3` 上的正文一律 `text-1`。
  3. **品牌渐变末端上的前景改用 `on-brand #04241C`**：`on-mint` 在 `#35B9B5` 上只有 4.07；`mint` 渐变三端从浅到深，因此**同一渐变上的文字统一取 `on-brand`**（起点 12.81、末端 6.87，处处合格），`on-mint` 保留给 `tint`/`mint-light` 一类浅薄荷底。spec §2.1 的「≈4.6:1」原文口径只适用于浅薄荷底，本条把它在渐变上的取值改钉为 `on-brand`。
  4. **图标两层结构豁免表**：33 枚中 **5 枚只有描边骨架、不建填充层**——`Back`、`ChevronRight`、`ChevronDown`、`Close`、`Check`。理由是这些字形没有可闭合的内部区域，强行加填充层等于把描边变实心，正是 B 案要避免的「两族字形」。同时**这 5 枚不得进入 ≥40dp 入口容器**（它们只作行内/尾部指示）。AC-R-05b 的「每枚两层」据此改为「除豁免表外每枚两层」。

  并在同文档给出 **33 枚图标逐枚清单**（spec §10.5 留给批 1 第一个任务钉进 `AppIcons.kt` 顶部注释的那份），名字与顺序按 Task 6 Interfaces 的枚举清单。

- [ ] **Step 3: 同步 spec 与风格指南**

  - spec §2.1 硬规则段：把「前景一律 `on-mint #174C44`」那句限定为「浅薄荷底取 `on-mint`；品牌渐变上的文字取 `on-brand #04241C`」，并加一句「`text-3` 不作正文前景，`text-2` 不得压 `tint-2`/`tint-3`」，指向本决策文档。
  - spec §9 AC-R-03：加一句「允许配对与禁配由 `DesignTokenContractTest` 的表钉住」。
  - spec §9 AC-R-05b：「每枚图标为两层结构」改为「除决策文档豁免表内 5 枚外每枚两层；豁免表内 5 枚不得进入 ≥40dp 入口容器」。
  - `docs/design/visual-style-guide.md`：§2.3 与 §12 各补一行同口径。

- [ ] **Step 4: 校验文档自洽**

  ```bash
  grep -n "on-brand\|text-3\|豁免" docs/superpowers/specs/2026-10-17-ui-redesign-design.md docs/design/visual-style-guide.md
  ```
  Expected: 三处修改都命中，且没有留下与新裁决矛盾的旧句子（人工读一遍命中行）。

- [ ] **Step 5: 提交**

  ```bash
  git add docs/decisions/2026-10-18-ui-redesign-contrast-and-icon-layer-rulings.md \
          docs/superpowers/specs/2026-10-17-ui-redesign-design.md \
          docs/design/visual-style-guide.md
  git -c user.name=steveliuyan -c user.email=2021250388@qq.com commit \
    -m "docs(ui-redesign): 批1 开工前裁决 text-3/text-2/on-brand 对比口径与图标两层豁免表"
  ```

---

### Task 2: Token 校准与补齐 + `AppColors.kt` + `DesignTokenContractTest`

**Files:**
- Modify: `app/src/main/java/com/example/englishlearning/ui/theme/AppPalette.kt`
- Create: `app/src/main/java/com/example/englishlearning/ui/theme/AppColors.kt`
- Test: `app/src/test/java/com/example/englishlearning/ui/DesignTokenContractTest.kt`
- Test: `app/src/test/java/com/example/englishlearning/ui/SourceRatchet.kt`（本任务只需 `uiFiles`/`stripComments`/`countMatches` 三个夹具雏形，Task 6 再补 `violations`）

**Interfaces:**
- Consumes: Task 1 决策文档的允许配对表。
- Produces:
  - `data class AppColorSet(background, surface, surfaceGlass, tint, tint2, tint3, outline, outlineSoft, text1, text2, text3, onMint, onBrand, error, errorFill, success: Color)`（16 字段，字段名即 camelCase token 名）
  - `val AppColorLight: AppColorSet`、`val AppColorDark: AppColorSet`
  - `enum class AppDomain { AiSpeech, Learn, Review, Library, Reading, Danger, Settings }`
  - `data class DomainPalette(val base: Color, val deep: Color)`
  - `val DomainLight: Map<AppDomain, DomainPalette>`、`val DomainDark: Map<AppDomain, DomainPalette>`
  - `val LocalAppColors: ProvidableCompositionLocal<AppColorSet>`、`val LocalDomainColors: ProvidableCompositionLocal<Map<AppDomain, DomainPalette>>`、`val LocalIsDark: ProvidableCompositionLocal<Boolean>`（**必须**是 `ProvidableCompositionLocal`：`provides` 是它的成员而非 `CompositionLocal` 的，Task 4 的装配点要写 `LocalAppColors provides set`，静态类型收窄成 `CompositionLocal` 就编译不过。原写作 `CompositionLocal<T>` 是本计划的笔误，Task 2 修复轮 1 已按此订正）
  - `@Composable @ReadOnlyComposable fun appColors(): AppColorSet`、`@Composable @ReadOnlyComposable fun appDomain(domain: AppDomain): DomainPalette`

- [ ] **Step 1: 写失败的契约测试**

  `DesignTokenContractTest.kt` 沿用本仓库 `AccessibilityColorTokenContractTest` 的样板：纯函数解析源码文本 + 目录守卫 + 合成正/负片段。核心断言（节选，逐字段写全）：

  ```kotlin
  package com.example.englishlearning.ui

  import java.io.File
  import kotlin.math.pow
  import kotlin.test.Test
  import kotlin.test.assertTrue
  import kotlin.test.assertFailsWith

  /**
   * AC-R-01/AC-R-03：token 取值由源码 hex 字面量钉住，配对可用性由 WCAG 相对亮度当场计算。
   * 读源码而不是读渲染结果：本仓库 JVM 单测零 androidx.compose 导入（robolectric 依赖在但没用过），
   * Compose 渲染类断言归 androidTest。
   */
  class DesignTokenContractTest {
      private val themeDir = File(THEME_SOURCE_PATH)

      private fun hexOf(source: String, token: String): Int {
          val m = Regex(""""$token\s*=\s*Color\(\s*(0x[0-9A-Fa-f]{8})\s*\)"""")
              .find(source) ?: error("token 未以 val $token = Color(0x…) 形状声明：$token")
          return m.groupValues[1].toULong(16).toInt() or 0 // 取低位 0xRRGGBB 由调用方掩码
      }

      /** WCAG 相对亮度（Global Constraints #7 的公式，逐分量实现，注释写明口径）。 */
      private fun luminance(rgb: Int): Double {
          fun lin(c: Int): Double {
              val s = (c and 0xFF) / 255.0
              return if (s <= 0.04045) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
          }
          return 0.2126 * lin(rgb shr 16) + 0.7152 * lin(rgb shr 8) + 0.0722 * lin(rgb)
      }

      private fun ratio(fg: Int, bg: Int): Double {
          val hi = maxOf(luminance(fg), luminance(bg))
          val lo = minOf(luminance(fg), luminance(bg))
          return (hi + 0.05) / (lo + 0.05)
      }

      private fun assertAtLeast(label: String, fg: Int, bg: Int, floor: Double) {
          val r = ratio(fg, bg)
          assertTrue(r >= floor, "$label 实测 %.2f:1 低于 %.1f:1".format(r, floor))
      }
  ```

  必须出现的取值断言（浅套，逐字从 spec §2.1 抄）：
  `bg 0xFFF1FBF5`、`surface 0xFFFFFFFF`、`surfaceGlass 0xEFFFFFFF`、`tint 0xFFDDF7E8`、`tint2 0xFFDCEAE1`、`tint3 0xFFCDEDD9`、`outline 0xFFADE7D2`、`outlineSoft 0xFFE4EFE9`、`text1 0xFF1C1C1E`、`text2 0xFF4E756A`、`text3 0xFF7A9088`、`onMint 0xFF174C44`、`onBrand 0xFF04241C`、`error 0xFFB30000`、`errorFill 0xFFFF3B30`、`success 0xFF1B5E20`；
  深套：`bg 0xFF0E1512`、`surface 0xFF17201C`、`surfaceGlass 0xE617201C`、`tint 0xFF1C2C26`、`tint2 0xFF22322B`、`tint3 0xFF27392F`、`outline 0xFF2E4238`、`outlineSoft 0xFF233029`、`text1 0xFFE9F2EC`、`text2 0xFF9FB3AA`、`text3 0xFF7F938A`、`onMint 0xFFDFF6EC`、`error 0xFFFF8177`、`errorFill 0xFFFF6B61`、`success 0xFF6BD98A`；
  域色浅套与既有 `DomainColors` **逐值相同**（不得改动，改了就是回归）：AI `#2EC99C/#188F76`、学习 `#0A84FF/#075EB4`、复习 `#FF9F0A/#AB5700`、词库 `#BF5AF2/#8E34B8`、阅读 `#FF375F/#C81E46`、危险 `#FF3B30/#B30000`、设置 `#8E8E93/#48484A`；深套 `deep = base`：`#35E0AC`、`#5AC0FF`、`#FFB340`、`#DA8CFF`、`#FF7A93`、`#FF6B61`、`#B0B0B5`。

  必须出现的配对断言（用上面的 `assertAtLeast`，允许配对 floor=4.5 正文 / 3.0 图形）：
  ```kotlin
  // 正文族 ≥4.5
  assertAtLeast("text1/bg-light", 0xFF1C1C1E, 0xFFF1FBF5, 4.5)   // 16.09
  assertAtLeast("text1/tint", 0xFF1C1C1E, 0xFFDDF7E8, 4.5)       // 15.02
  assertAtLeast("text2/surface", 0xFF4E756A, 0xFFFFFFFF, 4.5)    // 5.15
  assertAtLeast("text2/bg", 0xFF4E756A, 0xFFF1FBF5, 4.5)         // 4.87
  assertAtLeast("text2/tint", 0xFF4E756A, 0xFFDDF7E8, 4.5)       // 4.55
  assertAtLeast("error/surface", 0xFFB30000, 0xFFFFFFFF, 4.5)    // 7.20
  assertAtLeast("success/surface", 0xFF1B5E20, 0xFFFFFFFF, 4.5)  // 7.87
  assertAtLeast("onMint/tint", 0xFF174C44, 0xFFDDF7E8, 4.5)
  assertAtLeast("onBrand/gradientEnd", 0xFF04241C, 0xFF35B9B5, 4.5) // 6.87
  // 深套
  assertAtLeast("dark text1/bg", 0xFFE9F2EC, 0xFF0E1512, 4.5)    // 16.20
  assertAtLeast("dark text2/surface", 0xFF9FB3AA, 0xFF17201C, 4.5)
  assertAtLeast("dark error/surface", 0xFFFF8177, 0xFF17201C, 4.5)
  assertAtLeast("dark onMint/tint", 0xFFDFF6EC, 0xFF1C2C26, 4.5) // 12.89
  ```
  禁配表（断言比值**低于**阈值，证明规则不是单向摆饰；每条注释写明「本禁配由 Task 1 裁决 #N 立」）：
  ```kotlin
  assertTrue(ratio(0xFF7A9088, 0xFFFFFFFF) < 4.5, "text-3 若已合格，裁决 #1 需重议")   // 3.40
  assertTrue(ratio(0xFF7A9088, 0xFFDDF7E8) < 4.5)                                     // 3.00
  assertTrue(ratio(0xFF4E756A, 0xFFDCEAE1) < 4.5, "text-2 压 tint-2 若已合格，裁决 #2 需重议") // 4.15
  assertTrue(ratio(0xFF174C44, 0xFF35B9B5) < 4.5, "on-mint 压渐变末端若已合格，裁决 #3 需重议") // 4.07
  ```
  另有目录守卫（照抄 `AccessibilityColorTokenContractTest` 的 `scanMainSources` 措辞与失败信息形状，目录换 `THEME_SOURCE_PATH`）、一个「缺失目录大声失败」用例、以及 `AppPalette.OnMintPrimary == 0xFF174C44` / `ErrorText == 0xFFB30000` / `SuccessText == 0xFF1B5E20` 三个既有钉值不变的断言。

  文件末尾 `private const val THEME_SOURCE_PATH = "src/main/java/com/example/englishlearning/ui/theme"`。

- [ ] **Step 2: 跑测试确认失败**

  Run: `"$GRADLE" :app:testDebugUnitTest --tests '*DesignTokenContractTest*' --offline --no-daemon --no-build-cache`
  Expected: FAIL —— 报「token 未以 val … 形状声明」（`AppColorSet` 文件还不存在，扫描零命中）。

- [ ] **Step 3: 写 `AppColors.kt`**

  按 Interfaces 的形状把浅/深两套 `AppColorSet` 与两套域色 Map 写成顶层 val，注释里点名「取值来自 spec §2.1 + 决策 2026-10-18 裁决」；`LocalAppColors`/`LocalDomainColors`/`LocalIsDark` 用 **`compositionLocalOf`**（不是 `staticCompositionLocalOf`）。订正理由（Task 3 实现现场发现，控制者裁决）：三态主题（跟随系统 / 浅色 / 深色）在设置页翻动时 `MaterialTheme` 装配不重建，`staticCompositionLocalOf` 的读取**不会失效重算**，所有 `Mint*` 组合期 getter 会停在旧值——「整树切换、不需要每次变更重建」正是把静默失效当成优化。`compositionLocalOf` 多一次记录查找，换来的是能用的深色。Task 4 必须连带把三个 Local 的声明与 `DesignTokenContractTest` 的形状断言一并改过来。读取器：

  ```kotlin
  @Composable @ReadOnlyComposable
  fun appColors(): AppColorSet = LocalAppColors.current

  @Composable @ReadOnlyComposable
  fun appDomain(domain: AppDomain): DomainPalette = LocalDomainColors.current.getValue(domain)
  ```

  `AppDomain` 七个条目名必须与既有 `DomainColors` 的属性名逐字一致（迁移时可以按名字对位，别改成 `AI`/`SPEECH` 之类）。

- [ ] **Step 4: 校准 `AppPalette.kt` 取值（保留 object 与字段名，只改值 + 补新 token）**

  改值：`Background 0xFFF5F5F7 → 0xFFF1FBF5`、`TextSecondary 0xFF6E6E73 → 0xFF4E756A`、`Separator 0xFFE5E5EA → 0xFFE4EFE9`、`GlassFill 0xCCFFFFFF → 0xEFFFFFFF`。
  不动：`Surface`、`TextPrimary`、`Scrim`、`OnMintPrimary`、`ErrorText`、`SuccessText`、`GlassHighlight`。
  新增字段（同一 object 内，注释写明「浅套静态源，深色由 AppColorDark/LocalAppColors 提供」）：`Tint`、`Tint2`、`Tint3`、`Outline`、`OutlineSoft`、`TextTertiary`、`OnBrand`、`ErrorFill`。
  `DomainColors`/`DomainAccent` **零改动**（浅套取值本就与 spec 一致）。

- [ ] **Step 5: 跑测试确认通过 + 全站构建不破**

  改 `AppPalette` 取值后 154 处引用仍在（只改值不改名），但 `TextSecondary` 变深会让部分浅底文字对比变好、不会破坏编译。
  Run: 门控命令（§Global Constraints #11）。Expected: BUILD SUCCESSFUL，`DesignTokenContractTest` 全绿，其余 1499 基线不减。

- [ ] **Step 6: 提交**

  ```bash
  git add app/src/main/java/com/example/englishlearning/ui/theme/AppColors.kt \
          app/src/main/java/com/example/englishlearning/ui/theme/AppPalette.kt \
          app/src/test/java/com/example/englishlearning/ui/DesignTokenContractTest.kt \
          app/src/test/java/com/example/englishlearning/ui/SourceRatchet.kt
  git -c user.name=steveliuyan -c user.email=2021250388@qq.com commit \
    -m "feat(ui-theme): 批1 深浅两套 token 落地并用 WCAG 计算钉住允许/禁止配对"
  ```

---

### Task 3: `Mint*` 改为组合期读取（深色生效的主切换）

**Files:**
- Modify: `app/src/main/java/com/example/englishlearning/ui/theme/MintPalette.kt`
- Create: `app/src/test/java/com/example/englishlearning/ui/MintTokenIsComposableContractTest.kt`

**Interfaces:**
- Consumes: Task 2 的 `LocalAppColors`、`appColors()`、`AppColorSet` 字段名。
- Produces: `MintPalette.kt` 内**七个**中性令牌改组合期 getter（名字与 `internal` 可见性不变）：`MintBackground`、`MintSurface`、`MintTint`、`MintPrimary`、`MintPrimaryDark`、`MintOutline`、`MintTextMuted`；四个品牌令牌 `AppleMintStart`/`AppleMintMiddle`/`AppleMintEnd`/`AppleMintLight` **保持静态**。

**已现场核实的映射表（`MintPalette.kt:13-25` 逐行读得，照此实现，别再猜）**：

| 既有令牌 | 现值来源 | 目标读取 |
|---|---|---|
| `MintBackground` | `AppPalette.Background` | `LocalAppColors.current.background` |
| `MintSurface` | `AppPalette.Surface` | `…surface` |
| `MintTint` | 裸 `Color(0xFFDDF7E8)` | `…tint`（取值本就与 spec §2.1 相同，改读即顺手去掉一处裸 hex） |
| `MintPrimary` | `DomainColors.AiSpeech.base` | `LocalDomainColors.current.getValue(AppDomain.AiSpeech).base` |
| `MintPrimaryDark` | `AppPalette.TextPrimary` | `…text1` |
| `MintOutline` | `AppPalette.Separator`（= 分隔线） | `…outlineSoft`（**不是** `outline`） |
| `MintTextMuted` | `AppPalette.TextSecondary` | `…text2` |

**命名陷阱（务必按此处置）**：`MintOutline` 名字像 spec 的 `outline #ADE7D2`，实际值是分隔线。若把它映射到 `outline`，全部既有分隔线会突然变成粗薄荷线。决定：`MintOutline → outlineSoft`，而 spec 的 `outline #ADE7D2` **不建 `Mint` 别名**，只由新组件（`IconBadge` 描边、卡片主描边）直接读 `appColors().outline`。


- [ ] **Step 1: 复核现状**

  ```bash
  grep -n 'internal val' app/src/main/java/com/example/englishlearning/ui/theme/MintPalette.kt
  ```
  Expected: 正好 11 行——七个 `Mint*` + 四个 `AppleMint*`，与上方映射表逐条对上。若不符（说明 `main` 又前进了），**以文件为准**补齐映射表并把差异写进报告，再往下走。

- [ ] **Step 2: 写契约测试（失败）**

  断言：七个中性 `Mint*` 的声明形状是 `internal val X: Color` + `@Composable @ReadOnlyComposable get() = LocalAppColors.current.<field>`（`MintPrimary` 例外，读 `LocalDomainColors`）；四个 `AppleMint*` 仍是 `= Color(0x…)` 静态。规则用正则，配合成片段正/负用例各一组（形状同 Task 2）。失败信息要点名具体成员名。

- [ ] **Step 3: 改写 `MintPalette.kt`**

  逐条按上方映射表落地。注释里写明两件事：① 全站 `Mint*` 读点全在组合期内，因此这一步不改任何调用文件；② `AppleMint*` 保持静态的原因（`AiFeatureScreen.kt:47`、`AiLearningScreen.kt:56`、`AppScreen.kt:100` 三处顶层 `Brush.linearGradient` 需要组合期外读取 + 品牌渐变模式不变）。

- [ ] **Step 4: 编译并跑全部门控**

  Run: 门控命令。Expected: BUILD SUCCESSFUL。
  **若出现「不是组合期」的调用点报错**——已实测的预期站点有三类：`Canvas` 的 `DrawScope` lambda（`Modifier.drawBehind` 同形）、`graphicsLayer`/`Painter` 一类 lambda、以及返回 `Color` 的非组合辅助函数（枚举色表之类）；另外留意顶层 `val`/默认参数/`object` 属性初始化。停下并在报告里逐个列出报错站点；处理办法只有两种，按优先级：① 把该调用点移进 `@Composable`（`DrawScope` 站点 = 在组合期读进局部 `val`，lambda 只捕获该 `val`）；② 若它本质是常量表（`GlassButton.kt:47` 的默认参数 `accent: DomainAccent = DomainColors.AiSpeech` 读的是**保持静态的** `DomainColors`，预期不报错；真报错才处理），改用无默认值 + 组合期实参传入。**不得**为了让它编译而把令牌退回静态 val——那会让深色失效。

- [ ] **Step 5: 提交**

  ```bash
  git add app/src/main/java/com/example/englishlearning/ui/theme/MintPalette.kt \
          app/src/test/java/com/example/englishlearning/ui/MintTokenIsComposableContractTest.kt
  git -c user.name=steveliuyan -c user.email=2021250388@qq.com commit \
    -m "refactor(ui-theme): Mint 中性令牌改读 CompositionLocal，组合期读点零重写获得深色能力"
  ```

  落地说明（Task 3 后补）：已提交的 `c64df0a` 沿用了本行原文的「817 处调用点」——那是计划撰写时的估算，实测口径为 808 出现 / 611 读点行 / 31 文件（改后 803 / 606 / 31）。提交信息不改写历史（禁 `--amend`），收口文档 `docs/verification/ui-redesign-2026-10.md` 与交接报告一律以实测口径为准。

---

### Task 4: 唯一 `MaterialTheme` 装配点 + `MainActivity` 接线

**Files:**
- Create: `app/src/main/java/com/example/englishlearning/ui/theme/MintGlassTheme.kt`
- Create: `app/src/main/java/com/example/englishlearning/ui/theme/AppThemePreference.kt`（**本任务只交付纯码部分**：`enum ThemeMode` + `shouldUseDarkTheme` + `themeModeFromStorage`，不碰 `Context`； prefs 读写与 `rememberAppThemeMode` 由 Task 5 追加到同一文件）
- Modify: `app/src/main/java/com/example/englishlearning/ui/theme/AppType.kt`（只加一个把六档语义装成 `Typography` 的函数，钉值不动）
- Modify: `app/src/main/java/com/example/englishlearning/MainActivity.kt`（`setContent { AppScreen(...) }` 外面包一层 `MintGlassTheme`）
- Test: `app/src/test/java/com/example/englishlearning/ui/MaterialThemeAssemblyContractTest.kt`
- Test: `app/src/test/java/com/example/englishlearning/ui/ThemeModeDecisionTest.kt`（纯函数三态判定，本任务交付；Task 5 复用不重建）

**Interfaces:**
- Consumes: Task 2 的 `AppColorSet`/`LocalAppColors`/`LocalDomainColors`/`LocalIsDark`/两套域色 Map；Task 3 的组合期 `Mint*`；既有 `AppType`（六档钉值已符合 spec §2.2）、`AppShape`（`Dialog 24/Card 20/Button 14/Pill 50%`）、`MintColors`（既有 M3 `lightColorScheme` 装配源）。
- Produces:
  - `enum class ThemeMode(val storageKey: String) { System("system"), Light("light"), Dark("dark") }` 与纯函数 `fun shouldUseDarkTheme(mode: ThemeMode, systemInDark: Boolean): Boolean`、`fun themeModeFromStorage(raw: String?): ThemeMode`（**本任务交付这三个**；Task 5 的 prefs I/O 依赖它们）
  - `@Composable fun MintGlassTheme(themeMode: ThemeMode = ThemeMode.System, content: @Composable () -> Unit)`（Task 4 起被 `MainActivity` 使用；内部据 `shouldUseDarkTheme(themeMode, isSystemInDarkTheme())` 选 set 并 `CompositionLocalProvider`）
  - `@Composable fun appTypography(): Typography`
  - `@Composable fun appColorScheme(dark: Boolean): ColorScheme`
  - `val AppShapes`（若 `AppShape` 尚未导出 `Shapes`，在此导出：`Dialog→large`、`Card→medium`、`Button→small`、额外 `extraSmall = CornerShape(icon 12)`）
  - `LocalAppColors` 等的**唯一**提供者

- [ ] **Step 1: 写契约测试（失败）**

  `MaterialThemeAssemblyContractTest` 断言三件事（全用源码正则，禁渲染）：
  1. `main` 源码里 `MaterialTheme(` 的**调用**只出现在 `MintGlassTheme.kt` 一处：
     ```kotlin
     val sites = uiFiles(mainDir).flatMap { f ->
         stripComments(f.readText()).let { t ->
             Regex("""MaterialTheme\(""").findAll(t).map { f.name }.toList()
         }
     }
     assertTrue(sites == listOf("MintGlassTheme.kt"), "MaterialTheme( 装配点应唯一，实际：$sites")
     ```
  2. `MainActivity.kt` 里 `setContent {` 后第一个组合调用是 `MintGlassTheme(`：
     ```kotlin
     val body = stripComments(File("src/main/java/com/example/englishlearning/MainActivity.kt").readText())
     assertTrue(Regex("""setContent\s*\{\s*MintGlassTheme\(""").containsMatchIn(body),
         "MainActivity 内容根必须是 MintGlassTheme，否则 CompositionLocal 无提供者，Mint* 会取到默认浅套")
     ```
  3. `appTypography()` 的六档钉值与 spec §2.2 逐字一致（正则读 `AppType.kt` 里的 `34.sp`/`24.sp`/`17.sp`/`15.sp`/`13.sp`/`12.sp` 与 `FontWeight.Bold/SemiBold/Regular/Medium` + `ls 0.4/0.3`）。

  再加守卫用例：`mainDir` 缺失或零 `.kt` 立即失败（照 Task 2 的 `scanMainSources` 形状）。

- [ ] **Step 2: 跑测试确认失败**

  Run: `"$GRADLE" :app:testDebugUnitTest --tests '*MaterialThemeAssemblyContractTest*' --offline --no-daemon --no-build-cache`
  Expected: FAIL（`MintGlassTheme.kt` 不存在 → 断言 1/2 失败）。

- [ ] **Step 3: 写 `MintGlassTheme.kt`**

  ```kotlin
  /**
   * 全站唯一的 MaterialTheme 装配点。
   *
   * 存在理由：此前仓库没有 MaterialTheme(...) 调用（实测 0 处），206 处
   * MaterialTheme.typography.* 与 16 处 colorScheme 直接吃 M3 默认值，深色模式没有落点。
   * 本文件把 Typography / Colors / Shapes 一次装配，并作为 LocalAppColors /
   * LocalDomainColors / LocalIsDark 的唯一提供者——Mint* 的组合期 getter 依赖这里的 Local。
   */
  @Composable
  fun MintGlassTheme(
      themeMode: ThemeMode = ThemeMode.System,
      content: @Composable () -> Unit,
  ) {
      val dark = shouldUseDarkTheme(themeMode, isSystemInDarkTheme())
      val set = if (dark) AppColorDark else AppColorLight
      val domains = if (dark) DomainDark else DomainLight
      CompositionLocalProvider(
          LocalAppColors provides set,
          LocalDomainColors provides domains,
          LocalIsDark provides dark,
      ) {
          MaterialTheme(
              colorScheme = appColorScheme(dark),
              typography = appTypography(),
              shapes = AppShapes,
              content = content,
          )
      }
  }
  ```

  `appColorScheme(dark)` 用 `lightColorScheme(...)`/`darkColorScheme(...)`，参数**全部取自 `AppColorSet`**（不得再出现裸 hex；`primaryContainer` 取 `tint`、`onPrimaryContainer` 取 `onMint`、`error` 取 `error`、`background`/`surface`/`onBackground`/`onSurface` 按名对应，`outlineVariant` 取 `outlineSoft`）。`appTypography()` 把 `AppType` 六档映射到 M3 角色（Display→`displaySmall`、Headline→`headlineSmall`、Title→`titleMedium`、Body→`bodyMedium`、Footnote→`bodySmall`、Label→`labelMedium`），其余 M3 角色从这六档派生（不引新字号）。
  **`colorScheme.outline` 取 `text2` 而非 `AppColorSet.outline`（Task 4 评审裁决，覆盖本计划原文）**：M3 的 `outline` 槽不是装饰细线，是**控件边界**（`OutlinedButton` 描边、`Switch` 未选中态描边…），受 WCAG 1.4.11 非文本 3:1 约束。`AppColorSet.outline`（浅 `#ADE7D2`、深 `#2E4238`）压各自 `surface` 只有 **1.39 / 1.55**，进 M3 槽会把 `main` 里全部 `OutlinedButton` 的描边打到 1.39:1——而 Task 4 之前它们吃 material3 库默认约 4.1:1，属本任务引入的回归（Task 4 评审期实测调用点 13 处，分布在 `BackupRestoreScreen` / `CardDetailScreen` / `PhotoTranslationScreen` / `WordCardScreen`，批 2/批 3 逐屏改版时换成 spec 组件）。`text2` 浅套压 surface/bg/tint/tint2/tint3 = 5.15/4.87/4.55/4.15/**4.10**、深套 = 7.54/8.37/6.61/6.09/**5.55**，全部 ≥3.0；曾考虑的 `text3` 压 tint2 2.74、压 tint3 2.71，否决（`surfaceVariant` 就是 tint2）。`AppColorSet.outline` 继续作装饰性细线，只由新组件直读。`main` 里 `colorScheme.outline`/`.outlineVariant` 零直读点，换槽不牵动既有代码。契约由 `MaterialThemeAssemblyContractTest` 的 `boundaryRoles` 用例压住：它读**生产 `appColorScheme` 实参**按亮度当场复算（下限 3.0），把 `set.outline` 塞回 M3 槽必红。
  `AppShapes` 导出时一并做 **Card 20dp → 18dp 校准**（spec §2.3「卡 18dp」；`Dialog 24`/`Button 14`/`Pill 50%` 与 spec 相符，不动）。取值一致性优先于「既有值不动」，但改这一档会让全部卡片圆角变化，属可见改动，须在提交信息点名并留给真机核验（Task 26 Step 4）。

- [ ] **Step 4: 写 `AppThemePreference.kt` 的纯码部分 + `ThemeModeDecisionTest`**

  本步骤先于装配点接线落地下列三个纯符号（无 `Context`，可在 JVM 测）：
  ```kotlin
  enum class ThemeMode(val storageKey: String) { System("system"), Light("light"), Dark("dark") }

  fun shouldUseDarkTheme(mode: ThemeMode, systemInDark: Boolean): Boolean = when (mode) {
      ThemeMode.System -> systemInDark
      ThemeMode.Light -> false
      ThemeMode.Dark -> true
  }

  /** 存储键写死、大小写敏感：非法/空/未知值一律回退 System，避免「两种写法都合法」的漂移。 */
  fun themeModeFromStorage(raw: String?): ThemeMode =
      ThemeMode.entries.firstOrNull { it.storageKey == raw } ?: ThemeMode.System
  ```
  同任务建 `ThemeModeDecisionTest`（JUnit5 + kotlin.test，四条用例）：
  ```kotlin
  @Test fun `system mode follows the platform`() {
      assertTrue(shouldUseDarkTheme(ThemeMode.System, systemInDark = true))
      assertFalse(shouldUseDarkTheme(ThemeMode.System, systemInDark = false))
  }
  @Test fun `explicit light and dark ignore the platform`() {
      assertFalse(shouldUseDarkTheme(ThemeMode.Light, systemInDark = true))
      assertTrue(shouldUseDarkTheme(ThemeMode.Dark, systemInDark = false))
  }
  @Test fun `storage round-trips every mode`() {
      ThemeMode.entries.forEach { mode -> assertEquals(mode, themeModeFromStorage(mode.storageKey)) }
  }
  @Test fun `blank, null and unknown storage values fall back to system`() {
      listOf(null, "", "darkish", "LIGHT", "  ").forEach { raw ->
          assertEquals(ThemeMode.System, themeModeFromStorage(raw), "raw=$raw 应回退 System")
      }
  }
  ```

- [ ] **Step 5: 接 `MainActivity`**

  `setContent {` 内包一层：
  ```kotlin
  setContent {
      val themeMode = rememberAppThemeMode()   // Task 5 提供；本任务先用 ThemeMode.System 常量占位，见下
      MintGlassTheme(themeMode = ThemeMode.System) { AppScreen(/* 26 个 hiltViewModel 实参原样保留 */) }
  }
  ```
  **本任务只接装配点、不接偏好读写**（Task 5 换成 `rememberAppThemeMode()`）。改这一行时 `AppScreen` 的 26 个实参顺序与表达式一字不动——`AppScreenProductionWiringTest` 强制每个可选参数都在 `MainActivity` 接线，漏一个即挂测试（预期门，非环境故障）。

- [ ] **Step 6: 跑测试确认通过 + 门控**

  Expected: `MaterialThemeAssemblyContractTest` 全绿；门控全绿；基线 1499 + 新增用例，0 失败。
  额外人工检查：`"$GRADLE" :app:assembleDebug` 成功后 APK 内主题不崩——本任务无渲染测试，把「装配点下首个组合可能因 Local 无提供者而崩」列为批 1 收口 connected 走查项（Task 10）。

- [ ] **Step 7: 提交**

  ```bash
  git add app/src/main/java/com/example/englishlearning/ui/theme/MintGlassTheme.kt \
          app/src/main/java/com/example/englishlearning/ui/theme/AppThemePreference.kt \
          app/src/main/java/com/example/englishlearning/ui/theme/AppType.kt \
          app/src/main/java/com/example/englishlearning/MainActivity.kt \
          app/src/test/java/com/example/englishlearning/ui/MaterialThemeAssemblyContractTest.kt \
          app/src/test/java/com/example/englishlearning/ui/ThemeModeDecisionTest.kt
  git -c user.name=steveliuyan -c user.email=2021250388@qq.com commit \
    -m "feat(ui-theme): 建立唯一 MaterialTheme 装配点并接入 MainActivity 内容根"
  ```

---

### Task 5: 主题三态偏好（跟随系统 / 浅色 / 深色）

**Files:**
- Modify: `app/src/main/java/com/example/englishlearning/ui/theme/AppThemePreference.kt`（Task 4 已建纯码部分；本任务在同一文件追加 prefs I/O 与 `rememberAppThemeMode`）
- Modify: `app/src/main/java/com/example/englishlearning/MainActivity.kt`（换成 `rememberAppThemeMode()`）
- Test: `app/src/test/java/com/example/englishlearning/ui/ThemeModeDecisionTest.kt`（**Task 4 已交付并跑绿**，本任务只复跑，不重建）
- Test（androidTest）: `app/src/androidTest/java/com/example/englishlearning/ui/ThemeModeSwitchTest.kt`

**Interfaces:**
- Consumes: Task 4 的 `MintGlassTheme(themeMode = …)`、`ThemeMode`、`shouldUseDarkTheme`、`themeModeFromStorage`。
- Produces:
  - `object AppThemePreferences { const val PREFS_NAME = "ui_display_prefs"; const val KEY_THEME_MODE = "theme_mode"; fun read(context: Context): ThemeMode; fun write(context: Context, mode: ThemeMode) }`
  - `@Composable fun rememberAppThemeMode(): ThemeMode`（`ContextCompat` + `DisposableEffect` 的 `OnSharedPreferenceChangeListener`）

- [ ] **Step 1: 复跑纯函数单测确认绿**

  Run: `"$GRADLE" :app:testDebugUnitTest --tests '*ThemeModeDecisionTest*' --offline --no-daemon --no-build-cache`
  Expected: PASS（Task 4 交付的四条用例：`System` 跟随系统、`Light`/`Dark` 无视平台、`storageKey` 全枚举往返、`null`/空/`"darkish"`/大小写不符的 `"LIGHT"`/`"  "` 一律回退 `System`——**大小写敏感是有意为之**，存储键写死，不做归一化以免「两种写法都合法」的漂移）。

- [ ] **Step 2: 追加 prefs I/O 与 `rememberAppThemeMode()`**

  在 `AppThemePreference.kt` 内实现 `object AppThemePreferences { PREFS_NAME/KEY_THEME_MODE + read/write }` 与 `@Composable fun rememberAppThemeMode()`（`LocalContext.current` → `AppThemePreferences.read`，`DisposableEffect` 注册/注销 `OnSharedPreferenceChangeListener`，`mutableStateOf` 承载）。prefs 读写本身需要 `Context`，**不在 JVM 单测覆盖**（本仓库 JVM 测试零 Android 依赖，robolectric 依赖虽在但从未用过，不为一个 prefs 开新口径）；读写由 Step 3 的 connected 用例覆盖。

  类注释写明两条口径：
  - **不进 Room、不做 v33 迁移、不进备份包**（spec §5.2）：显示偏好不是学习数据；因此不用 `LearningSettingsEntity`，也**不得**把 `ui_display_prefs` 加进任何备份文件清单。
  - prefs 文件是 app 私有，卸载即失——与 AGENTS.md「卸载/清除数据前未导出数据可能无法恢复」一致，主题偏好属可丢项。

- [ ] **Step 3: 写 connected 用例（像素证据）**

  `ThemeModeSwitchTest`（androidTest，`createComposeRule`）：同一测试类两个方法（**同一次测试内两次 `setContent` 真机必崩**——这是已记录怪癖，必须拆方法）：
  ```kotlin
  @Test fun dark_mode_root_paints_the_dark_background() {
      activityRule.activity.setContent { MintGlassTheme(themeMode = ThemeMode.Dark) {
          Box(Modifier.fillMaxSize().testTag("theme_probe"))
      } }
      val px = onNodeWithTag("theme_probe").captureToImage().toBitmap().let {
          it.getPixel(it.width / 2, it.height / 2) and 0xFFFFFF
      }
      assertEquals(0x0E1512, px, "深色装配下根底应取 AppColorDark.background")
  }
  ```
  另一方法同形断言 `0xF1FBF5`（`ThemeMode.Light`）。
  注：`Box` 底色由 `MaterialTheme.colorScheme.background` 提供还是需显式 `.background(appColors().background)`——以 Step 4 现场为准；若 `setContent` 根不带背景，就显式加 `.background(LocalAppColors.current.background)` 并在注释说明这是探针而非生产形状。

- [ ] **Step 4: `MainActivity` 接 `rememberAppThemeMode()`**，跑门控 + 编译 androidTest。

- [ ] **Step 5: 在 `emulator-5574` 跑 connected**

  ```bash
  export ANDROID_SERIAL=emulator-5574
  "$GRADLE" :app:assembleDebug :app:assembleDebugAndroidTest --offline --no-daemon --no-build-cache
  adb install -r app/build/outputs/apk/debug/app-debug.apk
  adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
  adb shell am instrument -w -e class com.example.englishlearning.ui.ThemeModeSwitchTest \
    com.example.englishlearning.test/androidx.test.runner.AndroidJUnitRunner
  ```
  Expected: 末行 `OK (2 tests)`。失败先看 logcat，再对照 `android-connected-qa-loop` 怪癖表（息屏/弹窗劫持/dump 过期）。

- [ ] **Step 6: 提交**

  ```bash
  git add app/src/main/java/com/example/englishlearning/ui/theme/AppThemePreference.kt \
          app/src/main/java/com/example/englishlearning/MainActivity.kt \
          app/src/androidTest/java/com/example/englishlearning/ui/ThemeModeSwitchTest.kt
  git -c user.name=steveliuyan -c user.email=2021250388@qq.com commit \
    -m "feat(ui-theme): 主题三态偏好落 SharedPreferences 并用真像素验证深浅装配"
  ```

---

### Task 6: `AppIcons.kt` — 33 枚 B 案图标的两层路径数据

**Files:**
- Create: `app/src/main/java/com/example/englishlearning/ui/components/AppIcons.kt`
- Test: `app/src/test/java/com/example/englishlearning/ui/AppIconsStructureContractTest.kt`

**Interfaces:**
- Consumes: Task 1 决策文档的 33 枚清单与 5 枚两层豁免表。
- Produces:
  - `data class AppIconSpec(val name: String, val strokePaths: List<String>, val fillPaths: List<String>)`（路径为 24×24 画布的 SVG path data 字符串；圆形填充用 `CircleFill(cx, cy, r)` 标记串，由 Task 7 的渲染器解释）
  - `object DuotoneFillAlpha { const val Light = 0.42f; const val Dark = 0.30f; fun forTheme(isDark: Boolean): Float }`（执行期修正：计划原文把 `fillAlpha: Float = 0.30f` 挂在 `AppIconSpec` 上，既无出处又拿深色档当两档通用值。透明度是**主题属性**而非字形属性——逐枚写一份就是第二套真值——故从字形数据里移出，两个取值以决策文档为唯一权威：`docs/decisions/2026-10-18-ui-redesign-icon-duotone.md`）
  - `enum class AppIcon { Back, ChevronRight, ChevronDown, Close, Check, Search, Book, BookOpen, List, Bookmark, Star, Clock, Chart, Spark, Speaker, Play, Pause, Stop, Mic, Translate, Camera, Image, Document, Newspaper, Gear, Person, Info, Bell, Shield, Warning, Trash, Upload, Download }`
  - `val AppIcon.spec: AppIconSpec`（`AppIcons.all: Map<AppIcon, AppIconSpec>` 的读取扩展）
  - `val StrokeOnlyIcons: Set<AppIcon>` = `setOf(Back, ChevronRight, ChevronDown, Close, Check)`

  **枚数核对**：枚举 33 条目 = 33 枚字形，符合 spec §4.1「约 30 枚上下」。`Book`（合上的书，底导「学习」tab 的替代品；造型系本轮自绘，只借现状 tab 的合书意象）与 `BookOpen`（摊开的书，阅读/词书页头）是两枚不同字形，都保留。若执行时认为其中一枚冗余，删枚举条目属范围变化，先改 Task 1 决策文档的清单再删，不得静默改数。

- [ ] **Step 1: 写结构契约测试（失败）**

  `AppIconsStructureContractTest` 用源码文本断言（形状同 Task 2/4，含目录守卫）：
  ```kotlin
  private val expectedIcons = listOf(
      "Back", "ChevronRight", "ChevronDown", "Close", "Check", "Search", "Book", "BookOpen",
      "List", "Bookmark", "Star", "Clock", "Chart", "Spark", "Speaker", "Play", "Pause", "Stop",
      "Mic", "Translate", "Camera", "Image", "Document", "Newspaper", "Gear", "Person", "Info",
      "Bell", "Shield", "Warning", "Trash", "Upload", "Download",
  )
  ```
  断言六条（计划原文写「四条」实列五条，控制者裁决追加第 6 条）：
  1. `enum class AppIcon { … }` 条目集合与 `expectedIcons` **完全等序**（解析枚举体，逗号切分后比对；不等时报出差异项）。
  2. 除 `StrokeOnlyIcons` 五枚外，每枚的 `fillPaths = listOf(...)` 至少一条路径；五枚豁免必须 `fillPaths = emptyList()`（双向都测：豁免表内若有填充层也算失败，防止「两层」变成含糊口径）。
  3. 每枚 `strokePaths` 至少一条；每条路径以 `M`/`m` 开头且只含允许字符集 `[MmLlHhVvCcSsQqTtAaZz0-9eE.,\s-]`（防把 `CircleFill` 塞进 stroke）。
  4. 全站 `Icons.` 引用为 0：
     ```kotlin
     val hits = uiFiles(mainDir).flatMap { f ->
         Regex("""\bIcons\.""").findAll(stripComments(f.readText())).map { f.name }.toList()
     }
     assertTrue(hits.isEmpty(), "禁止 AndroidX material-icons（会牵动 lockfile）：${hits.distinct()}")
     ```
  5. `AppIcons.kt` 文件头注释含「逐枚清单」小节，且注释里枚到的名字与枚举一致（用注释文本 `contains` 逐个查）。这条钉的是 spec §10.5 要求的「逐枚清单钉进 `AppIcons.kt` 顶部注释」。
  6. `fillPaths` 每条必须**要么**以 `M`/`m` 开头并符合同样的描边字符集，**要么**匹配 `CircleFill(<数>, <数>, <数>)` 标记形状。裁决理由：不钉这条，「往填充层塞任意字符串」也能通过，第 3 条防的那类漂移只是换个口袋重长。另注：`CircleFill(...)` 是本仓库自定标记（视觉稿里对应的是 `<circle cx cy r>`，稿中不存在该串），文件头注释须写明它由 Task 7 的 `AppGlyph` 渲染器解释，圆形填充不走 SVG path 语法。

- [ ] **Step 2: 跑测试确认失败** — Expected: 断言失败而非编译失败。源码文本契约不引用 `AppIcon` 符号，`AppIcons.kt` 尚不存在时第 1/5 条会以「应恰好一个，实际 0 个」一类信息红掉，这即是 RED 证据（计划原文写「编译失败（`AppIcon` 未定义）」是把文本契约误当成符号引用测试，现按实际形状记录）。

- [ ] **Step 3: 转录路径数据**

  来源：`docs/design/2026-10-17-ui-redesign-mockups.html` §6.2 已定稿的 B 案字形（**已入库的自绘路径，本任务是转录不是创作**）。已确认可用的填充层与骨架层片段（照抄，勿改写坐标）：

  填充层：
  - `Book`：`M5 6h5.2v10.6H5z`（左页半幅）
  - `Search`：`CircleFill(11, 11, 6)`
  - `Speaker`：`M5 10v4h3l4 3V7L8 10z`
  - `Spark`：`M11 3.5l1.8 4.5 4.5 1.8-4.5 1.8L11 16.1l-1.8-4.5L4.7 9.8l4.5-1.8z`
  - `Star`：`M6.4 4h11.2v15.2L12 15.6l-5.6 3.6z`
  - `Clock`：`CircleFill(12, 12, 8)`
  - `Shield`：`M12 4l7 3v5c0 4.2-3 7.3-7 8.2-4-.9-7-4-7-8.2V7z`
  - `Trash`：`M7.6 8h8.8l-.9 11.2H8.5z`

  描边骨架层：
  - `Back`：`M15 5l-7 7 7 7`
  - `Upload`：`M12 20V9` + `M8 13l4-4 4 4` + `M5 5h14`
  - `Download`：`M12 4v9` + `M8 10l4 4 4-4` + `M19 19h-14`（写作 `M5 19h14`）
  - `Bell`：`M5 12a7 7 0 0114 0` + `M9 15h6`
  - `Chart`：`M4 18l5-6 4 3 6-8` + `M4 6v12h16`
  - `BookOpen`：`M4 5h7a2 2 0 012 2v12a3 3 0 00-3-2H4z` + `M20 5h-4a2 2 0 00-2 2v12a3 3 0 013-2h3z`
  - `Trash`：`M5 7h14` + `M9 7V5h6v2` + `M7 7l1 12h8l1-12`
  - `Bookmark`：`M6 4h12v16l-6-3-6 3z`
  - `Camera`：`M4 8h4l2-2h4l2 2h4v11H4z`
  - `Warning`：`M12 4l8 14H4z` + `M12 9v4` + `M12 15.5v.5`
  - `Speaker`：`M5 10v4h3l4 3V7L8 10z` + 两道波弧

  稿中未逐条转录到的其余字形（`ChevronRight`/`ChevronDown`/`Close`/`Check`/`List`/`Play`/`Pause`/`Stop`/`Mic`/`Translate`/`Image`/`Document`/`Newspaper`/`Gear`/`Person`/`Info`/`Clock`/`Book`/`Star`/`Spark`/`Shield`/`Trash` 的骨架层等），按下列度量**自绘补齐**，不得引第三方路径：
  - 画布 24×24，视觉描边重量 2px，圆头（渲染器统一 `StrokeCap.Round`/`StrokeJoin.Round`），优先无填充（填充只作层次暗示）；
  - 关键几何留在 4–20 的安全区内（与批 1 时 `AppBottomBar.kt` 旧自绘字形 `drawTabGlyph` 的同一 24 网格习惯，便于对照；该函数已在 Task 17A 随底导 duotone 改造删除，本句为历史口径参照，见本计划 :1739）；
  - 方向类字形（chevron/back/check/close）笔画端点对称，17dp 行内尺寸下仍可辨；
  - 每枚补完在注释里标一行「原创自绘（B 案度量）」——许可台账 `app-authored-ui-icons` 的措辞依赖它（AC-R-05）。

  文件头注释顺序写：许可结论 → 度量基准（仅参考 Material Symbols 的 24dp 网格与 2px 重量，未内嵌其路径数据）→ 逐枚清单（33 枚 + 各自用途屏）→ 豁免表 5 枚。

- [ ] **Step 4: 跑测试确认通过** — Run `--tests '*AppIconsStructureContractTest*'`，Expected: PASS。

- [ ] **Step 5: 提交**

  ```bash
  git add app/src/main/java/com/example/englishlearning/ui/components/AppIcons.kt \
          app/src/test/java/com/example/englishlearning/ui/AppIconsStructureContractTest.kt
  git -c user.name=steveliuyan -c user.email=2021250388@qq.com commit \
    -m "feat(ui-icons): 33 枚 B 案双色图标路径数据入库并钉住两层结构与零 Icons 引用"
  ```

---

### Task 7: `AppIcon.kt` 运行时组装 + `IconBadge.kt` 两种呈现

**Files:**
- Create: `app/src/main/java/com/example/englishlearning/ui/components/AppIcon.kt`
- Create: `app/src/main/java/com/example/englishlearning/ui/components/IconBadge.kt`
- Test: `app/src/test/java/com/example/englishlearning/ui/IconBadgeContrastContractTest.kt`

**Interfaces:**
- Consumes: Task 6 的 `AppIcon`/`AppIconSpec`/`StrokeOnlyIcons`；Task 2 的 `appDomain(AppDomain)`、`appColors()`。
- Produces:
  - `@Composable fun appImageVector(icon: AppIcon, tint: Color, strokeScale: Float = 1f): ImageVector`（`remember(icon, tint, strokeScale)` 缓存；`vector { group { path(pathData = …, fill/stroke) } }`；`CircleFill(...)` 串解释为 `addPath(...)` 圆形或 `path("M…a…")`）
  - `@Composable fun AppGlyph(icon: AppIcon, tint: Color, size: Dp, modifier: Modifier = Modifier)`（`Image` 渲染，`contentDescription = null`——装饰性由调用方语义承载）
  - `@Composable fun EntryIconBadge(icon: AppIcon, domain: AppDomain, modifier: Modifier = Modifier)`：40dp 容器，渐变 `linearGradient(listOf(mix(deep, base, 0.35f), deep))`，字形墨色按主题取（浅色白、深色 `AppColorDark.onBrand`——白压深套 `deep` 实测 1.69–2.79 不合格，且视觉稿深色档本就给的是 `rgba(6,35,26,…)` 深墨），填充层不透明度取 `DuotoneFillAlpha.forTheme(isDark)`，`inset 0 0 0 1px rgba(255,255,255,.26)`（稿深 `.20`）用 `BorderStroke(1.dp, AppPalette.GlassHighlight)`（α≈0.349）**按 token 近似的描边**——计划原文写「等价实现」是过度声称，控制者裁决改为近似并须在代码注释里写出 α 差与"稿分两档、实现单档"，圆角 `AppShape.Icon`（12dp）——控制者裁决：计划原文写的小写 `AppShape.icon` 是笔误，`AppShape` 里没有这一枚，本任务在 `AppShape` 补 `val Icon: Shape = RoundedCornerShape(12.dp)` 并用它（12dp 值来自视觉稿 §4 栅格的图标容器圆角，与 `AppShapes.extraSmall` 同值但语义不同，故不复用）。
  - `@Composable fun InlineIconBadge(icon: AppIcon, domain: AppDomain, modifier: Modifier = Modifier)`：30dp 容器 `tint` 底 + 17dp 单层域色 `deep` 描边字形（填充层不显示），圆角用 `AppShape.Inline`（9dp）——控制者裁决（Task 7 修复轮）：视觉稿 §4 行内档 `.ix-sm .ixc` 明确写 `border-radius:9px`（mockups.html:242），与入口档 12px 是两档真实设计值，不得合并为一档；`AppShape` 须同时有 `Icon = 12.dp` 与 `Inline = 9.dp`，两枚数值都要被契约测试从 `AppShape.kt` 源码钉住（新增形状档必须进钉值表，否则等于没落地）。
  - `fun gradientStartForDomain(deep: Color, base: Color): Color = mix(deep, base, 0.35f)`（纯函数，颜色数学可直接单测）

- [ ] **Step 1: 写对比契约测试（失败）**

  `IconBadgeContrastContractTest` 断三组数（用 Task 2 的 `luminance`/`ratio`；把颜色数学与源码正则都放本测试，禁渲染）：

  ```kotlin
  /** B 案容器规则：渐变亮端 = deep 混 35% base；白字形在非文本口径下必须 ≥3:1。 */
  @Test fun white_glyph_on_every_domain_gradient_start_clears_3_to_1() {
      val white = 0xFFFFFF
      DomainLight.forEach { (domain, p) ->
          val start = mixHex(p.deep, p.base, 0.35)
          val r = ratio(white, start)
          assertTrue(r >= 3.0, "$domain 容器亮端 #%06X 上白字形 %.2f:1 不足 3:1".format(start and 0xFFFFFF, r))
      }
  }
  @Test fun base_as_white_glyph_backdrop_is_rejected() {
      // 反证：直接用 base 作底正是 spec §2.1 点名的失败形状（AI 2.11 / 复习 2.06）
      listOf("AiSpeech" to 0xFF2EC99C, "Review" to 0xFFFF9F0A).forEach { (name, base) ->
          assertTrue(ratio(0xFFFFFF, base) < 3.0, "$name 的 base 作白字底本应不合格；规则需重议")
      }
  }
  @Test fun inline_deep_glyph_on_tint_clears_3_to_1() {
      DomainLight.forEach { (_, p) ->
          assertTrue(ratio(p.deep and 0xFFFFFF, 0xFFDDF7E8) >= 3.0)   // 实测 3.55–8.06
      }
  }
  @Test fun dark_container_with_dark_glyph_clears_3_to_1() {
      DomainDark.forEach { (_, p) ->
          assertTrue(ratio(p.deep and 0xFFFFFF, 0xFF06231A) >= 3.0)   // 实测 5.96–9.82，最差危险域
      }
  }
  ```
  再加两条源码正则断言：`EntryIconBadge` 里渐变参数必须是 `gradientStartForDomain(...)` 的结果（不得直接写 `p.base`）；`InlineIconBadge` 不出现 `appImageVector` 的填充层（呈现为单层描边）。

  Expected 首跑：编译失败（`mixHex`/`gradientStartForDomain` 未定义）。`mixHex` 在测试侧独立实现一份（int 域），避免测试与生产共用同一实现而失去验证力——注释里写明这是有意的重复。

- [ ] **Step 2: 实现 `gradientStartForDomain` + 跑测试通过**

  `fun gradientStartForDomain(deep: Color, base: Color): Color = mix(deep, base, 0.35f)`（用 Compose 的 `Color.mix`；若本工程 Compose 版本无 `mix`，手写 ARGB 线性插值并保留 alpha）。混出端点后再对 sRGB 做视觉校验的责任由测试承担：测试里用 int 版 `mixHex` 算出的比值即验收依据（两版实现必须给出同一端点，加一条断言：把 `Color` 分量转回 int 与 `mixHex` 逐通道相等）。

- [ ] **Step 3: 实现 `AppIcon.kt`**

  `vector { }` DSL 组装：`group` 内先画填充层（`fillBrush = SolidColor(tint.copy(alpha = DuotoneFillAlpha.forTheme(isDark)))`，`isDark` 取 `LocalIsDark`；计划原文写的 `spec.fillAlpha` 已由 Task 6 执行期修正移除，透明度不再挂在字形上）再画骨架层（`stroke = Stroke(width = 2f * densityScale, cap = Round, join = Round, brush = SolidColor(tint))`）。`CircleFill(cx, cy, r)` 串解析成圆形 path。所有尺寸走 24dp `defaultWidth/defaultHeight`，实际缩放交给 `Image`。
  注释写明为什么运行时组装而不是直接内嵌静态 `ImageVector`：颜色必须来自 `LocalAppColors`/域色，静态 val 会把主题化挡在门外（与 Task 3 同一理由）。

- [ ] **Step 4: 实现 `IconBadge.kt`**

  两种呈现按 Interfaces 的几何。`Modifier.semantics` 不留内容描述（装饰性），容器 `Box(Modifier.size(40.dp).clip(AppShape.Icon).background(brush).border(1.dp, AppPalette.GlassHighlight, AppShape.Icon))`（原文的 `RoundedCornerShape(AppShape.icon)` 是双重笔误：`AppShape` 里没有小写 `icon`，且 `AppShape.Icon` 本身就是 `Shape`，不能再包一层）。
  **豁免表强制**：`EntryIconBadge` 开头
  ```kotlin
  require(icon !in StrokeOnlyIcons) { "$icon 属两层豁免表，只作行内/尾部指示，不得进入 ≥40dp 入口容器" }
  ```
  并写一个 `@Test fun entry_badge_rejects_stroke_only_glyphs()` 的 JVM 单测覆盖这条 `require`（纯函数式调用 `StrokeOnlyIcons.contains(...)` 的判断即可，不必渲染——把判定抽成 `fun allowsInEntryContainer(icon: AppIcon): Boolean`）。

- [ ] **Step 5: 门控 + 提交**

  ```bash
  git add app/src/main/java/com/example/englishlearning/ui/components/AppIcon.kt \
          app/src/main/java/com/example/englishlearning/ui/components/IconBadge.kt \
          app/src/test/java/com/example/englishlearning/ui/IconBadgeContrastContractTest.kt
  git -c user.name=steveliuyan -c user.email=2021250388@qq.com commit \
    -m "feat(ui-icons): 双色图标运行时组装与入口/行内两种呈现，容器对比按实测钉死"
  ```

---

### Task 8: 三件棘轮测试上线（裸 hex / 深色令牌消费 / 组件份数）

**Files:**
- Modify: `app/src/test/java/com/example/englishlearning/ui/SourceRatchet.kt`（补 `violations`）
- Create: `app/src/test/java/com/example/englishlearning/ui/RawColorLiteralRatchetTest.kt`
- Create: `app/src/test/java/com/example/englishlearning/ui/DarkTokenConsumptionRatchetTest.kt`
- Create: `app/src/test/java/com/example/englishlearning/ui/SharedComponentUniquenessRatchetTest.kt`

**Interfaces:**
- Consumes: 全 `src/main/java` 源码文本。
- Produces: `fun uiFiles(dir: File): List<File>`、`fun stripComments(text: String): String`、`fun countMatches(text: String, regex: Regex): Int`、
  `fun violations(label: String, rule: Regex, allowlist: Set<String>, scanDir: File): List<String>`（allowlist 条目形如 `"文件名: 折叠后的源码行"`；返回行不在 allowlist 内即违规）、以及两个 seed 表 `RawColorSeedAllowlist`、`DarkTokenSeedAllowlist`（`Map<String, Int>` 按屏计数）、`ComponentSeedCounts`。

- [ ] **Step 1: 完成 `SourceRatchet.kt`**

  `violations` 的守卫必须复刻 `AccessibilityColorTokenContractTest.scanMainSources` 的措辞：目录缺失或零 `.kt` 立即 `assertTrue` 失败并在消息里点名期望路径与当前 JVM 工作目录（`walkTopDown()` 对缺失路径返回空序列会造成静默空扫）。
  offender 行按既有口径做「文件名 + `trim().replace(Regex("\\s+"), " ")` 的源码行」，**不报行号**（块注释剥离会把其后行折叠进同一行文本）。

- [ ] **Step 2: `RawColorLiteralRatchetTest`**

  规则：`Regex("""Color\(\s*0x[0-9A-Fa-f]{8}\s*\)""")`，扫描 `src/main/java/com/example/englishlearning/ui`，**排除 `ui/theme/`**（令牌定义本体必须写 hex）。
  seed allowlist = 实测 13 处站点（每处以 `文件名: 行` 形状写入，行内容用 `grep -n` 现场取回后折叠空白）：
  `AppScreen.kt:2223`、`AppScreen.kt:2336`、`AppScreen.kt:2369`、`AppScreen.kt:2413`、`ArticleReadingScreen.kt:346`、`CheckInScreen.kt:148`、`glass/GlassButton.kt:76`、`mascot/AiMascot.kt:46`、`TodayPlanScreen.kt:160`、`TodayPlanScreen.kt:161`、`TodayPlanScreen.kt:247`、`TodayPlanScreen.kt:343`、`TodayPlanScreen.kt:344`。
  两条断言：① 违规集 ⊆ seed（新增裸 hex 即失败）；② seed 大小 == 实际命中数（**token 化掉一处时必须同步删 seed**，防止 seed 变成永久豁免）。
  测试注释写明：本棘轮管「新增即挡」，不宣称全站零裸 hex——AC-R-02 的「零」由批 3 收口时 seed 清空来兑现。

- [ ] **Step 3: `DarkTokenConsumptionRatchetTest`**

  按文件统计静态 `AppPalette.` 与 `DomainColors.` 读点，seed 记录每个文件当前计数；断言**每文件计数不得上升**，并在 offenders 里提示应改为 `appColors().x` / `appDomain(AppDomain.X).deep`。
  豁免集合（允许继续静态读，写死并逐条注释理由）：`ui/theme/AppPalette.kt`、`ui/theme/AppColors.kt`、`ui/theme/MintGlassTheme.kt`、`ui/theme/MintPalette.kt`、以及任何**顶层 `Brush`/默认参数**站点（Task 3 Step 4 若产生新站点，一并列入并注明）。
  批 2/批 3 每屏任务的 Step「迁完本屏深色读点」依据的就是本棘轮的 seed 递减。

- [ ] **Step 4: `SharedComponentUniquenessRatchetTest`**

  四条私有实现规则，seed 为 spec §3 实测份数，目标是清零：
  - 页头：`Regex("""Text\([^)]*(?:title|Title)[^)]*style = MaterialTheme\.typography\.headline""")` 形态的私有页头 + 手写返回 `IconButton`/`Icons`-替代的 `clickable`；实测 14 份（`AppScreenHeader(` 调用数应 == 使用它的屏数）。规则形状取「出现 `AppScreenHeader(` 的文件数」与「不出现该调用却含返回+标题版式的文件数」两者，后者即违规。
  - `WordRow`：私有行实现台账 = `SharedComponentUniquenessRatchetTest.componentFamilySeeds["词行（应调 WordRow）"]`（**按文件计**，一个文件可能含两枚裸行组合，故「文件数」≤「实现份数」；原稿写的「7 份」是把 `GlobalVocabularySearchScreen` 的两枚各算一次得到的份数，不是台账数）。台账只减不增，接线一屏删一行，清零归批 2/批 3。
  - `NotConfiguredCard`：4 屏 12 行，规则 = 出现「未配置」文案但不用 `NotConfiguredCard(` 的文件；seed = `SentenceAnalysisScreen.kt`、`WordAiQaScreen.kt`、`PhotoTranslationScreen.kt`、`ImageStudioScreen.kt`。
  - `InlineNotice`：11 份，规则 = 用 `AppPalette.ErrorText`/`colorScheme.error` 直接渲染错误 `Text` 而不走 `InlineNotice(`；seed 从 `LearningRecordsScreen.kt`、`ReminderSettingsScreen.kt`、`BackupExportScreen.kt`、`SettingsScreen.kt` 现场枚举。
  **本任务先只建规则与 seed**，此时九个组件尚未存在，规则应给出「组件文件不存在 → 该规则整体跳过并打印警告」以外的行为：改成断言 `AppScreenHeader.kt` 等文件存在（Task 9/10 建出前本测试会红，因此**把本 Step 放在 Task 10 之后执行**，或先建三个组件的**空签名占位**）。

  执行选择（按 TDD 不写无效测试的口径）：把本 Step 4 推迟到 Task 12 Step 4（组件全部就位后）再建，Task 8 只交付 Step 1–3。计划据此把 Step 4 标注为「顺延到 Task 12 Step 3」。

- [ ] **Step 5: 跑 Step 2/3 的棘轮通过**

  Run: `--tests '*RawColorLiteralRatchetTest*' --tests '*DarkTokenConsumptionRatchetTest*'`
  Expected: PASS（seed 与现状一致）。若命中数与 seed 不符，先 `grep -n` 复核实际站点再决定改 seed 还是改代码——**不得**为凑数放宽正则。

- [ ] **Step 6: 提交**

  ```bash
  git add app/src/test/java/com/example/englishlearning/ui/SourceRatchet.kt \
          app/src/test/java/com/example/englishlearning/ui/RawColorLiteralRatchetTest.kt \
          app/src/test/java/com/example/englishlearning/ui/DarkTokenConsumptionRatchetTest.kt
  git -c user.name=steveliuyan -c user.email=2021250388@qq.com commit \
    -m "test(ui): 裸 hex 与深色令牌消费棘轮上线，只减不增并禁止 seed 固化"
  ```

---

### Task 9: `AppScreenHeader` + `ScreenUnavailable`

**Files:**
- Create: `app/src/main/java/com/example/englishlearning/ui/components/AppScreenHeader.kt`
- Create: `app/src/main/java/com/example/englishlearning/ui/components/ScreenUnavailable.kt`
- Test: `app/src/androidTest/java/com/example/englishlearning/ui/AppScreenHeaderTest.kt`

**Interfaces:**
- Consumes: `appColors()`、`AppType.Title/Footnote`、`AppIcon.Back/Info`、既有唯一按钮 `PillButton`（`ui/components/glass/GlassButton.kt`）。
- Produces:
  - `@Composable fun AppScreenHeader(title: String, modifier: Modifier = Modifier, subtitle: String? = null, onBack: (() -> Unit)? = null, action: @Composable (() -> Unit)? = null)`
  - `@Composable fun ScreenUnavailable(title: String? = null, reason: String, actionLabel: String, onAction: () -> Unit, modifier: Modifier = Modifier, icon: AppIcon = AppIcon.Info)`
    （`title` 是 Task 9 返工轮按裁决 R-9.4 补的可选层，默认 `null` 时卡内只有 `reason` 一层；批 2 消费屏按稿 `.card.empty` 的两层形态传 `title`。）

- [ ] **Step 1: 写 connected 测试（失败）**

  控制者派前核对（原Step 1 的伪 API 全部换成本仓库已实测的口径，依据 `CheckInScreenTest.kt` 既有两条护栏；下列三条已由 Task 9 在 emulator-5574 实测坐实）：
  - 规则用 `createComposeRule()`（组件级，无需 Activity；既有 `CheckInScreenTest` 就是这一种），**不用** `rule.activity.setContent`；
  - 48dp 量法（评审轮按 ui-test 1.10.0 实测纠正口径）：`assertIsDisplayed()` 是唯一读**裁剪后**矩形的断言、只钉可见性；`assertHeightIsAtLeast(48.dp)` / `assertWidthIsAtLeast(48.dp)` 内部走 `withUnclippedBoundsInRoot`，与末尾手写的 `getUnclippedBoundsInRoot()` 是**同一份 unclipped 测量**（各钉一轴 + 一条可读失败消息兜底），**不是两套口径**。**不用** `getSemanticsNode().layoutNode.height` / `captureRegionInRoot`（本仓库无此用法）。**`performScrollTo()` 只适用于身处可滚动容器内的节点**（`CheckInScreenTest` 的文字链在 `horizontalScroll` 行里），组件级宿主没有滚动祖先，Task 9 实测该方法在此直接抛 `Semantic Node has no parent layout with a Scroll SemanticsAction` → 组件级用例不写这一步；
  - 大字号护栏用既有写法：`setContent` 内 `val base = LocalDensity.current` 后 `CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = 2f)) { … }`；
  - 「只有一个可点出口」用 `onAllNodes(hasClickAction()).fetchSemanticsNodes()` 的长度断言，不用 `onLast()`（`onLast()` 不判数量，恒真）；
  - **ui-test 1.10.0（BOM 2025.12.00）没有 `SemanticsNodeInteractionCollection.onFirst()/onLast()`**——`javap` 实测该类只剩 `get(int)` 与 `fetchSemanticsNodes()`；单节点取 `onNodeWithContentDescription(...)` / `onNodeWithTag(...)`，`assertExists` 是成员不是顶层函数（不得 import）。

  三条（每方法一次 `setContent`，绝不复用）：
  ```kotlin
  @Test fun header_back_is_a_48dp_target_with_a_readable_description() {
      composeRule.setContent { MintGlassTheme { AppScreenHeader("学习记录", onBack = { backed = true }) } }
      val back = composeRule.onNodeWithContentDescription("返回")   // 同时钉住「返回节点恰好一个」
      back.assertHasClickAction()
      back.assertIsDisplayed()
      back.assertHeightIsAtLeast(48.dp); back.assertWidthIsAtLeast(48.dp)
      val b = back.getUnclippedBoundsInRoot()
      assertTrue("返回可点区应 ≥48dp，实际=$b", b.bottom - b.top >= 48.dp && b.right - b.left >= 48.dp)
      backed = false; back.performClick(); assertTrue(backed)
  }
  @Test fun header_survives_font_scale_2_without_clipping_the_title() {   // F5-03 移交账
      composeRule.setContent {
          val base = LocalDensity.current
          CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = 2f)) {
              MintGlassTheme { AppScreenHeader("学习记录与复习统计（长标题用）", subtitle = "本周新增 128 词", onBack = {}) }
          }
      }
      val title = composeRule.onNodeWithText("学习记录与复习统计（长标题用）")
      title.assertExists()                                   // 语义节点存在（注意：Clip 与 Ellipsis 都不改语义，此条不足以判裁切）
      val box = title.getUnclippedBoundsInRoot()
      assertTrue("fontScale=2.0 下标题不得塌成 0 宽，实际=$box", box.right - box.left >= 48.dp)
      assertTrue("两行标题不得是 0 高（仅作哨兵，不充当裁切判定：同 style 同 fontScale 的节点高实测 41.90dp vs 34.29dp）",
          box.bottom - box.top > 24.dp)
      // 裁切判定必须读 TextLayoutResult，dp 常数门限不可靠（实测 fontScale=2f 下同款单行节点高
      // 在页头是 41.90dp、在同宿主参考 Text 是 34.29dp → 「> 24.dp」恒真，变异 maxLines=1 不红）。
      // 口径抄本仓库既有的 WordBookWordsScreenTest.kt:179-186，生产代码零改动。
      val layouts = mutableListOf<TextLayoutResult>()
      title.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
      val layout = layouts.single()
      assertTrue("标题必须真的换到两行且无字形裁切：lineCount=${layout.lineCount} overflow=${layout.hasVisualOverflow} size=${layout.size}",
          layout.lineCount >= 2 && !layout.hasVisualOverflow)
  }
  @Test fun screen_unavailable_offers_exactly_one_action() {
      composeRule.setContent {
          MintGlassTheme { ScreenUnavailable(reason = "AI 尚未配置", actionLabel = "去配置", onAction = { tapped = true }) }
      }
      composeRule.onNodeWithTag("screen_unavailable_action").assertIsDisplayed().performClick()
      assertTrue(tapped)
      assertEquals("整屏不可用态只能有一个可点出口",
          1, composeRule.onAllNodes(hasClickAction()).fetchSemanticsNodes().size)
  }
  ```

- [ ] **Step 2: 跑 connected 确认失败**（组件不存在 → 编译失败即 RED 证据，写进报告）。

- [ ] **Step 3: 实现两个组件**

  几何一律以视觉稿 `docs/design/2026-10-17-ui-redesign-mockups.html` 的 §页头（`.appheader`，119–126 行）与 §骨架/空态（`.card .empty`，196–198 + 610 行）为准；下列三处与本计划原表述不同，是控制者派前对照稿子的裁决（R-9.1/R-9.2/R-9.3），稿子是权威。

  `AppScreenHeader`（= 稿子 `.appheader`：`Row`、`gap 10dp`、`align-items:center`、`min-height 48dp`）：
  - **R-9.1 返回位取稿子的 40dp 视觉容器 + 48dp 可点区**（原计划写 `size(48.dp).clip(CircleShape)` 与稿不符）。外层 `Box(Modifier.size(48.dp).clip(AppShape.Icon).clickable(onClick = onBack).semantics { contentDescription = "返回" }, contentAlignment = Center)`，内层视觉 `Box(Modifier.size(40.dp).clip(AppShape.Icon).background(appColors().surface).border(1.dp, appColors().outlineSoft, AppShape.Icon))` 内放 `AppGlyph(AppIcon.Back, appColors().text1, 22.dp)`（字形 22dp 是稿 `mockups.html:528` 的定稿散文**直写**值——「40dp 容器 + 22dp 字形，触控区仍 ≥48dp（外扩 padding）」，与同族 `.ic svg {22px}` 一致；评审轮纠正了此前「稿未单独给尺寸、仅沿 `.ic` 口径」的错误引据，几何不变）。F5-03 的「真 48dp」由外层可点节点满足，Step 1 第一条用例钉的正是它。
  - 标题用 **`AppType.Title`**（稿子 `.ht` = 17px/700，六档里对应 `Title` 17sp SemiBold；原计划写 `Headline` 24sp 是首屏 `.page-title` 那一档，不属于带返回键的页头）+ `color = appColors().text1` + `maxLines = 2` + **不省略号**（页标题裁掉即丢信息）。副标题 `AppType.Footnote` + `color = appColors().text2`（稿 `.hs` 是 12px/400，六档无 12sp Regular——`Label` 是 12sp Medium，引新档会破 spec §2.2 的六档锁，故取同族 `.page-sub` 的 13sp/400 `Footnote`，登记为按 token 近似）。
  - **R-9.2 无 `onBack` 时不渲染占位**（原计划写「同宽 `Spacer(48.dp)` 保持对齐」）。稿子里不带返回键的首屏标题是贴左的 `.page-title`，插一个 48dp 空占位是稿子里不存在的排印。`action` 槽仍在行尾（稿 `.tr { margin-left:auto }`）。
  - 行 `heightIn(min = 48.dp)`（稿 min-height 48px；原计划写 56dp 是入口行 `.entry` 的档，不是页头），`Modifier.testTag("app_screen_header")`；**组件内部不写 `padding(bottom = 6.dp)`**——稿子的 `margin-bottom:6px` 归各屏的列节奏，写进组件会与 `Column(verticalArrangement = spacedBy(…))` 叠成幽灵间距（F5-03 移交账点过名）。

  `ScreenUnavailable`（= 稿子 `.card` 里嵌 `.empty`）：`Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(appColors().surface).border(1.dp, appColors().outlineSoft, MaterialTheme.shapes.medium).padding(24.dp), horizontalAlignment = CenterHorizontally)` + `AppGlyph(icon, appColors().text2, 24.dp)` + 原因文案 `AppType.Footnote` `text2` + **一个**动作 = 复用既有 `PillButton(text = actionLabel, onClick = onAction, style = PillStyle.Secondary, testTag = "screen_unavailable_action")`。
  - **R-9.3 卡片底色取稿子的 `surface` + 1dp `outlineSoft` 描边**（原计划写「`tint` 底卡片」；稿子里 tint 底是 `.banner`/`.card-sel`，`.empty` 所在的是 `.card`＝surface）。圆角走 `MaterialTheme.shapes.medium`——Task 4 已把该 M3 槽校准到 spec §2.3 的 18dp，而 `AppShape.Card` 仍是旧值 20dp（Task 4 明确留给逐屏迁移批），**新组件不得读 `AppShape.Card`**。
  - 动作不新建按钮组件：批 1 唯一按钮是 `PillButton`，再造一个「不可用态专用按钮」会直接撞上 Task 12 的组件份数棘轮。
  - **R-9.4（Task 9 返工轮裁决）`ScreenUnavailable` 补可选 `title` 层**：稿 `.card .empty`（`mockups.html:610-619`）是 **`.t`（15px/700）+ `.d`（13px/400）两层**，Task 9 出厂签名只有 `reason` 一层，间隙 12dp 被错配给了 `.d`（稿里 12 是「插画→`.t`」的 margin，`.d` 自身是 4px）。评审给的两个选项中选 ①：加 `title: String? = null`（`title != null` 时按稿 `.t` 呈现：`AppType.Body`（15sp 与稿同档）+ `color = appColors().text1`，稿的字重 700 与 `Body` 的 400 之差按 token 近似登记，不引新档、不 `copy(fontWeight=…)`（六档锁）），默认 `null` 保持批 1 现有调用不变。选 ②（卡外自排标题）会让 12/4px 两层间隙各屏各抄，正是 Task 12 份数棘轮要挡的形态。副标题/标题的行数上限**不加**（spec §3「不裁文字」硬于稿 panel 散文「副标题只给一行」）。
  - **按钮静态 ink 债（控制者实测登记，Task 9 只登记不修）**：`PillButton` 的 Secondary 档容器读 `AppPalette.Surface`（浅套白）、文字读 `accent.deep`（默认 `DomainColors.AiSpeech.deep` = `#188F76`），两者都是静态浅套值 → 深色模式下整屏会浮出一枚白药片。WCAG 相对亮度比值（与 `SourceRatchet` 同式）：`#188F76` 压白 **4.02:1**（`AppType.Title` 17sp SemiBold 不达 WCAG large-text 口径，故 4.5 线以下）、压深套 `surface #17201C` **4.15:1**；同族对照：深套 `text2 #9FB3AA` 压深套 `surface` **7.54:1**、浅套 `text2 #4E756A` 压白 **5.15:1**。该债属 Task 8 台账「组件层静态读点」一类，修点归批 2 Task 14 的按钮族档位裁决（同批解决白字压域色的既有问题）。**放行前置两条**（Task 9 评审裁决 4.3）：① `ScreenUnavailable` 的动作处 KDoc 必须点明「本卡按钮不随主题翻色、深套下浮白片」这个具体后果，别写成纯视觉档位问题；② 该债是 **Task 14 的硬项**（不是顺带看），且 Task 12 的份数棘轮预先豁免「改 `PillButton`、不新建组件」这条路径，否则两个门互相咬。
  两者颜色全部走 `appColors()`，零裸 hex（Task 8 棘轮会挡），且不新增 `AppPalette.` / `DomainColors.` 静态读点（`Text` 用 `style = AppType.X, color = appColors().…` 的既有写法，照 `GlassButton.kt:93`）。

  本轮登记两处**计划缺口**（不在本任务补，避免接口膨胀；派到各自 owner 时必须先裁决）：
  - 首屏三件套（稿 `.page-title` 24sp + `.page-sub` 13sp + 可选 meta 行，见稿 899/1015/1065/1103 行）无 owner → 归批 2 Task 13/14 派工前裁决是否落成 `AppPageHeader`；
  - 稿子按钮族（`.btn-primary` 54dp/r-btn 14/16sp、`.btn-secondary` 48dp/15sp、`.btn-ghost`、`.btn-pill` 40dp）与 `PillButton`（Pill 50%、min 48dp、`AppType.Title` 17sp、Secondary 描边用 `Separator`）不同档 → 归批 2 Task 14（1 CTA 改版）派工前一并裁决，含 `PillButton` 白字压域色的既有问题。

- [ ] **Step 4: 跑 connected 通过 + 门控**（只准 `emulator-5574`，命令口径照 Task 5 Step 5：`assembleDebug` + `assembleDebugAndroidTest` → 两次 `adb install -r` → `adb shell am instrument -w -e class com.example.englishlearning.ui.AppScreenHeaderTest com.example.englishlearning.test/androidx.test.runner.AndroidJUnitRunner`；**绝禁 `connectedDebugAndroidTest`**，绝不在真机 `bf353dda` 装测试包或跑 instrument）。JVM 门控基线：**214 suites / 1599 tests / 0 失败 / 0 跳过**（本任务只加 androidTest，JVM 总数持平即合格）。

- [ ] **Step 5: 提交**

  ```bash
  git add app/src/main/java/com/example/englishlearning/ui/components/AppScreenHeader.kt \
          app/src/main/java/com/example/englishlearning/ui/components/ScreenUnavailable.kt \
          app/src/androidTest/java/com/example/englishlearning/ui/AppScreenHeaderTest.kt
  git -c user.name=steveliuyan -c user.email=2021250388@qq.com commit \
    -m "feat(ui-components): 统一页头与整屏不可用态，返回位真 48dp 且大字号不裁标题"
  ```

---

### Task 10: `InlineNotice` + `EntryRow`

**Files:**
- Create: `app/src/main/java/com/example/englishlearning/ui/components/InlineNotice.kt`
- Create: `app/src/main/java/com/example/englishlearning/ui/components/EntryRow.kt`
- Modify: `app/src/main/java/com/example/englishlearning/ui/components/IconBadge.kt`（R-10.2 加 `EntryIconTone` + `tone` 形参；R-10.3 字形 24→22dp）
- Modify: `app/src/test/java/com/example/englishlearning/ui/IconBadgeContrastContractTest.kt`（28 组合复算 + 锚点串 `size = 24.dp` → `22.dp`）
- Test: `app/src/androidTest/java/com/example/englishlearning/ui/EntryRowTest.kt`
- Test: `app/src/androidTest/java/com/example/englishlearning/ui/InlineNoticeTest.kt`
- Test: `app/src/test/java/com/example/englishlearning/ui/InlineNoticeKindTest.kt`
- Test: `app/src/test/java/com/example/englishlearning/ui/EntryRowInkTest.kt`

**Interfaces:**
- Consumes: `appColors()`、`appDomain(AppDomain)`、`IconBadge`（`EntryIconBadge`/`InlineIconBadge`）、`AppType.Body/Footnote`、`androidx.compose.ui.graphics.lerp`。
- Produces:
  - `enum class InlineNoticeKind { Error, Info, Success }`
  - `data class NoticeInk(val foreground: Color, val backdrop: Color, val border: Color, val action: Color)`（控制者派前把原 `Pair<Color, Color>` 扩成四字段：稿 `.banner` 每态钉的是**前景 + 底 + 描边 + 动作墨**四个值，Pair 钉不住后两个 → 纯表必须全覆盖才叫「纯表」）
  - `fun noticeInk(kind: InlineNoticeKind, set: AppColorSet): NoticeInk`（纯函数，无 `@Composable`）
  - `@Composable fun InlineNotice(text: String, kind: InlineNoticeKind, modifier: Modifier = Modifier, action: @Composable (() -> Unit)? = null)`（`testTag("inline_notice_${kind.name.lowercase()}")`）
  - `enum class EntryIconTone { Gradient, Tint }`（新增在 `IconBadge.kt`，见 R-10.2）
  - `@Composable fun EntryIconBadge(icon: AppIcon, domain: AppDomain, modifier: Modifier = Modifier, tone: EntryIconTone = EntryIconTone.Gradient)`（既有签名**加默认值形参**，批 1 既有调用零改动）
  - `data class EntryRowInk(val title: Color, val subtitle: Color, val trailing: Color, val chevron: Color, val divider: Color)` + `fun entryRowInks(set: AppColorSet): EntryRowInk`（纯表，见 R-10.4）
  - `@Composable fun EntryRow(icon: AppIcon, domain: AppDomain, title: String, modifier: Modifier = Modifier, subtitle: String? = null, trailing: String? = null, showChevron: Boolean = true, topDivider: Boolean = false, iconTone: EntryIconTone = EntryIconTone.Gradient, onClick: () -> Unit)`（整行 ≥48dp、`role = Role.Button`、`testTag` 由调用方经 `modifier` 传入）

- [ ] **Step 1: 写纯函数单测（失败）**

  `InlineNoticeKindTest` 断言三态到 token 的映射是**纯表**（`noticeInk(kind, set)`，两套主题各跑一遍；亮度函数在测试端独立实现，同 `SourceRatchet`/`IconBadgeContrastContractTest` 的先例，注释不抄比值以防漂移）。控制者已按下表**先算过一遍**（公式与 `SourceRatchet` 同式；底色里的 `mix` 是 CSS `color-mix(in srgb, X f%, surface)` 的 sRGB 逐通道插值，等价 `lerp(surface, X, f)`）：

  | 态 | 前景 | 底 | 浅套比值 | 深套比值 |
  |---|---|---|---|---|
  | Info | `onMint` | `tint`（token 直取） | **8.61** | **12.89** |
  | Error | `error` | `lerp(surface, errorFill, 0.12)` | **6.11** | **5.84** |
  | Success | `success` | `lerp(surface, success, 0.12)` | **6.53** | **7.34** |

  三条用例（每条都双向：既钉「等哪个 token」也钉「≥4.5」）：
  ```kotlin
  @Test fun error_notice_foreground_is_the_error_text_token_not_the_error_fill() {
      val ink = noticeInk(InlineNoticeKind.Error, AppColorLight)
      assertEquals(AppColorLight.error, ink.foreground)
      assertTrue(ink.foreground != AppColorLight.errorFill)   // 前景是 error 文，不是 error-fill 填充
      assertEquals(lerp(AppColorLight.surface, AppColorLight.errorFill, 0.12f), ink.backdrop)
      assertTrue(ratioOf(ink.foreground, ink.backdrop) >= 4.5)
  }
  @Test fun info_notice_pins_tint_backdrop_and_on_mint_foreground() { /* 同形，钉 tint + onMint，浅深两套都 ≥4.5 */ }
  @Test fun success_notice_clears_aa_on_its_own_backdrop_in_both_sets() { /* 同形，两套主题各断一次 */ }
  ```
  **动作墨必须偏离稿值（R-10.1b，控制者实测后裁决）**：稿 `.banner .act { color: var(--d-ai) }`（浅套 = `#188F76`）压浅套 `tint` 只有 **3.55:1**，正文级文本不达 4.5，会被批 1 Task 1 的低对比禁令契约当场挡下；`noticeInk` 的 `action` 一律取 `set.text1`（压三态底最低：浅 **14.11**、深 **11.31**），稿的 700 字重靠 `FontWeight.Bold` 保「动作是一枚链接」的读感。深套 `.act` 实测 8.63 达标，但两套同值比「按主题分叉一个未裁决的强调色」便宜。

  **只有三态，不建 `Warning` 第四态（R-10.1c）**：稿另有 `.banner warn`（琥珀三角，`mockups.html:180-181`），但它的两处出现分别是组件样张本身（`:596`）与 AI 屏的「未配置」提示（`:1106`，归 Task 11 `NotConfiguredCard`），且 `AppIcons.kt:30` 已把 `Warning` 字形登记为 **`InlineNoticeKind.Error` 用图**——枚举加第四态会让图标登记与 `DesignTokenContractTest` 双双对不上（spec §2.1 的 token 表没有 warning 值，组件里私设琥珀色就是私设 token）。处置：三态 = `Info`/`Error`/`Success`，`Error` 取稿 `.err` 的红与 `Warning` 字形；若批 2 真出现「警示但不致命」的行，走 token 契约升级（spec §2.1 补值 + Task 2 契约同步），不在组件里凑色。`Success` 态稿面没有样张，依据是 spec §组件表把 `InlineNotice` 定义为「错误/**回执**行统一样式」+ `AppIcons.kt:5` 已登记 `Check` 为成功回显——墨色用既有 `success` token 按 `.warn` 同式（12% 混底 + 34% alpha 边），不是新造色。

  同一步再建 `EntryRowInkTest`（钉 R-10.4 的 `entryRowInks` 纯表，浅深两套各一遍）——connected 读不到颜色，这张表是入口行墨色的**唯一**证据面：
  ```kotlin
  @Test fun entry_row_inks_are_pinboard_slots_not_ad_hoc_colors() {
      val inks = entryRowInks(AppColorLight)
      assertEquals(AppColorLight.text1, inks.title)
      assertEquals(AppColorLight.text2, inks.subtitle)
      assertEquals(AppColorLight.text2, inks.trailing)
      assertEquals(AppColorLight.outlineSoft, inks.divider)
  }
  @Test fun chevron_is_pinned_to_text2_and_explicitly_not_to_text3() {
      // text3 压浅套 tint 恰 3.00:1，卡在非文本 3:1 线上；Task 1 裁决 #1 又禁 text-3 作前景
      listOf(AppColorLight, AppColorDark).forEach { set ->
          val inks = entryRowInks(set)
          assertEquals(set.text2, inks.chevron)
          assertTrue(inks.chevron != set.text3, "chevron 不得回到 text3：$inks")
          assertTrue(ratioOf(inks.chevron, set.tint) >= 3.0, "chevron 压 tint 的非文本对比：${ratioOf(inks.chevron, set.tint)}")
      }
  }
  ```
  Expected: 编译失败（`InlineNoticeKind`/`noticeInk`/`EntryRowInk`/`entryRowInks` 不存在）——这就是 RED 证据，贴原始输出。

- [ ] **Step 2: 实现 `InlineNotice.kt`**（含 `noticeInk` 纯表）与 `EntryRow.kt`。

  几何一律以视觉稿为准（稿 §入口行 `mockups.html:150-160`、§Banner `:178-186`）。控制者派前对照稿子与既有契约下五裁决（R-10.1~R-10.5），稿子是权威、计划原表述与之冲突时按裁决改：

  - **R-10.1 `InlineNotice` = 稿 `.banner` 家族**：`Row(fillMaxWidth().clip(MaterialTheme.shapes.medium).background(backdrop).border(1.dp, ink.border, MaterialTheme.shapes.medium).padding(horizontal = 14.dp, vertical = 12.dp), spacedBy(10.dp), verticalAlignment = Top)` + `AppGlyph(状态字形, ink.foreground, 18.dp)`（稿 `.banner svg` 是 18px；字形按 `AppIcons.kt` 逐枚清单已登记的落点取：**Info→`Info`、Error→`Warning`（`:30` 钉死）、Success→`Check`（`:5`）**。稿 `.err` 画的是「圆圈+叉」，本仓字形家族没有这枚（B 案 **33** 枚已定稿——Task 1 决策文档的逐枚清单与本计划通篇同数，枚数可重算：`sed -n '/^enum class AppIcon /p' app/src/main/java/com/example/englishlearning/ui/components/AppIcons.kt | sed 's/.*{//; s/}//' | tr ',' '\n' | wc -l` 打印 33；扩一枚要重走图标决策 + 许可登记），按 `:30` 的登记用三角承载 Error，这是**已登记的偏离**、不是随手替换，KDoc 里写明；**层开关按豁免表走**：`Info`/`Warning` 是两层枚用默认 `Duotone`，`Check` 在 `StrokeOnlyIcons` 里、必须显式 `layer = AppGlyphLayer.StrokeOnly`；字形是装饰层，`contentDescription = null`，语义由文案承载）+ 文案 `AppType.Footnote`（稿 13px）+ `ink.foreground`（**不给 `maxLines`**：提示文案裁掉即丢信息，与入口行副标题不同口径）+ `action`。**动作不是按钮**：稿 `.act` 是无容器文本链接，`PillButton` 会把「一条提示」升级成「一个主操作」，故用 `Text` + `clickable` + `ink.action` + `FontWeight.Bold`（稿 700）+ `Modifier.testTag("inline_notice_action")`，触控面按 F5-03 硬下限给 **`Modifier.heightIn(min = 48.dp).padding(horizontal = 8.dp)`**（在 `clickable` 之前）。**注意别把 48dp 下限错派给整条提示**：无动作的 `.banner` 只有 `padding(vertical = 12)` + 13sp 行高 ≈ 42dp，它是**内容不是触控目标**，48dp 只约束动作节点——`EntryRow` 那种整行可点的才需要行级下限。**border 三态取稿**：Info `set.outline`、Error `set.errorFill.copy(alpha = 0.36f)`、Success `set.success.copy(alpha = 0.34f)`（稿 `color-mix(… 34%/36%, transparent)` 的等价写法就是 alpha）。
  - **R-10.2 入口图标容器有两档强度，`EntryIconBadge` 加 `tone` 而不是再造组件**：稿 `.ic`（40dp，实底域渐变 + 白墨）与 `.ic-tint`（同 40dp 容器、底 = `color-mix(in srgb, deep 16%, surface)`、字形 = `deep` 单层描边）是**同族两档**，稿里 `.ic-tint` 出现 21 次，且批 2 Task 14「今日计划」的三条入口行**全部用弱档**（稿 `:912-916`）——不加 `tone` 就等于让 Task 14 手搓第二份容器，正撞 Task 12 份数棘轮。做法：`IconBadge.kt` 内新增 `enum class EntryIconTone { Gradient, Tint }`，`EntryIconBadge` 加 `tone: EntryIconTone = EntryIconTone.Gradient` 默认形参（既有调用零改动）；`Tint` 档 = `size(40.dp)` + `clip(AppShape.Icon)` + `background(lerp(colors.surface, palette.deep, 0.16f))` + `AppGlyph(icon, palette.deep, 22.dp, layer = StrokeOnly)`，**不描 1dp 玻璃高光边**（稿 `.ic-tint` 无描边；高光边属于 `.ic` 实底档）。准入 `require(allowsInEntryContainer(icon))` **两档都保留**（弱档同样不该承接开放折线豁免枚——豁免枚只在行内/尾部出现）。控制者已按 `SourceRatchet` 同式复算弱档「`deep` 字形压 16% 混色底」（底色 = `lerp(set.surface, palette.deep, 0.16f)`，浅套 `surface` 取 `FFFFFF`、深套取 `17201C`，**不是** `background`）的非文本 3:1：浅套最低 `AiSpeech` **3.30**、其余 4.11~7.04；深套最低 `Danger` **4.76**、其余 5.21~6.86 → **七域两套全部达标**，`IconBadgeContrastContractTest` 里要按这 28 个组合（7 域 × 2 档 × 2 主题）双向复算，比值写进断言消息、注释不抄数（抄了会随取值漂移）。两档字形调用段的**形状**契约重写见 R-10.3——同一文件、同一提交，别只改对比不改形状。
  - **R-10.3 字形 24dp → 22dp，且 `IconBadgeContrastContractTest` 的入口形状段必须同提交重写**：稿 `.ic svg { width:22px; height:22px }`（`:153`），Task 7 出厂的 `EntryIconBadge` 画的是 24dp。批 1 里这是最后一处能顺手对齐的度量差，且 `EntryIconBadge` 目前**零生产消费方**（改它不会波及任何屏）。
    现有契约（`IconBadgeContrastContractTest.kt:126-146`、`:673-700`）钉的是三件事：行内 17dp 调用**必须**显式 `layer = StrokeOnly`（`strokeOnlyGlyphCall`）；入口调用段靠 `entryGlyphCallSegment`（内含 `size = 24.dp`）找出**恰好 1 处**；该段内 `layer =` 出现 **0 次**（`explicitLayerArgument`），另有一条 `strokeOnlyOnEntryGlyph`（`size = 24.dp , layer = StrokeOnly`）禁止入口被降级成单层。R-10.2 落地后这三条里有一条**口径反了**：弱档合法地就是单层。因此本任务把这一段改成（逐条写进测试，含双向合成片段）：
    ```kotlin
    // 入口族字形统一 22dp（稿 .ic svg）；两档各一枚，共 2 处调用
    private val entryGlyphCallSegment =
        Regex("""AppGlyph\((?:[^()]|\([^()]*\))*?size\s*=\s*22\.dp(?:[^()]|\([^()]*\))*?\)""")
    private val entryStrokeOnlyCall =
        Regex("""size\s*=\s*22\.dp\s*,\s*layer\s*=\s*AppGlyphLayer\.StrokeOnly""")
    ```
    断言面：`entryGlyphCallSegments(badge).size == 2`（0/1/3 都判红：1 处 = 弱档没接上或两档坍缩成一档）；**两档各按其形状判**——恰一处含 `entryStrokeOnlyCall`（弱档单层），余下一处 `layer =` 出现 0 次（实底档靠默认两层）。`strokeOnlyOnEntryGlyph` 这条旧禁令连同 `24.dp` 锚点一并删除，别留死正则。合成片段同步：实底档 22dp 无 layer、弱档 22dp 带 StrokeOnly、行内 17dp 带 StrokeOnly（不得被认成入口段）、旧 24dp 串不得再被任何入口正则匹配。
    **反向变异自证**（Task 9 C-1 学到的纪律：改锚点必须自证锚点还承重）：① 生产实底档字形改回 `24.dp` → 契约红；② 生产弱档删掉 `layer = StrokeOnly` → 契约红；③ 生产弱档补上渐变（多一处 `linearGradient`）→ `gradientFromRule` 的「恰好一处」红。三次变异各跑一次、贴输出、改回。
    另注意：`rawBaseEndpoint` 与 `gradientFromRule` 两条正则在两档实现下仍然成立（弱档底色是 `lerp(colors.surface, palette.deep, 0.16f)`，不含裸 `.base`，也不新增渐变），不要顺手改它们。
  - **R-10.4 入口行墨色走纯表 `entryRowInks(set)`**：`AppGlyph`/`Text` 的颜色不是语义节点能读的东西，connected 侧「断言 chevron 颜色 == `text2`」经语义树根本取不到色（Task 9 实测口径；像素捕获不在本条口径内，见 Task 10 Step 3 口径）。把墨色抽成纯函数在 JVM 侧钉，`EntryRow` 只读这张表：`title = text1`、`subtitle = text2`、`trailing = text2`、`chevron = text2`、`divider = outlineSoft`。**chevron 用 `text2` 不用 `text3`**（控制者复算：`text3` 压浅套 `tint` 恰 **3.00:1**、压白 3.40，`text2` 压 `tint` **4.55**、压白 5.15；非文本 3:1 线卡在边界上不可靠，Task 1 裁决 #1 也禁 `text-3` 作前景）。用例里同时钉「`chevron != set.text3`」，防止有人「按稿改回 text3」。
  - **R-10.5 行间发丝线归组件、用 `topDivider: Boolean = false`**：稿 `.entry + .entry { border-top:1px outline-soft }` 是「除首行外每行一条顶线」。不新增 `EntryRowGroup`（撞份数棘轮），也不留给各屏手搓 `HorizontalDivider`（批 2 有 5 屏 × 3~8 行要抄 30 余次）；`topDivider == true` 时在内容行**外侧**画一条 1dp `entryRowInks(set).divider` 顶线（`drawBehind` 或外层 `Column` 皆可），**不得**把 56dp 最小内容高吃成 55/57。
  - **R-10.6 可证性两条（Task 10 评审轮由控制者补，批 2 起同样适用）**：
    ① **几何值必须在 JVM 侧有契约面**：connected 只能钉「下限」（`assertHeightIsAtLeast(48.dp)`、`≥40dp`），钉不住稿面的**取值**（徽标容器 40dp、行 56dp/间隙 12dp/padding 8dp、提示条 padding 14×12dp、间隙 10dp、字形 18dp、1dp 描边）。取值漂移（把 40 改成 48、把 12 改成 8）在 connected 全绿，只有源码契约能挡——用共享夹具 `uiFiles()` + `stripComments`（`SourceRatchet.kt`，`IconBadgeContrastContractTest` 同一读法）在 `app/src/test/.../ui/SharedComponentGeometryContractTest.kt` 里逐枚钉字面量，并配合成正/负片段自证正则承重（本仓 glyph 尺寸锚的先例口径）。
    ② **墨色「防回潮」必须是可判红的断言**：`assertTrue(inks.chevron != set.text3)` 这类写法在 `assertEquals(set.text2, inks.chevron)` 之后**永远不会单独红**（两枚 token 取值不同，等值成立即不等式成立）——它是文档不是闸。要留裁决就得留它的**依据**：把被拒候选当场复算成不合格（`ratioOf(AppColorLight.text3, AppColorLight.tint) < 3.1`：浅套 text3 压 tint 恰 3.00，卡在 3:1 线上没有余量；`ratioOf(AppColorLight.errorFill, 浅套 Error 底) < 4.5`：实测 3.01）。装饰档阈值因此从 `≥3.0` 收紧为 `≥3.1`（「边界上的合格不算合格」的算术写法，与被拒依据同一条线）。同族先例：`InlineNoticeKindTest` 里 R-10.1b 的 `mockActionRatio < 4.5` 就是这种承重写法，不抄数值、只写进断言消息，token 漂移时当场提示重议。
  - **R-10.7 触控与可点计数的测量节点（Task 10 二轮评审由控制者补，批 2/批 3 全部 connected 用例适用）**：
    ① **48dp 下限必须量在「能点的那枚节点」上**。`assertHeightIsAtLeast` 打在调用方链首的 `testTag` 节点上量到的是整行外框（恒 ≥56dp），把 `clickable` 挪回 `padding` 内侧那种回归（真机实测的可点面只有 40dp）它抓不住；正确写法是 `onNode(hasClickAction(), useUnmergedTree = true).assertHeightIsAtLeast(48.dp)`。同理，`InlineNotice` 的动作槽若把 48dp 断在自带 `heightIn(min = 48.dp)` 的包裹 `Box` 上就是**自证恒真**——槽内文字能否填满 48dp 由 `propagateMinConstraints = true` 决定，断言必须打在槽内那枚可点上。
    ② **「整行只有一个可点目标」这类计数要两棵树都钉**（评审 I-2 的推断被实测否掉，措辞按实测订正）：变异 ⑩（给 chevron 的 `AppGlyph` 加一枚 `Modifier.clickable { }`）实测**合并树与未合并树同时数到 2** —— `Modifier.clickable` 自身写 `MergeDescendants = true`，作为子节点不会被并进父节点，所以「合并树看不见嵌套可点源」不成立、合并树那条本来就能抓这种回归。未合并树计数仍保留（`onAllNodes(hasClickAction(), useUnmergedTree = true).size == 1`），理由是：把「原始语义树里只有一枚可点」钉成契约，并覆盖 `semantics { onClick = … }` 那类非合并型可点源；断言消息与 KDoc **不许写「合并树会把它并进去、数不出来」这种未实测的机制话**。与 Task 9 `AppScreenHeaderTest`（`:106/131`）的差别：那边根节点不是 clickable，同一写法不等于同一承重，照抄前先实测。
    ③ **测试端复算的配方必须绑回生产源码**。28 组合对比表若在测试里自己 `mix(surface, deep, 0.16)` 现算底色，而生产写成 `background` 或 `0.30f`，全表照绿——那张表只对「理想配方」成立。修法：给配方加一条源码字面量锚（`lerp\(colors\.surface,\s*palette\.deep,\s*0\.16f\)` 恰 1 处 + 禁形 `colors\.background`/异系数 0 处 + 正负片段自证），与本任务的几何契约同类。
  `EntryRow` 布局（**Task 10 实测后修订，修饰符顺序就是契约，别再按稿面顺序「顺手调回去」**）：`Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).then(顶线装饰).heightIn(min = 56.dp).padding(vertical = 8.dp), spacedBy(12.dp), CenterVertically)` = `EntryIconBadge(icon, domain, modifier = Modifier.testTag("entry_row_icon"), tone = iconTone)`
  —— 计划原稿写的 `.heightIn(…).padding(…).clickable(…)` 在真机上量到的可点面只有 **40dp**：`clickable` 排在 padding 之后时，上下 8dp padding 与那条 1dp 顶线都留在可点面**外侧**，可点区塌成内容盒，直接撞 F5-03 的 48dp 硬下限（connected 用例 `entry_row_clears_the_48dp_touch_floor` 首跑实测 `Actual height is 40.0.dp` 红，改成上面的顺序后 56dp 绿）。几何等价：整盒仍是 1dp 线 + 56dp（内含 8+8 padding、内容下限 40dp），行的外框不因线而缩。
  行内内容：`Column(weight = 1f)`（`title` 取 `AppType.Body` + `text1` + `maxLines = 1` + `Ellipsis`；`subtitle` 取 `AppType.Footnote` + `text2` + `padding(top = 1.dp)` + `maxLines = 1` + `Ellipsis`——入口行的标题/副标题都是提示性内容，与页标题不同档，允许省略号，稿 `.en`/`.ed` 各一行）+ `trailing`（`AppType.Footnote` + `text2`）+ `AppGlyph(AppIcon.ChevronRight, text2, 16.dp, layer = StrokeOnly)`（`showChevron` 为真时）。**省略号必须可证**：`hasVisualOverflow == true` 判不出 `Ellipsis` 与 `Clip`（两种都报 true），所以钉省略号的用例要加 `layout.isLineEllipsized(0)`（R-10.6，Task 10 评审轮补）。
  排印按 token 近似的两处要写进 KDoc：稿 `.en` 是 15px/**600**，六档 `Body` 是 15sp/400；稿 `.ed` 是 12px/400，六档无 12sp Regular 档（同 Task 9 副标题，取 `Footnote` 13sp）。**不引新档、不 `copy(fontWeight = …)`**（spec §2.2 六档锁）。

- [ ] **Step 3: 写 connected 用例（`EntryRowTest` + `InlineNoticeTest`）**

  **口径先写清楚**（批 1 终审 Seat A M7 收窄，原句过宽）：connected **经语义树读不到颜色**（Task 9 实测）——`contentDescription` 与 `SemanticsPropertyKey` 里没有渲染色值，所以墨色一律由 Step 1 的 JVM 纯表（`noticeInk` / `entryRowInks`）钉死，**组件级 connected 用例**只钉**几何、语义、可点数量、裁切**四件事。计划原来那句「connected 断言 chevron 颜色 == `text2`」是不可执行的，作废。
  **但「connected 读不到颜色」不等于「connected 不能验色」**：`captureToImage().asAndroidBitmap()` 读得到真实像素，Task 5 `ThemeModeSwitchTest` 与 Task 12 `IconGalleryRenderTest` 就是靠它把 prefs→装配→像素这条链闭合的（语义通道给不了的唯一证法）。走像素捕获的用例必须写明它依赖的渲染链假设——宿主有没有铺主题底、取样点取在哪、深色档靠 `rememberAppThemeMode()` 活链路还是靠重新 `setContent`；假设不写出来，「取样点其实不是那块底」这类假绿后人无从复核（`IconGalleryRenderTest` 的铺底缺陷就是现例）。
  另一条 Task 9 学到的：`assertHeightIsAtLeast` / `getUnclippedBoundsInRoot` 走的是同一个 `withUnclippedBoundsInRoot` 口径（单口径，不是双口径），`assertIsDisplayed` 才是裁剪后口径；`performScrollTo()` 在组件级 `createComposeRule()` 下无可滚动祖先会抛，别写。

  `EntryRowTest`（每条一个 `setContent`，同方法内不二次 `setContent`——真机必崩）：
  1. `entry_row_is_one_click_target_whole_row_with_button_role`：`Modifier.testTag("entry_row")` + `assert(hasClickAction())` + `SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)` + `performClick()` 后 `clicked == true`；同时 `onAllNodes(hasClickAction()).fetchSemanticsNodes().size == 1`（徽标与 chevron 不得是第二可点源）。
  2. `entry_row_clears_the_48dp_touch_floor`：`assertHeightIsAtLeast(48.dp)`。**故意钉 48 而不是 56**——48 是 F5-03 无障碍硬下限（回归会立刻响），56 是本组件的设计目标值，钉死 56 会让任何合法的字体缩放/内容增高变成假红。
  3. `entry_row_keeps_a_40dp_icon_container_in_both_tones`：`iconTone = Gradient` 与 `Tint` 各一条 `EntryRow`（同一 `setContent` 内竖排两行），取徽标节点的 `getUnclippedBoundsInRoot`，断两档**宽高都 ≥40dp 且相等**（R-10.2 的「同族两档」= 同容器尺寸，只有底色/字形墨不同）。
  4. `entry_row_truncates_title_and_subtitle_with_ellipsis_not_clipping`：`fontScale = 2f` 口径 + 超长标题/副标题，对两个文本节点各取 `TextLayoutResult`（`performSemanticsAction(SemanticsActions.GetTextLayoutResult)`，仓库先例 `WordBookWordsScreenTest.kt:179-186`），断 `lineCount == 1`。这里 **`hasVisualOverflow == true` 是期望结果**（省略号生效=提示性内容被裁，合规），与页头用例的判定方向相反，KDoc 必须写明这条相反口径，否则后人会当成同族断言改掉。
  5. `entry_row_top_divider_is_outside_the_content_height`：两条 `EntryRow`（`topDivider` false / true），断两条 `getUnclippedBoundsInRoot` 高度差 ≥1dp（线画在外侧）且都 ≥48dp——钉 R-10.5 的「不得把 56dp 吃成 55」。

  `InlineNoticeTest`：
  1. `notice_without_action_has_no_click_target`：三态各一条 `InlineNotice`（无 `action`）竖排在**一个** `setContent` 里，`onAllNodes(hasClickAction()).fetchSemanticsNodes().size == 0`，三条 `testTag`（`inline_notice_error` / `_info` / `_success`）都 `assertExists`。
  2. `notice_action_is_the_only_click_target_and_meets_the_48dp_floor`：带 `action` 的一条，`onNodeWithTag("inline_notice_action").performClick()` 触达一次，可点节点数 == 1，且该动作节点 `assertHeightIsAtLeast(48.dp)`（48dp 下限属于**动作**，不属于提示条本体）。
  3. `notice_text_wraps_instead_of_clipping_at_font_scale_2`：长文案 + `fontScale = 2f`，`TextLayoutResult` 断 `!hasVisualOverflow`（提示文案不给省略号，被裁掉的是信息——与入口行副标题的判定方向相反）；**本用例不断高度**，无动作的提示条约 42dp 是合规的（它是内容不是触控目标），把它断成 ≥48dp 会钉出一条稿里没有、F5-03 也没要求的水线。

- [ ] **Step 4: 跑测试通过 + 门控 + 提交**

  JVM 门控（基线 **214 suites / 1599 tests / 0 失败 / 0 跳过**，本任务净增 JVM 用例：`InlineNoticeKindTest` 3 + `EntryRowInkTest` 2 + `IconBadgeContrastContractTest` 的 28 组合改写（净增 0 或按实际），只增不减；`rm -rf` 后按 XML 重数）：

  ```bash
  V=$(grep -oE 'gradle-[0-9]+\.[0-9]+' gradle/wrapper/gradle-wrapper.properties | head -1)
  GRADLE=$(ls -1 "$HOME/.gradle/wrapper/dists/$V-bin/"*/"$V/bin/gradle.bat" | head -1)
  rm -rf app/build/test-results/testDebugUnitTest
  "$GRADLE" :app:assembleDebug :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin \
    verifyThirdPartyNotices --offline --no-daemon --no-build-cache 2>&1 \
    | grep -E 'FAILED|^e:|BUILD|tests completed' ; exit ${PIPESTATUS[0]}
  ```

  connected 只跑这两个类（`emulator-5574`，**绝不用 `connectedDebugAndroidTest`**，绝不在真机 `bf353dda` 上跑）：

  ```bash
  export ANDROID_SERIAL=emulator-5574
  adb install -r app/build/outputs/apk/debug/app-debug.apk
  adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
  adb shell am instrument -w \
    -e class com.example.englishlearning.ui.EntryRowTest \
    com.example.englishlearning.test/androidx.test.runner.AndroidJUnitRunner
  adb shell am instrument -w \
    -e class com.example.englishlearning.ui.InlineNoticeTest \
    com.example.englishlearning.test/androidx.test.runner.AndroidJUnitRunner
  ```
  Expected: 两个类各自 `OK (n tests)`。**反向变异自证清单（本任务唯一一份，跑满五条、逐条贴输出、每条改回；R-10.3 段与 Task 10 评审轮补的两条合并在此）**：
  ① 生产实底档字形改回 `24.dp` → `IconBadgeContrastContractTest` 红（`entryGlyphCallSegments` 计数 2→1 + `legacyEntryGlyphSize` 不归零，双锚）；
  ② 生产弱档删掉 `layer = StrokeOnly` → 分档计数 1→0 红；
  ③ 生产弱档补上一条渐变（多一处 `linearGradient`）→ `gradientFromRule` 的「恰好一处」红；
  ④ `entryRowInks` 的 `chevron` 改回稿面 `text3` → `EntryRowInkTest` 红；
  ⑤ **（R-10.6，Task 10 评审轮补）** 把 `EntryRow` 标题的 `TextOverflow.Ellipsis` 改成 `TextOverflow.Clip` → `entry_row_truncates_title_and_subtitle_with_ellipsis_not_clipping` 必须红（红点必须是 `isLineEllipsized(0)` 那条，不是 `lineCount`/`hasVisualOverflow`——那两条在 `Clip` 下同样成立，抓不住「裁掉 vs 省略号」）。

  ```bash
  git add app/src/main/java/com/example/englishlearning/ui/components/InlineNotice.kt \
          app/src/main/java/com/example/englishlearning/ui/components/EntryRow.kt \
          app/src/main/java/com/example/englishlearning/ui/components/IconBadge.kt \
          app/src/test/java/com/example/englishlearning/ui/IconBadgeContrastContractTest.kt \
          app/src/test/java/com/example/englishlearning/ui/InlineNoticeKindTest.kt \
          app/src/test/java/com/example/englishlearning/ui/EntryRowInkTest.kt \
          app/src/androidTest/java/com/example/englishlearning/ui/EntryRowTest.kt \
          app/src/androidTest/java/com/example/englishlearning/ui/InlineNoticeTest.kt
  git -c user.name=steveliuyan -c user.email=2021250388@qq.com commit \
    -m "feat(ui-components): 统一行内提示与彩色图标入口行，chevron 按非文本对比取 text2，入口徽标补弱档"
  ```

---

### Task 11: `NotConfiguredCard` + `ConfirmDestructiveDialog` + `OutboundConfirmDialog`

> **R-11.0（Task 10 二轮评审 I-5 的控制者裁决，接线前定）**：`InlineNotice` 的 `action` 槽目前只给「48dp + testTag + 合并语义」，
> 动作本体（`Text` + `clickable` + `FontWeight.Bold` + `noticeInk(kind, appColors()).action`）**留给调用方**——批 2/3 收 11 份提示的同时会长出 11 份链接配方，
> 而 Task 12 的份数棘轮按**文件**数「不走 `InlineNotice(` 的错误渲染站点」，自排动作链接既不是错误 Text 站点也不新增文件，抓不住。
> 裁决：Task 11 的第一步就是在 **`InlineNotice.kt` 同文件**里加公开 `InlineNoticeAction(kind: InlineNoticeKind, label: String, onClick: () -> Unit, modifier: Modifier = Modifier)`
> （内部就是那四件套 + ~~`fillMaxHeight()` 承接 48dp~~ **见下 R-11.0a 订正**），调用方写成 `action = { InlineNoticeAction(InlineNoticeKind.Error, "重试", onClick = { … }) }`。
> 同文件新增 → 不撞份数棘轮；墨色/字重/触控只有一处真值。先写失败用例（connected：槽内可点节点 ≥48dp 且未合并树计数仍为 1；JVM：源码契约钉 `FontWeight.Bold` 与 `noticeInk(…).action` 各恰 1 处、
> 且禁止调用方文件里出现 `color = noticeInk(` 自取动作墨），再实现。R-10.7① 的那条 48dp 断言在本任务起改打在 `InlineNoticeAction` 的可点节点上。

> **R-11.0a（实现期实测订正，2026-10-19 控制者追认）**：上条裁决写的「内部就是那四件套 + `fillMaxHeight()` 承接 48dp」**机制上不成立**，作废。
> `fillMaxHeight()` 吃的是**最大**高约束而非最小：在有界宿主（组件级 `createComposeRule()`）里它把可点面撑到 **635.0476dp**（整条提示被拉成半屏空白，实测断言消息 `expected:<48.0.dp> but was:<635.0476.dp>`）；
> 在无界宿主（`LazyColumn` 一类可滚动容器）里它是**空操作**，48dp 仍旧得靠别的机制交付。
> 正确交付机制（即 R-10.7① 已落地的那条）：48dp 钉在 [InlineNotice] 动作槽的**包裹层**上——
> `Modifier.heightIn(min = 48.dp)` + `propagateMinConstraints = true`，包裹层把 48dp 的**最小高约束**传进槽内，槽内 `Text` 量出 48dp，`clickable` 的可点面随之 48dp。
> 故 `InlineNoticeAction` 本体**不写任何尺寸修饰符**，且「本文件 0 处 `fillMaxHeight`」钉进 `InlineNoticeActionContractTest`；
> 槽内自己另写一条 `heightIn(min = 48.dp)` 也不行——那会让「48dp 恰一处」的源码锚变成两处（`SharedComponentGeometryContractTest` 会红）。
> 附带订正：`InlineNoticeAction` 末位形参是 `modifier`，**不能用尾随 lambda**，回调必须具名写 `onClick = { … }`（裁决原文的示例写法编译不过：`No value passed for parameter 'onClick'`）。
> 批 2/批 3 各屏接入时一律照 `InlineNotice.kt:195-199` 的示例写法。

**Files:**
- Create: `app/src/main/java/com/example/englishlearning/ui/components/NotConfiguredCard.kt`
- Create: `app/src/main/java/com/example/englishlearning/ui/components/ConfirmDestructiveDialog.kt`
- Create: `app/src/main/java/com/example/englishlearning/ui/components/OutboundConfirmDialog.kt`
- Test: `app/src/androidTest/java/com/example/englishlearning/ui/NotConfiguredCardTest.kt`
- Test: `app/src/test/java/com/example/englishlearning/ui/ConfirmDestructiveWordingTest.kt`

**Interfaces:**
- Consumes: `IconBadge`、`InlineNotice`、`appColors()`、`AppType`。
- Produces:
  - `@Composable fun NotConfiguredCard(featureLabel: String, onConfigure: () -> Unit, modifier: Modifier = Modifier, detail: String? = null)`
  - `data class DestructiveConfirmTitle/…` 简化为：`@Composable fun ConfirmDestructiveDialog(state: Boolean, title: String, cascadeLines: List<String>, keepLines: List<String>, irreversible: Boolean = true, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit)`
  - `@Composable fun OutboundConfirmDialog(endpointHost: String, payloadKind: PayloadKind, onConfirm: () -> Unit, onDismiss: () -> Unit)`，`enum class PayloadKind { Text, Image }`

- [ ] **Step 1: 写文案与结构单测（失败）**

  `ConfirmDestructiveWordingTest` 断措辞四要素齐备（纯函数 `fun destructiveDialogBody(cascadeLines, keepLines, irreversible): List<String>`）：
  ```kotlin
  @Test fun body_states_cascade_count_kept_surfaces_and_irreversibility() {
      val body = destructiveDialogBody(listOf("会同时删除 12 条复习记录"), listOf("收藏与查询历史不受影响"), irreversible = true)
      assertTrue(body.any { it.contains("12 条复习记录") })
      assertTrue(body.any { it.contains("收藏") })
      assertTrue(body.any { it.contains("不可撤销") })
  }
  @Test fun irreversible_flag_cannot_be_forgotten_to_be_nice() {
      // 不可撤销为 true 时必须出现该字样——业务规则（AGENTS.md 删除级联确认）不得被 UI 轮软化
      val body = destructiveDialogBody(listOf("删除词书《核心 1000》"), emptyList(), irreversible = true)
      assertTrue(body.any { it.contains("不可撤销") }, "级联删除必须明说不可撤销：$body")
  }
  ```
  `OutboundConfirm` 口径另加一条纯函数测试：`fun mustAskEveryTime(kind: PayloadKind, rememberedForHost: Boolean) = kind == PayloadKind.Image || !rememberedForHost`（图片每次确认，文本按域名记忆——AGENTS.md 硬要求，UI 轮不得改动）。

- [ ] **Step 2: 写 `NotConfiguredCardTest`（connected）**：断言「一句现状 + 一个『去配置 ›』出口」，出口可点且 `testTag("not_configured_goto")`；**死屏禁令**用源码契约表达：四个消费屏（`SentenceAnalysisScreen`/`WordAiQaScreen`/`PhotoTranslationScreen`/`ImageStudioScreen`）重排后必须调用 `NotConfiguredCard(` ——该断言在批 3 才有对象，本任务先测组件自身，批 3 收口时补屏侧断言。

- [ ] **Step 3: 实现三组件**，全部走 `appColors()`，不引裸字面量。`ConfirmDestructiveDialog` 确认按钮的容器/前景墨色**按下面 R-11.1 执行**，本任务原稿那句「容器 `errorFill` + 白字」已作废。

  **R-11.1（控制者在 Task 11 派发前裁掉本步的摇摆）**：破坏性确认按钮的墨色**不写成本段原来那句「`errorFill` 填充 + 白字」**。三个理由，均已实算（WCAG 相对亮度，同一套线性化）：
  ① 浅套 `Color.White` on `errorFill #FF3B30` 实测 **3.547**，不是本段原稿写的 4.0；② 深套 `errorFill #FF6B61` 上白字只有 **2.790**，连 3:1 的图形口径都不过——「白字」这条在深套根本不能用，而原稿把它当成跨模式统一规则；
  ③ 17sp 换算约 12.75pt，够不到 WCAG 大字豁免线（14pt bold / 18pt regular），所以「按大字口径享受 3:1」这个前提本身不成立。
  裁决：确认按钮容器取 `set.error`、前景取 `set.surface`——浅套 `#FFFFFF` on `#B30000` = **7.200**，深套 `#17201C` on `#FF8177` = **6.873**，两套都真 ≥4.5:1，且只用既有 token、不新增裸字面量（`RawColorLiteralRatchet` 覆盖 `src/main`）。
  `errorFill` 保持它现有的职责（`InlineNotice` 的 Error 背景与描边、Material `errorContainer`），**不得**被用作按钮容器。要与既有取证挂上钩：滑动删除那枚判合规的是 `#B3261E`（白字 6.5:1 量级），与这里同族的是「深底浅字/浅底深字按套分叉」，不是「红底一律白字」。
  用例：新增 JVM 对比度契约（可仿 `EntryRowInkTest` / `noticeInk` 的写法）钉两套各 ≥4.5，并钉一条**会被拒的候选**——`ratioOf(Color.White, AppColorLight.errorFill) < 4.5` 与 `ratioOf(Color.White, AppColorDark.errorFill) < 4.5` 都为真，把「白字 on errorFill」这条错配方永久钉死在不能回潮的位置。

- [ ] **Step 4: 跑测试通过 + 门控 + 提交**

  ```bash
  git add app/src/main/java/com/example/englishlearning/ui/components/NotConfiguredCard.kt \
          app/src/main/java/com/example/englishlearning/ui/components/ConfirmDestructiveDialog.kt \
          app/src/main/java/com/example/englishlearning/ui/components/OutboundConfirmDialog.kt \
          app/src/androidTest/java/com/example/englishlearning/ui/NotConfiguredCardTest.kt \
          app/src/test/java/com/example/englishlearning/ui/ConfirmDestructiveWordingTest.kt
  git -c user.name=steveliuyan -c user.email=2021250388@qq.com commit \
    -m "feat(ui-components): 未配置卡唯一出口 + 破坏性确认统一措辞 + 出站确认按域名记忆"
  ```

---

### Task 12: `WordRow` + `SpeakerButton` + 组件唯一性棘轮 + 批 1 收口

**Files:**
- Create: `app/src/main/java/com/example/englishlearning/ui/components/WordRow.kt`
- Create: `app/src/main/java/com/example/englishlearning/ui/components/SpeakerButton.kt`
- Create: `app/src/test/java/com/example/englishlearning/ui/SharedComponentUniquenessRatchetTest.kt`（承接 Task 8 Step 4）
- Create: `app/src/androidTest/java/com/example/englishlearning/ui/IconGalleryRenderTest.kt`（Step 4b，批 1 里新字形唯一的呈现处）
- Create: `docs/verification/ui-redesign-2026-10.md`
- Modify: `docs/third-party-notices.md`（`app-authored-ui-icons` 条目的「用途/使用位置」补 `AppIcons.kt` 实际路径）

**Interfaces:**
- Consumes: 批 1 全部产物。
- Produces:
  - `@Composable fun WordRow(lemma: String, gloss: String?, modifier: Modifier = Modifier, status: String? = null, meta: String? = null, domain: AppDomain = AppDomain.Library, leading: @Composable (() -> Unit)? = null, onClick: (() -> Unit)? = null, actions: @Composable (RowScope.() -> Unit)? = null)`
  - `@Composable fun SpeakerButton(state: SpeakerState, onClick: (SpeakerState) -> Unit, modifier: Modifier = Modifier)`；`enum class SpeakerState { Idle, Playing, Paused }`；`fun speakerContentDescription(state: SpeakerState): String`（一处定义朗读文案：`播放发音`/`暂停发音`/`继续发音`）

- [ ] **Step 1: 写 `speakerContentDescription` 单测（失败→实现→通过）**
  ```kotlin
  @Test fun each_playback_state_has_one_distinct_readout() {
      val labels = SpeakerState.entries.map(::speakerContentDescription)
      assertEquals(labels.distinct().size, SpeakerState.entries.size, "三态文案必须互不相同，TalkBack 才能区分")
      assertTrue(labels.all { it.contains("发音") })
  }
  ```
  这条取代两套同构几何里的「文案各写一遍」——spec §3 点名的重复源。

- [ ] **Step 2: 实现 `WordRow` 与 `SpeakerButton`**
  `SpeakerButton` 触控：药片 `heightIn(min = 48.dp)` + `AppGlyph(Speaker, …)` + 文字；`WordRow`：lemma `AppType.Body` `text1`、gloss `AppType.Footnote` `text2`（可 `maxLines = 2`）、status/meta 右对齐 `text2`、`role` 按 `onClick` 是否为空取 `Role.Button`/无。

- [ ] **Step 3: 写 `SharedComponentUniquenessRatchetTest`**（规则与 seed 见 Task 8 Step 4 四条），~~此时九个组件文件都存在~~（**R-12.3 订正：Task 11 落地后是 10 枚，本任务再加 `WordRow.kt`/`SpeakerButton.kt` = 12 枚；断言按「点名文件必须存在」写，不按总数写——总数会随后续批次漂移**），目录守卫与 seed 只减不增口径同 Task 8。
  **R-12.3（Task 8 Step 4 四条规则的实现期订正，控制者本席 grep 实测；详表见 `.superpowers/sdd/2026-10-17-ui-redesign/task-12-precheck.md`）**：
  ① **`NotConfiguredCard` 份数 = 4，已核实**（`ImageStudioScreen.kt:250`/`PhotoTranslationScreen.kt:706`/`SentenceAnalysisScreen.kt:209`/`WordAiQaScreen.kt:264` 各一份 `private fun NotConfiguredCard(`），共享份在 `components/NotConfiguredCard.kt:117`。
  注意**同名遮蔽**：私有份与共享份同名，所以「文件里出现 `NotConfiguredCard(`」不能当合规证据——规则必须是「出现 `private fun NotConfiguredCard(` 即违规」+「`components/NotConfiguredCard.kt` 恰 1 处 `fun NotConfiguredCard(`」。
  ② **页头规则的「14」在批 1 时点断不出东西**：`grep -rl 'AppScreenHeader(' app/src/main/java/com/example/englishlearning/ui` 只命中组件自身（批 1 不接线业务屏，接线在批 2/3），Task 8 Step 4 那条「出现 `AppScreenHeader(` 的文件数 == 使用它的屏数」两侧都是 0/1。
  **扫描根一律写死为 `app/src/main/java/com/example/englishlearning/ui`，别用 `.` 或省参数**（Task 12 派前补）：`.` 会把 `docs/`、`.superpowers/` 简报里的引用和 `app/build/` 的生成物一起卷进来，数出来的「份数」与测试的 `uiFiles()` 口径不同源，两条数无法对账。
  本任务只钉两条：`components/AppScreenHeader.kt` 存在且恰 1 处 `fun AppScreenHeader(`；私有页头形态（同文件含 `MaterialTheme.typography.headline` 却不调 `AppScreenHeader(`）的**文件数** seed = 实测值、只减不增。14 → 0 的兑现归批 3，本任务不宣称清零。
  ③ **`InlineNotice` 的 seed 文件清单与 Task 8 Step 4 写的不符**：实测 `grep -rln 'AppPalette\.ErrorText\|colorScheme\.error' app/src/main/java/com/example/englishlearning/ui` 得 **13 个文件**，其中 `InlineNotice.kt` 是共享组件自身（它合法读错误色），排除后 **12 个**即 seed（`AppScreen`/`ArticleImportScreen`/`BackupExportScreen`/`BackupPairingExportScreen`/`BackupRestoreScreen`/`CardDetailScreen`/`ReadingAccessScreen`/`ReminderSettingsScreen`/`SpeechSettingsScreen`/`WordAiQaScreen`/`WordCardScreen`/`WorksheetSettingsScreen`），
  Task 8 点名的 `LearningRecordsScreen.kt` 与 `SettingsScreen.kt` **不在其中**。Task 8 已写「现场枚举」，故以实测 12 为 seed，清零归批 3。
  **两处口径差要记住，裸 grep 数不等于测试数**：测试走的是**剥注释后**的源码，而 `components/InlineNotice.kt` 的 KDoc 里逐字复述了上面这条 grep、`AppScreen.kt` 也有一行注释提到 `colorScheme.error`——裸 grep 会把注释当债务；同时共享组件文件本身要排除。**seed 的权威是 `SharedComponentUniquenessRatchetTest.componentFamilySeeds`，不是本行写的 12**；接线一屏就删一行、文档随之改，别拿文档当台账。
  ④ **`WordRow` 私有份不能只搜函数名**：私有实现多是 `Row { Text(lemma); Text(gloss) }` 这类无名裸组合，规则按「同文件出现 lemma+gloss 两枚 Text 且不调 `WordRow(`」的形态写；扫描根同②，`ui/` 全目录（含子包）。
  ⑤ **预登记豁免照 Task 12 Step 3 原文写进棘轮注释与 seed 说明**：本棘轮管「同一形态不得有第二份组件」，不管「就地改既有组件的取值面」——Task 14 修 `PillButton` 必须改本体，不得被本棘轮挡。
  **预登记豁免（Task 9 评审裁决 4.3 前置②）**：本棘轮管「同一形态不得有第二份组件」，**不管「就地改既有组件的取值面」**——Task 14 修 `PillButton` 的静态 ink / 档位差时必须改 `PillButton` 本体而不是新建按钮，这条路径不得被本棘轮挡；写进测试注释与 seed 说明，避免批 2 为绕门再造一枚按钮。

- [ ] **Step 4: 建 AC-R 证据文档**

  `docs/verification/ui-redesign-2026-10.md`：表头 `| AC-R | 口径 | 证据 | 状态 |`，逐条填批 1 已可证的：
  - AC-R-01 → `DesignTokenContractTest` + `MaterialThemeAssemblyContractTest`（测试类名，不写用例数）
  - AC-R-02 → `RawColorLiteralRatchetTest`（注明「新增为 0，存量 seed 待批 3 清空」）
  - AC-R-03 → `DesignTokenContractTest` 配对表 + `IconBadgeContrastContractTest`
  - AC-R-04 → `AppScreenHeaderTest` + `EntryRowTest`（connected，emulator-5574）
  - AC-R-05 → `verifyThirdPartyNotices` + 台账三条目
  - AC-R-05b → `AppIconsStructureContractTest`（含豁免表）
  - AC-R-06 → 重算命令与其输出
  - AC-R-07/10/11/12 → 「批 2/批 3 逐屏补」，状态列写「未开始」
  禁写会自漂移的计数（§Global Constraints #16）。

- [ ] **Step 4b: 图标一览 androidTest（批 1 唯一能看到新字形的地方）**

  控制者裁决（修本计划一处真缺陷）：Step 5 标题写「装真机手工看图标」，但批 1 不把底导/业务屏换成 `AppGlyph`（那在批 2/批 3），真机上看到的仍是旧 Canvas 字形——里程碑日无图标可看。补一步：
  - Create: `app/src/androidTest/java/com/example/englishlearning/ui/IconGalleryRenderTest.kt`
  - 一个方法内**只允许一次 `setContent`**（真机两次必崩的既有约束），渲染 33 枚的三视图：`AppGlyph` 单层描边 24dp、`EntryIconBadge`（40dp 渐变容器 + 两层，豁免表 5 枚按 Task 7 的 `allowsInEntryContainer` 跳过）、`InlineIconBadge`（30dp 底 + 17dp 单层）。
  - 每视图 `captureToImage().asAndroidBitmap()` 后写入应用外部缓存目录，用 `adb pull` 取回本地临时目录目视（**图片一律不入库**，与 QR 证据同红线）；断言只放机器可判的两条：非空像素占比 > 0（防空绘制）与浅色/深色两轮都能出图（`AppThemePreferences.write(...Dark)` 后 `waitUntil` 重绘，用完复原 `System`）。
  - 只跑 `emulator-5574`（`am instrument`，禁 `connectedDebugAndroidTest`），禁真机。
  - 目视核验清单（写进 `docs/verification/ui-redesign-2026-10.md` 的 AC-R-05b 行，缺项不得放行）：① `Spark` 伴星补回 `-.9-2.2` 后是四芒而非带竖直尖刺的坏形；② `Book`（合上）与 `BookOpen`（摊开）在 24dp 下可区分，且 `Book` 的左半填充与 `BookOpen` 的左页填充不会让人读成同一枚；③ `Camera` 镜头环（r=3.2）在 17dp 行内不与机身墨迹糊成一团；④ `Trash` 桶身填充与骨架的 0.2dp 同心差、`Spark`/`Download` 笔画端点出 4–20 安全区后在容器内不裁；⑤ 豁免 5 枚（Back/Chevron×2/Close/Check）在 17dp 与 24dp 下均可辨；⑥ 深色档填充层（`DuotoneFillAlpha.Dark`）在深底容器上仍读得出层次。

- [ ] **Step 5: 批 1 门禁 + 里程碑装真机手工看主题与排印**

  ```bash
  "$GRADLE" :app:assembleDebug :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin verifyThirdPartyNotices --offline --no-daemon --no-build-cache 2>&1 | grep -E 'FAILED|^e:|BUILD|tests completed' ; exit ${PIPESTATUS[0]}
  grep -h -o 'tests="[0-9]*" skipped="[0-9]*" failures="[0-9]*" errors="[0-9]*"' app/build/test-results/testDebugUnitTest/TEST-*.xml | awk -F'"' '{t+=$2;s+=$4;f+=$6;e+=$8} END{print "tests="t" skipped="s" failures="f" errors="e}'
  git diff --name-only main..HEAD | grep -c lockfile   # 期望 0：依赖锁零变化
  ANDROID_SERIAL=bf353dda adb install -r app/build/outputs/apk/debug/app-debug.apk
  ```
  Expected: 全绿；单测计数**只增不减、且必须按下一行的重算命令现场取数**（R-12.1：本行原稿写的「基线 ≥1499」是批 1 早期的旧数，Task 10 收口时实测已到 **217 suites / 1610 tests / 0 失败 / 0 跳过**；按 Global Constraints #16，计划与报告都不得写会自漂移的绝对计数，要写就写成可重算的命令 + 当次输出）；lockfile 未改；真机装上后**手工试用**（真机只 `install -r`，绝不 `am instrument`）。批 1 时页头/入口行尚未接入业务屏，真机可看的是底导与设置页既有形状 + 主题三态（此时设置页尚无主题开关，Task 19 接入）。**图标不在本步真机核验范围**（新字形尚未接进任何业务屏），图标的第一印象归 Step 4b 的模拟器截图与清单；真机上第一次看到新图标是批 2 收口（Task 18 Step 1）。

  本步的人工核验清单（Task 3/4 复评遗留的三项「只有真机能判」的风险，逐条写进报告，缺项不得放行）：
  1. **行高**：六档 `AppType` 不写 `lineHeight`，装配后 M3 每角色的库内行高消失，正文变紧。判「太紧」即触发批 2 在装配点补 `lineHeight`，并先把取值写进 spec §2.2（不得只改代码）。
  2. **排印换档的整体观感**：全站 `MaterialTheme.typography.*` 读点集体按 R3 映射换档（`titleLarge` 22→24、`titleMedium` 14→17、`bodySmall` 12→13、`labelLarge` 14→12、`titleSmall` 14→15），逐屏看有没有被撑破或挤扁的头部。
  3. **M3 控件默认色**：`ColorScheme` 装配后所有 M3 控件默认色由库紫转薄荷，另加两处形状改动（Card 圆角 20→18dp、`colorScheme.outline` 由库默认换成 `text2` 使描边变深），确认可接受。
  4. **深色与 `fontScale 2.0`**：切系统深色走查一遍底导与设置页（本批次唯一可用的深色入口就是跟随系统），大字号下标题与卡片不裁。

- [ ] **Step 6: 提交 + 推送 + 核验**

  ```bash
  git add <逐个文件>            # 组件两份 + 棘轮测试 + 验证文档 + 台账
  git -c user.name=steveliuyan -c user.email=2021250388@qq.com commit \
    -m "feat(ui-components): 词行与发音药片收口批1，组件唯一性棘轮与 AC-R 证据文档上线"
  git -c credential.interactive=false push origin stage-5-ui-r1-design-system
  git -c credential.interactive=false ls-remote origin stage-5-ui-r1-design-system
  ```
  Expected: `ls-remote` 哈希 == `git rev-parse HEAD`。

  **R-12.2（控制者裁决，压过本步的推送命令）**：用户在批 1 执行期间明确说过「**不用合 main，先停在分支上**」，且本仓规矩是推送属需授权动作。所以本步的 `push origin stage-5-ui-r1-design-system` **不是执行者的自主动作**：提交照做，推送**当场请示用户**，得到明确同意后才推；未获同意就在报告里写「已提交未推送，等授权」，并把 `git rev-parse HEAD` 记进去。合 main 一律不在本计划范围内（批 1/2/3 全部停在各自分支）。若获准推送，仍按 Global Constraints #15 用 `ls-remote` 与 HEAD 比对核验，未核验不得宣称已备份；禁 force push、禁删远端分支。

---

## 批 2 · 核心学习路径五屏（Task 13–18）

分支：`stage-5-ui-r2-learning-path`（**从批 1 分支 `stage-5-ui-r1-design-system` 的当前 tip 切出，不是从 `main`** —— 用户裁决「不用合 main，先停在分支上」，R-12.2）。
批 2 只碰四屏 + 学习壳（裁决补 1）：词书选取 / 今日计划 / 词卡 / 词卡详情 + `AppScreen`/`AppBottomBar`。每屏任务的固定收尾三步：**① `grep -n 'testTag'` 本屏并把清单贴进报告；② 迁完本屏静态 `AppPalette.`/`DomainColors.` 读点并递减 `DarkTokenConsumptionRatchetTest` seed；③ connected 跑本屏既有用例类**（类名以现场 `ls app/src/androidTest/.../ui` 为准，不猜）。

**批 2 开工判据（批 1 终审 F7 / D-I-1，控制者侧登记）：`SharedComponentUniquenessRatchetTest` 族 1/2/4 的判定面各带一个「本文件调了共享组件即整体合规」出口，接线后它同时是一只失明面——同一屏残留的私有 forbidden 形态会整体隐形。所以删四族 seed 行的判据是「该屏 forbidden 读点清零」（`AppScreenHeader` 那族 = 本屏 `MaterialTheme.typography.headline` 读点为 0；提示条族 = `AppPalette.ErrorText|colorScheme.error` 读点为 0；词行族 = 顶层私有 `*Row*` 窗口内「lemma 词头 Text + 第二枚 Text」为 0），绝不是「补一行 `AppScreenHeader(` 就准删」。语义由 `family_compliance_exits_are_a_declared_known_blindness_surface` 双向钉住；改成「每屏读点计数」的收紧属计划修订事项，批 2 不做。**

---

### Task 12.5（批 2 前置，用户 2026-10-19 拍板）: 全站静态容器读点一次性改读 `appColors()`，先让深色整体可用

**为什么排在批 2 最前面（不是缺陷，是半迁移态）**：批 1 Task 3 把 `Mint*` 中性令牌改成组合期读取
（`MintPalette.kt:58` `MintPrimaryDark` = `LocalAppColors.current.text1`），**墨色侧已经跟主题走**；
而业务屏的容器仍写 `AppPalette.Surface`（`AppPalette.kt:12` 静态 `Color(0xFFFFFFFF)`），**不跟主题走**。
真机 `bf353dda` 深色档实勘（`docs/verification/ui-redesign-2026-10.md` 末节「真机深色实勘」）：
今日计划屏「今日学习进度」卡呈**白底 + 主题浅墨**，三行文案几乎看不清。逐屏迁（原计划）会让这个状态一直持续到 Task 14/16，
用户拍板改为**先一次性把容器面补齐，再逐屏排版面**。

**Files:**
- Modify: 本任务的**权威清单是棘轮自己的采样**，不是裸 `grep`：`grep -rln 'AppPalette\.\(Surface\|Background\|TextPrimary\|TextSecondary\|Separator\)' app/src/main/java/com/example/englishlearning/ui | sort`
      实跑列出 9 个文件、44 处，但其中 `theme/MintPalette.kt:32` 与 `components/ScreenUnavailable.kt:42` 是**注释**、
      `theme/AppType.kt:12-17` 是装配路径经 `neutralForTheme()` 摘色的**源值**（`AppType.kt:30`），三类都不该算进迁移面。
      所以动谁、删谁，以 Step 2 那次 RED 运行点名的 `unseeded` 文件与 `DarkTokenSeedAllowlist` 在册站点为准。
- Modify: `app/src/test/java/com/example/englishlearning/ui/DarkTokenConsumptionRatchetTest.kt`（`DarkTokenSeedAllowlist` 相应行**只删本次真迁掉的**）。
- Test: `app/src/androidTest/java/com/example/englishlearning/ui/ThemeModeSwitchTest.kt`（既有类内增一条代表屏像素用例；类名现场核实）。

**Interfaces:**
- Consumes: `appColors()`（批 1 Task 2 交付的 `LocalAppColors` 读取口）、`AppColorLight`/`AppColorDark` 的 `surface`/`background`/`text1`/`text2`/`outlineSoft` 字段。
- Produces: 无新公共 API；本任务只把**读点**从静态源换成组合期源，并让深色 seed 台账递减。

**边界（越界即评审打回）**：
- **不碰 `AppPalette.GlassFill` 四个消费点**（`ui/components/glass/GlassDialog.kt:46`、`ui/components/glass/GlassOverlay.kt:69`、
  `ui/AiProfileSettingsScreen.kt:499`、`ui/SpeechSettingsScreen.kt:234`）——玻璃容器必须与其面板内前景墨色**同批**迁移（AC-R-11 口径），
  归批 3「深色清零专项」；本任务只迁**不透明中性容器与中性文字读点**。
- **不碰主题不变蒙版族**（`DarkTokenConsumptionRatchetTest` 的蒙版色台账在册站点），它们深浅两套都合法。
- **不改任何版面**：不重排 modifier 链、不换组件、不动间距。版面改动归 Task 13–18。

- [ ] **Step 1（RED）**: 先跑一次门禁确认基线绿（命令见 `docs/verification/ui-redesign-2026-10.md`「门禁与基线」，含 `:app:testDebugUnitTest --rerun`）。
- [ ] **Step 2（RED）**: 把本批要迁的文件的 `DarkTokenSeedAllowlist` 行**删掉**，跑 `:app:testDebugUnitTest --rerun --tests '*DarkTokenConsumptionRatchetTest*'`。
      期望 **FAIL**：报 `unseeded`（读点还在、seed 已撤），逐条点名本任务要迁的站点。这一步是「先看到红」，不许跳过。
- [ ] **Step 3（RED→GREEN 的行为面）**: 在 `ThemeModeSwitchTest` 增一条代表屏用例：`ThemeMode.Dark` 下取今日计划屏进度卡容器像素，
      断言其亮度落在深色档（沿用该类既有的像素取样与阈值口径，别新造判据）；先跑到 **FAIL**（当前是白底）。
- [ ] **Step 4（GREEN）**: 逐文件把静态中性容器/文字读点改读 `appColors()`（`@Composable` 站点内取，`DrawScope`/字段初始化器这类组合期外站点
      先在组合期读好再带入 —— 批 1 Task 3 在 `CardDetailScreen.kt` 已确立该写法）。重跑 Step 2/3 的两条，期望 **PASS**。
- [ ] **Step 5**: 全量门禁（五元组只增不减、lockfile 零变化）+ 真机 `bf353dda` `adb install -r` 里程碑包，
      系统深色下逐屏走查（**只观察，不跑 instrument、不装测试包**），截图留本机忽略区不入库。
- [ ] **Step 6**: 提交（`fix(ui-theme): 业务屏中性容器改读 appColors()，深色不再出现白底配浅墨`）+
      把「哪些文件的 seed 行已删、删了几行」写进 `.superpowers/sdd/2026-10-17-ui-redesign/progress.md`（**报告里不写绝对余量**，以台账与重算命令为准）。

**修复轮记录（R-12.5-F，2026-10-19 独立评审 spec FAIL / quality FAIL 后据其 最小返工清单 落地；先改本节再实施，AGENTS.md「范围变化先更新 Spec/brief」）**：

- **本任务的立论前提被证伪了一半**：原文第 3–5 行称「批 1 已把墨色侧改成组合期读取，容器补齐即整体可用」，这对
  `MintPalette.kt` 的动态 getter 与摘色后的 `MaterialTheme.typography.*` 成立，对 `style = AppType.*` **直读不成立**
  （`theme/AppType.kt:12-17` 六档仍钉 `AppPalette.TextPrimary/TextSecondary`，摘色只发生在装配路径 `neutralForTheme()`）。
  于是「只迁容器」在四个位置造出深色档 1.0–3.0:1 的**反向不可读**。裁决口径：**配得上就同批改墨，配不上就把容器回退**，
  两种都不动版面；回退项连同理由交给批 2 Task 14（按钮族）与批 3（`GlassFill` 面板族）。
- **新增判据（I-5）**：`app/src/test/java/com/example/englishlearning/ui/ContainerInkPairingContractTest.kt`
  —— 顶层函数段内既有 `appColors().surface` 容器读点、又有无 `color =` 的 `Text(style = AppType.*)` 即判红；
  含合成正/反夹具防空转。该判据不声明覆盖全部配对缺陷（域色 `deep` 墨与玻璃面板前景仍由同批理由台账与批 3 负责）。
- **C-1 前修**：`AiProfileSettingsScreen.kt` 的 `AiProfileRow` 三处排印点补显式 `color = appColors().text1/text2`
  （浅套取值与 `AppType` 原静态墨逐字相同 → 浅色像素零变化）。
- **C-2 回退**：同文件 `VoiceOverlayOption` 的前景回退 `AppPalette.TextPrimary`——它渲染在未迁的 `GlassOverlay`
  白玻璃面板里；理由并入 `DarkTokenConsumptionRatchetTest.GlassFillBackdropDebtLedger` 的该屏条目。
- **I-1 整族回退**：`components/glass/GlassButton.kt` 的 `PillButton` 四处读点（禁用墨 / 描边 / Secondary 容器 / 禁用底）
  全部回退基线静态值。它在 `accent: DomainAccent` 静态色对形参上无法就地改读 `appDomain(...)`，配对必须连着签名改，
  归 Task 14（R-14.1）；下游 7+ 个屏本轮不该被动改色。
- **I-2 分治**：今日计划屏「阅读文章」按钮前景改 `appDomain(AppDomain.Reading).deep`（浅套与 `DomainColors.Reading.deep`
  逐字同值，由域色双钉用例保证 → 浅色零变化）；「查看打卡与成就」按钮容器回退 `AppPalette.Surface`
  （其棕色前景 `#795B36` 是在册裸 hex、无可逐字等价的动态对应值，`RawColorSeedSiteLines` 同步回退）。
- **权威清单偏离（I-3）**：原文按三类排除算出的真迁面是 35 处，实迁 34 处。差的 `ui/AiLearningScreen.kt:177`
  （`AppPalette.TextSecondary`，存量 `0a6d63ac`）**判定为不该迁**：它压在 `AiLearningScreen.kt:168` 未迁的
  `Color.White.copy(alpha = 0.85f)` 白药片上，单独迁墨会复刻 C-2。本节即该收窄的正式偏离说明，
  同屏另有存量缺陷 `AiLearningScreen.kt:156/161`（品牌渐变上白字）结转批 3。
- **Step 5 走查面（I-4）**：一级四 tab 不足以验收本任务，必须补走「AI 服务列表 / 编辑 / 试听浮层」与
  `PillButton` Secondary 的下游屏（生图、阅读入口、拍照翻译、例句分析、语音设置、AI 问答、不可用屏）。
- **I-4 走查结果（2026-10-19，真机 `bf353dda` 深色档，截图只落本地忽略区 `qa-scratch/`，一律不入库）**：
  补走 13 屏（AI 服务列表 / 编辑 / 试听浮层 + 生图 / 阅读入口 / 拍照翻译 / 例句分析 / 语音设置 / AI 问答 /
  今日计划 / 词书 / 词卡 / 首页）。**本任务动过的每一对容器-墨都可读**；仍可辨识但**存量**不可读的两族被再次实拍确认：
  ①`AppType.*` 直读的静态浅套墨（语音设置屏页头「语音合成」+ 四个区块标题最刺眼，该屏仅此一族约 25 处）；
  ②`AppPalette.GlassFill` 白玻璃面板压主题浅墨（四处在册站点）。另实拍到 `AiLearningScreen.kt:156/162`
  品牌渐变上的白字（I-3 已登记）。裁决：**不在本轮扩面**——把 `AppType` 中心摘色会让未迁的白玻璃面板
  当场翻成「浅墨压白底」，正是 AC-R-11 禁的那一侧；摘色必须与 `GlassFill` 批同批做，已写入批 3 账。
- **connected 取样点（I-5 后半）**：`ThemeModeSwitchTest` 由 3 例增至 4 例，新增
  `the_ai_profile_row_paints_theme_ink_on_the_migrated_container_in_dark_mode`——同一行内先取容器众数像素
  （必须等于深套 `surface`，容差 2），再取标题墨核像素（压容器必须 ≥ 正文 AA 4.5:1）。
  两条坑都实测踩过并写进了注释：`captureToImage()` 的位图在节点界外是**透明**像素，不筛 alpha 就会把
  0x000000 当墨核；按文案取样会命中 `clickable` 合并语义后的**整行**，位图里混进副标题那档 `text2`
  （实测 7.54:1 假绿），必须 `useUnmergedTree = true` 才拿得到那一段标题字。
  判据有效性按 RED/GREEN 双向实跑：删掉 `AiProfileRow` 标题的显式 `color` → 墨核 `0x1B1B1E`、比值 1.023 → 红；
  恢复 → 4 例全绿（emulator-5574）。静态判据 `ContainerInkPairingContractTest` 同一 Mutation 下报 2 例红
  （全量真文件例 + 新增的真形状例），4 例中另 2 例是合成夹具。
- **m-1 到 m-7 逐条处置**：
  m-1 **修**：像素容差 24 → 2（`captureToImage` 是离屏位图、不经显示链，实测逐通道差 0），并加自检③
  「容差必须容不下深套相邻填充档」（`background` 11 / `tint` 12 / `outlineSoft` 16，门槛 4）——
  原口径下「容器漏画、透出页底」会被判成通过，这条把该盲区钉成判据。
  m-2 **修**：自检消息与算式同名（写的是「最大逐通道间距」，算的是 `maxChannelDelta`）。
  m-3 **修（改判据，不改字体档）**：`composeRule` 没有钉 `fontScale` 的公开口子（`setDensity` 只管密度），
  故按「默认档为判据、非默认档允许判红并转人工」写进 KDoc，另加自检②「取样像素必须离该套正文墨 ≥32 档」
  作为「量到的是容器而不是字身」的硬判据（门槛不再挂钩容差，否则容差收到 2 后这条就没判据了）。
  m-4 **修**：`tonalElevation` 与 `surfaceTint = set.onMint` 的前提、以及「≥3dp 时必须改用
  `surfaceColorAtElevation` 重算而不是放大容差」都写进了注释。
  m-5 **修（文档）**：报告 §3 补一行说明 Step 2 的 RED 形态被替换成更强的「旧生产码 + 递减 seed」双向绑定形态。
  m-6 **登记交批 2**：`CheckInScreen` 零完成空柱 `appColors().outlineSoft` 压 `MintSurface` 卡底 1.21:1
  （深套）/1.06:1（浅套）——弱描边档按定义就是低对比，属 token 意图；是否把图表「轨道」换成 `outline`(#2E4238, 1.6:1)
  或 `tint3`，由 Task 15（打卡屏版面）裁决。
  m-7 **登记收口时处置**：`.superpowers/` 台账目录在 `.gitignore` 内，AGENTS.md「阶段文档推送」在文档层落不了地；
  批 2 收口时一并决定（要么把计划/评审记录转成 `docs/handoff/` 入库件，要么在验证文档里记这条口径张力）。

**窄复评记录（R-12.5-R1，2026-10-19，范围 = `a4b5bbf..a50eabe` 三条提交）**：

- 复评独立复算了取值等价（浅套 `text1/text2` ≡ `AppPalette.TextPrimary/TextSecondary`、
  `DomainLight[Reading].deep` ≡ `DomainColors.Reading.deep`）、seed 台账（`AiProfileSettingsScreen` 28 /
  `GlassButton` 3→7 / `TodayPlanScreen` 5 净 0）、对比度（变异墨核 `0x1B1B1E` 压 `0x17201C` ≈1.026:1）
  与三条自检的间距数值（11/12/16 与 210/136），结论与报告数字自洽。
- **七条 finding 全判 ADDRESSED**，无新增 Critical/Important：C-1/C-2/I-1/I-2 按「配得上前修、配不上回退 + 登记」
  实质消灭本轮新造的深色不可读对且浅色逐字节等价；I-3/I-4 以偏离说明 + 口径订正收口；I-6 登记批 3。
  复评同时确认裁决未被越界执行（`AppType.kt` 与四处 `GlassFill` 消费点未动）也未执行不彻底（无逐字等价对应物的站点都走了回退）。
- 复评点名的两处「差一小半」予以登记，不再起第二轮：I-3 的机检理由表行（该站点是 `Color.White.copy` 白药片、
  不是 `GlassFill`，塞进那张按文件键的表语义不符，理由落在本节散文即正式落点）；
  I-5 的第二枚 connected 取样点（试听浮层已随 C-2 回退成静态白玻璃前景，「迁移容器-墨配对」的像素点不再存在）。
- 三条新增 Minor 的处置：**①注释前提写反**（把「`Card` 默认 `tonalElevation` 1dp」当成不混色的原因）——**当场修**，
  注释改为实测口径：这张卡没显式传 `tonalElevation`（`TodayPlanScreen.kt:168-175` 只给 colors/shape/modifier），
  叠加 alpha 为 0、取样逐通道差实测 0；并写明混色通道没被关掉（主题把 M3 `surface` 也设成同一枚 `set.surface`，
  `isSurfaceColor` 按数值成立），故加 `tonalElevation` 到 ≥3dp 必须改用 `surfaceColorAtElevation` 重算。
  **②静态判据对 `val set = appColors()` 间接写法失明**（只匹配字面量 `appColors().surface`）——**结转批 3**：
  与 `AppType` 摘色同批把谓词扩到 `set.surface` / `colors.surface`，届时全树一起收敛，现在扩会漏报变误报。
  **③同族处置不一致**（页头两枚标题 `:133/:301` 因走查实证 1.05:1 被补色，紧邻副标题 `:134` 留在原地 ≈3.6:1）——
  **结转批 3**，与 `MintBackground` 上的 `AppType` 直读一族（全树未补色直读 88 处）一起摘色；
  本轮不给它单独补色：判据的容器谓词只盯 `appColors().surface`，页底一族没有任何自动化保护，
  补一枚就欠一枚，半吊子扩面比统一留给批 3 更糟。

---

- [ ] **Step 1（每屏通用）: 先跑该屏 connected 基线**，记录改动前通过数；改动后必须全绿。

---

### Task 13: 词书选取屏（`VocabularyScreen` 词书列表 / 首页入口）

**Files:**
- Modify: `app/src/main/java/com/example/englishlearning/ui/AppScreen.kt`（`LearningSetupScreen` 段与其私有组件，见 R-13.0 的行号清单）
- Create: `app/src/main/java/com/example/englishlearning/ui/components/Skeleton.kt`
- Test: `app/src/androidTest/.../ui/SetupScreenScreenshotTest.kt`、`WordBookSwipeRowTest.kt`（既有，须全绿）+ 新增 `WordbookPickerHeaderTest.kt`

**Interfaces:**
- Consumes: `AppScreenHeader`、`EntryRow`、`Skeleton`（本任务新建 `ui/components/Skeleton.kt`）。
- Produces: `@Composable fun ListSkeleton(rows: Int, rowHeight: Dp, modifier: Modifier = Modifier)`（骨架高度预留，spec §3 与 F5-03 移交账「每册骨架」）。

**开工前裁决（R-13.0，控制者现场核码后据 AGENTS.md「范围变化先改计划再实施」写入；实施者按本节执行，不再自行判断落点）**：

- **落点澄清**：标题里的 `VocabularyScreen` 是**生词本列表**（`ui/VocabularyScreen.kt`，页头文案「生词本」），不是词书选取屏，
  本任务不动它。词书选取屏的真实落点是 `ui/AppScreen.kt` 的 `LearningSetupScreen`（`:1851` 起）：
  页头两行排印 `:1950-1960`、`SetupIntroductionCard`（`:2059`）、首帧骨架 `WordBookListPlaceholder`（`:2098`）
  + `PlaceholderBar`（`:2138`）+ 共用版面常量 `WORD_BOOK_PLACEHOLDER_ROWS` / `WordBookCardShape` /
  `WordBookCardMinHeight` / `WordBookCardContentPadding`（`:2077-2085`）、词书行 `SwipableWordBookCard`（`:2154`）。
  connected 侧既有类是 `SetupScreenScreenshotTest`、`WordBookSwipeRowTest`。
- **「每册行换 `EntryRow`」按字面执行不可行，改判**：`EntryRow`（`components/EntryRow.kt:111-122`）的契约只有
  `icon/domain/title/subtitle/trailing/showChevron/topDivider/onClick`，**没有选中态、进度条、滑动删除，
  也没有 trailing 组合槽**。硬换会删掉「当前选中哪套词书」「已学/总数 + 进度条」「删除词书的
  `customActions`」这些业务信息与无障碍语义，撞 Global Constraints #3（不动业务规则）与 #9（既有
  `testTag`/`contentDescription`/`stateDescription` 不得改名或删除）。
  裁决：词书行**保留 `SwipableWordBookCard` 的结构与语义**，本任务对它只做三件事——
  ①容器与前景按 `appColors()` **同批配对**迁移（浅色档取值必须逐字等价；判据是新增的
  `ContainerInkPairingContractTest`：同一段里容器读了 `appColors().surface`，段内 `Text(style = AppType.*)`
  就必须带显式 `color =`）；②册与册之间的 `Arrangement.spacedBy` 幽灵间距换成真实 `appColors().outlineSoft`
  分隔线（spec §2.3 禁令）；③复验触控下限（该行现 72dp 下限，不得因换分隔线掉到 48dp 以下）。
  `EntryRow` 若在本屏无处可放（`SetupIntroductionCard` 里没有纯跳转入口），就**不出现**，
  报告里写一句「本屏无符合 `EntryRow` 契约的入口行」即可，不为了用组件而改业务信息层级。
- **页头换 `AppScreenHeader` 的边界**：`AppScreenHeader` 的 `onBack` 位写死 `contentDescription = "返回"`
  （`components/AppScreenHeader.kt:83-89`，裁决 R-9.1），而本屏的返回入口是 `← 返回上一层`
  （`:1941-1948`）——那句中性表述是既有决策（该屏既能从「学习」栏也能从「设置」栏打开），
  改名会撞 Global Constraints #9。裁决：用 `AppScreenHeader(title, subtitle, onBack = null)` 承载
  标题与副标题两档排印，**返回位保留现有 `TextButton`**；不给组件新增 `backContentDescription` 形参
  （那属批 1 组件契约变更，需要自己的用例与评审）。
- **骨架共享化的既有偏差必须原样带走**：`WordBookCardShape` 是 18dp 而非 `AppShape.Card` 的 20dp，
  注释（`:2080-2082`）写明理由：滑动删除底、描边与卡片必须**同角**（卡身与删除底共用同一 `shape`
  才是约束本身；`WordBookSwipeRowTest` 钉的是「静止时不露红底」，两者一起改成 20dp 它照样绿——
  别把它当成 18dp 这个具体数值的守卫）。所以 `ListSkeleton` 的圆角/行高/内边距走**形参**，
  不许在组件里写死成 `AppShape.Card`；该屏继续传 18dp 那组参数，并**保留既有
  `testTag("wordbook_placeholder_$row")`**（connected 判据依赖它）。骨架保持零文案零数字
  （F1-01 无障碍口径：占位期播报「已学 0 词」比什么都不念更糟）。


- [ ] **Step 1（先红）: 写失败测试**（新增 `app/src/androidTest/.../ui/WordbookPickerHeaderTest.kt`，一个 `@Test` 只 `setContent` 一次）：
  ①页头由 `AppScreenHeader` 承载——`testTag("app_screen_header")` 存在，标题节点文案「选好词书，开始今天的积累」、
  副标题节点文案「每天一点点，学习会自然变成习惯。」；返回入口仍是现有 `← 返回上一层`（`contentDescription` 原样）。
  ②首帧骨架：`wordbook_placeholder_0..2` 三行齐全，每行实测高度 ≥ `WordBookCardMinHeight`（72dp）。
  **不**断言骨架与真实卡逐像素同高——真卡带进度条约 130dp、骨架按最小行高约 74dp，是 `:2088-2096`
  注释里写明并刻意保留的取舍（首帧要「立刻有东西」，不是复刻终版面），把它判成同高等于推翻既有裁决。
  ③册与册之间必须是**真实分隔线**而不只是 `spacedBy` 空白：取相邻两卡之间那条缝的中点像素，
  按当前主题档断言它等于 `appColors().outlineSoft`（浅套 `#E4EFE9`（`AppColors.kt:52`，
  控制者订正：本行原写 `#E5EFE9` 是笔误，测试取 token 不取 hex）/ 深套 `#233029`，容差与
  `ThemeModeSwitchTest` 同为逐通道 2），并写明变异证据——把画线调用删掉本条必须变红。
  取样按 `captureToImage()` + alpha ≥250 过滤（界外是透明像素，不筛就会把 0x000000 当成命中的颜色）。
- [ ] **Step 2: 实现**：按 R-13.0 落地——页头换 `AppScreenHeader(title, subtitle, onBack = null)`（返回位保留）；
  把 `WordBookListPlaceholder` / `PlaceholderBar` 的版面参数抽进新建 `ui/components/Skeleton.kt` 的
  `ListSkeleton(rows: Int, rowHeight: Dp, shape: Shape, contentPadding: PaddingValues, modifier: Modifier = Modifier)`
  （圆角/行高/内边距走形参，不写死 `AppShape.Card`；沿用 18dp 那组参数与 `wordbook_placeholder_$row` tag；零文案零数字）；
  词书行保留 `SwipableWordBookCard` 结构与语义，只做「容器-墨同批配对迁移 + 幽灵间距换真实 `outlineSoft` 分隔线 + 触控下限复验」；
  容器迁 `appColors().surface` 时同段内每处 `Text(style = AppType.*)` 必须补显式 `color = appColors().text1/text2`
  （`ContainerInkPairingContractTest` 会当场挡），且 `DarkTokenConsumptionRatchetTest` / `RawColorLiteralRatchetTest`
  的 seed 台账按实际迁移点同步。`EntryRow` 在本屏没有符合其契约的纯跳转入口时**不出现**，理由写进报告。
- [ ] **Step 3: 门控 + 本屏 connected + 提交**（`feat(ui-learning): 词书选取屏接入统一页头与入口行并预留骨架高度`）。
  门控三任务（`assembleDebug → testDebugUnitTest → compileDebugAndroidTestKotlin`，本机 gradle 8.13-bin 路径见
  `android-repo-session-handoff`，注意其 glob 少了哈希目录，实测用
  `$HOME/.gradle/wrapper/dists/gradle-8.13-bin/5xuhj0ry160q40clulazy9h7d/gradle-8.13/bin/gradle.bat`）；
  connected 只走 `adb install -r` + `am instrument`（`emulator-5574`），
  跑 `WordbookPickerHeaderTest` + 既有 `SetupScreenScreenshotTest` + `WordBookSwipeRowTest`，全绿才算收口。

**修复轮裁决（R-13.1，控制者据独立评审 `dab01a2`  findings 写入；AGENTS.md「范围变化先改计划」）**：

- **C-1（Critical）本任务范围扩到 `ProfileCreationScreen` 页头**：`dab01a2` 从
  `SharedComponentUniquenessRatchetTest` 页头族 seed 删了 `"AppScreen.kt"` 一行，但 `AppScreen.kt:1777`
  仍有 `MaterialTheme.typography.headlineSmall` 读点——族判据是「读点存在 **且** 本文件没调
  `AppScreenHeader(`」，`:1955` 接线后整个 `AppScreen.kt` 对这族永久隐形，正是批 2 开工判据
  （本文 `:1303`）明文禁止的「补一行组件就准删 seed」。裁决走**清零**路线：同批把
  `ProfileCreationScreen` 的两行裸页头换成 `AppScreenHeader(title = "先认识一下你", subtitle = "填写一个称呼，学习记录只保存在这台设备上。", onBack = null)`
  （`AppScreenHeader.kt:105-118` 前景为 `colors.text1` / `colors.text2`，与 `MintPrimaryDark`/`MintTextMuted`
  同源同值，浅套逐字不变；字号字重按统一组件变，属预期）。不采「补一枚点名读点数的锚」方案——
  那等于给批 3 留一件要拆的东西，且批 2 开工判据已写明收紧属计划修订、批 2 不做。**收口判据**：
  `grep -c "MaterialTheme.typography.headline" app/src/main/java/com/example/englishlearning/ui/AppScreen.kt` == 0。
- **I-1 `ContainerInkPairingContractTest` 对别名形失明**：`MigratedNeutralContainer` 只认字面
  `appColors().surface`，本轮容器全走 `val colors = appColors()` + `colors.surface`，判据对本次交付一枪未发。
  裁决：**先测再定**——扩谓词到别名形后跑真实扫描，绿则保留并补合成正反片段；红（批次外存量站点）则回退扩展
  并把命中清单登记进批 3 账（本任务无权给别人的屏还债）。两种结果都必须把报告里「规避正则……通过」
  改成事实描述：**判据抓不到 ≠ 契约放行**。
- **I-2 浅色等价证据指错测试**：`SetupScreenScreenshotTest` 只做节点存在断言 + 落 PNG 供人看，
  无基线比对，不能当像素守卫。真证据链是 `MintPalette.kt:42-72`（`Mint*` 本就是 `LocalAppColors` getter 桥，
  迁移是纯改名，浅/深两档逐字不变）+ `AppColors.kt:49-50`（`tint2`/`tint3` 与被删两枚裸 hex 逐字相同）。
- **登记不修**：m-5（无 connected 用例钉「屏级 wiring → 骨架」这条 loading 分支）、
  `AppScreen.kt` 余 2 处裸 hex（`:2383` Switch / `:2427` Slider）与 3 处 `AppPalette.OnMintPrimary`
  （`:1824/:1846/:2028`）——评审确认**不迁才对**：`:2028` 压在保持静态的 `AppleMintGradient` 上，
  迁 `appColors().onMint`（深套 `#DFF6EC` 浅墨）会立刻造出「浅墨压浅渐变」不可读对。

**修复轮复评补记（R-13.2，窄复评判定 C-1/I-1/I-2/m-1/2/3/4/6 全 ADDRESSED，修复轮关闭）**：

- **口径不对称必须写明**：R-13.1 那条收口判据 `grep -c "MaterialTheme.typography.headline"` 是**含注释**的粗口径，
  而棘轮与新增的机检门 `removing_a_file_from_the_header_seed_requires_its_headline_reads_to_be_cleared`
  都按**剥注释**口径（`uiScreenSource()` → `stripComments`）判定。**权威判据是那道 JVM 门，不是这条 grep**；
  注释里逐字复述该串会计债于 grep、不会判红。谁将来把两者当同一件事，会误判「门漏了」。
- **`ProfileCreationScreen` 换页头组件的连带版面变化**（裁决只点名了字号字重）：标题↔副标题间隙
  由列节奏 `spacedBy(14.dp)` 变成组件内 `padding(top = 2.dp)`，并引入 `heightIn(min = 48.dp)`。
  色值两档逐字不变（`MintPrimaryDark`/`MintTextMuted` 与 `colors.text1`/`text2` 是同一枚 getter 桥的两侧）。
  **批 2 收口（Task 18）目检确认这一屏观感**，不留到批 3。
- **新门的意图**：`wiredRootScreens.isNotEmpty()` 防空转断言意味着「把 `AppScreen.kt` 放回页头 seed」
  在批 2 后续屏接线之前会当场判红（响亮失败）。批 3 若有执行者把这当 bug 拆掉，等于拆掉 F7 失明面的封堵——
  本条即意图说明。
- 复评另发现实现者自报的 `R-13.2` 悬空编号（`AppScreen.kt:1954-1955` 引用了当时还不存在的裁决号）已由控制者
  就地改指 `R-13.0`，纯注释、逻辑零变化；报告 §八 的「230 insertions」实为 233（报告不入 git，仅记账）。
- 复评独立补验了首启屏暴露面：`AppScreenTest` connected **OK (45 tests)**（实现者未跑该类）。



---

### Task 14: 今日计划屏（AC-R-20：8 个整宽按钮 → 1 CTA + 入口行组）

**Files:**
- Modify: `app/src/main/java/com/example/englishlearning/ui/TodayPlanScreen.kt`
- Test: `app/src/androidTest/java/com/example/englishlearning/ui/TodayPlanScreenTest.kt`（既有类内增用例；类名现场核实）

**Interfaces:**
- Consumes: `EntryRow`、`InlineNotice`、`AppScreenHeader`。
- Produces: 今日数字单一权威呈现（`AppType.Display` 一处），其余数字降为 `Footnote` 且不带强调色。

- [ ] **Step 1: 写失败测试**：Ready 态下整宽主按钮数 == 1，入口行数 ≥3；断言「今日新词/到期数」在屏幕上只出现一处 `Display` 尺寸节点。
- [ ] **Step 2: 实现**：主 CTA 唯一（继续学习），其余动作换 `EntryRow`；`TodayPlanScreen.kt:160-161/247/343-344` 五处裸 hex（`#68BDA6`/`#C7A47D`/`#795B36`）token 化并**从 `RawColorLiteralRatchetTest` seed 删除对应条目**；Workload 三档色改取域色（复习 `Review` 系）。
  **硬项（Task 9 评审裁决 4.3 前置②，不是「顺带看」）**：`PillButton` 的静态 ink 债必须在本任务裁决并落地——Secondary 档容器读 `AppPalette.Surface`（浅套白）、文字读 `accent.deep`（默认 `DomainColors.AiSpeech.deep` = `#188F76`）、描边读 `AppPalette.Separator`，深套下整屏浮白药片（压白 4.02:1 / 压深套 surface 4.15:1，数字与推论见 Task 9 登记）。同批一起处理稿按钮族档位差与「白字压域色」，修法走**改 `PillButton` 本体**（Task 12 棘轮已预登记豁免该路径），消费面含 `ScreenUnavailable` 的动作位。
  **R-14.1（控制者预算好的修法，别再让执行者现场试色）**：上面那句「同批处理白字压域色」没给可过的配方，本条补齐。全部数字为控制者按 WCAG 相对亮度实算（同一套线性化，脚本可重算），`DomainColors` 七域的 `base`/`deep` 取自 `AppPalette.kt:47-55`。
  **① Primary 非品牌域（`style == Primary && accent != AiSpeech`）：白字压 `base→deep` 渐变，亮端全线不合规。** 白 on `base`：AiSpeech 2.110 / Learn 3.647 / Review 2.056 / Library 3.524 / Reading 3.521 / Danger 3.547 / Settings 3.261——七域**无一**过 4.5，最好也只有 3.647。白 on `deep`：Learn 6.424 / Review 5.128 / Library 6.300 / Reading 5.630 / Danger 7.200 / Settings 9.125，全过（AiSpeech 走品牌渐变分支，不经此路）。
  **根因是渐变跨度**：`base→deep` 太宽，任何单一前景都守不住两端——深字（`text1` #1C1C1E）在 base 端 4.665~8.277 全过，却在 deep 端掉到 1.865~4.237 全崩；白字正好相反。所以**不是换前景色能解决的，必须收窄容器**。
  裁决：容器改成**只往深走**——纯 `accent.deep`，或 `deep → lerp(deep, Color.Black, 0.15f)` 的渐变（暗端实测白字 6.597~10.838，只会更好）。**禁止把 `base` 端留在白字容器里**。前景保持白色（`Color.White` 是 Compose 具名常量，不是 `Color(0x…)` 裸字面量，不撞 `RawColorLiteralRatchetTest`）；这里容器是**主题无关**的饱和域色，前景也必须主题无关，所以**不要**改成 `set.surface`（深套会翻成 #17201C，白字变深字，deep 端只有 1.827~3.250，反而全线崩）。品牌分支现状已合规（`OnMintPrimary` #174C44 压三端 5.373 / 5.887 / 7.577），不动。
  **② Text/Secondary 档（前景 = 域色、容器 = surface）：深套全线不合规，浅套有一处不合规。** `accent.deep` 压浅套 surface(#FFFFFF)：AiSpeech **4.016（不过）** / Learn 6.424 / Review 5.128 / Library 6.300 / Reading 5.630 / Danger 7.200 / Settings 9.125；`accent.deep` 压深套 surface(#17201C)：1.827~4.151，**七域全不过**（这正是 Task 9 登记的 4.02/4.15 那两枚，本条已复算确认）。`accent.base` 压深套 surface：AiSpeech 7.899 / Learn 4.570 / Review 8.109 / Library 4.731 / Reading 4.734 / Danger 4.699 / Settings 5.112，**全过**。
  裁决：这一档的前景**按套分叉**——浅套 `accent.deep`、深套 `accent.base`（容器/描边一并从 `AppPalette.Surface`/`AppPalette.Separator` 迁到 `appColors()`，否则 `DarkTokenConsumptionRatchetTest` 的静态读点计数不减）。唯一残口是 **AiSpeech 在浅套的 4.016**：先 `grep -rn 'PillStyle.Text\|PillStyle.Secondary' app/src/main/java` 核实是否真有调用点以 AiSpeech（默认值！）走这两档——**有**则该处前景改 `set.onMint`（#174C44 压 #FFFFFF = 9.751，实测），**没有**就不为它造例外分支，只在测试注释里记这条边界。注意 `accent` 形参默认值就是 `DomainColors.AiSpeech`，所以「没人显式传 accent」的调用点全都落在这个残口上，grep 时要按「未显式传 accent」来数，不能只搜 `AiSpeech` 字样。
  **③ 用例**：新增 JVM 对比度契约（仿 `EntryRowInkTest`），对七域 × 两套 × 三档逐一断 ≥4.5，并钉两条**会被拒的候选**：`ratioOf(Color.White, DomainColors.Review.base) < 4.5`（2.056，白字压 base 的代表）与 `ratioOf(DomainColors.AiSpeech.deep, AppColorLight.surface) < 4.5`（4.016，Secondary 浅套残口）。改 `PillButton` 本体属 Task 12 棘轮预登记豁免路径，不得为绕门另造一枚按钮。

- [ ] **Step 3: 规则零改动自检**：解锁条件、快照不可变性相关代码一行不动；跑该屏业务单测（`grep -rln TodayPlan app/src/test` 全跑）。

- [ ] **Step 4: 门控 + connected + 提交**（`feat(ui-learning): 今日计划收成单一主按钮与入口行，今日数字一处权威`）。

**拆席裁决（R-14.2，控制者据首任实现者中断现场写入；AGENTS.md「范围变化先改计划」）**：

首任实现者在 150 轮上限处中断，现场只留下一份未入库的 JVM 契约草案
`app/src/test/.../ui/TodayPlanNumberAuthorityTest.kt`（254 行，6 条用例 + 一条防空转自证；生产码零改动，
`git status` 除该文件外干净）。复盘结论：**Task 14 是两个可独立评审的面**——
①共享组件 `PillButton` 的三档配对（消费面 15 个文件、波及 11 屏 connected）；
②`TodayPlanScreen` 一屏的版面收成与 token 清扫（该屏现存量：裸 hex 5 处、`Mint*` 读点 40、
`FontWeight.` 12、`MaterialTheme.typography.` 11、`Button(` 10）。一席吞下两头必然超轮次预算。
裁决：**拆成 Task 14A / Task 14B，各走一遍实现 → 独立评审 → 至多一轮修复 → 一次窄复评**。

- **顺序 14B → 14A**（不是 14A 优先）。理由很实际：14B 的 RED 判据就是那份草案，
  它必须在 14B 收尾时转绿，否则 `testDebugUnitTest` 全量跑不动；若先做 14A，
  就得把这份未入库草案挪出源码树暂存，多一道「测试文件搬家」的不可追溯动作。
  先 14B 还有一条产品侧理由：`TodayPlanScreen.kt:202` 那枚 `PillButton` 走默认 accent（品牌渐变分支，
  R-14.1 ① 判定现状已合规），所以本屏不依赖 14A 就能深色可用；14A 随后统一改组件本体，
  把包括这枚 CTA 在内的全部消费面一起复算。
- **Task 14B 范围**：`TodayPlanScreen.kt` 版面收成 1 主 CTA + 入口行组、今日数字单一权威、
  五处裸 hex 与 Workload/计数卡色点改域色、该屏 `Mint*`/`FontWeight.`/`MaterialTheme.typography.` 清扫、
  seed 同步递减；**不碰** `GlassButton.kt`/`PillButton` 本体。既有判据草案可修订，
  但**每条修订必须在报告里写明理由**（草案与 brief 冲突时以 brief + R-14.1 为准）。
- **Task 14A 范围**：`components/glass/GlassButton.kt` 三档按 R-14.1 落地（Primary 非品牌域容器收窄到
  `accent.deep` 或 `deep→lerp(deep,Black,0.15f)`；Text/Secondary 前景按套分叉 浅 `deep`/深 `base`；
  容器与描边从 `AppPalette.Surface`/`Separator` 迁 `appColors()`）+ 新增七域 × 两套 × 三档 JVM 对比度契约
  （仿 `EntryRowInkTest`，含 brief ③ 点名两条「会被拒的候选」）+ accent 默认值残口取证
  （按「未显式传 accent」数，不能只搜 `AiSpeech` 字样）+ 15 个消费文件的 connected 复跑。
- **`today_plan_progress_summary` 那张卡的三条保命项**（14B 重构本屏时必须保住，`ThemeModeSwitchTest`
  的深色像素取样依赖它）：tag 名不变、容器继续读 `appColors().surface`、**不得**新增显式 `tonalElevation`
  （主题把 `surfaceTint` 设成 `set.onMint`，混色通道是开着的，加到 ≥3dp 就把逐通道容差 2 撑破）。
  这条口径写在 `ThemeModeSwitchTest.kt` 的 KDoc 里（提交 `0dd83bb`），别当注释忽略。

**14B 独立评审后的三条裁决（R-14.3，控制者；评审席 `task-14b-review-1.md` 判 2 Critical + 7 Important）**：

- **裁决一：AC-R-20 的「数字权威」判据必须防两条真实退路，已在 14B 修复轮 1 落地。**
  原草案只数 `AppType.Display` 次数 + 逐个固定 tag 取样窗口，评审席用变异证明：把 tag 上提到包裹容器
  会让「向前找最近 `Text(`」的取样器**假绿**（旧取样器在 M1 变异下 6 tests / 0 failures），
  而新增一枚自带新 tag 的 `Headline` 数字两道门都不红。改法定为：取样器改成**全量枚举 + 归属判定**
  （命中 0 处 / >1 处一律红），另补**全文件**门 `AppType.(Display|Headline) == 1` 且 `AppType.Title == 1`。
  connected 侧的高度代理原用「行高不足一半」，实测 420dpi/fontScale 1.0 下 Display 105px、Title 52px
  ——`52×2=104 < 105` 恰好放过 Title 回退（零余量），改成 `<= H×0.45`（Footnote 实测 0.381、Label 0.352）。
  复评席又用新变异打穿一次：`AppType.Footnote.copy(fontSize = 22.sp)` 这类**间接升档**不动任何
  `AppType.*` 字面量，上面三道计数门全盲（实测 6 tests 全绿）。控制者当场补第四道门——本屏
  `fontSize|lineHeight|TextStyle(|.merge(` 读点计数必须为 0，RED 取证：临时给明细行加
  `.copy(fontSize = 22.sp)` → 该用例红（`red-n1.log`），撤掉后全量门控 **225/1666/0/0/0** 复绿。
  **普适口径（批 2 后续四屏同样适用）**：「一处权威尺寸」是**全文件级**判据，不是逐 tag 判据；
  按 tag 取样必须保证「tag 属于那枚 `Text(` 的实参」，否则等价于没判；
  凡判「字号档」的门都必须同时封掉 `copy(fontSize=…)`/`TextStyle(…)`/`merge` 这三条间接升档路径，
  否则计数门只是字面量门。
- **裁决二：入口行组不得改变既有出口的可见性集合。** 14B 把原 `SearchEntry`（在 `when (state)` **之外**，
  Loading/缺配置/不可用三态都可见）收进只在 Ready 分支渲染的 `TodayPlanEntryGroup`，评审席判为真实行为回归。
  控制者裁决的**主依据不是可达性论证，而是授权范围**：AC-R-20 说的是「8 枚整宽按钮 → 1 CTA + 入口行组」，
  常驻搜索条从来不是那 8 枚之一，把它收进 Ready 分支属**超出本任务授权的静默行为变更**；
  版面轮只许改呈现，不许改「某个出口在哪些态出现」。裁决：查词条目回到页头之后的独立常驻卡，组内剩 6 行。
  加重理由（口径已按复评席取证订正）：`onOpenSearch` 只接线于 `AppScreen.kt:1545`（置位在 `:1551`），底导四枚
  （`AppTab.kt`：学习/阅读/AI 学/设置）**没有查词位**，所以它是**学习 tab 内的唯一直达查词入口**——
  但**不是全应用唯一出口**：词典另有两处开启点（阅读页点未知词 `AppScreen.kt:1387`、
  拍照翻译页 `AppScreen.kt:1259`），故「非 Ready 态整体不可达」的说法不成立，真实后果是
  「学习 tab 内查词退化为必须先切 tab」。这条口径对批 2/批 3 通用：**收成组之前，先列出被收物件的可见性集合，
  组必须与原集合逐态相同**；若版面确需把常驻物件下移，只许「组本身常驻 + 组内容随态变化」，
  不许把常驻出口降级为条件呈现。
  审计（`docs/design/2026-10-17-content-placement-audit.md` §5.2）的「查词条移出页首」在 14B **不执行**，
  归批 3 与「删两条切 tab 入口」一并处理；观感（常驻卡与入口组连排的节奏）归 **Task 18 目检**
  （连同 Task 13 结转的 `ProfileCreationScreen` 页头 14dp→2dp 一起看）。


- **裁决三：与逐屏内容审计的两项差值登记在案，归批 3 收口，不在 14B 追改。**
  审计（`docs/design/2026-10-17-content-placement-audit.md`）建议而 14B **没有**做的两件事：
  ① 屏内那两条「切到别的 tab」型入口没有删（14B 只把目录型动作收进入口行组，tab 切换语义原样保留）；
  ② 「调整词书与目标」没有按审计建议压进汇总卡，仍作为入口组末位独立行。
  两者都不影响解锁语义与数据，属于版面归属判断；差值记在这里而不是回改，是为了让 14B 的评审边界
  停在「AC-R-20 + 行为回归」，不扩成版面复审。**批 3 兑现时会连带触发** `TodayPlanScreenTest`
  那条可点集合闭合断言（现值 9）——那是预期的红，改测试即可，别当成 AC-R-20 回退。

- **14A 开工前必读**：本轮已确认 `appDomain(...)` 域色在两套里有独立取值
  （`AppColors.kt:95` 浅 Learn `base #0A84FF / deep #075EB4`；`:106` 深 Learn `#5AC0FF` 两端同值），
  14A 算 Text/Secondary 档「深套前景改读 `accent.base`」时，**深套的 base/deep 是同一个值**这条会直接改变结论，
  按 `AppColors.kt` 的深套实值重算，别照 R-14.1 里引的浅套 `AppPalette` 数字推。

**R-14.4（控制者按深套实值复算后，对 R-14.1 的订正；14A-i 派发前写入，执行者照本条不走回头路）**：

- **`PillButton` 必须改签名**（`accent: DomainAccent` → `domain: AppDomain = AppDomain.AiSpeech`，组件内读 `appDomain(domain)` + `appColors()` + `LocalIsDark.current`）。
  理由：深套 `base == deep` 且是**亮色**（`DomainDark` 七枚 `#35E0AC`/`#5AC0FF`/`#FFB340`/`#DA8CFF`/`#FF7A93`/`#FF6B61`/`#B0B0B5`），
  R-14.1 ①「前景保持白色、不许改 `set.surface`」那句是**拿浅套静态 `DomainColors` 推的**，成立前提是容器为浅套深值。
  改读 `appDomain()` 后深套容器是亮色，白字压它 1.692~2.790 全线崩，而 `set.surface` 压它 5.975~9.850 全线过 ⇒
  **B 档前景按套分叉：浅套 `Color.White`、深套 `appColors().surface`**（不是 `set.text1`，深套 `text1 = #E9F2EC` 是亮墨，压亮容器只有 1.5~2.4）。
- **R-14.1 ② 的「浅 `deep` / 深 `base`」分叉塌成一条**：深套 `base` 与 `deep` 同值，所以落地公式统一 `palette.deep`，
  唯一的例外仍是浅套 AiSpeech（`#188F76` 压 `#FFFFFF` 4.016）→ 取 `set.onMint`（9.751 / 压浅套 background 9.220）。
  残口取证按「未显式传 accent」数：控制者括号配平解析 36 枚调用点（不含定义行）得 20 显式 / 16 默认，
  Secondary|Text 档的 AiSpeech 站点共 **8 处**（默认 3：`SpeechSettingsScreen.kt:148/197`、`components/ScreenUnavailable.kt:88`；
  显式 5：`AiProfileSettingsScreen.kt:120/234/250/288/356`）⇒ 例外分支必须真落地，不能只在测试注释里记边界。
- **禁用态一并主题化**（本席把 `AppPalette.TextSecondary`/`Surface`/`Separator` 三枚静态读点清掉）：
  前景 `set.text2`、Primary 容器 `set.tint2`、Secondary 容器 `set.surface` + 描边 `set.outlineSoft`。
  复算 4.146（浅 text2/tint2）~8.375（深 text2/background）；WCAG 1.4.3 豁免正文 4.5，契约按 **≥3.0** 断（不写 4.1——浅套只剩 0.046 余量，
  与 14B 那条零余量高度代理同病）。
- **归属债（结转批 3 Task 26 Step 3）**：品牌分支前景 `AppPalette.OnMintPrimary` 是**刻意主题无关**的读点
  （品牌渐变两套同三段，深套 `onMint = #DFF6EC` 压薄荷渐变会塌），14A 后它成为 `GlassButton.kt` 唯一 `AppPalette.` 读点、seed 留 1。
  它既不是蒙版成员（`ThemeInvariantMaskReadLedger` 按 `{GlassHighlight, Scrim}` 成员名分流，塞进去会污染那条合成探针用例），
  也不是形参默认值（`ParameterDefaultStaticReadLedger` 的 PillButton 条目随签名改造消失），
  所以批 3 收口时二选一：把品牌三端 + 这枚墨一起搬成 `theme/MintPalette.kt` 的 `internal val`（棘轮看不见 theme/ 内的裸 hex 与 `AppPalette.`），
  或为「与主题无关的容器-墨对」新开一张逐站点注记表。**14A 不许现场造第三张表。**

**14A 关闭记录与订正（R-14A-i.1 / R-14A-ii.1，控制者；提交链 `42e9c14`..`edacaae`）**：

- **14A-i**（`42e9c14`+`51cf5f5`）：评审席变异 M3 证明源码门「只守表不守读表」——修复轮补组件体 ink 五成员
  消费点判据（正向四条 + 反向 `Color.<命名色>`/`BrandMintGradientStops`），M3 现同点必红。
  **R-14A-i.1**：复评实证反向判据漏拦 `Color(r,g,b)` 浮点构造器，扩面并入 14A-ii 落地（现 `PillButtonInkTest.kt:334` 拦全部 `Color(`）。
  实测订正一条书写坑：`stripComments` 剥注释后计数，**KDoc 内嵌被钉形状不撞门**（此前「注释写全名撞红」口径因果写反，作废）。
- **14A-ii**（`d4baf26`+`edacaae`）：三条设备取色用例上桩、评审席两发动码变异证明有牙（`onMint` 例外回退→残口用例红；
  壳写死 `isDark=false`→knockout 用例红）；connected 14 类/176 用例/0 失败。
  **R-14A-ii.1（历史订正）**：旧逐位 `== 0xFFE5E5EA` 断言由 `0a6d63a` 引入、**在其时确为绿**
  （禁用底经 `f245874` 定义的 `AppPalette.Separator = Color(0xFFE5E5EA)` 真实渲染过）；`bc256fd` 改 token `#E4EFE9`、
  `42e9c14` 禁用底改接 `tint2` 后逐位等值必红。正确口径是「**token 迁移未回写测试的陈旧断言**」，
  而非派发词里流传的「生产从未渲染过该值」（全称历史断言必须当场 `git log -p` 取证，不得二手引用）。
  后果评估：无——陈旧判定、基线取证在前处置、token 容差判据均不受影响。
- 结转：`Text` 档设备侧无独立取色用例、`PhotoTranslationScreen` 无 connected 类 → Task 18 目检清单；
  `<=18.0` 容差浅套 tint 族不可分辨（plan-mandated，负向断言兜底）与取样块四处逐字重复 → 终复审 triage。


---

### Task 15: 词卡屏（回忆-揭晓流程 + 退出停播）

**Files:**
- Modify: `app/src/main/java/com/example/englishlearning/ui/WordCardScreen.kt`
- Test: 既有 `WordCard*` connected 类

**Interfaces:**
- Consumes: `SpeakerButton`（替 `WordCardScreen.kt:410-425/427-471/280-301` 三套私有播放件）。
- Produces: `LaunchedEffect` 停播点（退出浮层/离开屏时 `stop()`），F5-03 移交账「退出学习不停播」在此关闭。

- [ ] **Step 1: 写失败测试**：`DisposableEffect(onDispose)` 路径上断言播放器收到 `stop`（JVM 侧用假播放器断言调用次数；connected 侧断言退出后无语播焦点）。
- [ ] **Step 2: 实现**：播放控件换 `SpeakerButton`；`testTag` 与 `contentDescription` 沿用 `speakerContentDescription` 一处定义；揭晓焦点顺序保持 F5-03 已建的语义顺序不动。
- [ ] **Step 3: 大字号护栏**：`fontScale 2.0` 下词卡主词与释义完整可见（`CheckInScreenTest` 口径）。
- [ ] **Step 4: 门控 + connected + 提交**（`fix(ui-learning): 词卡退出即停播并统一发音药片`）。

---

### Task 16: 词卡详情屏（`CardDetailScreen`）

**Files:**
- Modify: `app/src/main/java/com/example/englishlearning/ui/CardDetailScreen.kt`（其私有播放件 `:539-568/:570-616/:345-366` 删除，换 `SpeakerButton`）
- Test: 既有 connected 类 + 宿主传参清单核验

**Interfaces:**
- Consumes: `AppScreenHeader`（详情层是带返回的二级层，必须用页头）、`WordRow`（例句/派生词行）。
- Produces: 两套同构几何消除（spec §3 喇叭药片行的「两套」记账）。

- [ ] **Step 1: 写失败测试**：`grep -c "SpeakerButton(" CardDetailScreen.kt == 1` 且文件内不再出现私有 `PlayPill`/`SpeakerPill` 一类重复实现（以现场实际私有函数名为准）。
- [ ] **Step 2: 实现** + **现场核实 spec §10.2 未核实项**：「另 4 个宿主的传参」逐一列出（`grep -rn "CardDetailScreen(" app/src/main/java`），任一宿主缺参即在此补，并把核实结果写进报告（不进验收标准，但必须留痕）。
- [ ] **Step 3: 门控 + connected + 提交**。

---

### Task 17: 学习壳与底部导航（duotone 图标替换自绘 Canvas）

**Files:**
- Modify: `app/src/main/java/com/example/englishlearning/ui/AppBottomBar.kt`
- Modify: `app/src/main/java/com/example/englishlearning/ui/AppScreen.kt`（页头/浮层容器统一，2424 行文件只做**版式接入**，不做宿主拆分）
- Test: `app/src/androidTest/java/com/example/englishlearning/ui/AppBottomBarTest.kt`（若既有的 `AppTabTest` 是 JVM 测试则保持不动）

**Interfaces:**
- Consumes: `AppGlyph`、`IconBadge`、`appDomain(AppDomain)`。
- Produces: 四 tab 图标改 duotone 字形（学习 `Book` / 阅读 `Newspaper` / AI `Spark` / 设置 `Gear`），`drawTabGlyph` 自绘路径与 `drawSparkle` 删除。

- [ ] **Step 1: 写失败测试**：底导 `Canvas` 自绘字形数 == 0（`AppBottomBar.kt` 不含 `drawTabGlyph`），四 tab 仍各有 `testTag("app_tab_*")` + `Role.Tab` + `stateDescription`；`AppTabTest` 零修改通过。
- [ ] **Step 2: 实现**：选中态用 `InlineIconBadge`/双色强调（选中 `deep` + 加粗标签，未选中 `text2`），保持 `heightIn(min = 64.dp)` + `navigationBarsPadding()` 不动（F5-03 Task 11 成果）。
- [ ] **Step 3: 底导对比断言**：`text2` 未选中态压 `MintSurface` 底 ≥4.5 —— 若底仍是 `MintSurface`（白）则 5.15 合格；把这条写进 `IconBadgeContrastContractTest` 或新 `AppBottomBarContrastTest`。
- [ ] **Step 4: 门控 + connected（含底导走查 + TalkBack 朗读四 tab）+ 提交**。

**R-17.1（控制者拆席裁决，沿用 R-14.2 口径；AGENTS.md「范围变化先改计划」）**：本任务两面可独立评审——
**17A**＝`AppBottomBar.kt`（156 行自包含）duotone 重写 + Step 1/3 判据 + 底导 connected（含 TalkBack 四 tab 朗读）；
**17B**＝`AppScreen.kt`（2447 行）页头/浮层容器版式接入（不做宿主拆分）。一席吞两面必超 150 轮预算
（Task 14/15/16 三连证），顺序取 17A 先：17B 不依赖 17A 的渲染细节但 connected 底导走查要在最终形态上做，
且 17B 现场要顺带核底导接入点。各自走完整循环（实现→独立评审→至多一轮修复→窄复评）。
17B 派发前由控制者现场清点「页头/浮层容器统一」的具体落点清单，避免大文件漫游。

**R-17B.1（控制者清点 + 路由裁决，落点清单见 `.superpowers/sdd/2026-10-17-ui-redesign/task-17b-scope-audit.md`）**：
现场实测把 Task 17 Step 2 那句「页头/浮层容器统一」拆成可执行的三件事，并推翻了控制者自己的两版误记：

- **页头＝零落点**：`AppScreen.kt` 的页头已在批 1/批 2 接入共享 `components/AppScreenHeader.kt:67`
  （调用点 `:1782`、`:1956`），且 `SharedComponentUniquenessRatchetTest:533/559` 已把该文件 headline 读点数钉为 0。
  17B 再新增页头或另造标题即越界。
- **浮层＝真落点，且分两路**：`AppScreen.kt` 内 6 枚裸 `AlertDialog(`（`:647/:1325/:1608/:1702/:1873/:1913`）。
  房内有两枚共享浮层档，各管一路：**路 A** `components/ConfirmDestructiveDialog.kt:219`（批 1 Task 11 建档后
  **消费者实数 0**，正文配方强制「级联 → 保留面 → 不可撤销」三要素、按钮走 `dialogButtonInk` 三档表）收 4 枚破坏性确认
  （`:647` 清空搜索历史 / `:1325` 删除这一版 / `:1702` 删除全部学习数据 / `:1873` 删除词书）；
  **路 B** `glass/GlassDialog.kt:31` + `PillButton` 按钮对（房内形状样板 `ReadingAccessScreen.kt:291-322`）收 2 枚非破坏选择
  （`:1608` 导出词书选择器 / `:1913` 迁移二选一）。
  理由：`ConfirmDestructiveDialog.kt:47-53` **逐字登记**了「刻意不复用 `GlassDialog`」——`AppPalette.GlassFill` 是静态浅套值，
  深套墨字压上去读不出来；把破坏性确认迁到玻璃档等于绕开 R-11.1 的墨色裁决。
- **迁移自由度取证**：8 枚站点私有 `testTag` 与旧措辞在 `androidTest`/JVM 中零引用（当场 grep），故 tag 收敛到组件固定
  `confirm_destructive_*` 与「此操作不可撤销。」固定句不破坏任何用例；反面事实是这 6 枚浮层 **connected 覆盖为 0**，
  故 17B 必须自建设备侧背书（不止编译门）。
- **组件本体冻结**：不许为迁站点给 `ConfirmDestructiveDialog` 加参数（撞 Task 11 份数/几何棘轮）；塞不进去就停下回报。
- **色债边界**：顺带可摘只有裸 hex 2 枚（`:2392/:2436`，`RawColorLiteralRatchetTest:56` seed 2 + `:76/:77` 在册，摘则 seed 与册同减）；
  `AppPalette.OnMintPrimary` 3 处（= DarkToken seed `"AppScreen.kt" to 3`）归批 3 Task 26 Step 3；`Mint*` 族 66 处、
  m-4 的 17/22dp 尺寸差、`AppType`/`GlassFill` 均不进 17B。
- **诚实留痕**：本清点先后有两版错误（首版记成 `Modifier.surface(` 5 处 + `AppColorLight` 硬编码，实测两者皆 0；
  次版把 6 枚一律路由 `GlassDialog`，被组件 KDoc 的登记偏离推翻），均在派发前被自查推翻重做。
  口径升级：**清点即取证**，未经当场 grep 的结论不得写进派发词（与 R-14A-ii.1 同族）。

---

### Task 18: 批 2 收口

- [ ] **Step 1:** 门禁全绿 + 基线重算 + `DarkTokenConsumptionRatchetTest` seed 递减确认 + 里程碑装真机手工试用五屏。
- [ ] **Step 2:** 更新 `docs/verification/ui-redesign-2026-10.md`：AC-R-07（本批覆盖的屏）、AC-R-20（今日计划）证据行；真机截图存本地忽略区（不入库）。
- [ ] **Step 3:** 按 `docs/handoff/REPORT-TEMPLATE.md` 写批 2 报告；提交 + 推送 + `ls-remote` 核验。

**R-18-R1（批 2 全分支终审 Critical 的处置裁决，用户拍板「同批迁容器」）**：终审六席（A 拆 A1/A2）坐实一条 Critical——
`AppPalette.GlassFill` 的四枚容器消费点在深色下仍是写死的 94% 白，面板内无 `color=` 的静态档排印点是「深墨压白玻璃」，
两套主题各抄一遍同一个值；`DarkTokenConsumptionRatchetTest` 的 `GlassFillBackdropDebtLedger` 把「批 1 不迁」的理由登记为
「容器填充必须与面板内前景墨色同批迁，否则深色下由意外可读翻成当场不可读」，而这个「同批」一直悬在批 3。

- **决定**：把 AC-R-11 的迁移侧从批 3 提前到批 2 收口——四枚容器填充迁 `appColors().surfaceGlass`，**面板内前景同批迁**，
  两处逐类 connected 深色取样 + 浅套像素腿同时立。
- **依据（可重算，浅套侧像素零变化）**：`AppColorLight.surfaceGlass = 0xEFFFFFFF` 与 `AppPalette.GlassFill` 逐字相同；
  `TextPrimary`↔`text1`（`0xFF1C1C1E`）、`Surface`↔`surface`（`0xFFFFFFFF`）、`Separator`↔`outlineSoft`（`0xFFE4EFE9`）、
  `TextSecondary`↔`text2`（`0xFF4E756A`）四对同源 ⇒ 迁移只解锁深色档，浅套逐值不变。
- **代价与约束**：① `GlassHighlight` / `Scrim` 属主题不变蒙版族（`ThemeInvariantMaskMembers`）**不迁**，不随本轮扩表；
  ② 每迁一处 `GlassFillBackdropDebtLedger` 删行 + `DarkTokenSeedAllowlist` 同文件计数减 1，两张表同减、谁单独漂移都会红，
  台账清空后必须留合成探针防「空表即空闸」；③ 破坏性浮层的路由仍按 `R-11.1` / `R-17B.1`，不为迁色改路由。
- **落地**：修复轮 A `2c08792`（`GlassDialog` + 八枚消费者 16 处前景 + `PillButton` 可点 role），修复轮 B `ce1e188`
  （`GlassOverlay` / `AiProfileSettingsScreen.SectionCard` / `SpeechSettingsScreen.PreviewCard` 三块面板同批迁）。
  `app/src/main` 里 `AppPalette.GlassFill` 实读点归零。
- **附带裁决（正文档不得按装饰档放行）**：浅套 `AiSpeech.deep`（`#188F76`）压玻璃合成实底，B 席报 4.01:1 并按非正文 3.1 钉进取样类
  （`GlassPanelThemeInkTest` 的 `DECOR_AA_RATIO`）。控制者一度复算成 ≈3.61（压近白）/≈3.19（压 `0xEFFFFFFF` 合成底）并据此驳回席报——
  **那是控制者算错，席报的 4.01 与实测像素 `#FEFFFE` 都对**，该条驳回理由作废（`srgbToLinear(0x18,0x8F,0x76)` 的 L ≈ 0.2114 ⇒ `1.05/0.2614 = 4.016`）。
  **但裁决本身成立，理由换成另一条**：那两行是 `style = AppType.Body` 正文档，阈值线就是 4.5，4.006 不过线，
  把它标成装饰档即「为过判据放宽判据」。修法照房内先例 `AppBottomBar.kt:95`（17A 修复轮 `0ed3984`：字形腿走 `deep`、
  标签文字腿浅套走 `onMint`）按套归位——浅 `#174C44` ≈9.727:1、深 `#35E0AC` 压 `#161F1B` ≈9.960:1，
  `DECOR_AA_RATIO` 常量随之删除，并留「修复前那枚墨喂 4.5 闸必须判红」的反空转腿。
  浅套这一处观感会变（密钥态与浮层选中行由薄荷绿墨变深墨），列入 18-ii 用户目检。

---

## 批 3 · 其余屏与入口分组重排（Task 19–26）

分支：`stage-5-ui-r3-surfaces-and-ia`（批 2 合入 `main` 后切出）。
批 3 承载信息架构六条裁决中的 ①②③④⑤⑥ 与 F5-03 移交账剩余项。
**入口迁移铁律（spec §6，违反即判失败）**：先建新入口，验证可达且 `testTag`/朗读描述齐全，**再**摘旧入口。收藏与错词在「我的词」可用之前绝不能先从打卡页删掉——因此 Task 20 必须早于 Task 22。

### 批 3 深色清零专项（批 1 终审 D-I-3 增补：Task 19–26 共同的硬项，Task 26 Step 3 的收口判据）

批 1 只交付了深色**能力**（两套 token + 装配点 + 主题三态活链路），业务屏的静态浅套读点一处没迁——line 119 那句
「批 2/批 3 每屏顺手迁完自己那屏」从本专项起有了明确的族划分与收口判据，Task 26 Step 3 按本节验收，**不是「顺手清理」**。专项的权威台账是 `DarkTokenConsumptionRatchetTest.DarkTokenSeedAllowlist`（`Map<相对 ui 根路径, 命中数>`，
双向精确相等），**批 1 tip 实测 32 个文件 / 157 处命中**（可重算：
`sed -n '/internal val DarkTokenSeedAllowlist/,/^)/p' app/src/test/java/com/example/englishlearning/ui/DarkTokenConsumptionRatchetTest.kt | grep -oE '"[^"]+" to [0-9]+' | awk -F'to ' '{s+=$2; n++} END{print "files="n" hits="s}'`）。
**文档里不写死站点数**：本会话内两份人工盘点就得出过 88 与 93 两个数（差在 `GlassButton.kt` 的主题不变蒙版色归类口径），
这正是「抄来的数会漂」的现场证据——要数就现跑上面那条，或在测试里读 seed。

拆成三族，各族的收法不同，**不许只迁填充不迁墨**：

1. **排印墨色族**（深色下「当场不可读」的主因）：`theme/AppType.kt` 六档里写死的 `AppPalette.TextPrimary`/`TextSecondary`
   与散落在屏里的 `AppPalette.Text*` 读点。收法 = 批 3 的 `appTypography()` 摘色版（装配点按 `LocalAppColors` 出六档），
   或逐点改读 `appColors().text1/text2`。**这条与视觉稿 §2.2 的六档锁不冲突**：摘的是 `color=`，字号/字重/字距一字不动。
2. **玻璃容器族**：四处 `AppPalette.GlassFill` 站点（`components/glass/GlassDialog.kt:46`、`components/glass/GlassOverlay.kt:69`、
   `AiProfileSettingsScreen.kt:499`、`SpeechSettingsScreen.kt:234`，可重算 `grep -rn 'AppPalette\.GlassFill' app/src/main/java --include=*.kt`，
   `ConfirmDestructiveDialog.kt:45` 是 KDoc 不是站点）**必须与其面板内的前景墨同一次提交迁移**——批 1 终审 F4 的裁决，
   只改填充会把「深墨压不透明白板 = 意外可读」变成「深墨压深板 = 当场不可读」，是净退化。
   目标值 `appColors().surfaceGlass`。收完后 `GlassFillBackdropDebtLedger` 与 seed **两张表同减**，单漂移即红。
3. **主题不变蒙版族**：`AppPalette.GlassHighlight` / `Scrim` 共 5 文件 6 处（`ThemeInvariantMaskReadLedger`）——**不迁**，
   它们是「白或黑 + alpha」的蒙版，深浅两套都合法；批 3 收口时这族留在台账里并写明理由，即兑现 line 119 的
   「seed 清零或仅剩确实与主题无关、已逐条登记理由的站点」。

每迁一屏的固定动作：减该文件 seed（减到 0 才删行）→ 跑门禁 → 在浅/深两套下截图（AC-R-10/11 的逐屏证据面）→
`AC-R-11` 那四处一并收口（`docs/verification/ui-redesign-2026-10.md` 的 AC-R-11 行现在就是这四个站点）。
**禁止**用「先 `rm -rf` 再整表重抄」的方式清 seed：seed 是债务台账，重抄等于抹掉「只减不增」的历史。

---

### Task 19: 设置页 4 组 9 行 + 主题三态开关（AC-R-21）

**Files:**
- Modify: `app/src/main/java/com/example/englishlearning/ui/SettingsScreen.kt`
- Test: `app/src/androidTest/java/com/example/englishlearning/ui/SettingsScreenTest.kt`（既有类内增用例）

**Interfaces:**
- Consumes: `EntryRow`（9 行统一件）、`AppThemePreference`（Task 5）、`InlineNotice`（替 `SettingsScreen.kt:269-279/:323-333` 两份私有提示）。
- Produces: `@Composable fun ThemeModeRow(mode: ThemeMode, onSelect: (ThemeMode) -> Unit)`；设置分组数据 `object SettingsGroups { val all: List<SettingsGroup> }`（4 组，总 9 行）。

- [ ] **Step 1: 写失败测试（connected + 源码契约双轨）**
  connected：分组数 == 4、行数 == 9、每一行都可点（`onAllNodes(hasClickAction())` 计数 ≥ 9，且不存在「看起来是行却不可点」的节点）、存在三态主题选择且切换后根底色改变（复用 Task 5 的像素断言口径）。
  源码契约（JVM，`app/src/test/java/.../ui/SettingsNoDeveloperJargonTest.kt`）：
  ```kotlin
  @Test fun settings_screen_speaks_user_language() {
      val src = stripComments(File(SETTINGS_FILE).readText())
      listOf("F4-04", "AI Profile", "Salvage", "salvage").forEach { term ->
          assertFalse(src.contains(term), "设置页出现开发者术语「$term」（AC-R-21 要求用户语言）")
      }
  }
  @Test fun no_placeholder_next_version_rows() {
      val src = stripComments(File(SETTINGS_FILE).readText())
      assertFalse(Regex("""后续版本|敬请期待|暂未开放""").containsMatchIn(src),
          "设置页不得保留「后续版本」占位行（裁决②）")
  }
  ```

- [ ] **Step 2: 实现**：4 组 9 行按裁决②落位——「学习提醒」归「学习」组；删「账户」整组与两条占位行；资料卡去掉今日计划数字（统计职责归打卡页）；备份入口改单行「备份与恢复」（Task 25 落实现）。每行 `EntryRow` + 域色（学习 `Learn`、复习 `Review`、词库 `Library`、阅读 `Reading`、AI `AiSpeech`、设置 `Settings`、危险 `Danger`）。
- [ ] **Step 3: 主题开关接线**：选中即 `AppThemePreferences.write` + 状态回读（`rememberAppThemeMode()` 的 listener 使界面即时切换，无需重启）。
- [ ] **Step 4: 迁完本屏静态令牌读点 + 门控 + connected + 提交**（`feat(ui-settings): 设置页收成四组九行并上线主题三态`）。

---

### Task 20: 词汇域三入口（查词 / 词表 / 我的词，裁决①）

**Files:**
- Create: `app/src/main/java/com/example/englishlearning/ui/MyWordsScreen.kt`（收藏 · 生词本 · 错词 = 一页三段）
- Modify: `app/src/main/java/com/example/englishlearning/ui/AppScreen.kt`（新增入口 + 挂载，**不摘旧入口**）
- Modify: `VocabularyScreen.kt`、`WordBookWordsScreen.kt`、`WrongWordsScreen.kt`、`FavoritesScreen.kt`（行实现换 `WordRow`）
- Test: `app/src/androidTest/java/com/example/englishlearning/ui/MyWordsScreenTest.kt`

**Interfaces:**
- Consumes: `WordRow`、`AppScreenHeader`、`EntryRow`、`ScreenUnavailable`。
- Produces: `enum class MyWordsSection { Favorites, Wordbook, Wrong }`；`@Composable fun MyWordsScreen(section: MyWordsSection = MyWordsSection.Favorites, …)`；三段各自 `testTag("my_words_section_<name>")`。

- [ ] **Step 1（风险前置，spec §10.1）: 评估宿主拆分**
  `MyWordsScreen` 要让三个 ViewModel 同宿主并存。**先跑一次最小可行性探针**：一个测试方法内 `setContent { MyWordsScreen(...) }` 且三个 `hiltViewModel()` 并存，确认不崩、数据各自加载（同一测试内两次 `setContent` 真机必崩，故一个方法一个 `setContent`）。
  若探针失败：本任务降级为「三段用同一 ViewModel 的聚合查询端口」，并在 `docs/decisions/2026-10-19-ui-redesign-mywords-host.md` 记录降级方案与影响——**这属架构级取舍，必须先向用户请示再实施**（四件停止事项之一：接口重构影响面大）。
- [ ] **Step 2: 写失败测试**：三段各自的行渲染正确（收藏段显示收藏词、错词段显示错词）、段标题可读、空态用 `ScreenUnavailable`/空态文案、每段有一个可点进详情的出口。
- [ ] **Step 3: 实现**：新入口挂到词汇域（三入口文案「查词 / 词表 / 我的词」，参考扇贝单词的简洁三段）；旧入口**保留**，摘除动作在 Task 22 之后或本批收口时执行，并在报告里逐条列出「哪些旧入口仍在」。
  - **留名（依 `TodayPlanScreenTest.ready_groups_entry_actions_and_keeps_one_primary_cta` 的自述规程）**：本步在「今日计划」入口组新增第 7 行「我的词」（`today_plan_open_my_words`），
    该判据的闭合集合因此由 **9 → 10**（1 主 CTA + 1 阅读文章 + 1 常驻查词 + 7 条组内行），已同步改判据与消息文案。
    改动理由 = 裁决①要求的第三枚词汇域入口；「只建不摘」铁律下本屏没有减任何出口，所以是 +1 而不是替换。
    同一判据在 Task 22 摘除旧入口时还要再改一次（届时是 −1，并把这条引用过去）。
  - **留名（20-A 修复轮 1 / 评审 I-2）**：9→10 那次只同步了 connected 判据一侧，生产屏 KDoc 与 `TodayPlanNumberAuthorityTest`
    白名单留在 9/6 且未认领新标签；修复轮已把两侧对齐，并把两处自述改成**不抄绝对枚数**的口径（权威 = 判据 `rowTags` + 白名单名册）。
    **Task 22 动入口时的规程**：`TodayPlanScreenTest` 闭合集合判据、`TodayPlanNumberAuthorityTest` 白名单名册、`TodayPlanScreen` KDoc
    必须**同批**同步，改一处不改其余即判违规。
- [ ] **Step 4: `WordRow` 替换词行台账（`componentFamilySeeds["词行（应调 WordRow）"]`，批 1 出厂 6 个文件 / 7 枚裸行组合）中的本域侧**——`WordBookWordsScreen.kt:239-258`、`GlobalVocabularySearchScreen.kt:274-299/:319-358`（**一个文件两枚**）、`WrongWordsScreen.kt:110-129`、`FavoritesScreen.kt:116-156` 四个文件；同步递减 seed（seed 按文件计，四文件一次删净，剩下 `LearningRecordsScreen.kt`/`VocabularyScreen.kt` 两文件归批 3）。
- [ ] **Step 5: 门控 + connected + 提交**（`feat(ui-words): 词汇域收成查词/词表/我的词三入口`）。

---

### Task 21: 字母 Rail 真 48dp（F5-03 移交账，随词表屏）

**Files:**
- Modify: 词表/字母索引所在屏（现场以 `grep -rln "字母\|Rail\|alphabet" app/src/main/java/com/example/englishlearning/ui` 定位）
- Test: 该屏 connected 类内增 48dp 命中区用例

- [ ] **Step 1: 写失败测试**：每个字母节点的可点区（`boundsInRoot().height`）≥ 48dp，**不是**视觉高度 48；断言相邻字母命中区不重叠（否则点击落到错字母）。
- [ ] **Step 2: 实现**：`Modifier.size(48.dp)` 或 `heightIn(min = 48.dp)` + 字形 17dp 居中；Rail 改横向可滚或折叠为「A-F / G-N / O-Z」三段（信息架构级改动，版面选型以不挤压正文为准，写进报告）。
- [ ] **Step 3: 门控 + connected + 提交**（`fix(ui-words): 字母索引真 48dp 命中区`）。

---

### Task 22: 打卡页回归统计职责（裁决③，依赖 Task 20）

**Files:**
- Modify: `app/src/main/java/com/example/englishlearning/ui/CheckInScreen.kt`
- Test: 既有 `CheckInScreenTest`（护栏口径沿用，不重写）

- [ ] **Step 1: 前置检查（必做，铁律）**：确认「我的词」三入口已可达且 connected 通过（Task 20 收口）。未完成则**本任务不得开始摘除**，只允许做版面收敛。
  - **能力前置一（R-20A-fix.4，评审席 I-4 的登记面）**：摘 `today_plan_open_vocabulary` / `showVocabulary` 老路由之前，
    「我的词」生词本段必须具备旧生词页已有、且摘除后会变全站不可达的前两枚能力——段头整册「开始重新学习」、
    逐行「标记为已掌握」（带二次确认）。凭据：`MyWordsScreenTest.wordbook_offers_relearn_and_mastered_behind_confirmation`
    与 `MyWordsScreenContractTest.wordbook_section_supplies_the_capabilities_task_22_depends_on`（均已绿）。
    **若这两枚判据任一不在册或为红，本任务不得摘上述老路由**，只允许收敛版面。
    摘除时同步改**三处**，缺一即留陈旧陈述（I-2 的双向同步规程）：今日计划可点闭合集合判据（−1）、
    `TodayPlanNumberAuthorityTest` 入口标签白名单（删行）、`TodayPlanScreen.kt` KDoc 里的枚数自述（改数）；
    写死枚数一律配可重算命令，别把新数字裸抄进文档。
  - **能力前置二（R-20A-fix.3，复评席 N-1 的登记面）＝失败可见反馈**：同一批摘除前，「我的词」还必须具备旧生词页
    `markMastered` 的**结果态消费**——`Success`/`Failure` 各弹与老页逐字同文的结果对话框，且点「知道了」即复位宿主态。
    缺它不是「少一层反馈」，而是两个用户可见错态：失败静默 + 未复位的 `Success` 会在别的老页一进页面就弹出无上下文的框；
    摘除老路由后这条反馈面全站不可达。凭据（提交 `0d673ea`，均已绿）：connected
    `MyWordsScreenTest.mastery_failure_shows_the_same_result_dialog_as_the_old_page_and_resets_on_ack` /
    `mastery_success_shows_the_same_result_dialog_as_the_old_page` / `submitting_renders_no_result_dialog` /
    `idle_renders_no_result_dialog` ＋ JVM
    `MyWordsScreenContractTest.the_merged_page_consumes_and_resets_the_mastery_state`。
    **这五枚判据任一不在册或为红，同样不得摘 `today_plan_open_vocabulary` / `showVocabulary`**（或明写「本批不摘」）。
- [ ] **Step 2: 写失败测试**：打卡页不再出现裸文字链（`grep` 级契约 + UI 断言「无两条通往收藏/错词的裸文字链节点」）；三统计卡数值口径不变（F4-01 已建的统计口径零改动）。
- [ ] **Step 3: 实现**：头部两条裸文字链移出；`CheckInScreen.kt:148` 的裸 hex `#FFF1DB` token 化并从 seed 删除；`:66-73` 注释自认「大字号挤压归本轮」的头部换 `AppScreenHeader`，跑 `fontScale 2.0` 护栏（AC-R-07）。
- [ ] **Step 4: 动效不做**（spec §1 不做的事 #5）：不引入动画代码；在 `docs/verification/ui-redesign-2026-10.md` 记一行「打卡页华丽动效 = 另批，未在本轮范围」。
- [ ] **Step 5: 门控 + connected + 提交**（`feat(ui-checkin): 打卡页回归统计职责并统一页头`）。

---

### Task 23: AI 主页默认展开「现在能用」（裁决④，AC-R-23）

**Files:**
- Modify: `app/src/main/java/com/example/englishlearning/ui/AiFeatureScreen.kt`（含 `:47` 顶层 `Brush` 与 `:87` 单字徽标站点）
- Test: `app/src/androidTest/java/com/example/englishlearning/ui/AiFeatureScreenTest.kt`
- Test: `app/src/test/java/com/example/englishlearning/ui/AiBadgeContrastFixTest.kt`

**Interfaces:**
- Consumes: `EntryRow`、`AppIcon.Spark`（替「AI」文字徽标）、`NotConfiguredCard`。
- Produces: 状态徽章与 `AiFeature.implemented` 一致的渲染函数 `fun featureStatusBadge(implemented: Boolean): String`（`"现在能用"` / `"未上线"`）。

- [ ] **Step 1: 写失败测试**：默认展开态可见「选能力」区（AC-R-23）；未上线项折叠且标「未上线」；徽章文案与 `implemented` 逐条一致（`assertEquals` 遍历能力表）；**含字徽标禁令**（spec §4.2）源码契约：
  ```kotlin
  @Test fun no_letter_badges_left_in_ai_surface() {
      val src = stripComments(File(AI_FEATURE_SCREEN).readText())
      assertFalse(Regex(""""""AI"""").containsMatchIn(src), "「AI」文字徽标应换 AppIcon.Spark（spec §4.2）")
  }
  ```
- [ ] **Step 2: 修 F5-03 遗留的渐变站点**：`AiFeatureScreen.kt:87` 单字徽标白字压 `AiFeatureGradient`（实测 ≈1.29–2.40:1）换为 `AppIcon.Spark` + **`on-brand #04241C`** 前景（Task 1 裁决 #3：渐变上取 `on-brand`）。`AiBadgeContrastFixTest` 用亮度函数断言 `ratio(0xFF04241C, gradientEnd) >= 4.5`。
- [ ] **Step 3: 实现**：未上线压成一条折叠行；`AppPalette` 静态读点迁 `appColors()`；顶层 `Brush`（`:47`）保持静态（品牌渐变模式不变，Task 3 口径）。
- [ ] **Step 4: 门控 + connected（含 TalkBack 读能力名 + 状态）+ 提交**（`feat(ui-ai): AI 主页默认展开可用能力并去掉文字徽标`）。

---

### Task 24: 阅读主页版面（裁决⑤）

**Files:**
- Modify: `app/src/main/java/com/example/englishlearning/ui/ReadingAccessScreen.kt`、外刊 Feed 相关屏（现场 `grep -rln "外刊\|Feed\|ArticleSource" app/src/main/java/.../ui` 定位）
- Test: 既有阅读 connected 类

- [ ] **Step 1: 写失败测试**：主任务（今日文章）在第一屏可见（节点 `boundsInRoot().top` 落在首屏）；外刊 Feed 为带返回的独立二级层（有 `AppScreenHeader(onBack != null)`）；「AI 生成」与「换一篇」合并为一个入口（断言主 CTA 数 == 1）。
- [ ] **Step 2: 实现**：类型与长度偏好下沉「生成前参数」；`:292-294` 出站确认换 `OutboundConfirmDialog`；`ArticleReadingScreen.kt:346` 裸 hex token 化并删 seed。
- [ ] **Step 3: 不做联网检索**（spec §8：外刊多源排在 UI 轮之后独立小批）；本轮只改版面，白名单源与 `ArticleSourceRegistry` 零改动。
- [ ] **Step 4: 门控 + connected + 提交**（`feat(ui-reading): 阅读主页主任务上提并把外刊 feed 改独立二级层`）。

---

### Task 25: 三个备份门合一（裁决⑥）

**Files:**
- Modify: `app/src/main/java/com/example/englishlearning/ui/BackupRestoreScreen.kt`、`BackupExportScreen.kt` 及第三个备份入口所在屏（现场 `grep -rln "备份" app/src/main/java/.../ui` 定位）
- Test: 既有备份 connected 类 + `BackupEntryUnificationTest.kt`

- [ ] **Step 1: 写失败测试**：设置页通往备份的入口只有 1 行；子页含三节（导出 / 恢复 / 删除全部学习数据），各节 `testTag` 齐备。
- [ ] **Step 2: 实现**：「删除全部学习数据」下沉末节，**级联条数 + 二次确认规则零改动**（换 `ConfirmDestructiveDialog` 呈现，业务判定与 AGENTS.md 约束的代码路径一行不动，报告里明确写出「未触碰 `…Restore…` 认证链路」）；`BackupExportScreen.kt:40-47` 私有提示换 `InlineNotice` 并迁 `AppPalette.ErrorText/SuccessText` 读点。
- [ ] **Step 3: 修 AC-R-22 文案缺陷**：备份页文案不得否认自身已实现的认证恢复（审计 §4 缺陷）；措辞改造前后各截一张文本差异进报告。
- [ ] **Step 4: 门控（含备份相关单测全跑）+ connected + 提交**（`feat(ui-backup): 三个备份入口合为一个子页三节`）。

---

### Task 26: 批 3 收口——逐词朗读、棘轮清零、真机与 TalkBack 取证、推送

- [ ] **Step 1: `ClickableText` 逐词无障碍**（F5-03 移交账）：逐词点击站点在朗读里能逐词播报。先写失败测试（语义断言 `TextRange`/`onClick` 标签），再改；本轮**不改**逐词点击的交互行为。
- [ ] **Step 2: 契约测试正则局限 M-B1/M-B2 补测**（不改门禁语义）：为两处已记录漏判面补合成用例与新规则；补完后 `AccessibilityColorTokenContractTest` 既有用例必须原样通过。
- [ ] **Step 3: 棘轮清零**（AC-R-02 兑现）：`RawColorLiteralRatchetTest` seed 应为空；若非空，逐站点要么 token 化、要么在验证文档写明「该站点与主题无关，理由是 X」。`SharedComponentUniquenessRatchetTest` 四条 seed 归零或写明保留理由。`DarkTokenConsumptionRatchetTest` 按**本批开头的「批 3 深色清零专项」**收口：排印墨色族与玻璃容器族迁净、蒙版族留档写理由，四处 `AppPalette.GlassFill` 的迁移动作即 AC-R-11 的兑现面（该行的站点清单已在验证文档登记，别另起口径）。
- [ ] **Step 4: 真机取证（不得凭代码宣称一致）**：六屏在 360dp 宽真机浅/深两套截图，逐屏与 `docs/design/2026-10-17-ui-redesign-mockups.html` 比对（AC-R-10）；玻璃层与域色容器深色下可读性实测（AC-R-11）；TalkBack 走查一遍（AC-R-12：图标替换后描述、逐词、未配置卡出口）。截图与脚本只放 `.git/qa-scratch/`，**不入库**；报告不写含用户数据的绝对路径。
- [ ] **Step 4b: 「我的词」进入帧时实测（性能债收口，评审席 `task-20A-review.md` m-1，控制者裁定 ②）**：合并页三段同屏是 eager `Column + verticalScroll` 全量组合，峰值 = 生词本 + 收藏 + 错词三册之和一次排布（生词本段从旧页 `LazyColumn` 退化而来）。本轮**有意不改** `LazyColumn`——同批把落位逻辑从 `scrollTo(px)` 换成 `scrollToItem(段落位下标)` 会与 I-1 的落位修正叠成结构重构。收口动作：用一台小屏（360dp 级）+ 合成大词表（数百条级，三册各 ≥200）量一次冷启动进入「我的词」的首帧帧时并**给出数字**，写进 `docs/verification/ui-redesign-2026-10.md` 的 AC-R 证据表；数字超 AGENTS.md「核心路径不得阻塞 UI」的口径则立改造任务（改 `LazyColumn` + 宿主 hoist `rememberLazyListState`，先例 `AppScreen.kt:207-209` 与 `WordBookWordsScreen.kt:83/:158`），不得只登记不处置。
  同批顺带观察（不是本席引入、已定性为既有脆弱）：`AppScreenTest.startLearningOpensTheWordCardAndBackReturnsToTheTodayPlan` 在装机后**整类首跑**连红三枚 HEAD、单跑与复跑绿；
  另 `wrongWordsState` 上提宿主后每次组合多收集一枚 flow，属该类 flake 的可疑面之一——取证时一并记录，**禁止**为它改实现或放宽判据。
- [ ] **Step 5: 门禁 + 基线重算 + lockfile 零变化确认**：Global Constraints #11 命令 + #10 重算 + `git diff --name-only main..HEAD | grep -c lockfile` == 0；`verifyThirdPartyNotices` 与 `verifyAcTraceabilityMatrix` 全绿（AC-R 不进矩阵，见 `build.gradle.kts:45-72` 的 `AC[0-4]-\d{2}` 过滤）。
- [ ] **Step 6: 更新 `docs/verification/ui-redesign-2026-10.md`**：AC-R-01…23 全表证据（含真机项的取证方式与结论），逐条要么给证据要么写「未覆盖 + 原因」。
- [ ] **Step 7: 按 `docs/handoff/REPORT-TEMPLATE.md` 写批 3 报告**（七节必填），提交，推送并用 `ls-remote` 核验远端 tip；合 `main` 需用户拍板（外部副作用，停止事项）。
- [ ] **Step 8: 里程碑装真机手工试用全链**（`ANDROID_SERIAL=bf353dda adb install -r …`），真机不跑 instrument。

---

## 计划自检（撰写完成后自查，已在文档内落实）

**1. 规格覆盖**：spec §2.1/§2.2/§2.3 → Task 2/3/4；§3 九个组件 → Task 9/10/11/12；§4 图标体系 → Task 1/6/7；§5 深色与三态 → Task 3/4/5/19；§6 六条裁决 → Task 19/20/22/23/24/25 + 补 1 → Task 13–17；§7 移交账 → Task 9/15/17/21/23/26；§8 外刊多源明确不占本轮 → Task 24 Step 3；§9 AC-R-01…08/20…23 → 各任务 Step 与 Task 26 证据表；AC-R-10/11/12 → Task 26 Step 4；§10 风险 → Task 20 Step 1（宿主拆分）、Task 16 Step 2（未核实项现场核）、Task 17 Step 1（testTag 影响面）、Task 26 Step 2（M-B1/M-B2）。
**2. 占位符扫描**：全文无 TBD/TODO/「类似 Task N」；确需现场取值的三处（`MintPalette` 真实成员名、词书选取屏与字母 Rail 的真实文件名、`CardDetailScreen` 四宿主清单）都写成「先 grep 再动，并把偏差写进报告」的具体命令，而非留空。
**3. 类型一致性**：`AppColorSet` 16 字段名、`AppDomain` 七条目名、`AppIcon` 33 枚举条目（33 字形）、`ThemeMode.storageKey`、`InlineNoticeKind`、`SpeakerState`、`PayloadKind`、`MyWordsSection` 在定义任务与消费任务的 Interfaces 块中逐字一致；`gradientStartForDomain`/`shouldUseDarkTheme`/`allowsInEntryContainer`/`noticeColors`/`speakerContentDescription`/`featureStatusBadge` 的签名在 Produces 与测试代码中同名同参。

---

## 里程碑与真机节奏

用户口径「有结果后可以安装的真机上」：**批 1 收口（Task 12 Step 5）、批 2 收口（Task 18 Step 1）、批 3 收口（Task 26 Step 8）** 各装一次 debug 包到真机 `bf353dda` 供手工试用；批 1 装的那次是图标的第一次真机印象检查点。

