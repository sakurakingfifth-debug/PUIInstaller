package com.panl.installer.core

import android.content.Context
import android.util.Log
import com.android.apksig.ApkSigner
import java.io.File
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate

/**
 * 给**未签名**的 APK 补一个签名。
 *
 * Android 的 PMS 对无签名包是硬拒的（`Package has no signatures`），root 也绕不过，
 * 所以想让这种包装上，唯一的路是先签一遍 —— 这里用一个随包分发的本地密钥
 * （assets/resign.p12，CN=PUI Resign，RSA 2048，30 年）补签 v1/v2/v3。
 *
 * 必须清楚的副作用：签完的包**签名就是这把本地密钥**，不是原作者。
 *  - 装全新应用：能装 ✅
 *  - 覆盖已装应用：签名对不上，照样会被拒（这是 Android 的设计，无解）
 */
object ApkResigner {

    private const val TAG = "ApkResigner"
    private const val ASSET = "resign.p12"
    private const val STORE_PASS = "pui-resign-2026"
    private const val ALIAS = "pui"

    /** 只对「一个签名者都没有」的包动手。 */
    fun needsResign(info: ApkInfo): Boolean = info.signers.isEmpty()

    /**
     * 重新签名，产物放在 `cache/resigned/`。
     *
     * @return 签名后的文件；失败返回 null（调用方按普通失败处理）
     */
    fun resign(context: Context, src: File): File? = runCatching {
        val ks = loadKeystore(context)
        val key = ks.getKey(ALIAS, STORE_PASS.toCharArray()) as? PrivateKey
            ?: error("keystore 里没有私钥")
        val cert = ks.getCertificate(ALIAS) as? X509Certificate
            ?: error("keystore 里没有证书")

        val dir = File(context.cacheDir, "resigned").apply { mkdirs() }
        // 同名旧产物先删，避免 apksig 报输出文件已存在
        dir.listFiles()?.forEach { runCatching { it.delete() } }
        val out = File(dir, src.nameWithoutExtension + "-resigned.apk")

        val signer = ApkSigner.SignerConfig.Builder("PUI", key, listOf(cert)).build()
        ApkSigner.Builder(listOf(signer))
            .setInputApk(src)
            .setOutputApk(out)
            // v1（JAR 签名）默认用 SHA1withRSA，Android 14+ 直接拒绝，
            // 会抛 "Failed to encode signature block"；v2/v3 对 minSdk 24+ 足够了
            .setV1SigningEnabled(false)
            .setV2SigningEnabled(true)
            .setV3SigningEnabled(true)
            .build()
            .sign()

        Log.i(TAG, "resigned ${src.name} -> ${out.name} (${out.length()} B)")
        out.takeIf { it.length() > 0 }
    }.onFailure {
        Log.w(TAG, "resign failed for ${src.name}", it)
    }.getOrNull()

    private fun loadKeystore(context: Context): KeyStore {
        val file = File(context.filesDir, "pui-resign.p12")
        if (!file.exists()) {
            // 第一次：把 assets 里的密钥复制出来（assets 不能直接给 KeyStore.load 用流式的场景更多）
            context.assets.open(ASSET).use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
        }
        return KeyStore.getInstance("PKCS12").apply {
            file.inputStream().use { load(it, STORE_PASS.toCharArray()) }
        }
    }
}
