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

## androidx-compose-ui-test-junit4
- 名称: Jetpack Compose UI Test JUnit4
- 版本: 由 androidx-compose-bom 2025.12.00 管理
- 许可证: Apache-2.0
- 用途: Android instrumentation UI 测试断言与规则
- 数据流: 仅在测试设备内执行 UI 验证，不处理用户数据或网络数据
- NOTICE 位置: Android test/debug-only 依赖，不打包进 release APK；本文件
- 替代方案: Espresso 或手写 Android UI 测试
- 商业分发结论: 测试依赖不随 release APK 分发；保留 Apache-2.0 许可证与 NOTICE

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
- 用途: 解析导入的词书包 `book.json` 与 `manifest.json`；并对齐 Room migration test 运行时所需的 Kotlin serialization API
- 数据流: 仅在设备内解析用户主动导入的词书包文件与 Room schema 测试元数据，不传输用户数据
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

## plugin-kotlin-serialization
- 名称: Kotlin Serialization Gradle Plugin
- 版本: 2.1.21
- 许可证: Apache-2.0
- 用途: 为词书包 `book.json`、`manifest.json` 的 `@Serializable` DTO 生成序列化器
- 数据流: 仅在构建环境编译本地源代码，不处理用户数据
- NOTICE 位置: 构建插件不打包进 release APK；本文件
- 替代方案: 手写 JSON 解析（org.json）
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

## androidx-core-splashscreen
- 名称: AndroidX Core SplashScreen
- 版本: 1.0.1
- 许可证: Apache-2.0
- 用途: 启动阶段显示系统 SplashScreen，避免冷启动白屏
- 数据流: 仅在设备内绘制启动画面，不采集或传输用户数据
- NOTICE 位置: APK 的 META-INF/NOTICE 与本文件
- 替代方案: 自定义启动主题与窗口背景
- 商业分发结论: 可商业分发，保留 Apache-2.0 许可证与 NOTICE

## androidx-lifecycle-viewmodel
- 名称: AndroidX Lifecycle ViewModel KTX
- 版本: 2.9.0
- 许可证: Apache-2.0
- 用途: Compose 界面的 ViewModel 与生命周期作用域
- 数据流: 仅在设备内保存界面状态，不传输用户数据
- NOTICE 位置: APK 的 META-INF/NOTICE 与本文件
- 替代方案: Android 平台 ViewModelStore 手写封装
- 商业分发结论: 可商业分发，保留 Apache-2.0 许可证与 NOTICE

## androidx-hilt-navigation-compose
- 名称: AndroidX Hilt Navigation Compose
- 版本: 1.3.0
- 许可证: Apache-2.0
- 用途: 在 Compose 界面中注入 Hilt ViewModel
- 数据流: 仅在设备内完成依赖注入，不传输用户数据
- NOTICE 位置: APK 的 META-INF/NOTICE 与本文件
- 替代方案: 手动构造 ViewModel 工厂
- 商业分发结论: 可商业分发，保留 Apache-2.0 许可证与 NOTICE

## androidx-compose-ui-test-manifest
- 名称: Jetpack Compose UI Test Manifest
- 版本: 由 androidx-compose-bom 2025.12.00 管理
- 许可证: Apache-2.0
- 用途: 为 Compose 测试提供空的测试 Activity 清单
- 数据流: 仅在测试设备内提供测试宿主 Activity，不处理用户数据或网络数据
- NOTICE 位置: Android test/debug-only 依赖，不打包进 release APK；本文件
- 替代方案: 手写测试 Activity 与清单条目
- 商业分发结论: 测试依赖不随 release APK 分发；保留 Apache-2.0 许可证与 NOTICE

## androidx-room-testing
- 名称: Room Testing
- 版本: 2.8.4
- 许可证: Apache-2.0
- 用途: 在 JVM 与真机测试中构建内存 Room 数据库
- 数据流: 仅在测试进程内创建内存数据库，不读取真实用户数据、不联网
- NOTICE 位置: Android test/debug-only 依赖，不打包进 release APK；本文件
- 替代方案: 直接使用 SQLite 内存数据库做夹具
- 商业分发结论: 测试依赖不随 release APK 分发；保留 Apache-2.0 许可证与 NOTICE

## app-authored-placeholder-word-cards
- 名称: 应用自撰占位词卡内容（F1-03 词卡与反馈）
- 版本: v1 占位（实现位于 `app/src/main/java/com/example/englishlearning/learning/PlaceholderWordCardSource.kt`）
- 许可证: 无第三方许可依赖。内容由本项目自行撰写（一小组基础词条，含单词、IPA、词性、中文释义与例句），不复制、不派生于 NGSL/NAWL、CEFR-J 或任何其他第三方词书、词典或数据集，因此不触发 CC BY-SA 4.0 或其他第三方许可义务
- 用途: 在经许可核验的词条数据到位之前，为词卡展示、三档反馈与排程记录提供可运行、可真机验证的占位内容
- 数据流: 仅随 APK 打包并在设备内读取；不下载、不上传、不联网、不参与任何 AI 或网络请求
- NOTICE 位置: 本文件、`docs/decisions/2026-09-19-placeholder-word-card-content.md`
- 替代方案: 经许可核验且允许离线再分发的词条数据集，或「仅在线查词、不打包词条正文」的实现
- 商业分发结论: **发布前必须替换**。占位内容不属于正式词书，不得作为词书内容对外宣称；替换为真实数据集时须同步更新本条目与 `app/src/main/assets/wordbooks/metadata.json` 的 `totalWords`

## app-authored-card-illustrations
- 名称: 应用自研词卡说明图（F1-06 彩色卡通插画，12 张）
- 版本: v1（资源位于 `app/src/main/res/drawable-nodpi/illus_<lemma>.webp`；生成与处理台账见 `docs/design/card-illustration-prompts.md`）
- 许可证: 无第三方素材许可依赖。图片由本项目通过 AI 图像生成能力按自有提示词产出，不含、不派生自任何第三方素材库、图库或数据集作品，不复制任何第三方图标、插画或摄影作品，因此不触发 CC BY-SA 4.0 或其他第三方素材许可义务
- 用途: 为占位词条的词卡详情页提供彩色卡通说明图，以图示辅助理解词义（解释的补充而非替代）
- 数据流: 仅随 APK 打包并在设备内渲染；不下载、不上传、不联网、不参与任何 AI 或网络请求
- NOTICE 位置: 本文件、`docs/decisions/2026-09-22-card-illustration-assets.md`、`docs/design/card-illustration-prompts.md`
- 替代方案: 经许可核验的第三方插画素材（须逐张核验归属与署名），或继续使用应用自绘矢量说明图
- 商业分发结论: 可随应用分发。**发布前须复核**所用 AI 图像生成服务自身的使用条款（尤其商用与再分发条款）；这 12 张仅服务占位词条，真实词书配图的许可须单独闭合后另行登记

## voa-learning-english
- 名称: VOA Learning English（美国之音学习英语）文章正文
- 版本: 未固定版本；按 F2-06 白名单在运行期抓取当期文章，来源站点 `learningenglish.voanews.com`
- 许可证: 公有领域（美国法下；依据美国版权法对政府作品的规定及 VOA 官方内容使用说明）。VOA 官方允许为教育目的与商业目的转载，条件为署名 `learningenglish.voanews.com`
- 用途: 为「外刊抓取」来源提供英语文章正文与标题，作为 AI 生成之外的第二条文章来源。仅取文本，不取图片、音频与视频
- 数据流: 运行期按用户主动触发的抓取请求从 `learningenglish.voanews.com` 下载文章标题与正文；结果保存在应用私有存储中供离线重读。不上传任何用户数据，不发送设备标识，不携带用户自配的 AI Key，不使用任何 Cookie 或登录态
- NOTICE 位置: `docs/third-party-notices.md`、`docs/decisions/2026-09-24-article-source-licensing.md`；应用内阅读页与历史列表展示署名 `learningenglish.voanews.com` 与原文链接文本
- 替代方案: 仅保留 AI 生成与用户粘贴导入两条来源；或替换为其他已完成逐源许可核验的外刊来源
- 商业分发结论: **有条件可商业分发，且发布前必须复核**。条件为保留署名 `learningenglish.voanews.com` 与原文链接文本，且不转载 VOA 所使用的 AP / Reuters 图片与视频。**待闭合项**：VOA 内容在美国法下属公有领域，但其在中国法下的保护状态尚未取得专业法律判断；在完成该判断前，抓取正文不得用于应用商标、宣传或付费墙内容。未通过核验前，抓取路径不得合并进发布构建

## ngsl-nawl-1.2
- 名称: New General Service List / New Academic Word List（NGSL/NAWL）
- 版本: 1.2（候选来源标识；本任务不打包词条正文）
- 许可证: CC BY-SA 4.0；后续实际使用受署名与 ShareAlike 义务约束，并须逐份确认数据文件的许可证与署名文本
- 用途: 为应用内学习分组建立候选词汇来源标识；当前仅用于元数据来源治理
- 数据流: 不下载、不导入、不传输词条正文；APK 仅含六本学习分组元数据
- NOTICE 位置: `docs/third-party-notices.md`、`docs/decisions/2026-09-18-initial-wordbook-data-policy.md`；实际打包前还须在发行 NOTICE 保留署名与 CC BY-SA 4.0 文本
- 替代方案: 经独立许可核验、允许离线再分发并可完成完整台账的数据集，或暂不提供词条正文
- 商业分发结论: 本任务未分发 NGSL/NAWL 词条正文；未来分发仅可在保留署名并满足 CC BY-SA 4.0 ShareAlike 条件后进行

## ngsl-core-10-acceptance-package
- 名称: NGSL 目标 10 验收词书包（`outputs/word-book-packing/ngsl-core-10`）
- 版本: 仅用于本地导入链路验收，不是正式内置词书
- 许可证: 验收包中的词条与中文释义/例句为测试夹具；配图标记为 `ai-generated`、模型 `m-test`，未完成商业再分发许可核验
- 用途: 验证 wbpack 解析、图片哈希校验、导入登记、真实词卡读取、搜索与详情展示
- 数据流: 仅保存在本地 `outputs/` 验收目录，不进入 APK，不进入正式发布包，不作为默认词书
- NOTICE 位置: 本条目、`outputs/word-book-packing/ngsl-core-10/ngsl-core-10/book.json`、`manifest.json`
- 替代方案: 使用项目自撰且明确许可的最小测试夹具；正式词书须逐项核验来源与再分发许可后另立条目
- 商业分发结论: **禁止进入发布构建**。完成词条来源、中文释义/例句、图片许可和署名逐项核验后，必须创建新的正式版本条目，不能直接把验收包升级为发布资源

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
- 版本: 仅登记 NGSL/NAWL 1.2 与 CEFR-J 1.5 来源标识；未引入第三方词条正文
- 许可证: 见本文件 `ngsl-nawl-1.2` 与 `cefr-j-1.5` 条目；其他数据集未引入/待许可核验
- 用途: 保存六本应用内学习分组元数据；F1-03 起另有应用自撰的占位词卡内容（见 `app-authored-placeholder-word-cards`），仍不使用、不下载、不打包任何第三方词条正文或第三方中文释义
- 数据流: 未引入/不处理/不传输第三方词条、释义或其他数据集内容
- NOTICE 位置: 本文件、`docs/decisions/2026-09-18-initial-wordbook-data-policy.md`、`docs/decisions/2026-09-19-placeholder-word-card-content.md`；未来实际打包时须在发行 NOTICE 完整登记
- 替代方案: 用户导入且经许可核验的数据，或不提供该资源
- 商业分发结论: 元数据与应用自撰占位内容可随应用分发；第三方词条正文在完成逐项许可核验前禁止商业分发

## onnxruntime-android
- 名称: Microsoft ONNX Runtime Android
- 版本: com.microsoft.onnxruntime:onnxruntime-android:1.29.0；AAR 52,897,836 bytes；SHA-256 e97540ca78fe36f6fe2013f82843414fb843b6c7681fb04644cba5e1406662dd
- 许可证: MIT，已核验固定 AAR/POM；保留 AAR 所含第三方说明
- 用途: Kokoro Android Spike 的 CPU 推理，仅 androidTestImplementation，不进入正式 debug/release APK
- 数据流: 仅在测试设备内读取固定本地模型和测试单词；不读 Room、密钥，不联网
- NOTICE 位置: 本条目、app/src/androidTest/assets/kokoro-notices/ORT-LICENSE.txt 与 ORT-ThirdPartyNotices.txt、outputs/kokoro-spike-20261006/REPORT.md；AAR 的 classes.jar 仅有 LICENSE-1DS，不代替 ORT 完整许可；测试分发随包保留全部原文
- 替代方案: Android 系统 TTS；放弃本地模型 Spike
- 商业分发结论: MIT 允许商业使用；当前仅允许隔离测试，不授权正式产品接入；正式分发前复核最终包 NOTICE
- 来源: https://repo.maven.apache.org/maven2/com/microsoft/onnxruntime/onnxruntime-android/1.29.0/；外部固定文件 D:/kokoro-spike-assets/onnxruntime-1.29.0/

## kokoro-community-int8
- 名称: Kokoro-82M v1.0 社区 INT8 ONNX 转换
- 版本: thewh1teagle/kokoro-onnx release model-files-v1.0；kokoro-v1.0.int8.onnx 92,361,271 bytes；SHA-256 6e742170d309016e5891a994e1ce1559c702a2ccd0075e67ef7157974f6406cb
- 许可证: Kokoro 模型 Apache-2.0；社区转换/打包代码 MIT；代码许可不替代上游模型许可
- 用途: 测试专用单词音频合成；不是 hexgrad 官方 Android 导出，不进入 main/assets 或正式 APK
- 数据流: 本地固定测试模型接收测试音素、voice style 和速度，输出临时 PCM；不读数据库、密钥，不联网
- NOTICE 位置: 本条目、outputs/kokoro-spike-20261006/REPORT.md、.superpowers/sdd/2026-10-06-kokoro-word-pronunciation-spike/research-report.md；测试包须保留模型 Apache-2.0 与社区 MIT 来源说明
- 替代方案: Android 系统 TTS；经独立许可核验的其他离线模型
- 商业分发结论: 上游 Apache-2.0 和社区 MIT 允许商业使用，须保留版权、许可证、来源及修改说明；本批只授权外部/测试资产，正式分发 NOTICE 与接入评审仍待完成
- 来源: https://huggingface.co/hexgrad/Kokoro-82M；https://github.com/thewh1teagle/kokoro-onnx/releases/tag/model-files-v1.0

## kokoro-community-voices
- 名称: Kokoro v1.0 社区 NPZ voice pack（本次 af_heart / bf_emma）
- 版本: model-files-v1.0/voices-v1.0.bin；28,214,398 bytes；SHA-256 bca610b8308e8d99f32e6fe4197e7ec01679264efed0cac9140fe9c29f1fbf7d
- 许可证: Kokoro 上游模型/声音资产 Apache-2.0；社区打包代码 MIT；不将声音身份或品牌作为授权宣传
- 用途: 测试专用英美口音 style；NPZ float32[510,1,256]，style 使用 tokenCount 行
- 数据流: 仅本地读取固定 voice 参数，不录音、不读取用户数据、不联网
- NOTICE 位置: 本条目、上游 VOICES.md、outputs/kokoro-spike-20261006/REPORT.md；测试分发须附模型许可、社区许可和 voice 来源说明
- 替代方案: Android 系统 TTS 英美英语 voice
- 商业分发结论: 保留上游 Apache-2.0 与社区 MIT 条件；当前只允许测试，正式分发需逐 voice 复核随包来源和 NOTICE，不承诺音质或真人身份权利
- 来源: https://huggingface.co/hexgrad/Kokoro-82M/blob/main/VOICES.md；https://github.com/thewh1teagle/kokoro-onnx/releases/tag/model-files-v1.0

## phonemis-english-frontends
- 名称: Phonemis v2.1.0 英语 neural frontend（en-us / en-gb）与 dense 116 labels
- 版本: Shusek00/kokoro-kmp-models@8026061b7671cccf9efb65498e95a0f2e09508e5；上游 IgorSwat/Phonemis@71eb1ce33bd586d38cbac037843b8539d7829c3b；两个 frontend 各 7,105,527 bytes；US SHA-256 479cd9d89819a60c4f8609dbb116ae5b362d46c0c592b97605968cbaef1e7bc6；GB SHA-256 065baba624db8391905e53c78706a12c5f4cba12a77e62a90900b2113bdb17e7
- 许可证: Phonemis MIT；已核验 LICENSE 1,065 bytes，SHA-256 f09f9295f76d14fdac54f708716e3d30c712cecb7f7a26a2b23284e56a88231e；不引入 eSpeak/GPL、Misaki 或自动 tagger
- 用途: 测试专用 1–20 ASCII 字母及内部 apostrophe 的单词转音素；en-us/en-gb 分别加载，CTC argmax→collapse→移除 blank
- 数据流: 本地测试单词进入 input_ids，读取 logits；不联网、不读取 Room、Profile 或 Key
- NOTICE 位置: 本条目、D:/kokoro-spike-assets/phonemis-audit/Phonemis-LICENSE.txt、固定 revision THIRD_PARTY_NOTICES.md；测试包附完整 MIT 版权与许可文本
- 替代方案: 固定许可词典命中测试；系统 TTS
- 商业分发结论: MIT 允许商业使用且必须保留版权/许可文本；本批仅外部/测试资产，正式接入须另行评审；转换来源必须保留而不能冒称上游官方 Android 包
- 来源: https://huggingface.co/Shusek00/kokoro-kmp-models/tree/8026061b7671cccf9efb65498e95a0f2e09508e5；转换来源 ShinudaCommunity/kokoro-narrator-suvio-plugin@d16c577a516feac9ce0459bd615fd3a847a4334e

## kokoro-spike-vocabulary
- 名称: 固定 Kokoro v1.0 sparse vocabulary 配置
- 版本: runtime/kokoro-v1.0-config.json，来自 Shusek00/kokoro-kmp-models@8026061b7671cccf9efb65498e95a0f2e09508e5；2,351 bytes；SHA-256 5abb01e2403b072bf03d04fde160443e209d7a0dad49a423be15196b9b43c17f
- 许可证: Kokoro 上游配置 Apache-2.0；固定包第三方说明保留模型许可；不包含未核验的词典/tagger 资产
- 用途: 测试专用音素到 Kokoro sparse token 的转换，BOS/EOS=0；与 Phonemis dense label ID 分离
- 数据流: 本地固定 JSON 解析，不读取用户数据，不联网
- NOTICE 位置: 本条目、固定 revision THIRD_PARTY_NOTICES.md、outputs/kokoro-spike-20261006/REPORT.md；测试包附 Apache-2.0 和来源说明
- 替代方案: 系统 TTS；停止模型测试
- 商业分发结论: 当前仅隔离测试；正式分发须保留模型 Apache-2.0、配置来源和完整随包 NOTICE 后另行批准
- 来源: https://huggingface.co/Shusek00/kokoro-kmp-models/tree/8026061b7671cccf9efb65498e95a0f2e09508e5/runtime

## models
- 名称: OCR、TTS、AI 与其他模型的正式产品资源
- 版本: OCR 已批准引入（ML Kit bundled 随 APK、eng.traineddata 按需下载，见 mlkit-text-recognition 与 eng-traineddata-tessdata-fast 条目）；TTS 正式模型待阶段 3 F3-02 许可闭合；Kokoro Android Spike 测试资源按上方独立固定条目登记
- 许可证: OCR 条目已逐项核验；其余待逐项核验；测试资源许可见 Kokoro/Phonemis 条目
- 用途: 不将 Spike 模型打包进正式 debug/release APK；测试 APK 单独隔离
- 数据流: 正式产品不处理此类本地模型；Spike 仅本地固定测试数据，不涉及用户数据库
- NOTICE 位置: 正式产品未打包；Spike 测试通知见 app/src/androidTest/assets/kokoro-notices
- 替代方案: 系统能力或不提供对应功能
- 商业分发结论: 未引入/不打包，完成许可核验前禁止商业分发

## androidx-work-runtime-ktx
- 名称: AndroidX WorkManager (work-runtime-ktx 及传递依赖 work-runtime/startup-runtime/lifecycle-service/tracing-ktx/concurrent-futures-ktx)
- 版本: 2.10.0（传递依赖版本以 gradle.lockfile 为准）
- 许可证: Apache-2.0
- 用途: F4-02 本地提醒的非精确调度（唯一 WorkRequest replace 语义保证"取消旧任务并重排唯一一次"）；设备重启后自动恢复持久化任务
- 数据流: 本地任务调度与通知触发；不联网、不读取用户数据；通知文案为固定字符串，不含学习数据
- NOTICE 位置: 本条目；随包 Apache-2.0 许可文本由 AAR 内嵌 NOTICE 覆盖
- 替代方案: AlarmManager（非精确 setAndAllowWhileIdle 亦可，但 WorkManager 提供重启恢复与约束）；不提供提醒
- 商业分发结论: Apache-2.0，允许商业分发

## tink
- 名称: Google Tink Java (com.google.crypto.tink:tink 及传递依赖 gson/protobuf-javalite 等，以 gradle.lockfile 为准)
- 版本: 1.21.0
- 许可证: Apache-2.0
- 用途: F4-04 备份包可选加密的 AES-256-GCM AEAD 原语（docs/decisions/2026-10-13-f4-04-crypto-and-pairing-design.md C4）；仅在设备内加解密用户自选导出的备份字节，不发起任何网络请求
- 数据流: 本地内存中处理用户备份数据；密钥仅来自本地 Argon2id 派生与随机 DEK，不经网络
- NOTICE 位置: APK 的 META-INF/NOTICE 与本文件
- 替代方案: 平台 JCE AES/GCM 直用（误用面更大）、Hans Provider（KDF 面缺失）、XChaCha20-Poly1305 无 Tink Java 实现
- 商业分发结论: 可商业分发，保留 Apache-2.0 许可证与 NOTICE

## bouncy-castle-bcprov
- 名称: Bouncy Castle bcprov-jdk18on (org.bouncycastle:bcprov-jdk18on)
- 版本: 1.81
- 许可证: BC 许可证（MIT/X11 类宽松许可，LICENSE 文本随库分发）
- 用途: F4-04 Argon2id 内存硬 KDF（lightweight API Argon2BytesGenerator，不注册为 JCE Provider）（决策文档 C3）；仅本地密码派生，不联网
- 数据流: 仅在设备内存处理用户备份密码派生输入（密码 CharArray 即用即清），不传输任何数据
- NOTICE 位置: APK 的 META-INF/NOTICE 与本文件
- 替代方案: scrypt（BC 同库提供，决策文档记为未来算法串候选）、Android Keystore2 Argon2（仅 API 31+，违反 minSdk 26）
- 商业分发结论: 可商业分发，保留 BC 许可文本

## androidx-biometric
- 名称: AndroidX Biometric (androidx.biometric:biometric)
- 版本: 1.1.0
- 许可证: Apache-2.0
- 用途: F4-04 旧设备配对签发与导出前的用户认证（BIOMETRIC_STRONG/DEVICE_CREDENTIAL 兼容矩阵，docs/decisions/2026-10-13-f4-04-crypto-and-pairing-design.md §3.3/D1）；仅触发系统认证对话框，不处理学习数据
- 数据流: 本地系统认证对话框；认证结果仅用于门控本地 Keystore 签名，不传输网络
- NOTICE 位置: 本文件
- 替代方案: 直接使用 android.hardware.biometrics 平台 API（兼容矩阵需自维护）
- 商业分发结论: 可商业分发，保留 Apache-2.0 许可证与 NOTICE

## androidx-camera-core
- 名称: AndroidX CameraX (androidx.camera:camera-core/camera-camera2/camera-lifecycle/camera-view 及传递依赖 camera-video 等，以 gradle.lockfile 为准)
- 版本: 1.6.1
- 许可证: Apache-2.0
- 用途: F4-04 新设备实时扫码取景（无 GMS 取景方案，docs/decisions/2026-10-13-f4-04-crypto-and-pairing-design.md §7/D2）；仅取景解码，不拍摄存储任何照片
- 数据流: 相机帧仅在本机内存中交由本地二维码解码；不保存、不传输
- NOTICE 位置: 本文件
- 替代方案: ML Kit Barcode/Google Code Scanner（引入 GMS 依赖，与本项目"无 GMS 降级"方向冲突）
- 商业分发结论: 可商业分发，保留 Apache-2.0 许可证与 NOTICE

## zxing
- 名称: ZXing Core (com.google.zxing:core)
- 版本: 3.5.3
- 许可证: Apache-2.0
- 用途: F4-04 配对二维码本地解码（维护模式为已知妥协，理由见决策文档 §7/D1）；仅在本机内存中解码相机帧内的配对码
- 数据流: 本地内存解码；不保存、不传输
- NOTICE 位置: 本文件
- 替代方案: ML Kit Barcode（GMS 依赖）、自研解码器（不可靠）
- 商业分发结论: 可商业分发，保留 Apache-2.0 许可证与 NOTICE

## androidx-camera-camera2
- 名称: AndroidX CameraX camera2 后端 (androidx.camera:camera-camera2)
- 版本: 1.6.1
- 许可证: Apache-2.0
- 用途: F4-04 实时扫码取景的 CameraX camera2 后端实现（同 androidx-camera-core 条目）
- 数据流: 本地相机帧，不保存、不传输
- NOTICE 位置: 本文件
- 替代方案: 无独立替代（CameraX 必需后端）
- 商业分发结论: 可商业分发，保留 Apache-2.0 许可证与 NOTICE

## androidx-camera-lifecycle
- 名称: AndroidX CameraX lifecycle 绑定 (androidx.camera:camera-lifecycle)
- 版本: 1.6.1
- 许可证: Apache-2.0
- 用途: F4-04 实时扫码取景的生命周期绑定（同 androidx-camera-core 条目）
- 数据流: 本地相机帧，不保存、不传输
- NOTICE 位置: 本文件
- 替代方案: 无独立替代（CameraX 必需组件）
- 商业分发结论: 可商业分发，保留 Apache-2.0 许可证与 NOTICE

## androidx-camera-view
- 名称: AndroidX CameraX PreviewView (androidx.camera:camera-view)
- 版本: 1.6.1
- 许可证: Apache-2.0
- 用途: F4-04 扫码取景控件（同 androidx-camera-core 条目）
- 数据流: 本地相机帧，不保存、不传输
- NOTICE 位置: 本文件
- 替代方案: 自绘 TextureView 绑定（维护成本高）
- 商业分发结论: 可商业分发，保留 Apache-2.0 许可证与 NOTICE

## zxing-core
- 名称: ZXing Core (com.google.zxing:core)
- 版本: 3.5.3
- 许可证: Apache-2.0
- 用途: F4-04 配对二维码本地解码（同 zxing 条目）
- 数据流: 本地内存解码；不保存、不传输
- NOTICE 位置: 本文件
- 替代方案: ML Kit Barcode（GMS 依赖）
- 商业分发结论: 可商业分发，保留 Apache-2.0 许可证与 NOTICE

## mlkit-text-recognition
- 名称: ML Kit Text Recognition v2 (com.google.mlkit:text-recognition)
- 版本: 16.0.1（bundled 变体；传递链以 gradle.lockfile 为准）
- 许可证: ML Kit Terms of Service（POM 声明 https://developers.google.com/ml-kit/terms，非开源许可；核验见 docs/decisions/2026-10-13-f3-03-ocr-provider-licenses.md）
- 用途: F3-03 拍照 OCR 主路径，本地文字识别（拉丁语系）；模型随 APK 静态打包
- 数据流: 图像识别全程在本机推理，输入图像与识别文本不发送 Google 服务器（ToS 保证）；但库可能联系 Google 服务器获取更新并发送性能 metrics（不含用户内容）——须在隐私政策与应用内披露
- NOTICE 位置: 本文件、docs/decisions/2026-10-13-f3-03-ocr-provider-licenses.md；应用隐私政策含 ML Kit metrics 披露
- 替代方案: Tesseract4Android 单引擎（决策文档方案 B）
- 商业分发结论: 可商业分发（bundled 模型即设计为打进 APK）；禁止逆向工程或提取模型；须履行遥测披露义务；真机抓包核验外联（决策文档 T2）完成前不得标记已验证

## mlkit-text-recognition-bundled-common
- 名称: ML Kit Text Recognition bundled common (com.google.mlkit:text-recognition-bundled-common)
- 版本: 17.0.0（由 mlkit-text-recognition 传递引入）
- 许可证: ML Kit Terms of Service（同 mlkit-text-recognition 条目）
- 用途: bundled 识别的共享层与端侧模型实现
- 数据流: 同 mlkit-text-recognition 条目
- NOTICE 位置: 本文件
- 替代方案: 同 mlkit-text-recognition 条目
- 商业分发结论: 同 mlkit-text-recognition 条目

## mlkit-common
- 名称: ML Kit Common (com.google.mlkit:common)
- 版本: 18.11.0（传递依赖，以 gradle.lockfile 为准）
- 许可证: ML Kit Terms of Service
- 用途: ML Kit API 公共运行时
- 数据流: 同 mlkit-text-recognition 条目
- NOTICE 位置: 本文件
- 替代方案: 无独立替代（ML Kit 必需）
- 商业分发结论: 同 mlkit-text-recognition 条目

## mlkit-vision-common
- 名称: ML Kit Vision Common (com.google.mlkit:vision-common)
- 版本: 17.3.0（传递依赖，以 gradle.lockfile 为准）
- 许可证: ML Kit Terms of Service
- 用途: ML Kit Vision API 公共层
- 数据流: 同 mlkit-text-recognition 条目
- NOTICE 位置: 本文件
- 替代方案: 不可独立替换
- 商业分发结论: 同 mlkit-text-recognition 条目

## mlkit-vision-interfaces
- 名称: ML Kit Vision Interfaces (com.google.mlkit:vision-interfaces)
- 版本: 16.3.0（传递依赖，2026-10-13 由 gradle.lockfile 锁定核验后补登记）
- 许可证: ML Kit Terms of Service
- 用途: ML Kit Vision 输入输出接口定义层
- 数据流: 同 mlkit-text-recognition 条目
- NOTICE 位置: 本文件
- 替代方案: 不可独立替换
- 替代方案: 无独立替代（ML Kit 必需）
- 商业分发结论: 同 mlkit-text-recognition 条目

## play-services-mlkit-text-recognition
- 名称: Play Services ML Kit Text Recognition (com.google.android.gms:play-services-mlkit-text-recognition 及 -common)
- 版本: 19.0.1 / common 19.1.0（由 mlkit-text-recognition 传递引入的客户端 stub，非动态模型路径）
- 许可证: ML Kit Terms of Service
- 用途: bundled artifact 的传递依赖；运行时设备无 GMS 时不作为识别通路（模型在 APK 内），真机核验见决策文档 T2
- 数据流: 同 mlkit-text-recognition 条目
- NOTICE 位置: 本文件
- 替代方案: 无法从 bundled artifact 排除而不偏离官方装配；整体替代为决策文档方案 B
- 商业分发结论: 同 mlkit-text-recognition 条目

## play-services-base
- 名称: Google Play Services Base/Basement/Tasks (com.google.android.gms:play-services-base/play-services-basement/play-services-tasks)
- 版本: 18.5.0 / 18.4.0 / 随链（以 gradle.lockfile 为准）
- 许可证: Android Software Development Kit License（POM 声明 https://developer.android.com/studio/terms.html）
- 用途: ML Kit 传递依赖的任务与连接基座
- 数据流: 本机内存任务编排；不主动收集用户数据
- NOTICE 位置: 本文件
- 替代方案: 无独立替代（ML Kit 必需）
- 商业分发结论: 可商业分发（Android SDK 条款允许集成进应用分发）

## tesseract4android
- 名称: Tesseract4Android (cz.adaptech.tesseract4android:tesseract4android)
- 版本: 4.9.0（JitPack 分发；内嵌 Tesseract 5.5.1、Leptonica 1.85.0、libjpeg v9f、libpng 1.6.48）
- 许可证: Apache-2.0（核验见 docs/decisions/2026-10-13-f3-03-ocr-provider-licenses.md §2）
- 用途: F3-03 无 GMS/ML Kit 不可用时的本地 OCR 降级引擎
- 数据流: 本机内存识别；图像与文本不上传；traineddata 由应用按需下载（见 eng-traineddata-tessdata-fast 条目）
- NOTICE 位置: APK 的 META-INF/NOTICE 与本文件；发布时随包保留 Apache-2.0 与内嵌组件许可文本
- 替代方案: 自封装 JNI（维护成本高，决策文档记为后备）；tess-two 已归档不采用
- 商业分发结论: 可商业分发，保留 Apache-2.0 许可证与 NOTICE；锁定版本并校验产物哈希（仅 JitPack 分发的供应链风险已在决策文档 T3 记录）

## eng-traineddata-tessdata-fast
- 名称: Tesseract 英文训练数据 eng.traineddata（tessdata_fast）
- 版本: tesseract-ocr/tessdata_fast main 分支文件，4,113,088 bytes；实现期按决策文档锁定 commit 与 SHA-256
- 许可证: Apache-2.0（仓库 LICENSE 与 README；无 CC 条款、无 ShareAlike、无非商业标注）
- 用途: Tesseract4Android 降级引擎的英文识别模型；首次使用按需下载，不打进 APK，可删除
- 数据流: 从固定 URL 下载至应用私有目录；下载不携带用户数据；识别在本机进行
- NOTICE 位置: 应用内下载页展示许可名称与本文件；随包（下载缓存）保留许可文本
- 替代方案: tessdata_best float 版（15.4 MB，精度更高、更慢更大）；不下载则 Tesseract 降级不可用
- 商业分发结论: 可商业分发；分发时保留 Apache-2.0 许可与来源说明，禁止微调后冒充官方来源

## tesseract-ocr
- 名称: Tesseract OCR 引擎（Tesseract4Android 内嵌 native）
- 版本: 5.5.1（随 tesseract4android 4.9.0）
- 许可证: Apache-2.0
- 用途: OCR 降级链的识别核心
- 数据流: 本机内存处理；不联网
- NOTICE 位置: 本文件、随包 Apache-2.0 许可文本
- 替代方案: 见 tesseract4android 条目
- 商业分发结论: 可商业分发，保留 Apache-2.0 许可证与 NOTICE

## leptonica
- 名称: Leptonica 图像处理库（Tesseract4Android 内嵌 native）
- 版本: 1.85.0（随 tesseract4android 4.9.0）
- 许可证: BSD-2-Clause 式自定义许可（leptonica-license.txt 逐字核验，保留版权声明与免责即合规）
- 用途: OCR 图像预处理
- 数据流: 本机内存处理；不联网
- NOTICE 位置: 本文件、随包保留版权两条款声明
- 替代方案: 无独立替代（Tesseract 必需）
- 商业分发结论: 可商业分发，保留版权与免责文本

## libjpeg-libpng-ocr-chain
- 名称: libjpeg v9f 与 libpng 1.6.48（Tesseract4Android 内嵌 native）
- 版本: 随 tesseract4android 4.9.0
- 许可证: IJG 许可证与 libpng 许可证（均为宽松许可）
- 用途: OCR 链图像编解码
- 数据流: 本机内存处理；不联网
- NOTICE 位置: 本文件、随包保留许可文本
- 替代方案: 无独立替代（Tesseract4Android 构建必需）
- 商业分发结论: 可商业分发，保留各自许可文本

## user-configured-ai-endpoints
- 名称: 用户自配 AI 服务（OpenAI 兼容端点与小米 MiMo 语音预设；含 Talkify 式在线 TTS 的上游服务）
- 版本: 无随包 SDK；仅应用内实现开放 HTTP 协议（chat/completions 与 `api-key` 头预设，预填官方默认地址，用户可自行改配）
- 许可证: 不涉及软件再分发；上游服务条款由用户与其服务商约定，应用不代理、不转售任何服务
- 用途: 文章生成、翻译、拍照翻译与可选云端发音的 AI 通道；未配置或未逐次确认时零出网
- 数据流: 仅用户主动触发的文本/图片与 Key 发送给用户自配的 HTTPS 端点（首次调用域名确认、图片逐次确认）；Key 只存系统安全区，不进日志、数据库与导出包
- NOTICE 位置: 本文件、应用内「关于与开源许可」页隐私说明（`LicensesScreen`）与 `docs/verification/release-notes-v0.0.1.md`
- 替代方案: 系统 TTS、离线 OCR、不启用 AI 功能
- 商业分发结论: 应用本体可商业分发；不含也不分发上游服务，服务依赖由用户自配自担

## material-symbols-icon-paths
- 名称: Material Symbols 矢量图标集（仅作度量与风格基准；24dp 画布、2px 视觉重量、Outlined 变体）
- 版本: Google Material Symbols 图标集（Outlined 变体、24dp 画布）；**UI 重设计轮批 1 按图标选型 B（双色层次自绘族）执行，本轮不复制其路径数据**，只沿用其网格、描边重量与无填充优先的风格作参照。实际入库的字形路径全部为自绘，逐枚清单记录在 `app/src/main/java/com/example/englishlearning/ui/components/AppIcons.kt` 顶部注释并登记于 `app-authored-ui-icons`；若后续任何一枚改为直接内嵌上游路径数据，本条目对那一枚重新生效，须在本字段逐个列出内嵌的图标名与出处
- 许可证: Apache License 2.0（Google 对 Material Symbols / Material 图标集的授权口径），允许商用、再分发与改作，条件是保留许可声明与 NOTICE；Google 另行说明该集合不含第三方品牌 logo（法务原因），故本项目不使用任何第三方标志
- 用途: 为全站统一图标体系提供度量基准——替换现状的 emoji、字母缩写徽标（「AI」「♪」「⏰」）与各处自绘异形图标，使全站只有一族字形；因 `app/build.gradle.kts` 的 `dependencyLocking { lockAllConfigurations() }` 与「本轮不新增依赖」约束，不引入 `material-icons-extended`，改为把本项目**自绘**的 SVG path data 与圆形填充标记内嵌打包（`AppIcons.kt` 的 `AppIconSpec` 常量），由本项目的 `AppGlyph` 渲染器在设备内绘制。**本条目只提供度量与风格基准**：本项目只参考 Material Symbols 的 24dp 网格与 2px 视觉重量，未内嵌、也未派生任何它的 path data（度量与风格标准不受版权保护），本 App 内全部字形为本项目自绘或本仓视觉稿转录（逐枚来历见 `app-authored-ui-icons` 与 `ui/components/AppIcons.kt`）；「内嵌打包」一句的主语始终是本项目自绘的那 33 枚，不是上游图标集
- 数据流: 本条目为设计基准参照，无文件随 APK 打包；渲染所用的路径数据全部由本项目自行绘制，仅在设备内绘制，不联网、不读取也不传输任何用户数据
- NOTICE 位置: 本文件、应用内「开源许可与隐私说明」页（`LicensesScreen` 渲染同一份台账）、`ui/components/AppIcons.kt` 顶部注释、`docs/decisions/2026-10-18-ui-redesign-icon-duotone.md`
- 替代方案: 直接内嵌 Material Symbols 的 SVG 路径数据为 `ImageVector`（成本更低，但拿不到双色层次，视觉选型 A 案即此路径）；或引入 `androidx.compose.material.icons` 的 extended 工件（须先解除依赖锁定并另立决策记录）
- 商业分发结论: 可商业分发。Apache-2.0 下保留许可与 NOTICE 即合规；Google 在 Material 图标指南中「希望在应用关于界面注明出处，但非强制」，本项目主动署名。若某枚图标改为内嵌上游路径数据并经简化重绘，属改作，仍受同一许可覆盖，且须同步本条目「版本」字段与 `AppIcons.kt` 清单

## app-authored-ui-icons
- 名称: 应用自绘 UI 图标族（双色层次 duotone，描边骨架 + 内部半透明填充层）
- 版本: v1 已入库（实现为 `app/src/main/java/com/example/englishlearning/ui/components/AppIcons.kt` 中的 `AppIconSpec` 路径数据常量：24×24 画布的 SVG path data 串 + `CircleFill(cx, cy, r)` 圆形填充标记，由 `AppGlyph` 渲染器绘制，本族不含渲染码与颜色取值；枚数以 `docs/decisions/2026-10-18-ui-redesign-contrast-and-icon-layer-rulings.md` 钉定的 33 枚清单为准，结构由 `AppIconsStructureContractTest` 双向钉住——除该文档豁免表的 5 枚描边 only 外每枚两层；逐枚清单与命名同时钉在该文件顶部注释，每新增一枚须同步本字段与 `material-symbols-icon-paths` 生效范围说明）
- 许可证: 无第三方许可依赖。路径数据由本项目按统一度量（24dp 网格、2px 视觉重量、圆头描边）自行绘制，不复制、不派生自任何第三方图标集、素材包或字形文件，因此不触发署名义务；造型仅参考 Material Symbols 的度量与风格标准（度量与风格不受版权保护），不使用其路径数据
- 用途: 承载 UI 重设计轮的图标体系（用户 2026-10-18 选定 B 案）：入口块为域色渐变容器 + 双色层次字形，列表行内退化为单层描边；替换 7 个 AI 功能的单字徽标、设置页共用同一 ⭕ 的 3 个备份入口、以及被手写两次的喇叭图形；使用位置 `app/src/main/java/com/example/englishlearning/ui/components/AppIcons.kt`（33 枚 `AppIconSpec` 路径数据常量与 5 枚两层豁免表所在地），运行时组装在 `app/src/main/java/com/example/englishlearning/ui/components/AppIcon.kt`，容器呈现（40dp 入口 / 30dp 行内）在 `app/src/main/java/com/example/englishlearning/ui/components/IconBadge.kt`
- 数据流: 仅以 Kotlin 路径数据随 APK 打包并在设备内绘制；不下载、不上传、不联网、不含位图素材文件，也不参与任何 AI 或网络请求
- NOTICE 位置: 本文件、应用内「开源许可与隐私说明」页（`LicensesScreen` 渲染同一份台账）、`docs/decisions/2026-10-18-ui-redesign-icon-duotone.md`、`ui/components/AppIcons.kt` 顶部注释
- 替代方案: 直接内嵌经 Apache-2.0 授权的 Material Symbols 路径数据（选型 A，工时更低但无双色层次）；或引入 extended 工件（违反本轮不新增依赖约束）
- 商业分发结论: 可随应用分发，无许可或署名义务。约束：全站只允许这一族字形，禁止再混入 emoji、含字徽标或各处自绘异形图标；容器渐变亮端必须取 `deep` 混 35% `base`，白描边非文本对比 ≥3:1 由计算函数当场判定（浅色 `base` 直接作白字形底不合格：AI 2.11:1、复习 2.06:1）

## app-authored-ui-illustrations
- 名称: 应用自绘界面插画（UI 重设计轮空态与首启引导，上限 5 张）
- 版本: v1 规划（实现为 `app/src/main/java/com/example/englishlearning/ui/components/AppIllustrations.kt` 中的 `ImageVector` 常量；清单与落点逐张记录在 `docs/decisions/2026-10-17-ui-redesign-illustrations.md`，每新增一张须同步该记录与本字段）
- 许可证: 无第三方许可依赖。每张插画的几何路径由本项目自行设计绘制，不复制、不派生自任何第三方图库、素材包、图标集或角色作品，因此不触发 CC BY-SA 4.0 或其他素材许可义务，也不产生署名义务；不使用 Material Symbols 作为插画载体，不模仿任何第三方品牌或吉祥物造型
- 用途: 为空态与首启引导提供情绪锚（首启未选词书、收藏为空、生词本为空、错词本为空、阅读历史为空），语义由旁边文字与动作承担，图形本身为装饰性 `contentDescription = null`
- 数据流: 仅以 Kotlin 路径数据随 APK 打包并在设备内绘制；不下载、不上传、不联网、不含任何位图素材文件，也不参与任何 AI 或网络请求
- NOTICE 位置: 本文件、`docs/decisions/2026-10-17-ui-redesign-illustrations.md`、`docs/superpowers/specs/2026-10-17-ui-redesign-design.md` §4.5。**当前状态 = 待建**：`app/src/main/java/com/example/englishlearning/ui/components/AppIllustrations.kt` 尚未落码（批 3 交付），所以「实现文件顶部注释」与「应用内开源许可页 `LicensesScreen` 的对应条目」这两处 NOTICE 落点现在还不存在——批 3 落码时必须一并补上，届时删除本段「待建」标注；在此之前不要把本条目当作已随 APK 分发的 NOTICE 证据
- 替代方案: 空态一律纯文字加一个动作（零图形资产，但五个空态全无锚点）；或采用经逐张许可核验的第三方插画（须逐项确认归属与署名）
- 商业分发结论: 可随应用分发，无许可或署名义务。硬上限 5 张，新增第 6 张须先改决策记录与本条目；本轮为静态矢量，不含动画

## newsreader-font
- 名称: Newsreader 衬线字体（可读性英文正文/标题字族；本应用只取正体，不取 italic）
- 版本: 原型 `assets/fonts/newsreader-regular.woff2`（可变字体，`opsz` 6–72、`wght` 200–800）经 fonttools 4.67.0 + brotli 1.2.0，在 `opsz=16` 上取 `wght` 400/500/550/600/650 五个静态实例，落盘为 `app/src/main/res/font/newsreader_{regular,medium,book,semibold,bold}.ttf`（Android 不支持 woff2 与可变字体，故转静态 TTF）；字重档与原型 `@font-face` 的 `font-weight: 400 600` 区间一致，550/650 为区间内的插值档
- 许可证: SIL Open Font License 1.1（OFL-1.1）。允许嵌入、修改与随应用分发，须随包保留 OFL 全文与版权声明，且字体本身不得单独售卖
- 用途: 原型 `--serif` 字族，仅用于英文/词形/数字的展示层排印（Display、StrongTitle、ArticleTitle、StatNum、PlanNum、Lemma、Prose 等档），中文与 UI 正文仍走系统 sans；不参与任何业务逻辑或数据流
- 数据流: 五个 TTF 随 APK 打包，仅在设备内由 Compose 渲染；不下载、不上传、不联网，不参与任何 AI 或网络请求
- NOTICE 位置: 本文件、应用内「开源许可与隐私说明」页（`LicensesScreen` 渲染同一份台账，构建期由 `docs/third-party-notices.md` 同步进 assets）、`ui/theme/AppFont.kt` 顶部注释；发布包须随附 OFL 1.1 全文
- 替代方案: 系统衬线族（`FontFamily.Serif`，各厂商字形不一，字号度量与原型的 1.05/1.2 行高需重校）；或不设衬线层级、全部走 sans（层级只剩字号与字重）
- 商业分发结论: 可商业分发，条件是保留 OFL 1.1 全文与版权声明、不单独售卖字体、不对字体改名后重新发布；本应用未修改字形，只做可变轴取实例的格式转换
