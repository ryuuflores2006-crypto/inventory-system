package com.ryuuflores2006.inventorysystem.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ryuuflores2006.inventorysystem.data.BranchStore
import com.ryuuflores2006.inventorysystem.data.BranchTransfer
import com.ryuuflores2006.inventorysystem.data.LiveStore
import com.ryuuflores2006.inventorysystem.data.RepairPart
import com.ryuuflores2006.inventorysystem.data.RetailGadget
import com.ryuuflores2006.inventorysystem.data.ScanResolver
import com.ryuuflores2006.inventorysystem.data.SupabaseHelper
import com.ryuuflores2006.inventorysystem.ui.components.*
import com.ryuuflores2006.inventorysystem.ui.theme.Amber
import com.ryuuflores2006.inventorysystem.ui.theme.Ash
import com.ryuuflores2006.inventorysystem.ui.theme.Cyan
import com.ryuuflores2006.inventorysystem.ui.theme.GlassSurfaceRaised
import com.ryuuflores2006.inventorysystem.ui.theme.Emerald
import kotlinx.coroutines.launch

/**
 * Checking stock out of one store and into another.
 *
 * Scanning is the whole point of this screen: point the camera at the handset's
 * IMEI and the unit identifies itself — which model it is, which store holds it
 * now, and whether it is actually free to move. Nothing has to be typed, and a
 * unit that is sold, already in transit or unknown is refused before it can
 * leave a paper trail that does not match the shelf.
 *
 * A dispatch marks the handset `In Transit` immediately so it stops counting as
 * sellable at the source; the destination books it in from the same screen,
 * which is the only moment the branch actually changes.
 */
@Composable
fun TransferScreen(onScanClick: (onScanned: (String) -> Unit) -> Unit) {
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val snackbar = remember { SnackbarHostState() }

    var reference by remember { mutableStateOf("") }
    var destination by remember { mutableStateOf("") }
    var dispatcher by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("1") }
    var isSending by remember { mutableStateOf(false) }
    var formError by remember { mutableStateOf<String?>(null) }
    var receivingId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (BranchStore.branches.isEmpty()) BranchStore.refresh()
    }

    // What the reference currently points at, recomputed as it is typed or
    // scanned. This is what makes the scan feel instant — no lookup round trip,
    // the answer is already in the synced store.
    val gadget: RetailGadget? = remember(reference, LiveStore.gadgets) {
        reference.trim().takeIf { it.isNotBlank() }?.let { ref ->
            LiveStore.gadgets.firstOrNull { it.imei_1 == ref || it.imei_2 == ref }
        }
    }
    val part: RepairPart? = remember(reference, LiveStore.parts, gadget) {
        if (gadget != null) null
        else reference.trim().takeIf { it.isNotBlank() }?.let { ref ->
            LiveStore.parts.firstOrNull { it.sku.equals(ref, ignoreCase = true) }
        }
    }

    // Nothing is selected yet, so help rather than scold. An exact IMEI is what
    // a scan produces, but a person typing has a model name in their head, not
    // fifteen digits — so match on anything printed on the shelf label and
    // offer what is actually movable. With the box empty this is simply the
    // stock list, which is the fastest way to send something without a scanner.
    data class Candidate(val id: String, val title: String, val detail: String)

    val suggestions: List<Candidate> = remember(reference, LiveStore.gadgets, LiveStore.parts, gadget, part) {
        if (gadget != null || part != null) return@remember emptyList()
        val q = reference.trim().lowercase()
        fun hit(vararg fields: String) = q.isBlank() || fields.any { it.lowercase().contains(q) }

        val devices = LiveStore.gadgets
            .filter { it.status == "In Stock" }
            .filter { hit(it.imei_1, it.imei_2.orEmpty(), it.brand, it.model, it.sku, it.color) }
            .map {
                Candidate(
                    it.imei_1,
                    "${it.brand} ${it.model}",
                    "IMEI ${it.imei_1} · ${it.current_branch}"
                )
            }
        val bulk = LiveStore.parts
            .filter { it.stock_qty > 0 }
            .filter { hit(it.sku, it.part_name) }
            .map {
                Candidate(
                    it.sku,
                    it.part_name,
                    "SKU ${it.sku} · ${it.stock_qty} at ${it.branch_location}"
                )
            }
        (devices + bulk).take(8)
    }

    val source = gadget?.current_branch ?: part?.branch_location ?: ""
    val destinations = BranchStore.names.filter { it != source }

    // A branch that was picked before the unit was scanned can turn out to be
    // the unit's own store; drop it rather than let the insert fail.
    LaunchedEffect(source) {
        if (destination == source) destination = ""
    }

    val blocker: String? = when {
        reference.isBlank() -> null
        gadget == null && part == null && suggestions.isEmpty() ->
            "Nothing movable matches that — check the spelling, or scan the box."
        gadget != null && gadget.status == "Sold" -> "This unit is already sold."
        gadget != null && gadget.status == "In Transit" -> "This unit is already on its way somewhere."
        part != null && part.stock_qty <= 0 -> "No stock of this part at ${part.branch_location}."
        else -> null
    }

    val qty = quantity.toIntOrNull() ?: 0
    val qtyBad = part != null && (qty <= 0 || qty > part.stock_qty)
    val canSend = blocker == null && (gadget != null || part != null) &&
        destination.isNotBlank() && dispatcher.isNotBlank() && !qtyBad

    fun send() {
        formError = null
        val ref = reference.trim()
        scope.launch {
            isSending = true
            val error = SupabaseHelper.dispatchTransfer(
                BranchTransfer(
                    source_branch = source,
                    destination_branch = destination,
                    item_type = if (gadget != null) "Serialized" else "Bulk",
                    reference_identifier = if (gadget != null) gadget.imei_1 else ref,
                    quantity = if (gadget != null) 1 else qty,
                    dispatcher = dispatcher.trim(),
                    transfer_status = "In Transit"
                )
            )
            isSending = false
            if (error == null) {
                val what = gadget?.let { "${it.brand} ${it.model}" } ?: part?.part_name ?: ref
                snackbar.showSnackbar("$what sent to $destination.")
                reference = ""; destination = ""; quantity = "1"
                LiveStore.refresh()
            } else {
                formError = error
            }
        }
    }

    val inTransit = LiveStore.transfers.filter { it.transfer_status == "In Transit" }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ScreenHeader(
                title = "Transfer",
                subtitle = "Move stock between stores"
            )

            ErrorBanner(formError)

            SectionLabel("What is moving")

            AppTextField(
                value = reference,
                onValueChange = { reference = it },
                label = "Scan, or search your stock",
                placeholder = "Model, IMEI, part name or SKU",
                trailing = {
                    IconButton(onClick = { onScanClick { scanned -> reference = scanned } }) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan", tint = Cyan)
                    }
                }
            )

            when {
                gadget != null -> IdentifiedCard(
                    heading = "${gadget.brand} ${gadget.model}",
                    lines = listOfNotNull(
                        listOfNotNull(gadget.storage.ifBlank { null }, gadget.ram.ifBlank { null })
                            .joinToString(" · ").ifBlank { null },
                        "IMEI ${gadget.imei_1}",
                        "At ${gadget.current_branch} · ${gadget.status}"
                    ),
                    tint = if (blocker == null) Emerald else Amber
                )

                part != null -> IdentifiedCard(
                    heading = part.part_name,
                    lines = listOf(
                        "SKU ${part.sku}",
                        "At ${part.branch_location} · ${part.stock_qty} on hand"
                    ),
                    tint = if (blocker == null) Emerald else Amber
                )

                suggestions.isNotEmpty() -> Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        if (reference.isBlank()) "Tap what you are sending" else "Did you mean",
                        color = Ash,
                        style = MaterialTheme.typography.bodySmall
                    )
                    suggestions.forEach { candidate ->
                        AppCard(
                            onClick = { reference = candidate.id },
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Text(candidate.title, style = MaterialTheme.typography.titleSmall)
                            Text(
                                candidate.detail,
                                color = Ash,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                reference.isNotBlank() -> IdentifiedCard(
                    heading = "Not recognised",
                    lines = listOf(
                        if (ScanResolver.resolve(reference).isImei) {
                            "That IMEI is not stocked at any branch."
                        } else {
                            "No unit or part matches that code."
                        }
                    ),
                    tint = Amber
                )
            }

            blocker?.let {
                Text(it, color = Amber, style = MaterialTheme.typography.bodySmall)
            }

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

            SectionLabel("Where it is going")

            AppDropdown(
                label = "Destination branch",
                selected = destination,
                options = destinations,
                onSelect = { destination = it },
                enabled = source.isNotBlank(),
                emptyHint = if (source.isBlank()) "Scan something first" else "No other branch yet"
            )

            AppTextField(
                value = dispatcher,
                onValueChange = { dispatcher = it },
                label = "Released by",
                placeholder = "Who is handing it over"
            )

            PrimaryButton(
                text = if (source.isBlank()) "Send" else "Send from $source",
                onClick = ::send,
                enabled = canSend,
                busy = isSending,
                icon = Icons.Default.LocalShipping
            )

            Spacer(Modifier.height(6.dp))
            SectionLabel("On the road (${inTransit.size})")

            if (inTransit.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.LocalShipping,
                    title = "Nothing in transit",
                    message = "Units you send appear here until the receiving store books them in."
                )
            } else {
                inTransit.forEach { transfer ->
                    val id = transfer.transfer_id
                    AppCard {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    transfer.reference_identifier,
                                    style = MaterialTheme.typography.titleSmall
                                )
                                StatusPill(text = "In Transit", color = Amber)
                            }
                            Text(
                                "${transfer.source_branch} → ${transfer.destination_branch}" +
                                    if (transfer.quantity > 1) " · ${transfer.quantity} pcs" else "",
                                color = Ash,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "Released by ${transfer.dispatcher}",
                                color = Ash,
                                style = MaterialTheme.typography.bodySmall
                            )
                            OutlinedButton(
                                onClick = {
                                    if (id == null) return@OutlinedButton
                                    scope.launch {
                                        receivingId = id
                                        val error = SupabaseHelper.receiveTransfer(
                                            id,
                                            dispatcher.trim().ifBlank { "Received at ${transfer.destination_branch}" }
                                        )
                                        receivingId = null
                                        if (error == null) {
                                            snackbar.showSnackbar(
                                                "Booked in at ${transfer.destination_branch}."
                                            )
                                            LiveStore.refresh()
                                        } else {
                                            formError = error
                                        }
                                    }
                                },
                                enabled = id != null && receivingId == null,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (receivingId == id) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = Cyan
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text("Receive at ${transfer.destination_branch}")
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

/** What the scan turned out to be, stated plainly before anything is sent. */
@Composable
private fun IdentifiedCard(
    heading: String,
    lines: List<String>,
    tint: androidx.compose.ui.graphics.Color
) {
    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(heading, color = tint, style = MaterialTheme.typography.titleMedium)
            lines.forEach {
                Text(it, color = Ash, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
