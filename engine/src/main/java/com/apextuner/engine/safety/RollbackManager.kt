package com.apextuner.engine.safety

import com.apextuner.data.model.TunerLog
import com.apextuner.data.repository.LogRepository
import com.apextuner.engine.root.ShellExecutor
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns transaction lifecycle and rollback. Every privileged controller in
 * :engine (CPU, GPU, eventually Display) routes its writes through
 * [begin] + [rollback] so that a partial-apply never leaves the device in an
 * inconsistent state.
 *
 * Rollback writes the captured [CapturedValue.original] back to its path.
 * Paths whose original value was null (unreadable) are skipped — this is
 * intentional, because re-writing nothing is safer than writing garbage.
 */
@Singleton
class RollbackManager @Inject constructor(
    private val logs: LogRepository
) {
    fun begin(id: String): Transaction = Transaction(id)

    suspend fun rollback(tx: Transaction, shell: ShellExecutor) {
        if (tx.isCommitted) return
        var restored = 0
        var skipped = 0
        // Reverse order — undo the most recent write first.
        for (cv in tx.capturedPaths.reversed()) {
            val original = cv.original
            if (original == null) { skipped++; continue }
            try {
                shell.writeFile(cv.path, original)
                restored++
            } catch (t: Throwable) {
                skipped++
                logs.log(
                    level = TunerLog.Level.ERROR,
                    category = TunerLog.Category.SAFETY,
                    message = "Rollback write failed for ${cv.path}",
                    detail = t.message
                )
            }
        }
        logs.log(
            level = TunerLog.Level.WARN,
            category = TunerLog.Category.SAFETY,
            message = "Rolled back transaction ${tx.id}: restored=$restored skipped=$skipped",
            detail = tx.capturedPaths.joinToString("\n") { "${it.path} => ${it.original ?: "<null>"}" }
        )
    }
}
