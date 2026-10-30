package com.example.englishlearning.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.englishlearning.ui.theme.appColors

/**
 * 列表首帧骨架（批 2 Task 13 从 `AppScreen.WordBookListPlaceholder` 抽出共享）。
 *
 * 用途：数据还在读盘时先立 [rows] 行**同圆角、同最小行高、同内边距**的空壳，让这一屏的第一帧就不是空的
 * （spec F1-01「打开选取词书先卡一下、整列突然蹦出来」那一投诉的对策）。
 *
 * 两条刻意的设计：
 *  - **零文案、零数字**：占位行手里还没有数据，向 TalkBack 播报「已学 0 词 / 共 0 词」这种假数字
 *    比什么都不念更糟（F1-01 无障碍口径）。这里只有色块。
 *  - **圆角 / 行高 / 内边距全走形参**，组件不写死 `AppShape.Card`：词书卡的圆角是 18dp（滑动删除底、描边
 *    与卡片必须同角，换 `AppShape.Card` 的 20dp 会让红底从四角渗出；同角这件事由「卡身与删除底读调用方传入的
 *    **同一份** shape」保证，`WordBookPickRowTest` 的前身 `WordBookSwipeRowTest` 钉的是静止时不露红底，它按整行捕获，两处一起改成 20dp
 *    它照样绿，所以它不是同角约束的守卫——R-13.1 / m-6 措辞订正），由调用方把那组参数传进来。
 *  - **行与行之间的 14dp `spacedBy` 不是 spec §2.3 禁的「幽灵间距」**（R-13.1 / m-2）：那条禁令管的是
 *    **真实列表**里「本该有一条线、却只靠 spacedBy 撑出来的缝」。占位行此刻手里没有内容可分隔，那道缝本来就该是
 *    空的，所以骨架保留 14dp 间隙；真列表到位后同一道缝由「1dp `outlineSoft` 分隔线居中填满 14dp」承接，
 *    带高逐字相等（同为 14dp）→ 回填时列表总高度不跳、滚动位置不被顶偏。等高回填是这条取舍成立的唯一理由，
 *    改骨架间隙必须同时改真实列表的缝宽，否则「第一帧不跳」这条就断了。
 *
 * 只保证**横向**起点对齐（圆角、行高下限、内边距、单选钮盒与文字列起点照抄真卡）；纵向高度不承诺与真卡一致：
 * 真卡带进度文本 + 进度条 +「未学」时约 130dp，骨架按最小行高约 74dp，回填到位后卡片会长高——这是刻意的取舍，
 * 首帧要的是「立刻有东西」，不是逐像素复刻最终版面。
 *
 * 无障碍：每行保留 [rowTagPrefix] + 行号的可测试标签（connected 判据依赖 `wordbook_placeholder_$row`）；
 * 骨架不携带任何会被读屏播报的文本节点。容器走**别名形** `val colors = appColors()` + `colors.surface`，
 * R-13.1 / I-1 之后这种形状已被 `ContainerInkPairingContractTest` 的容器谓词认下来（原先只认字面
 * `appColors().surface`，本组件与 `AppScreen.WordBookCard` 都不在它视野里）；骨架段内零 `Text`，
 * 所以「已迁容器 + 未补色 `AppType` 前景」这一形在这儿不成立，判据绿是**被看见之后**的绿，不是看不见。
 */
@Composable
fun ListSkeleton(
    rows: Int,
    rowHeight: Dp,
    shape: Shape,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    rowTagPrefix: String = "wordbook_placeholder_",
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        repeat(rows) { row ->
            SkeletonRow(
                row = row,
                rowHeight = rowHeight,
                shape = shape,
                contentPadding = contentPadding,
                rowTagPrefix = rowTagPrefix,
            )
        }
    }
}

@Composable
private fun SkeletonRow(
    row: Int,
    rowHeight: Dp,
    shape: Shape,
    contentPadding: PaddingValues,
    rowTagPrefix: String,
) {
    val colors = appColors()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("$rowTagPrefix$row"),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = colors.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = rowHeight)
                .padding(contentPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 40dp 的盒子里放 20dp 的圆点：真卡的 RadioButton 也是 40dp 触控尺寸、20dp 圆面，
            // 照抄这层盒子，后面的文字条才能和真卡文字对齐。
            Box(
                modifier = Modifier.size(40.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(colors.outlineSoft.copy(alpha = 0.30f), RoundedCornerShape(percent = 50)),
                )
            }
            Column(
                modifier = Modifier.padding(start = 8.dp).weight(1f),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                SkeletonBar(fraction = 0.45f)
                SkeletonBar(fraction = 0.22f)
                SkeletonBar(fraction = 0.7f)
            }
        }
    }
}

@Composable
private fun SkeletonBar(fraction: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth(fraction)
            .height(12.dp)
            .background(appColors().outlineSoft.copy(alpha = 0.22f), RoundedCornerShape(6.dp)),
    )
}
