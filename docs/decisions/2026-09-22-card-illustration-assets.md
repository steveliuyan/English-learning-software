# 词卡说明图资源：AI 生成彩色卡通插画（位图）

- 日期：2026-09-22
- 状态：已接受（实现与真机验收见下文「验收证据」）
- 相关规格：`docs/specs/01-vocabulary-learning-and-review.md` F1-06、AC1-08/AC1-11/AC1-12
- 相关决策：`2026-09-21-card-detail-page.md`（本文件修订其「说明图资源」章节）、`2026-09-19-placeholder-word-card-content.md`（占位内容与许可）
- 相关规范：`docs/design/visual-style-guide.md`（新增 §5.3 说明图插画）

## 背景

F1-06 切片 2 落地的 12 张说明图为**单色薄荷绿抽象几何矢量**（圆角方块打底 + 一圆一点一梯形）。真机查看后用户反馈：读起来像图标、颜色单一、看不出词义，要求改为**彩色卡通、一眼可理解**的插画，并允许 AI 生成或联网取材。

调研事实：

- 现有资源为 `app/src/main/res/drawable/ic_illus_<lemma>.xml`，共 12 个，覆盖占位词源 `PlaceholderWordCardSource` 的全部 12 个自撰词（`metadata.json` 的 `totalWords` 仍为 0）。
- 项目**没有位图解码代码，也没有图片库**；`res/` 资源一律经 `painterResource` 使用。
- `minSdk` 为 26，WebP 与 PNG 位图均可直接经 `painterResource` → `BitmapPainter` 渲染，**不需要新增任何依赖，也不需要自写解码**。
- `AGENTS.md` 硬约束：未通过许可核验的图片不得打包；`docs/third-party-notices.md` 是发布前许可检查的输入，验收脚本 `tools/verify-third-party-notices.main.kts` 要求每条目 8 个字段齐全。
- 项目已有先例：F1-03 的占位词条为「应用自撰、不派生于任何第三方数据集」，登记于台账 `app-authored-placeholder-word-cards`。

## 候选方案

1. **AI 生成彩色卡通插画，键控去背景后转为无损 WebP 位图（采用）**
2. 联网检索 CC0 / 开放素材库图片
3. 继续用矢量，手工重绘为多色卡通

## 决定

### 图片来源：AI 生成（方案 1）

- 提示词、生成参数与后处理脚本全部入库，**资源可复现**：`tools/illustrations/prepare_illustrations.py` 与 `docs/design/card-illustration-prompts.md`。
- 产物不是第三方素材库作品，不派生自 NGSL/NAWL、CEFR-J 或任何第三方词书、词典、图片集，因此不触发第三方素材许可义务（登记见台账 `app-authored-card-illustrations`）。
- **残留风险（发布前须复核）**：生成服务自身的使用条款（尤其商用与再分发条款）以所用生成服务为准，本项目未在代码层面对其作保证。这 12 张图服务的是**占位词条**，真实词书及其配图的许可闭合是独立事项（见「未纳入」）。
- **否决联网检索 CC0 素材**：省事但需逐张核验归属与署名，且开放素材库常见 CC BY-SA 等传染性条款，对商业分发不友好；人工核验成本高于收益。
- **否决手工重绘矢量**：体积最小、天然缩放、零许可风险，但手写路径难以达到卡通插画的造型质感，与用户诉求（彩色卡通、易理解）不匹配。

### 资源格式与渲染：无损 WebP + `painterResource`（位图化）

- 资源落 `app/src/main/res/drawable-nodpi/illus_<lemma>.webp`，512×512，RGBA，**无损**编码，共 12 个，合计 1164.2 KB（约 1.14 MB）。
- 选无损而非有损：卡通插画是大色块硬边，有损压缩会在轮廓上产生振铃，且边缘 alpha 会被破坏。
- 选 WebP 而非 PNG：同图无损 WebP 约为 PNG 的一半体积（实测单张 66.7 KB vs 132.2 KB）。
- 选 `drawable-nodpi` 而非 `drawable`：位图不随密度缩放，`Image` + 默认 `ContentScale.Fit` + 固定 `160.dp` 由渲染期缩放，避免多套密度目录。
- **渲染层零改动**：`CardDetailScreen` 仍为 `painterResource(res)`，映射函数 `lemmaToDrawableRes(lemma): Int?` 签名不变，只改 `R.drawable` 常量名（`ic_illus_*` → `illus_*`）。不引入 Coil 等图片库，不自写解码。
- **否决 `assets/` + `BitmapFactory`**：需自建解码、采样与内存管理，V1 属过度设计（沿用 `2026-09-21-card-detail-page.md` 的同一判断）。
- 删除 12 个旧矢量 `ic_illus_*.xml`；`res/drawable` 下的启动页与应用图标资源不受影响。

### 生成与后处理流水线（坑与结论）

生成器有四个必须绕开的坑，均已实测定位：

1. **`background=transparent` 不是真透明**：会把「透明棋盘格」直接烘焙成像素（alpha 全为 255）。改用 `background=opaque` 生成纯色背景再键控。
2. **平台强制水印**：右下角烧入「AI生成 / WORKBUDDY」商标，提示词与 `footnote` 均无法抑制。实测画面底线在 y≈830、水印占 y 963–1013，**裁掉底部即可彻底去水印且不切画面**（`BOTTOM_CROP = 955`）。
3. **键控不能按固定色 + 容差洪泛填充**：背景在主体附近有暗部渐变，与角落种子色差超过容差，洪泛填充够不到，会留下成片背景色；且模型未必照做指定键色（实测有一张把洋红画成粉色）。改用**从边框取样自动检测背景中位色 + 全局距离判定**，并在检测到的背景不够「彩色」或边框不均匀时**拒绝处理而不是误吃画面**。
4. **抗锯齿顺序易错**：在**二值** mask 上做反混合是空操作（此时还没有半透明像素）；「先腐蚀再羽化再反混合」也不成立——羽化会让边缘 alpha 升到 39% 而底层像素仍是纯背景，解出的还是背景色。正确顺序是**二值 mask → 2px 腐蚀 → 裁切+留白 → 降采样（抗锯齿由重采样产生）→ 在输出分辨率上反混合 → alpha 下限归零孤立残留**。
5. **自检必须宽松到能抓光晕**：只判「洋红占优」会漏掉粉/洋红**光晕**（实测 `obvious` 的灯泡光晕即由此漏检，靠目视联络表才发现）。自检阈值收紧到「几乎就是检测到的背景色」，并以**目视联络表为最终判据**，不自检通过即宣称可用。

### 产出质量（如实记录）

- 清晰可用：`ability`、`achieve`、`benefit`、`climate`、`develop`、`economy`、`feature`、`generous`、`obvious`、`reduce`。
- 返工后可用：`influence`（首版「人物 + 扩音器」造型怪异、画面拥挤，改为**多米诺连锁**）、`maintain`（首版元素杂乱，改为**盆栽 + 浇水壶**）。`develop`、`obvious` 也曾因构图碎裂与键控光晕返工。
- **已知不足**：整体风格仍有漂移（部分偏手绘柔光、部分偏扁平描边），未做到完全统一。这 12 张定位为**占位词条级视觉**，真实词书配图不在本次范围。

## 实现边界

- 代码改动仅 4 处：`res/drawable-nodpi/illus_*.webp`（新增 12）、`res/drawable/ic_illus_*.xml`（删除 12）、`CardDetailResources.kt`（`R.drawable` 常量名 + KDoc）、`CardDetailResourcesTest.kt`（期望值 + KDoc）；另有 `CardDetailScreen.kt` 的 KDoc 措辞修正。
- 不改动：`lemmaToDrawableRes` 签名与语义（无资源返回 null → 隐藏图片模块）、详情页布局与尺寸、学习事件模型、FSRS 调度、解锁判定、`showSetup`/`showLearning` 顶层状态机。
- 不新增任何 Gradle 依赖，`gradle.lockfile` 无需变更。

## 验收证据

- JVM：`CardDetailResourcesTest` 4 个用例（已知 lemma 命中、12 个占位词全覆盖、未知 lemma 返回 null、大小写不敏感）。
- 真机：`AppScreenTest` 全量（含详情页说明图断言），日志见 `verification-logs/`；说明图为位图，其**像素内容**只能由真机流程截图判定，JVM 侧只断言映射。
- 引用日志只引用日志中真实存在的文本；文件名不作为结论依据。

## 未纳入

- 真实词书词条的配图（许可未闭合，见 `2026-09-19-placeholder-word-card-content.md`）。
- 插画风格的进一步统一与逐张重绘。
- 说明图的模块级开关（后期设置项，V1 全部提供）。
- 位图资源的按需加载/内存缓存优化（12 张 512×512 无损图，当前规模不需要）。
