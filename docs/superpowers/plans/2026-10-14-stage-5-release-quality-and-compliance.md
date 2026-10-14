# 阶段 5「发布质量、可访问性与许可证治理」实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 证明应用具备可发布的可用性、离线可靠性、隐私边界、资源合规性，并建立可重复执行的发布前门禁。

**Architecture:** 阶段 5 覆盖五个可独立验收的子系统（质量门禁 / 性能可靠性 / 无障碍兼容 / 隐私许可 / 发布构建）。本计划把 SP-A（质量门禁与 AC 追踪矩阵）落到 bite-sized TDD 任务；SP-B…SP-E 各给里程碑与验收定义，其详细计划在各自开工时按同一模板补写（性能阈值、TalkBack 走查结果等无法在不实测的情况下预先写成代码，硬编码即占位符）。

**Tech Stack:** Gradle（Kotlin DSL）、JVM 单测（JUnit + kotlin.test）、bash + git grep（密钥扫描）、docs/verification（既有分阶段验证记录）。

**Spec:** `docs/specs/05-release-quality-and-compliance.md`（连同前置 `docs/specs/00`–`04`）

## Global Constraints

- 单测门禁基线：main tip `89a72e8` 上 **1322 全绿，只增不减**；detekt/ktlint 存在 ~1705 基线债，不作为验收门禁（见 `project-acceptance-gate` 交接记录），但新增文件不得引入新告警。
- 红线文件零触碰：`BackupCryptoPolicy` / `MigrationChallengePolicy` / `AndroidMigrationKey`。
- 密钥红线：API Key、Authorization Header、明文密码、设备私钥、挑战码/配对 QR 截图**一律不得进入** git、日志、导出包、报告（`.git/qa-scratch/` 为本地忽略区）。
- applicationId `com.example.englishlearning`；minSdk 26 / targetSdk 36；依赖全部走 `libs.versions.toml` + dependencyLocking（改依赖需 `--write-locks` 三任务流程，见环境备忘）。
- 提交身份：`git -c user.name=steveliuyan -c user.email=2021250388@qq.com commit`；推送用
  `git -c http.proxy=http://127.0.0.1:3067 -c https.proxy=http://127.0.0.1:3067 -c credential.interactive=false push`，推后 `ls-remote` 核验。
- 每个 AC 编号必须能指向一个自动化测试或一份可重复手工脚本（AC5-01）。

---

## 子项目划分与执行顺序

| 子项目 | 范围 | Spec 条目 | 产出 | 开工前置 |
|---|---|---|---|---|
| **SP-A 质量门禁与 AC 追踪矩阵** | F5-01 | AC5-01、AC5-05（扫描部分） | 本计划任务 1–5，代码级 | 无（立即开工） |
| **SP-B 性能与可靠性** | F5-02 | AC5-02 | 性能基线报告 + 可取消/超时契约测试 | SP-A 任务 5 后写详细计划 |
| **SP-C 可访问性与兼容性** | F5-03 | AC5-03 | TalkBack/动态字体/深色/小屏走查记录 + 修复轮 | 可与 SP-B 并行（设备 QA 分对话） |
| **SP-D 隐私、许可与发布审核** | F5-04 | AC5-04 | 「关于与开源许可」页 + 台账终审 + 发布说明 | SP-A 任务 3 后开工 |
| **SP-E 发布构建与签名** | F5-01 发布检查 | AC5-01（发布构建通过） | release 构建类型、签名、`checkReleaseReadiness` 聚合任务 | SP-A 完成后写详细计划 |

阶段 4 遗留（B3 API26–29 降级、B4 Salvage 拒绝、TalkBack 揭晓焦点、B5 真实 Profile、T2 内容级）分别归入 SP-C（B3、TalkBack 焦点）与 SP-B（B4、B5、T2），在各子计划中作为首批任务承接，不再另开轮次。

---

## SP-A：质量门禁与 AC 追踪矩阵（代码级任务）

### Task 1: AC 追踪矩阵文档骨架 + 覆盖校验器（先红后绿）

**Files:**
- Create: `docs/verification/ac-traceability-matrix.md`
- Modify: `build.gradle.kts`（root，`verifyThirdPartyNotices` 之后注册新任务）
- Test: `app/src/test/java/com/example/englishlearning/verification/AcIdPatternTest.kt`

**Interfaces:**
- Produces: 矩阵表格式 `| AC | 验证方式 | 证据 |`（任务 2 的校验脚本按此解析）；Gradle 任务 `verifyAcTraceabilityMatrix`（任务 5 聚合依赖它）。

- [ ] **Step 1: 写暴露测试（正则口径先钉死）**

```kotlin
// app/src/test/java/com/example/englishlearning/verification/AcIdPatternTest.kt
package com.example.englishlearning.verification

import org.junit.Test
import kotlin.test.assertEquals

/** AC5-01：矩阵必须覆盖 specs/00–04 的全部 AC 编号；口径与校验脚本一致。 */
class AcIdPatternTest {
    private fun ids(text: String): Set<String> =
        Regex("""AC[0-4]-\d{2}""").findAll(text).map { it.value }.toSet()

    @Test
    fun `pattern captures stage 0 to 4 ids only`() {
        val sample = "AC0-01 AC4-08 AC5-03 ac1-22 AC1-3"
        assertEquals(setOf("AC0-01", "AC4-08"), ids(sample))
    }
}
```

- [ ] **Step 2: 跑测试确认绿（口径测试本就应绿；红在下一步的矩阵缺失）**

Run: `./gradlew :app:testDebugUnitTest --tests "*AcIdPatternTest*"`
Expected: PASS（此测试钉口径；若 FAIL 说明正则写错，先修口径）

- [ ] **Step 3: 建矩阵骨架，行从 specs 机械生成**

```markdown
# AC 追踪矩阵（阶段 0–4 → 证据）

> AC5-01：每行必须有非空「证据」列（自动化测试 FQCN#method、docs/verification 记录、
> 或 docs/handoff 验收/走查记录）。缺证据的行会被 `verifyAcTraceabilityMatrix` 拒绝。

| AC | 验证方式 | 证据 |
|---|---|---|
| AC0-01 | 自动化 | （待填） |
…（AC0-01 … AC4-08 共 50 行，按 specs 00–04 逐一列出）
```

- [ ] **Step 4: 在 root build.gradle.kts 注册校验任务**

```kotlin
tasks.register("verifyAcTraceabilityMatrix") {
    group = "verification"
    description = "AC5-01: every AC0..AC4 id in docs/specs/00-04 appears in the matrix with evidence."
    val specs = fileTree("docs/specs") { include("0[0-4]-*.md") }
    val matrix = layout.projectDirectory.file("docs/verification/ac-traceability-matrix.md")
    inputs.files(specs)
    inputs.file(matrix)
    doLast {
        val idRegex = Regex("""AC[0-4]-\d{2}""")
        val required = specs.files.flatMap { idRegex.findAll(it.readText()).map { m -> m.value } }.toSet()
        val rows = matrix.asFile.readLines().filter { it.startsWith("| AC") }
            .map { line -> line.split('|').map { it.trim() } }
        val covered = rows.filter { it.size >= 5 && it[3].isNotEmpty() && it[3] != "（待填）" }
            .associate { it[1] to it[3] }
        val errors = (required - covered.keys.toSet()).sorted().map { "缺少证据: $it" } +
            rows.filter { it.size >= 5 && (it[3].isEmpty() || it[3] == "（待填）") }.map { "证据为空: ${it[1]}" }
        check(errors.isEmpty()) { errors.joinToString("\n") }
        logger.lifecycle("AC matrix covered ${covered.size} ids with evidence.")
    }
}
```

- [ ] **Step 5: 跑校验确认红（50 行全部缺证据）**

Run: `./gradlew verifyAcTraceabilityMatrix`
Expected: FAIL，输出列出全部缺证据 AC

- [ ] **Step 6: Commit（骨架与工具先行）**

```bash
git add docs/verification/ac-traceability-matrix.md build.gradle.kts app/src/test/java/com/example/englishlearning/verification/AcIdPatternTest.kt
git -c user.name=steveliuyan -c user.email=2021250388@qq.com commit -m "test(stage5): AC 追踪矩阵骨架与校验器（AC5-01，先红）"
```

### Task 2: 用真实证据填满矩阵（批量回填）

**Files:**
- Modify: `docs/verification/ac-traceability-matrix.md`

**Interfaces:**
- Consumes: Task 1 的表格式与校验任务。

- [ ] **Step 1: 逐 AC 收集证据**

检索顺序（每条 AC 至少落一个引用）：
1. `app/src/test` / `app/src/androidTest` 中同 AC 编号的测试注释（各阶段实现均按 `F?-??/AC?-??` 标注，如 `grep -rn "AC4-07" app/src`）；
2. `docs/handoff/2026-10-1*-stage-*-acceptance-record.md` 与 `2026-10-1*-f3-03-acceptance-record.md` 的 AC 映射节；
3. `docs/verification/*` 分阶段验证记录；
4. 设备走查结论（`2026-10-14-batched-device-qa-report.md` §R1–R7）。

- [ ] **Step 2: 无法自动化的 AC 登记为手工脚本**

对纯 UI/设备项（如 AC3-04 真机 OCR 降级），「验证方式」填 `手工`，「证据」填走查记录文档路径 + 日期；后续 SP-C 若补上自动化再回改。

- [ ] **Step 3: 校验转绿**

Run: `./gradlew verifyAcTraceabilityMatrix`
Expected: `AC matrix covered 50 ids with evidence.`

- [ ] **Step 4: Commit**

```bash
git add docs/verification/ac-traceability-matrix.md
git -c user.name=steveliuyan -c user.email=2021250388@qq.com commit -m "docs(stage5): AC 追踪矩阵全量回填证据（AC5-01）"
```

### Task 3: 负向测试清单审计（Spec 点名的 10 类）

**Files:**
- Create: `docs/verification/negative-test-audit.md`
- Create（审计后按需）: 缺口对应的 `app/src/test/...` 新测试文件

**Interfaces:**
- Produces: 缺口清单 → SP-A 任务 4 / SP-B 的输入。

- [ ] **Step 1: 逐类定位现有测试**

| 负向类别 | 已知锚点（先 grep 验证存在） |
|---|---|
| 重复反馈 | `grep -rn "不可重复计数\|duplicate" app/src/test --include=*.kt` |
| 跨日/时区 | 今日计划快照相关测试（阶段 1） |
| 断网 | AI 请求失败映射测试（阶段 2） |
| AI 错误 | Profile/endpoint 错误类别测试 |
| AI Key 脱敏 | `AiProfileSecret`、日志脱敏测试 |
| 缓存篡改 | 资产哈希/代次校验测试（阶段 3） |
| 图片未确认上传 | F3-03 确认门测试（`PhotoTranslationPermissionContractTest` 等） |
| 备份认证失败 | `DevicePairedRestoreE2ETest`（篡改拒绝）、pairing verifier 单测 |
| 导入原子性 | `SnapshotSwapRestoreCommitGateway` 回滚测试、F4-03 导入测试 |
| （补）恢复后重启约束 | AC4-07 相关维护实例测试 `RoomFormalDataHandleCloseSemanticsTest` |

- [ ] **Step 2: 缺口按 TDD 补**

每个缺口：先写暴露测试（红）→ 最小实现或纯测试补齐（绿）→ 单独提交。发现的实现缺陷按 D-x 登记进 QA 报告，不得静默改行为。

- [ ] **Step 3: 审计文档落盘并提交**

`negative-test-audit.md` 每行：类别 / 覆盖测试 FQCN / 结论（已覆盖-自动化 / 已覆盖-设备 / 缺口已补 / 移交 SP-x）。

```bash
git add docs/verification/negative-test-audit.md app/src/test
git -c user.name=steveliuyan -c user.email=2021250388@qq.com commit -m "test(stage5): 负向测试十类审计与缺口补齐（F5-01）"
```

### Task 4: 密钥扫描门禁脚本（git 跟踪内容 + 构建产物）

**Files:**
- Create: `scripts/secret-scan.sh`
- Test: `scripts/secret-scan-selftest.sh`

**Interfaces:**
- Produces: `scripts/secret-scan.sh`（exit 0 = 干净；非 0 = 命中并打印 `文件:行`），任务 5 聚合调用。

- [ ] **Step 1: 先写自测（红）**

```bash
# scripts/secret-scan-selftest.sh
set -eu
TMP=$(mktemp -d)
printf 'Authorization: Bearer abcdefghijklmnop\n' > "$TMP/dirty.txt"  # secret-scan-allowlist
printf 'normal line\n' > "$TMP/clean.txt"
if SECRET_SCAN_EXTRA_PATHS="$TMP/dirty.txt" bash "$(dirname "$0")/secret-scan.sh" >/dev/null 2>&1; then
  echo "FAIL: dirty input not caught"; exit 1
fi
echo "PASS: scanner catches planted secret"
```

- [ ] **Step 2: 跑自测确认红（脚本还不存在）**

Run: `bash scripts/secret-scan-selftest.sh` → Expected: FAIL/报错

- [ ] **Step 3: 实现扫描器**

```bash
# scripts/secret-scan.sh —— AC5-05：跟踪内容与解包产物零密钥明文
set -eu
cd "$(git rev-parse --show-toplevel)"
PATTERN='Authorization:[[:space:]]*(Bearer|Basic)|[?&]api_?key=[A-Za-z0-9._-]{16,}|sk-[A-Za-z0-9]{20,}|BEGIN (RSA |EC )?PRIVATE KEY|ELPAIR1\|'
status=0
# 1) git 跟踪文件（自测夹具目录以 allowlist 行内注释豁免）
if git grep -nIE "$PATTERN" -- ':!scripts/secret-scan*' | grep -v 'secret-scan-allowlist'; then
  echo "SECRET SCAN HIT (tracked files)"; status=1
fi
# 2) 构建产物（存在才扫）
for apk in app/build/outputs/apk/debug/app-debug.apk app/build/outputs/bundle/release/*.aab; do
  [ -f "$apk" ] || continue
  d=$(mktemp -d); unzip -qq "$apk" -d "$d" 2>/dev/null || true
  if grep -rIlE "$PATTERN" "$d" | grep -v 'secret-scan-allowlist'; then
    echo "SECRET SCAN HIT ($apk)"; status=1
  fi
  rm -rf "$d"
done
# 3) 自测注入路径（供红测试使用）
if [ -n "${SECRET_SCAN_EXTRA_PATHS:-}" ]; then
  grep -nHE "$PATTERN" $SECRET_SCAN_EXTRA_PATHS | grep -v 'secret-scan-allowlist' && status=1 || true
fi
exit $status
```

注：`ELPAIR1|` 防挑战码明文入库；业务源码中确需出现字面样例时行尾加 `secret-scan-allowlist`。

- [ ] **Step 4: 自测转绿 + 全仓真扫**

Run: `bash scripts/secret-scan-selftest.sh && bash scripts/secret-scan.sh`
Expected: `PASS: ...` 且全仓扫描 exit 0（若有历史命中：清除或豁免注释，禁止直接放行）

- [ ] **Step 5: Commit**

```bash
git add scripts/secret-scan.sh scripts/secret-scan-selftest.sh
git -c user.name=steveliuyan -c user.email=2021250388@qq.com commit -m "test(stage5): 密钥扫描门禁脚本（AC5-05）"
```

### Task 5: 发布前聚合检查任务 `checkReleaseReadiness`

**Files:**
- Modify: `build.gradle.kts`（root）

**Interfaces:**
- Consumes: `verifyThirdPartyNotices`（既有）、`verifyAcTraceabilityMatrix`（Task 1）、`:app:testDebugUnitTest`、`scripts/secret-scan.sh`；SP-E 会把 release 构建与签名校验挂到这里。

- [ ] **Step 1: 注册聚合任务**

```kotlin
tasks.register("checkReleaseReadiness") {
    group = "verification"
    description = "Stage-5 F5-01 release gate: notices + AC matrix + unit tests + secret scan."
    dependsOn("verifyThirdPartyNotices", "verifyAcTraceabilityMatrix", ":app:testDebugUnitTest")
    doLast {
        val rc = providers.exec { commandLine("bash", "scripts/secret-scan.sh") }.result.get()
        check(rc.exitValue == 0) { "secret-scan 命中：见上方输出（AC5-05 发布阻断）" }
    }
}
```

- [ ] **Step 2: 全链跑通**

Run: `./gradlew checkReleaseReadiness`
Expected: 全绿（1322+ 单测、两校验、扫描 exit 0）

- [ ] **Step 3: 门禁登记 + 提交推送**

把 `checkReleaseReadiness` 写入阶段 5 Spec「发布前检查」映射与本计划完成记录；

```bash
git add build.gradle.kts docs/superpowers/plans/2026-10-14-stage-5-release-quality-and-compliance.md
git -c user.name=steveliuyan -c user.email=2021250388@qq.com commit -m "build(stage5): checkReleaseReadiness 聚合发布门禁（F5-01）"
git -c http.proxy=http://127.0.0.1:3067 -c https.proxy=http://127.0.0.1:3067 -c credential.interactive=false push origin main
```

---

## SP-B：性能与可靠性（里程碑，开工时补代码级计划）

1. 测量脚本 `scripts/perf-baseline.sh`：`am start -W` 冷启动、instrumentation 计时用例覆盖 Spec 点名六项（离线冷启动、今日计划读取、词卡翻页、查词、学习事件写入、大型备份校验/恢复）；在 emulator-5574（最低支持口径）与真机 bf353dda 各测 5 次取中位数，产出 `docs/verification/perf-baseline-2026-10.md` 并**回填阈值**。
2. 契约测试：所有 AI/网络调用可取消 + 超时 + 不阻塞 UI（挂接现有 AiClient 测试基类，补「取消后无半写文章版本」「重试不重复学习事件」两用例）。
3. 承接阶段 4 遗留：B4 Salvage 包拒绝、B5 真实 Profile 端到端、T2 内容级抓包。
4. 验收 = AC5-02：飞行模式下学习/复习/缓存阅读/TTS 全通 + AI 入口给出离线态不卡 UI（connected 用例 + 手工核）。

## SP-C：可访问性与兼容性（里程碑）

1. TalkBack 走查脚本化清单（AC5-03 六条链路），承接「词卡揭晓按钮焦点顺序」遗留；缺陷登记 D-x 后修复轮。
2. 动态字体（最大档）+ 小屏 360dp + 深色模式截图矩阵（含 D-2）。
3. 发音控件四要素（播放/暂停/重播/变速）与无 TTS 引擎文字态。
4. 承接 B3：API 26–29 镜像 BiometricPrompt 降级（需装 android-28 系统镜像）。

## SP-D：隐私、许可与发布审核（里程碑）

1. 「关于与开源许可」页（TDD：入口可达 + 台账渲染 + testTag），数据源 = `docs/third-party-notices.md` 打包进 assets，`verifyThirdPartyNotices` 已保证字段完整。
2. 台账终审：逐条核对版本/许可/数据流/商业结论（Kokoro、ML Kit、Tesseract4Android、zxing、Tink、BouncyCastle、词书数据为重点）。
3. 发布说明草稿（无账户、无自动云备份、AI 自配、Key 不导出、图片发送需确认）。
4. 验收 = AC5-04。

## SP-E：发布构建与签名（里程碑）

1. `release` buildType（minify + proguard 规则）、签名配置走本地 `keystore.properties`（不入库）；`signingReport` 与 `apkanalyzer` 验证。
2. AAB 构建纳入 `checkReleaseReadiness`；release APK 过 Task 4 扫描。
3. 发布构建上重跑 AC5-01 抽样自动化。

---

## Self-Review 记录

- Spec 覆盖：F5-01→任务 1–5 + SP-E；F5-02→SP-B；F5-03→SP-C；F5-04→SP-D；AC5-01…05 各有归属；发布阻断四条分别被任务 4（密钥）、Task 2 证据行（备份原子性）、SP-B（离线不阻塞）、SP-D（许可）承接。
- 占位符扫描：SP-A 内无 TBD；SP-B…E 的「回填阈值」「按需补测试」为显式的实测依赖声明，非任务内占位。
- 口径一致：`AC[0-4]-\d{2}` 正则、矩阵表列序（AC/验证方式/证据）、`checkReleaseReadiness` 依赖名在任务 1/4/5 间已互对。

## SP-A 完成记录（2026-10-14 本会话内联执行）

- 任务 1 ✅：`AcIdPatternTest`（口径正则）+ 矩阵骨架 + root `verifyAcTraceabilityMatrix`（先红：证据全空）。提交 `372b18f`。
- 任务 2 ✅：51 条 AC 证据全量回填（specs 修订后总数为 51，非计划初稿的 50）；顺带修复校验器两处口径缺陷（Gradle PatternSet 不支持 `[0-4]` 字符类、表头行误判为数据行）与台账真实缺项（mlkit-vision-common 商业分发结论）。提交 `c553698`。
- 任务 3 ✅：`docs/verification/negative-test-audit.md`——十类负向场景全部映射证据；AC3-02/03 语义候选复核达意，AC4-01 如实降级为部分覆盖。**暴露两个需用户拍板的功能级缺口**：F4-01 按日历史统计（含文章状态/收藏/错词）未实现；AC4-05「已使用」配对凭据无消费台账。提交 `dfe91de`。
- 任务 4 ✅：`scripts/secret-scan.sh` + 自测（先红后绿；ELPAIR1 模式收紧为 `ELPAIR1\|[A-Za-z0-9_-]{16,}` 以区分格式文档与真实挑战码；行尾 `secret-scan-allowlist` 豁免机制启用一处：AiProviderKind KDoc）。全仓真扫 exit 0。
- 任务 5 ✅：root `checkReleaseReadiness` 聚合（notices+矩阵+1324 单测+密钥扫描）全绿；已登记入阶段 5 Spec F5-01。
