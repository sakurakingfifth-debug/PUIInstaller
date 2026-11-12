package com.panl.installer.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Parcel
import android.util.Log

/**
 * 公平运行内存（ITGSA 金标联盟：vivo / 小米 / OPPO / 荣耀）的客户端。
 *
 * 系统在应用物理内存（PSS）或 Java 堆触达阈值时发广播过来：
 *
 *  - `itgsa.intent.action.TRIM`：预警，应用该尽快释放内存；
 *  - `itgsa.intent.action.KILL`：查杀前通知，应用该在 **3 秒内**存好现场并回执。
 *
 * 广播结构（官方文档）：
 * ```
 * extras["common"] = { notifyType, notifyId, reason, action, callback:IBinder }
 * extras["extra"]  = { pss, pssLimit }        // notifyType = 1000 物理内存
 *                  = { heapAlloc, heapCapacity } // notifyType = 2000 Java 堆
 * ```
 * 处理完必须通过 `common.callback` 这个 Binder 回调系统（否则系统会等满 3 秒再查杀），
 * 事务码固定为 `IBinder.FIRST_CALL_TRANSACTION`，参数依次是
 * `notifyType(int) notifyId(int) result(int) extra(Bundle)`。
 *
 * 说明：安装器没有需要长期驻留的业务，主要价值是**及时回执 + 主动归还内存**，
 * 避免被当成"不配合"的应用直接清掉。
 */
object FairMemoryReceiver {

    private const val TAG = "FairMemory"

    const val ACTION_TRIM = "itgsa.intent.action.TRIM"
    const val ACTION_KILL = "itgsa.intent.action.KILL"

    /** 回执事务码：官方示例里就是 FIRST_CALL_TRANSACTION。 */
    private const val TRANSACTION_EXCEPTION_REPLY = IBinder.FIRST_CALL_TRANSACTION

    private const val KEY_COMMON = "common"
    private const val KEY_EXTRA = "extra"

    /** 1000 = 物理内存（PSS），2000 = Java 堆。 */
    private const val TYPE_PSS = 1000
    private const val TYPE_HEAP = 2000

    /** result = 0 表示已处理完（释放内存 / 保存现场都算成功）。 */
    private const val RESULT_OK = 0

    @Volatile
    private var registered = false

    private var thread: HandlerThread? = null

    fun register(context: Context) {
        if (registered) return
        synchronized(this) {
            if (registered) return
            runCatching {
                val ht = HandlerThread("fair-memory").apply { start() }
                thread = ht
                val handler = Handler(ht.looper)
                val filter = IntentFilter().apply {
                    addAction(ACTION_TRIM)
                    addAction(ACTION_KILL)
                }
                val app = context.applicationContext
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    // 广播由系统侧发起，必须 EXPORTED 才收得到
                    app.registerReceiver(receiver, filter, null, handler, Context.RECEIVER_EXPORTED)
                } else {
                    app.registerReceiver(receiver, filter, null, handler)
                }
                registered = true
                Log.i(TAG, "registered TRIM/KILL receiver")
            }.onFailure { Log.w(TAG, "register failed", it) }
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action ?: return
            val data = intent.extras ?: return
            // 官方结构是 extras["common"] / extras["extra"]；这里兼容直接平铺的写法，
            // 方便用 `adb shell am broadcast ... --ei notifyType 1000` 自测。
            val common = data.getBundle(KEY_COMMON) ?: data
            val extra = data.getBundle(KEY_EXTRA) ?: data

            val notifyType = common.getInt("notifyType", 0)
            val notifyId = common.getInt("notifyId", 0)
            val reason = common.getString("reason")
            val callback = common.getBinder("callback")

            val detail = buildString {
                append(if (action == ACTION_KILL) "查杀通知" else "内存预警")
                append(" type=").append(notifyType)
                append(" id=").append(notifyId)
                if (!reason.isNullOrBlank()) append(" reason=").append(reason)
                if (extra != null) {
                    when (notifyType) {
                        TYPE_PSS -> append(" pss=").append(extra.getInt("pss"))
                            .append("/").append(extra.getInt("pssLimit")).append("MB")
                        TYPE_HEAP -> append(" heap=").append(extra.getInt("heapAlloc"))
                            .append("/").append(extra.getInt("heapCapacity")).append("MB")
                    }
                }
            }
            InstallerLog.record(context, "MEM", detail)

            if (action == ACTION_KILL) {
                MemoryPressure.preserve(context, "KILL")
                MemoryPressure.release(context, "KILL")
            } else {
                MemoryPressure.release(context, "TRIM")
            }

            // 必须在 3 秒内回执，否则系统会判定超时后直接查杀
            reply(callback, notifyType, notifyId, RESULT_OK)
        }
    }

    /** 通过系统给的 Binder 回执处理结果。 */
    private fun reply(callback: IBinder?, notifyType: Int, notifyId: Int, result: Int) {
        if (callback == null) return
        val data = Parcel.obtain()
        val replyParcel = Parcel.obtain()
        try {
            data.writeInt(notifyType)
            data.writeInt(notifyId)
            data.writeInt(result)
            data.writeBundle(Bundle())
            callback.transact(TRANSACTION_EXCEPTION_REPLY, data, replyParcel, IBinder.FLAG_ONEWAY)
            replyParcel.readException()
        } catch (e: Exception) {
            Log.w(TAG, "reply failed", e)
        } finally {
            replyParcel.recycle()
            data.recycle()
        }
    }
}
