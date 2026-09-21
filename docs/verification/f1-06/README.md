# F1-06 词卡详情页 —— 真机验证记录（说明图资源修订）

**日期：** 2026-09-22（设备本地时间）
**基线提交：** `7e118be`（说明图由单色矢量改为彩色卡通位图）
**产物：** `app-debug.apk`，14 282 086 字节，md5 `fa26c0d0a125b1ee6c3cf89889f7c8f5`

## 环境

| 项 | 值 |
|---|---|
| 设备 | `bf353dda` / Xiaomi M2102J2SC |
| Android | 13（API 33） |
| 安装方式 | `adb install -r`，随后为取得完整流程执行 `pm clear` |
| 屏幕 | 1080×2340 @440dpi，验证期间锁定竖屏 |

## 1. 本次修订的范围

F1-06 切片 2 原交付 12 张**单色薄荷绿抽象几何**矢量说明图。真机查看后用户反馈其观感"像图标、看不出词义"，要求改为**彩色卡通、易理解**的插画。

机制不变（`lemma → R.drawable` 纯函数 + `painterResource`，无新依赖、无自写解码），仅资源形态由矢量改为**位图**：

- 新增 `app/src/main/res/drawable-nodpi/illus_<lemma>.webp`，512×512 RGBA **无损 WebP**，12 张共 **1164.2 KB**；
- 删除 `app/src/main/res/drawable/ic_illus_<lemma>.xml`（12 个）；
- 决策与来源登记：`docs/decisions/2026-09-22-card-illustration-assets.md`、`docs/design/card-illustration-prompts.md`、`docs/third-party-notices.md` 的 `app-authored-card-illustrations`。

## 2. 资源确实进入产物

解包 `app-debug.apk`，`res/drawable-nodpi-v4/` 下 12 个 WebP，未压缩存储（WebP 已压缩，AGP 跳过二次压缩），合计 1164.2 KB：

```
res/drawable-nodpi-v4/illus_ability.webp    66.7 KB
res/drawable-nodpi-v4/illus_achieve.webp   172.8 KB
res/drawable-nodpi-v4/illus_benefit.webp   113.2 KB
res/drawable-nodpi-v4/illus_climate.webp    89.0 KB
res/drawable-nodpi-v4/illus_develop.webp    39.8 KB
res/drawable-nodpi-v4/illus_economy.webp    86.6 KB
res/drawable-nodpi-v4/illus_feature.webp   166.0 KB
res/drawable-nodpi-v4/illus_generous.webp  141.6 KB
res/drawable-nodpi-v4/illus_influence.webp  68.0 KB
res/drawable-nodpi-v4/illus_maintain.webp   85.9 KB
res/drawable-nodpi-v4/illus_obvious.webp    62.6 KB
res/drawable-nodpi-v4/illus_reduce.webp     72.2 KB
```

`05-shipped-illustrations-contact-sheet.png` 是**直接读取这 12 个已打包 WebP 字节**、合成到卡片底色 `MintSurface (#EFFFFFFF 叠 #F1FBF5)` 上得到的联络表 —— 图中像素就是 APK 里的像素。

## 3. 端到端流程（截图取证）

| 步骤 | 操作 | 观测结果 | 截图 |
|---|---|---|---|
| 1 | `pm clear` 后冷启动 | 无本地资料 → 进入「先认识一下你」，输入称呼并「创建资料」 | — |
| 2 | 在设置页保持默认（四级 / 每日新增 10 词 / 三档开关默认值）点「保存并开始学习」 | 今日计划：**今日新增 10 词 / 今日复习 0 词 / 今日计划共 10 项 / 新增 0/10 / 未解锁** | `01-today-plan-fresh.png` |
| 3 | 点「开始学习」 | 词卡 `第 1 / 10 张` = **ability**（`əˈbɪləti` / `n.` / `能力；才能`），三档按钮 `不认识` / `模糊` / `认识` | `02-word-card-ability.png` |
| 4 | 点「模糊」（该档开关**默认开启**） | 进入词义详情页，说明图节点 `content-desc="说明图 ability"`，bounds `[320,1043][760,1483]` = **440×440 px**（即 `160.dp` @440dpi），**彩色卡通插画正确渲染、透明底浮在卡片上** | `03-detail-ability.png` |
| 5 | 点「返回」→ 第 2 张 = **achieve** → 点「模糊」 | 详情页渲染对应插画（爬上小山插旗），节点 bounds `[320,1130][760,1570]`（因该卡有 `词形变化` 行而下移 87 px） | `04-detail-achieve.png` |

## 4. 覆盖的验收点

- **AC1-11 / AC1-12（有说明图则展示，无则整块隐藏）**：`ability`、`achieve` 两个 lemma 在真机上均渲染出说明图；无资源时隐藏由 `CardDetailScreenTest.illustrationHiddenForUnknownLemma` 覆盖。
- **位图可在真机解码**：说明图节点存在即要求 `painterResource` 成功解出位图；`CardDetailScreenTest.rendersLemmaIpaPartOfSpeechMeaningAndIllustration` 在真机通过。
- **视觉规范 §5.3**：透明底、无文字/水印、无洋红与紫色、卡片内 `160.dp`、`ContentScale.Fit` 未裁切 —— 四条均由截图目视确认。

## 5. 真机全量回归

`verification-logs/46-f1-06-illus-device-full.log`：

```
Starting 66 tests on M2102J2SC - 13
Finished 66 tests on M2102J2SC - 13
BUILD SUCCESSFUL in 2m 38s
```

结果 XML `tests="66" failures="0" errors="0" skipped="0"`，其中 `CardDetailScreenTest` 5 个用例全通过。JVM 侧 `CardDetailResourcesTest` `tests="4" failures="0" errors="0"`。

## 6. 限制与未覆盖项

- **步骤 1 的 `pm clear` 是验证前置操作，不代表产品行为**：验证开始时今日计划已 12/12 完成，而每日计划是**不可变快照**且设置「默认从次日生效」——实测把词书改成六级并保存、再冷启动，今日计划仍显示四级且仍为 12/12（`verification-logs/46-detail-ui-0*.xml`）。因此要在真机上取到"未提交词卡 → 详情页"的流程，只能清应用数据。**注意 `pm clear` 不可逆**，会连带删除本地数据库。
- 只截取了 2 / 12 张说明图的真机渲染（`ability`、`achieve`）；其余 10 张由 §2 的打包核对与联络表覆盖，未逐张走真机。
- 说明图**像素内容**的判定依赖目视；JVM 测试只能断言 lemma → 资源映射。
- 本次未重跑完整 JVM 回归（134 tests / 4 个既有 Stage-0 失败）。改动只涉及资源常量与 KDoc，无生产逻辑变化，且 `compileDebugUnitTestKotlin` 已随构建编译全部单测源码。
- **未关闭项（来自决策文档）**：所用 AI 图像生成服务自身的商用与再分发条款尚未复核；这 12 张仅服务占位词条，真实词书配图须单独完成许可核验。
