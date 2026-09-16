# 第三方依赖、数据集与模型台账

本台账是发布前许可检查的输入。未完整登记、未完成许可核验的依赖、数据集或模型不得进入正式发布包。

## Kotlin
- 名称: Kotlin
- 版本: 2.1.21
- 许可证: Apache-2.0
- 用途: Android 应用与构建逻辑编程语言
- 数据流: 不处理用户或网络数据；仅在构建时编译本地源代码
- NOTICE 位置: APK 的 META-INF/NOTICE 与本文件
- 替代方案: Java
- 商业分发结论: 可商业分发，保留 Apache-2.0 许可证与 NOTICE

## androidx-compose
- 名称: Jetpack Compose BOM 与 UI
- 版本: 2025.12.00
- 许可证: Apache-2.0
- 用途: 声明式本地 Android UI
- 数据流: 仅在设备内渲染应用状态；本阶段不发起网络请求、不收集用户数据
- NOTICE 位置: APK 的 META-INF/NOTICE 与本文件
- 替代方案: Android Views
- 商业分发结论: 可商业分发，保留 Apache-2.0 许可证与 NOTICE

## androidx-room
- 名称: Room
- 版本: 2.8.4
- 许可证: Apache-2.0
- 用途: 后续本地关系数据访问与 schema 生成
- 数据流: 本阶段不创建业务数据；后续仅处理应用私有存储中的本地数据，不传输网络数据
- NOTICE 位置: APK 的 META-INF/NOTICE 与本文件
- 替代方案: SQLiteDatabase
- 商业分发结论: 可商业分发，保留 Apache-2.0 许可证与 NOTICE

## hilt
- 名称: Dagger Hilt
- 版本: 2.57.2
- 许可证: Apache-2.0
- 用途: 后续依赖注入编译与运行时组装
- 数据流: 不处理、收集或传输用户数据
- NOTICE 位置: APK 的 META-INF/NOTICE 与本文件
- 替代方案: 手写构造函数注入
- 商业分发结论: 可商业分发，保留 Apache-2.0 许可证与 NOTICE

## androidx-security
- 名称: AndroidX Security Crypto
- 版本: 1.1.0
- 许可证: Apache-2.0
- 用途: 后续 Android Keystore 支撑的本地安全存储
- 数据流: 本阶段不保存密钥；后续仅处理设备内凭据密文，不上传网络
- NOTICE 位置: APK 的 META-INF/NOTICE 与本文件
- 替代方案: Android Keystore 平台 API
- 商业分发结论: 可商业分发，保留 Apache-2.0 许可证与 NOTICE

## junit
- 名称: JUnit Jupiter
- 版本: 5.12.2
- 许可证: EPL-2.0
- 用途: JVM 单元测试
- 数据流: 仅读取本地测试输入，不处理生产用户数据或网络数据
- NOTICE 位置: 开发与测试依赖，不打包进 release APK；本文件
- 替代方案: kotlin.test
- 商业分发结论: 测试依赖不随 APK 分发；源码/测试分发时保留 EPL-2.0 说明

## androidx-test
- 名称: AndroidX Test Core
- 版本: 1.7.0
- 许可证: Apache-2.0
- 用途: Android 测试运行与上下文支持
- 数据流: 仅在测试设备内读取测试状态，不上传数据
- NOTICE 位置: 开发与测试依赖，不打包进 release APK；本文件
- 替代方案: Android instrumentation API
- 商业分发结论: 测试依赖不随 APK 分发；保留 Apache-2.0 说明

## robolectric
- 名称: Robolectric
- 版本: 4.16
- 许可证: MIT
- 用途: 本地 JVM Android 行为测试
- 数据流: 仅运行本地测试；不处理生产用户数据或网络数据
- NOTICE 位置: 开发与测试依赖，不打包进 release APK；本文件
- 替代方案: Android instrumented tests
- 商业分发结论: 测试依赖不随 APK 分发；保留 MIT 许可证文本

## mockk
- 名称: MockK
- 版本: 1.14.5
- 许可证: Apache-2.0
- 用途: 后续 JVM 测试替身
- 数据流: 仅处理本地测试数据，不传输网络数据
- NOTICE 位置: 开发与测试依赖，不打包进 release APK；本文件
- 替代方案: 手写 fake 或 Mockito
- 商业分发结论: 测试依赖不随 APK 分发；保留 Apache-2.0 说明

## turbine
- 名称: Turbine
- 版本: 1.2.1
- 许可证: Apache-2.0
- 用途: 后续 Kotlin Flow 测试
- 数据流: 仅处理本地测试流数据，不传输网络数据
- NOTICE 位置: 开发与测试依赖，不打包进 release APK；本文件
- 替代方案: kotlinx-coroutines-test
- 商业分发结论: 测试依赖不随 APK 分发；保留 Apache-2.0 说明

## detekt
- 名称: detekt
- 版本: 未引入（计划质量工具）
- 许可证: Apache-2.0
- 用途: 静态代码分析；本任务仅登记，尚未启用插件
- 数据流: 未引入/不运行；启用后只分析本地源代码，不上传数据
- NOTICE 位置: 未打包；本文件
- 替代方案: Android Lint
- 商业分发结论: 未引入/不打包；启用时可商业分发并保留 Apache-2.0 说明

## ktlint
- 名称: ktlint
- 版本: 未引入（计划质量工具）
- 许可证: MIT
- 用途: Kotlin 格式检查；本任务仅登记，尚未启用插件
- 数据流: 未引入/不运行；启用后只分析本地源代码，不上传数据
- NOTICE 位置: 未打包；本文件
- 替代方案: Kotlin formatter
- 商业分发结论: 未引入/不打包；启用时可商业分发并保留 MIT 许可证文本

## datasets
- 名称: 词书、词典与其他数据集
- 版本: 未引入
- 许可证: 未引入/待许可核验
- 用途: 本阶段不使用、不下载、不打包
- 数据流: 未引入/不处理/不传输用户或数据集内容
- NOTICE 位置: 未打包；未来引入时须在本文件和发行 NOTICE 登记
- 替代方案: 用户导入且经许可核验的数据，或不提供该资源
- 商业分发结论: 未引入/不打包，完成许可核验前禁止商业分发

## models
- 名称: OCR、TTS、AI 与其他模型
- 版本: 未引入
- 许可证: 未引入/待许可核验
- 用途: 本阶段不使用、不下载、不打包
- 数据流: 未引入/不处理/不传输模型或用户数据
- NOTICE 位置: 未打包；未来引入时须在本文件和发行 NOTICE 登记
- 替代方案: 系统能力或不提供对应功能
- 商业分发结论: 未引入/不打包，完成许可核验前禁止商业分发
