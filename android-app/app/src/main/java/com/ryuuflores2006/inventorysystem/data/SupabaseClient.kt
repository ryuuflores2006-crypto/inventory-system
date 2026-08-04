package com.ryuuflores2006.inventorysystem.data

import android.content.Context
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object SupabaseHelper {
    private var client: SupabaseClient? = null

    // Replace with actual Supabase details
    private const val SUPABASE_URL = "https://omecmeysesqwaxbbtebb.supabase.co"
    private const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im9tZWNtZXlzZXNxd2F4YmJ0ZWJiIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODU3NDQzMDUsImV4cCI6MjEwMTMyMDMwNX0.7Bcdjxxrm8TkrOgo33Sd8P4y8yyztpRb1H3K7keWorQ"

    fun init() {
        if (client == null) {
            client = createSupabaseClient(
                supabaseUrl = SUPABASE_URL,
                supabaseKey = SUPABASE_ANON_KEY
            ) {
                install(Postgrest)
                install(Auth) {
                    // Staff sign in once on a shop phone and stay signed in.
                    // These are the library defaults on Android — the session
                    // is kept in the app's own private storage and the access
                    // token renewed in the background — but they are spelled
                    // out because the counter depends on them.
                    autoLoadFromStorage = true
                    autoSaveToStorage = true
                    alwaysAutoRefresh = true
                }
                install(Realtime)
                install(Functions)
            }
        }
    }

    /** The raw client, needed for realtime channels. */
    val supabase: SupabaseClient
        get() {
            if (client == null) init()
            return client!!
        }

    val realtime get() = supabase.realtime

    val postgrest: Postgrest
        get() {
            if (client == null) init()
            return client!!.postgrest
        }

    val auth: Auth
        get() {
            if (client == null) init()
            return client!!.auth
        }

    // --- Branches Service ---
    // Branches are runtime rows shared with the PC dashboard, not a hardcoded list.
    suspend fun getBranches(activeOnly: Boolean = true): List<Branch> = withContext(Dispatchers.IO) {
        try {
            postgrest["branches"].select {
                if (activeOnly) {
                    filter { eq("is_active", true) }
                }
                order("is_main", Order.DESCENDING)
                order("name", Order.ASCENDING)
            }.decodeList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /** Returns null on success, or a human-readable error message. */
    suspend fun addBranch(
        name: String,
        code: String? = null,
        address: String? = null,
        phone: String? = null
    ): String? = withContext(Dispatchers.IO) {
        try {
            postgrest["branches"].insert(
                Branch(
                    name = name.trim(),
                    code = code?.trim()?.ifBlank { null },
                    address = address?.trim()?.ifBlank { null },
                    phone = phone?.trim()?.ifBlank { null }
                )
            )
            null
        } catch (e: Exception) {
            e.printStackTrace()
            val msg = e.message ?: "Could not add branch."
            if (msg.contains("duplicate", ignoreCase = true)) {
                "A branch with that name or code already exists."
            } else {
                msg
            }
        }
    }

    suspend fun setBranchActive(branchId: String, active: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            postgrest["branches"].update({ set("is_active", active) }) {
                filter { eq("branch_id", branchId) }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // --- Gadgets Service ---
    suspend fun getGadgetOrNullByImei(imei: String): RetailGadget? = withContext(Dispatchers.IO) {
        try {
            val result = postgrest["retail_gadgets"]
                .select {
                    filter {
                        eq("imei_1", imei)
                    }
                }.decodeSingleOrNull<RetailGadget>()
            result
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun getAllGadgets(): List<RetailGadget> = withContext(Dispatchers.IO) {
        try {
            postgrest["retail_gadgets"].select().decodeList<RetailGadget>()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun insertGadget(gadget: RetailGadget): Boolean = withContext(Dispatchers.IO) {
        try {
            postgrest["retail_gadgets"].insert(gadget)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun updateGadgetStatus(itemId: String, status: String): Boolean = withContext(Dispatchers.IO) {
        try {
            postgrest["retail_gadgets"].update({
                set("status", status)
            }) {
                filter {
                    eq("item_id", itemId)
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Remove a unit that should never have been logged — a mis-typed IMEI at
     * intake, a duplicate.
     *
     * Only stock that has not done anything yet can go: a sold or in-transit
     * unit is part of the day's numbers, and deleting it would quietly rewrite
     * them. Those are refused here with a reason rather than at the database,
     * so the message means something to whoever is holding the phone.
     */
    suspend fun deleteGadget(gadget: RetailGadget): String? = withContext(Dispatchers.IO) {
        val id = gadget.item_id ?: return@withContext "This unit has not finished saving yet."
        if (gadget.status == "Sold" || gadget.status == "In Transit") {
            return@withContext "Cannot delete a unit that is ${gadget.status}."
        }
        try {
            postgrest["retail_gadgets"].delete { filter { eq("item_id", id) } }
            null
        } catch (e: Exception) {
            e.printStackTrace()
            e.message ?: "Could not delete this unit."
        }
    }

    /** Same idea for a bulk line. The database refuses one a repair consumed. */
    suspend fun deletePart(part: RepairPart): String? = withContext(Dispatchers.IO) {
        val id = part.part_id ?: return@withContext "This part has not finished saving yet."
        try {
            postgrest["repair_parts"].delete { filter { eq("part_id", id) } }
            null
        } catch (e: Exception) {
            e.printStackTrace()
            val msg = e.message ?: "Could not delete this part."
            if (msg.contains("violates foreign key", true)) {
                "This part has been used on a repair — it cannot be deleted."
            } else {
                msg
            }
        }
    }

    // --- Repair Parts Service ---
    suspend fun getAllParts(branch: String? = null): List<RepairPart> = withContext(Dispatchers.IO) {
        try {
            if (branch != null) {
                postgrest["repair_parts"].select {
                    filter {
                        eq("branch_location", branch)
                    }
                }.decodeList()
            } else {
                postgrest["repair_parts"].select().decodeList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun insertPartStock(part: RepairPart): Boolean = withContext(Dispatchers.IO) {
        try {
            postgrest["repair_parts"].insert(part)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // --- Service Tickets Service ---
    suspend fun getAllTickets(): List<ServiceTicket> = withContext(Dispatchers.IO) {
        try {
            postgrest["service_tickets"].select().decodeList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun createTicket(ticket: ServiceTicket): Boolean = withContext(Dispatchers.IO) {
        try {
            postgrest["service_tickets"].insert(ticket)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun updateTicketStatus(ticketId: String, status: String): Boolean = withContext(Dispatchers.IO) {
        try {
            postgrest["service_tickets"].update({ set("ticket_status", status) }) {
                filter { eq("ticket_id", ticketId) }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // --- Transfers Service ---
    suspend fun getAllTransfers(): List<BranchTransfer> = withContext(Dispatchers.IO) {
        try {
            postgrest["branch_transfers"].select().decodeList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Send a unit or a batch of parts to another store.
     *
     * A serialized handset is marked `In Transit` in the same breath, so it
     * stops showing as sellable stock at the source the moment it leaves the
     * counter. Returns null on success, or a message to show the dispatcher.
     */
    suspend fun dispatchTransfer(transfer: BranchTransfer): String? = withContext(Dispatchers.IO) {
        try {
            postgrest["branch_transfers"].insert(transfer)
            if (transfer.item_type == "Serialized") {
                postgrest["retail_gadgets"].update({ set("status", "In Transit") }) {
                    filter { eq("imei_1", transfer.reference_identifier) }
                }
            }
            null
        } catch (e: Exception) {
            e.printStackTrace()
            e.message ?: "Could not record the transfer."
        }
    }

    /**
     * Book an in-transit transfer in at the destination.
     *
     * This calls the `receive_branch_transfer` function rather than doing the
     * moves here: switching the branch, restoring the status and closing the
     * transfer have to happen together or not at all, and the database is the
     * only place that can promise that.
     */
    suspend fun receiveTransfer(transferId: String, receiver: String): String? =
        withContext(Dispatchers.IO) {
            try {
                supabase.postgrest.rpc(
                    "receive_branch_transfer",
                    buildJsonObject {
                        put("p_transfer_id", transferId)
                        put("p_receiver", receiver)
                    }
                )
                null
            } catch (e: Exception) {
                e.printStackTrace()
                e.message ?: "Could not receive the transfer."
            }
        }

    // --- App releases (in-app updater) ---
    /** Newest published release, or null if none / offline. */
    suspend fun getLatestRelease(): AppRelease? = withContext(Dispatchers.IO) {
        try {
            postgrest["app_releases"].select {
                order("version_code", Order.DESCENDING)
                limit(1)
            }.decodeList<AppRelease>().firstOrNull()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
