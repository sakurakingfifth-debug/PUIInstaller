package com.panl.installer.core

import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.io.ByteArrayInputStream
import com.panl.installer.i18n.tr

/**
 * APK 签名信息与「已知密钥」识别。
 *
 * 背景：AOSP 把四把构建密钥（testkey / platform / shared / media）公开在
 * `build/target/product/security/` 里，任何拿到源码的人都能用它们签名。
 * 用这些密钥签出来的包，可以被任何持有同一密钥的人伪造更新 —— 属于明确的安全信号。
 *
 * 这里只做**识别与提示**，不阻断安装（用户可能就是在刷机 / 改系统应用）。
 */
data class SignerInfo(
    /** 证书主题，RFC2253，例如 `EMAILADDRESS=android@android.com,CN=Android,...` */
    val subject: String,
    val issuer: String,
    val sha256: String,
    val sha1: String,
    val signatureAlgorithm: String,
    val keyAlgorithm: String,
    val keySize: Int,
    /** 命中的 AOSP 公开密钥名（testkey / platform / shared / media），否则 null。 */
    val aospKey: String?,
    /** Android 调试密钥（Android Studio 默认签名）。 */
    val isDebugKey: Boolean,
    val validFrom: Long,
    val validTo: Long
) {
    val isAosp: Boolean get() = aospKey != null

    /** 给界面用的一句话结论。 */
    val verdict: String
        get() = when {
            aospKey != null -> tr("AOSP 公开密钥（$aospKey）", "AOSP public key ($aospKey)")
            isDebugKey -> tr("Android 调试密钥", "Android debug key")
            else -> tr("未知签名者", "Unknown signer")
        }

    val shortSha256: String get() = sha256.chunked(2).take(8).joinToString(":").uppercase()
}

object ApkSignature {

    /** AOSP 公开密钥的 SHA-256（证书 DER 的摘要，小写十六进制）。 */
    private val AOSP_KEYS = linkedMapOf(
        "a40da80a59d170caa950cf15c18c454d47a39b26989d8b640ecd745ba71bf5dc" to "testkey",
        "c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8" to "platform",
        "28bbfe4a7b97e74681dc55c2fbb6ccb8d6c74963733f6a46ae74d8c3a6e879fd" to "shared",
        "465983f7791f2abeb43ea2cbdc7f21a8260b72bc08a55c839fc1a43bc741a81e" to "media"
    )

    /** 各密钥的用途说明，展示在签名详情里。 */
    val AOSP_KEY_NOTES = mapOf(
        "testkey" to tr("AOSP 通用测试密钥，随源码公开", "AOSP test key, published with the source"),
        "platform" to tr("AOSP 平台密钥，可签系统级应用", "AOSP platform key, can sign system apps"),
        "shared" to tr("AOSP 共享库密钥", "AOSP shared library key"),
        "media" to tr("AOSP 媒体/DRM 密钥", "AOSP media/DRM key")
    )

    fun fromBytes(raw: ByteArray): SignerInfo? = runCatching {
        val cert = CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(raw)) as X509Certificate
        val sha256 = cert.encoded.sha256Hex()
        val subject = prettyName(cert.subjectX500Principal.name)
        val isDebug = subject.contains("CN=Android Debug", ignoreCase = true)

        SignerInfo(
            subject = subject,
            issuer = prettyName(cert.issuerX500Principal.name),
            sha256 = sha256,
            sha1 = cert.encoded.sha1Hex(),
            signatureAlgorithm = cert.sigAlgName,
            keyAlgorithm = cert.publicKey.algorithm,
            keySize = keySize(cert),
            aospKey = AOSP_KEYS[sha256],
            isDebugKey = isDebug,
            validFrom = cert.notBefore.time,
            validTo = cert.notAfter.time
        )
    }.getOrNull()

    /**
     * `X500Principal.getName()` 会把 emailAddress 输出成 `1.2.840.113549.1.9.1=#1613...`（DER 十六进制）。
     * AOSP 的证书里正好带这个字段，直接显示很难看，这里还原成 `EMAILADDRESS=android@android.com`。
     */
    private fun prettyName(raw: String): String = raw.split(",").joinToString(",") { part ->
        val p = part.trim()
        if (p.startsWith("1.2.840.113549.1.9.1=#")) {
            runCatching {
                val bytes = p.substringAfter("#").chunked(2)
                    .map { it.toInt(16).toByte() }
                    .toByteArray()
                // DER: 0x16 (IA5String) + 长度 + 内容
                val len = bytes[1].toInt() and 0xFF
                "EMAILADDRESS=" + String(bytes, 2, len, Charsets.UTF_8)
            }.getOrDefault(p)
        } else {
            p
        }
    }

    private fun keySize(cert: X509Certificate): Int = runCatching {
        val key = cert.publicKey
        when (val alg = key.algorithm) {
            "RSA" -> (key as java.security.interfaces.RSAPublicKey).modulus.bitLength()
            "EC" -> (key as java.security.interfaces.ECPublicKey).params.order.bitLength()
            "DSA" -> (key as java.security.interfaces.DSAPublicKey).params.p.bitLength()
            else -> alg.length * 8
        }
    }.getOrDefault(0)
}

internal fun ByteArray.sha256Hex(): String = digestHex("SHA-256")

internal fun ByteArray.sha1Hex(): String = digestHex("SHA-1")

private fun ByteArray.digestHex(algorithm: String): String =
    MessageDigest.getInstance(algorithm).digest(this).joinToString("") { "%02x".format(it) }
