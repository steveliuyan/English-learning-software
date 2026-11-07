package com.example.englishlearning.ui.components

import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishlearning.ui.theme.MintGlassTheme
import com.example.englishlearning.ui.theme.appColors
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 批 C-6 骨架/shimmer **截图册的取证宿主**（决策
 * `docs/decisions/2026-11-07-loading-skeleton-shimmer-prefetch.md` §4「只能靠设备/截图、不假装单测覆盖」那条：
 * 「每条骨架规格各配一张 `animator_duration_scale=0` 截图 + 一张 `scale=1` 中段截图」）。
 *
 * **为什么必须开真窗口，而不是像 `RequestInProgressEvidenceTest` 那样用 `createComposeRule()`**：
 * 批 A 在 emulator-5554 实测过，Compose 测试环境替换了帧时钟——`waitForIdle()` 1ms 返回、宿主只重组 1 次、
 * 循环值恒 `0.0`。也就是说测试 rule 里 `ShimmerSpec` 的相位**根本不动**，`scale=1` 那一张会拍成静息骨架，
 * 和 `scale=0` 那一张看不出差别，取证直接失效。本类走 `ActivityScenario` + `ComposeView`：
 * 真 `WindowRecomposer`、真 Choreographer，相位由 `rememberInfiniteTransition` 自己推进。
 *
 * **为什么不 `Thread.sleep` 等窗口**：instrumentation 的测试线程与 App 主线程在单进程下是两条线程，
 * 但在测试线程上睡过去仍然会把「这一轮什么时候该截」变成猜测。这里由 `ComposeView.postDelayed`
 * 在**主线程**上给取证窗口打点并放行闩锁，观察窗内主线程一直在要帧、一直在重绘。
 *
 * **`scale=1` 那一半必须连拍，单张两帧会混叠（本轮实测踩到）**：七枚规格共用同一相位原点（都在 `holdStart`
 * 之后第一次组合启动），而 logcat 轮询的检测延迟几乎恒定 ⇒ 「+1.0s / +3.0s」两帧每次都抓在同一格相位上，
 * 那格恰好落在周期末尾的静息段（后 1/4、300ms 宽），于是 14 张 `scale=1` 帧**逐字节等于** `scale=0` 帧——
 * 看着像光带没扫，其实是取样周期与动画周期混叠。改成每规格连拍 12 帧、按像素选离静息帧最远的一帧才算取证
 * （脚本 `.git/qa-scratch/c6/capture1burst.sh` + `pick_frames.py`，读数登记在
 * `docs/verification/ui-redesign-2026-10.md` 批 C-6 那节）。
 *
 * 每个方法只 `setContent` 一次（同方法内二次 `setContent` 真机必崩，本包既有怪癖口径），
 * 七个规格因此是七个方法，**逐个单跑**：会话侧一条 `-e class …#<方法>` 配两次 `screencap`。
 * 截图落在宿主的 `screencap` 里，不在应用私有目录——本类不需要临时 `adb root`。
 *
 * 边界（登记进 `docs/verification/ui-redesign-2026-10.md` 时不许外推）：
 *  - 宿主是**取证用的裸 `ComposeView`**，不是真屏。截图证明的是「这一条骨架规格画出来是什么、关掉动画定格成什么」，
 *    不证明某一屏的排布；真屏首帧的观感归用户目视（各批报告已列）。
 *  - 页底取 `appColors().background`、左右 20dp 内边距抄自 `LearningRecordsScreen` 的宿主档，只为让色块
 *    落在真实页底上——骨架的墨、形状、光带全部由被摄规格自己决定。
 *  - 浅色档（模拟器系统主题为浅色）。深色档的骨架取色是同一份 `appColors()` 算式，由批 C-1 的取色闸钉住，不在此册重复。
 *  - 断言只有一条「真窗口里量到了宽度」，防的是「截到的是启动器/息屏画面却当成骨架取证」；
 *    观感强弱与定格是否真的静息不由本类判定，由截图册的像素取样判定——本轮读数（同一节）是
 *    「定格成立、光带确实在扫，但骨架与光带相对容器只差 1–4 个通道级」，色档因此回到用户裁决，不许本类替人眼放行。
 */
@RunWith(AndroidJUnit4::class)
class SkeletonShimmerEvidenceTest {
    private var scenario: ActivityScenario<ComponentActivity>? = null

    @After
    fun tearDown() {
        scenario?.let { runCatching { it.close() } }
        scenario = null
    }

    /** 规格 1：文字占位条四档——前三条逐字镜像 `ArticleReadingScreen` 首屏（0.72/26、0.46/12、0.34/12），
     * 第四条是 `VocabularyRelearnScreen` 顶替「假数字」的那条短横（0.24/10）。 */
    @Test
    fun skeleton_bar() {
        hold("bar") {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                SkeletonBar(fraction = 0.72f, height = 26.dp)
                SkeletonBar(fraction = 0.46f, height = 12.dp)
                SkeletonBar(fraction = 0.34f, height = 12.dp)
                SkeletonBar(fraction = 0.24f, height = 10.dp)
            }
        }
    }

    /** 规格 2：通栏行 + 行首圆点 + 发丝线，首行无线（选词书 / 今日计划 / 阅读 tab）。 */
    @Test
    fun skeleton_row_leading() {
        hold("row_leading") {
            SkeletonList(
                rows = 3,
                rowHeight = 48.dp,
                tagPrefix = "evidence_row_leading_",
            )
        }
    }

    /** 规格 3：无前导图标、96dp 高、词头短释义宽（查词结果行）。 */
    @Test
    fun skeleton_row_flat_96() {
        hold("row_flat_96") {
            SkeletonList(
                rows = 2,
                rowHeight = 96.dp,
                tagPrefix = "evidence_row_flat_96_",
                leading = false,
                titleFraction = 0.28f,
                subtitleFraction = 0.66f,
            )
        }
    }

    /** 规格 4：无前导图标、48dp 高（学习记录行）。 */
    @Test
    fun skeleton_row_flat_48() {
        hold("row_flat_48") {
            SkeletonList(
                rows = 3,
                rowHeight = 48.dp,
                tagPrefix = "evidence_row_flat_48_",
                leading = false,
                titleFraction = 0.34f,
                subtitleFraction = 0.68f,
            )
        }
    }

    /** 规格 5：带壳卡行（`Card` 描边 + 10dp 缝），词书单词那一族。 */
    @Test
    fun skeleton_row_framed() {
        hold("row_framed") {
            SkeletonList(
                rows = 3,
                rowHeight = 48.dp,
                tagPrefix = "evidence_row_framed_",
                leading = false,
                framed = true,
                titleFraction = 0.30f,
                subtitleFraction = 0.62f,
            )
        }
    }

    /** 规格 6：卡片块（今日计划主卡、词卡、生词复习），默认 92dp 下限档。 */
    @Test
    fun skeleton_card() {
        hold("card") {
            SkeletonCard(bars = 3)
        }
    }

    /** 规格 7：分段计数格三格（词卡统计、生词复习 48/54dp 档）——格子有壳、数字位留短横。 */
    @Test
    fun skeleton_stat_grid() {
        hold("stat_grid") {
            SkeletonStatGrid(cells = 3, rowHeight = 54.dp)
        }
    }

    /**
     * 把 [content] 装进真窗口，开着观察窗 [HOLD_MILLIS] 放行；会话侧在这段窗口里 `screencap`。
     *
     * logcat（tag `QA-C6`）每个规格两行：窗口开始带上当时的 `animator_duration_scale`，窗口结束带上量到的宽度。
     * 截图册登记时按这两行把 PNG 与档位、时刻对上——**不看日志就不知道那张图是哪一档拍的**。
     */
    private fun hold(id: String, content: @Composable () -> Unit) {
        val released = CountDownLatch(1)
        val composedWidth = AtomicInteger(-1)
        val launched = ActivityScenario.launch(ComponentActivity::class.java)
        scenario = launched
        launched.onActivity { activity ->
            val scale = animatorScale(activity)
            Log.i(TAG, "spec=$id scale=$scale holdStartMs=${SystemClock.elapsedRealtime()}")
            val view = ComposeView(activity)
            view.setContent {
                EvidenceHost(content)
            }
            activity.setContentView(view)
            view.postDelayed({
                composedWidth.set(view.width)
                Log.i(
                    TAG,
                    "spec=$id scale=$scale holdEndMs=${SystemClock.elapsedRealtime()} widthPx=${view.width}",
                )
                released.countDown()
            }, HOLD_MILLIS)
        }
        assertTrue(
            "观察窗没有在主线程走完（$id）——这一轮没截到有效帧，别把上一轮的 PNG 当证据",
            released.await(HOLD_MILLIS + 20_000L, TimeUnit.MILLISECONDS),
        )
        assertTrue(
            "$id：ComposeView 宽度 ${composedWidth.get()}px，说明截图时画面不是这块骨架（息屏 / 启动器 / 崩了）",
            composedWidth.get() > 0,
        )
    }

    @Composable
    private fun EvidenceHost(content: @Composable () -> Unit) {
        MintGlassTheme {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(appColors().background)
                    .padding(horizontal = 20.dp, vertical = 24.dp),
            ) {
                content()
            }
        }
    }

    /** 档位读 `global` 命名空间（批 A 实测本模拟器这枚键就在 `global`，`system` 下为 null）。 */
    private fun animatorScale(activity: ComponentActivity): String =
        Settings.Global.getString(activity.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE)
            ?: "unset(=1.0)"

    private companion object {
        const val TAG = "QA-C6"

        /**
         * 6s ≥ 5 个 shimmer 周期（`ShimmerSpec.cycleMillis` = 4 × `AppMotion.Slow` = 1200ms），
         * 两档的取帧方式不同，**别改回同一套**：
         *  - `scale=0`：窗口内截两帧即可——这档要判的是「定格后逐字节是否相同」，间隔多大都不影响结论。
         *  - `scale=1`：必须连拍（本轮 12 帧 × 0.2s，`capture1burst.sh`），事后按像素挑离静息帧最远的一帧
         *    （`pick_frames.py`）。两帧取样会与该 1200ms 周期混叠，见类注释第三条；连拍会越过本窗口末尾，
         *    拆窗后的白屏帧由页底取样点滤掉，不算证据。
         */
        const val HOLD_MILLIS = 6_000L
    }
}
