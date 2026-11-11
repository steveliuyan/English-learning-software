# 决策：release 产物体积策略（ABI 拆分 + 资源压缩口径）

日期：2026-11-11
相关：F5-01 / F5-04、`docs/verification/2026-11-11-release-build-size-and-qa3.md`

## 背景（实测）

首次 `:app:assembleRelease` 产出 **321.9 MiB** 的 unsigned APK（`aapt` 口径的 zip 中央目录实测）。构成：

- `lib/` **192.8 MiB**：`libonnxruntime.so` 四份（x86_64 38.5 + x86 38.4 + arm64 32.1 + armeabi-v7a 22.7 MiB）。
- `assets/kokoro-production` **97.5 MiB**：Kokoro INT8 模型 92.4 MB + voices 28.2 MB。
- `assets/wordbooks` 仅 **7.5 MiB**（原始 45.9 MiB，deflate 后约 6:1）。
- dex 合计 16.4 MiB，res 3.8 MiB。

即体积问题与「词书 + 例句」无关，全部来自推理运行时与模型。

## 决定

1. **优先做 ABI 拆分，而不是先动 R8。** 目标形态：App Bundle（AAB）+ `splits.abi` 或 `bundle { abi { enableSplit = true } }`，正式分发只带 **arm64-v8a + armeabi-v7a**；x86 / x86_64 只留给本地调试构建（`debug` 保留四 ABI，模拟器仍可跑）。预期单机下载体积减少约 **77 MiB**（两个 x86 库），且不触碰任何业务代码。
2. **Kokoro 模型与 voices 保持随包**：它是离线发音的唯一来源，剔除即破坏「无网可用」红线；其 97.5 MiB 是既定成本。若后续要瘦身，只能走「首次使用时按需下载 + 哈希钉值」的独立课题，不在本轮。
3. **R8 / 资源压缩单开一轮，不在本批启用。** 理由：当前 `isMinifyEnabled = false`，开启需覆盖 Hilt、Room、Compose、Serialization 的 keep 规则并用真机回归五条主链；dex 合计仅 16.4 MiB，收益上限远小于 ABI 拆分，风险却更高。决策顺序：先 ABI 拆分（低风险、大收益），再评估 R8。
4. **词书资产的压缩方式不改**：45.9 MiB 已压到 7.5 MiB，无需 `noCompress` 例外。

## 影响

- 发布脚本与 QA 装机口径需从「装一个 universal APK」改为「装目标 ABI 的拆分产物」；`verifyReleaseArtifact` 的产物扫描要跟着指向拆分产物集合。
- 真机（arm64）与模拟器（x86_64）各走各的产物，不再共用一个 APK。
- 签名仍是 TODO-USER：本地无 keystore，产物为 unsigned；用户提供发布 keystore 后再补验签与 `apksigner verify` 证据。

## 实施结果（2026-11-11 实测）

实现：`app/build.gradle.kts` 新增 `splits { abi { isEnable = abiSplitsEnabled … } }`，开关是 Gradle 属性 `abiSplits`，**默认 false**——默认构建仍是四 ABI 的 universal APK，既有 QA 口令与模拟器 instrumented 形状不变。契约测试 `verification.ReleaseAbiSplitContractTest`（3 例）钉住「属性驱动 / 只发两块 arm ABI / R8 仍关」三条。

```bash
"$GRADLE" :app:assembleRelease -PabiSplits=true --no-daemon   # BUILD SUCCESSFUL in 1m 42s
"$GRADLE" verifyReleaseArtifact -PabiSplits=true --no-daemon  # 两份产物各自 secret-scan 干净
```

| 产物 | 字节 | MiB | 相对 universal 321.9 MiB |
|---|---|---|---|
| `app-arm64-v8a-release-unsigned.apk` | 185,930,228 | 177.3 | **−144.6 MiB（−45%）** |
| `app-armeabi-v7a-release-unsigned.apk` | 170,205,460 | 162.3 | **−159.6 MiB（−50%）** |
| （对照）universal `app-release-unsigned.apk` | 337,521,268 | 321.9 | — |

单机实际下载量比预估更好：省下的不只是两个 x86 库（约 77 MiB），`mergeReleaseNativeLibs` 也不再需要把四份库合并进同一个包。`verifyReleaseArtifact` 已改成「扫目录里每一个 APK」，拆分产物同样逐份过 secret-scan。
