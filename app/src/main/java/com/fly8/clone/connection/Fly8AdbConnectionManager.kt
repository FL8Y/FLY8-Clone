package com.fly8.clone.connection

import android.content.Context
import android.os.Build
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
        key = material.first
        certificate = PublicKeyCertificate(material.second)
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
        require(code.matches(Regex("\d{6}"))) {
            "Enter the six-digit Wireless debugging pairing code."
        }
        pair(host, port, code)
    }

    /**
     * Reconnect in the same order as the reference UX:
     * 1. Reuse the last known connection endpoint when available.
     * 2. Fall back to Android's mDNS _adb-tls-connect service.
     *
     * Pairing and connecting are deliberately separate operations.
     */
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

private object KeyMaterial {
    private const val KEY_FILE = "fly8_adb_private.der"

    fun loadOrCreate(context: Context): Pair<PrivateKey, PublicKey> {
        val keyFile = File(context.filesDir, KEY_FILE)

        if (keyFile.exists()) {
            runCatching {
                val privateKey = KeyFactory.getInstance("RSA").generatePrivate(
                    java.security.spec.PKCS8EncodedKeySpec(keyFile.readBytes())
                )
                val rsa = privateKey as java.security.interfaces.RSAPrivateCrtKey
                val publicKey = KeyFactory.getInstance("RSA").generatePublic(
                    java.security.spec.RSAPublicKeySpec(rsa.modulus, rsa.publicExponent)
                )
                return privateKey to publicKey
            }
        }

        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(2048, SecureRandom())
        val pair = generator.generateKeyPair()
        keyFile.writeBytes(pair.private.encoded)
        return pair.private to pair.public
    }
}

private class PublicKeyCertificate(
    private val publicKey: PublicKey
) : Certificate("X.509") {
    override fun getEncoded(): ByteArray = publicKey.encoded.copyOf()

    override fun verify(key: PublicKey) {
        require(key.algorithm == publicKey.algorithm) {
            "Certificate verification is not available for the FLY8 ADB key wrapper."
        }
    }

    override fun verify(key: PublicKey, sigProvider: String) = verify(key)

    override fun toString(): String = "FLY8 ADB public-key wrapper"

    override fun getPublicKey(): PublicKey = publicKey
}
