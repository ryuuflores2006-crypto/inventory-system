package com.ryuuflores2006.inventorysystem.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Size
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.ryuuflores2006.inventorysystem.data.ScanResolver
import com.ryuuflores2006.inventorysystem.data.TacEntry
import com.ryuuflores2006.inventorysystem.data.TacLookup
import com.ryuuflores2006.inventorysystem.ui.components.StatusPill
import com.ryuuflores2006.inventorysystem.ui.components.peso
import com.ryuuflores2006.inventorysystem.ui.theme.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Camera scanner that names what it just read before you commit to it.
 *
 * The moment a code is decoded the preview stops and a card reports what the
 * code is (IMEI or SKU) and, if the shop has seen it before, which exact device
 * or part it belongs to — pulled from [ScanResolver], i.e. from our own rows.
 * A barcode contains no brand or model on its own, so anything we cannot
 * recognise is reported plainly as new rather than guessed at.
 *
 * [onBarcodeScanned] receives the cleaned value: digits only for an IMEI, the
 * trimmed text for a SKU.
 */
@OptIn(ExperimentalGetImage::class)
@Composable
fun BarcodeScanScreen(
    onBarcodeScanned: (String) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionAsked by remember { mutableStateOf(false) }

    // Set once a code is decoded; also gates the analyzer so a single scan
    // cannot fire the callback several times from consecutive frames.
    var result by remember { mutableStateOf<ScanResolver.Scan?>(null) }
    val claimed = remember { AtomicBoolean(false) }

    var camera by remember { mutableStateOf<Camera?>(null) }
    var torchOn by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasCameraPermission = granted
            permissionAsked = true
        }
    )

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    DisposableEffect(Unit) {
        onDispose { cameraExecutor.shutdown() }
    }

    if (!hasCameraPermission) {
        CameraPermissionNotice(
            asked = permissionAsked,
            onGrant = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            onSettings = {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null)
                    )
                )
            },
            onClose = onClose
        )
        return
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                // Only ever touched from the decode callback, which ML Kit posts
                // to the main thread — one writer, so plain vars are enough.
                var lastRead: String? = null
                var agreements = 0

                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()

                    val preview = Preview.Builder().build().apply {
                        setSurfaceProvider(previewView.surfaceProvider)
                    }

                    // Only the formats that actually appear on phone boxes,
                    // spare-part bags and supplier labels. A narrower list
                    // decodes faster and misreads less than ALL_FORMATS.
                    //
                    // ITF is deliberately absent. It carries no checksum and
                    // has no start/stop guard a decoder can trust, so a partly
                    // framed Code 128 IMEI strip can come back as a shorter,
                    // perfectly plausible ITF number — a wrong IMEI that looks
                    // right. Nothing in the shop is labelled with ITF anyway.
                    val scannerOptions = BarcodeScannerOptions.Builder()
                        .setBarcodeFormats(
                            Barcode.FORMAT_CODE_128,
                            Barcode.FORMAT_CODE_39,
                            Barcode.FORMAT_CODE_93,
                            Barcode.FORMAT_EAN_13,
                            Barcode.FORMAT_EAN_8,
                            Barcode.FORMAT_UPC_A,
                            Barcode.FORMAT_UPC_E,
                            Barcode.FORMAT_QR_CODE,
                            Barcode.FORMAT_DATA_MATRIX
                        )
                        .build()
                    val scanner = BarcodeScanning.getClient(scannerOptions)

                    val imageAnalysis = ImageAnalysis.Builder()
                        // An IMEI strip is a long run of thin bars. At 720p the
                        // narrowest ones land on barely a pixel and the decoder
                        // starts inventing digits; 1080p gives it room.
                        .setTargetResolution(Size(1920, 1080))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        val mediaImage = imageProxy.image
                        if (mediaImage == null || claimed.get()) {
                            imageProxy.close()
                            return@setAnalyzer
                        }
                        val image = InputImage.fromMediaImage(
                            mediaImage,
                            imageProxy.imageInfo.rotationDegrees
                        )
                        scanner.process(image)
                            .addOnSuccessListener { barcodes ->
                                val raw = barcodes.firstNotNullOfOrNull { it.rawValue?.takeIf(String::isNotBlank) }
                                if (raw == null) {
                                    // Lost it. Start the agreement count over so
                                    // two halves of two different reads can never
                                    // add up to a confirmation.
                                    lastRead = null
                                    agreements = 0
                                    return@addOnSuccessListener
                                }
                                // One frame is a guess. A blurred or half-framed
                                // strip decodes to something different each time,
                                // while a real code decodes to the same digits
                                // over and over — so wait for two frames to agree
                                // before believing it. At ~20fps this costs a
                                // fraction of a second and removes nearly every
                                // one-off misread.
                                if (raw == lastRead) agreements++ else { lastRead = raw; agreements = 1 }
                                if (agreements >= 2 && claimed.compareAndSet(false, true)) {
                                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                    result = ScanResolver.resolve(raw)
                                }
                            }
                            .addOnFailureListener { it.printStackTrace() }
                            .addOnCompleteListener { imageProxy.close() }
                    }

                    try {
                        cameraProvider.unbindAll()
                        camera = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageAnalysis
                        )
                    } catch (exc: Exception) {
                        exc.printStackTrace()
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // Framing guide — holding the code inside this window is what makes a
        // long IMEI strip decode on the first try.
        if (result == null) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.86f)
                    .height(150.dp)
                    .border(2.dp, Cyan, RoundedCornerShape(16.dp))
            )
            Text(
                "Line the barcode or IMEI strip up inside the frame",
                color = Chalk,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(top = 210.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }

        // Top controls
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            FilledTonalIconButton(
                onClick = onClose,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = Color.Black.copy(alpha = 0.55f),
                    contentColor = Chalk
                )
            ) { Icon(Icons.Default.Close, contentDescription = "Close scanner") }

            if (camera?.cameraInfo?.hasFlashUnit() == true) {
                FilledTonalIconButton(
                    onClick = {
                        torchOn = !torchOn
                        camera?.cameraControl?.enableTorch(torchOn)
                    },
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = Color.Black.copy(alpha = 0.55f),
                        contentColor = if (torchOn) Amber else Chalk
                    )
                ) {
                    Icon(
                        if (torchOn) Icons.Default.FlashlightOn else Icons.Default.FlashlightOff,
                        contentDescription = if (torchOn) "Turn torch off" else "Turn torch on"
                    )
                }
            }
        }

        // A clean 15-digit IMEI needs no confirming — the check digit already
        // proves it decoded correctly, so the number goes straight into the
        // form and the scanner closes. Anything less certain (a short read, a
        // SKU) still gets the card, so a misread never lands silently.
        LaunchedEffect(result) {
            val scan = result
            if (scan != null && scan.isImei) {
                camera?.cameraControl?.enableTorch(false)
                onBarcodeScanned(scan.value)
            }
        }

        result?.takeIf { !it.isImei }?.let { scan ->
            ScanResultCard(
                scan = scan,
                modifier = Modifier.align(Alignment.BottomCenter),
                onRescan = {
                    result = null
                    claimed.set(false)
                },
                onUse = {
                    camera?.cameraControl?.enableTorch(false)
                    onBarcodeScanned(scan.value)
                }
            )
        }
    }
}

/**
 * What we read, what kind of code it is, and — when we recognise it — exactly
 * which unit it is, so a wrong scan is caught before it reaches a form.
 */
@Composable
private fun ScanResultCard(
    scan: ScanResolver.Scan,
    modifier: Modifier = Modifier,
    onRescan: () -> Unit,
    onUse: () -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(20.dp),
        color = Ink700,
        tonalElevation = 6.dp
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusPill(
                    text = when (scan.kind) {
                        ScanResolver.CodeKind.IMEI -> "IMEI"
                        ScanResolver.CodeKind.PARTIAL_IMEI -> "IMEI (check digits)"
                        ScanResolver.CodeKind.SKU -> "SKU / barcode"
                    },
                    color = if (scan.isImei) Emerald else Azure,
                    dense = true
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    scan.value,
                    style = MaterialTheme.typography.titleMedium,
                    color = Chalk
                )
            }

            Spacer(Modifier.height(12.dp))

            when (val match = scan.match) {
                is ScanResolver.Match.Device -> {
                    val g = match.gadget
                    IdentityBlock(
                        heading = "${g.brand} ${g.model}",
                        lines = listOf(
                            listOfNotNull(
                                g.storage.takeIf { it.isNotBlank() },
                                g.ram.takeIf { it.isNotBlank() },
                                g.color.takeIf { it.isNotBlank() }
                            ).joinToString(" · "),
                            "${g.status} at ${g.current_branch}",
                            "SKU ${g.sku} · ${peso(g.retail_price)}"
                        ),
                        tint = gadgetStatusColor(g.status)
                    )
                }

                is ScanResolver.Match.Part -> {
                    val p = match.part
                    IdentityBlock(
                        heading = p.part_name,
                        lines = listOf(
                            "${p.stock_qty} in stock at ${p.branch_location}",
                            "SKU ${p.sku} · ${peso(p.service_price)}",
                            p.compatible_models.joinToString(", ").ifBlank { "No fitment listed" }
                        ),
                        tint = if (p.stock_qty <= p.minimum_stock_threshold) Amber else Azure
                    )
                }

                is ScanResolver.Match.Ticket -> {
                    val t = match.ticket
                    IdentityBlock(
                        heading = t.device_model,
                        lines = listOf(
                            "On a repair ticket for ${t.customer_name}",
                            "${t.ticket_status} at ${t.branch_location}"
                        ),
                        tint = ticketStatusColor(t.ticket_status)
                    )
                }

                is ScanResolver.Match.KnownSku -> {
                    val g = match.example
                    IdentityBlock(
                        heading = "${g.brand} ${g.model}",
                        lines = listOf(
                            "You have stocked this SKU before",
                            listOfNotNull(
                                g.storage.takeIf { it.isNotBlank() },
                                g.ram.takeIf { it.isNotBlank() }
                            ).joinToString(" · "),
                            "The details will be filled in for you"
                        ),
                        tint = Cyan
                    )
                }

                is ScanResolver.Match.SameModel -> {
                    val g = match.example
                    IdentityBlock(
                        heading = "${g.brand} ${g.model}",
                        lines = listOf(
                            "New unit — same model as stock you already carry",
                            listOfNotNull(
                                g.storage.takeIf { it.isNotBlank() },
                                g.ram.takeIf { it.isNotBlank() }
                            ).joinToString(" · "),
                            "Recognised from the IMEI's model code"
                        ),
                        tint = Emerald
                    )
                }

                ScanResolver.Match.Unknown -> {
                    // Nothing of ours matches, so ask what the model code says.
                    // Runs once per model and is cached for everyone after that.
                    var tac by remember(scan.value) { mutableStateOf<TacEntry?>(null) }
                    var looking by remember(scan.value) { mutableStateOf(scan.isImei) }

                    LaunchedEffect(scan.value) {
                        if (scan.isImei) {
                            tac = TacLookup.identify(scan.value)
                            looking = false
                        }
                    }

                    when {
                        tac?.label != null -> IdentityBlock(
                            heading = tac!!.label!!,
                            lines = listOfNotNull(
                                "New to your stock — identified from the IMEI",
                                tac!!.release_year?.let { "Released $it" }
                            ),
                            tint = Cyan
                        )

                        looking -> Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Cyan
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "Identifying the model…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Ash
                            )
                        }

                        else -> IdentityBlock(
                            heading = "Not in the system yet",
                            lines = listOf(
                                if (scan.isImei) {
                                    "A new unit we could not name. Type the brand and " +
                                        "model once and every later unit fills itself in."
                                } else {
                                    "No stock matches this code yet."
                                }
                            ),
                            tint = Slate
                        )
                    }
                }
            }

            if (scan.kind == ScanResolver.CodeKind.PARTIAL_IMEI) {
                Spacer(Modifier.height(10.dp))
                Text(
                    if (scan.value.length == 15) {
                        "That IMEI failed its check digit — the camera may have misread it. " +
                            "Compare it with the label before using it."
                    } else {
                        "Only ${scan.value.length} digits were read. A full IMEI is 15."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Amber
                )
            }

            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onRescan,
                    modifier = Modifier.weight(1f)
                ) { Text("Scan again", color = Ash) }

                Button(
                    onClick = onUse,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Ink900)
                ) { Text("Use this") }
            }
        }
    }
}

@Composable
private fun IdentityBlock(heading: String, lines: List<String>, tint: Color) {
    Row {
        Box(
            modifier = Modifier
                .width(3.dp)
                .heightIn(min = 40.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(tint)
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(heading, style = MaterialTheme.typography.titleLarge, color = Chalk)
            lines.filter { it.isNotBlank() }.forEach {
                Text(it, style = MaterialTheme.typography.bodySmall, color = Ash)
            }
        }
    }
}

@Composable
private fun CameraPermissionNotice(
    asked: Boolean,
    onGrant: () -> Unit,
    onSettings: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Camera access needed", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "The scanner reads IMEI strips and SKU barcodes with the back camera. " +
                "Nothing is recorded — frames are decoded and discarded.",
            style = MaterialTheme.typography.bodyMedium,
            color = Ash,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = if (asked) onSettings else onGrant,
            colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Ink900)
        ) { Text(if (asked) "Open app settings" else "Allow camera") }
        TextButton(onClick = onClose) { Text("Cancel", color = Ash) }
    }
}
