package com.beykus.beygram.data.vpn

/**
 * Remembers the Mihomo node that automatic selection relies on, and whether TDLib confirmed it.
 *
 * A testDelay (HTTPS) result alone does not prove that TDLib can reach Telegram through a node. When a
 * node is selected and TDLib still has not reached Connected after [confirmMillis], the node is cooled
 * down for [cooldownMillis], so the next sweep tries another candidate instead of picking the same one.
 */
internal class AutoSelectRouteTracker(
    private val confirmMillis: Long,
    private val cooldownMillis: Long,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    private var pendingName: String? = null
    private var pendingSince = 0L
    private val cooldownUntil = HashMap<String, Long>()

    /** Records the node the latest sweep relies on. The confirmation timer starts once per node. */
    @Synchronized
    fun markPending(name: String) {
        if (pendingName == name) return
        pendingName = name
        pendingSince = clock()
    }

    /** TDLib reached Connected through the pending node. */
    @Synchronized
    fun confirm() {
        pendingName = null
    }

    /**
     * Cools down the pending node when TDLib did not confirm it in time.
     * Returns true when a node was cooled down.
     */
    @Synchronized
    fun expireIfUnconfirmed(): Boolean {
        val name = pendingName ?: return false
        if (clock() - pendingSince < confirmMillis) return false
        pendingName = null
        cooldownUntil[name] = clock() + cooldownMillis
        return true
    }

    @Synchronized
    fun isCoolingDown(name: String): Boolean {
        val until = cooldownUntil[name] ?: return false
        if (clock() >= until) {
            cooldownUntil.remove(name)
            return false
        }
        return true
    }

    /** A new network reachability state or another connection group gives every node a fresh chance. */
    @Synchronized
    fun clearCooldowns() {
        cooldownUntil.clear()
    }
}
