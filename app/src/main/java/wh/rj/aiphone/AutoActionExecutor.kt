package wh.rj.aiphone



import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

object AutoActionExecutor {
    private const val TAG = "AutoActionExecutor"

    /** 根据文本点击可点击控件 */
    fun clickByText(root: AccessibilityNodeInfo?, text: String): Boolean {
        if (root == null) return false
        val nodes = root.findAccessibilityNodeInfosByText(text)
        for (node in nodes) {
            if (node.isClickable) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                Log.d(TAG, "点击文本: $text")
                return true
            }
        }
        return false
    }

    /** 根据提示文字输入内容 */
    fun inputText(root: AccessibilityNodeInfo?, hintText: String, content: String): Boolean {
        if (root == null) return false
        val nodes = root.findAccessibilityNodeInfosByText(hintText)
        for (node in nodes) {
            if (node.isEditable) {
                val arguments = android.os.Bundle()
                arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, content)
                node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                Log.d(TAG, "输入内容: $content 到字段: $hintText")
                return true
            }
        }
        return false
    }
}
