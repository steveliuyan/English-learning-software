package com.example.englishlearning.ai.domain

import com.example.englishlearning.core.error.AppError
import com.example.englishlearning.core.storage.AppErrorException
import java.net.URI
import java.net.InetAddress

fun validateEndpoint(endpoint: String): Result<URI> {
    val uri = runCatching { URI(endpoint) }.getOrElse {
        return Result.failure(AppErrorException(AppError.InvalidAiConfiguration))
    }
    val host = uri.host ?: return Result.failure(AppErrorException(AppError.InvalidAiConfiguration))
    if (uri.scheme != "https" || uri.userInfo != null || uri.rawQuery?.contains("key", ignoreCase = true) == true || uri.rawQuery?.contains("token", ignoreCase = true) == true) {
        return Result.failure(AppErrorException(AppError.InvalidAiConfiguration))
    }
    if (host.equals("localhost", ignoreCase = true) || host.endsWith(".localhost", ignoreCase = true)) {
        return Result.failure(AppErrorException(AppError.InvalidAiConfiguration))
    }
    val address = if (host.any { it == ':' } || host.all { it.isDigit() || it == '.' }) {
        runCatching { InetAddress.getByName(host) }.getOrNull()
    } else {
        null
    }
    if (address?.isLoopbackAddress == true || address?.isAnyLocalAddress == true || address?.isLinkLocalAddress == true || address?.isSiteLocalAddress == true) {
        return Result.failure(AppErrorException(AppError.InvalidAiConfiguration))
    }
    return Result.success(uri)
}
