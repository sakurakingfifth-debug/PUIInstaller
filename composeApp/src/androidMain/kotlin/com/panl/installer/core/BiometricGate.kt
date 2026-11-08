package com.panl.installer.core

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import android.util.Log
import androidx.core.content.ContextCompat
import com.panl.installer.i18n.tr
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * 安装前的生物识别确认（思路来自 InstallerX-Revived 的 require_biometric_auth）。
 *
 * 用的是**平台**的 `BiometricPrompt` 而不是 androidx 那套：后者要求宿主是
 * `FragmentActivity`，而这里的页面都是 `ComponentActivity`，没必要为了一个弹窗换基类。
 *
 * 三种情况分开处理：
 *  - 设备没设生物识别 / 硬件不支持 → 直接放行（不能因为没录指纹就把安装堵死）；
 *  - Android 11+ 允许「生物识别 + 锁屏密码」二选一，10 及以下只能走旧的
 *    `setDeviceCredentialAllowed`；
 *  - Android 9 以下没有这套 API，直接放行。
 */
object BiometricGate {

    private const val TAG = "BiometricGate"

    /** 设备当前能不能做认证；不能就别弹窗了。 */
    fun canAuthenticate(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 28) return false
        // 系统没设锁屏（没有 DeviceCredential）时，BIOMETRIC_WEAK 也可能是 SUCCESS，
        // 但设备凭据那条路走不通，这里以「有没有安全锁」为准更稳
        val km = context.getSystemService(KeyguardManager::class.java)
        if (km?.isDeviceSecure != true) return false
        return runCatching {
            val bm = context.getSystemService(BiometricManager::class.java)
            val authenticators = if (Build.VERSION.SDK_INT >= 30) {
                BiometricManager.Authenticators.BIOMETRIC_WEAK or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            } else {
                BiometricManager.Authenticators.BIOMETRIC_WEAK
            }
            bm?.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS
        }.getOrDefault(false)
    }

    /**
     * 弹认证框，挂起等结果。
     *
     * @return true = 认证通过（或设备不支持、直接放行）；false = 用户取消 / 认证失败。
     */
    suspend fun authenticate(activity: Activity): Boolean {
        Log.i(TAG, "authenticate called, can=${canAuthenticate(activity)}")
        if (!canAuthenticate(activity)) {
            Log.i(TAG, "no biometric available, skip")
            return true
        }
        return suspendCancellableCoroutine { cont ->
            val executor = ContextCompat.getMainExecutor(activity)
            val signal = CancellationSignal()
            var finished = false

            fun finish(result: Boolean) {
                if (finished) return
                finished = true
                Log.i(TAG, "finish result=$result")
                if (cont.isActive) cont.resume(result)
            }

            val callback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                    finish(true)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                    Log.i(TAG, "auth error $errorCode: $errString")
                    finish(false)
                }

                override fun onAuthenticationFailed() {
                    // 认错了但还能重试，不结束
                    Log.i(TAG, "auth failed, retry allowed")
                }
            }

            val builder = BiometricPrompt.Builder(activity)
                .setTitle(tr("安装确认", "Confirm install"))
                .setSubtitle(
                    tr(
                        "请认证以确认本次安装操作",
                        "Authenticate to confirm this install"
                    )
                )
                .setNegativeButton(tr("取消", "Cancel"), executor) { _, _ -> finish(false) }

            if (Build.VERSION.SDK_INT >= 30) {
                builder.setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_WEAK or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
                )
            } else {
                @Suppress("DEPRECATION")
                builder.setDeviceCredentialAllowed(true)
            }

            Log.i(TAG, "prompt shown")
            cont.invokeOnCancellation { runCatching { signal.cancel() } }
            runCatching { builder.build().authenticate(signal, executor, callback) }
                .onFailure {
                    Log.w(TAG, "authenticate failed to start", it)
                    finish(false)
                }
        }
    }
}
