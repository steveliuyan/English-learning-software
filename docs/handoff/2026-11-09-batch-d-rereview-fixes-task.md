# 批 D 页面转场 —— scoped 复评修复轮任务书（2026-11-09）

## 一句话结论

批 D 的「#### D-4 终评补口」7 条裁定已全部落成（席 A/B1/B2/B3/B4 + 控制器接管），
一次 **scoped 复评**（覆盖 `911ad0b..cfee0c1`，16 枚提交 / 17 文件 / +1896−72）已跑完，判词是
**With fixes（无 Critical）**：7 条裁定在代码里成立、没有放宽任何断言，但留下 **2 项 Important**
（一枚契约闸的形状盲区 + 一处证据归属写过头）与 **10 处过期行号引用**。本任务书就是这一轮的修复清单，
修完即可回到用户面前做收口请示（推送 + 真机目视 + N-1/N-2 两项待拍板）。

## 基线（开工先复核，别照抄）

- 分支 `stage-5-ui-r2-learning-path`，**不合 `main`**；HEAD `cfee0c1`；工作树净。
- 远端跟踪 ref `origin/stage-5-ui-r2-learning-path` = `5d93cdc`；本批**全程未推送**。
  复核：`git log --oneline origin/stage-5-ui-r2-learning-path..HEAD | wc -l` → 现算 **35**。
- 复评证据包（gitignored 工作区，接手先读）：
  - `.superpowers/sdd/2026-11-06-motion-and-adaptive-layout/review-911ad0b..cfee0c1.diff`（16 枚提交 + stat + 全 diff）
  - 台账同目录 `progress.md`（逐席裁定、事故、读数）；四份席报告 `task-D4fix{A,B2,B3,B4}-report.md`
- 门禁现值（复评时点）：JVM **341 类 / 2553 条 / 0 失败 0 错误 0 跳过**；
  connected `ui.ScreenTransitionInstrumentedTest` 全类硬化后 **Compact 2 趟 + Wide 3 趟 = `OK (9 tests)`**
  （读数已回写 `docs/handoff/2026-11-09-batch-d-page-transition-report.md` §3.7）。

## 待办（按此顺序做，一枚 Important 一提交）

### 1. Important —— 层深闸对 **getter 形态的覆写** 失明（`ScreenTransitionContractTest.kt`）

- 现象：取样正则 `DECLARED_TRANSITION_DEPTH`（`:1249`）只认 `override val transitionDepth = <数字>`。
  若某支写成 `override val transitionDepth: Int get() = 3` 且**不在** `TRANSITION_DEPTH_OVERRIDES`（`:1234`）里，
  `routeDepthRows` 取到 `declaredTransitionDepth = null`（`:1008-1013`），
  而 `depthDeclarationOffenders`（`:1022-1043`）没有 `declared == null && expected == null` 这一臂 ⇒ **零判红**，
  该支的方向判定被静默改掉。判据 ②（`:1045-1056`）只比对静态清单与用例名，也拦不住。
- 为什么会踩：同一个文件里默认访问器就是 getter 形态（`AppOverlayRoute.kt:31`），
  而且该闸自己的检测器 `ROUTE_DEPTH_DEFAULT_ACCESSOR`（`:1243`）认的正是 getter 形态 —— 不对称在同一份文件内。
- 已在清单内的两支**不受影响**：把 `LearningFlow` 的 `= 6` 改成 `get() = 6` 会由 `declared == null && expected != null`
  那一臂（`:1038-1039`）判红。洞只在「清单外的新增覆写」。
- 修法（按本仓既有形状，同族可参考 form-agnostic 的 `DECLARED_NAVIGATION_IDENTITY` `:1259`）：
  1. 增设一枚**存在性**正则（`override val transitionDepth`，不带数字），
     在 `depthDeclarationOffenders` 里补一臂：段内**有声明但取不到数值** ⇒ offender 文案写清
     「写法无法取样：本闸只认数值字面量，表达式形态（如 `get() = mountIndex + 1`）的覆写等于绕开清单」。
  2. 把 `DECLARED_TRANSITION_DEPTH` 放宽到同时认 `= 6` 与 `get() = 6`（两种字面量形态都要能取到值并和清单比对）。
  3. 在合成双边用例 `judgementDepthGateFlagsSyntheticOverrideAndUnpairedNamedEdge`（`:691-760`）里
     **补两条 must-flag**：①getter 形态的清单外覆写；②表达式形态（`get() = mountIndex + 1`）。
     现有 8 个形状从未走过 getter 形态，所以该闸的自证与它 KDoc（`:646`「清单外的覆写……任一形状即判红」）不符。
  4. 顺手核 `DECLARED_MOUNT_INDEX`（`:1248`）是否同形盲区；若挂载序号也有同款风险，一并加一臂并说明
     （本项不强制，判不动就先在注释里如实记下「只钉了数值形态，getter 形态由层深臂兜住」的理由，别静默留着）。
- TDD：**先加探针看红**（当前正则下 getter 形态那条 must-flag 必然不过），再改正则看绿。
  定向跑法：`"$GRADLE" :app:testDebugUnitTest --tests '*ScreenTransitionContractTest*' --rerun-tasks --no-daemon --no-build-cache`。

### 2. Important —— `:198` 那趟红的**配置归属被写成事实**，与原始出处矛盾

三处都写了「那趟红发生在摘掉 `contentKey` 的变异探针配置里，不是 shipped 配置的 flake」：

- `app/src/androidTest/java/com/example/englishlearning/ui/ScreenTransitionInstrumentedTest.kt:229-232`（`ff4177c` 加的「订正」）
- `docs/verification/ac-traceability-matrix.md:82`（`a5a619d` 改的那段）
- `.superpowers/sdd/2026-11-06-motion-and-adaptive-layout/progress.md:274`（台账席 B4 块）

复评给的证据：

- 原始出处 `task-D4fixB2-report.md:96` 把表第 2 行标为「全类（**还原 contentKey 后重装**）`Tests run: 8, Failures: 1`
  —— 红点 `transitionsProduceNoExtraBillableOutbound`（`:198`）」，而 `:88-89` 记的是探针后**逐字还原**（`RESTORE_IDENTICAL`）。
- 唯一留存的探针日志 `b2-connected-red.log` 是 `Tests run: 2, Failures: 2`（红点 `:310` / `:382`）——
  与表第 2 行的**枚数、用例、红点都不同**；全仓 `b2-*.log` 里**没有** `Tests run: 8, Failures: 1` 的原始日志。
- 所以「归属探针配置」这件事**无法证实**，不能当事实写；矩阵里前半句已经承认「未留原始日志」，后半句却仍下定论。

修法（只改措辞，不改代码、不改行为）：三处统一改成实话 ——
**「该趟的配置归属无留存日志可证；既不能算 shipped 配置的实证 flake，也不能断定是探针配置。
shipped 配置下可证的偶发红只有 `f3cc0c3` 的 Wide 全类一趟（`b4-wide-connected.log`，`Tests run: 9, Failures: 1`，
红点在 `pumpUntil` 3s 超时）」**。硬化本身（`ff4177c` 的两道守卫）理由成立、不动。
另在同一遍里给 `task-D4fixB2-report.md:84` 加一句**订正括注**（它引探针为 `Tests run: 1, Failures: 1`，
日志实际 `Tests run: 2, Failures: 2`）——席报告是历史记录，**只追加括注，不改写当时结论**。

### 3. 过期行号 10 处（测试注释里的引用，机器活但必须现算）

本仓规则：文档/注释里的行号必须可复算。复评逐条核过并**已在本任务书里由控制器二次抽查确认**（标 ✓）：

| 位置 | 现引 | 应为 |
|---|---|---|
| `ScreenTransitionDirectionTest.kt:53` | resolver `:23-24`、`:45-48` | `:25-26`、`:47-50` ✓ |
| `ScreenTransitionDirectionTest.kt:66` | `AppScreen.kt:1865`（`onOpenArticle`） | `:1872` ✓ |
| `ScreenTransitionDirectionTest.kt:84` | `AppScreen.kt:1896`（`onFetchFeedItem`） | `:1903` ✓（1896 是该腿注释行） |
| `ScreenTransitionDirectionTest.kt:111` | resolver `:23`/`:24`、`AppScreen.kt:1075-1077` | `:25`/`:26`、`:1082-1085` ✓ |
| `AppOverlayRouteNavigationIdentityTest.kt:24` | `AppScreen.kt:830-832` | `:834-836` ✓ |
| `AppOverlayRouteNavigationIdentityTest.kt:171` | `AppScreen.kt:830-832` | `:834-836` ✓ |
| `AppOverlayRouteNavigationIdentityTest.kt:246` | `AppScreen.kt:1868` | `:1872` ✓ |
| `ScreenTransitionContractTest.kt:847` | `AppScreen.kt:830-832` | `:834-836` ✓ |
| `ScreenTransitionContractTest.kt:1114` | `AppScreen.kt:830-832` | `:834-836` ✓ |
| 裁定 1 的「逐对找到发起点行号」证据条款 | 4 对命名边里 **3 对的证据注释行号不可复算** | 改完上表即满足 ✓ |

`docs/decisions/2026-11-08-page-transition-routing-and-motion.md:260/263/276` 仍写 `:1865` —— **合法保留**：
该文件「2026-11-09 修正」节 `:361` 自定规则「历史读数冻结、只改活 KDoc」。测试注释没有这层豁免，所以要改。

改法注意：只动注释文本；`ScreenTransitionContractTest.kt` 的 `MOUNT_CONDITION_EXEMPTIONS`（`:1201-1202` = 583/587）
是**按行区间钉源码**的闸，测试文件自身行号变化不影响它，但**别顺手重排 `AppScreen.kt`**——
那会移动五枚退场闸（`799/1094/1109/1304/1744`）与 `consumeShareRequest`（`:1314/:1317`）以及一堆刚核对过的引用。

### 4. 在册债（本轮不修，登记即可）

- `AppOverlayRouteResolver.kt:11` 的 KDoc「与 `AppScreen.kt:728`–`2020` 原链逐字一致」——链现在跨 `782`→`~2330`。
  矩阵 `:82` 已把这枚 KDoc 记为「活文档债在册」，由契约闸判据 1/3 兜住。要修就单独一席，别混进本轮。
- 计划 `docs/superpowers/plans/2026-11-06-motion-and-adaptive-layout.md:1363/1364` 引的
  `AppScreen.kt:1911-1917`、`ScreenTransitionContractTest.kt:788` 已是死指针；`:1312-1323` 的「历史措辞指针」块没点名这两处。
  该段属执行记录不回头改，**若要动就在指针块里补这两条**，不改裁定正文。

## 验收与收口（做完以上才许宣称收口）

1. 门禁三连（一次只跑一条 Gradle，并发会因残留进程锁 `R.jar` 假红）：
   ```bash
   GRADLE="$HOME/.gradle/wrapper/dists/gradle-8.13-bin/5xuhj0ry160q40clulazy9h7d/gradle-8.13/bin/gradle.bat"
   "$GRADLE" :app:assembleDebug :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin --no-daemon --no-build-cache --rerun-tasks 2>&1 \
     | grep -E 'FAILED|^e:|BUILD|tests completed' ; exit ${PIPESTATUS[0]}
   ```
   基线只增不减（现 341 类 / 2553 条）；按 XML mtime 计数，别数陈旧文件。
2. 本轮若碰了 `androidTest`（第 2 条会碰注释）：`assembleDebug` + `assembleDebugAndroidTest` →
   `adb -s emulator-5554 install -r` 双包 → `am instrument -w -e class com.example.englishlearning.ui.ScreenTransitionInstrumentedTest
   com.example.englishlearning.test/androidx.test.runner.AndroidJUnitRunner`，Compact 与 Wide
   （`adb -s emulator-5554 shell wm size 2560x1600`，收口 `wm size reset`）各至少一趟。
   **禁用 `connectedDebugAndroidTest`**（会清空设备数据）；真机 `bf353dda` 本轮零命令。
3. 分域提交（带 `-c user.name=steveliuyan -c user.email=2021250388@qq.com`，逐文件 `git add`，禁 `git add -A`、禁 `--amend`、禁 `--no-verify`）。
4. 回写：报告 `docs/handoff/2026-11-09-batch-d-page-transition-report.md` §3/§4 与矩阵 `:82` 的受影响句子；台账追加本轮块。
5. **推送要逐批请示用户**，推送后 `git ls-remote` 对 `git rev-parse HEAD` 核验，把远端 tip 回填报告 §4；未核验不得宣称备份完成。

## 修完之后交回用户的三件事（不要替他们决定）

1. **推送请示**：本分支 ahead 现算 35（+ 本轮新提交），含 N-1 那 6 枚（任务 #75 仍挂着）——推不推。
2. **真机目视**（`bf353dda`，只能人眼）：§5(a) 的全量条目 ——
   29→32 方向复现 / TalkBack 是否只读一屏 / 转场跟不跟手 / 底栏与 Wide 侧栏随全屏子页进出 /
   中途连点连按返回（决定要不要输入闸）/ `clip = true` 贴边裁切 / 顶档 3.0 字号下的转场排印。
3. **N-2 待拍板**（任务 #70）：词典词义详情谎报「AI 问词从这里进入」——改文案还是接入口。
