package com.fly8.clone.connection

import android.content.Context
import android.os.Build
import android.sun.security.x509.AlgorithmId
import android.sun.security.x509.CertificateAlgorithmId
import android.sun.security.x509.CertificateExtensions
import android.sun.security.x509.CertificateIssuerName
import android.sun.security.x509.CertificateSerialNumber
import android.sun.security.x509.CertificateSubjectName
import android.sun.security.x509.CertificateValidity
import android.sun.security.x509.CertificateVersion
import android.sun.security.x509.CertificateX509Key
import android.sun.security.x509.KeyIdentifier
import android.sun.security.x509.PrivateKeyUsageExtension
import android.sun.security.x509.SubjectKeyIdentifierExtension
import android.sun.security.x509.X500Name
import android.sun.security.x509.X509CertImpl
import android.sun.security.x509.X509CertInfo
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import io.github.muntashirakon.adb.AdbStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import java.util.Random
import java.util.concurrent.TimeUnit

class Fly8AdbConnectionManager private constructor(
    private val context: Context
) : AbsAdbConnectionManager() {

    private val key: PrivateKey
    private val certificate: Certificate

    init {
        setApi(Build.VERSION.SDK_INT)
        setTimeout(8, TimeUnit.SECONDS)
        setThrowOnUnauthorised(false)

        val material = KeyMaterial.loadOrCreate(context)
        key = material.privateKey
        certificate = material.certificate
    }

    override fun getPrivateKey(): PrivateKey = key

    override fun getCertificate(): Certificate = certificate

    override fun getDeviceName(): String =
        ("FLY8-" + Build.MANUFACTURER + "-" + Build.MODEL)
            .replace(Regex("[^A-Za-z0-9._-]"), "-")
            .take(60)

    fun pairDevice(host: String, port: Int, code: String): Result<Unit> = runCatching {
        require(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            "Wireless debugging pairing requires Android 11 or newer."
        }
        require(host.isNotBlank()) { "Wireless debugging address is missing." }
        require(port in 1..65535) { "Wireless debugging pairing port is invalid." }
        require(code.matches(Regex("\\d{6}"))) {
            "Enter the six-digit Wireless debugging pairing code."
        }
        pair(host, port, code)
    }

    fun connectAutomatically(): Result<Unit> = runCatching {
        if (isConnected) return@runCatching

        val prefs = context.getSharedPreferences("fly8_connection", Context.MODE_PRIVATE)
        val savedHost = prefs.getString("host", "").orEmpty()
        val savedPort = prefs.getInt("connection_port", 0)

        if (savedHost.isNotBlank() && savedPort in 1..65535) {
            runCatching { connect(savedHost, savedPort) }
            if (isConnected) return@runCatching
        }

        connectTls(context, 8_000)
        check(isConnected) {
            "Wireless debugging endpoint was not found. Keep Wireless debugging enabled and on the same network."
        }
    }

    fun connectManually(host: String, port: Int): Result<Unit> = runCatching {
        require(host.isNotBlank()) { "Wireless debugging address is missing." }
        require(port in 1..65535) { "Wireless debugging connection port is invalid." }
        connect(host, port)
        check(isConnected) { "ADB connection was not established." }
    }

    fun runCommand(command: String): BridgeResult {
        return try {
            if (!isConnected) {
                val auto = connectAutomatically()
                if (auto.isFailure) {
                    return BridgeResult(
                        -1,
                        error = auto.exceptionOrNull()?.message
                            ?: "Wireless debugging is not connected."
                    )
                }
            }

            val stream: AdbStream = openStream("shell:")
            try {
                val input = stream.openInputStream()
                val output = stream.openOutputStream()
                val marker = "__FLY8_RC__"
                output.write(
                    (command + "\necho " + marker + ":$?\nexit\n")
                        .toByteArray(StandardCharsets.UTF_8)
                )
                output.flush()

                val bytes = ByteArray(8192)
                val result = StringBuilder()
                while (true) {
                    val count = input.read(bytes, 0, bytes.size)
                    if (count < 0) break
                    if (count > 0) result.append(String(bytes, 0, count, StandardCharsets.UTF_8))
                }

                val raw = result.toString().trim()
                val match = Regex(marker + ":(\\d+)").find(raw)
                val code = match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
                val clean = raw.replace(
                    Regex("(?m)^" + Regex.escape(marker) + ":\\d+\\s*$"),
                    ""
                ).trim()

                BridgeResult(
                    code = code,
                    output = clean,
                    error = if (code == 0) "" else clean.ifBlank { "ADB shell command failed." }
                )
            } finally {
                runCatching { stream.close() }
            }
        } catch (t: Throwable) {
            disconnectQuietly()
            BridgeResult(-1, error = t.message ?: t.javaClass.simpleName)
        }
    }

    fun disconnectQuietly() {
        runCatching { disconnect() }
    }

    companion object {
        @Volatile
        private var instance: Fly8AdbConnectionManager? = null

        fun get(context: Context): Fly8AdbConnectionManager =
            instance ?: synchronized(this) {
                instance ?: Fly8AdbConnectionManager(context.applicationContext).also {
                    instance = it
                }
            }
    }
}

private data class AdbKeyMaterial(
    val privateKey: PrivateKey,
    val certificate: Certificate
)

private object KeyMaterial {
    private const val KEY_FILE = "fly8_adb_private.der"
    private const val CERT_FILE = "fly8_adb_cert.der"

    fun loadOrCreate(context: Context): AdbKeyMaterial {
        val keyFile = File(context.filesDir, KEY_FILE)
        val certFile = File(context.filesDir, CERT_FILE)

        if (keyFile.exists() && certFile.exists()) {
            runCatching {
                val privateKey = KeyFactory.getInstance("RSA").generatePrivate(
                    PKCS8EncodedKeySpec(keyFile.readBytes())
                )
                val certificate = CertificateFactory.getInstance("X.509")
                    .generateCertificate(certFile.inputStream())
                return AdbKeyMaterial(privateKey, certificate)
            }
        }

        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(2048, SecureRandom())
        val pair = generator.generateKeyPair()

        val certificate = generateCertificate(pair.public, pair.private)
        keyFile.writeBytes(pair.private.encoded)
        certFile.outputStream().use { it.write(certificate.encoded) }

        return AdbKeyMaterial(pair.private, certificate)
    }

    private fun generateCertificate(
        publicKey: PublicKey,
        privateKey: PrivateKey
    ): Certificate {
        val subject = "CN=FLY8"
        val algorithmName = "SHA512withRSA"
        val notBefore = Date(System.currentTimeMillis() - 60_000)
        val notAfter = Date(System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000)

        val extensions = CertificateExtensions()
        extensions.set(
            "SubjectKeyIdentifier",
            SubjectKeyIdentifierExtension(KeyIdentifier(publicKey).identifier)
        )
        extensions.set(
            "PrivateKeyUsage",
            PrivateKeyUsageExtension(notBefore, notAfter)
        )

        val name = X500Name(subject)
        val info = X509CertInfo()
        info.set("version", CertificateVersion(2))
        info.set(
            "serialNumber",
            CertificateSerialNumber(Random().nextInt() and Int.MAX_VALUE)
        )
        info.set("algorithmID", CertificateAlgorithmId(AlgorithmId.get(algorithmName)))
        info.set("subject", CertificateSubjectName(name))
        info.set("key", CertificateX509Key(publicKey))
        info.set("validity", CertificateValidity(notBefore, notAfter))
        info.set("issuer", CertificateIssuerName(name))
        info.set("extensions", extensions)

        return X509CertImpl(info).also {
            it.sign(privateKey, algorithmName)
        }
    }
}
