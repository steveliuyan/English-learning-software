# 2026-10-19 UI 重设计 · 批 1（设计系统底座）工作报告

## 1. 本次目标

用户原话：「继续完成开发」——按 `docs/superpowers/plans/2026-10-17-ui-redesign.md` 用子代理逐任务推进 UI 重设计轮，
本棒收口**批 1 · 设计系统底座（Task 1–12）+ 整分支终审 + 一轮修复 + 一席窄复评**；并明确
「不用合 main，先停在分支上」「有结果后可以安装的真机上」。

## 2. 完成了什么

**主题与令牌（Task 2–5）**
- 新增 `ui/theme/AppColors.kt`（浅/深两套中性与域色 token）、`ui/theme/MintGlassTheme.kt`（全站唯一 `MaterialTheme` 装配点）；
  `AppPalette.kt` / `AppShape.kt` / `AppType.kt` / `MintPalette.kt` 改为**组合期读取**（Task 3 的主切换：
  既有 `Mint*` 调用点零重写即获得深色能力；读点面可重算 `grep -rn 'Mint[A-Z][A-Za-z]*' app/src/main/java --include=*.kt | wc -l`，
  本文件不抄具体数——该数随任何一次改动漂移，Task 3 当时报的 817 如今已是 828）。
- 新增 `ui/theme/AppThemePreference.kt`：主题三态偏好（跟随系统/浅色/深色）落 SharedPreferences，`MainActivity` 用
  `rememberAppThemeMode()` 活链路重绘，并用真像素（不是布尔）验证两条装配通道都到位。
- `CardDetailScreen.kt` / `WordCardScreen.kt` / `TodayPlanScreen.kt` 各只有少量改动：把 `Canvas` 绘制 lambda（`DrawScope`，组合期外）
  里直接读的静态中性令牌提到组合期读好再带入。**批 1 未碰任何业务屏版面。**

**图标（Task 6–7）**
- 新增 `AppIcons.kt`（33 枚 B 案图标的两层路径数据入库）、`AppIcon.kt`（运行时组装）、`IconBadge.kt`（入口/行内两种呈现 + 弱档）。
- `ui/` 下 `\bIcons.` 引用为 0，且不新增 `material-icons-extended`（lockfile 零变化）。

**共享组件（Task 9–12）**
- 新增 `AppScreenHeader`、`ScreenUnavailable`、`InlineNotice` + `InlineNoticeAction`、`EntryRow`、`NotConfiguredCard`、
  `ConfirmDestructiveDialog`、`OutboundConfirmDialog`、`WordRow`、`SpeakerButton`（共 9 个组件文件，均落在 `ui/components/`）。
- 每枚都带几何/墨色/顺序/可证性四套 JVM 契约，外加 connected 真测（48dp 量在**可点节点**上，不靠 padding 凑）。

**债务棘轮（Task 8 + 12）**
- `RawColorLiteralRatchetTest`（裸 `Color(0xFF…)`）、`DarkTokenConsumptionRatchetTest`（深色令牌消费）、
  `SharedComponentUniquenessRatchetTest`（四族组件份数）三件上线：seed 是**双向精确相等**的债务台账，只减不增、
  禁止把新增固化进 seed，且不管「就地改既有组件取值面」（预登记豁免，Task 14 改 `PillButton` 本体不受挡）。
- `DesignTokenContractTest` 追加**防死 token 闸**：浅/深两套同名 token 逐字相同即判红，除非在豁免名单点名给依据。

**文档**
- 新增 `docs/verification/ui-redesign-2026-10.md`（AC-R 证据表 + 门禁/connected 重算命令 + 图标一览目视清单 + 「仍待用户目视」四项）。
- 新增 `docs/decisions/2026-10-18-ui-redesign-contrast-and-icon-layer-rulings.md`；改 `docs/decisions/2026-10-18-ui-redesign-icon-duotone.md`。
- 改 `docs/superpowers/specs/2026-10-17-ui-redesign-design.md`、`docs/design/visual-style-guide.md`、`docs/third-party-notices.md`、
  计划本身（批 1 期间落进编号裁决 `R-9.1`…`R-12.3` 与面向批 2 的 `R-14.1`；本次实跑 20 条，判定以重算为准：
  `grep -oE 'R-[0-9]+\.[0-9]+[a-z]?' docs/superpowers/plans/2026-10-17-ui-redesign.md | sort -u | wc -l`；另有批 2 开工判据一段）。

**质量流程**
- 逐任务：新实现者子代理 + 独立评审 + 修复轮（Task 7/8/9/10/11/12 各走过修复或复评）。
- 整分支终审按**五席分域**（B / C1 / C2 / A / D）只读并行，报告齐后控制者**逐条 Important 亲自复算站点与机理**，
  合并成**唯一一轮**修复派发（F1–F8 + m1–m12），修完派**一席窄复评**只看修复 diff，其 1 Important + 5 Minor 已在本棒就地裁决并闭环。

## 3. 验证记录

**JVM 门禁（离线、无守护进程、无构建缓存，`--rerun` 强制真跑；命令见验证文档「门禁与基线」）**
- `BUILD SUCCESSFUL`，`suites=223 tests=1654 skipped=0 failures=0 errors=0`（本行是运行日志，判定以重算命令为准）。
- 基线只增不减：`1646`（Task 12 修复轮 1）→ `1653`（批 1 终审修复轮）→ `1654`（窄复评收口）。
- `verifyThirdPartyNotices` 打印 `Validated 76 third-party notice entries.`；`git diff --name-only main..HEAD | grep -c lockfile` = **0**。
- 结果 XML 文件数 = suite 数 = 223（无孤儿 XML，五元组不虚高）。
- 越界核验三条实跑：`AppTabTest`/`di`/`learning` 零改动；测试侧改动全在 `ui/` 下；生产侧越出 `ui/` 只允许 `MainActivity.kt`。

**connected（只在 `emulator-5574`，`adb install -r` + `am instrument` 按类跑；绝禁 `connectedDebugAndroidTest`）**
- 批 1 清单 27 用例 0 失败：`AppScreenHeaderTest` OK(4)、`IconGalleryRenderTest` OK(3)、
  `EntryRowTest`+`InlineNoticeTest`+`NotConfiguredCardTest`+`ConfirmDestructiveDialogTest`+`OutboundConfirmDialogTest` OK(18)、`ThemeModeSwitchTest` OK(2)。
- 终审修复轮复跑：`AppScreenHeaderTest` OK(4)、`EntryRowTest` OK(5)，新增 `WordRowTest` OK(1)
  （大字号右列宽度上限；**变异态先红**：摘掉 `widthIn(max = 120.dp)` 后实测 meta 195.05dp / lemma 177.52dp）。

**真机里程碑装机（用户授权的观察面，非测试面）**
- `adb -s bf353dda install -r app/build/outputs/apk/debug/app-debug.apk` → `Success`；`monkey` 注入启动后
  `dumpsys window | grep mCurrentFocus` 校验落在 `com.example.englishlearning/.MainActivity`；**未跑 `am instrument`、未装测试包**。
- 系统深色用 `cmd uimode night yes` 切换取证、看完恢复 `night no`。**主题层深色链路在真机成立**（根底/页头/卡片随系统翻深，活链路无需重启），
  但业务屏半迁移态暴露出一处可读性缺陷 —— 现象、机理（定义行）与重算命令记在验证文档末节，判读见 §5。

**RED→GREEN 与变异证据（本棒新钉的判据全部做过「不改生产就永远绿」的反证）**
- F7 失明面用例：族 1 :550 / 族 2 :572 / 族 4 :595 分别由「摘掉出口条件」的定向变异打红；
  夹具自检（族 1/2 各 `assertEquals(1, countMatches(…))`）保证不是空转。
- N-3 顺序判据收窄：新增「同文件更早处有无关 `.padding(`」夹具，收窄前 `SharedComponentGeometryContractTest` :146 与
  `WordRowSpeakerContractTest` :289 **先红**，收窄后判合法 —— 批 2 在链首加 padding 不会假红。
- **detekt/ktlint 不作门禁**（`docs/decisions/2026-09-19-static-analysis-and-test-baseline.md`：两者在基线上就不通过，
  ktlint 基线 610 条、覆盖 34 个文件）。本棒未重跑它们，因此不宣称增量；
  批 1 新增文件若有新违规属存量债族内（`MagicNumber`/`FunctionNaming` 等既有口径），不视为回归。

## 4. Git 状态

- 分支：`stage-5-ui-r1-design-system`，基线 = `main` 的 tip `46f4dee`（分支从计划提交处切出，与 main 无分叉）。
- 提交数：`git log --oneline main..HEAD | wc -l`（本棒未写绝对数，该命令即权威）；本地 tip 以
  `git rev-parse HEAD` 为准（关键三枚：测试侧收口 `eaebb34`、文档回填 `837f28a`、本报告 `b3f54b8`）。
- **未推送**：`git ls-remote --heads origin` 只有 `main`（`46f4dee`）与 `stage-1-f1-02-new-word-ordering`（`7c16ec1`），
  本分支不在远端。推送与否属用户拍板项（R-12.2：推送从不自主执行）。
- 未提交遗留文件：**无**（工作树干净；`.superpowers/sdd/` 下的台账、简报、报告与评审包是 git-ignored 的本地产物，不入库）。
- 未合 `main`：按用户指令停在分支上。

## 5. 未完成 / 遗留问题

**批 2 / 批 3 的既定范围（不是缺陷）**
- AC-R-07（`fontScale 2.0` 六屏 connected）、AC-R-10（六屏 360dp 浅/深截图比对）、AC-R-11（玻璃层与域色容器深色可读性实测）、
  AC-R-12（TalkBack 重走）状态均为「未开始」。
- 三件棘轮的 seed 存量**未清零**：裸 hex 台账（可重算：`grep -A8 'RawColorSeedAllowlist: Map' app/src/test/java/com/example/englishlearning/ui/RawColorLiteralRatchetTest.kt`）
  + `launch/BrandLaunchActivity.kt` 品牌渐变三段；深色令牌消费台账（`DarkTokenSeedAllowlist`）；
  四族组件 seed（`SharedComponentUniquenessRatchetTest.kt` 的 `componentFamilySeeds`）。
  **本文件不写这些台账的绝对条数** —— 它们由测试当场做双向精确相等断言，写进文档就是一份会漂的副本。清零归批 2 逐屏接线、批 3 收口。

**需要用户拍板 / 人眼判定的**
- **推送与否**（本分支全部提交目前只有本机一份，AGENTS.md 要求阶段收口推送作异地备份）。
- 装真机后的四项目视（`docs/verification/ui-redesign-2026-10.md` §仍待用户目视）：① 六档 `AppType` 摘掉 `lineHeight` 后正文是否偏紧
  （判「太紧」则批 2 在装配点补，并把取值先写进规格 §2.2）；② 排印换档整体观感；③ M3 控件默认色由库紫转薄荷 +
  Card 圆角 20→18dp + `colorScheme.outline` 换 `text2` 是否可接受；④ 跟随系统的深色档与 `fontScale 2.0` 下底导/设置页不裁。
- AC-R-11 的深色玻璃前景：批 1 只把口径纠正为「容器填充与面板内前景墨色必须**同批**迁移」，四个 `AppPalette.GlassFill` 消费点一律**未迁**。
- **深色半迁移态已在真机肉眼可见，需要用户拍推进顺序**（2026-10-19 里程碑装机实勘，`bf353dda`，只 `install -r`、未跑 instrument；
  详见验证文档末节「真机深色实勘」）：Task 3 让 `Mint*` 墨色改成组合期读取后**跟主题走了**，而业务屏容器仍写
  `AppPalette.Surface`（`AppPalette.kt:12` 写死 `Color(0xFFFFFFFF)`）**不跟主题走** → 系统深色下今日计划屏的
  「今日学习进度」卡呈**白底 + 主题浅墨**，三行文案几乎看不清，两枚计数卡仍是浅档外观。
  这是台账在册的深色消费债（不是新缺陷类别），但**开系统深色的用户在批 2 收完核心屏之前都会看到半深色界面**。
  控制者建议：**批 2 开工第一步先把全站静态容器读点（`AppPalette.Surface`/`Background`/`TextPrimary`…）一次性改读 `appColors()`**，
  让深色立刻整体可用，再逐屏做版面；替代方案是按原计划逐屏迁（深色要到 Task 14/16 才在核心屏可用）。**未拍板前不越界改**——
  它会同时动 seed 台账与业务屏，属批 2 评审面。

**登记在册的技术债（批 2 派单时必须带上，勿当已完成）**
- **批 2 开工判据（F7 / D-I-1）**：`SharedComponentUniquenessRatchetTest` 族 1/2/4 各带「本文件调了共享组件即整体合规」出口，
  出口同时是**已知失明面**——接线后同屏残留的私有 forbidden 形态对棘轮整体隐形。
  所以删 seed 行的判据是「**该屏 forbidden 读点清零**」，绝不是「补一行 `AppScreenHeader(` 就准删」。已写进计划批 2 段首。
- **WordRow 族顺序判据仍是 F1 类字面邻接正则**（`.then(touchModifier)` … `heightIn(min = 48.dp)` … `.padding(`，160 字符窗口），
  **未经 N-3 式链窗口收窄**。批 2 若重排 WordRow 的 modifier 链（把 padding 提前到链首）会假红：
  Task 15 派单时要么先按 N-3 口径改成本条链窗口，要么明确「批 2 不重排该链」。
- **浅套 `text-2`/`tint` 余量仅 0.047**：任何 tint 梯度重排必须「先改裁决再改码」。
- Task 14 携带 R-14.1（`PillButton` 三档墨色实算矩阵）与 `PillButton` 静态 ink 债；`SpeakerButton.kt:138` 有一枚 no-op `spacedBy`（C1 M-4）。
- `SharedComponentUniquenessRatchetTest.kt:13` 的 KDoc 链接写 `[ComponentFamilySeeds]`，实际属性是 `componentFamilySeeds`
  （大小写不符的失效链接，纯文档级）。批 2 顺手改，不单独开轮。
- Task 11 的三条未现场核验项、Task 7 的 R2-1/R2-2、Task 8 的 MI-a/b/c、今日计划弱档条目、
  五席各自的结转 Minor（B M-1/M-2/M-3/M-4、C2 M-1…M-4、Seat A M6/M8 与 `DesignTokenContractTest.kt:325`、
  Seat D M-5 后半的 `(?m)^fun X(` 锚点失明）——全部记在 `.superpowers/sdd/2026-10-17-ui-redesign/progress.md`。
- `docs/third-party-notices.md` 的 `app-authored-ui-illustrations` 条目 NOTICE 位置仍标「待建」（批 3 收）。

## 6. 下一任接手须知

**第一步**：读 `docs/verification/ui-redesign-2026-10.md`（口径 + AC-R 表 + 重算命令）与
`.superpowers/sdd/2026-10-17-ui-redesign/progress.md`（**七轮**台账，含批 2 开工判据与结转清单），
再读计划批 2 段（标题「批 2 · 核心学习路径五屏（Task 13–18）」，勿按行号定位）。批 2 从 `stage-5-ui-r1-design-system` 的当前 tip 切
`stage-5-ui-r2-learning-path`（用户若不推不合 main，则批 2 分支基线仍是批 1 tip，不是 main）。
每屏任务的固定收尾三步（贴 tag 清单 / 迁本屏静态读点并递减 seed / 跑本屏 connected）+ 上面那条「删 seed 判据」必须一起走。

**环境与纪律坑（本棒踩实过的）**
- **绝不用 `./gradlew`**（wrapper 联网下载必失败）。从 `gradle-wrapper.properties` 发现版本并强制 `-bin`：
  `V=$(grep -oE 'gradle-[0-9]+\.[0-9]+' gradle/wrapper/gradle-wrapper.properties | head -1)`；
  `GRADLE=$(ls -1 "$HOME/.gradle/wrapper/dists/$V-bin/"*/"$V/bin/gradle.bat" | head -1)`。禁止取最新目录（本机另有 9.2.1）。
- 管道必须带 `; exit ${PIPESTATUS[0]}`，否则构建失败误报成功。
- **`--no-build-cache` ≠ 强制重跑**：`testDebugUnitTest` 仍可能 UP-TO-DATE，产物停在上一轮。要证据就 `--rerun`。
- **多席/并席期间禁 `rm -rf app/build/test-results/testDebugUnitTest`**（别的会话在读同一批 XML 取证）；
  先 `ls TEST-*.xml | wc -l` 与 suite 数对账，发现孤儿只记名字、等无并发读者再删并重跑。
- connected 只在 `emulator-5574`，`am instrument` 前 `dumpsys user | grep State:` 确认 `RUNNING_UNLOCKED`（否则 UI 驱动全无效）。
  **绝不在真机 `bf353dda` 上跑 instrument 或装测试包**；真机只 `adb install -r app/build/outputs/apk/debug/app-debug.apk` 手工试用（MIUI 会自动卸载第三方 adb 安装包，装完立刻测）。
- **文档禁写自漂移计数**：凡「共 N 处 / 几枚 / 几个文件几处」一律写成可重算命令；实跑数字只作运行日志并标明。
  提交信息里把既有资产报成本轮产出是本棒最贵的教训（F7）——**宣称前先 `git log -S` / `git blame -L` 归因**。
- 契约测试用「剥注释后断言源码内容」时注意：字符串字面量含 `*/` 会吞掉块注释剥离正则；夹具名与 `dropCall` 的目标 token
  别撞子串（`dropCall(…, "WordRow(")` 会连带删掉 `SyntheticWiredWordRow(` 的声明行，本棒踩过）。
- 推送：`git -c credential.interactive=false push origin <branch>`，代理已在全局 git config；推完必须
  `git ls-remote` 与 `git rev-parse HEAD` 双向核验才敢宣称备份。git 身份未配置，提交须带
  `-c user.name=steveliuyan -c user.email=2021250388@qq.com`，并按功能域逐个 `git add`（禁 `-A`/`.`）。

**关联文档**：`docs/superpowers/specs/2026-10-17-ui-redesign-design.md`（规格）、
`docs/superpowers/plans/2026-10-17-ui-redesign.md`（计划，含批 1 期间落地的编号裁决 `R-9.x`…`R-12.x` 与面向批 2 的 `R-14.1`）、
`docs/decisions/2026-10-18-ui-redesign-contrast-and-icon-layer-rulings.md`、`docs/decisions/2026-10-18-ui-redesign-icon-duotone.md`、
`docs/design/visual-style-guide.md`、`docs/verification/ui-redesign-2026-10.md`、
视觉稿 `visualizations/`（本会话产物，非仓库文件）。

**安全红线（不变）**：API Key 只在 Keystore 支撑的凭据区，不进 Room/日志/崩溃报告/剪贴板/导出包/文章元数据/截图诊断；
密钥、挑战码明文、配对 QR 截图一律不入库（放 `.git/qa-scratch/` 或本地忽略区）；发布 keystore 与 `local.properties` 四键绝不入库、不进报告；
迁移失败绝不删库、绝不 `fallbackToDestructiveMigration`；未获用户确认不得新增账号/云同步/订阅/广告/远程代码执行/任意 AI 请求覆盖；
禁 force push、禁推其他远端、禁擅删远端分支；破坏性动作先取得用户确认。

## 7. 用户确认过的决策

- 功能面（阶段 0–4 + 阶段 5 SP-A~E + F5-03 无障碍）已完成，本轮只做 UI 重设计；无障碍的硬约束随移交账进入本轮。
- UI 不满意的是「不好看」，方案走**先高保真视觉稿、后代码级实现计划**，计划分 3 批（批 1 底座 / 批 2 核心学习五屏 / 批 3 其余屏与入口分组重排）。
- 图标定 **B 案**（双色层次 + 域色渐变容器），5 枚只描边的豁免；风格延续既有薄荷玻璃但重做版式层级。
- 浅薄荷底与品牌渐变上**禁止白字**，前景走 `on-mint`（≈4.6:1）；破坏性操作用色统一走 `error`/`surface` 配对（作废 `errorFill` + 白字配方）。
- 推进方式：**用子代理逐任务执行 + 独立评审**，批末整分支终审；不停下来问，有结果再汇报。
- **本轮不合 main，先停在分支上**；里程碑构建**可以装真机**（只手工试用，测试包不上真机）。
- 交流用中文。
