# 决策 2026-11-05：对话框排印三档 + 查词域令牌收口（UI 重设计轮的可见面收口批）

## 背景

用户 2026-11-05 的总盘点提出「某些弹窗的排版对不对」并批准本批口径：弹窗排版统一 + `WordBookWordsScreen` 补完 +
逐词朗读明确裁决。取证方式改为「模拟器截图 + 用户目视」，**不新增源码正则闸**，门禁每批只跑一次（不是每项一次）。

## 决定

1. **对话框排印三档**：`Dialog` 窗口一律 标题 `AppType.Title` / 正文 `AppType.Body`（墨 `text1`）/ 出口按钮
   `AppType.ButtonLabel`。页内浮层与底部面板（`ModalBottomSheet`、`GlassOverlay`、覆盖词面板）不在这条口径内，
   仍可用 `Headline`——它们是「页面级内容」，不是「一次确认」。
2. **正文取 Body 而不是 Footnote**（对本批提案的唯一偏离，理由写在这里）：级联条数、密钥外发、不可撤销这类
   多行安全说明用全站最小档（13sp）读起来费力，且三枚玻璃面板的正文本来就是 `Body`——统一到 `Body` 既更正读法，
   改动面也更小（2 处正文 vs 3 处正文 + 一处既有判据）。
3. **`DialogPanel` 补高度上限 + 竖向滚动**：链序 `heightIn(max = 上限)` → `verticalScroll` → `padding(24.dp)`
   （边距放进滚动内容里才不吞首尾行，与 `glass/GlassDialog.kt` 同一条链序）。这是本批唯一的真缺陷修复：
   原面板无上限，设备字号 2.0 叠长正文时出口按钮被顶出窗口且滚不回来。
   **上限口径 2026-11-06 连改两版才定**（三版都在 `emulator-5554` 实跑过，数字来自临时探针 `QA-PANEL`，收口即删）：
   ① 首版照抄 `GlassDialog` 的固定 560.dp，connected 侧当场把既有护栏打红
   （`AppScreenOverlayContainerTest` 在 `font_scale = 2.0` 报「此操作不可撤销。」is not displayed，2 红/6 例）——
   那一屏无上限时正文本来就整屏放得下，被 560 一刀切掉反而变成「必须滚动才看到不可撤销那句」。
   ② 第二版取 `LocalConfiguration.current.screenHeightDp`（宿主窗口可用高）：正文进界，红点**挪到出口按钮**
   （`confirm_destructive_dismiss` is not displayed）。③ 把上限临时改成 `10000.dp`（等效无上限）复跑同一条：
   出口**照样红**——探针给的答案是这一屏在 2.0 的自然本来就长过对话框窗口，面板被窗口自己的约束裁在
   **659.05dp**（`Configuration.screenHeightDp` = 659，物理屏 = 731.4dp，40 行级联的自然高 = 3592dp），
   所以**没有任何数值上限**能让「出口不滚就可见」在这一档成立。
   终版上限取 `BoxWithConstraints.maxHeight`＝**对话框窗口给这一列的约束上界**：放得下的对话框不受约束
   （不新增滚动），放不下的视口＝窗口、超出部分靠滚动，两档字号与任意屏高同一条式子。随之把宿主侧断言口径
   改成与 `GlassDialog` 那族同一形状：正文首屏逐条可见，出口**滚到可见**后仍可点、仍 ≥48dp
   （先例＝本类 12 册导出选择器那条的 `performScrollTo()` → `assertIsDisplayed()` → `performClick()`）。
   不留 85% 那类余量——余量会让自然高落在 `0.85×H..H` 之间的对话框凭空多一次滚动，正是 ① 那次误伤的形状。
   `GlassDialog` 的 560.dp 原样保留（它载的是可滚动的册列表，且 `GlassPanelInkPairingContractTest` 把那条链钉在 200..700 一档）。
4. **私有对话框实测为 2 枚**（不是提案里说的 3 枚，重算：`grep -rn 'AlertDialog(' app/src/main/java/com/example/englishlearning/ui --include=*.kt | grep -v components/ | wc -l`）：
   `CardDetailScreen` 的「管理笔记」接共享 `DialogPanel` + `DialogButton`（出口键原先是手搓 `TextButton` +
   `FontWeight.Bold`，不吃六档也不保证 48dp）；`ReminderSettingsScreen` 的「提醒时间」**保留** Material3
   `AlertDialog`，只把标题从 `Body` 对齐到 `Title`——`TimePicker` 表盘要靠自身高度摆位，塞进带高度上限的滚动面板会与
   拖拽指针的高度约束打架。
5. **`WordBookWordsScreen` 令牌收口**：7 枚 `Mint*` 读点 → `appColors()` / `appDomain(AppDomain.AiSpeech).base`，
   3 处手搓字号（分组头 16.sp、索引芯片 17.sp/13.sp、页头默认档）→ 六档。**事实更正**：`Mint*` 是
   `theme/MintPalette.kt` 里的 `@Composable` 桥 getter，内部已读 `LocalAppColors` / `LocalDomainColors`，
   所以这一屏此前在深色档**没有**拿到浅值，不是暗色缺陷现场；本条收益是读法归一（桥可下线）+ 字号进档（缩放一致），
   两套主题的像素取值不变。
6. **逐词朗读裁决＝已落地，本批零改动**：批 3 Task 26 Step 1（提交 `759126f`）已给出
   `ArticleReadingScreen.kt:859-891` 的逐词 `CustomAccessibilityAction` + `WordRange` 注解 + 同一条落点解析。
   此前审计把它报成「被漏掉的项」，来源是计划文档的复选框缺勾（那份计划只有 10 枚 `[x]`），不是代码事实——
   收口记录以 `git log` 与源码为准。

## 判据侧随档位一起改的两处（不放宽阈值）

- `OutboundDialogFamilyTest.theSharedDialogStillSetsItsOwnType`：正文档锚 `AppType.Footnote` → `AppType.Body`，
  「恰一枚」不动。这条钉的从来是「正文必须有显式档」，不是「正文必须 13sp」。
- `LearningSetupLayoutContractTest.every group heading is the shared eyebrow heading…`：`AppType.Title` 的禁令
  从整窗收窄到**可滚表单段**（切点 `val colors = appColors()`，窗内恰一枚，多一枚即切点搬家）。
  原口径把同窗口里切书迁移框的**对话框标题**也算成「第二套分组排印」——眉标（12sp 大写 + 注）与对话框标题
  （17sp/650）不同形态，把它算进来是判据串味。收窄后补两枚断言：整窗只许一枚 `AppType.Title`，
  且必须逐字挂在 `text = "发现已学过的单词", style = AppType.Title` 上（否则阈值就成了「随便哪儿的一枚」）。
  切点字面量**不含换行**：仓库源码 CRLF，把 `\n` 写进锚点会假红（实测）。

## 新增的 connected 护栏（本批唯一的取证件）

`app/src/androidTest/…/ui/DialogTypographyEvidenceTest.kt` 四例，走设备而不是源码正则——源码里 `AppType.X`
出现几次证不了渲染面按那一档画：

- **三档比**（四族对话框各一次）：标题字号 / 正文字号 == `17/15`，按钮标签 / 正文 == `1`，字重 650 / Normal / 600。
  取比值不取绝对 sp：`textLayout().layoutInput.style` 在设备侧可能是 density × fontScale 解析后的 px 口径，
  绝对值在 2.0 档不可依赖，比值两档都成立。按钮一律 `useUnmergedTree = true` 取内层字形节点
  （合并后的可点容器没有 `GetTextLayoutResult` 动作）。
- **面板出路**：内容超高时面板 ≤ **物理屏高**（`WindowManager` 取，本机 731.4dp）、带竖向滚动动作、
  出口按钮**滚之前必须不可见**、`performScrollTo()` 之后必须可见。为此 `DialogPanel` 补了
  `DIALOG_PANEL_TAG`（`dialog_panel`）——上限与滚动是**面板自身**的性质，量标题或按钮证不了「长内容时出路还在」。
  界取物理屏高而不取 `Configuration.screenHeightDp`：后者是整数 dp（本机 659）而窗口给面板的约束实测 659.05dp，
  按它取界会因取整反向误伤。级联行数取 40（自然高 3592dp），保证 1.0 与 2.0 两档都真的走到
  「滚动才够得着出口」那一支；「滚之前必须不可见」那条反证是防用例空转——哪天内容不再超界，
  只剩 `performScrollTo()` 的话本来就可见、滚不滚都绿。
- **共享件真被复用**：`CardDetailScreen` 的管理笔记框里必须出现同一枚面板锚（私搓卡面时锚消失）。
  出口可见性用 `assertIsDisplayed` 的成功/失败判定，不用 `SemanticsNode.isVisible`——后者不算滚动容器裁剪，
  被上限裁掉的按钮在它眼里照样 visible，护栏会变假绿。

## 验收与取证结果

既有判据只跟着改「被故意换档」那几处取样，不放宽任何阈值；`AppScreenOverlayContainerContractTest` 钉的
迁移框「恰 1 枚 `AppType.Body`」在新口径下原样成立。

- **门禁**（固定三任务离线一次，上限口径改完后重跑）：`assembleDebug` + `testDebugUnitTest` +
  `compileDebugAndroidTestKotlin` 全绿，`321 类 / 2370 条 / 0 失败`。
  现值复算：`python .git/qa-scratch/tally.py <落盘路径>`——**注意脚本的入参是输出文件**，
  直接把门禁日志名传进去会把日志覆盖成一行（本批实测踩到）；留档见
  `.git/qa-scratch/gate-2026-11-06d.log`（真跑那次在 `…-06c.log`，被覆盖后复跑只剩 61 条 up-to-date）
  与 `.git/qa-scratch/gate-2026-11-06d.tally`。
- **connected**（`emulator-5554`，`am instrument`，非 `connectedDebugAndroidTest`）：
  对话框族 17 例 + 页面族 67 例，合并一次 84 例。`font_scale = 2.0` **84 例全绿**
  （`.git/qa-scratch/connected-final-2.0b.log`）；`font_scale = 1.0` 81 绿 + 3 红，
  3 红全是 `AppScreenOverlayContainerTest` 名字里写着 `AtDoubleFontScale` 那三条自带的
  「设备字号档没落进对话框窗口」反空转闸（`assertScaleTookEffect`）在自报前提不满足——那一族只在 2.0 档跑，
  不混进「全绿」。
- **上限改完后 2.0 复跑抓到的一条与本批无关的红**：`CardDetailScreenTest.askAiEntryInvokesCallback`
  （1.0 绿、2.0 恒红两次）。它钉的屏是整页 `verticalScroll`，2.0 下「问 AI」药丸落在首屏外、
  被滚动容器裁成 0×0，`performClick()` 的落点不在它身上 → 点击前补 `performScrollTo()`，
  与本类「笔记管理」那条同先例；回调恰一次的阈值一条没松。
- **双字号取证**（PNG 落 `.git/qa-scratch/dialog-evidence-{1.0,2.0}/` 与 `wordbook-rail-{1.0,2.0}/`，不入库）：
  本批早期 2.0 首轮另有两处红，都判为**判据绑字号**而非产品缺陷——
  ① 新护栏「一次 `swipeUp` 后出口可见」在 2.0 不成立（级联需要多推几次）→ 现改为
  「滚前必须不可见 + `performScrollTo()` 后可见」两条，比 swipe 循环更强且不依赖手势时长；
  ② `WordBookWordsScreenTest.letterGroupsRenderWithStickyHeadersAndRows` 断言三组组头不滚就可见，
  而本批把组头 16.sp 换进 `Title`（17sp/650、行高 27sp）后 2.0 档下 `#` 组被挤出视口 →
  按本类既有先例改走 `performScrollToIndex(下标)`（`LazyColumn` 只组合视口内的项，
  对未组合的 tag 调 `performScrollTo()` 会抛「找不到节点」，实测）。
  顶部区仍按「不滚就该看见」判，档位没放松。
- **仍待用户目视**：两档共 8 张对话框 PNG + 4 张词单页 PNG；以及本批实测出来的一条产品事实——
  `font_scale = 2.0` 下正文长的确认框**必然**要滚一下才够得着出口（探针：40 行级联自然高 3592dp，
  而窗口给面板的约束 659.05dp；上限取多大都改变不了这一点）。现在的形状是「正文首屏可见 + 出口滚到可见」。
  若希望出口常驻，得把 `DialogPanel` 改成「正文滚动 + 动作粘底」，那是本批范围外的组件改动，等用户拍板。

