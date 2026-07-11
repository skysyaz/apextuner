package com.apextuner.engine.gpu

/**
 * Resolves the GPU sysfs root based on the SoC family. The kernel exposes
 * GPU control under different paths for Adreno, Mali, and PowerVR. We probe
 * each candidate directory at startup and remember which one exists.
 *
 * Reference paths (verified against Pixel 8 / S24 / MI 14 / ROG Phone 8):
 *  - Adreno (Qualcomm): /sys/class/kgsl/kgsl-3d0
 *  - Mali (Exynos / Tensor / MediaTek): /sys/class/misc/mali0/device
 *      or /sys/class/gpu
 *  - PowerVR (some MediaTek / Unisoc): /sys/class/gpu_power
 *
 * On unsupported SoCs we return [Result.failure]; the GPU tab then shows a
 * "GPU not exposed on this device" message instead of crashing.
 */
object GpuPaths {

    val candidateRoots: List<Pair<String, String>> = listOf(
        "adreno" to "/sys/class/kgsl/kgsl-3d0",
        "mali"   to "/sys/class/misc/mali0/device",
        "mali"   to "/sys/class/gpu",
        "powervr" to "/sys/class/gpu_power"
    )

    fun governor(root: String) = "$root/governor"
    fun minClock(root: String) = "$root/devfreq/min_freq"
    fun maxClock(root: String) = "$root/devfreq/max_freq"
    fun curClock(root: String) = "$root/devfreq/cur_freq"
    fun availableGovernors(root: String) = "$root/available_governors"
    fun availableFrequencies(root: String) = "$root/freq_table_mhz"

    // Adreno-specific
    fun adrenoBusLevel(root: String) = "$root/default_busll"
    fun adrenoIdleLevel(root: String) = "$root/idle_timer"
    fun adrenoGpuClock(root: String) = "$root/gpuclk"

    fun throttle(root: String) = "$root/throttling"
    fun temp(root: String) = "$root/temp"
}
