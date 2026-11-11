package com.example.ui

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.example.models.Bill
import com.example.models.BusinessInfo
import com.example.models.OrderItem
import com.example.youpi.YouPiPaymentManager
import com.example.youpi.YouPiPermissionHelper
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.util.Locale
import android.graphics.Color as AndroidColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillScreen(
    businessInfo: BusinessInfo,
    orderItems: List<OrderItem>,
    phone: String,
    onBack: () -> Unit,
    onCloseBill: (Bill) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var hasNotificationAccess by remember { mutableStateOf(YouPiPermissionHelper.isNotificationAccessGranted(context)) }
    var hasSmsPermission by remember { mutableStateOf(YouPiPermissionHelper.isSmsPermissionGranted(context)) }

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        hasSmsPermission = YouPiPermissionHelper.isSmsPermissionGranted(context)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasNotificationAccess = YouPiPermissionHelper.isNotificationAccessGranted(context)
                hasSmsPermission = YouPiPermissionHelper.isSmsPermissionGranted(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val subTotal = orderItems.sumOf { it.item.price * it.quantity }
    val gst = orderItems.sumOf { (it.item.price * it.quantity) * (it.item.effectiveTaxRate / 100.0) }
    val total = subTotal + gst
    val formattedTotal = String.format(Locale.US, "%.2f", total)
    val upiName = if (businessInfo.payeeName.isNotBlank()) businessInfo.payeeName.trim() else businessInfo.name.trim()
    val transactionRef = remember { "BILL${System.currentTimeMillis()}" }
    val upiIdToUse = if (businessInfo.upiId.isNotBlank()) businessInfo.upiId.trim() else "9135649926@ibl"
    val upiString = "upi://pay?pa=$upiIdToUse&pn=${Uri.encode(upiName)}&tr=$transactionRef&tn=Bill%20Payment&am=$formattedTotal&cu=INR"
    val qrBitmap = remember(upiString) { generateQrCode(upiString) }
    var showQrDialog by remember { mutableStateOf(false) }

    // Collect YouPI Payment State
    val paymentState by YouPiPaymentManager.paymentState.collectAsState()

    var autoCloseRemainingSeconds by remember { mutableIntStateOf(3) }
    var isAutoClosePaused by remember { mutableStateOf(false) }

    // Chime when payment is received
    LaunchedEffect(paymentState.isPaymentReceived) {
        if (paymentState.isPaymentReceived) {
            try {
                val toneGen = android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 90)
                toneGen.startTone(android.media.ToneGenerator.TONE_PROP_ACK, 250)
            } catch (_: Throwable) {}
        }
    }

    // Auto-close dialog and save bill if QR dialog is showing when payment is received
    LaunchedEffect(paymentState.isPaymentReceived, showQrDialog, isAutoClosePaused) {
        if (paymentState.isPaymentReceived && showQrDialog && !isAutoClosePaused && YouPiPaymentManager.autoCloseDialogOnSuccess) {
            autoCloseRemainingSeconds = YouPiPaymentManager.autoCloseDelaySeconds
            while (autoCloseRemainingSeconds > 0) {
                kotlinx.coroutines.delay(1000L)
                if (isAutoClosePaused || !showQrDialog) break
                autoCloseRemainingSeconds -= 1
            }
            if (!isAutoClosePaused && showQrDialog) {
                showQrDialog = false
                val currentBill = Bill(
                    customerPhone = phone,
                    items = orderItems,
                    subTotal = subTotal,
                    gst = gst,
                    total = total,
                    paymentStatus = "PAID_UPI_YOUPI",
                    utrNumber = paymentState.utr
                )
                onCloseBill(currentBill)
            }
        }
    }

    // Start YouPI listening session on mount, stop on dispose
    LaunchedEffect(total, transactionRef, upiIdToUse) {
        YouPiPaymentManager.startSession(
            expectedAmount = total,
            transactionRef = transactionRef,
            upiId = upiIdToUse
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            YouPiPaymentManager.stopSession()
        }
    }

    val billText = buildString {
        appendLine("🧾 *${businessInfo.name}*")
        if (businessInfo.address.isNotBlank()) {
            appendLine(businessInfo.address)
        }
        appendLine("------------------------")
        orderItems.forEach {
            val lineTotal = it.item.price * it.quantity
            appendLine("${it.item.name} x${it.quantity} - ₹${String.format(Locale.US, "%.2f", lineTotal)} [${it.item.taxLabel}]")
        }
        appendLine("------------------------")
        appendLine("Subtotal: ₹${String.format(Locale.US, "%.2f", subTotal)}")
        appendLine("Total Tax: ₹${String.format(Locale.US, "%.2f", gst)}")
        appendLine("*Grand Total: ₹${String.format(Locale.US, "%.2f", total)}*")
        if (paymentState.isPaymentReceived) {
            appendLine("✅ *Payment Status: PAID via UPI*")
            if (paymentState.utr.isNotBlank()) {
                appendLine("UPI Ref / UTR: ${paymentState.utr}")
            }
        }
        appendLine("Thank you for your visit!")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Bill", fontWeight = FontWeight.Medium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Box(
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .size(32.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = businessInfo.name.take(2).uppercase().ifEmpty { "POS" },
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.background)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // YouPI Payment Live Status Banner
                if (paymentState.isPaymentReceived) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF81C784)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF2E7D32),
                                    modifier = Modifier.size(22.dp)
                                )
                                Column {
                                    Text(
                                        text = "Payment Recived Succeess",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color(0xFF1B5E20)
                                    )
                                    Text(
                                        text = "UTR: ${paymentState.utr} • ₹${String.format(Locale.US, "%.2f", paymentState.receivedAmount)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF2E7D32)
                                    )
                                }
                            }
                            Text(
                                text = "VERIFIED",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 10.sp,
                                color = Color(0xFF1B5E20),
                                modifier = Modifier
                                    .background(Color(0xFFC8E6C9), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                } else {
                    // Waiting for payment indicator with permission guidance
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(13.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Listening for PhonePe / UPI payment...",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (hasNotificationAccess && hasSmsPermission) {
                                Text(
                                    text = "LIVE",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2E7D32),
                                    modifier = Modifier
                                        .background(Color(0xFFE8F5E9), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        if (!hasNotificationAccess || !hasSmsPermission) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (!hasNotificationAccess) {
                                    FilledTonalButton(
                                        onClick = { YouPiPermissionHelper.openNotificationListenerSettings(context) },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(26.dp)
                                    ) {
                                        Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(11.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("Allow Notification Access", fontSize = 10.sp)
                                    }
                                }
                                if (!hasSmsPermission) {
                                    FilledTonalButton(
                                        onClick = { smsPermissionLauncher.launch(YouPiPermissionHelper.REQUIRED_SMS_PERMISSIONS) },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(26.dp)
                                    ) {
                                        Icon(Icons.Default.Sms, contentDescription = null, modifier = Modifier.size(11.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("Allow SMS", fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "GRAND TOTAL",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (paymentState.isPaymentReceived) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "PAID",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2E7D32),
                                    modifier = Modifier
                                        .background(Color(0xFFE8F5E9), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Text(
                            text = "₹${String.format(Locale.US, "%.2f", total)}",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (paymentState.isPaymentReceived) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onBackground
                        )
                    }
                    if (qrBitmap != null) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable { 
                                isAutoClosePaused = false
                                hasNotificationAccess = YouPiPermissionHelper.isNotificationAccessGranted(context)
                                hasSmsPermission = YouPiPermissionHelper.isSmsPermissionGranted(context)
                                YouPiPermissionHelper.ensureServiceConnected(context)
                                showQrDialog = true 
                            }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .background(Color.White, RoundedCornerShape(8.dp))
                                    .border(
                                        width = if (paymentState.isPaymentReceived) 2.dp else 1.dp,
                                        color = if (paymentState.isPaymentReceived) Color(0xFF4CAF50) else MaterialTheme.colorScheme.outlineVariant,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .padding(4.dp)
                            ) {
                                Image(
                                    bitmap = qrBitmap.asImageBitmap(),
                                    contentDescription = "Mini QR Code",
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Text(
                                text = if (paymentState.isPaymentReceived) "PAID" else "SCAN FOR UPI",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                fontWeight = FontWeight.Bold,
                                color = if (paymentState.isPaymentReceived) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { 
                                isAutoClosePaused = false
                                hasNotificationAccess = YouPiPermissionHelper.isNotificationAccessGranted(context)
                                hasSmsPermission = YouPiPermissionHelper.isSmsPermissionGranted(context)
                                YouPiPermissionHelper.ensureServiceConnected(context)
                                showQrDialog = true 
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = CircleShape,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (paymentState.isPaymentReceived) Color(0xFF4CAF50) else MaterialTheme.colorScheme.outline
                            )
                        ) {
                            Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (paymentState.isPaymentReceived) "View Receipt QR" else "QR Code")
                        }
                        Button(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    data = Uri.parse("whatsapp://send?phone=$phone&text=${Uri.encode(billText)}")
                                }
                                try {
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = CircleShape,
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                        ) {
                            Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Send Bill")
                        }
                    }

                    Button(
                        onClick = {
                            val currentBill = Bill(
                                customerPhone = phone,
                                items = orderItems,
                                subTotal = subTotal,
                                gst = gst,
                                total = total,
                                paymentStatus = if (paymentState.isPaymentReceived) "PAID_UPI_YOUPI" else "PAID",
                                utrNumber = paymentState.utr
                            )
                            onCloseBill(currentBill)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (paymentState.isPaymentReceived) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary,
                            contentColor = Color.White
                        )
                    ) {
                        if (paymentState.isPaymentReceived) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Complete & Save Bill", fontWeight = FontWeight.Bold)
                        } else {
                            Text("Close Bill & Save", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Customer Details Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "CUSTOMER DETAILS",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
                        }
                        Text(
                            text = if (phone.isNotBlank()) phone else "N/A",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Items Ordered Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Items Ordered",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), CircleShape)
                                .clickable { onBack() }
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "+ Add Item",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(orderItems) { item ->
                            val lineTotal = item.item.price * item.quantity

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    // Mini image thumbnail if exists
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (item.item.imageUrl.isNotBlank()) {
                                            AsyncImage(
                                                model = item.item.imageUrl,
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Icon(
                                                Icons.Default.Fastfood,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }

                                    Column {
                                        Text(
                                            text = item.item.name,
                                            fontWeight = FontWeight.SemiBold,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = "₹${String.format(Locale.US, "%.2f", item.item.price)} x ${item.quantity}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = "• ${item.item.taxLabel}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }

                                Text(
                                    text = "₹${String.format(Locale.US, "%.2f", lineTotal)}",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Subtotal", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("₹${String.format(Locale.US, "%.2f", subTotal)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Total Tax",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "₹${String.format(Locale.US, "%.2f", gst)}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Grand Total", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("₹${String.format(Locale.US, "%.2f", total)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }

    // Full-Screen Dialog with QR and Real-Time "Payment Recived Succeess" celebration
    if (showQrDialog) {
        Dialog(onDismissRequest = { showQrDialog = false }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (paymentState.isPaymentReceived) {
                        // CELEBRATION VIEW: Payment Recived Succeess
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .background(Color(0xFFE8F5E9), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "Success",
                                tint = Color(0xFF2E7D32),
                                modifier = Modifier.size(54.dp)
                            )
                        }

                        Text(
                            text = "Payment Recived Succeess",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF1B5E20),
                            textAlign = TextAlign.Center
                        )

                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F8E9)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Amount Paid:", style = MaterialTheme.typography.bodyMedium, color = Color.DarkGray)
                                    Text(
                                        "₹${String.format(Locale.US, "%.2f", paymentState.receivedAmount)}",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1B5E20)
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("UTR / Ref No:", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                    Text(
                                        paymentState.utr,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.Black
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Gateway:", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                    Text(
                                        "YouPI 0% MDR Verified",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF2E7D32)
                                    )
                                }
                            }
                        }

                        // Auto-closing countdown status bar
                        if (YouPiPaymentManager.autoCloseDialogOnSuccess) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFE8F5E9), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (isAutoClosePaused) "Auto-save paused" else "Auto-closing & saving bill in ${autoCloseRemainingSeconds}s...",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF1B5E20),
                                    fontWeight = FontWeight.Medium
                                )
                                TextButton(
                                    onClick = { isAutoClosePaused = !isAutoClosePaused },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                    modifier = Modifier.height(24.dp)
                                ) {
                                    Text(
                                        text = if (isAutoClosePaused) "Resume" else "Pause",
                                        fontSize = 11.sp,
                                        color = Color(0xFF2E7D32),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = {
                                showQrDialog = false
                                val currentBill = Bill(
                                    customerPhone = phone,
                                    items = orderItems,
                                    subTotal = subTotal,
                                    gst = gst,
                                    total = total,
                                    paymentStatus = "PAID_UPI_YOUPI",
                                    utrNumber = paymentState.utr
                                )
                                onCloseBill(currentBill)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                        ) {
                            Text("Complete & Save Bill", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { 
                                isAutoClosePaused = true
                                showQrDialog = false 
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Back to Bill")
                        }
                    } else {
                        // QR CODE DISPLAY VIEW
                        Text(
                            text = "Scan to Pay",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )

                        if (qrBitmap != null) {
                            Image(
                                bitmap = qrBitmap.asImageBitmap(),
                                contentDescription = "Full QR Code",
                                modifier = Modifier
                                    .size(240.dp)
                                    .border(1.dp, Color.LightGray, RoundedCornerShape(12.dp))
                                    .padding(8.dp)
                            )
                        }

                        Text(
                            text = "Amount: ₹${String.format(Locale.US, "%.2f", total)}\nUPI: $upiIdToUse",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.DarkGray,
                            textAlign = TextAlign.Center
                        )

                        // Real-time observer listening badge
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (hasNotificationAccess) Color(0xFFE8F5E9) else Color(0xFFE3F2FD), RoundedCornerShape(8.dp))
                                .border(1.dp, if (hasNotificationAccess) Color(0xFFA5D6A7) else Color(0xFF90CAF9), RoundedCornerShape(8.dp))
                                .padding(vertical = 8.dp, horizontal = 12.dp)
                        ) {
                            if (hasNotificationAccess) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(Color(0xFF2E7D32), CircleShape)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Listening for PhonePe • Auto-closes on payment",
                                    fontSize = 12.sp,
                                    color = Color(0xFF1B5E20),
                                    fontWeight = FontWeight.SemiBold
                                )
                            } else {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = Color(0xFF1976D2)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "YouPI observer waiting for permissions...",
                                    fontSize = 12.sp,
                                    color = Color(0xFF0D47A1),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        if (!hasNotificationAccess || !hasSmsPermission) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1)),
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFE082)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = Color(0xFFE65100),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "Auto-Detection Permissions Required",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = Color(0xFFE65100)
                                        )
                                    }
                                    Text(
                                        text = "Grant notification access and SMS permission so the POS automatically detects customer payments.",
                                        fontSize = 11.sp,
                                        color = Color(0xFF5D4037)
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        if (!hasNotificationAccess) {
                                            Button(
                                                onClick = { YouPiPermissionHelper.openNotificationListenerSettings(context) },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100)),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(34.dp)
                                            ) {
                                                Text("Enable Notification", fontSize = 10.sp)
                                            }
                                        }
                                        if (!hasSmsPermission) {
                                            OutlinedButton(
                                                onClick = { smsPermissionLauncher.launch(YouPiPermissionHelper.REQUIRED_SMS_PERMISSIONS) },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(34.dp)
                                            ) {
                                                Text("Allow SMS", fontSize = 10.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = { showQrDialog = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Close")
                        }
                    }
                }
            }
        }
    }
}

private fun generateQrCode(text: String): Bitmap? {
    return try {
        val size = 512
        val bitMatrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) AndroidColor.BLACK else AndroidColor.WHITE)
            }
        }
        bitmap
    } catch (e: Exception) {
        null
    }
}
