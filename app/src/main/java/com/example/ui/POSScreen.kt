package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LunchDining
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.models.MenuItem
import com.example.models.OrderItem
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun POSScreen(
    menuItems: List<MenuItem>,
    onGenerateBill: (List<OrderItem>, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var phone by remember { mutableStateOf("") }
    var orderItems by remember { mutableStateOf<Map<String, OrderItem>>(emptyMap()) }

    Column(modifier = modifier.fillMaxSize()) {
        OutlinedTextField(
            value = phone,
            onValueChange = { newValue ->
                val digitsOnly = newValue.filter { it.isDigit() }
                if (digitsOnly.length <= 10) {
                    phone = digitsOnly
                }
            },
            label = { Text("Customer Phone (10 digits)") },
            placeholder = { Text("Enter 10-digit mobile number") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            isError = phone.isNotEmpty() && phone.length < 10,
            singleLine = true
        )

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            val grouped = menuItems.groupBy { it.category }
            grouped.forEach { (category, items) ->
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = category.ifEmpty { "Uncategorized" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }
                items(items, key = { it.id }) { item ->
                    val quantity = orderItems[item.id]?.quantity ?: 0
                    val isSelected = quantity > 0

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            // Item Image or Category Placeholder
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(96.dp)
                                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (item.imageUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = item.imageUrl,
                                        contentDescription = item.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    val icon = when {
                                        item.category.contains("bev", ignoreCase = true) || item.name.contains("tea", ignoreCase = true) || item.name.contains("coffee", ignoreCase = true) || item.name.contains("chai", ignoreCase = true) -> Icons.Default.LocalCafe
                                        item.category.contains("snack", ignoreCase = true) || item.name.contains("burger", ignoreCase = true) || item.name.contains("sandwich", ignoreCase = true) -> Icons.Default.LunchDining
                                        else -> Icons.Default.Fastfood
                                    }
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }

                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = item.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "₹${String.format(Locale.US, "%.2f", item.price)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )

                                    // Tax Badge
                                    Surface(
                                        color = when (item.taxType) {
                                            MenuItem.TAX_NO_TAX -> MaterialTheme.colorScheme.surfaceVariant
                                            MenuItem.TAX_CUSTOM -> MaterialTheme.colorScheme.tertiaryContainer
                                            else -> MaterialTheme.colorScheme.secondaryContainer
                                        },
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = item.taxLabel,
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                            color = when (item.taxType) {
                                                MenuItem.TAX_NO_TAX -> MaterialTheme.colorScheme.onSurfaceVariant
                                                MenuItem.TAX_CUSTOM -> MaterialTheme.colorScheme.onTertiaryContainer
                                                else -> MaterialTheme.colorScheme.onSecondaryContainer
                                            },
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    IconButton(
                                        onClick = {
                                            if (quantity > 0) {
                                                val newQ = quantity - 1
                                                val newMap = orderItems.toMutableMap()
                                                if (newQ == 0) {
                                                    newMap.remove(item.id)
                                                } else {
                                                    newMap[item.id] = OrderItem(item, newQ)
                                                }
                                                orderItems = newMap
                                            }
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(Icons.Default.Remove, contentDescription = "Remove")
                                    }

                                    Text(
                                        text = "$quantity",
                                        modifier = Modifier.padding(horizontal = 8.dp),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )

                                    IconButton(
                                        onClick = {
                                            val newQ = quantity + 1
                                            val newMap = orderItems.toMutableMap()
                                            newMap[item.id] = OrderItem(item, newQ)
                                            orderItems = newMap
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "Add")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        val subTotal = orderItems.values.sumOf { it.item.price * it.quantity }
        val estTax = orderItems.values.sumOf { (it.item.price * it.quantity) * (it.item.effectiveTaxRate / 100.0) }
        val grandTotal = subTotal + estTax

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Total: ₹${String.format(Locale.US, "%.2f", grandTotal)}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Subtotal: ₹${String.format(Locale.US, "%.2f", subTotal)} + Tax: ₹${String.format(Locale.US, "%.2f", estTax)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Button(
                    onClick = { onGenerateBill(orderItems.values.toList(), phone) },
                    enabled = orderItems.isNotEmpty() && phone.length == 10,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Generate Bill")
                }
            }
        }
    }
}
