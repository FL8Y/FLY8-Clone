package com.fly8.clone

import android.os.ParcelFileDescriptor
import moe.shizuku.server.IRemoteProcess
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

data class ShellResult(val code: Int, val out: String, val err: String)

private class RemoteProcess(private val remote: IRemoteProcess) : Process() {
    override fun getOutputStream() = ParcelFileDescriptor.AutoCloseOutputStream(remote.getOutputStream())
    override fun getInputStream(): InputStream = ParcelFileDescriptor.AutoCloseInputStream(remote.getInputStream())
    override fun getErrorStream(): InputStream = ParcelFileDescriptor.AutoCloseInputStream(remote.getErrorStream())
    override fun waitFor(): Int = remote.waitFor()
    override fun exitValue(): Int = remote.exitValue()
    override fun destroy() { remote.destroy() }
}

object Shell {
    fun run(command: String): ShellResult {
        if (!Shizuku.pingBinder()) return ShellResult(-10, "", "Shizuku is not running.")
        if (Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return ShellResult(-11, "", "Shizuku permission is not granted.")
        }
        return try {
            val service = IShizukuService.Stub.asInterface(Shizuku.getBinder())
            val remote = service.newProcess(arrayOf("sh", "-c", command), null, null)
            val p = RemoteProcess(remote)
            val out = StringBuilder()
            val err = StringBuilder()

            val a = Thread {
                BufferedReader(InputStreamReader(p.inputStream)).useLines { xs ->
                    xs.forEach { out.append(it).append('\n') }
                }
            }
            val b = Thread {
                BufferedReader(InputStreamReader(p.errorStream)).useLines { xs ->
                    xs.forEach { err.append(it).append('\n') }
                }
            }
            a.start()
            b.start()

            if (!remote.waitForTimeout(25, TimeUnit.SECONDS.toString())) {
                p.destroy()
                return ShellResult(124, out.toString(), "Command timed out.")
            }

            a.join(1000)
            b.join(1000)
            ShellResult(remote.exitValue(), out.toString().trim(), err.toString().trim())
        } catch (t: Throwable) {
            ShellResult(-1, "", t.message ?: t.javaClass.simpleName)
        }
    }
}
