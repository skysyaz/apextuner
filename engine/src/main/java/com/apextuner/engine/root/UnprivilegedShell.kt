package com.apextuner.engine.root

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Non-root fallback for read-only access to world-readable sysfs/procfs nodes.
 * Used to read current CPU/GPU/thermal state even on devices where the user
 * has neither root nor Shizuku. All writes return false.
 */
@Singleton
class UnprivilegedShell @Inject constructor() : ShellExecutor {

    override val isPrivileged: Boolean = false

    override suspend fun exec(cmd: String): ShellResult = withContext(Dispatchers.IO) {
        try {
            val parts = if (cmd.contains(" ") || cmd.contains("|")) {
                arrayOf("sh", "-c", cmd)
            } else {
                arrayOf(cmd)
            }
            val proc = Runtime.getRuntime().exec(parts)
            val out = proc.inputStream.bufferedReader().readLines()
            val err = proc.errorStream.bufferedReader().readLines()
            val code = proc.waitFor()
            ShellResult(code, out, err)
        } catch (t: Throwable) {
            ShellResult.failure(t.message ?: "exec failed")
        }
    }

    override suspend fun execScript(script: String): ShellResult = exec(script)

    override suspend fun readFile(path: String): String? = withContext(Dispatchers.IO) {
        // Do not gate on File.canRead() — SELinux / OEM policies often make
        // canRead() return false even when open()+read succeeds (and vice versa).
        try {
            File(path).readText().trim().takeIf { it.isNotEmpty() }
        } catch (_: Throwable) {
            // Fallback via cat for paths that block java.io.File but allow shell read.
            try {
                val result = exec("cat '$path'")
                if (result.isSuccess) result.stdoutText.trim().takeIf { it.isNotEmpty() } else null
            } catch (_: Throwable) {
                null
            }
        }
    }

    override suspend fun writeFile(path: String, value: String): Boolean = false
}
