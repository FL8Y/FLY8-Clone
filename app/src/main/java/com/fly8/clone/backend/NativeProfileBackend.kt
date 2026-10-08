package com.fly8.clone.backend

import android.os.Build
import com.fly8.clone.connection.CommandBridge
import com.fly8.clone.model.CloneEntry
import com.fly8.clone.model.ProfileKind

class NativeProfileBackend(private val bridge: CommandBridge) : CloneBackend {

    override fun create(packageName: String, label: String): Result<CloneEntry> {
        val ready = bridge.check()
        if (!ready.ok) return Result.failure(IllegalStateException(ready.message))

        val cloneAttempt = createProfile(
            command = "pm create-user --user-type android.os.usertype.profile.CLONE --profileOf 0 " + safeName(label),
            kind = ProfileKind.CLONE_PROFILE,
            packageName = packageName,
            label = label
        )

        if (cloneAttempt.isSuccess) return cloneAttempt

        // Android clone profiles are intentionally limited. A separate secondary user is
        // a different supported isolation primitive on devices that expose multi-user support.
        val secondaryAttempt = createProfile(
            command = "pm create-user --user-type android.os.usertype.full.SECONDARY " + safeName(label),
            kind = ProfileKind.SECONDARY_USER,
            packageName = packageName,
            label = label
        )

        if (secondaryAttempt.isSuccess) return secondaryAttempt

        val cloneError = cloneAttempt.exceptionOrNull()?.message ?: "clone profile rejected"
        val secondaryError = secondaryAttempt.exceptionOrNull()?.message ?: "secondary user rejected"
        return Result.failure(
            IllegalStateException(
                "Android rejected both session backends. Clone profile: " +
                    cloneError + ". Secondary user: " + secondaryError +
                    ". This is a device/OEM capability limit."
            )
        )
    }

    override fun launch(clone: CloneEntry): Result<Unit> =
        runCommand(
            "am start-user " + clone.userId +
                " >/dev/null 2>&1; " +
                "monkey --user " + clone.userId +
                " -p " + clone.packageName +
                " -c android.intent.category.LAUNCHER 1"
        )

    override fun stop(clone: CloneEntry): Result<Unit> =
        runCommand("am stop-user -f " + clone.userId)

    override fun clear(clone: CloneEntry): Result<Unit> =
        runCommand("pm clear --user " + clone.userId + " " + clone.packageName)

    override fun remove(clone: CloneEntry): Result<Unit> =
        runCommand("pm remove-user " + clone.userId)

    private fun createProfile(
        command: String,
        kind: ProfileKind,
        packageName: String,
        label: String
    ): Result<CloneEntry> {
        if (kind == ProfileKind.CLONE_PROFILE && Build.VERSION.SDK_INT < 35) {
            return Result.failure(IllegalStateException("Native clone profiles require Android 15+."))
        }

        val result = bridge.run(command)
        if (!result.ok) {
            return Result.failure(IllegalStateException(result.message))
        }

        val id = Regex("(?i)(?:Success: created user id |created user id |UserInfo\\{)([0-9]+)")
            .find(result.output + "\n" + result.error)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?: return Result.failure(IllegalStateException("Android did not return a valid profile ID."))

        val installed = bridge.run("pm install-existing --user " + id + " " + packageName)
        if (!installed.ok) {
            bridge.run("pm remove-user " + id)
            return Result.failure(IllegalStateException(installed.message))
        }

        return Result.success(
            CloneEntry(
                packageName = packageName,
                label = label,
                userId = id,
                profileKind = kind
            )
        )
    }

    private fun runCommand(command: String): Result<Unit> {
        val result = bridge.run(command)
        return if (result.ok) Result.success(Unit)
        else Result.failure(IllegalStateException(result.message))
    }

    private fun safeName(label: String): String {
        val cleaned = label.replace(Regex("[^A-Za-z0-9._-]"), "_").take(28).ifBlank { "Session" }
        return "FLY8-" + cleaned
    }
}
