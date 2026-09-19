# 第三方依赖、数据集与模型台账

本台账是发布前许可检查的输入。未完整登记、未完成许可核验的依赖、数据集或模型不得进入正式发布包。

## androidx-core-ktx
- 名称: AndroidX Core KTX
- 版本: 1.17.0
- 许可证: Apache-2.0
- 用途: Android Kotlin 基础兼容 API
- 数据流: 仅在设备内处理应用状态，不传输用户数据
- NOTICE 位置: APK 的 META-INF/NOTICE 与本文件
- 替代方案: Android 平台 API
- 商业分发结论: 可商业分发，保留 Apache-2.0 许可证与 NOTICE

## androidx-activity-compose
- 名称: AndroidX Activity Compose
- 版本: 1.12.1
- 许可证: Apache-2.0
- 用途: Compose Activity 集成
- 数据流: 仅在设备内托管 UI 状态，不传输用户数据
- NOTICE 位置: APK 的 META-INF/NOTICE 与本文件
- 替代方案: Android Views Activity
- 商业分发结论: 可商业分发，保留 Apache-2.0 许可证与 NOTICE

## androidx-compose-bom
- 名称: Jetpack Compose BOM
- 版本: 2025.12.00
- 许可证: Apache-2.0
- 用途: 对齐 Compose 依赖版本
- 数据流: 仅在构建时解析版本，不处理用户数据
- NOTICE 位置: 构建元数据与本文件
- 替代方案: 分别固定 Compose 模块版本
- 商业分发结论: 可商业分发，保留 Apache-2.0 许可证与 NOTICE

## androidx-compose-ui
- 名称: Jetpack Compose UI
- 版本: 由 androidx-compose-bom 2025.12.00 管理
- 许可证: Apache-2.0
- 用途: 声明式本地 Android UI
- 数据流: 仅在设备内渲染应用状态，不发起网络请求
- NOTICE 位置: APK 的 META-INF/NOTICE 与本文件
- 替代方案: Android Views
- 商业分发结论: 可商业分发，保留 Apache-2.0 许可证与 NOTICE

## androidx-compose-material3
- 名称: Jetpack Compose Material 3
- 版本: 由 androidx-compose-bom 2025.12.00 管理
- 许可证: Apache-2.0
- 用途: 本地 Material 3 UI 组件
- 数据流: 仅在设备内渲染 UI，不收集或传输用户数据
- NOTICE 位置: APK 的 META-INF/NOTICE 与本文件
- 替代方案: 自定义 Compose 组件
- 商业分发结论: 可商业分发，保留 Apache-2.0 许可证与 NOTICE

## androidx-room-runtime
- 名称: Room Runtime
- 版本: 2.8.4
- 许可证: Apache-2.0
- 用途: 后续本地关系数据访问
- 数据流: 后续仅处理应用私有存储中的本地数据，不传输网络数据
- NOTICE 位置: APK 的 META-INF/NOTICE 与本文件
- 替代方案: SQLiteDatabase
- 商业分发结论: 可商业分发，保留 Apache-2.0 许可证与 NOTICE

## androidx-room-ktx
- 名称: Room KTX
- 版本: 2.8.4
- 许可证: Apache-2.0
- 用途: Room Kotlin 协程扩展
- 数据流: 后续仅处理应用私有存储中的本地数据，不传输网络数据
- NOTICE 位置: APK 的 META-INF/NOTICE 与本文件
- 替代方案: Room Java API
- 商业分发结论: 可商业分发，保留 Apache-2.0 许可证与 NOTICE

## kotlinx-serialization-json
- 名称: Kotlinx Serialization JSON
- 版本: 1.8.1
- 许可证: Apache-2.0
- 用途: 对齐 Room migration test 运行时所需的 Kotlin serialization API
- 数据流: 仅在设备内序列化 Room schema 测试元数据，不传输用户数据
- NOTICE 位置: APK 的 META-INF/NOTICE 与本文件
- 替代方案: 保持与 Room 传递依赖兼容的 Kotlinx Serialization 版本
- 商业分发结论: 可商业分发，保留 Apache-2.0 许可证与 NOTICE

## androidx-room-compiler
- 名称: Room Compiler
- 版本: 2.8.4
- 许可证: Apache-2.0
- 用途: Room schema 与 DAO 代码生成
- 数据流: 仅在构建时读取本地源代码，不处理用户数据
- NOTICE 位置: 构建插件依赖与本文件
- 替代方案: 手写 SQLite 访问层
- 商业分发结论: 构建依赖不随 APK 分发，可商业使用并保留 Apache-2.0 说明

## androidx-security-crypto
- 名称: AndroidX Security Crypto
- 版本: 1.1.0
- 许可证: Apache-2.0
- 用途: 后续 Android Keystore 支撑的本地安全存储
- 数据流: 后续仅处理设备内凭据密文，不上传网络
- NOTICE 位置: APK 的 META-INF/NOTICE 与本文件
- 替代方案: Android Keystore 平台 API
- 商业分发结论: 可商业分发，保留 Apache-2.0 许可证与 NOTICE

## hilt-android
- 名称: Dagger Hilt Android
- 版本: 2.57.2
- 许可证: Apache-2.0
- 用途: 后续依赖注入运行时组装
- 数据流: 不处理、收集或传输用户数据
- NOTICE 位置: APK 的 META-INF/NOTICE 与本文件
- 替代方案: 手写构造函数注入
- 商业分发结论: 可商业分发，保留 Apache-2.0 许可证与 NOTICE

## hilt-compiler
- 名称: Dagger Hilt Compiler
- 版本: 2.57.2
- 许可证: Apache-2.0
- 用途: Hilt 注入代码生成
- 数据流: 仅在构建时读取本地源代码，不处理用户数据
- NOTICE 位置: 构建插件依赖与本文件
- 替代方案: 手写依赖注入
- 商业分发结论: 构建依赖不随 APK 分发，可商业使用并保留 Apache-2.0 说明

## junit-jupiter
- 名称: JUnit Jupiter
- 版本: 5.12.2
- 许可证: EPL-2.0
- 用途: JVM 单元测试
- 数据流: 仅读取本地测试输入，不处理生产用户数据或网络数据
- NOTICE 位置: 开发与测试依赖，不打包进 release APK；本文件
- 替代方案: kotlin.test
- 商业分发结论: 测试依赖不随 APK 分发；源码测试分发时保留 EPL-2.0 说明

## androidx-test-core
- 名称: AndroidX Test Core
- 版本: 1.7.0
- 许可证: Apache-2.0
- 用途: Android 测试上下文支持
- 数据流: 仅在测试设备内读取测试状态，不上传数据
- NOTICE 位置: 开发与测试依赖，不打包进 release APK；本文件
- 替代方案: Android instrumentation API
- 商业分发结论: 测试依赖不随 APK 分发；保留 Apache-2.0 说明

## androidx-test-ext-junit
- 名称: AndroidX Test Ext JUnit
- 版本: 1.3.0
- 许可证: Apache-2.0
- 用途: Android instrumentation JUnit 测试运行支持
- 数据流: 仅在测试设备内读取测试状态，不上传数据
- NOTICE 位置: 开发与测试依赖，不打包进 release APK；本文件
- 替代方案: Android instrumentation API
- 商业分发结论: 测试依赖不随 APK 分发；保留 Apache-2.0 说明

## androidx-test-runner
- 名称: AndroidX Test Runner
- 版本: 1.7.0
- 许可证: Apache-2.0
- 用途: 启动 `androidx.test.runner.AndroidJUnitRunner` instrumentation 测试
- 数据流: 仅在测试设备内编排测试执行，不上传数据
- NOTICE 位置: 开发与测试依赖，不打包进 release APK；本文件
- 替代方案: Android instrumentation API
- 商业分发结论: 测试依赖不随 APK 分发；保留 Apache-2.0 说明

## robolectric
- 名称: Robolectric
- 版本: 4.16
- 许可证: MIT
- 用途: 本地 JVM Android 行为测试
- 数据流: 仅运行本地测试，不处理生产用户数据或网络数据
- NOTICE 位置: 开发与测试依赖，不打包进 release APK；本文件
- 替代方案: Android instrumented tests
- 商业分发结论: 测试依赖不随 APK 分发；保留 MIT 许可证文本

## mockk
- 名称: MockK
- 版本: 1.14.5
- 许可证: Apache-2.0
- 用途: JVM 测试替身
- 数据流: 仅处理本地测试数据，不传输网络数据
- NOTICE 位置: 开发与测试依赖，不打包进 release APK；本文件
- 替代方案: 手写 fake 或 Mockito
- 商业分发结论: 测试依赖不随 APK 分发；保留 Apache-2.0 说明

## turbine
- 名称: Turbine
- 版本: 1.2.1
- 许可证: Apache-2.0
- 用途: Kotlin Flow 测试
- 数据流: 仅处理本地测试流数据，不传输网络数据
- NOTICE 位置: 开发与测试依赖，不打包进 release APK；本文件
- 替代方案: kotlinx-coroutines-test
- 商业分发结论: 测试依赖不随 APK 分发；保留 Apache-2.0 说明

## kotlinx-coroutines-test
- 名称: kotlinx-coroutines-test
- 版本: 1.10.2
- 许可证: Apache-2.0
- 用途: Kotlin 协程测试调度与断言
- 数据流: 仅在本地测试进程执行，不处理用户数据
- NOTICE 位置: 开发与测试依赖，不打包进 release APK；本文件
- 替代方案: 手写协程测试调度器
- 商业分发结论: 测试依赖不随 APK 分发；保留 Apache-2.0 说明

## kotlin-test-junit5
- 名称: Kotlin Test JUnit 5
- 版本: 2.1.21
- 许可证: Apache-2.0
- 用途: Kotlin 单元测试断言与 JUnit 5 集成
- 数据流: 仅在本地测试进程执行，不处理用户数据
- NOTICE 位置: 开发与测试依赖，不打包进 release APK；本文件
- 替代方案: 直接使用 JUnit Jupiter Assertions
- 商业分发结论: 测试依赖不随 APK 分发；保留 Apache-2.0 说明

## kotlin-reflect
- 名称: Kotlin Reflect
- 版本: 2.1.21
- 许可证: Apache-2.0
- 用途: JVM 单元测试反射契约断言
- 数据流: 仅在本地测试进程检查编译类型与成员，不处理生产用户数据或网络数据
- NOTICE 位置: 开发与测试依赖，不打包进 release APK；本文件
- 替代方案: 不使用反射的手写契约断言
- 商业分发结论: 测试依赖不随 APK 分发；保留 Apache-2.0 许可证与 NOTICE

## plugin-android-application
- 名称: Android Gradle Plugin
- 版本: 8.12.2
- 许可证: Apache-2.0
- 用途: Android 应用构建、打包与测试任务
- 数据流: 仅在构建环境读取本地工程和依赖，不处理用户数据
- NOTICE 位置: 构建插件不打包进 release APK；本文件
- 替代方案: Android 命令行构建工具链
- 商业分发结论: 构建插件不随 APK 分发，可商业使用并保留 Apache-2.0 说明

## plugin-kotlin-android
- 名称: Kotlin Android Gradle Plugin
- 版本: 2.1.21
- 许可证: Apache-2.0
- 用途: Kotlin Android 源代码编译
- 数据流: 仅在构建环境编译本地源代码，不处理用户数据
- NOTICE 位置: 构建插件不打包进 release APK；本文件
- 替代方案: Java Android 编译
- 商业分发结论: 构建插件不随 APK 分发，可商业使用并保留 Apache-2.0 说明

## plugin-ksp
- 名称: Kotlin Symbol Processing Gradle Plugin
- 版本: 2.1.21-2.0.1
- 许可证: Apache-2.0
- 用途: 运行 Room 与 Hilt 注解处理器
- 数据流: 仅在构建环境读取本地源代码，不处理用户数据
- NOTICE 位置: 构建插件不打包进 release APK；本文件
- 替代方案: Kotlin kapt
- 商业分发结论: 构建插件不随 APK 分发，可商业使用并保留 Apache-2.0 说明

## plugin-compose-compiler
- 名称: Kotlin Compose Compiler Gradle Plugin
- 版本: 2.1.21
- 许可证: Apache-2.0
- 用途: 编译 Compose UI 源代码
- 数据流: 仅在构建环境编译本地源代码，不处理用户数据
- NOTICE 位置: 构建插件不打包进 release APK；本文件
- 替代方案: Android Views 编译路径
- 商业分发结论: 构建插件不随 APK 分发，可商业使用并保留 Apache-2.0 说明

## plugin-hilt
- 名称: Dagger Hilt Gradle Plugin
- 版本: 2.57.2
- 许可证: Apache-2.0
- 用途: 配置 Hilt Android 构建集成
- 数据流: 仅在构建环境读取本地工程配置，不处理用户数据
- NOTICE 位置: 构建插件不打包进 release APK；本文件
- 替代方案: 手写依赖注入构建配置
- 商业分发结论: 构建插件不随 APK 分发，可商业使用并保留 Apache-2.0 说明

## plugin-detekt
- 名称: detekt Gradle Plugin
- 版本: 1.23.8
- 许可证: Apache-2.0
- 用途: 静态代码分析质量门
- 数据流: 仅在构建环境分析本地 Kotlin 源代码，不收集或传输用户数据
- NOTICE 位置: 构建插件不打包进 release APK；本文件
- 替代方案: Android Lint
- 商业分发结论: 构建插件不随 APK 分发；可商业使用并保留 Apache-2.0 说明

## plugin-ktlint
- 名称: ktlint Gradle Plugin
- 版本: 12.1.2
- 许可证: MIT
- 用途: Kotlin 格式检查质量门
- 数据流: 仅在构建环境读取和检查本地 Kotlin 源代码，不收集或传输用户数据
- NOTICE 位置: 构建插件不打包进 release APK；本文件
- 替代方案: Kotlin formatter
- 商业分发结论: 构建插件不随 APK 分发；可商业使用并保留 MIT 许可证文本

## ngsl-nawl-1.2
- 名称: New General Service List / New Academic Word List（NGSL/NAWL）
- 版本: 1.2（候选来源标识；本任务不打包词条正文）
- 许可证: CC BY-SA 4.0；后续实际使用受署名与 ShareAlike 义务约束，并须逐份确认数据文件的许可证与署名文本
- 用途: 为应用内学习分组建立候选词汇来源标识；当前仅用于元数据来源治理
- 数据流: 不下载、不导入、不传输词条正文；APK 仅含六本学习分组元数据
- NOTICE 位置: `docs/third-party-notices.md`、`docs/decisions/2026-09-18-initial-wordbook-data-policy.md`；实际打包前还须在发行 NOTICE 保留署名与 CC BY-SA 4.0 文本
- 替代方案: 经独立许可核验、允许离线再分发并可完成完整台账的数据集，或暂不提供词条正文
- 商业分发结论: 本任务未分发 NGSL/NAWL 词条正文；未来分发仅可在保留署名并满足 CC BY-SA 4.0 ShareAlike 条件后进行

## cefr-j-1.5
- 名称: CEFR-J
- 版本: 1.5
- 许可证: 等级标注引用来源；后续使用须按来源要求正确引用，不能将其表述为中国官方考试大纲或词书
- 用途: 为应用内学习分组提供等级标注来源 ID
- 数据流: 不下载、不导入、不传输 CEFR-J 词条内容；APK 仅含来源 ID 和学习分组元数据
- NOTICE 位置: `docs/third-party-notices.md`、`docs/decisions/2026-09-18-initial-wordbook-data-policy.md`；实际引用时按来源要求加入引用说明
- 替代方案: 不显示 CEFR-J 等级标注，或使用经核验、可正确引用的其他等级框架
- 商业分发结论: 本任务不分发 CEFR-J 词条或官方内容；仅在正确引用的前提下将其作为等级标注来源

## datasets
- 名称: 词书、词典与其他数据集
- 版本: 仅登记 NGSL/NAWL 1.2 与 CEFR-J 1.5 来源标识；未引入词条正文
- 许可证: 见本文件 `ngsl-nawl-1.2` 与 `cefr-j-1.5` 条目；其他数据集未引入/待许可核验
- 用途: 当前仅保存六本应用内学习分组元数据，不使用、不下载、不打包任何词条正文或中文释义
- 数据流: 未引入/不处理/不传输词条、释义或其他数据集内容
- NOTICE 位置: 本文件、`docs/decisions/2026-09-18-initial-wordbook-data-policy.md`；未来实际打包时须在发行 NOTICE 完整登记
- 替代方案: 用户导入且经许可核验的数据，或不提供该资源
- 商业分发结论: 元数据可随应用分发；词条正文在完成逐项许可核验前禁止商业分发

## models
- 名称: OCR、TTS、AI 与其他模型
- 版本: 未引入
- 许可证: 未引入/待许可核验
- 用途: 本阶段不使用、不下载、不打包
- 数据流: 未引入/不处理/不传输模型或用户数据
- NOTICE 位置: 未打包；未来引入时须在本文件和发行 NOTICE 登记
- 替代方案: 系统能力或不提供对应功能
- 商业分发结论: 未引入/不打包，完成许可核验前禁止商业分发
