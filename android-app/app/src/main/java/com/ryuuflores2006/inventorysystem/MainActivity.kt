package com.ryuuflores2006.inventorysystem

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ryuuflores2006.inventorysystem.data.LiveStore
import com.ryuuflores2006.inventorysystem.data.SupabaseHelper
import com.ryuuflores2006.inventorysystem.data.UpdateManager
import com.ryuuflores2006.inventorysystem.ui.components.LivePill
import com.ryuuflores2006.inventorysystem.ui.screens.BarcodeScanScreen
import com.ryuuflores2006.inventorysystem.ui.screens.BranchScreen
import com.ryuuflores2006.inventorysystem.ui.screens.HomeScreen
import com.ryuuflores2006.inventorysystem.ui.screens.TransferScreen
import com.ryuuflores2006.inventorysystem.ui.screens.InventoryListScreen
import com.ryuuflores2006.inventorysystem.ui.screens.LoginScreen
import com.ryuuflores2006.inventorysystem.ui.screens.RegisterScreen
import com.ryuuflores2006.inventorysystem.ui.screens.RepairTicketScreen
import com.ryuuflores2006.inventorysystem.ui.screens.SellScreen
import com.ryuuflores2006.inventorysystem.ui.screens.StockInScreen
import com.ryuuflores2006.inventorysystem.ui.screens.UpdateDialog
import com.ryuuflores2006.inventorysystem.ui.theme.Ash
import com.ryuuflores2006.inventorysystem.ui.theme.Cyan
import com.ryuuflores2006.inventorysystem.ui.theme.GlassSurfaceRaised
import com.ryuuflores2006.inventorysystem.ui.theme.GlassBase
import com.ryuuflores2006.inventorysystem.ui.theme.InventorySystemTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Supabase Connection Client
        SupabaseHelper.init()

        setContent {
            InventorySystemTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainApp()
                }
            }
        }
    }
}

enum class Screen { STARTING, LOGIN, REGISTER, MAIN }

@Composable
fun MainApp() {
    // Start on a blank splash, not on the login form. The saved session is on
    // disk and takes a moment to load; showing the form first and correcting
    // it afterwards is what made the app look like it had forgotten you.
    var currentScreen by remember { mutableStateOf(Screen.STARTING) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        runCatching { SupabaseHelper.auth.awaitInitialization() }
        currentScreen =
            if (SupabaseHelper.auth.currentSessionOrNull() != null) Screen.MAIN else Screen.LOGIN
    }

    when (currentScreen) {
        // Nothing to draw yet — a flash of the wrong screen is worse than none.
        Screen.STARTING -> Box(modifier = Modifier.fillMaxSize())

        Screen.LOGIN -> LoginScreen(
            onLoginSuccess = { currentScreen = Screen.MAIN },
            onNavigateToRegister = { currentScreen = Screen.REGISTER }
        )

        Screen.REGISTER -> RegisterScreen(
            onRegisterSuccess = { currentScreen = Screen.MAIN },
            onNavigateToLogin = { currentScreen = Screen.LOGIN }
        )

        Screen.MAIN -> MainScreenContainer(
            onSignOut = {
                scope.launch {
                    runCatching { SupabaseHelper.auth.signOut() }
                    LiveStore.stopSync()
                    currentScreen = Screen.LOGIN
                }
            }
        )
    }
}

private data class Tab(val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("Home", Icons.Default.Dashboard),
    Tab("Stock", Icons.Default.Inventory2),
    Tab("Stock-In", Icons.Default.AddCircle),
    Tab("Sell", Icons.Default.PointOfSale),
    Tab("Repairs", Icons.Default.Build),
    Tab("Transfer", Icons.Default.LocalShipping),
    Tab("Branches", Icons.Default.Storefront)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreenContainer(onSignOut: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var currentTab by remember { mutableIntStateOf(0) }
    var menuOpen by remember { mutableStateOf(false) }

    // Callback scanner properties
    var isScannerActive by remember { mutableStateOf(false) }
    var onScannedCallback by remember { mutableStateOf<((String) -> Unit)?>(null) }

    val openScanner: (onScanned: (String) -> Unit) -> Unit = { callback ->
        onScannedCallback = callback
        isScannerActive = true
    }

    // One realtime subscription for the whole signed-in session, plus a quiet
    // check for a newer APK on launch.
    LaunchedEffect(Unit) {
        LiveStore.startSync(this)
        UpdateManager.check(context)
    }

    if (isScannerActive && onScannedCallback != null) {
        BarcodeScanScreen(
            onBarcodeScanned = { scannedValue ->
                onScannedCallback?.invoke(scannedValue)
                isScannerActive = false
                onScannedCallback = null
            },
            onClose = {
                isScannerActive = false
                onScannedCallback = null
            }
        )
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("J-LOU Inventory", style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = GlassBase,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                actions = {
                    LivePill(isLive = LiveStore.isLive, modifier = Modifier.padding(end = 4.dp))
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Ash)
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false },
                            modifier = Modifier.background(GlassSurfaceRaised)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Check for updates") },
                                leadingIcon = {
                                    Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = Cyan)
                                },
                                onClick = {
                                    menuOpen = false
                                    scope.launch { UpdateManager.check(context, manual = true) }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Sign out") },
                                leadingIcon = {
                                    Icon(Icons.Default.Logout, contentDescription = null, tint = Ash)
                                },
                                onClick = {
                                    menuOpen = false
                                    onSignOut()
                                }
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(containerColor = GlassBase) {
                TABS.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = currentTab == index,
                        onClick = { currentTab = index },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Cyan,
                            selectedTextColor = Cyan,
                            unselectedIconColor = Ash,
                            unselectedTextColor = Ash,
                            indicatorColor = Cyan.copy(alpha = 0.14f)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (currentTab) {
                0 -> HomeScreen(
                    onOpenInventory = { currentTab = 1 },
                    onOpenRepairs = { currentTab = 4 }
                )
                1 -> InventoryListScreen(onScanClick = openScanner)
                2 -> StockInScreen(onScanClick = openScanner)
                3 -> SellScreen(onScanClick = openScanner)
                4 -> RepairTicketScreen(onScanClick = openScanner)
                5 -> TransferScreen(onScanClick = openScanner)
                6 -> BranchScreen()
            }
        }
    }

    UpdateDialog()
}
