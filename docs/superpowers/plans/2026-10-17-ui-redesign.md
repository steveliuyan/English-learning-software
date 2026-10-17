# UI 重设计轮实现计划（3 批）

> **For agentic workers**: REQUIRED SUB-SKILL: 用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实施本计划。步骤用 checkbox（`- [ ]`）跟踪。

**Goal:** 把 33 屏「不统一、太乱、有些页面不该出现这些东西」的体验问题，一次解决到可发布水位——统一设计系统（浅/深两套 token + 装配点）+ 九个共享组件 + 32 枚 B 案双色层次图标 + 每屏只承担自己的职责。

**Architecture:** 三批串行。批 1 建底座：校准 token、建立唯一的 `MaterialTheme(...)` 装配点与主题三态、把 `Mint*` 令牌改成 CompositionLocal 读取（817 处调用点零重写即获得深色）、入库 `AppIcons.kt` 与 `IconBadge`、把九个共享组件建出来。批 2 用底座重排核心学习路径五屏。批 3 重排其余屏与入口分组（信息架构六条裁决），并用「棘轮测试」把私有实现的份数从 14/7/4/11 收敛到 1。
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
| `app/src/main/java/com/example/englishlearning/ui/components/AppIcons.kt` | 32 枚 B 案图标的数据：逐枚清单钉在文件头注释，每枚 `AppIconSpec(strokePaths, fillPaths)` |
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
| `app/src/test/java/com/example/englishlearning/ui/AppIconsStructureContractTest.kt` | 32 枚清单、两层结构、豁免表、`Icons.` 为 0 |
| `app/src/test/java/com/example/englishlearning/ui/IconBadgeContrastContractTest.kt` | 渐变端取 `deep` 混 35% `base`、白字形 ≥3:1、行内 ≥3:1、禁 `base` 本体作白字形底 |
| `app/src/test/java/com/example/englishlearning/ui/RawColorLiteralRatchetTest.kt` | 裸 hex 棘轮：seed 13 处只减不增 |
| `app/src/test/java/com/example/englishlearning/ui/SharedComponentUniquenessRatchetTest.kt` | 私有实现份数棘轮：页头 14、WordRow 7、NotConfigured 4、InlineNotice 11 → 0 |
| `app/src/test/java/com/example/englishlearning/ui/DarkTokenConsumptionRatchetTest.kt` | 静态 `AppPalette.`/`DomainColors.` 读点棘轮：按屏计数，只减不增 |
| `docs/verification/ui-redesign-2026-10.md` | AC-R-* 证据表（`verifyAcTraceabilityMatrix` 只吃 `AC[0-4]-\d{2}`，AC-R 不进矩阵，故单独成文） |

主要改动：`ui/theme/MintPalette.kt`（`Mint*` 变组合期读取器）、`ui/theme/AppPalette.kt`（取值校准 + 补齐缺失 token）、`ui/theme/AppType.kt`（接装配点）、`ui/AppScreen.kt`、`ui/AppBottomBar.kt`、`MainActivity.kt`（内容根换 `MintGlassTheme`）、批 2/批 3 的逐屏文件（每任务里点名）。

不动：`data/` Room schema 与迁移、`di/`、备份链路（除组件替换外）、`AppTabTest`、`app/gradle.lockfile`。

---

## 深色模式迁移策略（本计划的核心决策，先读这段再动手）

现状：`ui/theme/MintPalette.kt` 定义七个 `internal val Mint*`，全站 817 处 `Mint*` 读点（实测计数）**全部位于 `@Composable` 函数体内**，仅 3 处顶层 `Brush.linearGradient(listOf(AppleMintStart, AppleMintMiddle, AppleMintEnd))`（`AiFeatureScreen.kt:47`、`AiLearningScreen.kt:56`、`AppScreen.kt:100`）和 1 处默认参数 `accent: DomainAccent = DomainColors.AiSpeech`（`glass/GlassButton.kt:47`）是组合期之外的读取。

据此采用 Material 自己的做法——把 `Mint*` 从 `val` 改成读 `CompositionLocal` 的 `@Composable @ReadOnlyComposable get()`：

```kotlin
// MintPalette.kt 目标形状（保持名字与包内可见性，817 处调用点一行不改）
internal val MintBackground: Color
    @Composable @ReadOnlyComposable get() = LocalAppColors.current.background
```

817 处读点因为在组合期内，自动获得深色取值，零重写。`AppleMint*`（品牌渐变三端 + `mint-light`）**保持静态 `val`**：品牌渐变模式不变（spec §2.1「品牌渐变保持现状不动」），且三处顶层 `Brush` 需要组合期外的静态读取——若改成组合读取会让这三个顶层 val 编译失败。

`AppPalette`（91 处）与 `DomainColors`（71 处）**保持静态浅色源**，另建深色兄弟（`AppColorDark`、`DomainPalette` 深套），由 `LocalAppColors`/`LocalDomainColors` 在装配点选择；组合期代码改读 `appColors().textSecondary` / `appDomain(AppDomain.AiSpeech).deep`。这一步无法一次做完（162 处），因此用 `DarkTokenConsumptionRatchetTest` 把**按屏的静态读点计数**做成只减不增的棘轮：批 1 建表并记 seed，批 2/批 3 每屏重排时顺手迁完自己那屏，收口时 seed 应清零或仅剩确实与主题无关的站点（逐条写明理由）。

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
  - `text-2 #4E756A` 压 `tint-2 #DCEAE1` = **4.15**（压 `tint #DDF7E8` 为 4.55，合格）
  - `on-mint #174C44` 压品牌渐变末端 `#35B9B5` = **4.07**；压渐变中段 `#5DDFB4` = 5.89；压 `mint-light #D9FFF0` = 9.06
  - `on-brand #04241C` 压渐变末端 = **6.87**；压渐变起点 = 12.81

  Expected: 四个数字与上面一致（若不一致，以复算值为准并改正本计划与决策文档——不要沿用陈旧值）。

- [ ] **Step 2: 写决策文档**

  `docs/decisions/2026-10-18-ui-redesign-contrast-and-icon-layer-rulings.md` 含背景/候选/决定/影响四段，三条裁决逐条写：

  1. **`text-3` 降级为「非正文角色」**：仅用于 ≥24sp 大数字/装饰性大字与纯图标描边，**不得作任何正文或标签前景**（实测 3.00–3.40，达不到 AA 正文 4.5）。需要第三级弱化文字时用 `text-2` + 降字号，不用 `text-3`。候选方案「把 `text-3` 调深到通过 4.5」被否：调深后与 `text-2` 视觉上不再成三级。
  2. **`text-2` 的可用底限于 `surface`/`bg`/`tint`**（5.15 / 4.87 / 4.55），**禁压 `tint-2`/`tint-3`**（4.15 / ≈4.0）。`tint-2`/`tint-3` 上的正文一律 `text-1`。
  3. **品牌渐变末端上的前景改用 `on-brand #04241C`**：`on-mint` 在 `#35B9B5` 上只有 4.07；`mint` 渐变三端从浅到深，因此**同一渐变上的文字统一取 `on-brand`**（起点 12.81、末端 6.87，处处合格），`on-mint` 保留给 `tint`/`mint-light` 一类浅薄荷底。spec §2.1 的「≈4.6:1」原文口径只适用于浅薄荷底，本条把它在渐变上的取值改钉为 `on-brand`。
  4. **图标两层结构豁免表**：32 枚中 **5 枚只有描边骨架、不建填充层**——`Back`、`ChevronRight`、`ChevronDown`、`Close`、`Check`。理由是这些字形没有可闭合的内部区域，强行加填充层等于把描边变实心，正是 B 案要避免的「两族字形」。同时**这 5 枚不得进入 ≥40dp 入口容器**（它们只作行内/尾部指示）。AC-R-05b 的「每枚两层」据此改为「除豁免表外每枚两层」。

  并在同文档给出 **32 枚图标逐枚清单**（spec §10.5 留给批 1 第一个任务钉进 `AppIcons.kt` 顶部注释的那份），名字与顺序按 Interfaces 下方 Task 5 的清单。

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
  - `val LocalAppColors: CompositionLocal<AppColorSet>`、`val LocalDomainColors: CompositionLocal<Map<AppDomain, DomainPalette>>`、`val LocalIsDark: CompositionLocal<Boolean>`
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

  按 Interfaces 的形状把浅/深两套 `AppColorSet` 与两套域色 Map 写成顶层 val，注释里点名「取值来自 spec §2.1 + 决策 2026-10-18 裁决」；`LocalAppColors`/`LocalDomainColors`/`LocalIsDark` 用 `staticCompositionLocalOf`（整树切换，不需要每次变更重建）。读取器：

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
- Produces: 八个 `internal val Mint*` 的组合期 getter（名字与可见性不变）：`MintBackground`、`MintSurface`、`MintPrimary`、`MintPrimaryContainer`、`MintOnPrimary`、`MintOutline`、`MintSeparator`（按文件实际成员名对齐——**先读该文件列出真实成员名，本计划不猜**；映射规则见 Step 2）。`AppleMint*` 与 `MintGlass*` 保持静态。

- [ ] **Step 1: 先读现状**

  ```bash
  grep -n 'internal val\|^val' app/src/main/java/com/example/englishlearning/ui/theme/MintPalette.kt
  ```
  把真实成员名与各自当前 hex 抄进任务报告，作为映射表的输入。若成员集合与本计划描述不符，**以文件为准**，映射规则照 Step 2 的语义套用，并把偏差写进报告。

- [ ] **Step 2: 写契约测试（失败）**

  断言：每个中性 `Mint*` 的声明形状是 `internal val X: Color` + `@Composable @ReadOnlyComposable get() = LocalAppColors.current.<field>`；每个品牌/玻璃 `AppleMint*`/`MintGlass*` 仍是 `= Color(0x…)` 静态。规则用正则，配合成片段正/负用例各一组（形状同 Task 2）。失败信息要点名具体成员名。

- [ ] **Step 3: 改写 `MintPalette.kt`**

  语义映射（左：既有令牌 → 右：`AppColorSet` 字段）：
  背景类 → `background`；表面/卡片底 → `surface`；主色容器/浅薄荷底 → `tint`；主色/描边 → `outline`；分隔线 → `outlineSoft`；主色上的文字（若存在 `MintOnPrimary` 一类）→ `onMint`。
  注释里写明：817 处读点全在组合期内，因此这一步不改任何调用文件；`AppleMint*` 保持静态的原因（三处顶层 `Brush` + 品牌渐变模式不变）。

- [ ] **Step 4: 编译并跑全部门控**

  Run: 门控命令。Expected: BUILD SUCCESSFUL。
  **若出现「不是组合期」的调用点报错**（预期外的顶层/默认参数读取），停下并在报告里逐个列出报错站点；处理办法只有两种，按优先级：① 把该调用点移进 `@Composable`；② 若它本质是常量表（如 `GlassButton.kt:47` 的默认参数），改用无默认值 + 组合期实参传入。**不得**为了让它编译而把令牌退回静态 val——那会让深色失效。

- [ ] **Step 5: 提交**

  ```bash
  git add app/src/main/java/com/example/englishlearning/ui/theme/MintPalette.kt \
          app/src/test/java/com/example/englishlearning/ui/MintTokenIsComposableContractTest.kt
  git -c user.name=steveliuyan -c user.email=2021250388@qq.com commit \
    -m "refactor(ui-theme): Mint 中性令牌改读 CompositionLocal，817 处调用点零重写获得深色能力"
  ```

---

### Task 4: 唯一 `MaterialTheme` 装配点 + `MainActivity` 接线

**Files:**
- Create: `app/src/main/java/com/example/englishlearning/ui/theme/MintGlassTheme.kt`
- Modify: `app/src/main/java/com/example/englishlearning/ui/theme/AppType.kt`（只加一个把六档语义装成 `Typography` 的函数，钉值不动）
- Modify: `app/src/main/java/com/example/englishlearning/MainActivity.kt`（`setContent { AppScreen(...) }` 外面包一层 `MintGlassTheme`）
- Test: `app/src/test/java/com/example/englishlearning/ui/MaterialThemeAssemblyContractTest.kt`

**Interfaces:**
- Consumes: Task 2 的 `AppColorSet`/`LocalAppColors`/`LocalDomainColors`/`LocalIsDark`/两套域色 Map；Task 3 的组合期 `Mint*`；既有 `AppType`（六档钉值已符合 spec §2.2）、`AppShape`（`Dialog 24/Card 20/Button 14/Pill 50%`）、`MintColors`（既有 M3 `lightColorScheme` 装配源）。
- Produces:
  - `@Composable fun MintGlassTheme(themeMode: ThemeMode = ThemeMode.System, content: @Composable () -> Unit)`（Task 5 起被 `MainActivity` 使用；内部据 `shouldUseDarkTheme(themeMode, isSystemInDarkTheme())` 选 set 并 `CompositionLocalProvider`）
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

  `appColorScheme(dark)` 用 `lightColorScheme(...)`/`darkColorScheme(...)`，参数**全部取自 `AppColorSet`**（不得再出现裸 hex；`primaryContainer` 取 `tint`、`onPrimaryContainer` 取 `onMint`、`error` 取 `error`、`background`/`surface`/`onBackground`/`onSurface` 按名对应，`outline` 取 `outline`、`outlineVariant` 取 `outlineSoft`）。`appTypography()` 把 `AppType` 六档映射到 M3 角色（Display→`displaySmall`、Headline→`headlineSmall`、Title→`titleMedium`、Body→`bodyMedium`、Footnote→`bodySmall`、Label→`labelMedium`），其余 M3 角色从这六档派生（不引新字号）。

- [ ] **Step 4: 接 `MainActivity`**

  `setContent {` 内包一层：
  ```kotlin
  setContent {
      val themeMode = rememberAppThemeMode()   // Task 5 提供；本任务先用 ThemeMode.System 常量占位，见下
      MintGlassTheme(themeMode = ThemeMode.System) { AppScreen(/* 26 个 hiltViewModel 实参原样保留 */) }
  }
  ```
  **本任务只接装配点、不接偏好读写**（Task 5 换成 `rememberAppThemeMode()`）。改这一行时 `AppScreen` 的 26 个实参顺序与表达式一字不动——`AppScreenProductionWiringTest` 强制每个可选参数都在 `MainActivity` 接线，漏一个即挂测试（预期门，非环境故障）。

- [ ] **Step 5: 跑测试确认通过 + 门控**

  Expected: `MaterialThemeAssemblyContractTest` 全绿；门控全绿；基线 1499 + 新增用例，0 失败。
  额外人工检查：`"$GRADLE" :app:assembleDebug` 成功后 APK 内主题不崩——本任务无渲染测试，把「装配点下首个组合可能因 Local 无提供者而崩」列为批 1 收口 connected 走查项（Task 10）。

- [ ] **Step 6: 提交**

  ```bash
  git add app/src/main/java/com/example/englishlearning/ui/theme/MintGlassTheme.kt \
          app/src/main/java/com/example/englishlearning/ui/theme/AppType.kt \
          app/src/main/java/com/example/englishlearning/MainActivity.kt \
          app/src/test/java/com/example/englishlearning/ui/MaterialThemeAssemblyContractTest.kt
  git -c user.name=steveliuyan -c user.email=2021250388@qq.com commit \
    -m "feat(ui-theme): 建立唯一 MaterialTheme 装配点并接入 MainActivity 内容根"
  ```

---

### Task 5: 主题三态偏好（跟随系统 / 浅色 / 深色）

**Files:**
- Create: `app/src/main/java/com/example/englishlearning/ui/theme/AppThemePreference.kt`
- Modify: `app/src/main/java/com/example/englishlearning/MainActivity.kt`（换成 `rememberAppThemeMode()`）
- Test: `app/src/test/java/com/example/englishlearning/ui/ThemeModeDecisionTest.kt`
- Test（androidTest）: `app/src/androidTest/java/com/example/englishlearning/ui/ThemeModeSwitchTest.kt`

**Interfaces:**
- Consumes: Task 4 的 `MintGlassTheme(themeMode = …)`。
- Produces:
  - `enum class ThemeMode(val storageKey: String) { System("system"), Light("light"), Dark("dark") }`
  - `fun shouldUseDarkTheme(mode: ThemeMode, systemInDark: Boolean): Boolean`（纯函数）
  - `fun themeModeFromStorage(raw: String?): ThemeMode`（非法/缺失回退 `System`）
  - `object AppThemePreferences { const val PREFS_NAME = "ui_display_prefs"; const val KEY_THEME_MODE = "theme_mode"; fun read(context: Context): ThemeMode; fun write(context: Context, mode: ThemeMode) }`
  - `@Composable fun rememberAppThemeMode(): ThemeMode`（`ContextCompat` + `DisposableEffect` 的 `OnSharedPreferenceChangeListener`）

- [ ] **Step 1: 写纯函数单测（失败）**

  `ThemeModeDecisionTest`（JUnit5 + kotlin.test，无 Android 依赖——这正是把判定做成纯函数的原因）：
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
  （大小写敏感的 `LIGHT` 必须回退——存储键是写死的，不做归一化以免「两种写法都合法」的漂移。）

- [ ] **Step 2: 跑测试确认失败** — Run `--tests '*ThemeModeDecisionTest*'`，Expected: 编译失败/未解析（`ThemeMode` 不存在）。

- [ ] **Step 3: 实现 `AppThemePreference.kt`**

  纯函数 + prefs 读写 + `rememberAppThemeMode()`。类注释写明两条口径：
  - **不进 Room、不做 v33 迁移、不进备份包**（spec §5.2）：显示偏好不是学习数据；因此不用 `LearningSettingsEntity`，也**不得**把 `ui_display_prefs` 加进任何备份文件清单。
  - prefs 文件是 app 私有，卸载即失——与 AGENTS.md「卸载/清除数据前未导出数据可能无法恢复」一致，主题偏好属可丢项。

- [ ] **Step 4: 跑单测通过**

- [ ] **Step 5: 写 connected 用例（像素证据）**

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
  注：`Box` 底色由 `MaterialTheme.colorScheme.background` 提供还是需显式 `.background(appColors().background)`——以 Step 6 现场为准；若 `setContent` 根不带背景，就显式加 `.background(LocalAppColors.current.background)` 并在注释说明这是探针而非生产形状。

- [ ] **Step 6: `MainActivity` 接 `rememberAppThemeMode()`**，跑门控 + 编译 androidTest。

- [ ] **Step 7: 在 `emulator-5574` 跑 connected**

  ```bash
  export ANDROID_SERIAL=emulator-5574
  "$GRADLE" :app:assembleDebug :app:assembleDebugAndroidTest --offline --no-daemon --no-build-cache
  adb install -r app/build/outputs/apk/debug/app-debug.apk
  adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
  adb shell am instrument -w -e class com.example.englishlearning.ui.ThemeModeSwitchTest \
    com.example.englishlearning.test/androidx.test.runner.AndroidJUnitRunner
  ```
  Expected: 末行 `OK (2 tests)`。失败先看 logcat，再对照 `android-connected-qa-loop` 怪癖表（息屏/弹窗劫持/dump 过期）。

- [ ] **Step 8: 提交**

  ```bash
  git add app/src/main/java/com/example/englishlearning/ui/theme/AppThemePreference.kt \
          app/src/main/java/com/example/englishlearning/MainActivity.kt \
          app/src/test/java/com/example/englishlearning/ui/ThemeModeDecisionTest.kt \
          app/src/androidTest/java/com/example/englishlearning/ui/ThemeModeSwitchTest.kt
  git -c user.name=steveliuyan -c user.email=2021250388@qq.com commit \
    -m "feat(ui-theme): 主题三态偏好落 SharedPreferences 并用真像素验证深浅装配"
  ```

---

### Task 6: `AppIcons.kt` — 32 枚 B 案图标的两层路径数据

**Files:**
- Create: `app/src/main/java/com/example/englishlearning/ui/components/AppIcons.kt`
- Test: `app/src/test/java/com/example/englishlearning/ui/AppIconsStructureContractTest.kt`

**Interfaces:**
- Consumes: Task 1 决策文档的 32 枚清单与 5 枚两层豁免表。
- Produces:
  - `data class AppIconSpec(val name: String, val strokePaths: List<String>, val fillPaths: List<String>, val fillAlpha: Float = 0.30f)`（路径为 24×24 画布的 SVG path data 字符串；圆形填充用 `CircleFill(cx, cy, r)` 标记串，由 Task 7 的渲染器解释）
  - `enum class AppIcon { Back, ChevronRight, ChevronDown, Close, Check, Search, Book, BookOpen, List, Bookmark, Star, Clock, Chart, Spark, Speaker, Play, Pause, Stop, Mic, Translate, Camera, Image, Document, Newspaper, Gear, Person, Info, Bell, Shield, Warning, Trash, Upload, Download }`
  - `val AppIcon.spec: AppIconSpec`（`AppIcons.all: Map<AppIcon, AppIconSpec>` 的读取扩展）
  - `val StrokeOnlyIcons: Set<AppIcon>` = `setOf(Back, ChevronRight, ChevronDown, Close, Check)`

  **枚数核对**：枚举 33 个条目 = 32 枚字形（`Book` 与 `BookOpen` 是两枚不同字形：一本合上的书 / 摊开的书）。若执行时认为其中一枚冗余，删枚举条目属范围变化，先改 Task 1 决策文档的清单再删，不得静默改数。

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
  断言四条：
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

- [ ] **Step 2: 跑测试确认失败** — Expected: 编译失败（`AppIcon` 未定义）。

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
  - 关键几何留在 4–20 的安全区内（与 `AppBottomBar.kt` 里既有的 `drawTabGlyph` 同一 24 网格习惯，便于对照）；
  - 方向类字形（chevron/back/check/close）笔画端点对称，17dp 行内尺寸下仍可辨；
  - 每枚补完在注释里标一行「原创自绘（B 案度量）」——许可台账 `app-authored-ui-icons` 的措辞依赖它（AC-R-05）。

  文件头注释顺序写：许可结论 → 度量基准（仅参考 Material Symbols 的 24dp 网格与 2px 重量，未内嵌其路径数据）→ 逐枚清单（32 枚 + 各自用途屏）→ 豁免表 5 枚。

- [ ] **Step 4: 跑测试确认通过** — Run `--tests '*AppIconsStructureContractTest*'`，Expected: PASS。

- [ ] **Step 5: 提交**

  ```bash
  git add app/src/main/java/com/example/englishlearning/ui/components/AppIcons.kt \
          app/src/test/java/com/example/englishlearning/ui/AppIconsStructureContractTest.kt
  git -c user.name=steveliuyan -c user.email=2021250388@qq.com commit \
    -m "feat(ui-icons): 32 枚 B 案双色图标路径数据入库并钉住两层结构与零 Icons 引用"
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
  - `@Composable fun EntryIconBadge(icon: AppIcon, domain: AppDomain, modifier: Modifier = Modifier)`：40dp 容器，渐变 `linearGradient(listOf(mix(deep, base, 0.35f), deep))`，白色两层字形，`inset 0 0 0 1px rgba(255,255,255,.26)` 用 `BorderStroke(1.dp, AppPalette.GlassHighlight)` 等价实现，圆角 `AppShape.icon`（12dp）
  - `@Composable fun InlineIconBadge(icon: AppIcon, domain: AppDomain, modifier: Modifier = Modifier)`：30dp 容器 `tint` 底 + 17dp 单层域色 `deep` 描边字形（填充层不显示）
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

  `vector { }` DSL 组装：`group` 内先画填充层（`fillBrush = SolidColor(tint.copy(alpha = spec.fillAlpha))`）再画骨架层（`stroke = Stroke(width = 2f * densityScale, cap = Round, join = Round, brush = SolidColor(tint))`）。`CircleFill(cx, cy, r)` 串解析成圆形 path。所有尺寸走 24dp `defaultWidth/defaultHeight`，实际缩放交给 `Image`。
  注释写明为什么运行时组装而不是直接内嵌静态 `ImageVector`：颜色必须来自 `LocalAppColors`/域色，静态 val 会把主题化挡在门外（与 Task 3 同一理由）。

- [ ] **Step 4: 实现 `IconBadge.kt`**

  两种呈现按 Interfaces 的几何。`Modifier.semantics` 不留内容描述（装饰性），容器 `Box(Modifier.size(40.dp).clip(RoundedCornerShape(AppShape.icon)).background(brush).border(1.dp, AppPalette.GlassHighlight, …))`。
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
  - `WordRow`：私有行实现 7 份（spec §3 点名的 6 个文件里的行组合 + 1 变体）。
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
- Consumes: `appColors()`、`AppType.Headline/Body`、`AppIcon.Back`、`appImageVector`。
- Produces:
  - `@Composable fun AppScreenHeader(title: String, modifier: Modifier = Modifier, subtitle: String? = null, onBack: (() -> Unit)? = null, action: @Composable (() -> Unit)? = null)`
  - `@Composable fun ScreenUnavailable(reason: String, actionLabel: String, onAction: () -> Unit, modifier: Modifier = Modifier, icon: AppIcon = AppIcon.Info)`

- [ ] **Step 1: 写 connected 测试（失败）**

  三条（每方法一次 `setContent`，绝不复用）：
  ```kotlin
  @Test fun header_back_is_a_48dp_target_with_a_readable_description() {
      rule.setContent { MintGlassTheme { AppScreenHeader("学习记录", onBack = { backed = true }) } }
      rule.onAllNodesWithContentDescription("返回").onFirst().assert(hasClickAction())
        .assert(hasAnyChild())
      val node = rule.onAllNodesWithContentDescription("返回").onFirst()
      val h = node.getSemanticsNode().layoutNode.height  // 用 captureRegionInRoot / bounds 换算，见下
      assertTrue(h >= (48 * density).toInt(), "返回可点区应 ≥48dp，实际 $h px")
  }
  @Test fun header_survives_font_scale_2_without_clipping_the_title() {   // F5-03 移交账
      rule.activity.setContent { MintGlassTheme { AppScreenHeader("学习记录与复习统计（长标题用）", subtitle = "本周新增 128 词") } }
      // 在 fontScale=2.0 下：标题不得被裁（断言文本完整 + 高度 > 单行）
  }
  @Test fun screen_unavailable_offers_exactly_one_action() {
      rule.setContent { MintGlassTheme { ScreenUnavailable(reason = "AI 尚未配置", actionLabel = "去配置 ›", onAction = { tapped = true }) } }
      rule.onNodeWithText("去配置 ›").assertIsDisplayed().performClick()
      assertTrue(tapped)
      rule.onAllNodes(hasClickAction()).onLast()  // 只有一个可点出口
  }
  ```
  实现口径按仓库既有 connected 样板落地（`CheckInScreenTest` 的 fontScale 护栏与 48dp 量法直接沿用——**先读该文件确认量法名称再写**，别自创 API）。

- [ ] **Step 2: 跑 connected 确认失败**（组件不存在 → 编译失败即 RED 证据，写进报告）。

- [ ] **Step 3: 实现两个组件**

  `AppScreenHeader`：`Row(verticalAlignment = CenterVertically)`，返回按钮为 `Box(Modifier.size(48.dp).clip(CircleShape).clickable(onBack).semantics { contentDescription = "返回" })` + `AppGlyph(AppIcon.Back, appColors().text1, 22.dp)`；`title` 用 `AppType.Headline` + `maxLines = 2`（**不省略号**：页标题裁掉即丢信息），`subtitle` 用 `AppType.Body` + `appColors().text2`；无 `onBack` 时不渲染返回位（首屏用），标题左对齐位置不变（用同宽占位 `Spacer(48.dp)`）。整行 `heightIn(min = 56.dp)` 且 `Modifier.testTag("app_screen_header")`。
  `ScreenUnavailable`：`tint` 底卡片 + `InlineIconBadge` 位（或 `AppGlyph`）+ 原因文案 + **一个**动作按钮，`testTag("screen_unavailable_action")`。
  两者颜色全部走 `appColors()`，零裸 hex（Task 8 棘轮会挡）。

- [ ] **Step 4: 跑 connected 通过 + 门控**（emulator-5574，命令见 Task 5 Step 7）。

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
- Test: `app/src/androidTest/java/com/example/englishlearning/ui/EntryRowTest.kt`
- Test: `app/src/test/java/com/example/englishlearning/ui/InlineNoticeKindTest.kt`

**Interfaces:**
- Consumes: `appColors()`、`appDomain(AppDomain)`、`IconBadge`（`EntryIconBadge`/`InlineIconBadge`）、`AppType.Body/Footnote`。
- Produces:
  - `enum class InlineNoticeKind { Error, Info, Success }`
  - `@Composable fun InlineNotice(text: String, kind: InlineNoticeKind, modifier: Modifier = Modifier, action: @Composable (() -> Unit)? = null)`（`testTag("inline_notice_${kind.name.lowercase()}")`）
  - `@Composable fun EntryRow(icon: AppIcon, domain: AppDomain, title: String, modifier: Modifier = Modifier, subtitle: String? = null, trailing: String? = null, showChevron: Boolean = true, onClick: () -> Unit)`（整行 ≥48dp、`role = Role.Button`、`testTag` 由调用方传入）

- [ ] **Step 1: 写纯函数单测（失败）**

  `InlineNoticeKindTest` 断言三态到 token 的映射是**纯表**（把映射抽成 `fun noticeColors(kind: InlineNoticeKind, set: AppColorSet): Pair<Color, Color>` 返回 (前景, 底)）：
  ```kotlin
  @Test fun error_notice_uses_error_foreground_not_named_red() {
      val (fg, bg) = noticeColors(InlineNoticeKind.Error, AppColorLight)
      assertEquals(AppColorLight.error, fg)
      assertTrue(fg != AppColorLight.errorFill)   // 前景是 error 文，不是 error-fill 填充
      assertTrue(ratioOf(fg, bg) >= 4.5)          // 复用 Task 2 的亮度函数（测试侧独立实现）
  }
  @Test fun success_and_info_foregrounds_clear_aa_on_their_backdrops() { /* 同形两条 */ }
  ```
  Expected: 编译失败。

- [ ] **Step 2: 实现 `InlineNotice.kt`**（含 `noticeColors` 纯表）与 `EntryRow.kt`。
  `EntryRow` 布局：`Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button))` = `EntryIconBadge` + `Column(weight = 1f)`（title `AppType.Body` `text1`，subtitle `AppType.Footnote` `text2`）+ trailing（`text2`）+ `AppGlyph(ChevronRight, text3?, 16.dp)`。
  **注意 Task 1 裁决 #1**：`text-3` 不作正文前景。chevron 是图形不是文字，按非文本 ≥3:1 判定；`text3` 压在 `tint` 上实测 3.00 恰好不过 3:1 的边界风险，因此 chevron 一律用 **`text2`**（tint 上 4.55）。把这条写成断言进 `EntryRowTest`：chevron 颜色 == `appColors().text2`。

- [ ] **Step 3: 写 `EntryRowTest`（connected）**：整行可点、`Role.Button`、最小高度 ≥48dp、chevron 色取 `text2`、大字号下 subtitle 可省略（`maxLines = 1` 允许省略号——副标题是提示性内容，可裁）。

- [ ] **Step 4: 跑测试通过 + 门控 + 提交**

  ```bash
  git add app/src/main/java/com/example/englishlearning/ui/components/InlineNotice.kt \
          app/src/main/java/com/example/englishlearning/ui/components/EntryRow.kt \
          app/src/test/java/com/example/englishlearning/ui/InlineNoticeKindTest.kt \
          app/src/androidTest/java/com/example/englishlearning/ui/EntryRowTest.kt
  git -c user.name=steveliuyan -c user.email=2021250388@qq.com commit \
    -m "feat(ui-components): 统一行内提示与彩色图标入口行，chevron 按非文本对比取 text2"
  ```

---

### Task 11: `NotConfiguredCard` + `ConfirmDestructiveDialog` + `OutboundConfirmDialog`

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

- [ ] **Step 3: 实现三组件**，全部 `appColors()`；`ConfirmDestructiveDialog` 确认按钮容器色 `errorFill` + 白字（`Color.White` on `#FF3B30` = 4.0，属图形/大字口径；若正文则改用深字——按钮文字 17sp SemiBold 属大字 ≥18sp? 17sp 不达 18 大字豁免线，因此**按钮前景取 `AppColorLight.onBrand` 同族深色 `#3B0000`？** 不——按 spec 与既有裁决：红底白字滑动删除已判合规（`#B3261E` 真机取证）。这里为一致，`errorFill` 填充上的按钮文字**取 `Color.White` 且字号 ≥17sp SemiBold**，并在测试里断言 `ratio(white, errorFill) >= 3.0`（大字非文本口径），注释写明与滑动删除同族、不宣称 4.5:1。

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

- [ ] **Step 3: 写 `SharedComponentUniquenessRatchetTest`**（规则与 seed 见 Task 8 Step 4 四条），此时九个组件文件都存在，目录守卫与 seed 只减不增口径同 Task 8。

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

- [ ] **Step 5: 批 1 门禁 + 里程碑装真机手工看图标**

  ```bash
  "$GRADLE" :app:assembleDebug :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin verifyThirdPartyNotices --offline --no-daemon --no-build-cache 2>&1 | grep -E 'FAILED|^e:|BUILD|tests completed' ; exit ${PIPESTATUS[0]}
  grep -h -o 'tests="[0-9]*" skipped="[0-9]*" failures="[0-9]*" errors="[0-9]*"' app/build/test-results/testDebugUnitTest/TEST-*.xml | awk -F'"' '{t+=$2;s+=$4;f+=$6;e+=$8} END{print "tests="t" skipped="s" failures="f" errors="e}'
  git diff --name-only main..HEAD | grep -c lockfile   # 期望 0：依赖锁零变化
  ANDROID_SERIAL=bf353dda adb install -r app/build/outputs/apk/debug/app-debug.apk
  ```
  Expected: 全绿；基线 ≥1499 且 0 失败；lockfile 未改；真机装上后**手工试用**（真机只 `install -r`，绝不 `am instrument`）。批 1 时页头/入口行尚未接入业务屏，真机可看的是底导与设置页既有形状 + 主题三态（此时设置页尚无主题开关，Task 19 接入）——把「图标在真机上的第一印象」列为本步人工核验项并记进报告。

- [ ] **Step 6: 提交 + 推送 + 核验**

  ```bash
  git add <逐个文件>            # 组件两份 + 棘轮测试 + 验证文档 + 台账
  git -c user.name=steveliuyan -c user.email=2021250388@qq.com commit \
    -m "feat(ui-components): 词行与发音药片收口批1，组件唯一性棘轮与 AC-R 证据文档上线"
  git -c credential.interactive=false push origin stage-5-ui-r1-design-system
  git -c credential.interactive=false ls-remote origin stage-5-ui-r1-design-system
  ```
  Expected: `ls-remote` 哈希 == `git rev-parse HEAD`。

---

## 批 2 · 核心学习路径五屏（Task 13–18）

分支：`stage-5-ui-r2-learning-path`（批 1 合入 `main` 后切出）。
批 2 只碰四屏 + 学习壳（裁决补 1）：词书选取 / 今日计划 / 词卡 / 词卡详情 + `AppScreen`/`AppBottomBar`。每屏任务的固定收尾三步：**① `grep -n 'testTag'` 本屏并把清单贴进报告；② 迁完本屏静态 `AppPalette.`/`DomainColors.` 读点并递减 `DarkTokenConsumptionRatchetTest` seed；③ connected 跑本屏既有用例类**（类名以现场 `ls app/src/androidTest/.../ui` 为准，不猜）。

- [ ] **Step 1（每屏通用）: 先跑该屏 connected 基线**，记录改动前通过数；改动后必须全绿。

---

### Task 13: 词书选取屏（`VocabularyScreen` 词书列表 / 首页入口）

**Files:**
- Modify: 词书选取相关屏文件（现场以 `grep -rln "词书" app/src/main/java/com/example/englishlearning/ui | sort` 定位，锁定真实文件名后再动）
- Test: 该屏既有 connected 类 + 新增 `WordbookPickerHeaderTest.kt`

**Interfaces:**
- Consumes: `AppScreenHeader`、`EntryRow`、`Skeleton`（本任务新建 `ui/components/Skeleton.kt`）。
- Produces: `@Composable fun ListSkeleton(rows: Int, rowHeight: Dp, modifier: Modifier = Modifier)`（骨架高度预留，spec §3 与 F5-03 移交账「每册骨架」）。

- [ ] **Step 1: 写失败测试**：列表首帧渲染时骨架行数 == 上次已知的册数或固定 3，且加载完成后不整体跳动（断言骨架与真实行同高）。
- [ ] **Step 2: 实现**：页头换 `AppScreenHeader`；每册行换 `EntryRow`（域色 `Library`）；`Arrangement.spacedBy` 造成的幽灵间距换成真实 `outlineSoft` 分隔线（spec §2.3 禁令）。
- [ ] **Step 3: 门控 + 本屏 connected + 提交**（`feat(ui-learning): 词书选取屏接入统一页头与入口行并预留骨架高度`）。

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
- [ ] **Step 3: 规则零改动自检**：解锁条件、快照不可变性相关代码一行不动；跑该屏业务单测（`grep -rln TodayPlan app/src/test` 全跑）。
- [ ] **Step 4: 门控 + connected + 提交**（`feat(ui-learning): 今日计划收成单一主按钮与入口行，今日数字一处权威`）。

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

---

### Task 18: 批 2 收口

- [ ] **Step 1:** 门禁全绿 + 基线重算 + `DarkTokenConsumptionRatchetTest` seed 递减确认 + 里程碑装真机手工试用五屏。
- [ ] **Step 2:** 更新 `docs/verification/ui-redesign-2026-10.md`：AC-R-07（本批覆盖的屏）、AC-R-20（今日计划）证据行；真机截图存本地忽略区（不入库）。
- [ ] **Step 3:** 按 `docs/handoff/REPORT-TEMPLATE.md` 写批 2 报告；提交 + 推送 + `ls-remote` 核验。

---

## 批 3 · 其余屏与入口分组重排（Task 19–26）

分支：`stage-5-ui-r3-surfaces-and-ia`（批 2 合入 `main` 后切出）。
批 3 承载信息架构六条裁决中的 ①②③④⑤⑥ 与 F5-03 移交账剩余项。
**入口迁移铁律（spec §6，违反即判失败）**：先建新入口，验证可达且 `testTag`/朗读描述齐全，**再**摘旧入口。收藏与错词在「我的词」可用之前绝不能先从打卡页删掉——因此 Task 20 必须早于 Task 22。

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
- [ ] **Step 4: `WordRow` 替换 7 份私有行实现**中的本域 4 份（`WordBookWordsScreen.kt:239-258`、`GlobalVocabularySearchScreen.kt:274-299/:319-358`、`WrongWordsScreen.kt:110-129`、`FavoritesScreen.kt:116-156`），同步递减 `SharedComponentUniquenessRatchetTest` seed。
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
- [ ] **Step 3: 棘轮清零**（AC-R-02 兑现）：`RawColorLiteralRatchetTest` seed 应为空；若非空，逐站点要么 token 化、要么在验证文档写明「该站点与主题无关，理由是 X」。`SharedComponentUniquenessRatchetTest` 四条 seed 归零或写明保留理由。`DarkTokenConsumptionRatchetTest` 按 Task 8 口径收口。
- [ ] **Step 4: 真机取证（不得凭代码宣称一致）**：六屏在 360dp 宽真机浅/深两套截图，逐屏与 `docs/design/2026-10-17-ui-redesign-mockups.html` 比对（AC-R-10）；玻璃层与域色容器深色下可读性实测（AC-R-11）；TalkBack 走查一遍（AC-R-12：图标替换后描述、逐词、未配置卡出口）。截图与脚本只放 `.git/qa-scratch/`，**不入库**；报告不写含用户数据的绝对路径。
- [ ] **Step 5: 门禁 + 基线重算 + lockfile 零变化确认**：Global Constraints #11 命令 + #10 重算 + `git diff --name-only main..HEAD | grep -c lockfile` == 0；`verifyThirdPartyNotices` 与 `verifyAcTraceabilityMatrix` 全绿（AC-R 不进矩阵，见 `build.gradle.kts:45-72` 的 `AC[0-4]-\d{2}` 过滤）。
- [ ] **Step 6: 更新 `docs/verification/ui-redesign-2026-10.md`**：AC-R-01…23 全表证据（含真机项的取证方式与结论），逐条要么给证据要么写「未覆盖 + 原因」。
- [ ] **Step 7: 按 `docs/handoff/REPORT-TEMPLATE.md` 写批 3 报告**（七节必填），提交，推送并用 `ls-remote` 核验远端 tip；合 `main` 需用户拍板（外部副作用，停止事项）。
- [ ] **Step 8: 里程碑装真机手工试用全链**（`ANDROID_SERIAL=bf353dda adb install -r …`），真机不跑 instrument。

---

## 计划自检（撰写完成后自查，已在文档内落实）

**1. 规格覆盖**：spec §2.1/§2.2/§2.3 → Task 2/3/4；§3 九个组件 → Task 9/10/11/12；§4 图标体系 → Task 1/6/7；§5 深色与三态 → Task 3/4/5/19；§6 六条裁决 → Task 19/20/22/23/24/25 + 补 1 → Task 13–17；§7 移交账 → Task 9/15/17/21/23/26；§8 外刊多源明确不占本轮 → Task 24 Step 3；§9 AC-R-01…08/20…23 → 各任务 Step 与 Task 26 证据表；AC-R-10/11/12 → Task 26 Step 4；§10 风险 → Task 20 Step 1（宿主拆分）、Task 16 Step 2（未核实项现场核）、Task 17 Step 1（testTag 影响面）、Task 26 Step 2（M-B1/M-B2）。
**2. 占位符扫描**：全文无 TBD/TODO/「类似 Task N」；确需现场取值的三处（`MintPalette` 真实成员名、词书选取屏与字母 Rail 的真实文件名、`CardDetailScreen` 四宿主清单）都写成「先 grep 再动，并把偏差写进报告」的具体命令，而非留空。
**3. 类型一致性**：`AppColorSet` 16 字段名、`AppDomain` 七条目名、`AppIcon` 33 枚举条目（32 字形）、`ThemeMode.storageKey`、`InlineNoticeKind`、`SpeakerState`、`PayloadKind`、`MyWordsSection` 在定义任务与消费任务的 Interfaces 块中逐字一致；`gradientStartForDomain`/`shouldUseDarkTheme`/`allowsInEntryContainer`/`noticeColors`/`speakerContentDescription`/`featureStatusBadge` 的签名在 Produces 与测试代码中同名同参。

---

## 里程碑与真机节奏

用户口径「有结果后可以安装的真机上」：**批 1 收口（Task 12 Step 5）、批 2 收口（Task 18 Step 1）、批 3 收口（Task 26 Step 8）** 各装一次 debug 包到真机 `bf353dda` 供手工试用；批 1 装的那次是图标的第一次真机印象检查点。

