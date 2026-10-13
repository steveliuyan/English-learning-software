# 2026-10-13 F4-04 密码学与配对协议设计决策

**状态：待用户批准。批准前不得编写任何实现代码。**

本文档是 F4-04（配对认证、备份密码与恢复）全部密码学选择与配对协议的唯一批准来源。实现不得偏离本文确定的库、算法、参数与协议；如需偏离，必须先修订本文并重新获得批准。

依据的强制约束：

- AGENTS.md"备份与恢复"六条与"AI 与隐私安全"节；
- `docs/specs/04-history-reminders-backup-and-security.md` F4-04 与 AC4-04/05/06；
- `docs/superpowers/specs/2026-10-11-logical-backup-export-design.md` 第 4/依赖与待审决策节（"安全密码学选择必须基于受维护标准库调研……不得手写算法或用无密钥哈希冒充真实性"）;
- `docs/decisions/2026-10-13-f4-03-unprotected-export-scope.md`（v2 Salvage 包的诚实状态不被放宽）。

## 0. 决定摘要

| 编号 | 决定 | 选型 |
|---|---|---|
| C1 | 迁移签名密钥 | Android Keystore ECDSA secp256r1(P-256)，不可导出，强制用户认证后签名；平台 `java.security.Signature("SHA256withECDSA")`，零新依赖 |
| C2 | 配对协议 | 旧设备一次性、短时有效"迁移挑战"：Keystore 私钥签名包内配对块；二维码携带仅码内存在的秘密 `S`，包内仅存其承诺 `SHA-256(S)`；新设备扫码完成挑战响应 |
| C3 | 备份密码 KDF | Argon2id（RFC 9106），Bouncy Castle `Argon2BytesGenerator`（lightweight API）；V1 固定参数 m=64 MiB、t=3、p=1、salt=16 B、输出 32 B |
| C4 | 备份加密 AEAD | AES-256-GCM，Google Tink（`com.google.crypto.tink:tink` 1.21.0）；密钥层级：Argon2id 派生 KEK 包裹随机 DEK，逐条目加密 |
| C5 | 二维码编解码 | ZXing `com.google.zxing:core` 3.5.3（Apache-2.0，纯 JVM 无 GMS）+ CameraX 取景分析；新设备仅允许相机实时扫码（裁决点 D2） |
| C6 | 用户确认 | `androidx.biometric` BiometricPrompt，AllowedAuthenticators = BIOMETRIC_STRONG ∣ DEVICE_CREDENTIAL（API 30+），API 26–29 降级路径见 §3.3 |
| C7 | 格式版本 | 新包格式 v3 承载真实 `signature/authentication/encryption` 状态；v2 Salvage 规则逐字节冻结，导入端对 v2 一律返回"不支持认证恢复"固定类别 |
| C8 | 恢复语义 | 仅"替换本机数据"；隔离临时区全链验证 → 可验证回滚点 → 用户二次确认 → 原子切换；任一失败 fail-closed、正式数据零写入 |

## 1. 威胁模型

### 1.1 保护目标

1. **来源认证**：只有旧设备主人通过生物识别/系统凭据确认后签发的配对挑战，才能授权一次恢复。攻击者仅拿到备份文件、或仅拿到二维码/截图，都不能完成恢复。
2. **完整性与真实性**：包内任何 payload、资源、manifest、配对块字节的篡改必须被检测并拒绝（签名 + 结构摘要 + AEAD 标签三层）。
3. **机密性（可选）**：设置备份密码后，离线拿到包文件的攻击者在合理时间内无法在目标设备上恢复明文（内存硬 KDF + AES-256-GCM）。
4. **恢复安全**：验证通过前正式数据零写入；验证失败清理临时区；替换操作有回滚点。
5. **凭据隔离**：API Key、Keystore 私钥、配对秘密 `S`、KEK/DEK 明文永不进入包、日志、错误文案、剪贴板。

### 1.2 攻击者能力（必须防住）

- 修改/截断/重排/重复 ZIP 条目；构造超大声明、高解压比、Zip Slip、路径穿越、畸形 JSON/UTF-8（现有校验器已覆盖，v3 沿用并扩展）。
- 用自建包冒充（含伪造签名/摘要/配对字段）。
- 重放旧包+旧二维码（过期检查拒绝）；用截获的二维码给不同的包配对（nonce/承诺/摘要绑定拒绝）；从包文件反推二维码（秘密 `S` 不在包内，拒绝）。
- 弱口令字典攻击离线包（Argon2id 内存硬提高单次猜测成本；见 1.3 残余风险）。
- 构造恶意"配对 UI"诱导旧设备主人对攻击者的包签名 —— 本协议无法完全防御社工路径，靠 §2.5 的摘要人工比对提示缓解（明示于 UI 文案）。

### 1.3 非目标与残余风险（如实声明，写入 UI/文档）

- **本方案不防**：旧设备主人已被胁迫授权；目标设备被 root/恶意系统；设备时钟被人为拨快使过期检查失效；软件 Keystore（无硬件绑定）设备上私钥被提取；"在配对有效期内同时窃取二维码图像和包文件"的窃取-转发攻击（有效期压缩窗口，不能根除）。
- **已知协议级局限**：单程消息流（旧→新）不含活体回传。彻底消除截图转发需要双设备双向扫码的挑战-响应升级（见 §2.6 U2），列入升级策略而非 V1。
- 密码强度由用户决定：≥10 字符只是下限，不承诺高熵；UI 必须诚实说明"忘记备份密码即不可恢复"（AC4-06）。

## 2. 配对协议（C2）

### 2.1 角色与实体

- **旧设备**：持有 Keystore 迁移签名密钥 `K`（长期、不可导出）；执行用户认证；生成一次性挑战；导出并签名（可选加密）备份包。
- **新设备**：选择包文件；扫描二维码；本地验证全链（无任何网络）；执行替换恢复。
- **挑战字段**：
  - `N`：128-bit 随机 nonce（`SecureRandom`），每次挑战唯一；
  - `issuedAt` / `expiresAt`：Unix 秒，**有效期固定 30 分钟**；
  - `K.pub`：X.509 SubjectPublicKeyInfo 编码的 P-256 公钥；
  - `S`：128-bit 会话秘密，**只存在于二维码，不写入包、不落盘**；
  - `C = SHA-256(S)`：秘密承诺，**只存在于包内配对块，不出现在二维码**；
  - `D`：包 manifest 规范摘要（现有 `payloadSha256` 行式规范化的扩展，见 §5.2）。

### 2.2 消息流（一次换机会话）

```
旧设备                                          新设备
──────                                          ──────
1. 用户认证（BiometricPrompt / 设备凭据）
2. 生成 N、S；作废旧机当前未用挑战
3. 组装快照→（可选）Argon2id+AEAD 加密
4. 写 pairing.json（pub,N,issuedAt,expiresAt,C,
   sig = Sign_K(签名作用域字节)）并打包
5. 展示二维码 QR = ELPAIR1|b64u(pub)|b64u(N)
   |b64u(S)|issuedAt|expiresAt        ──扫码──▶ 6. 解析 QR；选择/接收包文件
                                              7. 验证：QR.pub==包.pub ∧ QR.N==包.N
                                                 ∧ now<expiresAt ∧ SHA256(QR.S)==包.C
                                                 ∧ 签名验证 ∧ 摘要/结构/AEAD/…（§6 顺序）
                                              8. 全通过 → 回滚点 → 二次确认 → 替换
```

安全性质：

- **仅得文件**：无法恢复。包内无 `S`，攻击者重制二维码时 `SHA256(S)≠C`；伪造配对块需要 `K` 私钥签名。
- **仅得二维码（含截图转发）**：无法恢复任何"不属于该挑战"的包（`N`/`pub`/摘要绑定拒绝）；对原包的恢复还须在 30 分钟窗口内同时持有文件，且物理上要求旧设备主人在场授权在先。
- **重放**：`N` 由 128-bit 随机保证全局唯一；旧设备侧状态机保证"同时至多一个有效挑战"，生成新挑战立即作废前一个。新设备侧无法感知另一台设备的使用情况，故"单次使用"由旧设备发放语义 + `expiresAt` 共同封顶——此局限如实写入验收记录。
- **时钟注记**：过期判定使用新设备墙钟；在验证开始时一次性锚定（`验证会话启动时刻 < expiresAt`），开始后不因时长中断。设备时钟被故意拨快属 1.3 残余风险。

### 2.3 旧设备配对状态机（域层纯 Kotlin，JVM 测试锚定）

```
Idle ──requestPairing()──▶ Authenticating ──authSucceeded──▶ Issuing(生成N,S,时间戳)
  ▲                            │authFailed/cancelled──▶ Idle（零发放）
  └──challengeConsumed/invalidated── Issued(challenge{N,S,issuedAt,expiresAt,boundDigest})
Issued ──requestPairing()──▶ Authenticating（旧挑战立即作废：invalidateReason=Superseded）
Issued ──expiresAt 经过──▶ Expired
```

- 状态机对每次转换输出纯数据事件；作废、过期、被取代均不可再签发对应 `N` 的签名。
- 发放台账（Room 新表 `migration_pairing_ledger`）：仅存 `N`、`pub` 指纹、`issuedAt`、`expiresAt`、`boundDigest`、状态；**不存 `S`**（`S` 仅在内存与二维码中存活）、不存任何密钥材料。台账用于审计与 UI"当前有效挑战"展示。

### 2.4 与"认证要求"的逐条对应

| Spec/AGENTS 要求 | 本协议机制 |
|---|---|
| 旧设备一次性、短时有效挑战响应 | `N` 唯一 + 30 分钟 TTL + 状态机单活跃挑战（§2.3） |
| 本地解锁/生物识别/系统凭据确认后才生成 | Keystore 密钥 `setUserAuthenticationRequired(true)`，未认证则签名操作在平台层直接失败（§3） |
| 静态码不能替代 | 挑战含每会话随机 `N`、`S` 与时间戳，无静态值 |
| 截图不能替代 | `expiresAt` 窗口 + `S` 秘密 + 包-码三元绑定（`pub/N/C`）；残余风险见 §1.3/§2.6 U2 |
| 仅包内公钥不构成认证 | 公钥单独不足：验证还必须持有仅经认证后发放、不经包传输的二维码秘密 `S` 与活体扫码事实 |

### 2.5 UI 活体提示（缓解非正式转发）

旧设备展示二维码的同屏显示 `expiresAt` 倒计时与 `D` 前 8 位 base32；新设备验证开始时显示包 `D` 前 8 位并提示"请与旧设备屏幕核对"。用户肉眼比对是两个无网络通道设备间唯一可行的活体性补充，作为提示实现、不作硬校验（裁决点 D2）。

### 2.6 升级策略（不在 V1 实现，写为路线图）

- **U1 参数集版本化**：KDF/AEAD/曲线参数以算法标识字符串记录在包内（`aes-256-gcm/argon2id-v1`），校验器对每个格式主版本只接受登记过的精确算法串；未来调整参数=新算法串或新格式版本，永不静默放宽。
- **U2 双向活体配对**：新设备先展示挑战码（含其选定的包 `D`），旧设备认证后扫码并签发"对该 `D` 的授权响应"二维码，彻底封堵截图转发；届时以 v4 格式承载。
- **U3 Keystore Attestation**：对 `K` 引入证书链验证以剔除软件 Keystore 设备（依赖远程 attestation 根，V1 明确不做网络校验）。
- **U4 密钥轮换**：`K` 支持按指纹轮换（台账记录多把公钥），旧包验证不依赖私钥存续，轮换无数据失效风险。

## 3. 签名方案（C1）

### 3.1 密钥与算法

- 密钥：AndroidKeyStore EC **secp256r1**，`KeyPairGenerator.getInstance("EC", "AndroidKeyStore")`；别名经 `KeyAliasEntity`（`purpose = "backup-migration-signer"`）登记，复用现有 Keystore 别名管理惯例。
- 用途约束：`PURPOSE_SIGN | PURPOSE_VERIFY`；曲线 `ECPairGenParameterSpec`/P-256；**不可导出、私钥永不离开 Keystore**（本方案唯一需要"不可导出私钥"的场景，故不使用现有 AES SecretKey 惯例而新建 EC 密钥路径）。
- 签名：`Signature("SHA256withECDSA")`，DER 编码，平台实现，无新依赖。验签使用包内/码内 SPKI 公钥经 `KeyFactory("EC")` 构造，JVM 测试与设备共用同一代码路径。
- 拒绝清单：非 P-256 曲线、非 DER、签名作用域外字节参与、任何"哈希冒充签名"。

### 3.2 签名作用域（覆盖"签名覆盖范围"AC）

`sig` 覆盖的规范字节 = 固定域分隔前缀 `elpair-v3/` + manifest 规范化字节，manifest 规范化规则沿用包内既有确定性序列化惯例（键序稳定、UTF-8、无多余空白），**纳入**：`formatVersion`、`createdAt`、`scope`、全部 `entries`（含密文条目的 size/SHA-256）、`payloadSha256`、`encryption` 块（如存在）、`pairing` 块的除 `sig` 外全部字段（`pub/N/issuedAt/expiresAt/C/algIds`）。篡改任一 payload/资源/manifest/配对字段 → 结构摘要或签名至少一层失败（AC4-05 负例矩阵逐字段断言）。

### 3.3 用户认证绑定

- `KeyGenParameterSpec.setUserAuthenticationRequired(true)`。
- **API 30+**：`setUserAuthenticationParameters(30, AUTH_BIOMETRIC_STRONG | AUTH_DEVICE_CREDENTIAL)`；BiometricPrompt 的 `setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)` 与之精确匹配（生物识别不可用设备走凭据降级——任务书要求）。
- **API 26–29**：`setUserAuthenticationValidityDurationSeconds(30)`；平台此区间无法限定认证器种类（锁屏凭据/弱生物均可解锁），如实记录为设备能力边界；UI 流程仍是"必须先认证才可能签名"。
- 前置：设备未设置安全锁屏时 Keystore 生成密钥抛异常 → 固定失败类别 `PairingAuthUnavailable`，UI 引导去系统设置，不提供无认证签名旁路。
- 认证窗口语义：认证通过后 30 秒内允许"签发挑战+签名导出"连续操作；超时后任何再签名都必须重新认证。

## 4. 备份密码与 KDF（C3）

- 算法：**Argon2id**（RFC 9106，PHC 竞赛胜出，现时密码哈希最佳实践共识——见 §7 调研引用），经 Bouncy Castle `org.bouncycastle.crypto.generators.Argon2BytesGenerator`。Android 上**只使用 lightweight API，不注册 JCE Provider**（避免与平台内嵌 BC 的 provider 冲突，这是 Android 使用 BC 的标准惯例）。
- **V1 固定参数**（校验器逐字段精确匹配，任何偏离 → `UnsupportedCryptoParameters`，fail-closed）：
  - salt：16 字节 `SecureRandom`，存于 manifest `encryption.kdf.salt`；
  - memory：**65536 KiB（64 MiB）**，iterations：**3**，parallelism：**1**——高于 OWASP 密码哈希下限（19 MiB/2/1）且为 2016 年后主流 Android 设备（≥2 GB RAM）可稳定承受；lower bound 由生成端与校验器双向强制，禁止弱参数包；
  - 输出：32 字节 KEK；
  - 密码输入：NFKC 规范化，10–128 字符（上限防 KDF 放大攻击），`CharArray` 处理、派生完成后立即清零；明文密码绝不落盘、不进日志/错误/剪贴板。
- 备选 scrypt（N=2^15, r=8, p=1）仅作为 U1 式算法串未来的可登记替代，V1 不实现双算法（参数面最小化）。

## 5. 加密方案与密钥层级（C4）

### 5.1 层级

```
密码 ──Argon2id(salt)──▶ KEK(32B, 仅内存, 即用即清)
随机 DEK(32B) ──AES-256-GCM(KEK, IV_dek, AAD=规范encryption块字节)──▶ wrappedDek{iv,ct} ─▶ manifest.encryption
每条目明文 ──AES-256-GCM(DEK, 每条目独立 12B 随机 IV, AAD=规范条目字节)──▶ 存储字节 = iv‖ct‖tag（16B 标签）
```

- Tink `AesGcmJce`/known-keyset `AES_GCM`：**密钥 32 字节、IV 12 字节每消息全新随机（`SecureRandom`）、96-bit 认证标签**；Tink 的 AEAD 接口保证 nonce/tag 处理不可误用。
- AAD 绑定：条目密文的 AAD = `formatVersion|N|entryPath` 规范串——把密文钉死在"本次配对的本次会话的具体路径"上，防条目跨包搬移/裁剪攻击。DEK 包裹的 AAD = `encryption` 块规范字节，防参数替换。
- Nonce 复用安全论证：DEK 每包随机新生成，单包内条目数 ≤10 000，远小于随机 96-bit IV 的生日界；DEK 不跨包复用，密钥整体唯一。
- 加密对象是"存储字节"：manifest 的 `entries[].byteSize/sha256` 记录**密文实际字节**，与现有校验器的逐条目结构验证完全同构；明文完整性由 AEAD 标签保证（解密失败即 `DecryptionFailure`/篡改类别）。

### 5.2 无密码的 v3 包

`encryptionStatus = "none"` 的 v3 包：仅签名+配对认证，不加密（机密性放弃如实标注于导出 UI）。此时"文件哈希/整体哈希"沿用 v2 语义（明文条目摘要）。有密码时该两步在顺序上由"结构（密文）哈希验证 + AEAD 解密认证"等价满足，Spec 验证顺序解释如下：

```
支持版本 → manifest schema → 配对挑战 → 签名覆盖 → [可选密码/KDF/AEAD] → 文件哈希 → 整体哈希 → 资源关系 → 实体引用 → 存储空间
```

（有加密时"文件哈希、整体哈希"作用于解密后落临时区的明文字节，逐条目重算并与 manifest 记录核对。）

## 6. 格式 v3 规范（C7）

- `BackupPackageFormat.CURRENT_VERSION = 3`；`BackupPackageValidator` 按 `formatVersion` 分派：**v2 解析分支逐规则冻结**（含三状态字段的 `not-protected/not-supported/none` 精确匹配——不放宽）；v3 分支接受新状态集合；其他版本一律 `UnsupportedFormat` fail-closed（沿用既有主版本不可变策略）。
- v3 新状态取值（精确字符串，白名单匹配）：
  - `signatureStatus`：`"ecdsa-p256-sha256"`；
  - `authenticationStatus`：`"paired-migration-v1"`;
  - `encryptionStatus`：`"none"` | `"aes-256-gcm/argon2id-v1"`。
- 新路径契约条目：`pairing.json`（v3 必填；恰一个；字段见 §2.1/§3.2）。`encryption` 块内联 manifest。加密包的记录/资源条目路径与 v2 完全相同，仅字节内容为密文——**恢复端解密后的明文只写入隔离临时区，正式区切换使用解密重编码前的原明文字节**（不做 JSON 往返，保证"数据完整可读"以字节级摘要为准）。
- `manifest.json` 新增字段仅 v3 解析器认识；v3 拒绝未知字段（沿用"静默迁移未知字段"禁令）。
- v2 包导入语义：`BackupRestoreCoordinator` 对 `formatVersion==2` 直接返回固定类别 `SalvageNotRestorable`（"应急保全包不支持认证恢复"），不进入验证链——诚实状态而非缺陷（沿 F4-03 裁决）。

## 7. 库选型调研结论（受维护标准 + Android 支持证据）

| 用途 | 采用 | 证据与理由 | 许可证 |
|---|---|---|---|
| ECDSA 签名/验签 | Android/Java 平台 API | minSdk 26 原生支持 Keystore EC P-256 与 `SHA256withECDSA`；零供应链面 | — |
| AEAD | **Tink Java 1.21.0**（`com.google.crypto.tink:tink`） | 官方文档："核心 Android 库为 tink-java，自 API 24 起完全支持"（本项目 minSdk 26）；1.18+ 最低 API 23；Google 持续发布（2025-12 / 2026-03 均有版本），misuse-resistant API，公开审计历史。XChaCha20-Poly1305 在 Tink Java 无实现，任务书允许 AES-256-GCM 路线 | Apache-2.0 |
| KDF | **Bouncy Castle `bcprov-jdk18on`（最新稳定 1.8x）lightweight `Argon2BytesGenerator`** | Argon2id 为 RFC 9106 标准、现时共识最优内存硬 KDF；BC 是其 Java 生态主流程实现（Spring Security Argon2 编码器即建于其上），发布频繁、CVE 响应活跃；Android 广泛使用（不注册 Provider 的 lightweight 用法规避平台 BC 冲突） | MIT 类（BC 独有宽松许可，可商用） |
| 二维码编解码 | **ZXing `com.google.zxing:core` 3.5.3** + **CameraX `androidx.camera:camera-core/camera-camera2/camera-lifecycle`（实现时最新稳定）** | ZXing 官方状态为维护模式（仅 bugfix），但 QR 编码/解码算法面已稳定十年、无已知 CVE、3.5.3 为当前中央仓库最新；候选中 ML Kit Barcode 与 Google Code Scanner API 均引入 GMS/Play 依赖，与本项目"无 GMS 降级"方向（OCR 已定 Tesseract 降级路线）冲突，CameraX 提供无 GMS 取景 | Apache-2.0 |
| 生物识别/凭据 | **`androidx.biometric`（实现时最新稳定）** | 官方 AndroidX 库，处理 BIOMETRIC_STRONG/DEVICE_CREDENTIAL 兼容矩阵与 API 26–29 降级 | Apache-2.0 |

被否决的候选（调研过程记录）：Jetpack Security（`security-crypto`，仓库已声明但未使用；其模型面向 SharedPreferences/文件加密，不提供 KDF/配对，不替代上述选型）；Hans Security Provider（平台级 JCE 硬化，但文档面向 Java 桌面、KDF 面不含 Argon2/scrypt）；libsodium JNI 绑定（ABI 矩阵维护成本）；Android Keystore2 Argon2（仅 API 31+，违反 minSdk 26）；PBKDF2/bcrypt（非内存硬，AGENTS.md 明确要求"内存硬 KDF"）。

## 8. 密钥生命周期总表

| 秘密 | 生成 | 存储位置 | 销毁 |
|---|---|---|---|
| `K` 私钥 | 首次受保护导出时，设备认证后 | Android Keystore，不可导出 | 仅随应用卸载/清除数据；无导出接口 |
| 配对秘密 `S` | 每次挑战生成 | 仅内存 + 二维码显示；不落盘、不写台账 | 会话结束/过期即弃 |
| 密码明文 | 用户输入 | `CharArray`，瞬时 | 派生/解密调用返回即清零 |
| KEK | Argon2id 派生 | 仅内存 | 包裹/解包 DEK 后立即清零 |
| DEK | 每包 `SecureRandom` 32B | 明文仅内存；密文形式在 manifest | 导出完成即清零（包内为 wrapped 形态） |
| 条目 IV / `N` / salt | `SecureRandom` | 公开字节（包内） | — |
| API Key 等既有机密 | 不涉 | **永不进入 v3 包**（哨兵测试扩展覆盖新 serializer，AGENTS.md 红线） |

## 9. 导入/恢复安全不变量（C8 展开）

1. 输入只接受用户 SAF 选定的单个 `.elpbak`；解包目标仅为应用私有隔离临时目录（`cacheDir/restore-scratch/<会话id>`），限额沿用 `BackupPackageLimits`（256 MiB 包体、10 000 条目、64 MiB 单条目、1 GiB 解压总量）。
2. 验证严格按 §5.2 顺序短路；每一步失败立即 `RestoreFailure`（sealed，固定类别，零自由文本）：`UnsupportedRestoreVersion / SalvageNotRestorable / ManifestSchemaInvalid / PairingExpired / PairingMismatch / PairingSignatureInvalid / PasswordRequired / PasswordIncorrect / DecryptionFailure / IntegrityFailure / ResourceRelationshipInvalid / EntityReferenceInvalid / StorageInsufficient`。
3. 任何失败：删除整个 scratch 会话目录，正式 Room/媒体/设置零写入（测试在导入前后对正式数据做逐字节快照比对——AC4-05 锚定方式）。
4. 全部通过 → 创建回滚点：正式 DB 文件（checkpoint 后三件套）+ 活动资源目录快照到私有 `rollback/<时间戳>`，写入完整性标记（快照自身的摘要清单）；回滚点保留至下一次成功恢复或用户手动清理。
5. 二次确认 UI（明示"当前数据将被替换、不可合并"）→ 原子切换：关闭数据库 → 按目录 rename 交换 → 打开恢复的 DB 并执行 `PRAGMA quick_check`；quick_check 失败 → 自动回滚点回切 + `RestoreFailure.RollbackVerification`。
6. 成功后固定动作：重建搜索索引（派生数据重算）、调用 `ReminderScheduler.refresh()`、**删除本机全部 AI Profile 凭据密文并置"需重新填写 Key"**（不迁移、不保留——见裁决点 D4）、写入不含秘密的恢复审计记录。
7. UI 无旁路：不存在"跳过校验""强制导入""降级为 Salvage 恢复"入口；AC4-06 以源码契约测试锚定（恢复流 ViewModel 不含 bypass 状态、Screen 无对应 testTag 按钮）。

## 10. 测试与验收锚（TDD 映射）

- 域层纯 JVM：配对状态机（过期/取代/单次/认证失败零发放）、签名作用域构造与验证（软件 EC 密钥与设备路径同码）、v3 manifest 规范化确定性与摘要、§5.2 顺序的逐阶段负例矩阵（AC4-05 全清单：篡改 payload/resource/manifest/pairing 字节、过期码、他人 nonce、错误密码、路径穿越包、资源缺失、空间不足模拟、v2/未知版本拒绝）、恢复失败正式数据逐字节不变断言。
- 密码学原语在 JVM 直测（Tink 与 BC 均为纯 Java 路径，Argon2id 测试参数在 JVM 用固定小参 fixture 仅测**调用正确性**，设备参数正确性另以参数常量断言锁定——避免测试侧弱化）。
- 真机攒批走查：Keystore EC 密钥+BiometricPrompt 全矩阵（含凭据降级）、双设备扫码配对、相机 QR 实测、替换恢复端到端。JVM 层完成即按惯例记录"未验证：真机"。

## 11. 待用户批准的裁决点

- **D1（依赖面）**：批准新增 4 个运行时依赖（Tink、bcprov、zxing core、CameraX 三件套计一项、androidx.biometric）及其 lockfile 三配置 `--write-locks` 与 `docs/third-party-notices.md` 八字段登记。ZXing 处于维护模式是已知妥协，理由见 §7。
- **D2（配对活体性）**：批准"新设备仅相机实时扫码、不提供手动输入/相册识码"（偏离任务书"扫码/输入"表述，原因：QR 内容超 200 字符不可手输，且相册/粘贴通道等价于允许截图转发，直接削弱 AC4-05）。接受 §1.3/§2.6 所述残余风险与 U2 升级路线。
- **D3（会话化定位）**：批准受保护包为"换机现场流程"产物——导出与恢复须在同一次 30 分钟配对有效期内连续完成；超期包不可恢复、须回旧设备重新认证导出。长期留档仍由 v2 Salvage 包承担（但不可用于恢复）。此语义必须写入导出确认文案。
- **D4（Key 处置）**：恢复成功后删除本机 AI Profile 凭据密文（而非仅置标志），强制重新填写。更严格，符合"不得自动迁移任何 Key"。
- **D5（参数固化）**：批准 §2/§3/§4/§5 全部固定参数（TTL 30 分钟、认证窗口 30 秒、Argon2id 64MiB/3/1、AES-256-GCM 12B IV/16B 标签、P-256/SHA-256/DER、10–128 字符密码）及"校验器精确匹配、拒绝一切偏离"的策略。

---

## 附：调研引用来源

- Tink 官方设置文档（tink-java 1.21.0；Android 自 API 24 完全支持；1.18 起最低 API 23）：https://developers.google.com/tink/setup/java 与 https://github.com/tink-crypto/tink-java/releases
- Java Argon2 主流实现路径（Bouncy Castle `Argon2BytesGenerator`，Spring Security Argon2PasswordEncoder 基于 BC）：https://zhuanlan.zhihu.com/p/612120129 、https://blog.csdn.net/qq_46150074/article/details/152459552 、https://zhuanlan.zhihu.com/p/1921212544711893306
- KDF 现状共识（Argon2 为当前推荐、内存硬要求）：https://m.blog.csdn.net/allway2/article/details/126732502 、https://blog.csdn.net/inthat/article/details/151352443
- ZXing 项目状态（维护模式、core 最新 3.5.3、Apache-2.0）：https://mvnrepository.com/artifact/com.google.zxing/core 、https://baike.baidu.com/item/zxing
- Android 平台 Bouncy Castle 共存惯例（不注册 Provider 的用法、平台 BC 包名历史）：https://m.blog.csdn.net/sonadorje/article/details/118637585 、https://blog.csdn.net/doomvsjing/article/details/72943987/
