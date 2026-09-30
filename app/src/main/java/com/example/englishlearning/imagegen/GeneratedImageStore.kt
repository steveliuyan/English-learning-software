package com.example.englishlearning.imagegen

import com.example.englishlearning.ai.AiException
import com.example.englishlearning.ai.AiFailure
import com.example.englishlearning.ai.net.AudioHttpRequest
import com.example.englishlearning.ai.net.AudioHttpResult
import com.example.englishlearning.ai.net.AudioHttpTransport
import java.io.File
import kotlinx.coroutines.CancellationException

/**
 * 生图产物落地：把 [GeneratedImage] 变成 cache 目录里的一张文件。
 *
 * - `Base64`：本地解码落盘，不再出网；
 * - `Url`：经**二进制**通道取回字节再落盘。用 [AudioHttpTransport] 而不是文本传输，
 *   因为图像是二进制——文本传输按 UTF-8 解码会把字节读坏；该通道自身强制 HTTPS
 *   且不跟随重定向，图片托管域名只拿到 URL，**不带生图服务的密钥**（headers 为空）。
 *
 * 取消原样抛出（不是网络错误）；其余失败一律收敛成带 [AiFailure] 的 [AiException]，
 * 供上层按既有失败文案规则展示。
 */
class GeneratedImageStore(
    private val transport: AudioHttpTransport,
    private val directory: File,
) {
    suspend fun persist(image: GeneratedImage): Result<File> = when (image) {
        is GeneratedImage.Base64 -> GeneratedImageWriter.save(image.data, directory)
        is GeneratedImage.Url -> persistRemote(image.url)
    }

    private suspend fun persistRemote(url: String): Result<File> = try {
        when (val result = transport.send(AudioHttpRequest(url = url))) {
            is AudioHttpResult.Success -> try {
                GeneratedImageWriter.saveBytes(result.body, directory)
            } finally {
                // 图像字节用完即清零，与音频路径同一处置。
                result.body.fill(0)
            }
            is AudioHttpResult.HttpError -> try {
                failure(failureOf(result.statusCode))
            } finally {
                result.body.fill(0)
            }
            AudioHttpResult.InsecureUrl -> failure(AiFailure.InvalidResponse)
            AudioHttpResult.NetworkUnavailable -> failure(AiFailure.NetworkUnavailable)
            AudioHttpResult.TimedOut -> failure(AiFailure.Timeout)
            AudioHttpResult.ResponseTooLarge -> failure(AiFailure.InvalidResponse)
            AudioHttpResult.Cancelled -> throw CancellationException("image download cancelled")
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        failure(AiFailure.InvalidResponse)
    }

    private fun failureOf(statusCode: Int): AiFailure = when (statusCode) {
        401 -> AiFailure.Unauthorized
        429 -> AiFailure.RateLimited
        in 500..599 -> AiFailure.ServerUnavailable
        else -> AiFailure.InvalidResponse
    }

    private fun failure(failure: AiFailure): Result<File> = Result.failure(AiException(failure))
}
