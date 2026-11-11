package com.example.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.models.BusinessInfo
import com.example.youpi.YouPiPaymentManager
import com.example.youpi.YouPiPermissionHelper

@Composable
fun SetupScreen(
    initialInfo: BusinessInfo? = null,
    onSetupComplete: (BusinessInfo) -> Unit,
    onResetApp: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(initialInfo?.name ?: "") }
    var address by remember { mutableStateOf(initialInfo?.address ?: "") }
    var upiId by remember { mutableStateOf(initialInfo?.upiId ?: "") }
    var payeeName by remember { mutableStateOf(initialInfo?.payeeName ?: "") }

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

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (initialInfo == null) "Setup Business" else "Business Settings",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Business Name (Display)") },
            placeholder = { Text("e.g. Chai Point") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(
            value = address,
            onValueChange = { address = it },
            label = { Text("Address") },
            placeholder = { Text("e.g. Shop 4, High Street") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(
            value = upiId,
            onValueChange = { upiId = it },
            label = { Text("UPI ID") },
            placeholder = { Text("e.g. yourname@upi or 9876543210@ibl") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
        )
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = payeeName,
            onValueChange = { payeeName = it },
            label = { Text("Registered UPI Payee Name") },
            placeholder = { Text("e.g. John Doe / Restaurant Pvt Ltd") },
            supportingText = { Text("The exact name linked to your bank account") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = {
                onSetupComplete(
                    BusinessInfo(
                        name = name.trim(),
                        address = address.trim(),
                        upiId = upiId.trim(),
                        payeeName = payeeName.trim()
                    )
                )
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            enabled = name.isNotBlank() && upiId.isNotBlank() && payeeName.isNotBlank()
        ) {
            Text(if (initialInfo == null) "Complete Setup" else "Save Settings")
        }
        
        if (initialInfo != null) {
            Spacer(modifier = Modifier.height(24.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "UPI Auto-Detection (YouPI Gateway)",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "Auto-detects customer payments from PhonePe, Google Pay, Paytm, and Bank SMS, instantly displaying 'Payment Recived Succeess'.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // 1. Notification Access Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Notification Listener Access",
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "PhonePe, GPay, Paytm, BHIM alerts",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (hasNotificationAccess) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .background(Color(0xFFE8F5E9), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(16.dp))
                                Text("Enabled", color = Color(0xFF1B5E20), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        } else {
                            OutlinedButton(
                                onClick = { YouPiPermissionHelper.openNotificationListenerSettings(context) },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Enable", fontSize = 12.sp)
                            }
                        }
                    }

                    // 2. SMS Reading Permission Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Bank SMS Detection Permission",
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Credit SMS from SBI, HDFC, ICICI, Axis, etc.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (hasSmsPermission) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .background(Color(0xFFE8F5E9), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(16.dp))
                                Text("Granted", color = Color(0xFF1B5E20), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        } else {
                            OutlinedButton(
                                onClick = { smsPermissionLauncher.launch(YouPiPermissionHelper.REQUIRED_SMS_PERMISSIONS) },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.Sms, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Allow", fontSize = 12.sp)
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)

                    // 3. Auto-Dismiss PhonePe/UPI Notifications Switch
                    var autoDismissNotif by remember { mutableStateOf(YouPiPaymentManager.autoDismissNotifications) }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Auto-Dismiss PhonePe Notifications",
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Clears status bar alerts once payment is detected",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = autoDismissNotif,
                            onCheckedChange = {
                                autoDismissNotif = it
                                YouPiPaymentManager.autoDismissNotifications = it
                            }
                        )
                    }

                    // 4. Auto-Close Modal on Success Switch
                    var autoCloseDialog by remember { mutableStateOf(YouPiPaymentManager.autoCloseDialogOnSuccess) }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Auto-Close Modal on Payment",
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Auto-saves bill & closes QR 3s after payment",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = autoCloseDialog,
                            onCheckedChange = {
                                autoCloseDialog = it
                                YouPiPaymentManager.autoCloseDialogOnSuccess = it
                            }
                        )
                    }
                }
            }
        }
        
        if (initialInfo != null && onResetApp != null) {
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(
                onClick = onResetApp,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Reset Account & Erase All Data")
            }
        }
    }
}
