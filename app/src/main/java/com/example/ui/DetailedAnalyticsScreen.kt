package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.models.Bill

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailedAnalyticsScreen(
    bills: List<Bill>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Calculate items sold (quantity and revenue)
    val itemStats = remember(bills) {
        bills.flatMap { it.items }
            .groupBy { it.item.name }
            .map { entry ->
                val quantity = entry.value.sumOf { it.quantity }
                val revenue = entry.value.sumOf { it.item.price * it.quantity }
                ItemStat(entry.key, quantity, revenue)
            }
            .sortedByDescending { it.quantity }
    }
    
    val maxQuantity = itemStats.maxOfOrNull { it.quantity }?.coerceAtLeast(1) ?: 1

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Item Sales Analytics") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (itemStats.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No sales data available.", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                Text(
                    text = "Top Selling Items",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(itemStats) { stat ->
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(stat.name, fontWeight = FontWeight.SemiBold)
                                Text("${stat.quantity} sold (₹${String.format("%.2f", stat.revenue)})", style = MaterialTheme.typography.bodySmall)
                            }
                            
                            // Bar chart horizontal bar
                            val fraction = stat.quantity.toFloat() / maxQuantity.toFloat()
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(24.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(fraction)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

data class ItemStat(val name: String, val quantity: Int, val revenue: Double)
