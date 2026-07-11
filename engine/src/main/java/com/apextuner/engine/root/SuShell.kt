package com.apextuner.engine.root

import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.io.SuFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Root shell backend built on libsu. Used when [RootCapabilities.hasRoot] is
 * true. libsu pools shell sessions, so the first call may take ~100 ms but
 * subsequent calls reuse the session.
 *
 * All commands run with [Dispatchers.IO] because libsu blocks on a pipe.
 */
@Singleton
class SuShell @Inject constructor() : ShellExecutor {

    override val isPrivileged: Boolean
        get() = try { Shell.getShell().isRoot } catch (t: Throwable) { false }

    override suspend fun exec(cmd: String): ShellResult = withContext(Dispatchers.IO) {
        try {
            val result = Shell.cmd(cmd).exec()
            ShellResult(
                exitCode = result.code,
                stdout = result.out,
                stderr = result.err
            )
        } catch (t: Throwable) {
            ShellResult.failure(t.message ?: "su exec failed")
        }
    }

    override suspend fun execScript(script: String): ShellResult = withContext(Dispatchers.IO) {
        try {
            val result = Shell.cmd(script).exec()
            ShellResult(
                exitCode = result.code,
                stdout = result.out,
                stderr = result.err
            )
        } catch (t: Throwable) {
            ShellResult.failure(t.message ?: "su script failed")
        }
    }

    override suspend fun readFile(path: String): String? = withContext(Dispatchers.IO) {
        try {
            val file = SuFile.open(path)
            if (!file.exists()) return@withContext null
            file.readText().trim().takeIf { it.isNotEmpty() }
        } catch (t: Throwable) {
            null
        }
    }

    override suspend fun writeFile(path: String, value: String): Boolean = withContext(Dispatchers.IO) {
        try {
            // Use a single shell line so we get one atomic write + can read the exit code.
            // echo without trailing newline is the canonical sysfs write idiom.
            val escaped = value.replace("'", "'\\''")
            val cmd = "echo -n '$escaped' > '$path'"
            Shell.cmd(cmd).exec().code == 0
        } catch (t: Throwable) {
            false
        }
    }
}
