package com.example.englishlearning.ai.domain

/** Device-level AI selection. It stores only the selected profile identity, never credentials. */
data class AiPreference(
    val preferenceId: String = DEVICE_PREFERENCE_ID,
    val defaultTextProfileId: String? = null,
    /** 默认生图服务：与文本默认各自独立，清除其中一个不得抹掉另一个。 */
    val defaultImageProfileId: String? = null,
) {
    init {
        require(preferenceId == DEVICE_PREFERENCE_ID)
    }

    companion object {
        const val DEVICE_PREFERENCE_ID = "device"
    }
}
