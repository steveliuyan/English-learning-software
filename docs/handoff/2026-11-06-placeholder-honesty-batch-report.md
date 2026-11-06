# UI 重设计轮·占位诚实化批（2026-11-06）

## 0. 开工口径（10 行）

1. 这批全在**看得见的表面**：词卡、查词、每日阅读、词库四格，以及随包词书的数据呈现。
2. 起因是走查截图（`emulator-5554`）：卡面印着「暂无例句」当内容；查词页没输过字就报
   「未找到匹配词卡」；阅读页页头让用户去下方找「生成前参数」，而锁定态下方根本没有那一块；
   词库四格里两格带条数、一格不带。
3. 口径：**占位文本不得当学习内容**（决策 `docs/decisions/2026-11-06-placeholder-text-is-not-learning-content.md`），
   归一只做在包解析的读取边界，界面不再各自认串。
4. 取舍：不改十册 `book.json`（45,290 条全量重写 + 包格式与校验一起动，代价与收益不匹配），
   也不在界面上用「暂无例句」去替代「暂无例句」。
5. 有行为的才走 TDD：例句/搭配归一、查词空历史落哪一档。纯排版与文案用 connected + 截图判，
   **不加源码正则闸**。
6. 门禁只在收口跑一次，不逐项跑。
7. 取证：`emulator-5554` 截图 + 目视，产物落 `.git/qa-scratch/`，不入库。
8. 真机 `bf353dda` 本批不碰（只手工试用，且这批不需要付费出站）。
9. 不合 main、不推送，等发话。
10. 验收 = 三连门禁 + 矩阵校验 + secret-scan 全绿，且上面四处界面读数与截图一致。

## 1. 做了什么

**归一（数据诚实化，TDD）**

- 新增 `wordbook/WordBookContentPlaceholders.kt`：全站唯一一份占位判据，两份**封闭清单**
  （`EXAMPLES` = 「暂无例句」；`NO_DATA_MARKS` = 「—」）加三条纯函数——
  `exampleOrNull`（整串是占位语或空白 ⇒ 没有例句）、`entryOrNull`（整串是无内容记号 ⇒ 这条不存在）、
  `phraseMeaningOrNull`（`text` 与 `meaningZh` 逐字相同 ⇒ 只讲得出一个字段）。
  判据一律**只认整串相等**（去首尾空白），不做子串匹配。
- `wordbook/WordBookPackage.kt`：DTO→domain 这一处出口（内置册与导入册共用）接上三条判据。
  例句被归一时它的译文 `exampleZh` 一起归零（例句都不存在，译文是凭空多出来的一行）；
  搭配与近义里整串是「—」的条目**整条丢弃**，不再只是抹掉第二行。
- `learning/domain/WordCard.kt`：`PhraseEntry.meaningZh` 由非空 `String` 改为 `String? = null`——
  域模型从此允许「讲不出中文释义的搭配」。`ui/CardDetailScreen.kt` 第二行随之按可空渲染。
- 查词页空历史落档（`ui/GlobalVocabularySearchViewModel.kt` 两处）：`showHistory` 在一条历史都没有时
  落 `Idle`（输入引导）而不是 `Empty`（「搜了，没搜到」），清空历史后同样落 `Idle`。
  今日学习页那枚常驻查词条走 `openSearch("")`，用户一个字没输入时报「未找到匹配词卡」是假失败。

**可见面（connected + 截图判，不加源码正则闸）**

- 词卡「显示释义」在 tint 卡上不可见：容器原本用 `colors.tint`，而它所在的 `SoftCard` 底色正是
  `colors.tint`（明档 `0xFFE8F2EA` / 暗档 `0xFF1C2A24`）——同色填充等于把按钮画隐形。
  改为 `surface` 底 + `outlineSoft` 描边，并把按钮抽成文件末尾的顶层组件 `RevealMeaningButton`
  （抽出来的理由是容器墨配对判据按**顶层函数段**取样，塞在大屏函数里会把十几处无关排印点一起判红）；
  标签墨显式取 `LocalContentColor.current`，因为 `AppType` 各档自带一份墨，只写 `style` 时
  `contentColor` 根本到不了字面。
- 阅读页页头副标题：末句「长度与题材在下方『生成前参数』里设置」只随 `Ready` 分支出现
  （`headerSubtitle`），锁定态只留一句实话——那枚开关与偏好卡只由 `ReadyContent` 画。
- 词库四格计数口径统一（`ui/WordBankScreen.kt`）：新增 `WordBankCountUnavailableMark = "—"` 与
  `wordBankSegmentLabel` / `wordBankSegmentDescription`。读到条数 ⇒ 「段名 N」；读不到 ⇒ 「段名 —」，
  **不是**裸段名（裸段名和词书格按设计的无数字形状撞成一张脸，而词书格的无数字是「按设计不带」）、
  **也不是**「段名 0」（0 只有 `Ready` 且空册才有资格说）。无障碍描述同口径。
- 计数的真根因在宿主（`ui/AppScreen.kt`）：进页只点名当前那一段，其余两段停在 `Loading`，
  于是落在收藏段时看到 `生词 0 / 错词 / 收藏 0`。补一条进页预读三段的 effect，
  键为 `(showWordBank, profile.id)`、**不含** `wordBankSection`——切段仍走按段刷新，
  不与 m-2（修复轮 1：页内重试一次拉三册造成闪烁）冲突。

**测试**

- 新增 `WordBookContentHonestyTest`（9 例）：占位例句归零、空白分支、含「暂无」的真实句子不被误杀、
  自镜像搭配只留一行、真中文释义保留、「—」搭配与「—」近义整条丢弃、含破折号的真实内容
  （`mother-to-be`、`the — not so simple — part`）保留，最后一条**读仓库里十册资产原件**断言
  归一后一张假例句卡都不剩、且搭配与近义条数确实下降（防止归一退化成空转）。
- `WordBookPackageFixture.CardSpec` 补 `synonyms` 参数（夹具此前写死一条近义，没法造「—」近义）。
- `GlobalVocabularySearchViewModelTest` +2 例钉空历史落 `Idle`；`WordBankScreenContractTest` 与
  connected 的 `WordBankScreenTest` 把「读不到」那格的期望从裸段名改成「错词 —」。

## 2. 数据面读数

十册随包资产（`app/src/main/assets/wordbooks/*/book.json`，2026-11-06 现算）：

| 字段 | 总数 | 占位/自镜像 | 归一后仍渲染 |
|---|---|---|---|
| `example` | 45,290 张卡 | 「暂无例句」45,290（**100%**，真实例句 0 条；`exampleZh` 非空 0 条） | 0 |
| `phrases` | 78,392 条 | `text == meaningZh` 自镜像 77,358（98.7%）；`text == "—"` 6,517 | 71,875 条（第二行只剩 1,034 条真释义） |
| `synonyms` | 91,971 条 | `lemma == "—"` 3,224 | 88,747 |
| `senses` / `derived` | 45,290 / 43,684 | 整串「—」命中 **0** | 不动 |

复算命令（不落脚本，随仓库路径可跑）：

```bash
python - <<'PY'
import json, glob
c=ex=ph=phm=phd=sy=syd=0
for p in glob.glob("app/src/main/assets/wordbooks/*/book.json"):
    for k in json.load(open(p, encoding="utf-8"))["cards"]:
        c += 1; ex += ((k.get("example") or "").strip() == "暂无例句")
        for x in k.get("phrases") or []:
            ph += 1; t=(x.get("text") or "").strip(); m=(x.get("meaningZh") or "").strip()
            phm += bool(t and t == m); phd += (t == "—")
        for x in k.get("synonyms") or []:
            sy += 1; syd += ((x.get("lemma") or "").strip() == "—")
print(c, ex, ph, phm, phd, sy, syd)
PY
```

读数出处就是这条命令的输出（仓库根目录跑）：
`45290 45290 78392 77358 6517 91971 3224`，依次对应表中四行的六个数。
「第二行只剩 1,034 条」是同一份数据上再算一步：`78,392 − 6,517 = 71,875` 条留下来的搭配里，
`text == meaningZh` 的自镜像 70,841 条，`71,875 − 70,841 = 1,034` 条真有中文释义。

## 3. 验证记录

- 三连门禁（离线，收口跑）：`gradle.bat --offline --no-daemon :app:assembleDebug
  :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin` → `BUILD SUCCESSFUL`；
  三任务当场为 `UP-TO-DATE`（源码自上一轮全绿构建未变），故又用
  `:app:testDebugUnitTest --rerun` 强制重跑一次真执行（`BUILD SUCCESSFUL in 42s`）。
  枚数 `python .git/qa-scratch/tally.py .git/qa-scratch/tally-1106e.txt`
  → `classes=322 tests=2381 failures=0 errors=0 skipped=0`（上一批 `0fdeeb9` 为 321/2370，
  本批 +1 类 = `WordBookContentHonestyTest` 9 例、+2 例 = 查词空历史）。
  日志与计数留档两个名字：`.git/qa-scratch/gate-1106e.log`、`tally-1106e.txt`
  （`tally.py` 的入参是**输出**文件，写成同名会覆盖日志）。
- `verifyAcTraceabilityMatrix` → `BUILD SUCCESSFUL`（本批不新增 AC 编号，AC1-12 既有行仍成立）；
  `bash scripts/secret-scan.sh` → 退出 0、零命中。
- connected（`emulator-5554`，`adb install -r` 双 APK + `am instrument`，未用 `connectedDebugAndroidTest`）：
  本批涉及的 10 个类合并一次跑（`WordCardScreenTest`、`WordCardRevealFocusTest`、
  `GlobalVocabularySearchScreenTest`、`ReadingAccessScreenTest`、`WordBankScreenTest`、`AppScreenTest`、
  `CardDetailScreenTest`、`WordBookPackTest`、`WordCardFieldsProbeTest`、
  `VocabularySearchIndexRepositoryFlowTest`），日志 `.git/qa-scratch/instr-1106e.txt` → `OK (161 tests)`。
  这是收口时的终版构建 + 终版判据合并跑；此前按项分三次跑过（88 / 68 / 36 例，各自 `OK`），
  合并跑的意义在于**同一枚 APK 同时**满足词卡、查词、阅读、词库与详情页。
- 设备目视取证（`.git/qa-scratch/sweep-1106b/`，不入库）：
  `01-reveal-light.png` / `02-reveal-dark.png` / `03-reveal-2x.png`（揭晓按钮三档可见）、
  `04-bank.png`（词库四格计数）、`05-reading-locked.png`（锁定态页头）、
  `06-detail-phrases.png`、`07-detail-a-nodash.png`（`doctoral-english:a` 详情页）。
  最后一张是本批两条「—」缺陷的现场对照：改前该页 dump 里 `常用搭配` 标题下只有一枚
  `txt='—'` 节点（`adetail.txt`，bounds `[95,1117][127,1166]`），改后整段区域消失、
  `近义词` 只剩真 chip `an`，dump 内 `"—"` 命中 0 次（`detail5.txt`）。
- detekt/ktlint 未跑（本仓验收门禁是单测，存量债 1500+ 条不是回归）。
- 设备侧收尾：`font_scale` 已回 1.0，`cmd uimode night no` 复原明档，本批未用 `adb root`。

## 4. Git 状态

- 分支 `stage-5-ui-r2-learning-path`；本批按功能域四枚提交：
  数据诚实化（归一 + 域模型 + 决策文档）、查词空历史落 `Idle`、三处可见面修正、本报告。
- **未推送**（用户口径「推送等我发话」）、**未合 main**。未推送枚数现算：
  `git rev-list --count origin/stage-5-ui-r2-learning-path..HEAD`（本文件所在那枚提交落地后 = 14）。

## 5. 未完成 / 待拍板

- **真实例句是内容级缺口，本批不解决**。三条出路等拍板：① AI 按词生成例句（付费出站 + 要限流，
  且要给随包 45,290 条逐词出网）；② 换带例句的外部数据集（先过许可核验，AGENTS.md 硬要求）；
  ③ 维持现状（区域隐藏，只缺内容不缺诚实）。当前实现落在 ③。
- 真机 `bf353dda` 目视（任务 #14）：本批四处 + 前批遗留，等用户自己试用。
- 观察项（等用户裁决，本批未动）：`fontScale 2.0` 下词卡底部三档评分按钮高度不齐——
  「模糊 / 差点没想起」折到三行，另两档两行。
- 仍开放的前批待拍板项不变：DialogPanel 固定出口按钮、4 张未实现的 AI 能力卡选哪几张、
  生成图是否入收藏、图片是否也走「按域名一次」、每日文章生成上限。

## 6. 下一任接手须知

- 占位判据只有 `WordBookContentPlaceholders.kt` 一处，清单是**封闭**的：新增一项要带数据出处
  （哪一册、哪个字段、多少条），不许凭感觉加，也不许改成子串/模糊匹配——
  `WordBookContentHonestyTest` 里「含占位词的真实句子保留」那几条是唯一防线。
- 随包十册**没有一条真例句**（§2 现算）。任何依赖「有例句」的展示、用例或截图取证都要用 fixture 数据，
  不能拿随包册当证据；`WordBookPackTest` 在设备上读的是真册，别在那儿断言例句存在。
- 词库四格的「没有数字」现在有两种含义，且**必须**继续是两种：词书格按设计不带条数（裸段名），
  其余三格读不到时带「—」。改渲染前先读 `wordBankSegmentLabel` 的注释与
  `WordBankScreenContractTest` 的两条禁止形态。
- 容器墨配对判据（`ContainerInkPairingContractTest`）按顶层函数段取样：往大函数里加一枚自画容器的
  组件会连带判红整屏无关排印点，正确处置是抽成顶层组件（本批 `RevealMeaningButton` 即先例），
  而不是给十几处补 `color =` 或放宽判据。
