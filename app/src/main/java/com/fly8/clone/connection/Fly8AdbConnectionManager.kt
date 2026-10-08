package com.fly8.clone.connection

import android.content.Context
import android.os.Build
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import io.github.muntashirakon.adb.AdbStream
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jca.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Security
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import java.util.concurrent.TimeUnit

class Fly8AdbConnectionManager private constructor(
    private val context: Context
) : AbsAdbConnectionManager() {

    private val key: PrivateKey
    private val certificate: Certificate

    init {
        ensureBcProvider()
        setApi(Build.VERSION.SDK_INT)
        setTimeout(8, TimeUnit.SECONDS)
        setThrowOnUnauthorised(false)

        val material = KeyMaterial.loadOrCreate(context)
        key = material.first
        certificate = material.second
    }

    override fun getPrivateKey(): PrivateKey = key

    override fun getCertificate(): Certificate = certificate

    override fun getDeviceName(): String =
        ("FLY8-" + Build.MANUFACTURER + "-" + Build.MODEL)
            .replace(Regex("[^A-Za-z0-9._-]"), "-")
            .take(60)

    fun pairDevice(host: String, port: Int, code: String): Result<Unit> = runCatching {
        require(host.isNotBlank()) { "Wireless debugging address is missing." }
        require(port in 1..65535) { "Wireless debugging pairing port is invalid." }
        require(code.matches(Regex("\\d{6}"))) { "Enter the six-digit Wireless debugging pairing code." }
        pair(host, port, code)
    }

    fun connectAutomatically(): Result<Unit> = runCatching {
        if (!isConnected) {
            connectTls(context, 8_000)
        }
        check(isConnected) { "Wireless debugging endpoint was not found." }
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
                    return BridgeResult(-1, error = auto.exceptionOrNull()?.message ?: "Wireless debugging is not connected.")
                }
            }

            val stream: AdbStream = openStream("shell:")
            try {
                val shellInput = stream.openInputStream()
                val shellOutput = stream.openOutputStream()
                val marker = "__FLY8_RC__"
                shellOutput.write(
                    (command + "\necho " + marker + ":$?\nexit\n")
                        .toByteArray(StandardCharsets.UTF_8)
                )
                shellOutput.flush()

                val bytes = ByteArray(8192)
                val result = StringBuilder()
                while (true) {
                    val count = shellInput.read(bytes, 0, bytes.size)
                    if (count < 0) break
                    if (count > 0) result.append(String(bytes, 0, count, StandardCharsets.UTF_8))
                }

                val raw = result.toString().trim()
                val match = Regex(marker + ":(\\d+)").find(raw)
                val code = match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
                val output = raw.replace(Regex("(?m)^" + Regex.escape(marker) + ":\\d+\\s*$"), "").trim()

                BridgeResult(
                    code = code,
                    output = output,
                    error = if (code == 0) "" else output.ifBlank { "ADB shell command failed." }
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

        private fun ensureBcProvider() {
            if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
                Security.addProvider(BouncyCastleProvider())
            }
        }
    }
}

private object KeyMaterial {
    private const val KEY_FILE = "fly8_adb_private.der"
    private const val CERT_FILE = "fly8_adb_cert.der"

    fun loadOrCreate(context: Context): Pair<PrivateKey, Certificate> {
        val keyFile = File(context.filesDir, KEY_FILE)
        val certFile = File(context.filesDir, CERT_FILE)

        if (keyFile.exists() && certFile.exists()) {
            try {
                val keySpec = PKCS8EncodedKeySpec(keyFile.readBytes())
                val privateKey = KeyFactory.getInstance("RSA").generatePrivate(keySpec)
                val certificate = CertificateFactory.getInstance("X.509")
                    .generateCertificate(certFile.inputStream())
                return privateKey to certificate
            } catch (_: Throwable) {
                runCatching { keyFile.delete() }
                runCatching { certFile.delete() }
            }
        }

        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(2048, SecureRandom())
        val pair = generator.generateKeyPair()

        val subject = X500Name("CN=FLY8")
        val now = System.currentTimeMillis()
        val builder = JcaX509v3CertificateBuilder(
            subject,
            BigInteger(96, SecureRandom()),
            Date(now - 60_000),
            Date(now + 3_650L * 24L * 60L * 60L * 1_000L),
            subject,
            pair.public
        )
        val signer = JcaContentSignerBuilder("SHA256withRSA")
            .setProvider(BouncyCastleProvider.PROVIDER_NAME)
            .build(pair.private)
        val certificate = JcaX509CertificateConverter()
            .setProvider(BouncyCastleProvider.PROVIDER_NAME)
            .getCertificate(builder.build(signer))

        keyFile.writeBytes(pair.private.encoded)
        certFile.writeBytes(certificate.encoded)
        return pair.private to certificate
    }
}
