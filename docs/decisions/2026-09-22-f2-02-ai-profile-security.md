# F2-02 AI Profile 与 Endpoint 安全策略

## 背景

阶段 2 需要支持用户自配的 OpenAI-compatible 服务，但 Endpoint 与凭据属于高风险出站配置，不能由任意字符串直接进入后续请求层。

## 决策

1. `AiProfile` 只包含非敏感元数据、能力声明、参数和 `SecretReference`；API Key 不属于领域模型字段。
2. `AiAdvancedParameters` 采用类型化白名单，限制为 `temperature` 0–2、`topP` 0–1、`maxTokens` 1–4096、`timeoutSeconds` 5–120，系统提示模板仅允许 `default-reading-v1`。
3. `validateEndpoint` 只接受 HTTPS、无 URL 用户凭据、无疑似 Key/token 查询参数的地址。
4. 纯策略拒绝 localhost、环回、任意本地/链路/站点地址和数字 IPv4/IPv6 私网地址；不执行 DNS 或网络请求。域名解析和重定向后的最终地址必须由未来传输层在连接前再次校验。
5. 所有失败统一为 `AppError.InvalidAiConfiguration`，不把原始 URL、凭据或底层异常放入用户可见错误。

## 影响

Task 2 将把 Profile 元数据保存到 Room，但只保存 SecretReference alias；Task 3 通过现有 SecretStore 保存 Key。F2-02 本身不建立真实 AI 网络调用，也不接受任意 Header、请求体覆盖或工具调用。
