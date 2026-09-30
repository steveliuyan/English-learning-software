package com.example.englishlearning.imagegen

/**
 * 一次生图调用的产物。二选一：
 * - [Base64]：响应体内直接携带的图像字节（base64 编码），已过大小上限校验；
 * - [Url]：服务端托管的图片地址，**必须 https**——明文 http 一律拒绝。
 */
sealed interface GeneratedImage {

    /** base64 编码的图像数据，调用方负责解码落盘。 */
    data class Base64(val data: String) : GeneratedImage

    /** 服务端托管的 https 图片地址。 */
    data class Url(val url: String) : GeneratedImage
}
