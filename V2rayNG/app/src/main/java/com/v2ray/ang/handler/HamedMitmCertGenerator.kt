package com.v2ray.ang.handler

import android.content.Context
import android.content.Intent
import android.security.KeyChain
import android.util.Base64
import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Generates and stores a local self-signed Root CA (`mycert.crt` and `mycert.key`) in
 * [Utils.userAssetPath] so Xray's MITM-DomainFronting inbound (`tls-decrypt-h11`/`h211`) can issue
 * leaf certificates without manual computer setup, and lets the user install `mycert.crt` into
 * Android's trusted CA store if they use MITM in Direct mode.
 */
object HamedMitmCertGenerator {

    private const val TAG = AppConfig.TAG
    private const val KEY_SIZE_BITS = 2048
    private const val VALIDITY_YEARS = 10

    // OIDs (DER encoded payloads without tag/length)
    private val OID_SHA256_WITH_RSA = oid(1, 2, 840, 113549, 1, 1, 11)
    private val OID_COMMON_NAME = oid(2, 5, 4, 3)
    private val OID_ORGANIZATION = oid(2, 5, 4, 10)
    private val OID_SUBJECT_KEY_ID = oid(2, 5, 29, 14)
    private val OID_KEY_USAGE = oid(2, 5, 29, 15)
    private val OID_BASIC_CONSTRAINTS = oid(2, 5, 29, 19)

    /** Returns `true` if both `mycert.crt` and `mycert.key` exist and are non-empty. */
    fun hasCertFiles(context: Context): Boolean {
        val assetDir = File(Utils.userAssetPath(context))
        val cert = File(assetDir, AppConfig.MITM_CERT_FILE)
        val key = File(assetDir, AppConfig.MITM_KEY_FILE)
        return cert.isFile && cert.length() > 0L && key.isFile && key.length() > 0L
    }

    /** Ensures `mycert.crt` and `mycert.key` exist in [Utils.userAssetPath], creating them if missing. */
    @Synchronized
    fun ensureCertFiles(context: Context): Boolean {
        val assetDirPath = Utils.userAssetPath(context)
        if (assetDirPath.isBlank()) return false
        val assetDir = File(assetDirPath).apply { mkdirs() }
        val certFile = File(assetDir, AppConfig.MITM_CERT_FILE)
        val keyFile = File(assetDir, AppConfig.MITM_KEY_FILE)
        if (certFile.isFile && certFile.length() > 0L && keyFile.isFile && keyFile.length() > 0L) {
            return true
        }
        return try {
            val pair = generateSelfSignedCaPem()
            certFile.writeText(pair.first)
            keyFile.writeText(pair.second)
            LogUtil.i(TAG, "Generated MITM Root CA at ${certFile.absolutePath}")
            true
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to generate MITM Root CA files", e)
            false
        }
    }

    /** Builds a system intent to install `mycert.crt` into Android Trusted Credentials (CA). */
    fun buildInstallCertIntent(context: Context): Intent? {
        if (!ensureCertFiles(context)) return null
        val certBytes = runCatching {
            File(Utils.userAssetPath(context), AppConfig.MITM_CERT_FILE).readBytes()
        }.getOrNull() ?: return null

        @Suppress("DEPRECATION")
        return KeyChain.createInstallIntent().apply {
            putExtra(KeyChain.EXTRA_CERTIFICATE, certBytes)
            putExtra(KeyChain.EXTRA_NAME, "Hamed Aether MITM CA")
        }
    }

    /**
     * Generates a `(certificatePem, pkcs8PrivateKeyPem)` pair using standard JDK JCA + ASN.1 DER.
     * Kept pure JCA so unit tests and Android API 24+ can run it without BouncyCastle/sun.security.
     */
    internal fun generateSelfSignedCaPem(
        commonName: String = "Hamed Aether MITM Root CA",
        organization: String = "Hamed Aether",
        encodeBase64: (ByteArray) -> String = { bytes ->
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        },
    ): Pair<String, String> {
        val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
        keyPairGenerator.initialize(KEY_SIZE_BITS, SecureRandom())
        val keyPair = keyPairGenerator.generateKeyPair()

        val tbs = buildTbsCertificate(
            publicKeySpkiDer = keyPair.public.encoded,
            commonName = commonName,
            organization = organization,
        )

        val signature = Signature.getInstance("SHA256withRSA")
        signature.initSign(keyPair.private)
        signature.update(tbs)
        val sigBytes = signature.sign()

        val fullCertDer = derSequence(
            tbs,
            algIdSha256WithRsa(),
            derBitString(sigBytes, unusedBits = 0),
        )

        // Verify with JCA before writing out
        CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(fullCertDer)) as X509Certificate

        val certPem = wrapPem("CERTIFICATE", encodeBase64(fullCertDer))
        val keyPem = wrapPem("PRIVATE KEY", encodeBase64(keyPair.private.encoded))
        return certPem to keyPem
    }

    private fun buildTbsCertificate(
        publicKeySpkiDer: ByteArray,
        commonName: String,
        organization: String,
    ): ByteArray {
        val serialBytes = ByteArray(8).also { SecureRandom().nextBytes(it) }
        serialBytes[0] = (serialBytes[0].toInt() and 0x7F).toByte()
        if (serialBytes[0].toInt() == 0) serialBytes[0] = 0x01

        val now = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            add(Calendar.DAY_OF_YEAR, -1)
        }
        val until = (now.clone() as Calendar).apply {
            add(Calendar.YEAR, VALIDITY_YEARS)
        }

        val name = derSequence(
            derSet(derSequence(OID_ORGANIZATION, derUtf8String(organization))),
            derSet(derSequence(OID_COMMON_NAME, derUtf8String(commonName))),
        )

        val subjectKeyIdDigest = MessageDigest.getInstance("SHA-1").digest(publicKeySpkiDer)
        val extensions = derExplicit(
            tag = 3,
            content = derSequence(
                // BasicConstraints: critical=TRUE, cA=TRUE
                derSequence(
                    OID_BASIC_CONSTRAINTS,
                    derBooleanTrue(),
                    derOctetString(derSequence(derBooleanTrue())),
                ),
                // KeyUsage: critical=TRUE, digitalSignature(0) | keyCertSign(5) | cRLSign(6) = 0x86 (1 unused bit)
                derSequence(
                    OID_KEY_USAGE,
                    derBooleanTrue(),
                    derOctetString(derBitString(byteArrayOf(0x86.toByte()), unusedBits = 1)),
                ),
                // SubjectKeyIdentifier
                derSequence(
                    OID_SUBJECT_KEY_ID,
                    derOctetString(derOctetString(subjectKeyIdDigest)),
                ),
            ),
        )

        return derSequence(
            derExplicit(tag = 0, content = derInteger(byteArrayOf(0x02))), // v3 = 2
            derInteger(serialBytes),
            algIdSha256WithRsa(),
            name, // issuer
            derSequence(derAsn1Time(now), derAsn1Time(until)),
            name, // subject
            publicKeySpkiDer,
            extensions,
        )
    }

    private fun algIdSha256WithRsa(): ByteArray = derSequence(OID_SHA256_WITH_RSA, byteArrayOf(0x05, 0x00))

    private fun derAsn1Time(calendar: Calendar): ByteArray {
        val year = calendar.get(Calendar.YEAR)
        return if (year in 1950..2049) {
            val fmt = SimpleDateFormat("yyMMddHHmmss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            tlv(0x17, fmt.format(calendar.time).toByteArray(Charsets.US_ASCII))
        } else {
            val fmt = SimpleDateFormat("yyyyMMddHHmmss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            tlv(0x18, fmt.format(calendar.time).toByteArray(Charsets.US_ASCII))
        }
    }

    private fun derSequence(vararg parts: ByteArray): ByteArray = tlv(0x30, concat(*parts))
    private fun derSet(vararg parts: ByteArray): ByteArray = tlv(0x31, concat(*parts))
    private fun derOctetString(content: ByteArray): ByteArray = tlv(0x04, content)
    private fun derUtf8String(text: String): ByteArray = tlv(0x0C, text.toByteArray(Charsets.UTF_8))
    private fun derBooleanTrue(): ByteArray = byteArrayOf(0x01, 0x01, 0xFF.toByte())
    private fun derExplicit(tag: Int, content: ByteArray): ByteArray = tlv(0xA0 or (tag and 0x1F), content)

    private fun derInteger(unsignedBigEndian: ByteArray): ByteArray {
        var start = 0
        while (start < unsignedBigEndian.size - 1 &&
            unsignedBigEndian[start].toInt() == 0 &&
            (unsignedBigEndian[start + 1].toInt() and 0x80) == 0
        ) {
            start++
        }
        val trimmed = unsignedBigEndian.copyOfRange(start, unsignedBigEndian.size)
        val payload = if ((trimmed[0].toInt() and 0x80) != 0) byteArrayOf(0x00) + trimmed else trimmed
        return tlv(0x02, payload)
    }

    private fun derBitString(bytes: ByteArray, unusedBits: Int): ByteArray =
        tlv(0x03, byteArrayOf((unusedBits and 0x07).toByte()) + bytes)

    private fun oid(vararg arcs: Int): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(arcs[0] * 40 + arcs[1])
        for (i in 2 until arcs.size) {
            writeBase128(out, arcs[i])
        }
        return tlv(0x06, out.toByteArray())
    }

    private fun writeBase128(out: ByteArrayOutputStream, value: Int) {
        val buf = IntArray(5)
        var n = value
        var count = 0
        do {
            buf[count++] = n and 0x7F
            n = n ushr 7
        } while (n > 0)
        for (i in count - 1 downTo 0) {
            val b = if (i > 0) buf[i] or 0x80 else buf[i]
            out.write(b)
        }
    }

    private fun tlv(tag: Int, content: ByteArray): ByteArray {
        val len = content.size
        val lenBytes = when {
            len < 128 -> byteArrayOf(len.toByte())
            len <= 0xFF -> byteArrayOf(0x81.toByte(), len.toByte())
            len <= 0xFFFF -> byteArrayOf(0x82.toByte(), (len ushr 8).toByte(), len.toByte())
            else -> byteArrayOf(0x83.toByte(), (len ushr 16).toByte(), (len ushr 8).toByte(), len.toByte())
        }
        return byteArrayOf(tag.toByte()) + lenBytes + content
    }

    private fun concat(vararg parts: ByteArray): ByteArray {
        val total = parts.sumOf { it.size }
        val result = ByteArray(total)
        var offset = 0
        for (p in parts) {
            System.arraycopy(p, 0, result, offset, p.size)
            offset += p.size
        }
        return result
    }

    private fun wrapPem(label: String, base64NoWrap: String): String = buildString {
        append("-----BEGIN ").append(label).append("-----\n")
        var i = 0
        while (i < base64NoWrap.length) {
            val end = minOf(i + 64, base64NoWrap.length)
            append(base64NoWrap, i, end).append('\n')
            i = end
        }
        append("-----END ").append(label).append("-----\n")
    }
}
