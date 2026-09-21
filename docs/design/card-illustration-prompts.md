# 词卡说明图生成台账（12 张占位词配图）

> 用途：让 `res/drawable-nodpi/illus_<lemma>.webp` 这 12 张位图**可复现**。每张图的提示词、生成参数与后处理方式都记录在此。
>
> 相关：决策 `docs/decisions/2026-09-22-card-illustration-assets.md`、许可台账 `docs/third-party-notices.md` 的 `app-authored-card-illustrations` 条目。
>
> 后处理脚本：`tools/illustrations/prepare_illustrations.py`。

## 1. 生成参数

| 项 | 值 |
| --- | --- |
| 尺寸 | `1024x1024` |
| 质量 | `high` |
| 背景 | `opaque` + 提示词指定纯色背景（**不要**用 `transparent`，见决策文档「流水线」第 1 条） |
| 提示词结构 | 共用风格前缀 + 单句主体描述 |
| 每词张数 | 1（`influence`、`maintain`、`develop`、`obvious` 各返工 1 次） |

## 2. 共用风格前缀

```
Flat vector-style cartoon illustration, soft rounded shapes, thick smooth outlines,
cheerful friendly mood, bright harmonious colors.
Palette: mint green, teal, warm coral, soft yellow, sky blue, white, cream, brown
- absolutely no magenta or purple anywhere in the subject.
The entire canvas background must be ONE single flat solid pure magenta color (#FF00FF):
no checkerboard, no grid, no gradient, no shading.
Composition: the subject sits fully inside the canvas with a wide empty magenta border
on all four sides - it must not touch, cross, or be cut off by any canvas edge, and the
bottom strip stays completely empty magenta.
Clean children's-book quality, no text, no letters, no words, no numbers.
Subject: <见下表>
```

说明：提示词里指定洋红只是为了让背景**尽量**单色、便于键控。脚本实际是**自动检测**背景色，不依赖提示词是否被模型照做（实测有图被画成粉色背景，仍能正确处理）。

## 3. 逐词主体描述（与已发布资源一一对应）

| lemma | 主体描述 | 备注 |
| --- | --- | --- |
| `ability` | a smiling child standing confidently and holding a bright glowing yellow light bulb high overhead with both hands, a small open toolbox beside them on the ground | — |
| `achieve` | a cheerful young person standing triumphantly on the rounded top of a small green hill, planting a bright coral flag, with a curved dotted yellow stepping path winding up the hill behind them | — |
| `benefit` | a happy person holding a large green gift box with both hands, with a rising coral arrow and a few small gold coins floating beside them | — |
| `climate` | a cute smiling Earth globe with rosy round cheeks wearing a little sun hat, with a few fluffy white clouds, a soft yellow sun and a small green sprout in a patch of soil beside it | — |
| `develop` | a simple row of three clearly separated stages of a plant growing from a small patch of brown soil - a small brown seed, then a short green sprout with two leaves, then a slightly taller green plant with a small yellow flower. All three stand on the same ground line, flat simple shapes, evenly spaced, nothing floating | **返工版**：首版构图碎裂、主体只占下部 18%，不可读 |
| `economy` | a cheerful market stall with a striped awning and colorful goods, a small neat stack of gold coins beside it, and a rising coral chart line on a small board behind | — |
| `feature` | a large magnifying glass held over a group of colorful puzzle pieces, with one bright coral puzzle piece standing out clearly, and a small checklist with tick marks beside it | — |
| `generous` | a smiling person handing a large coral heart-shaped gift box to another happy smiling person, with small sparkles and a few gold coins floating around them | — |
| `influence` | five tall rounded domino blocks standing upright on the ground with small dots on them, the leftmost block is bright coral and already tipping over onto the second block, the remaining four blocks are mint green, teal and soft yellow, showing a chain reaction | **返工版**：首版「人物 + 扩音器」造型怪异。返工前缀追加 `no people`、`only five objects, evenly spaced in a single horizontal row` |
| `maintain` | one healthy green plant with three big rounded leaves in a terracotta pot, and a small teal watering can beside it, with two tiny yellow sparkles above the plant | **返工版**：首版元素杂乱。返工前缀追加 `no people`、`only two objects side by side` |
| `obvious` | a big bright yellow light bulb with simple flat yellow triangular rays around it, floating above one large bright coral circle that stands out clearly among four plain grey circles | **返工版**：首版灯泡带洋红/粉色光晕（键控色残留）。返工前缀追加 `absolutely no magenta, no pink, no purple and no glow or halo anywhere in the image`，并把光线改为「平直黄色三角」 |
| `reduce` | a large downward-pointing coral arrow beside two stacks of cardboard boxes on the ground - a tall stack on the left and a much shorter stack on the right | — |

## 4. 从生成到入库

1. 生成 `1024x1024` 原图，按 lemma 命名（如 `raw/ability.png`）。
2. 跑后处理：
   ```
   python tools/illustrations/prepare_illustrations.py <原图目录> <输出目录>
   ```
   脚本会裁掉底部水印带、自动检测并键控背景、腐蚀+降采样抗锯齿、反混合边缘、裁白留边、输出 512×512 RGBA PNG，并打印每张的自检结论。
3. 编码为无损 WebP 落 `app/src/main/res/drawable-nodpi/illus_<lemma>.webp`：
   ```
   Image.open(png).convert("RGBA").save(target, "WEBP", lossless=True, method=6)
   ```
4. **目视联络表复核**：把 12 张合成到 `MintSurface` 底色上拼一张 4×3 联络表逐张看。自检只能抓背景色残留，抓不到光晕、构图碎裂这类问题；**目视是最终判据**。

## 5. 已知不足

- 风格仍有漂移：部分偏手绘柔光、部分偏扁平描边，未完全统一。
- 12 张仅服务占位词条（`PlaceholderWordCardSource` 的 12 个自撰词），真实词书配图的许可与生产是独立事项。
