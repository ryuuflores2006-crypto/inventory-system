package com.ryuuflores2006.inventorysystem.data

import android.content.Context
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.gotrue.GoTrue
import io.github.jan.supabase.gotrue.gotrue
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import io.ktor.client.plugins.cookies.HttpCookies
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SupabaseHelper {
    private var client: SupabaseClient? = null

    // Replace with actual Supabase details
    private const val SUPABASE_URL = "https://your-project.supabase.co"
    private const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.your-key-here"

    fun init() {
        if (client == null) {
            client = createSupabaseClient(
                supabaseUrl = SUPABASE_URL,
                supabaseKey = SUPABASE_ANON_KEY
            ) {
                install(Postgrest)
                install(GoTrue)
            }
        }
    }

    val postgrest: Postgrest
        get() {
            if (client == null) init()
            return client!!.postgrest
        }

    val auth: GoTrue
        get() {
            if (client == null) init()
            return client!!.gotrue
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
}
