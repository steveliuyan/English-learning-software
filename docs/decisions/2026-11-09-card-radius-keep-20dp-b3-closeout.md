# 决策：卡片圆角维持 20dp，B3「Card 圆角 20→18dp」关闭为「不改」

日期：2026-11-09　范围：`ui/theme/AppShape.kt`（不改代码，仅裁定）

## 背景

三份文档挂着同一条待用户拍板的视觉选项（代号 **B3**），措辞一致：

- `docs/verification/visual-walkthrough-2026-11-08.md:193`「M3 控件默认色由库紫转薄荷 + Card 圆角 20→18dp +
  `colorScheme.outline` 换成 text2 是否可接受」
- `docs/verification/ui-redesign-2026-10.md:1237` 同条
- `docs/handoff/2026-10-19-stage-5-ui-r1-design-system-report.md:104` 同条

它的来历：更早的两份计划里写过「卡片 18dp」（`docs/superpowers/plans/2026-10-17-ui-redesign.md:479`、
`docs/superpowers/specs/2026-09-26-ui-redesign-design.md` §2.3 的「弹窗 24 / 卡片 20 / 按钮 14」时期的口径），
于是后续复核把共享容器里的 20dp 当成「和计划不一致」，挂成待办。

## 现行权威口径

`docs/superpowers/specs/2026-10-17-ui-redesign-design.md` §2.3 栅格（第 159-162 行）：

> **2026-10-29 原型对齐（批 0）**：取代旧的「hero 24 / card 18 / 间距 14·18」一套。
>
> 圆角五档：`10dp`（小按钮、32 徽标、选择标记 7）/ `14dp`（按钮、输入框、44 徽标）/
> `20dp`（卡片、64 徽标）/ `26dp`（对话框、底部面板、强卡、空状态大图）/ `999dp`（药片）。

`docs/decisions/2026-10-29-ui-prototype-token-alignment.md:75` 逐字相同（10/14/20/26/999）。
`ui/theme/AppShape.kt` 的 `Card = RoundedCornerShape(20.dp)` 正是这一档，KDoc 也标注了原型 `--r-lg`。

## 决定

**维持 `AppShape.Card` = 20dp，B3 记为「已裁定，不改」。**理由：

1. 18dp 出处是**被批 0 取代的旧口径**，不是原型值；照它改会让全仓卡面偏离原型一次。
2. 改 20→18 是全局视觉变更（卡片、64dp 徽标一档），收益只是「跟旧计划文档对齐」，代价是再来一轮
   全屏走查——而旧文档已经被新 spec 明确作废。
3. 同一批 B3 里的另两项（M3 控件默认色转薄荷、`outline` 换成 text2）早已落地，只有圆角这条悬着；
   把它判「不改」即整条 B3 关闭，不再每次走查都被重新提起。

## 影响

- 不改任何代码。三份 verification/handoff 里的 B3 条目今后一律引用本决策。
- 收口后 `SettingsScreen` / `MyWordsScreen` / `AppCard` 等卡面 20dp 是**原型值**，不是遗留漂移；
  此前 `docs/decisions/2026-11-09-mywords-section-card-on-shared-appcard.md` 里记的
  「18dp→20dp 可视差异，待用户拍的全局 20↔18 决定」由此结案为「保持 20dp」。
- 同批新增的 `ContainerContractTest`「no listed screen hand rolls a corner radius」把屏侧裸圆角锁死，
  防止 18/13/10 这类魔数再以「对齐旧文档」的名义回来。
