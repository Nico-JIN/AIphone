package wh.rj.aiphone



import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import wh.rj.aiphone.config.ActionConfig
import wh.rj.aiphone.config.ActionType
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object ActionExecutor {
    private const val TAG = "ActionExecutor"

    fun parseActions(json: String): List<ActionConfig> {
        val gson = Gson()
        val type = object : TypeToken<List<ActionConfig>>() {}.type
        return gson.fromJson(json, type)
    }

    fun executeActions(root: AccessibilityNodeInfo?, actions: List<ActionConfig>): Boolean {
        for (action in actions) {
            if (action.delayMillis > 0) Thread.sleep(action.delayMillis)
            val success = when (action.actionType) {
                ActionType.CLICK -> action.targetText?.let { AutoActionExecutor.clickByText(root, it) } ?: false
                ActionType.INPUT -> {
                    if (action.targetText != null && action.inputContent != null) {
                        AutoActionExecutor.inputText(root, action.targetText, action.inputContent)
                    } else false
                }
                ActionType.WAIT -> true
            }
            Log.d(TAG, "执行动作: $action 结果: $success")
            if (!success) {
                Log.w(TAG, "动作执行失败，停止后续操作")
                return false
            }
        }
        return true
    }
}
