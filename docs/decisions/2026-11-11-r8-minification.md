# 决策：release 启用 R8 混淆与资源压缩

- 日期：2026-11-11
- 前序：`docs/decisions/2026-11-11-release-artifact-size-and-abi-strategy.md`（决定 1 先做 ABI 拆分，决定 3 把 R8 单开一轮）
- 触发：用户明确「开」——ABI 拆分已落地，R8 这一轮现在做。

## 决定

1. **release 默认开启 R8 与资源压缩。** `isMinifyEnabled` 与 `isShrinkResources` 都由 Gradle 属性 `r8` 驱动，**默认 true**；`-Pr8=false` 产出未混淆的对照包，用于「某个库的 keep 规则漏了」时二分定位。两条绑定是刻意的：AGP 不接受只 shrink 不 minify。
2. **用 `proguard-android-optimize.txt`，不叠加 `-dontoptimize`。** optimize 档做方法内联与类合并，是 dex 收益的主要来源；代价是排障更难，因此保留 `SourceFile,LineNumberTable` 并归档 mapping。
3. **keep 规则只留三类真正会断的入口**：反射、JNI、按类名字符串实例化。逐条写在 `app/proguard-rules.pro` 并注明理由，禁止 `-keep class com.example.**` 这种一揽子规则——它把收益吃光，还让「哪个库其实不需要 keep」永远查不清。
4. **不写全局 `-ignorewarnings`**：需要的 `-dontwarn` 逐条列，这样真正的 missing class 仍然会让构建红。

## 实施（本轮实测）

```bash
"$GRADLE" :app:assembleRelease --no-daemon                        # R8 默认开：BUILD SUCCESSFUL in 3m 37s
"$GRADLE" :app:assembleRelease -PabiSplits=true --no-daemon       # 拆分产物：BUILD SUCCESSFUL in 2m 57s
"$GRADLE" verifyReleaseArtifact -PabiSplits=true --no-daemon      # 逐份 secret-scan：BUILD SUCCESSFUL in 31s
"$GRADLE" :app:testDebugUnitTest --rerun-tasks --no-daemon        # 367 suites / 2,717 tests / 0 fail / 0 skip
```

| 产物 | 开 R8 前 | 开 R8 后 | 变化 |
|---|---|---|---|
| universal `app-release-unsigned.apk` | 321.9 MiB | **310.7 MiB** | −11.2 MiB |
| `app-arm64-v8a-release-unsigned.apk` | 177.3 MiB | **166.1 MiB** | −11.2 MiB |
| `app-armeabi-v7a-release-unsigned.apk` | 162.3 MiB | **151.1 MiB** | −11.2 MiB |

`mapping.txt` 98.5 MB，落在 `app/build/outputs/mapping/release/`，发布时必须随包归档——没有它，混淆后的崩溃栈只能看到行号区间。

## 真机冒烟（arm64 真机 bf353dda）

release 产物是 unsigned（本地无发布 keystore，见 SP-E 决策）。冒烟做法：用 `apksigner.jar` + 本机 **debug keystore** 签一份同字节的 APK 装上，只用于验证「R8 有没有把运行时打断」，**不是交付产物**。

| 检查 | 结果 |
|---|---|
| `adb install` | Success |
| 冷启动 `BrandLaunchActivity → MainActivity` | `Status: ok`，`TotalTime: 567ms` |
| 崩溃缓冲 `logcat -b crash` | 空 |
| 首页渲染 | 正常（今日学习、连续打卡、「新学 3 个都已完成」= 词书资产解析 + Room 写入 + WorkManager 排程全跑通） |
| 查词页 | 正常打开，无异常 |

首页能画出「新学 3 个」这件事本身就是 R8 侧最强的信号：它要求 kotlinx.serialization 解析 `book.json`、Room 读写、Hilt 注入、WorkManager 调度四条链路同时存活。

**没验到的**：Compose 底部导航的 tab 在 `uiautomator` 里 bounds 为 `[0,0][0,0]`（语义树合并），`adb shell input tap` 点不中，所以「学习 / 阅读 / AI 助学 / 我的」四条主链的**逐屏**回归仍需人工点一遍；OCR（ML Kit / Tesseract4Android）与 TTS（ONNX Runtime）这两条重型原生链也没在真机上跑到。

## 风险与回退

- 回退开关是 `-Pr8=false`，一条命令，不用改代码；CI 的对照包也应该跑一遍这条，避免 keep 规则腐化。
- 若线上出现「只有 release 崩、debug 正常」，先看 `mapping.txt` 反解，再用 `-Pr8=false` 确认是 R8 而非逻辑缺陷。
- 签名仍是 TODO-USER：本轮产物 unsigned，`apksigner verify` 只能验本地 debug 签名的冒烟包。
