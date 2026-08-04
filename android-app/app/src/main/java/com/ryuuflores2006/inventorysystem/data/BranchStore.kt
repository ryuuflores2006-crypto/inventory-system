package com.ryuuflores2006.inventorysystem.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Single source of truth for the branch list on the Android side.
 *
 * Branches live in the `branches` table, so they can be added from this app or
 * the PC dashboard. Every screen observes this store instead of a hardcoded
 * list, which means a newly added store shows up in all dropdowns at once.
 */
object BranchStore {
    var branches by mutableStateOf<List<Branch>>(emptyList())
        private set

    var isLoading by mutableStateOf(false)
        private set

    val names: List<String>
        get() = branches.map { it.name }

    /** Name to pre-select in forms: the main store, else the first one. */
    val defaultName: String
        get() = branches.firstOrNull { it.is_main }?.name ?: branches.firstOrNull()?.name ?: ""

    suspend fun refresh() {
        isLoading = true
        branches = SupabaseHelper.getBranches(activeOnly = true)
        isLoading = false
    }

    /** Returns null on success, or an error message. Refreshes on success. */
    suspend fun add(name: String, code: String?, address: String?, phone: String?): String? {
        val error = SupabaseHelper.addBranch(name, code, address, phone)
        if (error == null) refresh()
        return error
    }
}
