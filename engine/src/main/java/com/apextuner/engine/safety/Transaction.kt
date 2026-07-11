package com.apextuner.engine.safety

/**
 * One captured sysfs value used for rollback. [original] is null when the
 * node was unreadable before the write — in that case rollback skips it.
 */
data class CapturedValue(
    val path: String,
    val original: String?
)

/**
 * A transactional batch of sysfs writes. The flow is:
 *   1. [begin] — start a new transaction
 *   2. [capture] — record the pre-write value of each path you will touch
 *   3. perform the writes (via ShellExecutor.writeFile)
 *   4. verify by re-reading
 *   5. on success → [commit]; on failure → [RollbackManager.rollback]
 *
 * Transactions are NOT thread-safe — one transaction per coroutine.
 */
class Transaction(
    val id: String,
    val startedAtMs: Long = System.currentTimeMillis()
) {
    internal val captured = mutableListOf<CapturedValue>()
    @Volatile internal var committed = false

    fun capture(path: String, original: String?) {
        if (committed) error("Transaction $id already committed")
        captured.add(CapturedValue(path, original))
    }

    fun commit() { committed = true }

    val isCommitted: Boolean get() = committed
    val capturedPaths: List<CapturedValue> get() = captured.toList()
}
