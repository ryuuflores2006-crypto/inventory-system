package com.ryuuflores2006.inventorysystem.data

import android.content.Context
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
                install(Auth) {}
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
