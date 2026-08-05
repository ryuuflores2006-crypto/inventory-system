package com.ryuuflores2006.inventorysystem.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ryuuflores2006.inventorysystem.data.BranchStore
import com.ryuuflores2006.inventorysystem.data.LiveStore
import com.ryuuflores2006.inventorysystem.data.RepairPart
import com.ryuuflores2006.inventorysystem.data.RetailGadget
import com.ryuuflores2006.inventorysystem.data.Sale
import com.ryuuflores2006.inventorysystem.data.SaleOutcome
import com.ryuuflores2006.inventorysystem.data.SupabaseHelper
import com.ryuuflores2006.inventorysystem.ui.components.*
import com.ryuuflores2006.inventorysystem.ui.theme.Amber
import com.ryuuflores2006.inventorysystem.ui.theme.Ash
import com.ryuuflores2006.inventorysystem.ui.theme.Cyan
import com.ryuuflores2006.inventorysystem.ui.theme.Emerald
import com.ryuuflores2006.inventorysystem.ui.theme.Rose
import kotlinx.coroutines.launch

private val PAYMENT_METHODS = listOf("Cash", "GCash", "Card", "Bank Transfer", "Installment")

/**
 * The counter.
 *
 * Selling used to be possible only from the PC dashboard, which meant a phone
 * sold in a shop was not recorded until somebody walked to the office. This is
 * the same transaction, made where the sale actually happens.
 *
 * Nothing is written from here directly: [SupabaseHelper.recordSale] calls the
 * `record_sale` function, which takes the unit off the shelf and writes the
 * receipt together. That matters most when two counters are open at once — the
 * second cashier to reach for the same handset is refused, not merged.
 */
@Composable
fun SellScreen(onScanClick: (onScanned: (String) -> Unit) -> Unit) {
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val snackbar = remember { SnackbarHostState() }

    var search by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf("") }   // exact IMEI or SKU
    var price by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("1") }
    var payment by remember { mutableStateOf("Cash") }
    var customer by remember { mutableStateOf("") }
    // Whoever is on shift rings up a great many sales in a row; asking for the
    // name once per session is enough. It survives tab switches, not sign-out.
    var cashier by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var formError by remember { mutableStateOf<String?>(null) }
    var receipt by remember { mutableStateOf<Sale?>(null) }
    var voiding by remember { mutableStateOf<Sale?>(null) }
    var myBranch by rememberSaveable { mutableStateOf(BranchStore.defaultName) }

    val gadget: RetailGadget? = remember(selected, LiveStore.gadgets) {
        selected.takeIf { it.isNotBlank() }?.let { ref ->
            LiveStore.gadgets.firstOrNull { it.imei_1 == ref && it.status == "In Stock" }
        }
    }
    val part: RepairPart? = remember(selected, LiveStore.parts, gadget) {
        if (gadget != null) null
        else selected.takeIf { it.isNotBlank() }?.let { ref ->
            LiveStore.parts.firstOrNull { it.sku.equals(ref, true) && it.stock_qty > 0 }
        }
    }
    val chosen = gadget != null || part != null

    // Same idea as the transfer screen: search what is sellable rather than
    // demanding the exact code, and with an empty box just show the shelf.
    val suggestions: List<SellCandidate> =
        remember(search, LiveStore.gadgets, LiveStore.parts, chosen, myBranch) {
            if (chosen) return@remember emptyList()
            val q = search.trim().lowercase()
            fun hit(vararg f: String) = q.isBlank() || f.any { it.lowercase().contains(q) }

            val devices = LiveStore.gadgets
                .filter { it.status == "In Stock" && it.current_branch == myBranch }
                .filter { hit(it.imei_1, it.brand, it.model, it.sku, it.color) }
                .map {
                    SellCandidate(
                        it.imei_1,
                        "${it.brand} ${it.model}",
                        "${peso(it.retail_price)} · ${it.current_branch} · IMEI ${it.imei_1}"
                    )
                }
            val bulk = LiveStore.parts
                .filter { it.stock_qty > 0 && it.branch_location == myBranch }
                .filter { hit(it.sku, it.part_name) }
                .map {
                    SellCandidate(
                        it.sku,
                        it.part_name,
                        "${peso(it.service_price)} · ${it.stock_qty} at ${it.branch_location}"
                    )
                }
            (devices + bulk).take(8)
        }

    // The list price is a starting point, not the deal. Fill it in when
    // something is picked so the common case is one tap, and let it be edited.
    LaunchedEffect(gadget?.item_id, part?.part_id) {
        price = when {
            gadget != null -> trimZeros(gadget.retail_price)
            part != null -> trimZeros(part.service_price)
            else -> ""
        }
        quantity = "1"
    }

    val qty = quantity.toIntOrNull() ?: 0
    val qtyBad = part != null && (qty <= 0 || qty > part.stock_qty)
    val priceValue = price.toDoubleOrNull()
    val priceBad = price.isNotBlank() && (priceValue == null || priceValue < 0)
    val lineTotal = (priceValue ?: 0.0) * (if (part != null) qty.coerceAtLeast(0) else 1)
    val canSell = chosen && cashier.isNotBlank() && !qtyBad && !priceBad && priceValue != null

    fun sell() {
        formError = null
        scope.launch {
            busy = true
            val outcome = SupabaseHelper.recordSale(
                itemType = if (gadget != null) "Serialized" else "Bulk",
                reference = if (gadget != null) gadget.imei_1 else part!!.sku,
                cashier = cashier.trim(),
                quantity = if (gadget != null) 1 else qty,
                unitPrice = priceValue,
                paymentMethod = payment,
                customerName = customer.trim().ifBlank { null },
                branch = myBranch
            )
            busy = false
            when (outcome) {
                is SaleOutcome.Ok -> {
                    receipt = outcome.sale
                    search = ""; selected = ""; customer = ""; quantity = "1"; price = ""
                    LiveStore.refresh()
                }
                is SaleOutcome.Failed -> formError = outcome.message
            }
        }
    }

    val today = LiveStore.sales.take(40)
    val takings = LiveStore.completedSales.sumOf { it.total_amount }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ScreenHeader(title = "Sell", subtitle = "Ring up a phone or an accessory")

            ErrorBanner(formError)
            
            AppDropdown(
                label = "Selling from",
                selected = myBranch,
                options = BranchStore.names,
                onSelect = { 
                    myBranch = it
                    search = ""
                    selected = ""
                }
            )

            SectionLabel("What is being sold")

            AppTextField(
                value = if (chosen) selected else search,
                onValueChange = { if (!chosen) search = it },
                label = if (chosen) "Selected" else "Scan, or search your stock",
                placeholder = "Model, IMEI, part name or SKU",
                trailing = {
                    if (chosen) {
                        TextButton(onClick = { selected = ""; search = "" }) {
                            Text("Change", color = Cyan)
                        }
                    } else {
                        IconButton(onClick = { onScanClick { scanned -> selected = scanned } }) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan", tint = Cyan)
                        }
                    }
                }
            )

            when {
                gadget != null -> SoldItemCard(
                    heading = "${gadget.brand} ${gadget.model}",
                    lines = listOf(
                        listOfNotNull(
                            gadget.storage.ifBlank { null },
                            gadget.ram.ifBlank { null },
                            gadget.color.ifBlank { null }
                        ).joinToString(" · "),
                        "IMEI ${gadget.imei_1}",
                        "At ${gadget.current_branch} · list ${peso(gadget.retail_price)}"
                    )
                )

                part != null -> SoldItemCard(
                    heading = part.part_name,
                    lines = listOf(
                        "SKU ${part.sku}",
                        "${part.stock_qty} on hand at ${part.branch_location}",
                        "List ${peso(part.service_price)}"
                    )
                )

                suggestions.isNotEmpty() -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        if (search.isBlank()) "Tap what you are selling" else "Did you mean",
                        color = Ash,
                        style = MaterialTheme.typography.bodySmall
                    )
                    suggestions.forEach { c ->
                        AppCard(
                            onClick = { selected = c.id },
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Text(c.title, style = MaterialTheme.typography.titleSmall)
                            Text(c.detail, color = Ash, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                search.isNotBlank() -> Text(
                    "Nothing sellable matches that.",
                    color = Amber,
                    style = MaterialTheme.typography.bodySmall
                )

                else -> EmptyState(
                    icon = Icons.Default.PointOfSale,
                    title = "No stock to sell yet",
                    message = "Receive stock first and it will show up here."
                )
            }

            if (chosen) {
                SectionLabel("The deal")

                if (part != null) {
                    AppTextField(
                        value = quantity,
                        onValueChange = { quantity = it.filter(Char::isDigit).take(4) },
                        label = "How many",
                        keyboardType = KeyboardType.Number,
                        isError = qtyBad,
                        supportingText = if (qtyBad) "Between 1 and ${part.stock_qty}" else null
                    )
                }

                AppTextField(
                    value = price,
                    onValueChange = { price = it.filter { c -> c.isDigit() || c == '.' }.take(10) },
                    label = if (part != null) "Price each" else "Price",
                    keyboardType = KeyboardType.Decimal,
                    isError = priceBad,
                    supportingText = when {
                        priceBad -> "Enter an amount"
                        part != null && qty > 1 -> "Total ${peso(lineTotal)}"
                        else -> "Change it if you gave a discount"
                    }
                )

                AppDropdown(
                    label = "Paid with",
                    selected = payment,
                    options = PAYMENT_METHODS,
                    onSelect = { payment = it }
                )

                AppTextField(
                    value = customer,
                    onValueChange = { customer = it },
                    label = "Customer (optional)",
                    placeholder = "For warranty follow-ups"
                )

                AppTextField(
                    value = cashier,
                    onValueChange = { cashier = it },
                    label = "Sold by",
                    placeholder = "Your name"
                )

                PrimaryButton(
                    text = if (lineTotal > 0) "Take ${peso(lineTotal)}" else "Complete sale",
                    onClick = ::sell,
                    enabled = canSell,
                    busy = busy,
                    icon = Icons.Default.PointOfSale
                )
            }

            Spacer(Modifier.height(6.dp))
            SectionLabel("Recent sales · ${peso(takings)} in total")

            if (today.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.ReceiptLong,
                    title = "No sales recorded",
                    message = "Every sale you ring up here keeps its own receipt."
                )
            } else {
                today.forEach { sale ->
                    AppCard(contentPadding = PaddingValues(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(sale.description, style = MaterialTheme.typography.titleSmall)
                            StatusPill(
                                text = if (sale.isVoided) "Voided" else peso(sale.total_amount),
                                color = if (sale.isVoided) Rose else Emerald,
                                dense = true
                            )
                        }
                        Text(
                            listOfNotNull(
                                sale.invoice_no,
                                sale.branch_location,
                                sale.payment_method.takeIf { !sale.isVoided },
                                "by ${sale.cashier}"
                            ).joinToString(" · "),
                            color = Ash,
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (!sale.isVoided) {
                            TextButton(onClick = { voiding = sale }) {
                                Text("Void this sale", color = Rose)
                            }
                        } else if (!sale.void_reason.isNullOrBlank()) {
                            Text(
                                "Voided — ${sale.void_reason}",
                                color = Rose,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    receipt?.let { sale ->
        ReceiptDialog(sale = sale, onClose = { receipt = null })
    }

    voiding?.let { sale ->
        VoidDialog(
            sale = sale,
            onDismiss = { voiding = null },
            onConfirm = { reason ->
                scope.launch {
                    val error = SupabaseHelper.voidSale(
                        sale.sale_id ?: return@launch,
                        cashier.trim().ifBlank { "Counter" },
                        reason
                    )
                    voiding = null
                    if (error == null) {
                        snackbar.showSnackbar("${sale.invoice_no} voided — stock is back.")
                        LiveStore.refresh()
                    } else {
                        formError = error
                    }
                }
            }
        )
    }
}

private data class SellCandidate(val id: String, val title: String, val detail: String)

/** The thing about to be sold, stated plainly before money changes hands. */
@Composable
private fun SoldItemCard(heading: String, lines: List<String>) {
    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(heading, color = Emerald, style = MaterialTheme.typography.titleMedium)
            lines.filter { it.isNotBlank() }.forEach {
                Text(it, color = Ash, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** What the sale wrote, read back from the stored row rather than guessed at. */
@Composable
private fun ReceiptDialog(sale: Sale, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Sold — ${sale.invoice_no}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(sale.description, style = MaterialTheme.typography.titleMedium)
                Text(
                    if (sale.quantity > 1) {
                        "${sale.quantity} × ${peso(sale.unit_price)} = ${peso(sale.total_amount)}"
                    } else {
                        peso(sale.total_amount)
                    },
                    color = Emerald
                )
                Text("${sale.payment_method} · ${sale.branch_location}", color = Ash)
                sale.customer_name?.takeIf { it.isNotBlank() }?.let { Text("For $it", color = Ash) }
                Text("Sold by ${sale.cashier}", color = Ash)
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Done", color = Cyan) } }
    )
}

@Composable
private fun VoidDialog(sale: Sale, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Void ${sale.invoice_no}?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "The receipt is kept and marked voided — takings stay auditable — " +
                        "and ${sale.description} goes back on the shelf."
                )
                AppTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = "Why",
                    placeholder = "Wrong unit, customer changed mind…"
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(reason.trim()) }) { Text("Void", color = Rose) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Keep", color = Ash) } }
    )
}

/** 12500.0 reads as "12500", 12500.5 keeps its centavos. */
private fun trimZeros(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
