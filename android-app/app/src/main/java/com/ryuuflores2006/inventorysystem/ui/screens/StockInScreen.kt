package com.ryuuflores2006.inventorysystem.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ryuuflores2006.inventorysystem.data.BranchStore
import com.ryuuflores2006.inventorysystem.data.RepairPart
import com.ryuuflores2006.inventorysystem.data.RetailGadget
import com.ryuuflores2006.inventorysystem.data.ScanResolver
import com.ryuuflores2006.inventorysystem.data.SupabaseHelper
import com.ryuuflores2006.inventorysystem.data.TacLookup
import com.ryuuflores2006.inventorysystem.ui.components.*
import com.ryuuflores2006.inventorysystem.ui.theme.Ash
import com.ryuuflores2006.inventorysystem.ui.theme.Cyan
import com.ryuuflores2006.inventorysystem.ui.theme.GlassSurfaceRaised
import kotlinx.coroutines.launch

/**
 * Receiving screen. Two tracks: a serialized phone (one row, one IMEI) or a
 * bulk part line (a quantity). The form validates before it touches the
 * network so a bad IMEI never reaches the database.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockInScreen(onScanClick: (onScanned: (String) -> Unit) -> Unit) {
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val snackbar = remember { SnackbarHostState() }

    var isSerialized by remember { mutableStateOf(true) }
    var selectedBranch by remember { mutableStateOf("") }

    // Branches come from the database, not a hardcoded list.
    LaunchedEffect(Unit) {
        if (BranchStore.branches.isEmpty()) BranchStore.refresh()
    }
    LaunchedEffect(BranchStore.branches) {
        if (selectedBranch !in BranchStore.names) selectedBranch = BranchStore.defaultName
    }

    // Serialized track
    var sku by remember { mutableStateOf("") }
    var brand by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var storage by remember { mutableStateOf("") }
    var ram by remember { mutableStateOf("") }
    var color by remember { mutableStateOf("") }
    var costPrice by remember { mutableStateOf("") }
    var retailPrice by remember { mutableStateOf("") }
    var imei1 by remember { mutableStateOf("") }
    var imei2 by remember { mutableStateOf("") }
    var supplierName by remember { mutableStateOf("") }

    // Bulk track
    var partName by remember { mutableStateOf("") }
    var compatibleModelsInput by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("") }
    var minThreshold by remember { mutableStateOf("5") }

    var isSubmitting by remember { mutableStateOf(false) }
    var formError by remember { mutableStateOf<String?>(null) }

    val imeiLooksWrong = imei1.isNotBlank() && imei1.length != 15
    val margin = (retailPrice.toDoubleOrNull() ?: 0.0) - (costPrice.toDoubleOrNull() ?: 0.0)

    /**
     * Copy what we already know about a scanned code into the empty fields.
     * Anything the user has already typed is left alone — this only fills gaps.
     */
    suspend fun autofillFrom(scanned: String) {
        val scan = ScanResolver.resolve(scanned)
        val example = when (val m = scan.match) {
            is ScanResolver.Match.Device -> {
                snackbar.showSnackbar(
                    "That IMEI is already logged as ${m.gadget.status.lowercase()} at ${m.gadget.current_branch}."
                )
                m.gadget
            }
            is ScanResolver.Match.SameModel -> m.example
            is ScanResolver.Match.KnownSku -> m.example
            else -> null
        }

        if (example == null) {
            // Nothing of ours matches. If it is an IMEI, the model code can
            // still name the handset — first unit of a model we have never
            // stocked, which is exactly the case worth filling in.
            if (!scan.isImei) return
            val named = TacLookup.identify(scan.value) ?: return
            if (brand.isBlank()) named.brand?.let { brand = it }
            if (model.isBlank()) named.model?.let { model = it }
            named.label?.let { snackbar.showSnackbar("Looks like a $it — brand and model filled in.") }
            return
        }

        if (brand.isBlank()) brand = example.brand
        if (model.isBlank()) model = example.model
        if (storage.isBlank()) storage = example.storage
        if (ram.isBlank()) ram = example.ram
        if (sku.isBlank()) sku = example.sku
        if (retailPrice.isBlank()) retailPrice = example.retail_price.toString()

        if (scan.match is ScanResolver.Match.SameModel) {
            snackbar.showSnackbar("Recognised as ${example.brand} ${example.model} — details filled in.")
        }
    }

    // Typing the IMEI by hand should recognise the device just as scanning
    // does — the camera is a shortcut, not the only way in. Fires once the
    // 15th digit lands, and once per value.
    var lastLookedUp by remember { mutableStateOf("") }
    LaunchedEffect(imei1) {
        if (imei1.length == 15 && imei1 != lastLookedUp) {
            lastLookedUp = imei1
            autofillFrom(imei1)
        }
    }

    fun resetForm() {
        sku = ""; brand = ""; model = ""; storage = ""; ram = ""; color = ""
        costPrice = ""; retailPrice = ""; imei1 = ""; imei2 = ""; supplierName = ""
        partName = ""; compatibleModelsInput = ""; quantity = ""
    }

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
                title = "Stock-In",
                subtitle = "Log a delivery into a branch"
            )

            ErrorBanner(formError)

            AppDropdown(
                label = "Receiving branch",
                selected = selectedBranch,
                options = BranchStore.names,
                onSelect = { selectedBranch = it },
                emptyHint = "No branches yet — add one first"
            )

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = isSerialized,
                    onClick = { isSerialized = true; formError = null },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                    colors = segmentColors(),
                    label = { Text("Phone (serialized)") }
                )
                SegmentedButton(
                    selected = !isSerialized,
                    onClick = { isSerialized = false; formError = null },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                    colors = segmentColors(),
                    label = { Text("Parts (bulk)") }
                )
            }

            if (isSerialized) {
                SectionLabel("Device")
                AppTextField(sku, { sku = it }, "SKU *", placeholder = "e.g. IPH15P-256-BLK")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppTextField(brand, { brand = it }, "Brand", modifier = Modifier.weight(1f))
                    AppTextField(model, { model = it }, "Model", modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppDropdown(
                        label = "Storage",
                        selected = storage,
                        options = listOf("64GB", "128GB", "256GB", "512GB", "1TB"),
                        onSelect = { storage = it },
                        emptyHint = "—",
                        modifier = Modifier.weight(1f)
                    )
                    AppDropdown(
                        label = "RAM",
                        selected = ram,
                        options = listOf("4GB", "6GB", "8GB", "12GB", "16GB"),
                        onSelect = { ram = it },
                        emptyHint = "—",
                        modifier = Modifier.weight(1f)
                    )
                }
                AppTextField(color, { color = it }, "Colour")

                SectionLabel("Identity")
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.Top
                ) {
                    AppTextField(
                        value = imei1,
                        onValueChange = { imei1 = it.filter { c -> c.isDigit() }.take(15) },
                        label = "IMEI 1 *",
                        keyboardType = KeyboardType.Number,
                        isError = imeiLooksWrong,
                        supportingText = if (imeiLooksWrong) "${imei1.length}/15 digits" else null,
                        modifier = Modifier.weight(1f)
                    )
                    FilledTonalIconButton(
                        onClick = {
                            onScanClick { scanned ->
                                imei1 = scanned.filter { it.isDigit() }.take(15)
                                // A scan we recognise fills in the rest of the
                                // description, so receiving a repeat model is
                                // one scan and a price.
                                scope.launch { autofillFrom(scanned) }
                            }
                        },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = GlassSurfaceRaised,
                            contentColor = Cyan
                        ),
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .size(56.dp)
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan IMEI")
                    }
                }
                AppTextField(
                    value = imei2,
                    onValueChange = { imei2 = it.filter { c -> c.isDigit() }.take(15) },
                    label = "IMEI 2 (optional)",
                    keyboardType = KeyboardType.Number
                )
                AppTextField(supplierName, { supplierName = it }, "Supplier (optional)")
            } else {
                SectionLabel("Part")
                AppTextField(sku, { sku = it }, "Part SKU *")
                AppTextField(partName, { partName = it }, "Part or accessory name")
                AppTextField(
                    value = compatibleModelsInput,
                    onValueChange = { compatibleModelsInput = it },
                    label = "Fits which models",
                    placeholder = "Comma-separated, e.g. iPhone 13, iPhone 14"
                )

                SectionLabel("Quantity")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppTextField(
                        value = quantity,
                        onValueChange = { quantity = it.filter { c -> c.isDigit() } },
                        label = "Qty received",
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.weight(1f)
                    )
                    AppTextField(
                        value = minThreshold,
                        onValueChange = { minThreshold = it.filter { c -> c.isDigit() } },
                        label = "Reorder at",
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            SectionLabel("Pricing")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppTextField(
                    value = costPrice,
                    onValueChange = { costPrice = it },
                    label = "Cost price",
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f)
                )
                AppTextField(
                    value = retailPrice,
                    onValueChange = { retailPrice = it },
                    label = if (isSerialized) "Retail price" else "Service price",
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f)
                )
            }
            if (costPrice.isNotBlank() && retailPrice.isNotBlank()) {
                Text(
                    "Margin ${peso(margin)} per unit",
                    style = MaterialTheme.typography.bodySmall,
                    color = Ash
                )
            }

            Spacer(Modifier.height(8.dp))

            PrimaryButton(
                text = if (isSerialized) "Receive device" else "Receive parts",
                busy = isSubmitting,
                icon = Icons.Default.CheckCircle,
                onClick = {
                    formError = null
                    if (selectedBranch.isBlank()) {
                        formError = "Add a branch first, from the Branches tab."
                        return@PrimaryButton
                    }
                    if (sku.isBlank()) {
                        formError = "SKU is required."
                        return@PrimaryButton
                    }
                    if (isSerialized && imei1.length != 15) {
                        formError = "IMEI 1 must be exactly 15 digits."
                        return@PrimaryButton
                    }
                    isSubmitting = true
                    scope.launch {
                        val cost = costPrice.toDoubleOrNull() ?: 0.0
                        val retail = retailPrice.toDoubleOrNull() ?: 0.0
                        val success = if (isSerialized) {
                            SupabaseHelper.insertGadget(
                                RetailGadget(
                                    sku = sku.trim(),
                                    brand = brand.trim(),
                                    model = model.trim(),
                                    storage = storage,
                                    ram = ram,
                                    color = color.trim(),
                                    cost_price = cost,
                                    retail_price = retail,
                                    current_branch = selectedBranch,
                                    status = "In Stock",
                                    imei_1 = imei1,
                                    imei_2 = imei2.takeIf { it.isNotBlank() },
                                    supplier_name = supplierName.takeIf { it.isNotBlank() }
                                )
                            )
                        } else {
                            SupabaseHelper.insertPartStock(
                                RepairPart(
                                    sku = sku.trim(),
                                    part_name = partName.trim(),
                                    compatible_models = compatibleModelsInput
                                        .split(",")
                                        .map { it.trim() }
                                        .filter { it.isNotBlank() },
                                    branch_location = selectedBranch,
                                    stock_qty = quantity.toIntOrNull() ?: 1,
                                    minimum_stock_threshold = minThreshold.toIntOrNull() ?: 5,
                                    cost_price = cost,
                                    service_price = retail
                                )
                            )
                        }
                        isSubmitting = false
                        if (success) {
                            resetForm()
                            snackbar.showSnackbar("Logged into $selectedBranch")
                        } else {
                            formError =
                                "Could not save. That SKU or IMEI may already exist, or you are offline."
                        }
                    }
                }
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun segmentColors() = SegmentedButtonDefaults.colors(
    activeContainerColor = Cyan.copy(alpha = 0.16f),
    activeContentColor = Cyan,
    activeBorderColor = Cyan.copy(alpha = 0.5f),
    inactiveContainerColor = GlassSurfaceRaised,
    inactiveContentColor = Ash,
    inactiveBorderColor = com.ryuuflores2006.inventorysystem.ui.theme.GlassBorder
)
