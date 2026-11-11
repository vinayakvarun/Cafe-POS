#!/bin/bash
sed -i '/if (currentRoute in listOf("pos", "menu", "settings")) {/c\
                if (currentRoute in listOf("pos", "menu", "analytics", "settings")) {' app/src/main/java/com/example/ui/AppNavigation.kt

sed -i '/icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },/i\
                        NavigationBarItem(\
                            icon = { Icon(androidx.compose.material.icons.Icons.Default.Analytics, contentDescription = "Analytics") },\
                            label = { Text("Analytics") },\
                            selected = currentRoute == "analytics",\
                            onClick = { navController.navigate("analytics") }\
                        )' app/src/main/java/com/example/ui/AppNavigation.kt

sed -i '/composable("settings") {/i\
            composable("analytics") {\
                val bills by viewModel.bills.collectAsState()\
                AnalyticsScreen(bills = bills)\
            }' app/src/main/java/com/example/ui/AppNavigation.kt
