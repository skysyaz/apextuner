package com.apextuner.engine.root

/**
 * Result of a single privileged shell invocation. Mirrors libsu's output but
 * is a plain data class so it can be mocked in unit tests without Android.
 */
data class ShellResult(
    val exitCode: Int,
    val stdout: List<String>,
    val stderr: List<String>
) {
    val isSuccess: Boolean get() = exitCode == 0
    val stdoutText: String get() = stdout.joinToString("\n")
    val stderrText: String get() = stderr.joinToString("\n")

    companion object {
        val EMPTY = ShellResult(0, emptyList(), emptyList())
        fun failure(message: String) = ShellResult(1, emptyList(), listOf(message))
    }
}

/**
 * Abstraction over the three execution backends (root via libsu, Shizuku via
 * `IShell`, and a plain Runtime exec fallback for non-root reads). All
 * privileged sysfs writes flow through this interface so we can swap backends
 * in tests and audit every privileged operation in one place.
 */
interface ShellExecutor {
    /** Whether this executor can actually run privileged commands. */
    val isPrivileged: Boolean

    /**
     * Run [cmd]. The command is treated as a single shell line; multi-line
     * scripts should be passed via [ShellExecutor.execScript].
     */
    suspend fun exec(cmd: String): ShellResult

    /**
     * Run a multi-line shell script. Useful for transactional apply + verify
     * blocks that must execute atomically inside one root shell session.
     */
    suspend fun execScript(script: String): ShellResult

    /**
     * Read a single sysfs/procfs file. Returns the trimmed contents or null
     * on failure. Prefer this over [exec] + `cat` because libsu's IO module
     * does a direct file read which is faster and avoids quoting pitfalls.
     */
    suspend fun readFile(path: String): String?

    /**
     * Write a single value to a sysfs file. Returns true on success. The
     * caller is responsible for ensuring the value is valid for the target
     * node — invalid writes will return false rather than throw.
     */
    suspend fun writeFile(path: String, value: String): Boolean
}
