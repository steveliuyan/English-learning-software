# 2026-10-13 F3-03 OCR Provider 许可核验决策

**状态：待用户批准。批准前不得引入任何依赖、不得编写实现代码、不得下载任何模型或训练数据进仓库/资产。**

依据 AGENTS.md「未通过许可核验的词书、词典、音频或模型不得打包、下载或发布」与 `docs/handoff/2026-10-13-f3-03-photo-ocr-task.md` 第 1 节硬前置。核验渠道与访问日期见第 6 节，全部为一手来源（Maven POM、仓库 LICENSE、官方条款页、GitHub API）。

## 0. 结论摘要

| 编号 | 决定 | 结论 |
|---|---|---|
| L1 | ML Kit 主路径 | 采用 **bundled** artifact `com.google.mlkit:text-recognition:16.0.1`（拉丁语系文本识别 v2）。许可为「ML Kit Terms of Service」（非开源许可但明确允许在自研 App 中集成并商业分发），逐 POM 核验通过 |
| L2 | ML Kit 遥测条款 | ToS 声明 ML Kit 会向 Google 发送性能与使用 metrics、并可能联系 Google 服务器获取更新。**这是与本项目"本地优先、无隐式上报"约束冲突的合规义务**：须在隐私政策与应用内披露，列入发布前复核项（阶段 5），并接受真机抓包确认实际外联（见 §5 待办 T2） |
| L3 | 无 GMS 行为口径 | bundled 模型随 APK 静态打包，推理全在本机、可离线；官方无"GMS 缺失即不可用"条款，但 availability 异常仍可能出现。**不得把口径写死为"无 GMS→ML Kit 必失败"**：Provider 链按"ML Kit 初始化成功→主路径；失败→Tesseract；双失败→固定 OCR 不可用类别"（AC3-04 语义不变） |
| L4 | Tesseract 降级引擎 | **不采用 `tess-two`**（rmtheis/tess-two 已归档，README 明示 NO LONGER MAINTAINED，最后更新 2022-03，捆绑 Tesseract 3.05 过旧）。任务书候选 `ksvguyen/AndroidTesseract` 经核验 **仓库不存在（GitHub API 404）**，不可评估。采用其官方后继推荐 **`cz.adaptech.tesseract4android:tesseract4android:4.9.0`（Apache-2.0，活跃）** |
| L5 | `eng.traineddata` | 采用 `tesseract-ocr/tessdata_fast` 的 `eng.traineddata`（Apache-2.0，4,113,088 bytes，SHA 待实现期固定）。训练数据许可为 Apache-2.0，**无 CC 条款、无 ShareAlike、无署名广告条款**，可随应用按需下载与再分发，保留许可文本即可 |
| L6 | 整体裁决 | 无否决项。建议批准方案 A（ML Kit bundled 主路径 + Tesseract4Android 降级），同时批准 L2 的披露义务与 §5 待办 |

## 1. ML Kit（主路径）逐 artifact 核验

### 1.1 artifact 与版本（来自 Google Maven `com/google/mlkit/group-index.xml`）

- `com.google.mlkit:text-recognition` 16.0.0-beta1…16.0.1（当前稳定 16.0.1）——**bundled 变体**：官方文档明确 "Model is statically linked to your app at build time"，APK 体积约 +4 MB/脚本/ABI。
- `com.google.mlkit:text-recognition-bundled-common` 16.0.0…17.0.0——bundled 公共层，被上者依赖。
- `com.google.android.gms:play-services-mlkit-text-recognition` 16.0.0…19.0.1——**unbundled 变体**："Model is dynamically downloaded via Google Play Services"，约 +260 KB，首用需等待模型下载，且依赖 Google Play services/Play Store 投递。**本项目不选 unbundled**：动态下载路径与"按需、可解释、可离线"的产品约束不符，且无 GMS 设备直接不可用。
- `com.google.android.gms:play-services-mlkit-text-recognition-common` 19.1.0——unbundled 公共层。

### 1.2 许可声明（逐 POM 抓取核验）

| artifact | POM `<license>` 原文 | 说明 |
|---|---|---|
| `com.google.mlkit:text-recognition:16.0.1` | `ML Kit Terms of Service` / `https://developers.google.com/ml-kit/terms` | packaging=aar |
| `com.google.mlkit:text-recognition-bundled-common:17.0.0` | 同上 | 依赖 play-services-base/basement/tasks、mlkit `common`、`vision-common` |
| `com.google.android.gms:play-services-mlkit-text-recognition:19.0.1` | 同上 | 不采用，仅登记避免误引 |
| `com.google.mlkit:common:18.11.0`、`com.google.mlkit:vision-common:17.3.0` | 同上 | 传递依赖 |
| `com.google.android.gms:play-services-base:18.5.0`、`play-services-basement:18.4.0` | `Android Software Development Kit License` / `https://developer.android.com/studio/terms.html` | Google Maven 上全部 gms 客户端库的统一条款，允许在 Android 应用中集成与分发 |

注意：bundled artifact 的 POM 仍传递引用 `play-services-mlkit-text-recognition:19.0.1` 等 gms 客户端库。**这些只是编译期客户端 stub，不要求设备装有 GMS APK**；运行时行为以 §5 真机核验为准。台账登记时须按 gradle.lockfile 全链路逐 artifact 覆盖。

### 1.3 ML Kit Terms of Service 关键条款（页面 Last modified: 2025-05-14）

1. 条款并入并服从 **Google APIs Terms of Service**（现行版 2026-07-30 生效；其中"reverse engineering our services or underlying technology, such as our machine learning models"为禁止项之一）。
2. **机器学习模型被视为 related software**；不得逆向工程或试图提取源代码/相关软件（适用法律强制允许的除外）。→ 对本项目无碍：只调用 API，不改模型。
3. **隐私**：ML Kit API 在本机运行，输入数据（图像/视频/文本）与结果**不发送到 Google 服务器**；但 API **可能不时联系 Google 服务器**获取 bug 修复、更新模型与硬件加速器兼容性信息，并**发送关于 API 性能与使用情况的 metrics**；开发者**有责任依适用法律告知用户 Google 处理 ML Kit metrics 的事实**（Google 指引了 Play Store/App Store 数据披露要求的对照文档）。
4. GenAI API 另有附加条款——本项目不使用，无涉。

**商业分发结论**：ML Kit 的全部产品文档与示例均以"集成进自己的 App 分发"为默认用法，bundled 模型即设计为打进 APK；条款不含开源式 NOTICE 义务（因为不是开源许可），但要求遵守 ToS + 遥测披露责任。**可商业分发，条件是：不逆向、在隐私政策与应用内如实披露 Google metrics 处理。**

### 1.4 与本项目红线的一致性检查

- 本地优先：OCR 推理与图像全程本机，无用户内容外传 ✔（ToS 原文保证）。
- 遥测：与本仓库"无账户、无隐式云同步"不矛盾（metrics 非用户内容），但**构成新的数据流**，必须登记台账"数据流"字段、更新隐私文档，并在阶段 5 发布清单复核。这是 L2 待办的由来。
- 依赖面：仅 vision 路径，不引入 Firebase、不引入广告/分析 SDK。

## 2. Tesseract 降级链核验

### 2.1 引擎与图像库

| 组件 | 版本 | 许可证 | 核验来源 |
|---|---|---|---|
| Tesseract OCR | 5.5.1（由 Tesseract4Android 4.9.0 内嵌） | **Apache-2.0** | GitHub API `tesseract-ocr/tesseract` license=Apache-2.0，活跃（2026-10 仍有 push） |
| Leptonica | 1.85.0（同上内嵌） | **BSD-2-Clause 式自定义许可**（GitHub 识别为 NOASSERTION，逐字读取 `leptonica-license.txt`：标准两条款 BSD，保留版权与免责即可商用再分发） | raw LICENSE 文件文本 |
| libjpeg v9f / libpng 1.6.48 | 随 Tesseract4Android | IJG / libpng 许可证（均为宽松许可，随上游打包属常规做法） | Tesseract4Android README "Dependencies" 节 |

### 2.2 维护模式（Android 封装层）

- `rmtheis/tess-two`：**已归档**（GitHub API `archived: true`，最后 push 2022-03-17），README 第一句 "NO LONGER MAINTAINED"，捆绑 Tesseract 3.05 + Leptonica 1.74.1（2016 年代），其 LSTM 支持不完整且无法使用 v4/v5 traineddata。**结论：不采用。** 许可本身 Apache-2.0 无障碍，问题纯为维护性。
- `ksvguyen/AndroidTesseract`：任务书指定评估候选，**GitHub API 返回 404（不存在该仓库）**；搜索 "AndroidTesseract" 仅有零星个人 demo。**结论：候选失效，记录在案。**
- `adaptech-cz/Tesseract4Android`：tess-two 官方 README 推荐的项目重写；**Apache-2.0**（LICENSE 为 Apache 全文），GitHub API 未归档、最后 push 2026-02、最新 release **4.9.0（2025-06-11）**；内嵌 Tesseract 5.5.1 + Leptonica 1.85.0；支持 Standard（单线程）与 OpenMP（多线程）两个变体；通过 JitPack 分发 `cz.adaptech.tesseract4android:tesseract4android:4.9.0`；要求 Android API ≥21（本项目 minSdk 26 满足）；traineddata 需 v4.0+ 且置于应用私有目录（API≥29 限制）——与本任务"首次使用按需下载、可删除"设计吻合。**结论：采用。**
- 自封装 JNI：许可上等价（直接跟 Tesseract/Leptonica 上游），但把 NDK 构建、abi 矩阵、生命周期全部自担，维护成本最高；作为 Tesseract4Android 停止维护后的后备路径记录，不在 V1 采用。

### 2.3 `eng.traineddata` 训练数据

| 来源 | 许可证 | 大小 | 特性 |
|---|---|---|---|
| `tesseract-ocr/tessdata_fast` `eng.traineddata` | **Apache-2.0**（仓库 LICENSE 与 README 声明；GitHub API license=Apache-2.0） | 4,113,088 bytes | 整型 LSTM，速度快、体积小，适配移动端 |
| `tesseract-ocr/tessdata_best` `eng.traineddata` | Apache-2.0（README 明示 "All data in the repository are licensed under the Apache-2.0 License"） | 15,400,601 bytes | float LSTM，精度高、慢且大 |

- 两个仓库均为 LSTM-only（Tesseract 4/5 的 oem 1），与 Tesseract4Android 5.5.1 匹配；legacy 引擎模式不可用不影响本功能。
- **无 CC-BY-SA、无"非商业"标注、无署名广告条款**——与本项目对数据集最严格的两条红线（GPL-3.0 静态集成、非商用模型）均无冲突。
- 选型：**tessdata_fast**（下载体量 4 MB 级，对手机 OCR 场景速度优先）；分发方式为**首次使用时按需下载**（展示大小与许可），不打进 APK。恢复/重装后可再次下载，符合备份资源规则（traineddata 属可重下资源，不必进备份包，此点写入 F3-03 实现期的备份接线）。
- 实现期锁定：下载 URL 固定到具体 commit/tag，写入 SHA-256 校验，失败拒绝安装（资源完整性沿用 F3-02 模型下载先例）。

## 3. 替代方案盘点（若用户否决 ML Kit 或 Tesseract4Android）

| 方案 | 许可 | 否决理由 |
|---|---|---|
| 纯 Tesseract（无 ML Kit） | 全 Apache/BSD | 质量显著低于 ML Kit v2（真实照片、多方向、小字），且需自担 4 MB traineddata 冷启动体验；作为 L1 被否决时的降级备选保留 |
| ML Kit unbundled（gms artifact） | ML Kit ToS | 首用依赖 Play Services 动态下载，无 GMS 设备不可用，与降级链冲突 |
| tess-two | Apache-2.0 | 已归档，引擎过旧 |
| Google Cloud Vision / 任意云 OCR | 服务条款 | 图像必须离开设备，违背本地优先与确认门红线，直接排除 |
| Android 系统 `AccessibilityService`/`TextEdit` 类提取 | 平台 | 只读屏幕文本，不能识别相机照片，功能不等价 |

## 4. 方案与批准请求

**方案 A（建议批准）**：ML Kit `text-recognition:16.0.1`（bundled）主路径 + Tesseract4Android `4.9.0`（OpenMP 变体在验收中按性能择一）+ tessdata_fast `eng.traineddata` 按需下载。

批准 A 即同时批准以下义务，后续台账（`docs/third-party-notices.md`）将按 catalog alias 逐条登记八字段条目（ML Kit 至少覆盖 `text-recognition`、`text-recognition-bundled-common`、`common`、`vision-common`、`play-services-base`、`play-services-basement`、`play-services-tasks`；Tesseract 侧覆盖 `tesseract4android`、`tesseract`、`leptonica`、`libjpeg`、`libpng`、`eng-traineddata`），并以 gradle.lockfile 实际解析结果复核：

1. ML Kit 遥测披露（L2）：隐私政策与应用内说明"ML Kit 会向 Google 发送性能指标（不含图像与文本内容）"。
2. NOTICE：Apache-2.0 组件保留许可与 NOTICE 原文；BSD 组件保留两条款声明；ML Kit 组件附 ToS 引用（无开源 NOTICE 文本可附，登记结论说明）。
3. 不逆向、不修改模型；traineddata 不微调后冒充官方来源。

**方案 B**：弃 ML Kit，Tesseract 单引擎。仅当用户不接受 L2 遥测披露义务时选择；代价为识别质量与首包体验下降。

任一方案确定后，才进入任务书第 2 节提交切分（域层端口 → 设备层 → 采集临时区 → 确认门翻译页 → 历史与备份）。

## 5. 实现期与真机待办（批准不等于跳过）

- T1：新增依赖后按红线三任务一次 `--write-locks`；台账逐 artifact 补录版本与哈希。
- T2：**真机（bf353dda）核验口径**：(a) bundled ML Kit 在禁用/无 GMS 环境下的初始化与识别结果；(b) 抓包确认 ML Kit 联系 Google 服务器的实际时机与内容（验证 ToS 第 3 条 metrics 描述），据此定稿隐私披露文案；(c) 无 GMS + ML Kit 不可用时自动走 Tesseract 并产出结果；(d) 双不可用时显示固定「OCR 不可用」类别而非翻译失败（AC3-04）。上述完成前，AC3-04 验收记录不得标"已验证"。
- T3：Tesseract4Android 仅存在于 JitPack（不在 Maven Central）——gradle 需增加 `maven { url 'https://jitpack.io' }` 仓库；锁定版本 4.9.0，并在台账记录来源 URL 与校验方式。

## 6. 核验来源（访问日期 2026-10-13，均为一手页面/文件/API）

- ML Kit ToS：<https://developers.google.com/ml-kit/terms>（Last modified 2025-05-14；经 developers.google.cn 镜像抓取全文）
- Google APIs Terms of Service：<https://policies.google.com/terms>（2026-07-30 生效版，逆向工程禁止条款见 "What we expect from you"）
- ML Kit TRv2 Android 文档（bundled/unbundled 对照表、minSdk 23、依赖声明）：<https://developers.google.com/ml-kit/vision/text-recognition/v2/android>
- Google Maven 元数据与 POM（逐 artifact license/依赖）：`https://dl.google.com/dl/android/maven2/com/google/mlkit/...`、`https://dl.google.com/dl/android/maven2/com/google/android/gms/...`（text-recognition 16.0.1 / bundled-common 17.0.0 / play-services-mlkit-text-recognition 19.0.1 / common 18.11.0 / vision-common 17.3.0 / base 18.5.0 / basement 18.4.0）
- GitHub API：`tesseract-ocr/tesseract`（Apache-2.0）、`DanBloomberg/leptonica`（license 文件逐字核验）、`tesseract-ocr/tessdata_fast`、`tesseract-ocr/tessdata_best`（Apache-2.0 + eng.traineddata 大小）、`rmtheis/tess-two`（archived=true）、`adaptech-cz/Tesseract4Android`（Apache-2.0、release 4.9.0）、`ksvguyen/AndroidTesseract`（404）
- Tesseract4Android README（内嵌依赖版本、traineddata 要求、JitPack 分发）：<https://github.com/adaptech-cz/Tesseract4Android>
