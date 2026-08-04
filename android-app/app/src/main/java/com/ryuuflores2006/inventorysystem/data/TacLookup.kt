package com.ryuuflores2006.inventorysystem.data

import io.github.jan.supabase.functions.functions
import io.ktor.client.call.body
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** A row of `tac_catalog` — one model, named once and remembered. */
@Serializable
data class TacEntry(
    val tac: String,
    val brand: String? = null,
    val model: String? = null,
    val release_year: Int? = null
) {
    val label: String? get() = listOfNotNull(brand, model).joinToString(" ").ifBlank { null }
}

@Serializable
private data class LookupResponse(
    val tac: String? = null,
    val brand: String? = null,
    val model: String? = null,
    val release_year: Int? = null,
    val found: Boolean? = null,
    val error: String? = null,
    val quotaExhausted: Boolean? = null
)

/**
 * Names a phone model from the first 8 digits of its IMEI.
 *
 * Three tiers, cheapest first:
 *  1. this session's memory,
 *  2. the shared `tac_catalog` table — free, and covers every model the shop
 *     has ever looked up on any device,
 *  3. the `tac-lookup` Edge Function, which is the only step that spends a
 *     lookup from the monthly allowance, and only for a model nobody has
 *     scanned before.
 *
 * Every path is best-effort: no network, no key configured or no allowance left
 * simply means no name, never a crash or a blocked form.
 */
object TacLookup {
    private val memory = mutableMapOf<String, TacEntry?>()
    private val json = Json { ignoreUnknownKeys = true }

    /** Non-suspending peek, for drawing a name we already have. */
    fun cached(imei: String?): TacEntry? = ScanResolver.tacOf(imei)?.let { memory[it] }

    /** Returns null when the model cannot be named. */
    suspend fun identify(imei: String?): TacEntry? = withContext(Dispatchers.IO) {
        val tac = ScanResolver.tacOf(imei) ?: return@withContext null
        if (memory.containsKey(tac)) return@withContext memory[tac]

        fromCatalog(tac)?.let {
            memory[tac] = it
            return@withContext it
        }

        val fresh = fromFunction(tac)
        // Cache misses too — a TAC the provider does not know should not be
        // asked about again and again for the rest of the session.
        memory[tac] = fresh
        fresh
    }

    private suspend fun fromCatalog(tac: String): TacEntry? = try {
        SupabaseHelper.postgrest["tac_catalog"]
            .select { filter { eq("tac", tac) } }
            .decodeList<TacEntry>()
            .firstOrNull()
            ?.takeIf { it.label != null }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }

    private suspend fun fromFunction(tac: String): TacEntry? = try {
        val response: HttpResponse = SupabaseHelper.supabase.functions.invoke("tac-lookup") {
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject { put("tac", tac) } as JsonObject)
        }
        val parsed = json.decodeFromString<LookupResponse>(response.body<String>())
        if (parsed.error != null || (parsed.brand == null && parsed.model == null)) {
            null
        } else {
            TacEntry(tac, parsed.brand, parsed.model, parsed.release_year)
        }
    } catch (e: Exception) {
        // 401/429/503 all land here; the scanner just shows the unit as new.
        e.printStackTrace()
        null
    }
}
