# 2026-11-08 真机四项目视（AI 出站确认粒度）工作报告

## 1. 本次目标

用户原话：**「先跑一下真机本批四项目视」**——把 `docs/handoff/2026-11-05-batch2-verification-gaps-todo.md`
待办 3 挂着的四项，在真机 `bf353dda`（Mi 10S / API 33）上手工走完并留下可核验证据。
本会话**不改任何生产代码**：走查只读现树行为，新查出的两处缺陷另立条目等拍板。

## 2. 完成了什么

一句话结论：**四项里三项按预期通过（1、2、3 全绿，4 的文章腿在台账热的情况下仍逐次弹、拍照腿逐次弹），
第 4 项的两条腿（「换一篇」按钮本体、生图出图）因为 AI 服务端返回内容一律不合格而无法在真机触及**；
过程中新查出两条与确认逻辑无关的真缺陷（N-1 端点内容校验全红、N-2 词典详情谎报入口）。

本轮产出（仓库内只有文档）：

- 本报告；
- `docs/handoff/2026-11-05-batch2-verification-gaps-todo.md` 待办 3 就地打勾并标出两条未触及的腿（原口径不许被后来人误读成「四项全过」）。

证据册：`.git/qa-scratch/realview-14/`（62 张 `shot-NN-*.png` 截屏 + 64 份 `list-NN-*.txt` 层级转写，
同名 `wd-NN-*.xml` 原始 dump 64 份，采集脚本 `snap.sh` / 转写脚本 `ui.py`），按仓库隐私约束**不入库**。

## 3. 验证记录

### 3.1 判定口径与采集方式

判定不靠人眼单独成立：每一项都用 `adb shell uiautomator dump` 的层级树做**机读判据**——
弹框标题节点 `'确认发送请求'` 出现次数（`grep -c`）为 1 ⇒ 弹了，为 0 ⇒ 没弹；再用同批截屏做目视复核。
全程手工试用，**未跑任何 instrument**，未装 androidTest 包（跑前 `pm list packages | grep english` 复核包仍在）。

设备初始态：`font_scale = 1.0`（实测读回）、焦点 `com.example.englishlearning/…MainActivity`、
`wm size/density` 未动过。

### 3.2 逐项结果

| # | 待办 3 原句 | 结论 | 机读证据 |
|---|---|---|---|
| 1 | 长难句首次输入 ⇒ 出域名确认框 | **通过** | `list-10-confirm-dialog.txt` 弹框计数 1，正文含域名 `www.sssai.xin`；`list-11-after-cancel.txt` 取消后界面回到输入态、零请求态；`list-12-reprompt.txt` 取消后再次提交**仍弹**（计数 1）——取消不落账这条同时被验到 |
| 2 | 同一域名本次使用内第二次提问 ⇒ 不再弹 | **通过** | `list-23-dialog1.txt` 首次弹框 → 点「确认发送」 → `list-24-request1-a.txt` 出结果 → 同屏第二次提问 `list-25-second-ask.txt` 计数 **0** |
| 3 | 查词页 AI 问答同域名 ⇒ 也不弹（两屏共享一份台账） | **通过（但落点不是「查词页」，见 N-2）** | 实际路径：学习词卡 → 评「模糊」自动进词义详情 → 页底「问 AI（confidence）」→ 问答面板点「近义辨析」→ `list-45-qaask1.txt` 计数 **0**，且回答区落成「返回内容不合格，可以重新生成。」——这句本身就是**请求真的出了网并拿到响应**的证据：它是 `AiFailure.InvalidResponse` 的 UI 文案（`AiFailure.kt:65`），而该失败档的定义写着「对方返回了东西，但不满足字段、语言、长度或安全校验」，网络不通 / 401 / 超时 / 限流各另有档（`AiFailure.kt:23-45`）与别的文案。台账热态由 §3.3 的行覆盖证明 |
| 4a | 文章「换一篇」⇒ 每一次都还弹 | **按钮本体未触及；同语义的生成腿已通过两次** | 台账仍热的情况下点「生成今天的文章」→ `list-58-article-dialog.txt` 计数 **1**；取消第一次后（`list-61-aftercancel.txt` 计数 0，零出站）再点一次 → `list-60-article-dialog2.txt` 计数 **1**、随后取消。加上代码侧 `ReadingAccessViewModel.kt:145-146` 的 `generate(regenerate)` 无论真假都传 `confirmedTextHosts = emptySet()` ⇒ 「换一篇」与首次生成共用同一条**不读台账**的腿，结构上不可能被放宽 |
| 4b | 拍照翻译 ⇒ 每一次都还弹 | **通过（冷台账）** | `list-21-photo-dialog.txt` 计数 1，弹框文案如实写明图片不发送；取消后零出站。OCR 走本机 ML Kit，未出网 |
| 4c | 生图出图 ⇒ 每一次都还弹 | **真机未验** | 出图按钮要先有一次成功的「画面描述」才出现，而描述生成本轮一律失败（N-1）。代码侧证据完整：`AiOutboundConfirmation.kt` 里 `payloadKind == Image -> Required` **无条件**要求确认，图片载荷不走任何台账；且 `grep -rl AiOutboundGrantStore app/src/main/java` 命中的 5 个文件里（台账本体、判定函数 KDoc 提及、DI 绑定、两个 VM），真正读写台账的只有 `SentenceAnalysisViewModel` 与 `WordAiQaViewModel` 两个（`grep -rl outboundGrants` 只命中这两个）⇒ 拍照/生图/文章/探活四条腿结构上都拿不到豁免 |
| 附 | 生图·提示词腿（D-1 规定按域名一次，不属放宽范围） | 弹了，但**不是渗漏** | `list-65-image-dialog-warm.txt` 计数 1，且弹框自带文案「同一域名只需确认一次」——`ImageStudioViewModel` 用的是它自己的 `confirmedTextHost` 字段、不读共享台账，本会话从未在该屏确认过，所以第一次问是预期行为；随即取消（`list-66-image-cancelled.txt` 计数 0） |

### 3.3 台账热态的覆盖范围（为什么第 3、4a 项的证据算数）

`AiOutboundGrantStore` 是**进程内**内存集合，应用被杀即清空。本轮所有「不弹」判据都取自同一次连续进程：
第 2 项的确认入账发生在前，第 3 项 AI 问词的零弹框发生在后，第 4a 项文章两次弹框也都在台账已含
`www.sssai.xin` 的状态下取到——这正是 D-1 那张表要的行为差异：**同域名，高频两屏放行、逐次腿照问**。

### 3.4 真实出站次数与花费口径（如实登记，别当「发一次」交账）

用户在选项里拍的是「发一次，验完 2、3 项」。实际本会话共发生 **4 次真实文本出站**：

- 长难句 2 次（第 2 项的确认腿 1 次 + 零弹框腿 1 次）；
- AI 问词 1 次（第 3 项的零弹框腿）；
- 文章生成 1 次（为把「换一篇」摸到手而确认的那次，失败）。

多出来的不是浪费，而是**判据本体**：「同域名第二次不弹」这件事的唯一可观测形式，就是让第二次请求真的零弹框出去。
其余弹框全部点了「取消」，取消即零字节。4 次出站**全部返回内容校验失败**，未产出任何可用 AI 内容。

### 3.5 门禁

本会话仓库零代码改动，**未跑门禁**，也未重算 JVM 用例数——A 组收口报告里的 334 类 / 2479 条是上一批的现算值，
不得引用为本会话证据。`bash scripts/secret-scan.sh` 在两份文档落完后实跑过一次，**exit 0、零命中**
（本轮无新增源码，只有两份 markdown）。

### 3.6 设备侧写入的数据（用户已授权）

为把第 3、4 项走到可及的入口，`今日计划` 5 张卡全部提交了真实反馈：复习 2 张（`confidence` 记「模糊」≈11 小时、
`committee` 记「认识」≈23 小时）+ 新增 3 张（均记「认识」≈23 小时）。计划页落成「今日完成 / 5 个词」，
文章入口随之以「文章已解锁」出现（`list-56-plandone.txt`、`list-57-article.txt`）。
**这些进度是真的，明天 FSRS 会按它们排期；不是演示数据。** 阅读历史仍为 0 篇（生成失败）。

设备复原核对：输入法已切回 WeType（`settings get secure default_input_method` 读回 `com.tencent.wetype/.plugin.hld.WxHldService`）、
`font_scale` 仍 1.0、`/sdcard/DCIM/QA14` 已随走查删除（`ls` 确认不存在）、应用 `am force-stop` 收干净、
含用户相册缩略图的取证照在选完测试图后立刻删除。

## 4. Git 状态

- 分支：`stage-5-ui-r2-learning-path`（不合 `main`，按既有口径）。
- 本会话开工前 HEAD：`2c6d335`（A2.8 推送核验补记），工作树干净。
- 本次提交 `885ccc9`（两份文档：本报告 + 待办 3 打勾），逐文件 `git add`，带 `-c user.name/-c user.email`，不 `--amend`、不 `--no-verify`；本节这次补记本身是紧随其后的一枚 docs 枚（§3.5 把 secret-scan 从「未重跑」改成实跑 exit 0）。
- 是否已推送 GitHub：**尚未推送，待用户点头**（推送逐批请示）。写作本节时的现算实况：本地 HEAD `885ccc9`，`git ls-remote origin stage-5-ui-r2-learning-path` = `2c6d335`（= 上一批 A2.8 核验过的远端 tip），`git rev-list --count origin/stage-5-ui-r2-learning-path..HEAD` = 1。推送后须把远端 tip 与本地哈希对读再补记。
- 无未提交的遗留文件；证据册在 `.git/` 下，本就不入库。

## 5. 未完成 / 遗留问题

1. **N-1（新缺陷，建议立缺陷号并优先查）**：`www.sssai.xin` 的四次真实文本出站**一律**落回
   「返回内容不合格，可以重新生成。」——长难句 2 次、AI 问词 1 次、文章生成 1 次，加上用户自己在 11:15 手工复现的一次。
   与确认粒度无关（弹框闸在前、失败在响应侧），但它把「任何需要真实 AI 输出的真机验收」全部堵死：
   「换一篇」按钮、生图出图腿、生图描述→出图链路都因此不可达。
   待查方向（未验证，属推测）：该端点返回体形状不过 `AiResponse` 内容校验（例如把内容放进了 `reasoning_content`
   或带了非纯文本结构），需要一次响应体取证才能定性——取证本身又是付费请求，须先请示。
2. **N-2（新缺陷，文案谎报入口）**：词典侧的词义详情写着「长难句分析、AI 问词都从这里进入，围绕这一个词继续追问。」
   （`CardDetailScreen.kt:313`），但那条路径的 `AppScreen.kt:804` 传的是 `onAskAi = null` ⇒ 「问 AI」按钮整枚不渲染，
   只有提示语。与本轮第 3 项直接相关：**从查词页根本走不到 AI 问词**，所以第 3 项只能从学习词卡详情验。
   同一屏的另一处文案（`AiFeature.kt` 长难句卡）已在上一批改实话，这处漏了。
   两种修法需用户拍板：改文案（只留「长难句」）或给词典详情接上问答入口。
3. **第 4 项的两条腿真机未验**：「换一篇」按钮本体、生图出图。已登记为「结构证据 + 同语义腿已过」，
   等 N-1 修好后补做；不得写成已确认。
4. 待办 3 的第 1 项「取消不落账」顺带验到了，但**探活腿**（`AiProfileSettingsViewModel` 每次进入编辑页重新问）
   本会话没重验——它的语义在上一批 connected 里已过，真机目视仍挂着。

## 6. 下一任接手须知

- 第一步不是继续走查，而是**先判 N-1**：它决定后面一批 AI 相关真机验收能不能做。
  复现最短路径：真机 → AI 助学 → 长难句分析 → 输入任意英文句 → 确认发送 → 看是否落「返回内容不合格」。
- 判据脚本用法：`bash /h/EnglishLearningSoftware/.git/qa-scratch/realview-14/snap.sh <tag>`
  一次产出截屏 + 层级转写（`ui.py` 写 UTF-8 文件，**不要直接在 GBK 控制台打印中文**），
  再 `grep -c '确认发送请求' list-<tag>.txt`。
- 已知坑（本会话实测）：
  - MIUI WeType 会把 `input text` 里的 `%20` 打坏、搜狗会原样插入 `%20`；可用写法是
    `adb shell "input text '带空格的整句'"`，切输入法用 `adb shell ime set <id>`，走完记得切回。
  - 字段清空不彻底时别硬删：`am force-stop com.example.englishlearning` 重启后字段是干净的，
    但**这会清掉内存台账**，之后所有「第二次不弹」判据作废，必须重新确认一次才能继续。
  - 每次点击前重新 dump：键盘弹起会把主操作按钮从 y≈2168 抬到 y≈1312，按旧坐标会点空。
  - 系统返回键连按三次会退到 MIUI 桌面推荐流，不要靠 back 退出页面，用页内「返回」逐层退。
  - `uiautomator` 的 bounds 是节点∩父容器的可见交集，被裁到屏外的 Compose 节点整枚从层级消失——
    所以「页底按钮看不到」不等于「不存在」，先滚到底再判定（N-2 那条提示语一开始就落在 `bounds=[0,0][0,0]`）。
- 相关联文档：决策口径 `docs/decisions/2026-11-05-ai-outbound-confirmation-granularity-and-network-security-config.md`
  （D-1 表 / D-2 台账语义 / D-3 nsc），待办原册 `docs/handoff/2026-11-05-batch2-verification-gaps-todo.md`，
  上一批收口 `docs/handoff/2026-11-08-qa3-a-group-report.md`。
- 设备 QA 的通用怪癖与命令一律走 `android-connected-qa-loop` skill；本仓库会话流水线走
  `android-repo-session-handoff` skill。真机只手工试用，instrumented 只在 `emulator-5554`。

## 7. 用户确认过的决策

1. **「先跑一下真机本批四项目视」**——只做目视取证，不夹带代码改动。
2. **「发一次，验完 2、3 项（推荐）」**——授权在真机发真实付费文本请求来验「同域名第二次不弹」；
   实际发生 4 次出站（口径见 §3.4），全部失败于内容校验。
3. **「做完 5 张卡，验「换一篇」每次都弹」**——授权向手机真实学习数据写入 5 条反馈事件（含 FSRS 排期变化）
   以解锁当日文章入口。
4. 沿用的既有约束（本会话继续遵守）：分支不合 `main`、推送逐批请示；真机禁止 instrument；
   截图与日志落 `.git/qa-scratch/` 不入库；证据里不得出现 Key/挑战码明文；破坏性设备动作先确认。
