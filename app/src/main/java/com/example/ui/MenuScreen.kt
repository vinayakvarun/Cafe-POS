package com.example.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.models.MenuItem
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuScreen(
    menuItems: List<MenuItem>,
    onAddItem: (MenuItem) -> Unit,
    onDeleteItem: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var itemToEdit by remember { mutableStateOf<MenuItem?>(null) }
    var isAddingNew by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Menu Management", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { isAddingNew = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("add_menu_item_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Item")
            }
        },
        modifier = modifier
    ) { padding ->
        if (menuItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Restaurant,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = "No menu items yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Tap the + button to add your first menu item",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + 80.dp,
                    start = 16.dp,
                    end = 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                val grouped = menuItems.groupBy { it.category }
                grouped.forEach { (category, items) ->
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = category.ifEmpty { "Uncategorized" },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                    items(items, key = { it.id }) { item ->
                        MenuItemRow(
                            item = item,
                            onEdit = { itemToEdit = item },
                            onDelete = { onDeleteItem(item.id) }
                        )
                    }
                }
            }
        }
    }

    if (isAddingNew) {
        val existingCategories = menuItems.map { it.category }.filter { it.isNotBlank() }.distinct()
        ItemDialog(
            title = "Add Menu Item",
            initialItem = MenuItem(),
            existingCategories = existingCategories,
            onDismiss = { isAddingNew = false },
            onSave = {
                onAddItem(it)
                isAddingNew = false
            }
        )
    }

    if (itemToEdit != null) {
        val existingCategories = menuItems.map { it.category }.filter { it.isNotBlank() }.distinct()
        ItemDialog(
            title = "Edit Menu Item",
            initialItem = itemToEdit!!,
            existingCategories = existingCategories,
            onDismiss = { itemToEdit = null },
            onSave = {
                onAddItem(it)
                itemToEdit = null
            }
        )
    }
}

@Composable
fun MenuItemRow(
    item: MenuItem,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Item Image or Category Icon
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
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
                    Icon(
                        imageVector = Icons.Default.Fastfood,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // Info column
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "₹${String.format(Locale.US, "%.2f", item.price)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Tax Badge
                    Surface(
                        color = when (item.taxType) {
                            MenuItem.TAX_NO_TAX -> MaterialTheme.colorScheme.surfaceVariant
                            MenuItem.TAX_CUSTOM -> MaterialTheme.colorScheme.tertiaryContainer
                            else -> MaterialTheme.colorScheme.secondaryContainer
                        },
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = item.taxLabel,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = when (item.taxType) {
                                MenuItem.TAX_NO_TAX -> MaterialTheme.colorScheme.onSurfaceVariant
                                MenuItem.TAX_CUSTOM -> MaterialTheme.colorScheme.onTertiaryContainer
                                else -> MaterialTheme.colorScheme.onSecondaryContainer
                            },
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Actions
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit Item",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete Item",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemDialog(
    title: String,
    initialItem: MenuItem,
    existingCategories: List<String>,
    onDismiss: () -> Unit,
    onSave: (MenuItem) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(initialItem.name) }
    var category by remember { mutableStateOf(initialItem.category) }
    var price by remember { mutableStateOf(if (initialItem.price > 0) initialItem.price.toString() else "") }
    var taxType by remember { mutableStateOf(initialItem.taxType) }
    var customTaxRate by remember {
        mutableStateOf(if (initialItem.customTaxRate > 0) initialItem.customTaxRate.toString() else "5")
    }
    var imageUrl by remember { mutableStateOf(initialItem.imageUrl) }
    var showUrlInput by remember { mutableStateOf(false) }
    var urlInputText by remember { mutableStateOf("") }
    var expandedCategory by remember { mutableStateOf(false) }

    // Android Photo Picker launcher
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                // Copy selected image to app's internal files storage for persistent access
                val filename = "item_img_${System.currentTimeMillis()}.jpg"
                val destFile = File(context.filesDir, filename)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
                imageUrl = destFile.absolutePath
            } catch (e: Exception) {
                imageUrl = uri.toString()
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = title, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Image section
                Text(
                    text = "Item Image",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )

                if (imageUrl.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                    ) {
                        AsyncImage(
                            model = imageUrl,
                            contentDescription = "Selected Item Image",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        IconButton(
                            onClick = { imageUrl = "" },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .size(32.dp)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), CircleShape)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Remove Image",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (imageUrl.isBlank()) "Choose Photo" else "Change Photo")
                    }

                    OutlinedButton(
                        onClick = { showUrlInput = !showUrlInput }
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("URL")
                    }
                }

                if (showUrlInput) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = urlInputText,
                            onValueChange = { urlInputText = it },
                            label = { Text("Image Web URL") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                if (urlInputText.isNotBlank()) {
                                    imageUrl = urlInputText.trim()
                                    showUrlInput = false
                                }
                            }
                        ) {
                            Text("Use")
                        }
                    }
                }

                HorizontalDivider()

                // Category selector
                ExposedDropdownMenuBox(
                    expanded = expandedCategory,
                    onExpandedChange = { expandedCategory = !expandedCategory }
                ) {
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        label = { Text("Category (e.g. Beverages, Snacks)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    if (existingCategories.isNotEmpty()) {
                        ExposedDropdownMenu(
                            expanded = expandedCategory,
                            onDismissRequest = { expandedCategory = false }
                        ) {
                            existingCategories.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat) },
                                    onClick = {
                                        category = cat
                                        expandedCategory = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Item Name
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Item Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Item Price
                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it },
                    label = { Text("Price (₹) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider()

                // Tax Configuration Section
                Text(
                    text = "Tax Settings",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )

                // Segmented tax choices
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TaxOptionCard(
                        title = "GST 5% (Standard)",
                        subtitle = "Standard restaurant GST rate",
                        isSelected = taxType == MenuItem.TAX_DEFAULT,
                        onClick = { taxType = MenuItem.TAX_DEFAULT }
                    )

                    TaxOptionCard(
                        title = "No Tax (0%)",
                        subtitle = "Tax-exempt or non-taxable item",
                        isSelected = taxType == MenuItem.TAX_NO_TAX,
                        onClick = { taxType = MenuItem.TAX_NO_TAX }
                    )

                    TaxOptionCard(
                        title = "Custom Tax",
                        subtitle = "Specify custom tax percentage (e.g. 12%, 18%)",
                        isSelected = taxType == MenuItem.TAX_CUSTOM,
                        onClick = { taxType = MenuItem.TAX_CUSTOM }
                    )
                }

                // Custom tax rate input if Custom Tax selected
                if (taxType == MenuItem.TAX_CUSTOM) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = customTaxRate,
                            onValueChange = { customTaxRate = it },
                            label = { Text("Tax Percentage (%)") },
                            leadingIcon = { Icon(Icons.Default.Percent, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text(
                            text = "Quick Presets:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val presets = listOf("0", "2.5", "5", "12", "18", "28")
                            items(presets) { preset ->
                                FilterChip(
                                    selected = customTaxRate == preset,
                                    onClick = { customTaxRate = preset },
                                    label = { Text("$preset%") }
                                )
                            }
                        }

                        val numericPrice = price.toDoubleOrNull() ?: 0.0
                        val numericRate = customTaxRate.toDoubleOrNull() ?: 0.0
                        if (numericPrice > 0) {
                            val computedTax = numericPrice * (numericRate / 100.0)
                            Text(
                                text = "Preview: ₹${String.format(Locale.US, "%.2f", numericPrice)} + ₹${String.format(Locale.US, "%.2f", computedTax)} ($numericRate% tax) = ₹${String.format(Locale.US, "%.2f", numericPrice + computedTax)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val p = price.toDoubleOrNull() ?: 0.0
                    val rate = customTaxRate.toDoubleOrNull() ?: 0.0
                    if (name.isNotBlank() && p > 0) {
                        val updated = initialItem.copy(
                            name = name.trim(),
                            category = category.trim().ifEmpty { "General" },
                            price = p,
                            taxType = taxType,
                            customTaxRate = if (taxType == MenuItem.TAX_CUSTOM) rate else 5.0,
                            imageUrl = imageUrl.trim()
                        )
                        onSave(updated)
                    }
                },
                enabled = name.isNotBlank() && (price.toDoubleOrNull() ?: 0.0) > 0
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun TaxOptionCard(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            RadioButton(
                selected = isSelected,
                onClick = onClick,
                modifier = Modifier.size(20.dp)
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
