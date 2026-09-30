# 词书包（含配图）设计与打包流程

日期：2026-09-28
状态：**待用户确认词源与释义来源后开工**（格式部分可先行）

## 一、目标

把「词表 + 释义 + 配图」当成**可导入/导出的内容包**，在打包阶段一次性做好；运行时**零网络**，
有没有网都能看词卡配图。

这直接解决两个已实测的约束：

1. 公开图库（Openverse / Wikimedia）从设备与开发机**均不可达**（50 词探针：49 TIMEOUT + 1 NETWORK_UNAVAILABLE，0 次成功）；
2. AI 生图按张计费，放在运行时等于每次看词都花钱。

## 二、非目标

- 不做运行时联网抓图（已被实测否掉）。
- 不做词书云端市场/分享服务器（导出文件由用户自行传递）。
- 不改学习事件、FSRS 调度、UI 结构（`WordCardSource` 端口已为此预留）。

## 三、包格式

导出文件 `*.wbpack`（实为 zip，单文件可分享）：

```
book.json          # 词书元数据 + 词条数组（词条内含 image 引用）
manifest.json      # 每张图的 sha256 + 来源 + 许可 + 尺寸
images/
  apple.webp       # 512×512 WebP（约 40KB/张）
  umbrella.webp
```

### 3.1 book.json

```json
{
  "formatVersion": 1,
  "id": "ngsl-core-100",
  "displayName": "NGSL 核心 100（排名 301–400）",
  "level": "基础",
  "sourceId": "ngsl-1.2",
  "sourcePolicy": "应用内学习分组，不是官方考试大纲词表。",
  "attribution": "词表来源与许可文本（含 CC BY-SA 4.0 署名要求）",
  "cards": [
    {
      "cardId": "ngsl-core-100:surf",
      "lemma": "surf",
      "rank": 1204,
      "ipa": "sɜːrf",
      "senses": [
        { "pos": "v.", "meaningZh": "冲浪；（在互联网上）冲浪，浏览" },
        { "pos": "n.", "meaningZh": "拍岸碎浪；浪" }
      ],
      "example": "I spent the evening surfing the internet.",
      "exampleZh": "我整晚上都在网上闲逛。",
      "derived": [{ "lemma": "surfer", "pos": "n.", "meaningZh": "冲浪者；网虫" }],
      "phrases": [
        { "text": "surf the Internet", "meaningZh": "在网上冲浪" },
        { "text": "surf and turf", "meaningZh": "一种牛排餐" }
      ],
      "synonyms": [{ "lemma": "breaker", "pos": "n.", "meaningZh": "碎浪" }],
      "image": { "file": "images/surf.webp", "sha256": "<hex>" }
    }
  ]
}
```

字段说明（**以用户提供的竞品词详情页为基线**，见第十节）：

| 字段 | 约束 | 来源 |
|---|---|---|
| `lemma` / `rank` | 原形 + NGSL 频率排名 | 词表（CC BY-SA 4.0） |
| `ipa` | 无括号（解析时剥 `/` `[` `]`） | AI 生成 |
| `senses[]` | 1–4 条，每条 `pos` + `meaningZh`（≤30 字，多义项用「；」） | AI 生成 |
| `example` / `exampleZh` | 例句 ≤12 词 + 中文译文 | AI 生成 |
| `derived[]` | ≤3 条派生词（`lemma`/`pos`/`meaningZh`） | AI 生成 |
| `phrases[]` | ≤3 条关联短语（`text`/`meaningZh`） | AI 生成 |
| `synonyms[]` | ≤3 条近义词（`lemma`/`pos`/`meaningZh`） | AI 生成 |
| `image` | 可空；`file` + `sha256` | 打包阶段生成（512px WebP） |

`senses` 取代原先的单字段 `partOfSpeech` + `meaningZh`：参考图里一个词按词性**分组多条**释义，
单词性单条时数组长度为 1。**任何字段超限即整词拒绝**（不写半份数据），与解析器既有纪律一致。

### 3.2 manifest.json

```json
{
  "formatVersion": 1,
  "images": [
    {
      "file": "images/apple.webp",
      "sha256": "<hex>",
      "width": 512, "height": 512, "bytes": 41203,
      "origin": {
        "kind": "ai-generated",
        "model": "gpt-image-2",
        "prompt": "<最终英文提示词>",
        "generatedAt": "2026-09-28T20:00:00Z"
      },
      "license": "ai-generated"
    },
    {
      "file": "images/umbrella.webp",
      "sha256": "<hex>",
      "origin": {
        "kind": "gallery",
        "provider": "openverse",
        "sourceUrl": "https://...",
        "creator": "...",
        "matchedBy": "lemma"
      },
      "license": "cc0"
    }
  ]
}
```

`origin.kind ∈ {gallery, ai-generated}`、`origin.matchedBy ∈ {lemma, synonym}`——
保留这两维是为了将来「图库就绪后做增量替换」：manifest 里已经记录了每张图的来源，
可以把 `ai-generated` 且 `matchedBy=lemma` 的条目挑出来换成图库图。

## 四、运行时读取

同一个 `WordCardSource` 端口两个实现：

| 实现 | 位置 | 用途 |
|---|---|---|
| `BundledWordBookSource` | `assets/wordbooks/<id>/` | 随包内置词书（可先用 12 词最小册） |
| `ImportedWordBookSource` | `filesDir/wordbooks/<id>/` | 用户导入的 `.wbpack` 解包目录 |

UI / 事件库 / 调度器**不需要改动**（`WordCardSource` 注释即为此承诺）。

图片按 `cardId → 文件路径` 映射，读取走本地文件；**不进入 Room、不进相册**。

## 五、导出 / 导入

- **导出**：把 `book.json + manifest.json + images/` 打成 zip，`ShareSheet` 分享（复用现有 PDF 分享通道的写法）。
- **导入**：解析 zip → 校验 `formatVersion` → 逐张校验 `sha256` 与 `bytes` → 全通过才落 `filesDir`；
  **任何一项失败整体拒绝，零写入**（沿用 `BackupProvider` 注释里的纪律）。
- 导入后可选「替换同名词书」或「另存为新词书」。

## 六、配图解析阶梯（打包阶段）

按用户要求：**每个词都配图**，取图顺序为

1. **图库按拼写检索**（原文图）
2. **图库按释义/近义表达检索**（同义图）
3. **AI 生成**（受控模板 + 词义提示，兜底）

两个工程约束：

- **开跑前做一次可达性探测**：图库域名不通就整批跳过 1、2 两步并记录原因。
  否则 1000 词 × 25s 超时 ≈ 7 小时纯等待（已实测：设备侧 50 词探针全部超时）。
- **批量前一次总确认**：明确「共 N 张、按张计费、可中断续跑」，避免逐张弹窗。

## 七、成本与体积（需用户知情）

| 项 | 估算 |
|---|---|
| 单次出图 | 按张计费（用户服务侧定价） |
| 1000 词全配 | ≈ 1000 次调用 |
| 512px WebP | ≈ 40KB/张 → 1000 张 ≈ 40MB（宜按册导入，不塞 APK） |
| 建议 | 先跑 **100 词**校准单张成本/耗时/失败率与质量，再放量；manifest 记录进度支持续跑 |

## 八、词源与释义来源（**待用户确认，当前阻碍**）

现状：`assets/wordbooks/metadata.json` 里 6 个词书 `totalWords` 均为 0（「词条尚未随本任务打包」），
`sourceId = ngsl-nawl-1.2`，词卡仍是 `PlaceholderWordCardSource` 里 12 个代码内词条。

一册完整词书需要：**词表 + 音标 + 词性 + 中文释义 + 例句 + 配图**。其中前五项都还不存在，
且许可必须可验证（NGSL/NAWL 为 CC BY-SA，需署名）。可选：

| 方案 | 说明 |
|---|---|
| A. 用 NGSL/NAWL 词表 + 另配释义 | 词表许可清晰（需署名）；中文释义仍需来源（词典授权或 AI 生成，后者有准确性风险） |
| B. 用户提供已授权词表文件 | 最稳妥：用户手上若有 CSV/Excel/JSON（含释义），直接作为打包输入 |
| C. 先用 12 词最小真包跑通格式 | 不阻塞：先把格式/导入导出/批量配图这条管子做出来，词源谈定后喂进去即可 |

## 九、验收标准
1. **离线校验测试**（JVM）：遍历 `manifest.json`，断言每个 `cards[].image.file` 存在、
   `sha256` 与 `bytes` 匹配、每张图都有 `origin` 与 `license` 记录。
2. **导入拒绝测试**：篡改一张图的字节或删除一条 manifest 记录 → 导入失败且**零写入**（变异验证）。
3. **真机走查**：详情页显示配图；导出 → 重新导入 → 图片仍可见；飞行模式下同样可见。
4. 证据归档到 `outputs/verification-word-book-package-<date>/`。

## 十、词卡详情页版式基线（用户提供的竞品参考图，2026-09-28）

用户提供截图 `outputs/word-card-reference/reference-surf-detail.png`（1206×2622），
内容为另一款背单词 App 的**词详情页**（词：`surf`）。作为词详情页的信息架构基线。

| 区块 | 内容 | 词书包是否需要提供 |
|---|---|---|
| 顶部导航 | 返回 / 编辑 / 搜索 / 收藏(星) / 更多(三点) | 否（应用自身功能） |
| 单词区 | `surf` 大号粗体 + 音标 `/sɜːrf/` | ✅ `lemma` + `ipa` |
| 释义区 | 按词性分组：`v.` 冲浪；（在互联网上）冲浪/浏览 —— `n.` 拍岸碎浪[涛声]，浪 | ⚠️ 当前设计是**单一 `partOfSpeech` + `meaningZh`**，参考图要求**一词多词性多条释义** |
| 例句区 | 例句 + 中文翻译，目标词**高亮**，右侧**发音按钮** | ✅ `example`（参考图还要求 `exampleZh` 译文） |
| 派生联想 | `surfer` n. 冲浪者，进行冲浪运动的人，（互联网上）网虫 | ❌ **词书包当前没有这个字段** |
| 关联短语 | `surf the Internet`（网上冲浪）/ `surf and turf`（一种牛排餐） | ❌ **缺字段** |
| 近义词 | `breaker` (n.) | ❌ **缺字段** |
| 单词笔记 | 记忆辅助内容（如 `sure adj. 确信的`、`surfing n. 冲浪运动`） | ❌ 可复用已有 `word_ai_notes`（词卡笔记），也可内置 |
| 底部操作 | 绿色「下一个」按钮 + **下一词预览** | 否（应用功能，取词表顺序即可） |
| 版式 | 柔和浅色渐变底 + 白色圆角卡片分区，每区块标题带小图标 | 否（视觉，L 分支） |

**结论**：若词详情页按此基线做，词书包（`book.json`）字段需要从
`{lemma, ipa, partOfSpeech, meaningZh, example, image}`
扩展为
`senses[{pos, meaningZh}], exampleZh, derived[{lemma, pos, meaningZh}], phrases[{text, meaningZh}], synonyms[{lemma, pos}], image?`。

这必须在**批量生成前**定稿，否则 100 词（乃至整册）的生成结果要整体重跑。
