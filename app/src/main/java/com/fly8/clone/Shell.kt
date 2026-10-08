package com.fly8.clone

import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

data class ShellResult(val code: Int, val out: String, val err: String)

object Shell {
    fun run(command: String): ShellResult {
        if (!Shizuku.pingBinder()) return ShellResult(-10, "", "Shizuku is not running.")
        if (Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return ShellResult(-11, "", "Shizuku permission is not granted.")
        }
        return try {
            val p = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
            val out = StringBuilder()
            val err = StringBuilder()
            val a = Thread {
                BufferedReader(InputStreamReader(p.inputStream)).useLines { xs -> xs.forEach { out.append(it).append('\n') } }
            }
            val b = Thread {
                BufferedReader(InputStreamReader(p.errorStream)).useLines { xs -> xs.forEach { err.append(it).append('\n') } }
            }
            a.start(); b.start()
            if (!p.waitFor(25, TimeUnit.SECONDS)) {
                p.destroy()
                return ShellResult(124, out.toString(), "Command timed out.")
            }
            a.join(1000); b.join(1000)
            ShellResult(p.exitValue(), out.toString().trim(), err.toString().trim())
        } catch (t: Throwable) {
            ShellResult(-1, "", t.message ?: t.javaClass.simpleName)
        }
    }
}
