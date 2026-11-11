package com.example.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.models.OrderItem

@Composable
fun AppNavigation(
    viewModel: MainViewModel = viewModel(),
    navController: NavHostController = rememberNavController()
) {
    val isUserLoggedIn by viewModel.isUserLoggedIn.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val businessInfo by viewModel.businessInfo.collectAsState()
    val menuItems by viewModel.menuItems.collectAsState()
    val firebaseError by viewModel.firebaseError.collectAsState()

    var currentOrderItems by remember { mutableStateOf<List<OrderItem>>(emptyList()) }
    var currentCustomerPhone by remember { mutableStateOf("") }

    if (firebaseError != null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = firebaseError!!,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp),
                textAlign = TextAlign.Center
            )
        }
        return
    }

    if (isLoading && businessInfo == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    val initialStartDestination = remember {
        if (!viewModel.isUserLoggedIn.value) {
            "auth"
        } else if (viewModel.businessInfo.value == null) {
            "setup"
        } else {
            "pos"
        }
    }

    Scaffold(
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route

            if (isUserLoggedIn && businessInfo != null && currentRoute in listOf("pos", "menu", "analytics", "settings")) {
                NavigationBar {
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.PointOfSale, contentDescription = "POS") },
                        label = { Text("POS") },
                        selected = currentRoute == "pos",
                        onClick = {
                            if (currentRoute != "pos") {
                                navController.navigate("pos") {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.List, contentDescription = "Menu") },
                        label = { Text("Menu") },
                        selected = currentRoute == "menu",
                        onClick = {
                            if (currentRoute != "menu") {
                                navController.navigate("menu") {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Analytics, contentDescription = "Analytics") },
                        label = { Text("Analytics") },
                        selected = currentRoute == "analytics",
                        onClick = {
                            if (currentRoute != "analytics") {
                                navController.navigate("analytics") {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                        label = { Text("Settings") },
                        selected = currentRoute == "settings",
                        onClick = {
                            if (currentRoute != "settings") {
                                navController.navigate("settings") {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = initialStartDestination,
            modifier = Modifier.padding(padding)
        ) {
            composable("auth") {
                AuthScreen(
                    onAuthSuccess = {
                        val destination = if (viewModel.businessInfo.value == null) "setup" else "pos"
                        navController.navigate(destination) {
                            popUpTo("auth") { inclusive = true }
                        }
                    }
                )
            }
            composable("setup") {
                SetupScreen(
                    onSetupComplete = { info ->
                        viewModel.saveBusinessInfo(info)
                        navController.navigate("pos") {
                            popUpTo("setup") { inclusive = true }
                        }
                    }
                )
            }
            composable("analytics") {
                val bills by viewModel.bills.collectAsState()
                AnalyticsScreen(
                    bills = bills,
                    onClearTodaysBills = { viewModel.clearTodaysBills() },
                    onNavigateToDetailed = { navController.navigate("detailed_analytics") }
                )
            }
            composable("detailed_analytics") {
                val bills by viewModel.bills.collectAsState()
                DetailedAnalyticsScreen(
                    bills = bills,
                    onBack = { navController.popBackStack() }
                )
            }
            composable("settings") {
                SetupScreen(
                    initialInfo = businessInfo,
                    onSetupComplete = { info ->
                        viewModel.saveBusinessInfo(info)
                        navController.navigate("pos") {
                            popUpTo("pos") { inclusive = true }
                        }
                    },
                    onResetApp = {
                        viewModel.resetApp()
                        navController.navigate("auth") {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }
            composable("pos") {
                POSScreen(
                    menuItems = menuItems,
                    onGenerateBill = { items, phone ->
                        currentOrderItems = items
                        currentCustomerPhone = phone
                        navController.navigate("bill")
                    }
                )
            }
            composable("menu") {
                MenuScreen(
                    menuItems = menuItems,
                    onAddItem = { viewModel.addMenuItem(it) },
                    onDeleteItem = { viewModel.deleteMenuItem(it) }
                )
            }
            composable("bill") {
                businessInfo?.let { info ->
                    BillScreen(
                        businessInfo = info,
                        orderItems = currentOrderItems,
                        phone = currentCustomerPhone,
                        onBack = { navController.popBackStack() },
                        onCloseBill = {
                            viewModel.saveBill(it)
                            navController.navigate("pos") {
                                popUpTo("pos") { inclusive = true }
                            }
                        }
                    )
                }
            }
        }
    }
}
