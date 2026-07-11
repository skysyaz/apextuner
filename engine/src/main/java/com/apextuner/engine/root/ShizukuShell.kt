package com.apextuner.engine.root

import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shizuku backend. Shizuku exposes a binder that hands us an `IShell`
 * service equivalent to `adb shell` — enough for WRITE_SECURE_SETTINGS and
 * a subset of sysfs reads, but NOT enough to write to most cpufreq nodes on
 * production kernels (those need true root).
 *
 * For sysfs writes Shizuku is therefore only used as a read-only fallback
 * when root is unavailable. The UI surfaces a "Root Required" banner on
 * write controls when only Shizuku is available.
 *
 * NOTE: the real Shizuku `Shizuku.newProcess` API requires the hidden
 * `android.os.IShell` binder. This implementation uses the public reflection
 * shim documented in Shizuku's README; if the binder is unavailable (older
 * Shizuku server) every method degrades to a failure result.
 */
@Singleton
class ShizukuShell @Inject constructor() : ShellExecutor {

    override val isPrivileged: Boolean
        get() = try {
            Shizuku.pingBinder() && hasPermission()
        } catch (t: Throwable) { false }

    private fun hasPermission(): Boolean = try {
        if (Shizuku.isPreV11()) true
        else Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
    } catch (t: Throwable) { false }

    override suspend fun exec(cmd: String): ShellResult = withContext(Dispatchers.IO) {
        if (!isPrivileged) return@withContext ShellResult.failure("Shizuku not authorized")
        try {
            val proc = newProcess(cmd)
            val out = proc.inputStream.bufferedReader().readLines()
            val err = proc.errorStream.bufferedReader().readLines()
            val code = proc.waitFor()
            ShellResult(code, out, err)
        } catch (t: Throwable) {
            ShellResult.failure(t.message ?: "shizuku exec failed")
        }
    }

    override suspend fun execScript(script: String): ShellResult =
        exec("sh -c '${script.replace("'", "'\\''")}'")

    override suspend fun readFile(path: String): String? = withContext(Dispatchers.IO) {
        if (!isPrivileged) return@withContext null
        val res = exec("cat '$path' 2>/dev/null")
        if (!res.isSuccess) null else res.stdoutText.trim().takeIf { it.isNotEmpty() }
    }

    override suspend fun writeFile(path: String, value: String): Boolean = withContext(Dispatchers.IO) {
        if (!isPrivileged) return@withContext false
        val escaped = value.replace("'", "'\\''")
        exec("echo -n '$escaped' > '$path'").isSuccess
    }

    /**
     * Reflectively call `Shizuku.newProcess(String[] cmd, String[] env, String dir)`.
     * Wrapped so failures degrade gracefully on older Shizuku servers.
     */
    private fun newProcess(cmd: String): Process {
        val method = Shizuku::class.java.methods.firstOrNull { it.name == "newProcess" }
            ?: error("Shizuku.newProcess unavailable (server too old)")
        val args = arrayOf("sh", "-c", cmd)
        return method.invoke(null, args, null, null) as Process
    }
}

/** Sentinel Build reference to silence unused-import warnings in some IDEs. */
internal val buildSdkInt: Int get() = Build.VERSION.SDK_INT
