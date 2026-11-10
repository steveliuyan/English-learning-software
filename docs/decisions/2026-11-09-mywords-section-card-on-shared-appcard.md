# 决策：我的（MyWords）分段卡改用共享 AppCard（2026-11-09）

## 背景

设计系统收口（m00649 第 3 项「统一 设置/我的 的版式与弹窗风格」）已把 设置页 换成
共享 `AppCard` + `SegmentedControl`。`我的` 这一线还剩一处漂移：
`MyWordsScreen.kt` 的私有 `MyWordsSectionCard` 仍在直接读 material3 `Card` +
`CardDefaults.cardColors` + 私有 `RoundedCornerShape(18.dp)` + 手写
`.border(1.dp, colors.outlineSoft, ...)`。

它的 KDoc 自称「与『今日计划』入口组同一规格的卡（surface 底 + 1dp outlineSoft 描边 +
18dp 圆角）」，但真实取值与共享容器 `AppCard`（surface 底 + 1dp `colors.outline` 描边 +
`AppShape.Card` = 20dp）不一致，且是全仓主代码里最后一处 `RoundedCornerShape(18.dp)`。
更关键的是：`ContainerContractTest` 的 `screens` 表注释写着「零 M3 Card(」，
但那条断言此前只对 `Containers.kt` 生效——逐屏「不碰 M3 Card」是无人看管的自述。

## 决定

### 1. 分段卡并入共享容器

`MyWordsSectionCard` 保留（它有 `onTopChanged` 位置回填与标题行结构），但外壳换成：

```kotlin
AppCard(
    contentPadding = PaddingValues(0.dp),
    modifier = Modifier.testTag(sectionTag)
        .onGloballyPositioned { onTopChanged(it.positionInParent().y.toInt()) },
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) { /* 标题行 / count / caption / content 不变 */ }
}
```

即：**外壳（底色、描边、圆角、内间距 0）归共享容器**，内层 16/12dp 内距 + 4dp 节奏
留在本文件（分段卡比通用的 16dp 内距略紧，属该屏自身排版，不下沉到 `AppCard` 默认值）。

### 2. 接受的可见偏差（登记在案）

- 圆角 18dp → `AppShape.Card` 20dp；
- 描边 `outlineSoft` → `outline`（浅套 `#DDE5DD` / 深套 `#2A3931`，比 `outlineSoft` 略深一档）。

理由与 m00649 第 3 项一致：设计系统收口的目标就是「同一种卡只有一种规格」，
漂移值为历史遗留。**不因此做全局 20↔18dp 改判**——全局圆角口径仍是
`docs/verification/visual-walkthrough-2026-11-08.md` B3 里等用户拍板的事项。

### 3. 契约测试补真判据

`ContainerContractTest`：

- `screens` 表加入 `MyWordsScreen.kt`；
- 新增用例 `no listed screen reaches for the material3 card again`：
  对表内每个屏幕文件断言 `Regex("""\bCard\(""")` 命中数为 0；
- 附两条反空洞样本（真 `Card(`+`CardDefaults.cardColors()` 必须计 1；
  `AppCard(`/`SoftCard(`/`QuietCard(` 必须计 0），防止正则退化成永不命中。

自此「逐屏零 M3 Card」从注释自述变成断言事实（29 个屏幕里 MyWordsScreen 是最后一处）。

## 备选与否决

- **保留私有 18dp 卡**：与设计系统收口目标冲突，且 18dp 已是全仓孤例。
- **把 16/12dp 内距下沉进 `AppCard` 默认值**：不同卡的内距本就分档（`AppCard` 16dp、
  `StrongCard` 20dp），为一个屏改公共默认值会波及全部调用点。
- **顺手改 WordBankScreen 的手写分段控件**（13dp / 10dp 圆角 → 共享 `SegmentedControl`）：
  会把圆角矩形轨道变成 `AppShape.Pill`，属可见改版，需用户签字，本轮不做。

## 后续（2026-11-09 结案）

本文记的「18dp→`AppShape.Card` 20dp 的可视差异」以及「全局 20↔18dp 仍待用户拍板」两处，
已由 `docs/decisions/2026-11-09-card-radius-keep-20dp-b3-closeout.md` 裁定结案：**维持 20dp**，
B3 关闭为「不改」。今后不要再按旧计划文档把卡面改回 18dp。

## 影响面

- `app/src/main/java/com/example/englishlearning/ui/MyWordsScreen.kt`
- `app/src/test/java/com/example/englishlearning/ui/ContainerContractTest.kt`
- 无数据库 / 领域层 / 文本资源改动；`my_words_*` 全部 testTag 与文案不变。
