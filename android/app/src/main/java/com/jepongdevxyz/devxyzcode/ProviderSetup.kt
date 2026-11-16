package com.jepongdevxyz.devxyzcode

data class DetectedSetup(
    val provider: String,
    val baseUrl: String,
    val apiKey: String,
    val model: String?,
    val keyMasked: Boolean
)

object ProviderSetup {
    const val COARENA_BASE_URL = "https://coarena.ai/api/v1"

    val COARENA_MODELS = listOf(
        "gpt-6-luna",
        "gemini-3-8-flash",
        "deepseek-v4-1-flash-1017",
        "claude-fable-5"
    )

    fun modelsFor(baseUrl: String): List<String> =
        if (baseUrl.contains("coarena.ai")) COARENA_MODELS else AiClient.DEFAULT_MODELS

    fun providerName(baseUrl: String): String = when {
        baseUrl.contains("coarena.ai") -> "Coarena"
        baseUrl.contains("apinex") -> "APInex"
        else -> "Custom provider"
    }

    fun detect(text: String): DetectedSetup? {
        val base = firstMatch(
            text,
            Regex("""base_url\s*=\s*"([^"]+)""""),
            Regex("""ANTHROPIC_BASE_URL\s*=\s*(\S+)"""),
            Regex("""base URL:\s*(\S+)""", RegexOption.IGNORE_CASE)
        ) ?: return null
        val key = firstMatch(
            text,
            Regex("""api_key\s*=\s*"([^"]+)""""),
            Regex("""ANTHROPIC_AUTH_TOKEN\s*=\s*(\S+)"""),
            Regex("""Authorization:\s*Bearer\s+(\S+)""", RegexOption.IGNORE_CASE),
            Regex("""API key:\s*(\S+)""", RegexOption.IGNORE_CASE)
        ) ?: return null
        val model = firstMatch(
            text,
            Regex(""""model"\s*:\s*"([^"]+)""""),
            Regex("""model\s*=\s*'([^']+)'""")
        )
        val keyMasked = key.contains('•') || key.contains('…') || key.contains("****")
        return DetectedSetup(
            provider = providerName(base),
            baseUrl = base,
            apiKey = key,
            model = model,
            keyMasked = keyMasked
        )
    }

    private fun firstMatch(text: String, vararg patterns: Regex): String? {
        for (pattern in patterns) {
            pattern.find(text)?.let { return it.groupValues[1].trim().trim('"', '\'') }
        }
        return null
    }
}
