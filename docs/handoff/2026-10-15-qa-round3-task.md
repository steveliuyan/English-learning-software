# 第三轮设备 QA 任务书（SP-C 走查攒批，2026-10-15）

> 交接对象：独立设备 QA 分对话。本任务书自包含，执行前仍须读 `AGENTS.md`、
> `docs/specs/05-release-quality-and-compliance.md`（F5-03/AC5-03）与
> `android-connected-qa-loop` skill。基线分支：
> `stage-5-sp-cde-licenses-release-and-accessibility`（SP-C/D/E 收口后按惯例听用户拍板 FF 合 main；
> 若届时已合 main，直接在 main 上走查，产物提交按仓库交接惯例）。

## 0. 总纪律（违反任何一条=本轮结果作废）

- 连接测试**只用** `adb install -r` + `am instrument`，**禁用** `connectedDebugAndroidTest`（会卸载应用并清空设备数据，前两轮已受其害）。
- 多设备环境先 `export ANDROID_SERIAL=<serial>`，所有 adb 命令锁同一台；已知设备：emulator-5576（connected 功能机，onboarding 已完成）、emulator-5574（性能基线机）、bf353dda（MIUI 真机，装完立即测，防自动卸载）。
- 隐私红线：挑战码/配对 QR 截图/API Key/任何密钥明文**一律不入库**——截图与脚本放 `.git/qa-scratch/` 或本地忽略区；报告与 logcat 摘录零密钥明文。
- UI 无响应按怪癖表处置（息屏→弹窗劫持→Gboard），别急着怀疑应用；每次 tap 前重新 `uiautomator dump`。
- 收工报告按 `docs/handoff/REPORT-TEMPLATE.md` 七节，保存为 `docs/handoff/2026-10-15-qa-round3-report.md`；提交带 git 身份参数、逐文件 add；推送走本机代理 127.0.0.1:3067 + `-c credential.interactive=false`，推后 `ls-remote` 核验（第一轮文档推送失败丢失的教训）。
- 任何一项不可达/环境不具备：如实标 blocked 并写清缺什么，**不得标已验证**；未做不写通过。

## 1. AC5-03：TalkBack 六链关键流程（spec 05 F5-03）

开启 TalkBack（设置或 `adb shell cmd accessibility ...`/手动），逐链走查并记录焦点顺序、朗读内容与可达性：

1. 选词书（Setup/词书列表流程）
2. 开始学习（今日计划→进入词卡）
3. 提交三档反馈（不认识/模糊/认识）
4. 查看完成状态（今日计划完成态）
5. 打开历史文章（历史列表→重读）
6. 进入数据管理入口（设置→备份/恢复/删除全部学习数据；确认「删除全部学习数据」**只走查预览与取消**，不真删）

遗留专项：**词卡「显示释义」揭晓按钮焦点顺序**（QA 第二轮遗留，roadmap 2026-10-14 条）——回忆-揭晓流程下揭晓按钮应在词义内容之前获得焦点且朗读不含答案。

## 2. F5-03 展示矩阵

- 动态字体拉到**最大档**：核心路径不破版、不截断关键操作（词卡、今日计划、设置、新许可页）。
- 深色模式：全页面走查（D-2 遗留缺陷复测——见 QA 第二轮报告 D-2 条目，验证是否仍在）。
- 小屏 320dp 宽度：核心页可用性（建议 AVD 或 `wm size` 临时改，**测完复原 1080x2400 与 font_scale 1.0**）。
- **新页「关于与开源许可」TalkBack 朗读**（本分支新增）：设置→关于与开源许可→条目展开/收起→返回；朗读应含条目名、展开后的用途/数据流等字段，返回按钮 contentDescription=「返回」生效。

## 3. TODO-USER 攒批（阶段 4 / SP-B / F3-03 遗留）

- **B3**：API 26–29 BiometricPrompt 凭据降级分支——需 API 26–29 环境（AVD api26/30 镜像探测，`emulator -list-avds`；环境搭建超 ~30 分钟不通即 blocked 走人）。
- **bf353dda 六项性能复测**：对照 `docs/verification/perf-baseline-2026-10.md` 阈值列（冷启动 5000ms、plan_read 50ms、card_paging 50ms、word_lookup 50ms、event_append_100 650ms、backup_validate_500 500ms、restore_write_500 350ms），命令见该文档复测节；真机数值**只登记对照结论，不改模拟器基线表**（换基线设备才整表重测）。
- **飞行模式手工六条**：按 `docs/verification/ac5-02-offline-script.md` 链路清单逐条执行并登记复选框。
- **B4**：恢复页选择 `.elpbak`（Salvage 版）包→应拒收固定提示、正式数据零写入（模拟器 documentsui 选文件曾被 blocked 30+ 次 tap；真机侧优先试）。
- **B5**：真实 AI Profile 端到端（显式保存→历史→备份→恢复）——`TODO-USER`，等用户真实 Endpoint+Key；未配置就如实 blocked。
- **T2**：ML Kit 内容级抓包核验——`TODO-USER`，等用户 CA/可解密环境；用户已拍板项不擅自投入。
- **AppScreenTest 8 个 back/导航族 flaky**：记录复现条件（设备、步进方式、是否 TalkBack/字体缩放开着），有基线对照证据（SP-B 报告 §3），**不改断言**、只归因登记。

### 阶段 5 F5-03 收口攒批（Task 12 追加，只增不改上方条目）

来源：`docs/verification/f5-03-accessibility-hardening-2026-10.md` §9、`docs/decisions/2026-10-15-f5-03-accessibility-hardening-semantics.md` §2/§4/§6。均为代码侧完成但缺设备取证项：

- **I-8 四控真出声冒烟**：真机验「暂停→静音、继续→复播、停止→文案回空闲」。connected 断言只证委托发生，TTS 侧只能报「已请求」，无法自动化证明真出声。
- **系统 TTS 下「只有停止」的可理解性**：暂停/继续按钮呈现条件=选中引擎 ≠ SystemTts（决策 §2），TalkBack 下系统 TTS 用户只见「停止」是否会困惑；词卡路径 `pauseResumeSupported` 偏保守（系统 TTS 用户看不到本可工作的暂停）。
- **真机 1.25x→1.0x 降速取证**：系统 TTS 变速目前只有 JVM 序列证据，真机需确认切回常速真的降速（`fdbf4b4` 修的就是「切回不降速」）。
- **滑动删除 `customActions(label="删除词书")` 可达性**：Compose 测试框架无断言 customAction 可达性的 API（禁伪造断言），需 TalkBack 下走查该动作是否可触发且仍只弹二次确认、不真删。
- **揭晓焦点播报**：词卡「显示释义」由隐藏→可见时 `FocusRequester.requestFocus()` 是否真落地、朗读不含答案（全仓首个焦点顺序改动，`runCatching` 防崩）。
- **动态字体顶档不破版**：逐档走查，**必须含 `fontScale = 3.0` 与真机 MIUI 字体档**（connected 护栏只覆盖 2.0 档；几何推演 3.0 档三条 48dp 链会吃满约 370.6dp 内容宽、标题可能已 <48dp，HEAD 代码在该档会让护栏变红）——含本轮改过的底栏与 CheckIn 图表。
- **放大药片打架**：喇叭药片 `minimumInteractiveComponentSize` 在 padding 内侧、随节点放大到 ≈60dp，取证在词卡/音标行是否与相邻控件打架。
- **两处含字徽标顶档**：`AiFeatureScreen.kt:84 size(44.dp)`、`SettingsScreen.kt:395 size(32.dp)` 在 fontScale 顶档字形顶出底块的破版证据（归重设计轮）。
- **深色走查**：F5-03 侧 Compose 深色完全未落地（见 §2 深色全页面走查、D-2 复测），token 化归 UI 重设计轮，此处仅登记设备现状。
- **字母栏触控实际可达性**：词书字母 Rail 现命中区 ≈24×13dp（纵向未铺满整槽，代偿=整槽内命中 + 整轨拖动），真手指下的可命中率取证（48dp 目标归重设计轮）。

## 4. 证据与出口

- connected 测试需要跑时用 `android-connected-qa-loop` §1 序列（`assembleDebug`+`assembleDebugAndroidTest`→`install -r`→`am instrument -e class <FQCN>#<method>`）。
- 本轮新增 connected 资产（如需回归参考）：`LedgerAssetSyncTest`、`LicensesScreenTest`、`SettingsLicensesEntryTest`、`RecoveryScreenTest`。
- 报告 §3 验证记录须含每条命令原文与输出末行；§5 逐条回答本任务书每一项的 通过/blocked+原因；§7 只登记用户实际拍板过的决策。
