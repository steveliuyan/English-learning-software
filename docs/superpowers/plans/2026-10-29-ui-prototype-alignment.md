# UI 原型对齐计划（2026-10-29）

## 目标与权威源

把 Android 应用的 39 个界面与交互对齐到已冻结的 HTML 原型：

- 原型目录（只读，禁止修改）：
  `C:\Users\steveliuyan\AppData\Roaming\Open Design\namespaces\release-stable-win\data\projects\6a0aa00b-6bfc-43e7-b35f-13f7478d3147`
- 权威文件：`index.html`、`assets/app.css`、`assets/app.js`、`assets/data.js`、`assets/icons/*.png`、`assets/fonts/*.woff2`
- 冲突裁决：原型 > 本文描述；原型未画到的状态先问用户，不自行补。
- 范围外：FSRS 排期、备份、AI 请求协议、权限、依赖。只在接界面时读/写既有接口。

## 批次

| 批 | 内容 | 验收 |
|---|---|---|
| 0 | 令牌层：颜色 / 排印 / 形状 / 动效 / 字体 / 图标贴纸 / 共享组件 | JVM 契约全绿 + 共享组件样例可编译；不逐屏验收 |
| 1 | 学习路径 11 屏 + 词卡反馈/回执交互 | 逐屏对照原型截图 |
| 2 | 阅读 5 屏 + 我的 8 屏 + 其他 5 屏 | 逐屏对照 |
| 3 | AI 助学 10 屏（含未接入控件禁用态） | 逐屏对照 |
| 4 | 全量 QA：深浅两套、fontScale 2.0、TalkBack、动效减弱、真机走查 | 门禁 + 真机清单 |

## 批 0 步骤

1. **红**：改写/新增 JVM 契约测试，钉住本批 token 与共享组件结构：
   - `DesignTokenContractTest`：新 `AppColorSet` 字段与取值、域色、`AppPalette` 同步。
   - `MaterialThemeAssemblyContractTest`：M3 角色新映射、`AppShapes`、无裸 hex。
   - `AppTypeContractTest`（新）：字号/字重/字距/行高/字族与 M3 派生的逐档钉值。
   - `AppMotionContractTest`（新）：160/220/300 与两条缓动。
   - `AppStickerContractTest`（新）：32 张 `sticker_*.png` 存在、尺寸 256、`res/drawable-nodpi` 路径。
   - `AppFontContractTest`（新）：Newsreader 5 个静态字重 + OFL 许可登记。
   - `SharedComponentContractTest`（扩展）：贴纸徽标 / 卡片 / 行 / 分段 / 芯片 / 开关 / 选择行 / 提示 / 空状态 / 双段进度条。
2. **绿**：按测试实现 `AppColors`、`AppPalette`、`AppType`、`AppShape`、`AppMotion`、`MintGlassTheme`、共享组件。
3. **字体**：用 `fonttools` 从原型 woff2 生成静态 TTF（400/500/550/600/650），落 `res/font/newsreader_*.ttf`；登记 SIL OFL 1.1。
4. **贴纸**：32 张 256×256 PNG 拷进 `res/drawable-nodpi/sticker_<name>.png`，代码按资源名引用，不联网。
5. **门禁**：`assembleDebug → testDebugUnitTest → compileDebugAndroidTestKotlin`（本机 Gradle 8.13，`exit ${PIPESTATUS[0]}`）。
6. **报告**：`docs/handoff/2026-10-29-ui-prototype-batch-0-report.md`，含对照表、实测对比度、未决项。

## 迁移策略

- 既有 57 个屏在批 0 不重写；保留同名兼容字段（`surfaceGlass`、`tint3`、`onMint`、`onBrand`、`errorFill`）以免半迁移屏编译失败，批 1–3 逐屏改用新 token，批 4 删除兼容字段。
- `AppType` 六档保留名字、换成原型度量；新增 `Eyebrow / Caption / Meta / BodyLg / Lemma / StatNum / Prose / ProseZh / NavLabel / StrongTitle / ArticleTitle / PlanNum` 供共享组件与新屏使用。
- M3 的 15 个排印角色由六档派生；M3 颜色角色改映到新 token，不再使用旧 `onMint / errorFill` 语义。

## 已知冲突（需在报告点名）

- 原型 `--fuzzy #A8792A` 压 `--surface #FFFFFF` 实测 3.87:1，低于正文 4.5:1；原型为权威，先照原值落地并记录，待用户裁决是否调深。
- 原型 `--ink-3` 压 `--bg` 浅套 4.39:1，略低于 4.5；`ink-3` 只用于 11–13sp 辅助文字，记录实测。
- 原型没有「设置」域色；现有 `AppDomain.Settings` 由 `--ink-3` 派生，批 1 屏迁移时按原型逐处核对。
- 原型的 AI 配置项（temperature / max_tokens / stream / 语音 / 生图参数）超出允许的请求协议；按用户裁决 B 画布，禁用未接入控件并标注「暂未接入」。

## 证据与门禁

- 每个 token 断言都从源码文本或真实 `Color` 复算，不靠注释。
- 对比度一律按 WCAG 相对亮度当场计算，结论写实测值，不写「合格」。
- 单测基线只增不减；报告 §3 对照基线写清新增/下降。
