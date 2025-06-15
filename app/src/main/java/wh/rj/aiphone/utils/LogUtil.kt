package wh.rj.aiphone.utils



import android.util.Log

object LogUtil {
    private const val TAG = "AutoControl"
    fun d(msg: String) = Log.d(TAG, msg)
    fun w(msg: String) = Log.w(TAG, msg)
    fun e(msg: String, tr: Throwable? = null) = Log.e(TAG, msg, tr)
}
