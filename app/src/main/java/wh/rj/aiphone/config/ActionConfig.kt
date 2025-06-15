package wh.rj.aiphone.config


enum class ActionType {
    CLICK,
    INPUT,
    WAIT
}

data class ActionConfig(
    val actionType: ActionType,
    val targetText: String? = null,
    val inputContent: String? = null,
    val delayMillis: Long = 0
)
