package com.fly8.clone.backend

import android.os.Build
import com.fly8.clone.Shell
import com.fly8.clone.model.CloneEntry

class ShizukuCloneBackend : CloneBackend {
    override fun create(packageName: String, label: String): Result<CloneEntry> {
        if (Build.VERSION.SDK_INT < 35) {
            return Result.failure(IllegalStateException("Native clone profiles require Android 15 or newer."))
        }
        val made = Shell.run("pm create-user --user-type android.os.usertype.profile.CLONE --profileOf 0 FLY8")
        if (made.code != 0) {
            return Result.failure(IllegalStateException(made.err.ifBlank { "Clone profile creation failed." }))
        }
        val text = made.out + "\n" + made.err
        val id = Regex("(?i)(?:Success: created user id |created user id )([0-9]+)")
            .find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: return Result.failure(IllegalStateException("Android did not return the new clone profile ID."))

        val installed = Shell.run("pm install-existing --user " + id + " " + packageName)
        if (installed.code != 0) {
            Shell.run("pm remove-user " + id)
            return Result.failure(IllegalStateException(installed.err.ifBlank { "Could not install the app into the clone." }))
        }
        return Result.success(CloneEntry(packageName, label, id))
    }

    override fun launch(clone: CloneEntry): Result<Unit> = runCommand(
        "am start-user " + clone.userId + " >/dev/null 2>&1; monkey --user " + clone.userId + " -p " + clone.packageName + " -c android.intent.category.LAUNCHER 1"
    )

    override fun stop(clone: CloneEntry): Result<Unit> =
        runCommand("am stop-user -f " + clone.userId)

    override fun clear(clone: CloneEntry): Result<Unit> =
        runCommand("pm clear --user " + clone.userId + " " + clone.packageName)

    override fun remove(clone: CloneEntry): Result<Unit> =
        runCommand("pm remove-user " + clone.userId)

    private fun runCommand(command: String): Result<Unit> {
        val result = Shell.run(command)
        return if (result.code == 0) Result.success(Unit)
        else Result.failure(IllegalStateException(result.err.ifBlank { "Command failed." }))
    }
}
