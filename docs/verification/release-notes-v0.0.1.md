# 发布说明 v0.0.1（F5-04，AC5-04）

日期：2026-10-15
版本：versionName `0.0.1` / versionCode `1`（`app/build.gradle.kts` defaultConfig）
适用：阶段 0–5 SP-A/B/C?/D/E（SP-C 设备走查轮另出记录）

## 产品边界（发布必告知的五要点）

1. **无账户**：学习数据只保存在本机，不上传任何服务器。
2. **无自动云备份**：只有用户主动导出时才会生成本地文件；**卸载、清除数据或设备损坏前未导出数据，可能无法恢复**。
3. **AI 为用户自配**：文章、翻译、拍照翻译与可选云端发音都要求用户自行配置兼容 OpenAI 的服务；未配置或未确认时零出网。
4. **Key 不导出**：API Key 只存 Android Keystore 支撑的安全凭据区，不进 Room 业务表、日志、崩溃报告、剪贴板、导出包、文章元数据与截图诊断，也无法在应用内查看。
5. **照片发送第三方前需确认**：图片出站每次都需明确确认，拒绝确认则零出网。

同一口径固化在应用内「关于与开源许可」页（`ui/LicensesScreen`，数据源=构建期同步的 `docs/third-party-notices.md`，见决策 `2026-10-15-sp-cde-licenses-page-release-build-and-qa-batch.md`）。

## 许可与台账终审结论

- 台账 `docs/third-party-notices.md`：**73 条**，`verifyThirdPartyNotices` 强制八字段（名称/版本/许可证/用途/数据流/NOTICE 位置/替代方案/商业分发结论）非空。
- 本轮终审新增 `user-configured-ai-endpoints` 条目（用户自配 OpenAI 兼容端点与 MiMo/Talkify 式在线 TTS 上游服务：不随包分发、条款用户自担、逐次确认出站）——此前为 AC5-04 真缺项。
- 未核验资源处置不变：第三方词条正文、未闭合许可的 TTS 正式模型与 Spike 资源**禁止进入发布构建**（见 `datasets`、`models` 条目）。

## 发布门禁命令（本机实测口径）

```bash
GRADLE="C:/Users/steveliuyan/.gradle/wrapper/dists/gradle-8.13-bin/5xuhj0ry160q40clulazy9h7d/gradle-8.13/bin/gradle.bat"
"$GRADLE" checkReleaseReadiness --no-daemon --no-build-cache   # notices+矩阵+单测+secret-scan+release 产物扫描
bash scripts/secret-scan-selftest.sh && bash scripts/secret-scan.sh
bash scripts/apk-secret-scan-selftest.sh
```

## 已知边界与 TODO-USER（发布前必须处置/知悉）

- **签名**：release 产物当前未签名（仓库不持有 keystore）。用户提供发布 keystore 并在 `local.properties` 配置四键后重跑 `:app:assembleRelease` 与验签；详见决策 §3。
- **R8/资源压缩**：本轮未启用（决策 §2），后续专项轮验证。
- **AC5-03 无障碍设备走查**：TalkBack 六链、动态字体、深色、小屏以第三轮设备 QA 任务书（`docs/handoff/2026-10-15-qa-round3-task.md`）执行为准；本说明发布前其结论为「部分覆盖」。
- 真机 bf353dda 性能复测、飞行模式手工六条、B4/B5/T2 同见任务书 TODO-USER 清单。
