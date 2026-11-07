package com.panl.installer.corepatch.hook

import com.panl.installer.corepatch.Config
import com.panl.installer.corepatch.XposedHelper
import com.panl.installer.corepatch.XposedHelper.hostClassLoader

object MessageDigestHook : BaseHook() {
    override val name = "MessageDigestHook"

    override fun hook() {
        val messageDigestClazz = hostClassLoader.loadClass("java.security.MessageDigest")
        // https://cs.android.com/android/platform/superproject/main/+/main:libcore/ojluni/src/main/java/java/security/MessageDigest.java;l=518
        // public static boolean isEqual(byte[] digesta, byte[] digestb)
        val isEqualMethod = messageDigestClazz.getDeclaredMethod(
            "isEqual", ByteArray::class.java, ByteArray::class.java
        )
        XposedHelper.hookBefore(isEqualMethod) { callback ->
            if (Config.isBypassVerificationEnabled()) {
                callback.returnAndSkip(true)
            }
        }
    }
}
