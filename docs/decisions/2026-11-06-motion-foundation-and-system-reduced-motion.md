# 决策：动效基建只跟随系统降级，禁止自研时长缩放（批 A）

日期：2026-11-06
分支：`stage-5-ui-r2-learning-path`（不合 main、未推送）
关联：`docs/superpowers/plans/2026-11-06-motion-and-adaptive-layout.md`（动效与自适应轮 批 A–D）

## 1. 背景

用户 2026-11-05 提出「可不可以做各个页面的动画动效以及预加载的动画特效」，随后追加「全部都做，然后横屏也要做适配，就是不是要硬横过来哦」，并批准按 批 A（动效基建 + 降级）→ 批 B（全面自适应）→ 批 C（预加载特效）→ 批 D（页面转场）推进。批 A 的三项拍板（选项题原文）：起步范围＝「动效基建 + 降级开关」；降级口径＝「只跟随系统」；批 B 野心＝「全面自适应（含平板/折叠 + 侧边导航）」。

「降级开关」这一项在动手前必须先回答一个事实问题：**平台是否已经遵守 `animator_duration_scale`**。原 `AppMotion` 里躺着两枚换算函数（`durationMs(base, scale)` 与 `animatorDurationScale(context)`），KDoc 写着「MIUI 真机已验证该行为是正确遵守」，但没有一处生产调用点——它们是「基建」还是「陷阱」取决于平台行为，取决于平台行为的东西不能靠注释里的一句话定案。

## 2. 证据

### 2.1 字节码（primary source，本机解包 `animation-core` / `ui-android` 1.10.0）

- `androidx.compose.ui:ui-android` 的 `WindowRecomposer_androidKt` 直接读字面量 `animator_duration_scale`，把 `MotionDurationScaleImpl` 装进 Recomposer 的协程上下文。
- `androidx.compose.animation:animation-core` 的 `SuspendAnimationKt.getDurationScale(CoroutineContext)` 取该元素（缺省 1f，负值抛 `negative scale factor`）。
- `doAnimationFrameWithScale` 反编译等价于：

  ```kotlin
  val timeForPercent =
      if (durationScale == 0f) animation.durationNanos
      else ((frameTimeNanos - scope.startTimeNanos) / durationScale).toLong()
  ```

  ⇒ 有限动画由平台自行缩放；`scale = 0` 时首帧就落在目标值。

### 2.2 设备实测（emulator-5554，一次性探针，代码不入库）

宿主＝`ActivityScenario.launch(ComponentActivity)` + `ComposeView.setContent`，走真 WindowRecomposer 与真 Choreographer。
第一版探针用 `createComposeRule()` **量废**：Compose 测试环境自己替换了帧时钟，`waitForIdle()` 1ms 就返回、宿主只重组 1 次、循环值恒 `0.0`——量到的是测试替身，不是真机行为。

| 规格 | `scale=1`（内对照） | `scale=0` |
|---|---|---|
| `animateFloatAsState(0f→1f, tween(600))` | 150 次重组 / 37 个不同值（0.0…缓动到 1.0） | 3 次重组 / 值序列 `[0.0, 0.0, 1.0]`（无中间值） |
| `rememberInfiniteTransition` + `infiniteRepeatable(tween(600), Restart)` | 150 次重组 / 148 个不同值，2.5s 内跑了约 4 个周期 | 3 次重组 / 值序列 `[0.0, 1.0, 1.0]`，此后再无新帧 |

读数原文与重算说明：`.git/qa-scratch/motion-probe-readings-1106.md`（gitignored，随证据留本地）。设备设置已复原：`adb -s emulator-5554 shell settings get global animator_duration_scale` → `null`（默认 1.0）。

## 3. 决定

**3.1 降级只跟随系统，不做应用内开关。** 用户拍板一致，且 2.1/2.2 说明这不需要新代码：有限动画、状态过渡、`AnimatedVisibility`、`animateContentSize` 都由平台缩放；`scale=0` 即「减弱动效」。不新增设置行，不加「跟随系统/始终关闭/始终开启」三态。

**3.2 删除两枚换算入口，禁止双重缩放。** `AppMotion.durationMs` 与 `AppMotion.animatorDurationScale` 从 token 表删除，`AppMotion` 退回纯 token 表。理由写进 KDoc 并由 `MotionSpecContractTest` 钉住：谁把换算接回调用点，`scale=0.5` 就会被平台乘一次、我们再乘一次 ⇒ 实际 0.25；`scale=0` 的「时长归零」也不再是平台那条口径。原 `AppMotionTest` 的三枚用例（`250×0.5=125`）钉的正是这条被推翻的口径，随函数一并删除。

**3.3 用途口径表（批 C/D 消费 token 时按此挑选，不是可选装饰）。**

| 用途 | 时长 | 缓动 | 现有落点 |
|---|---|---|---|
| 跟手反馈（按压缩放、分段格位移） | `Fast` 160 | `EasingOut` | `GlassButton.pressableScale`、`SegmentedControl` |
| 入场 / 展开（浮层、开关、面板展开、内容高度变化） | `Normal` 220 | `EasingOut` | `GlassOverlay` enter、`AppSwitch` 三枚（轨道/描边/拇指位移）、`SpeechSettingsScreen` 两处 `animateContentSize` |
| 退场 / 收起 | `Normal` 220 | `EasingIn` | `GlassOverlay` exit（本批由借用的入场曲线改回） |
| 需要被看见的数值变化（进度条、段宽） | `Slow` 300 | `EasingOut` | `PlanBar` 两枚宽度 |
| 页面转场 | `Slow` 300 | 入场 `EasingOut` / 退场 `EasingIn` | 批 D 落地 |
| 循环（骨架 shimmer） | 周期自定 | `EasingOut` 起、按 3.4 设计定格值 | 批 C 落地 |

**3.4 循环动画的减弱效果照实测口径设计。** `scale=0` 时平台**不会让循环空转**（2.5s 窗口只观测到 3 次重组），所以不需要运行时开关去关掉 shimmer 的帧；但它停下的那一格是**一个周期的末尾值**，不是中性初值。⇒ 批 C 每条循环规格必须把「周期末尾值」当作静态外观来写（例如让高亮带在周期末尾停在骨架之外），并各配一张 `animator_duration_scale=0` 的截图取证；只有当静态外观无法用规格表达时，才允许新增**布尔门**（读设置判 `<= 0f` 后换静态渐变），且该门不得参与时长换算。

**3.5 裸 `AppMotion.Easing` 别名退役，入场/退场分缓动。** 别名的历史作用是让旧读点统一接到入场曲线，代价是退场也借了入场曲线（`GlassOverlay` 的 `fadeOut`/`scaleOut` 正是这一形）。本批 7 个旧读点全部写明 `EasingOut` / `EasingIn`，别名从 `AppMotion` 删除；`PrototypeTokenContractTest` 的别名断言同向翻转为禁令，`MotionSpecContractTest` 另加「`fadeIn(`/`scaleIn(` 必须 `EasingOut`、`fadeOut(`/`scaleOut(` 必须 `EasingIn`」的行级闸。

## 4. 验证边界（不写假用例）

- 已钉住（JVM）：换算入口与 `AppMotion` 里的 `scale` 字样不得复活；每枚 `tween(` 显式带 token 缓动；enter/exit 缓动分档；生产站点点名零 offender + 覆盖面下限（含 `tween(` 的宿主文件数下限，防契约对空档放行）+ 合成 must-flag/must-not-flag 双边 + 缺失目录响亮失败。
- 判不到、也不假装判到：行级子串匹配看不到 `easing = ` 换行、也看不到把 spec 包进工厂函数的间接传参——由代码评审与批 D 的转场判据兜；「减弱动效下的视觉观感」是像素层面的事，归批 C/批 D 的设备截图与真机目视；探针是一次性代码，已删除、不入库，重跑需按 §2.2 的宿主形状重写。

## 5. 影响

- `ui/theme/AppMotion.kt`：纯 token 表 + 口径 KDoc；删除 `Easing` / `durationMs` / `animatorDurationScale` 与 `Context`、`Settings` 依赖。
- `ui/components/glass/GlassButton.kt`、`ui/components/glass/GlassOverlay.kt`、`ui/SpeechSettingsScreen.kt`：7 个裸别名读点写明方向（其中 `GlassOverlay` 两枚退场改为 `EasingIn`，是本批唯一的可见行为变化）。
- 测试：新增 `ui/MotionSpecContractTest.kt`；删除 `ui/theme/AppMotionTest.kt`；`PrototypeTokenContractTest` 别名断言翻转。
- 对后续批次：批 C 拿到 3.4 的编排约束；批 D 拿到 3.3 的转场档位；批 B 与本批无耦合（自适应只改版面，不改时长/缓动口径）。

复算（现算，别引用文档里写死的数）：

```bash
grep -rn "AppMotion\.Easing\b" --include=*.kt app/src/main | wc -l      # 期望 0
# 覆盖面：按 `tween(` 的真调用点算，`grep -l "tween("` 会把 Duration.between( 也算进来。
grep -rlP "(?<![A-Za-z0-9_])tween\(" --include=*.kt app/src/main/java/com/example/englishlearning/ui | wc -l
#   ↑ 含 AppMotion.kt 的注释提法；剥注释后 6 个宿主，是 MotionSpecContractTest 的覆盖面下限锚。
```
