package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import com.example.models.Bill
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    bills: List<Bill>,
    onClearTodaysBills: () -> Unit,
    onNavigateToDetailed: () -> Unit,
    modifier: Modifier = Modifier
) {
    val totalRevenue = bills.sumOf { it.total }
    val totalBills = bills.size

    var showClearConfirm by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Analytics") },
            actions = {
                IconButton(onClick = { showClearConfirm = true }) {
                    Icon(Icons.Default.Delete, contentDescription = "Clear Today's Bills")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
        )
        
        if (showClearConfirm) {
            AlertDialog(
                onDismissRequest = { showClearConfirm = false },
                title = { Text("Clear Today's Bills?") },
                text = { Text("Are you sure you want to delete all bills created today? This action cannot be undone.") },
                confirmButton = {
                    TextButton(onClick = {
                        onClearTodaysBills()
                        showClearConfirm = false
                    }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearConfirm = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(modifier = Modifier.weight(1f)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Total Revenue", style = MaterialTheme.typography.titleSmall)
                    Text("₹${String.format("%.2f", totalRevenue)}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                }
            }
            Card(modifier = Modifier.weight(1f)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Total Bills", style = MaterialTheme.typography.titleSmall)
                    Text("$totalBills", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
        
        Button(
            onClick = onNavigateToDetailed,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text("View In-Depth Item Analytics")
        }
        
        Text(
            text = "Recent Bills",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(bills) { bill ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val format = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
                            Text(format.format(Date(bill.timestamp)), style = MaterialTheme.typography.bodySmall)
                            Text("Total: ₹${String.format("%.2f", bill.total)}", fontWeight = FontWeight.Bold)
                        }
                        Text("Items: ${bill.items.joinToString { "${it.item.name} x${it.quantity}" }}", style = MaterialTheme.typography.bodyMedium)
                        if (bill.customerPhone.isNotBlank()) {
                            Text("Customer: ${bill.customerPhone}", style = MaterialTheme.typography.bodySmall)
                        }
                        if (bill.utrNumber.isNotBlank()) {
                            Text(
                                text = "Paid via UPI (Ref: ${bill.utrNumber})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}
