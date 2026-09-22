package com.example.englishlearning.ai.domain

import com.example.englishlearning.ai.domain.AiAdvancedParameters
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AiProfileTest {
    @Test
    fun acceptsBoundaryAdvancedParameters() {
        assertEquals(0.0, AiAdvancedParameters(temperature = 0.0, topP = 0.0, maxTokens = 1, timeoutSeconds = 5).temperature)
        assertEquals(2.0, AiAdvancedParameters(temperature = 2.0, topP = 1.0, maxTokens = 4096, timeoutSeconds = 120).temperature)
    }

    @Test
    fun rejectsOutOfRangeAdvancedParameters() {
        assertFailsWith<IllegalArgumentException> { AiAdvancedParameters(temperature = -0.1) }
        assertFailsWith<IllegalArgumentException> { AiAdvancedParameters(topP = 1.1) }
        assertFailsWith<IllegalArgumentException> { AiAdvancedParameters(maxTokens = 0) }
        assertFailsWith<IllegalArgumentException> { AiAdvancedParameters(timeoutSeconds = 121) }
    }
}
