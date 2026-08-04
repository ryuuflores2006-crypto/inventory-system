package com.ryuuflores2006.inventorysystem

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.ryuuflores2006.inventorysystem.data.SupabaseHelper
import com.ryuuflores2006.inventorysystem.ui.screens.BarcodeScanScreen
import com.ryuuflores2006.inventorysystem.ui.screens.BranchScreen
import com.ryuuflores2006.inventorysystem.ui.screens.InventoryListScreen
import com.ryuuflores2006.inventorysystem.ui.screens.RepairTicketScreen
import com.ryuuflores2006.inventorysystem.ui.screens.StockInScreen
import com.ryuuflores2006.inventorysystem.ui.screens.LoginScreen
import com.ryuuflores2006.inventorysystem.ui.screens.RegisterScreen
import com.ryuuflores2006.inventorysystem.ui.theme.InventorySystemTheme

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

enum class Screen { LOGIN, REGISTER, MAIN }

@Composable
fun MainApp() {
    var currentScreen by remember { mutableStateOf(Screen.LOGIN) }
    
    when (currentScreen) {
        Screen.LOGIN -> {
            LoginScreen(
                onLoginSuccess = { currentScreen = Screen.MAIN },
                onNavigateToRegister = { currentScreen = Screen.REGISTER }
            )
        }
        Screen.REGISTER -> {
            RegisterScreen(
                onRegisterSuccess = { currentScreen = Screen.MAIN },
                onNavigateToLogin = { currentScreen = Screen.LOGIN }
            )
        }
        Screen.MAIN -> {
            MainScreenContainer()
        }
    }
}

@Composable
fun MainScreenContainer() {
    var currentTab by remember { mutableIntStateOf(0) }
    
    // Callback scanner properties
    var isScannerActive by remember { mutableStateOf(false) }
    var onScannedCallback by remember { mutableStateOf<((String) -> Unit)?>(null) }

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
    } else {
        Scaffold(
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    NavigationBarItem(
                        selected = currentTab == 0,
                        onClick = { currentTab = 0 },
                        icon = { Icon(Icons.Default.List, contentDescription = "Inventory") },
                        label = { Text("Inventory") }
                    )
                    NavigationBarItem(
                        selected = currentTab == 1,
                        onClick = { currentTab = 1 },
                        icon = { Icon(Icons.Default.AddCircle, contentDescription = "Stock-In") },
                        label = { Text("Stock-In") }
                    )
                    NavigationBarItem(
                        selected = currentTab == 2,
                        onClick = { currentTab = 2 },
                        icon = { Icon(Icons.Default.Build, contentDescription = "Repairs") },
                        label = { Text("Repairs") }
                    )
                    NavigationBarItem(
                        selected = currentTab == 3,
                        onClick = { currentTab = 3 },
                        icon = { Icon(Icons.Default.Home, contentDescription = "Branches") },
                        label = { Text("Branches") }
                    )
                }
            }
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                when (currentTab) {
                    0 -> InventoryListScreen()
                    1 -> StockInScreen(onScanClick = { callback ->
                        onScannedCallback = callback
                        isScannerActive = true
                    })
                    2 -> RepairTicketScreen(onScanClick = { callback ->
                        onScannedCallback = callback
                        isScannerActive = true
                    })
                    3 -> BranchScreen()
                }
            }
        }
    }
}
