package com.ryuuflores2006.inventorysystem.data

import kotlinx.serialization.Serializable

@Serializable
enum class BranchLocation {
    ManilaHQ, CebuOutlet, DavaoHub
}

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
