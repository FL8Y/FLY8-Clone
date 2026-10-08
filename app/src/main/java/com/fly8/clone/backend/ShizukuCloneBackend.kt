package com.fly8.clone.backend

import android.os.Build
import com.fly8.clone.Shell
import com.fly8.clone.model.CloneEntry
import com.fly8.clone.model.ProfileKind

class ShizukuCloneBackend : CloneBackend {

    override fun create(packageName: String, label: String): Result<CloneEntry> {
        if (Build.VERSION.SDK_INT < 35) {
            return createSecondary(packageName, label).recoverCatching {
                throw IllegalStateException("Android 15+ is required for the native clone-profile path, and this device also rejected the secondary-user fallback.")
            }
        }

        val cloneResult = createProfile(
            kind = ProfileKind.CLONE_PROFILE,
            command = "pm create-user --user-type android.os.usertype.profile.CLONE --profileOf 0 " + safeName(label)
        )

        if (cloneResult.isSuccess) {
            val id = cloneResult.getOrThrow()
            return installInto(id, packageName, label, ProfileKind.CLONE_PROFILE)
        }

        return createSecondary(packageName, label).fold(
            onSuccess = { Result.success(it) },
            onFailure = {
                Result.failure(
                    IllegalStateException(
                        "Android could not create a native clone profile (" +
                            (cloneResult.exceptionOrNull()?.message ?: "clone slot unavailable") +
                            ") and the secondary-user fallback was also rejected (" +
                            (it.message ?: "unknown error") +
                            "). This is a device/OEM capability limit, not a FLY8 account restriction."
                    )
                )
            }
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

    private fun createSecondary(packageName: String, label: String): Result<CloneEntry> {
        val result = createProfile(
            kind = ProfileKind.SECONDARY_USER,
            command = "pm create-user --user-type android.os.usertype.full.SECONDARY " + safeName(label)
        )
        return result.fold(
            onSuccess = { installInto(it, packageName, label, ProfileKind.SECONDARY_USER) },
            onFailure = { Result.failure(it) }
        )
    }

    private fun createProfile(kind: ProfileKind, command: String): Result<Int> {
        val result = Shell.run(command)
        if (result.code != 0) {
            return Result.failure(
                IllegalStateException(result.err.ifBlank { result.out.ifBlank { "Android rejected profile creation." } })
            )
        }

        val id = Regex("(?i)(?:Success: created user id |created user id |UserInfo\\{)([0-9]+)")
            .find(result.out + "\n" + result.err)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()

        return if (id != null) Result.success(id)
        else Result.failure(IllegalStateException("Android did not return a valid " + kind.name.lowercase() + " user ID."))
    }

    private fun installInto(
        userId: Int,
        packageName: String,
        label: String,
        kind: ProfileKind
    ): Result<CloneEntry> {
        val installed = Shell.run("pm install-existing --user " + userId + " " + packageName)
        if (installed.code != 0) {
            Shell.run("pm remove-user " + userId)
            return Result.failure(
                IllegalStateException(
                    installed.err.ifBlank {
                        installed.out.ifBlank { "Could not install the app into the new " + kind.name.lowercase() + "." }
                    }
                )
            )
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

    private fun safeName(label: String): String {
        val cleaned = label.replace(Regex("[^A-Za-z0-9._-]"), "_").take(28).ifBlank { "Session" }
        return "FLY8-" + cleaned
    }

    private fun runCommand(command: String): Result<Unit> {
        val result = Shell.run(command)
        return if (result.code == 0) Result.success(Unit)
        else Result.failure(IllegalStateException(result.err.ifBlank { result.out.ifBlank { "Command failed." } }))
    }
}
