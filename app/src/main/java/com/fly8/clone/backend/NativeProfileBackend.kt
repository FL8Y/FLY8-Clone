package com.fly8.clone.backend

import android.os.Build
import com.fly8.clone.connection.CommandBridge
import com.fly8.clone.model.CloneEntry
import com.fly8.clone.model.ProfileKind

class NativeProfileBackend(private val bridge: CommandBridge) : CloneBackend {

    override fun create(packageName: String, label: String): Result<CloneEntry> {
        val ready = bridge.check()
        if (!ready.ok) return Result.failure(IllegalStateException(ready.message))

        // Android exposes a clone-profile user as a single extra profile for the main user
        // on supported devices. Reuse an existing FLY8-created clone profile instead of
        // creating a new profile for every session.
        findReusableFly8CloneProfile()?.let { existing ->
            val installed = bridge.run("pm list packages --user " + existing.userId + " " + packageName)
            if (!installed.ok) {
                return Result.failure(IllegalStateException(installed.message))
            }

            if (installed.output.lines().any { it.trim() == "package:$packageName" }) {
                // A clone profile cannot contain two installs of the same package.
                // Continue to the secondary-user path below.
            } else {
                val attached = installInto(
                    existing.userId,
                    packageName,
                    label,
                    ProfileKind.CLONE_PROFILE,
                    removeUserOnFailure = false
                )
                if (attached.isSuccess) return attached
            }
        }

        val cloneAttempt = createProfile(
            command = "pm create-user --user-type android.os.usertype.profile.CLONE --profileOf 0 " + safeName(label),
            kind = ProfileKind.CLONE_PROFILE,
            packageName = packageName,
            label = label
        )

        if (cloneAttempt.isSuccess) return cloneAttempt

        // A second copy of the same package, or an OEM clone-profile quota, requires
        // a different Android user space when the device permits multi-user creation.
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
                "Android could not create another isolated session. " +
                    "Clone profile: " + cloneError + ". " +
                    "Secondary user: " + secondaryError + ". " +
                    "This is a device/OEM capability limit."
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
        runCommand("am force-stop --user " + clone.userId + " " + clone.packageName)

    override fun clear(clone: CloneEntry): Result<Unit> =
        runCommand("pm clear --user " + clone.userId + " " + clone.packageName)

    override fun remove(clone: CloneEntry): Result<Unit> =
        if (clone.profileKind == ProfileKind.SECONDARY_USER) {
            runCommand("pm remove-user " + clone.userId)
        } else {
            runCommand("pm uninstall --user " + clone.userId + " " + clone.packageName)
        }

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

        return installInto(id, packageName, label, kind, removeUserOnFailure = true)
    }

    private fun installInto(
        userId: Int,
        packageName: String,
        label: String,
        kind: ProfileKind,
        removeUserOnFailure: Boolean
    ): Result<CloneEntry> {
        val installed = bridge.run("pm install-existing --user " + userId + " " + packageName)
        if (!installed.ok) {
            if (removeUserOnFailure) bridge.run("pm remove-user " + userId)
            return Result.failure(IllegalStateException(installed.message))
        }
        return Result.success(
            CloneEntry(
                packageName = packageName,
                label = label,
                userId = userId,
                profileKind = kind
            )
        )
    }

    private fun findReusableFly8CloneProfile(): UserRecord? {
        val result = bridge.run("pm list users")
        if (!result.ok) return null

        return Regex("UserInfo\\{(\\d+):([^:}]*):([0-9a-fA-F]+)\\}")
            .findAll(result.output)
            .mapNotNull { match ->
                val id = match.groupValues.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
                val name = match.groupValues.getOrNull(2).orEmpty()
                val flags = match.groupValues.getOrNull(3)?.toIntOrNull(16) ?: return@mapNotNull null
                UserRecord(id, name, flags)
            }
            .firstOrNull { it.id != 0 && it.name.startsWith("FLY8-") && (it.flags and FLAG_PROFILE) != 0 }
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

    private data class UserRecord(
        val id: Int,
        val name: String,
        val flags: Int
    )

    companion object {
        private const val FLAG_PROFILE = 0x00001000
    }
}
