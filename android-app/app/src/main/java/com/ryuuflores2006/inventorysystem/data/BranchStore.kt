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
    /** Every store, archived ones included. Only the Branches screen shows these. */
    var all by mutableStateOf<List<Branch>>(emptyList())
        private set

    var isLoading by mutableStateOf(false)
        private set

    /** Active stores only — this is what every dropdown and form uses. */
    val branches: List<Branch>
        get() = all.filter { it.is_active }

    val archived: List<Branch>
        get() = all.filter { !it.is_active }

    val names: List<String>
        get() = branches.map { it.name }

    /** Name to pre-select in forms: the main store, else the first one. */
    val defaultName: String
        get() = branches.firstOrNull { it.is_main }?.name ?: branches.firstOrNull()?.name ?: ""

    suspend fun refresh() {
        isLoading = true
        all = SupabaseHelper.getBranches(activeOnly = false)
        isLoading = false
    }

    /**
     * Archive or restore a store. Refuses to archive the last active one so the
     * app can never be left with no branch to select. Returns null on success.
     */
    suspend fun setActive(branch: Branch, active: Boolean): String? {
        val id = branch.branch_id ?: return "This branch has no id yet."
        if (!active && branches.size <= 1) {
            return "You need at least one active branch."
        }
        val ok = SupabaseHelper.setBranchActive(id, active)
        if (!ok) return "Could not update ${branch.name}."
        refresh()
        return null
    }

    /** Returns null on success, or an error message. Refreshes on success. */
    suspend fun add(name: String, code: String?, address: String?, phone: String?): String? {
        val error = SupabaseHelper.addBranch(name, code, address, phone)
        if (error == null) refresh()
        return error
    }
}
