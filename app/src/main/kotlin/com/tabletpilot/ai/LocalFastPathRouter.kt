package com.tabletpilot.ai

object LocalFastPathRouter {

    data class LocalAction(
        val actionType: String,
        val params: Map<String, String>
    )

    // Patterns compiled once at startup
    private val OPEN_APP_PATTERN = Regex(
        """^(?:open|launch|start|run)\s+(.+)$""",
        RegexOption.IGNORE_CASE
    )
    private val HOME_PATTERN = Regex(
        """^(?:go\s+home|home|go\s+to\s+home(?:\s+screen)?)$""",
        RegexOption.IGNORE_CASE
    )
    private val BACK_PATTERN = Regex(
        """^(?:go\s+back|back(?:space)?|navigate\s+back)$""",
        RegexOption.IGNORE_CASE
    )
    private val RECENTS_PATTERN = Regex(
        """^(?:recents?|recent\s+apps?|app\s+switcher|overview)$""",
        RegexOption.IGNORE_CASE
    )
    private val NOTIFICATIONS_PATTERN = Regex(
        """^(?:notifications?|show\s+notifications?|pull\s+down\s+shade|notification\s+shade)$""",
        RegexOption.IGNORE_CASE
    )
    private val SCROLL_PATTERN = Regex(
        """^scroll\s+(up|down|left|right)(?:\s+a\s+bit)?$""",
        RegexOption.IGNORE_CASE
    )
    private val VOLUME_UP_PATTERN = Regex(
        """^(?:volume\s+up|louder|increase\s+volume|turn\s+(?:it\s+)?up)$""",
        RegexOption.IGNORE_CASE
    )
    private val VOLUME_DOWN_PATTERN = Regex(
        """^(?:volume\s+down|quieter|decrease\s+volume|turn\s+(?:it\s+)?down|lower\s+volume)$""",
        RegexOption.IGNORE_CASE
    )
    private val TIMER_PATTERN = Regex(
        """^(?:set\s+)?timer\s+(?:for\s+)?(\d+)\s*(?:min(?:utes?)?|m)(?:\s+(.+))?$""",
        RegexOption.IGNORE_CASE
    )
    private val SPLIT_SCREEN_PATTERN = Regex(
        """^(?:split\s+screen|enter\s+split\s+screen|split\s+view)$""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Attempts to match [command] to a local action.
     * Returns a [LocalAction] if matched, null if Gemini should handle it.
     */
    fun route(command: String): LocalAction? {
        val trimmed = command.trim()

        OPEN_APP_PATTERN.find(trimmed)?.let { match ->
            val appName = match.groupValues[1].trim()
            return LocalAction("OPEN_APP", mapOf("app" to appName))
        }

        if (HOME_PATTERN.matches(trimmed)) {
            return LocalAction("GLOBAL", mapOf("action" to "HOME"))
        }

        if (BACK_PATTERN.matches(trimmed)) {
            return LocalAction("GLOBAL", mapOf("action" to "BACK"))
        }

        if (RECENTS_PATTERN.matches(trimmed)) {
            return LocalAction("GLOBAL", mapOf("action" to "RECENTS"))
        }

        if (NOTIFICATIONS_PATTERN.matches(trimmed)) {
            return LocalAction("GLOBAL", mapOf("action" to "NOTIFICATIONS"))
        }

        SCROLL_PATTERN.find(trimmed)?.let { match ->
            val direction = match.groupValues[1].uppercase()
            return LocalAction("SCROLL", mapOf("direction" to direction))
        }

        if (VOLUME_UP_PATTERN.matches(trimmed)) {
            return LocalAction("VOLUME", mapOf("direction" to "UP"))
        }

        if (VOLUME_DOWN_PATTERN.matches(trimmed)) {
            return LocalAction("VOLUME", mapOf("direction" to "DOWN"))
        }

        TIMER_PATTERN.find(trimmed)?.let { match ->
            val minutes = match.groupValues[1]
            val label = match.groupValues[2].takeIf { it.isNotBlank() } ?: "Timer"
            return LocalAction("TIMER", mapOf("minutes" to minutes, "label" to label))
        }

        if (SPLIT_SCREEN_PATTERN.matches(trimmed)) {
            return LocalAction("GLOBAL", mapOf("action" to "SPLIT_SCREEN"))
        }

        return null // No local match — escalate to Gemini
    }
}
