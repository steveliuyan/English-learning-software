package com.example.englishlearning.ai

/**
 * 把 [AiFailure] 装进异常通道的载体，与 `AppErrorException` 同构：无消息、无堆栈、不 cause。
 *
 * `AiFailure` 与 `AppError` 是两个独立的封闭接口（后者的枚举里没有 AI 调用的失败分类），
 * 所以不能复用 `AppErrorException`。携带的 [failure] 是无字段 data object，自由文本
 * 进不来，泄露防线与 `AiFailure` 本身一致。
 */
class AiException(val failure: AiFailure) : RuntimeException(null, null, false, false)
