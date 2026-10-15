# 决策：SP-C/D/E 范围口径——许可页数据源、R8 缓开、可选签名、无障碍走查分轮

日期：2026-10-15
关联：`docs/specs/05-release-quality-and-compliance.md`（F5-01/F5-03/F5-04）、
计划：`docs/superpowers/plans/2026-10-15-stage-5-sp-cde-licenses-release-and-accessibility.md`

## 背景

SP-B 合入 main（tip `285a4c4`）后，用户拍板接续 SP-C（F5-03 可访问性与兼容性）、SP-D（F5-04 隐私、许可与发布审核）、SP-E（F5-01 发布构建与签名）。查勘事实：

- 「关于与开源许可」页**不存在**（全仓无 LicenseScreen/About 类屏幕）；
- 台账 `docs/third-party-notices.md` 有 72 条，root `verifyThirdPartyNotices` 已强制八字段非空；
- `app/build.gradle.kts` **没有任何 buildTypes/signingConfig**，release 从未构建过；
- keystore 不存在，签名材料只在用户手里；
- AC5-03（TalkBack 六链）本质是设备走查，无法在本对话以 JVM 单测「完成」。

## 候选与决定

### 1. 许可页数据源：构建期复制台账进 assets（单一事实源）

候选：A. Kotlin 内再抄一份台账列表；B. 构建期 `Copy` 任务把 `docs/third-party-notices.md` 同步进 `app/build/generated/noticesAssets`，运行时纯解析器展示；C. 构建期生成 JSON。

**决定：B。** A 必然漂移（72 条改一条就忘）；C 引入自定义 schema 与生成链复杂度。B 让「设备上看到的=门禁校验过的」，并由 connected 测试 `LedgerAssetSyncTest` 对真 APK 资产跑同一 `verifyThirdPartyNotices` 校验函数钉死一致性。

### 2. R8/资源压缩：本轮不启用，记技术债

候选：A. release 立即开 `isMinifyEnabled=true`+keep 规则；B. 本轮保持关闭，先打通构建-扫描-签名链。

**决定：B。** Room/KSP/Hilt/ML Kit/Tesseract/ONNX 的 keep 规则组合必须真机专项验证，混进本轮会让「发布链是否通」与「R8 是否破坏运行时」两问题纠缠。再启用条件：release 产物真机全链冒烟通过后另开小轮，逐库开 minify 并 connected 复验。

### 3. 签名：local.properties 可选四键，缺省未签名且不谎称验签

**决定：** release buildType 读 `local.properties` 的 `RELEASE_STORE_FILE/RELEASE_STORE_PASSWORD/RELEASE_KEY_ALIAS/RELEASE_KEY_PASSWORD`；存在则签名，不存在则产出 `app-release-unsigned.apk` 并在配置期如实打印「keystore 未配置：验签 TODO-USER」。理由：签名密钥是用户的生产资产，仓库与 CI 都不该持有；门禁验证「构建+扫描」，「签名验证」在用户提供商号 keystore 后执行（Spec F5-01 第三条已按此如实修订）。四键绝不入库、不进报告。

### 4. AC5-05 发布构建扫描：新增 apk-secret-scan.sh 并入门禁

候选：A. 仅靠仓库级 `secret-scan.sh`（源不含密钥则 APK 也不含）；B. A + 对 APK 产物做条目清单与 assets 内容扫描。

**决定：B。** AC5-05 点名「发布构建扫描」，只扫源码是口径偷换。脚本三项检查：禁入文件类型（.jks/.keystore/.p12/.env/local.properties）、assets 内容密钥模式（沿用 SP-A 收紧后的 `sk-` 与 `ELPAIR1|` 模式）、未签名产物如实 WARN 不 fail。先以自测脚本（造假 APK 必红、净包必绿）建立可信度。

### 5. SP-C（F5-03/AC5-03）：走查移交第三轮设备 QA 分对话，本轮只补代码侧语义契约

候选：A. 本对话用 adb 驱动 TalkBack 走查；B. 产出攒批任务书（含 AC5-03 六链、焦点遗留、bf353dda 性能复测、飞行模式六条、B3/B4/B5/T2、AppScreenTest flaky），派独立设备对话执行。

**决定：B**，沿用用户既定的「走查攒批派分对话」模式；本轮代码侧补 `SettingsLicensesEntryTest`/`LicensesScreenTest` 语义契约（可达、无假入口、诚实降级），矩阵 AC5-03 如实标「部分覆盖（自动化语义 + 走查任务书已备）」。

### 6. AC5-01「发布构建中通过」口径

**决定：** 验收=单测全绿（debug 门禁）+ `assembleRelease` 成功 + APK 扫描绿，经 `checkReleaseReadiness` 一次聚合。「release 变体重跑全量单测」不强制（同码同库，双变体测试为纯重复开销），此口径登记于矩阵与本报告。

## 影响

- Spec F5-01 第三条按 §3/§6 修订（先改 Spec 再实施，AGENTS.md 要求）。
- 新增文件：`NoticesCatalog.kt`、`NoticesPrivacyStatement.kt`、`LicensesScreen.kt`、`scripts/apk-secret-scan.sh(+selftest)`、`release-notes-v0.0.1.md`、QA 轮三任务书；改动 `app/build.gradle.kts`（assets+buildTypes）、root `build.gradle.kts`（verifyReleaseArtifact）、`SettingsScreen.kt`、`AppScreen.kt`、矩阵。
- 无新增第三方依赖 → lockfile 不动。
- 遗留登记：验签 TODO-USER、R8 技术债、AC5-03 设备轮、bf353dda 性能复测、飞行模式手工六条、B4/B5/T2、AppScreenTest flaky（延续 SP-B 清单）。
