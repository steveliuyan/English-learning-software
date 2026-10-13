# 2026-10-13 阶段 4 F4-04 配对认证、备份密码与恢复 实现计划

**依据：** `docs/handoff/2026-10-13-f4-04-paired-restore-task.md`、Spec-04 F4-04、
`docs/decisions/2026-10-13-f4-04-crypto-and-pairing-design.md`（用户已批准 D1–D5，2026-10-13）。

**分支：** `stage-4-f4-04-paired-restore`。

## 提交切分与状态

| # | 提交 | 内容 | 状态 |
|---|---|---|---|
| 1 | docs(decisions) | 密码学/配对设计文档 + 依赖批准 | ✅ d3bf0fc（已批准） |
| 1b | build(deps) | Tink 1.21.0 + bcprov-jdk18on 1.81 入册（lockfile、台账、排除 security-crypto 捆绑的旧 tink-android、gradle.properties 内存参数） | 进行中 |
| 2 | feat(backup) | 配对协议域层：挑战签发状态机、迁移码编解码、配对校验器（纯 JVM，TDD） | 待做 |
| 3 | feat(backup) | 格式 v3：状态取值、pairing.json、签名作用域规范化、校验器版本分派（v2 冻结）；Keystore ECDSA 签名器、Argon2id/Tink AEAD 受保护导出 | 待做 |
| 4 | feat(backup) | 导入验证管线（Spec 顺序 fail-closed）、隔离临时区、回滚点、原子替换、提醒/索引重建、AI Key 清除 | 待做 |
| 5 | feat(ui) | BiometricPrompt 认证门、ZXing/CameraX 扫码、导出保护选项、恢复流程与二次确认 UI、AC4-06 无旁路契约测试；届时登记 zxing/camerax/biometric 依赖 | 待做 |
| 6 | docs | 验收记录（AC4-04/05/06 映射 + AC4-05 负例矩阵）+ 收口报告 | 待做 |

## 门禁（每步执行）

`:app:assembleDebug` → `:app:testDebugUnitTest`（基线 1118，不允许新失败类别）→
`:app:compileDebugAndroidTestKotlin`；detekt/ktlint 只报增量。

## 环境注意

- Gradle 本地 8.13 发行版路径；git 代理 127.0.0.1:3067；提交 `-c user.name=steveliuyan -c user.email=2021250388@qq.com`。
- 新增依赖必须三配置 `--write-locks`（本次已在 `gradle.properties` 提升 `org.gradle.jvmargs=-Xmx2048m -XX:MaxMetaspaceSize=1024m`，否则 daemon 在 dex/ksp 阶段堆内存抖动）。
- `androidx.security:security-crypto` 保留声明但排除其捆绑 `tink-android:1.8.0`（与 tink 1.21.0 类冲突；security-crypto 主源码无使用）。

## 范围红线（任务书重申）

不做合并恢复/云同步/WebDAV/F3-03；明文密码、`S`、KEK/DEK 永不落盘或入包/日志/错误文案；
哨兵测试扩展覆盖新 serializer；不改 F4-03 Salvage 导出既有行为；UI 接线级不打磨视觉。
