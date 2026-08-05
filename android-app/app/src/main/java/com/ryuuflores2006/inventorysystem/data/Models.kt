package com.ryuuflores2006.inventorysystem.data

import kotlinx.serialization.Serializable

/**
 * Branches are rows, not a fixed enum — they can be added or renamed at runtime
 * from this app or the PC dashboard, and every other table references the name.
 */
@Serializable
data class Branch(
    val branch_id: String? = null,
    val name: String,
    val code: String? = null,
    val address: String? = null,
    val phone: String? = null,
    val is_main: Boolean = false,
    val is_active: Boolean = true,
    val created_at: String? = null,
    val updated_at: String? = null
)

/**
 * A published APK build. The in-app updater compares [version_code] with the
 * installed package's versionCode and offers a download when it is higher.
 */
@Serializable
data class AppRelease(
    val release_id: String? = null,
    val version_code: Int,
    val version_name: String,
    val apk_url: String,
    val release_notes: String? = null,
    val is_mandatory: Boolean = false,
    val published_at: String? = null
)

@Serializable
enum class GadgetStatus {
    InStock, Reserved, Sold, InTransit, Returned
}

@Serializable
enum class TicketStatus {
    Pending, Diagnosing, WaitingForParts, Repairing, Ready, Completed
}

@Serializable
enum class TransferStatus {
    InTransit, Received, Cancelled
}

@Serializable
data class RetailGadget(
    val item_id: String? = null,
    val sku: String,
    val brand: String,
    val model: String,
    val storage: String,
    val ram: String,
    val color: String,
    val cost_price: Double,
    val retail_price: Double,
    val current_branch: String, // mapped from enum
    val status: String, // mapped from enum
    val imei_1: String,
    val imei_2: String? = null,
    val supplier_name: String? = null,
    val created_at: String? = null
)

@Serializable
data class RepairPart(
    val part_id: String? = null,
    val sku: String,
    val part_name: String,
    val compatible_models: List<String>,
    val branch_location: String,
    val stock_qty: Int,
    val minimum_stock_threshold: Int,
    val cost_price: Double,
    val service_price: Double,
    val created_at: String? = null
)

@Serializable
data class ServiceTicket(
    val ticket_id: String? = null,
    val customer_name: String,
    val phone_number: String,
    val device_model: String,
    val imei_serial: String,
    val issue_description: String,
    val assigned_technician: String? = null,
    val ticket_status: String,
    val labor_cost: Double = 0.0,
    val total_amount: Double = 0.0,
    val branch_location: String,
    val created_at: String? = null,
    val updated_at: String? = null
)

@Serializable
data class TicketPartsUsed(
    val ticket_part_id: String? = null,
    val ticket_id: String,
    val part_id: String,
    val quantity_used: Int = 1,
    val price_charged: Double,
    val created_at: String? = null
)

@Serializable
data class BranchTransfer(
    val transfer_id: String? = null,
    val source_branch: String,
    val destination_branch: String,
    val item_type: String, // 'Serialized' or 'Bulk'
    val reference_identifier: String, // IMEI or SKU
    val quantity: Int = 1,
    val dispatcher: String,
    val receiver: String? = null,
    val transfer_status: String,
    val created_at: String? = null,
    val updated_at: String? = null
)

/**
 * A sale that actually happened.
 *
 * Written only by the `record_sale` database function — never inserted from a
 * client — so the stock move and the money are one transaction. Voiding keeps
 * the row and puts the stock back rather than deleting the receipt.
 */
@Serializable
data class Sale(
    val sale_id: String? = null,
    val invoice_no: String = "",
    val branch_location: String = "",
    val item_type: String = "Serialized", // 'Serialized' or 'Bulk'
    val reference_identifier: String = "", // IMEI or SKU
    val description: String = "",
    val item_id: String? = null,
    val part_id: String? = null,
    val quantity: Int = 1,
    val unit_price: Double = 0.0,
    val total_amount: Double = 0.0,
    val cost_total: Double = 0.0,
    val payment_method: String = "Cash",
    val customer_name: String? = null,
    val customer_phone: String? = null,
    val cashier: String = "",
    val notes: String? = null,
    val status: String = "Completed", // 'Completed' or 'Voided'
    val void_reason: String? = null,
    val voided_by: String? = null,
    val voided_at: String? = null,
    val sold_at: String? = null
) {
    val isVoided: Boolean get() = status == "Voided"
    /** What the shop actually made on it, once the unit's own cost is off. */
    val profit: Double get() = total_amount - cost_total
}

/** Either a finished receipt, or a reason a cashier can act on. */
sealed interface SaleOutcome {
    data class Ok(val sale: Sale) : SaleOutcome
    data class Failed(val message: String) : SaleOutcome
}
