package com.apextuner.engine.cpu

import com.apextuner.data.datastore.SettingsDataStore
import com.apextuner.engine.root.ShellSelector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Polls CPU state at ~1 Hz and emits [CpuSnapshot]s.
 *
 * Load percentages come from `/proc/stat` deltas and work without root on
 * every Android device. Topology / cur-freq sysfs reads are best-effort —
 * when they fail we still emit aggregate load so the dashboard is never stuck at 0.
 */
@Singleton
class CpuMonitor @Inject constructor(
    private val controller: CpuController,
    private val selector: ShellSelector,
    private val settings: SettingsDataStore
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(CpuSnapshot.EMPTY)
    val snapshot: StateFlow<CpuSnapshot> = _state.asStateFlow()

    private var lastProcStat: Map<Int, LongArray>? = null
    private var running = false

    fun start() {
        if (running) return
        running = true
        scope.launch { loop() }
    }

    fun stop() { running = false }

    fun tick(): Flow<CpuSnapshot> = flow {
        var last: Map<Int, LongArray>? = null
        while (true) {
            val snap = readOnce(prevProcStat = last)
            last = readProcStatPerCpu()
            if (snap != null) emit(snap)
            delay(1000L)
        }
    }

    private suspend fun loop() {
        while (running) {
            val snap = readOnce(lastProcStat)
            lastProcStat = readProcStatPerCpu()
            if (snap != null) _state.value = snap
            delay(1000L)
        }
    }

    private suspend fun readOnce(prevProcStat: Map<Int, LongArray>?): CpuSnapshot? {
        val curProcStat = readProcStatPerCpu()
        if (curProcStat.isEmpty() && prevProcStat == null) return null

        val loads = computeLoads(prevProcStat, curProcStat)
        val clusters = runCatching { controller.readCurrent() }.getOrDefault(emptyList())

        if (clusters.isEmpty()) {
            val avgLoad = if (loads.isEmpty()) 0f else loads.values.average().toFloat()
            return CpuSnapshot(emptyList(), avgLoad, 0f, System.currentTimeMillis())
        }

        val shell = selector.best()
        val temps = mutableListOf<Float>()
        val clusterStates = clusters.map { cluster ->
            val curFreqs = cluster.cores.map { cpu ->
                shell.readFile(CpuPaths.scalingCurFreq(cpu))?.toLongOrNull() ?: 0L
            }
            val coreLoads = cluster.cores.map { cpu -> loads[cpu] ?: 0f }
            val temp = 0f
            CpuClusterState(
                clusterId = cluster.clusterId, label = cluster.label, cores = cluster.cores,
                onlineMask = cluster.onlineMask, governor = cluster.governor,
                minFreqKHz = cluster.minFreqKHz, maxFreqKHz = cluster.maxFreqKHz,
                curFreqsKHz = curFreqs, loadsPercent = coreLoads, temperatureC = temp,
                availableGovernors = cluster.availableGovernors,
                availableFrequencies = cluster.availableFrequencies
            )
        }

        val avgLoad = when {
            loads.isNotEmpty() -> loads.values.average().toFloat()
            else -> clusterStates.flatMap { it.loadsPercent }.ifEmpty { listOf(0f) }.average().toFloat()
        }
        val avgTemp = if (temps.isEmpty()) 0f else temps.average().toFloat()
        return CpuSnapshot(clusterStates, avgLoad, avgTemp, System.currentTimeMillis())
    }

    private fun computeLoads(
        prev: Map<Int, LongArray>?,
        cur: Map<Int, LongArray>
    ): Map<Int, Float> {
        if (prev == null) return cur.keys.associateWith { 0f }
        val out = HashMap<Int, Float>(cur.size)
        for ((cpu, curArr) in cur) {
            val prevArr = prev[cpu] ?: continue
            val totalDelta = (curArr.sum() - prevArr.sum()).coerceAtLeast(1L)
            val idleDelta = (curArr.getOrElse(3) { 0L } - prevArr.getOrElse(3) { 0L }).coerceAtLeast(0L)
            val busy = (totalDelta - idleDelta).coerceAtLeast(0L)
            out[cpu] = (busy * 100f / totalDelta).coerceIn(0f, 100f)
        }
        return out
    }

    private suspend fun readProcStatPerCpu(): Map<Int, LongArray> {
        val shell = selector.best()
        val raw = shell.readFile(CpuPaths.procStat) ?: return emptyMap()
        val out = HashMap<Int, LongArray>()
        for (line in raw.lineSequence()) {
            if (!line.startsWith("cpu")) continue
            val parts = line.trim().split(Regex("\\s+"))
            // Skip aggregate "cpu" line — only per-core "cpu0", "cpu1", ...
            val cpuId = parts[0].removePrefix("cpu").toIntOrNull() ?: continue
            val jiffies = parts.drop(1).mapNotNull { it.toLongOrNull() }
            if (jiffies.size >= 4) {
                out[cpuId] = jiffies.toLongArray()
            }
        }
        return out
    }
}
