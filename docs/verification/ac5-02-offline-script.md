# AC5-02 离线核心链路验收脚本（阶段 5 / SP-B Task 7）

> 口径：`docs/specs/05-release-quality-and-compliance.md` F5-02/AC5-02 与
> `docs/decisions/2026-10-15-sp-b-performance-and-reliability-semantics.md` §4。
> 自动化子集（库层，connected）已由 `androidTest perf.OfflineCorePathsInstrumentedTest`（4 用例）钉死；
> 本脚本覆盖**设备级飞行模式**手工六条链路，逐条勾选并记录证据。

## 前置

1. 设备：emulator-5576（或 5574）。已完成 onboarding，存在至少 1 个词书计划、1 篇已缓存文章、离线查词索引已建立。
2. 先跑自动化子集（飞行模式无关的进程内断言）：

```bash
adb -s emulator-5576 shell am instrument -w \
  -e class com.example.englishlearning.perf.OfflineCorePathsInstrumentedTest \
  com.example.englishlearning.test/androidx.test.runner.AndroidJUnitRunner
# 预期 OK (4 tests)：反馈重放零重复/到期查询、缓存文章回读、离线词条检索、AI 不可达→Failed(NetworkUnavailable) 零写入
```

3. 开启飞行模式（API 30 实测可用命令）：

```bash
adb -s emulator-5576 shell cmd connectivity airplane-mode enable
adb -s emulator-5576 shell dumpsys connectivity | grep -m1 "Airplane mode"   # 必须显示 enabled
```

## 手工六条链路（逐条勾）

| # | 链路 | 操作 | 预期 | 结果（填 PASS/FAIL+备注） |
|---|---|---|---|---|
| 1 | 背词 | 今日计划 → 开始学习 → 三档反馈提交 | 反馈即时成功，卡片翻页不卡、无网络报错 | ☐ |
| 2 | 复习 | 复习入口 → 到期卡 | 到期卡正常出卡，评分保存成功 | ☐ |
| 3 | 已缓存文章阅读 | 阅读页 → 今日已生成文章 | 全文+译文可读、可高亮查词、可发音（系统 TTS） | ☐ |
| 4 | 离线查词 | 查词页输入已索引单词 | 词典命中，释义显示 | ☐ |
| 5 | 系统 TTS | 任意词卡/文章点发音 | 离线英语包发音可播；无包时显示安装提示而非崩溃 | ☐ |
| 6 | AI 入口文案 | 文章「换一篇」/拍照翻译/AI 语音 | 明确「网络/AI 不可用」类可行动提示，**不转圈卡死**，本地链路不受影响 | ☐ |

测完复原：`adb -s emulator-5576 shell cmd connectivity airplane-mode disable`。

## 阶段 4 遗留承接登记

- **B4 Salvage 包拒收**：既有自动化证据 = `backup.BackupRestorePipelineTest#v2 salvage package answers not restorable without entering the chain`（RestoreFailure.SalvageNotRestorable）、`backup.BackupPackageValidatorV3Test#v2 salvage package cannot smuggle a pairing entry`、`ui.RestoreNoBypassContractTest`（源码契约禁 salvage 入口）。设备手工核：恢复页选择 `.elpbak`（salvage 版）包 → 应拒收并给出固定分类提示，正式数据零写入。结果：☐（手工待走查攒批）。
- **B5 真实 AI Profile 端到端**：`TODO-USER`——需用户真实 Endpoint+Key 配置，未做不写通过。
- **T2 内容级抓包核验**：`TODO-USER`——需用户侧 CA/抓包环境，未做不写通过。

## 记录要求

每次执行在本文件「结果」列勾选并注明设备 serial + 日期；截图（如拍）一律放 `.git/qa-scratch/` 或本地忽略区，不入库；任何密钥/配对码明文不得出现在本文件。
