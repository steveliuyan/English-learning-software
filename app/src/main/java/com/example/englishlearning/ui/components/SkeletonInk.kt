package com.example.englishlearning.ui.components

import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import com.example.englishlearning.ui.theme.AppColorSet

/**
 * 骨架的「墨」：填充与光带取色的**唯一落点**（批 C-7 从 `Skeleton.kt` / `Shimmer.kt` 的两处内联写法收拢过来）。
 *
 * 为什么要有这个对象，而不是让色档继续写在使用点里：批 C-6 的实机像素取样证明「骨架条相对容器只差 1–4 个通道级」
 * 这件事**在 JVM 侧根本判不出来**——节点画了、形状对、读屏不念假数字，四条结构判据全绿，用户看到的仍是空白页。
 * 观感强弱里「能不能看见」这一半是纯算术（同一份 token、同一个 alpha、渲染后的通道差），可以提前钉死；
 * 剩下的「好不好看」才归截图册。判据落点：`SkeletonVisibilityContrastTest`。
 *
 * 三条约束：
 *  - 取值只走 [AppColorSet] 的既有 token，**禁裸 hex**（`ShimmerSpecContractTest` 与 `SkeletonFamilyContractTest`
 *    的禁令族继续覆盖本文件，别在这里开口子）。
 *  - 深浅两档用**同一份算式**，不许按主题分支——需要分支通常说明取色选错了（批 C-7 的光带就是靠混合模式而不是
 *    `if (isDark)` 达成「比容器亮」）。
 *  - 本对象**只给颜色与混合模式**，不给 alpha 之外的动效参数：周期、行程、峰值仍归 [ShimmerSpec]。
 */
object SkeletonInk {

    /**
     * 骨架条 / 前导圆点的填充：**`outline` 满值**（批 C-7 裁决「提到可见档」）。
     *
     * 为什么不沿用批 C-6 那档 `outlineSoft.copy(alpha = 0.22f)`：实机像素取样测到它压浅色页底差 **1 个通道级**、
     * 压白卡面差 4 级、压深色卡面差 3 级——三档读作空白页，节点画了但眼睛看不见。
     * `outline` 满值把四档都拉到 20–34 级（浅对页底 20 / 对卡面 34，深对页底 33 / 对卡面 26），
     * 与本轮实测「唯一看得见」的卡壳档（16 级）同强或更强；这条线由 `SkeletonVisibilityContrastTest` 当场复算钉死，
     * 退回去就红。
     *
     * 已知的不适用：≥3:1 那条是**图标/非文本信息载体**的尺子（`docs/design/visual-style-guide.md` §对比度口径），
     * 骨架不承担信息，且 pastel 档里没有任何一枚 token 够得着 3:1（`outline` 满值只有 1.159:1）——
     * 硬凑只能画深灰块，与设计语言冲突，所以这里用通道差的经验锚而不是 WCAG 比。
     */
    fun fill(colors: AppColorSet): Color = colors.outline

    /**
     * 扫动光带的取色：仍是 `tint`，**方向靠 [bandBlend] 保证**，不靠取色本身。
     *
     * 批 C-6 的问题不是 `tint` 选错，而是 `SrcOver` 把方向交给了 palette 明暗顺序：浅色卡面上 `tint #E8F2EA` 压
     * `#FFFFFF` 是画暗，深色档 `tint #1C2A24` 比骨架条 `#2A3931` 更暗，两档都错向且方向还相反。
     * 两套 palette 里也没有「比 `outline` 更亮的中性 token」（浅档往白、深档往亮灰，方向天然相反），
     * 所以批 C-7 的解法是**提亮混合**而不是新增 token 或按主题分支——`Screen` 的结果只可能比底色亮，
     * 于是 `tint` + `Screen` + `ShimmerSpec.peakAlpha = 0.6f` 给出浅档 18–19 级、深档 19–20 级的亮带（同一份算式）。
     *
     * 读数为什么是一个区间而不是一个定值：`alphaAt(phase) = sin(phase·π) · peakAlpha` 且渐变峰值同时在扫，
     * 所以**单帧抬升量取决于截屏落在周期哪一格**；`screencap` 自身 0.15–0.3 秒，12 帧短促采样会欠峰
     * （实测同规格密拍 26 帧后 `card` / `stat-grid` 从 14 / 11 升到 19）。区间上限才是组件上限，
     * 逐规格复算口径见 `docs/verification/ui-redesign-2026-10.md` §批 C-7 的取样四坑。
     */
    fun band(colors: AppColorSet): Color = colors.tint

    /**
     * 光带的混合模式：**`Screen`（提亮）**。
     *
     * 判据落点 `SkeletonVisibilityContrastTest.band mechanism is a lightening blend not a theme branch`——
     * 换回 `SrcOver` 即使取色与 alpha 都不动，浅色卡面上的光带也会立刻变成一条暗带，这条判据会当场红。
     * 注意 `Screen` 是**与当前图层已画像素混合**：骨架条自己 `.clip(shape)` 后光带只在条内，
     * 落点符合预期；但如果哪天把骨架放进带 `graphicsLayer` 的容器（玻璃、模糊、抬升），混合底会变成图层底而不是页底，
     * 这一条只能靠截图册复核，JVM 判据管不到（批 C-7 已按深浅两档各重拍一册）。
     */
    val bandBlend: BlendMode = BlendMode.Screen
}
