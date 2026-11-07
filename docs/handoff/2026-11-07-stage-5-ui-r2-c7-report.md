# 批 C-7 工作报告：骨架与 shimmer 色档整改到可见档（2026-11-07）

分支 `stage-5-ui-r2-learning-path`；设备 `emulator-5554`（API 30）；真机 `0123456789` 本轮未触碰。

## 1. 本次目标

批 C-6 把骨架的结构判据跑满（门禁全绿、`scale=0` 定格逐字节等于静息骨架、`scale=1` 连拍证明光带在扫），
但实机像素取样测到骨架条相对容器只差 1–5 个通道级、光带 ≤3 级——**四档里三档读作空白页，感知判据不过**。
用户裁决「提到可见档」，并把两条口径钉在本批：光带机制 = **现有 token + 提亮混合模式**（不新增 palette token、不按主题分支），
可见度 = **最大单通道差 ≥16 级，与 C-6 实测唯一看得见的卡壳档同强**。

本批要把这条感知判据**提前成 JVM 判据**（改回不可见档必须当场红，而不是等下一轮截图册），
并按裁决重拍深浅两套截图册取证；范围止于此，批 D 页面转场未开工。

## 2. 完成了什么

1. **色档唯一落点 `ui/components/SkeletonInk.kt`（新增）**：`fill` = `outline` 满值、`band` = `tint`、`bandBlend` = `BlendMode.Screen`。
   `Skeleton.kt` 的条与前导圆点、`Shimmer.kt` 的光带各自内联挑色的两处写法收拢到这一个对象——
   JVM 侧要能对「渲染后差几级」建模，前提是取色只有一处可改。发丝线与 `framed` 行壳的 `outlineSoft` **未动**
   （那是与真实行逐字相同的镜像件，F5-05 的判据就是像不像）。
2. **光带从「画暗」改成「提亮」**：`SrcOver` 把方向交给了 palette 明暗顺序（浅色卡面上 `tint` 压白是画暗，深色档 `tint` 比骨架条还暗，
   两档方向还相反）；`Screen` 的性质是结果只可能比底色亮，于是深浅两档用同一份算式成立，不需要 `if (isDark)`。
   `ShimmerSpec.peakAlpha` 0.16 → **0.6**（0.55 也过线，留作目视嫌抢眼时的回落档）；周期、行程、首末相位归零语义一字未改，
   `scale=0` = 静息骨架这条设计目标原样成立。
3. **新判据 `SkeletonVisibilityContrastTest`（6 条，先红后绿）**：浅档条 / 深档条 / 两档光带 / 机制必须是提亮 /
   **反空转（C-6 旧写法必须被同一条阈值按量级与方向双双判死）** / 唯一落点锚。
4. **既有判据补一条扫面闸**：`ShimmerSpecContractTest` 的禁令族原先只扫 `Shimmer.kt` / `Skeleton.kt`，
   新增的色档落点不进去就等于「色档不许写在用法点」管不到真正写颜色的那个文件；按 TDD 先红（名单只有两枚）后绿（加入第三枚）。
   `SkeletonFamilyContractTest` 的合规夹具示例从已退役的 `outlineSoft.copy(alpha = 0.22f)` 换成 `SkeletonInk.fill(appColors())`
   ——退役色档留在「合规形状」里会被下一个人当范例抄；判据本身未放宽。
5. **截图册按深浅两档各重拍一次**：7 枚规格 × `scale=0` / `scale=1` 各 12 帧，另补密拍 26 帧两档各一份，
   共 610 帧落 `.git/qa-scratch/c7/shots/`（gitignored，不入库）。深色靠 `cmd uimode night yes` 翻系统，
   `MintGlassTheme` 默认 `ThemeMode.System` ⇒ **零改生产码**。读数与取样坑登记进 `docs/verification/ui-redesign-2026-10.md` §批 C-7。

## 3. 验证记录

### TDD 红→绿

```bash
GRADLE=$(find "$HOME/.gradle/wrapper/dists/gradle-8.13-bin" -maxdepth 4 -name gradle.bat | head -1)
"$GRADLE" :app:testDebugUnitTest --tests 'com.example.englishlearning.ui.<类名>' --offline --no-daemon --rerun-tasks
```

| 记录 | 内容 |
|---|---|
| `.git/qa-scratch/c7/red.log`（17:15） | `6 tests completed, 4 failed`——红在色档不够（两条 fill 腿 + 两条光带腿），实现未改 |
| `.git/qa-scratch/c7/gate.log`（17:22） | `2466 tests completed, 2 failed`——**这条红是判据自己的错**：两条 fill 腿比的是「token vs 它自己的渲染结果」，而 `fill` 取满值 alpha=1，该差结构上恒等于 0。改判成「合成后的落色 vs 容器本色」后绿，教训写进 `visibility()` 的 KDoc |
| `.git/qa-scratch/c7/red-skeletonink-scan.log`（17:57） | `tests="7" failures="1"`，失败腿 `the colour tier file is inside the offender scan` |
| `.git/qa-scratch/c7/green-scan.log` | `tests="7" failures="0"` |

### 门禁（五任务一条命令，`--offline --no-daemon --no-build-cache --rerun-tasks`）

```bash
"$GRADLE" :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest \
  :app:compileDebugAndroidTestKotlin verifyThirdPartyNotices --offline --no-daemon --no-build-cache --rerun-tasks
```

`BUILD SUCCESSFUL in 4m 49s`，`GATE_EXIT=0`，**332 类 / 2467 条 / 0 失败 / 0 错误 / 0 跳过**
= 批 C-6 基线 331 类 / 2460 条 **+ 1 类 + 7 条**（`SkeletonVisibilityContrastTest` 6 条 + `ShimmerSpecContractTest` 新增的扫面闸 1 条），
基线只增不减。日志 `.git/qa-scratch/c7/gate3.log`；五元组现算命令：

```bash
python - <<'PY'   # 类数 / tests / failures / errors / skipped 一次算全
import glob,re
xmls=glob.glob('app/build/test-results/testDebugUnitTest/TEST-*.xml'); t=f=e=s=0
for p in xmls:
    m=dict(re.findall(r'(tests|failures|errors|skipped)="(\d+)"',open(p,encoding='utf-8',errors='replace').read(400)))
    t+=int(m.get('tests',0)); f+=int(m.get('failures',0)); e+=int(m.get('errors',0)); s+=int(m.get('skipped',0))
print(f'类={len(xmls)} tests={t} failures={f} errors={e} skipped={s}')
PY
```

中途两枚日志是过程证据，不当最终读数：`gate.log` 是判据写错腿那次的红（`2466 completed, 2 failed`），
`gate2.log` 是判据修好、扫面闸未补时的绿（332 类 / 2466 条）。

### 实机像素读数（两套主题各 7 枚规格，判据线 ≥16 级）

| 档 | 骨架条落色 | 条 vs 页底 | 条 vs 卡面 | 光带峰值 vs 条 | `scale=0` 帧种数 |
|---|---|---|---|---|---|
| 浅色（页 `#F1F4EF` / 卡 `#FFFFFF`） | `#DDE5DD` = `outline` | 20 | 34 | 18–19（提亮） | 每规格 1 种 |
| 深色（页 `#101814` / 卡 `#161F1A`） | `#2A3931` = `outline` | 33 | 26 | 19–20（提亮） | 每规格 1 种 |

对照 C-6：1 级（条 vs 页）/ 4 级（条 vs 白卡）/ 光带 ≤3 级且浅色卡面上方向是**画暗**。
逐规格表、算式对照表与复算命令在 `docs/verification/ui-redesign-2026-10.md` §批 C-7（`calc.py` / `analyze.py` / `shoot.sh` / `burst_dense.sh`）。

**取样口径本轮补了四条护栏**（都踩过）：短促采样欠峰（`screencap` 0.15–0.3s 落在正弦半波哪一格决定单帧抬升，
12 帧把 `card` / `stat-grid` 报成 14 / 11，密拍 26 帧两枚都到 19 ⇒ 是抽样不是渲染）；burst 第 1 帧是上一窗口画面须按页底色过滤；
骨架条众数要同时排除页底与卡面；光带峰值只在静息态确为骨架条的像素上取且只计提亮方向。

### connected

`emulator-5554` 单腿 compact UI 包全量（`-e package com.example.englishlearning.ui`，「UI 包全量」口径与批 B/C-6 可比）：
两枚门禁产物 `install -r` 均 `Success`（数据保留），前置实测 `Physical 1080x1920 @420` 无 Override、`font_scale=1.0`、
`animator_duration_scale=1.0`、`ui_night_mode=1`。

```bash
bash .git/qa-scratch/c7/connected_c7.sh
# → Time: 504.914  OK (588 tests)（墙钟 507s，EXIT=0，全流零 F/E 标记）
```

588 与批 C-6 **同枚数**：本批零新增 instrumentation 用例，截图册的取证类 `SkeletonShimmerEvidenceTest` 仍是 7 枚方法
（日志 `:.......` 恰 7 点），这腿的增量是同一族判据在新色档下重跑一遍。wide 腿未重跑，理由见 §5。

### 设备复原

`ui_night_mode=1`（= no/浅色）、`animator_duration_scale=1.0`、`font_scale=1.0`、
`wm size` / `wm density` 回 `Physical 1080x1920 @420` 无 Override。

## 4. Git 状态

- 分支：`stage-5-ui-r2-learning-path`，未合 `main`（口径：不合）。
- 开工基线 = 收口前远端 tip：`e7a1dab`（批 C-6 收口），`git rev-list --count origin/<branch>..HEAD` 开工实测 **0**，
  即批 C-4c/C-5/C-6 已推送，本批是在已推送基线上叠的新一批。
- 本批提交（按功能域分三枚，`git add` 逐文件；`feat` 先落保证每枚单独可绿，`test` 那枚是判据本体）：
  `69ab2fd`（feat(ui)：`SkeletonInk.kt` 新增 + `Skeleton.kt` 两枚填充点 + `Shimmer.kt` 取色/混合模式/`peakAlpha`）
  → `d6ce08e`（test(ui)：`SkeletonVisibilityContrastTest.kt` 新增 6 条 + `ShimmerSpecContractTest` 扫面闸 + `SkeletonFamilyContractTest` 合规夹具换例）
  → 本报告所在 docs 枚（决策册 §6 与 §4 补条 + 验证册批 C-7 那节 + 风格指南一句口径 + 计划册 C-7 行 + 本报告），零 `app/src/main` 改动。
- 工作树未入库物证：`.git/qa-scratch/c7/`（610 帧截图、6 份 gradle 日志、4 份脚本）按红线留在忽略区，
  零密钥、零挑战码明文、零用户数据。
- 推送状态见 §5：**逐批请示**，本报告写完未推。

## 5. 未完成与遗留

- **推送待用户点头**（口径「推送逐批请示」）。
- **wide 腿未重跑**：C-7 只改色档与取色机制，不改版面、不改尺寸类判据；批 C-6 的 wide UI 全量（`OK (588 tests)`）仍是同族判据的最近证据。
  本批只补 compact UI 包单腿。若用户要 wide 复验，走 `.git/qa-scratch/c6/connected.sh` 的四格口径即可。
- **待用户目视**（人眼判，本棒不替判）：
  1. 浅档骨架条从 1 级跳到 20 级，观感是否偏重（现在与 `outline` 描边同色，只是不带 alpha）；
  2. 光带 `Screen` 提亮在白卡面上是否过亮（浅档峰值 +19 级、落色 `#F0F4F0`）；
  3. 嫌抢眼时的回落档：`peakAlpha` 0.6 → 0.55（算式 17/18 级，仍过线），与填充档互相独立。
- **累计未销的目视项**（前几批结转）：C-4b 两问、C-4c 按张计费句、批 B 四项、C-5「闪屏后进今日页的骨架时长」（量它要在 `TodayPlanViewModel.Ready` 插临时桩，两轮未做）。
- **#14 真机四项目视**仍开放（真机只手工试用，不装 instrumented 包）。
- 下一批 = **批 D 页面转场**（用户已选）。

## 6. 下一任接手须知

1. 骨架/shimmer 的色档与混合模式**只能在 `SkeletonInk.kt` 改**；使用点出现 `outlineSoft.copy(` 或裸 hex 会红
   （`ShimmerSpecContractTest` 扫 `Shimmer.kt` / `Skeleton.kt` / `SkeletonInk.kt` 三枚，`SkeletonFamilyContractTest` 扫使用点）。
2. 可见度判据是**最大单通道差 ≥16 级**的经验锚，**不是** WCAG 3:1；3:1 那把尺子只管图标/非文本信息载体
   （`IconBadgeContrastContractTest`），别拿它去量骨架，pastel token 里没有一枚够得着。
3. 量「渲染后可见度」时比较对象必须是**合成后的落色 vs 容器本色**，不是 token vs 落色（满值 alpha=1 时后者恒为 0，会写出必红的判据）。
4. 光带读数只能取**区间上限**且必须密拍；12 帧短促连拍会欠峰，看着像窄条规格不达标。
5. 深色档取证用 `adb -s emulator-5554 shell cmd uimode night yes`（翻完记得回 `no`），生产零改动；
   `secure ui_night_mode` 的值是 1=no、2=yes，别看反。
6. 环境口径未变：本机 Gradle 8.13 `-bin`（勿用 `./gradlew`）、管道要 `${PIPESTATUS[0]}`、
   `--rerun-tasks` 才算真跑、门禁 = JVM 单测全绿而非 detekt/ktlint。
   本轮新增一条 Windows 侧坑：并发跑第二枚 gradle 时 `processDebugResources` 会因 `R.jar` 被占用而 `Couldn't delete`，
   等前一轮结束或改串行即可，不是代码问题。
7. 必读：`docs/decisions/2026-11-07-loading-skeleton-shimmer-prefetch.md`（§6 是本批口径与实测回写）、
   `docs/verification/ui-redesign-2026-10.md` §批 C-6 / §批 C-7、`docs/design/visual-style-guide.md` 骨架可见度口径一节。

## 7. 用户确认过的决策

- 「提到可见档」——骨架填充与光带都要提到实机看得见的档（C-6 报告 §5 的待裁决，本批执行）。
- 光带机制 = **现有 token + 混合模式**：不新增 palette token（那会动 `PrototypeTokenContractTest` 的「恰好 28 枚」闸）、不按主题分支。
- 可见度 = **≥16 级，与卡壳同强**：以 C-6 实机唯一看得见的那档为下限锚，不用 WCAG 3:1 量骨架。
- 分支不合 `main`；推送逐批请示；截图/日志等 QA 物证不入库。
- 下一批 = 批 D 页面转场。
