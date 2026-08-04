package com.ryuuflores2006.inventorysystem.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Everything the UI reads, in one place, kept current by Supabase realtime.
 *
 * A single channel listens to every change in the `public` schema. When a row
 * changes anywhere — this phone, another phone, or the PC dashboard — the store
 * refetches and every screen observing it recomposes. No pull-to-refresh needed,
 * though the screens still offer it.
 */
object LiveStore {
    var gadgets by mutableStateOf<List<RetailGadget>>(emptyList())
        private set
    var parts by mutableStateOf<List<RepairPart>>(emptyList())
        private set
    var tickets by mutableStateOf<List<ServiceTicket>>(emptyList())
        private set
    var transfers by mutableStateOf<List<BranchTransfer>>(emptyList())
        private set

    /** True during the very first load, so screens can show skeletons not "empty". */
    var isLoading by mutableStateOf(false)
        private set
    var hasLoadedOnce by mutableStateOf(false)
        private set

    /** Set when realtime is connected, so the UI can show a "Live" pill. */
    var isLive by mutableStateOf(false)
        private set

    /** Bumped on every realtime event; screens can key animations off it. */
    var lastChangeAt by mutableStateOf(0L)
        private set

    private var channel: RealtimeChannel? = null
    private var listenJob: Job? = null
    private var pendingRefresh: Job? = null

    val openTickets: List<ServiceTicket>
        get() = tickets.filter { it.ticket_status !in setOf("Completed", "Cancelled") }

    val lowStockParts: List<RepairPart>
        get() = parts.filter { it.stock_qty <= it.minimum_stock_threshold }

    fun gadgetsIn(branch: String?): List<RetailGadget> =
        if (branch == null) gadgets else gadgets.filter { it.current_branch == branch }

    fun partsIn(branch: String?): List<RepairPart> =
        if (branch == null) parts else parts.filter { it.branch_location == branch }

    fun ticketsIn(branch: String?): List<ServiceTicket> =
        if (branch == null) tickets else tickets.filter { it.branch_location == branch }

    /** Refetch everything at once. Safe to call as often as you like. */
    suspend fun refresh() = coroutineRefresh()

    private suspend fun coroutineRefresh() {
        isLoading = true
        try {
            kotlinx.coroutines.coroutineScope {
                val g = async { SupabaseHelper.getAllGadgets() }
                val p = async { SupabaseHelper.getAllParts() }
                val t = async { SupabaseHelper.getAllTickets() }
                val x = async { SupabaseHelper.getAllTransfers() }
                val b = async { BranchStore.refresh() }
                awaitAll(g, p, t, x, b)
                gadgets = g.await()
                parts = p.await()
                tickets = t.await()
                transfers = x.await()
            }
            hasLoadedOnce = true
        } finally {
            isLoading = false
        }
    }

    /**
     * Start listening. Call once after sign-in; calling again is a no-op.
     * The scope should outlive the screens (the activity scope).
     */
    fun startSync(scope: CoroutineScope) {
        if (channel != null) return

        scope.launch { refresh() }

        val ch = SupabaseHelper.supabase.channel("inventory-sync")
        channel = ch

        listenJob = scope.launch {
            try {
                ch.postgresChangeFlow<PostgresAction>(schema = "public").collect {
                    lastChangeAt = System.currentTimeMillis()
                    scheduleRefresh(scope)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                isLive = false
            }
        }

        scope.launch {
            try {
                ch.subscribe(blockUntilSubscribed = true)
                isLive = true
            } catch (e: Exception) {
                e.printStackTrace()
                isLive = false
            }
        }
    }

    /**
     * One write can fire several row events (a part allocation touches the
     * junction row, the part and the ticket). Coalesce them into one refetch.
     */
    private fun scheduleRefresh(scope: CoroutineScope) {
        pendingRefresh?.cancel()
        pendingRefresh = scope.launch {
            delay(350)
            refresh()
        }
    }

    /** Drop the subscription and wipe cached rows — used on sign-out. */
    suspend fun stopSync() {
        listenJob?.cancel()
        listenJob = null
        pendingRefresh?.cancel()
        pendingRefresh = null
        channel?.let { runCatching { SupabaseHelper.realtime.removeChannel(it) } }
        channel = null
        isLive = false
        gadgets = emptyList()
        parts = emptyList()
        tickets = emptyList()
        transfers = emptyList()
        hasLoadedOnce = false
    }
}
