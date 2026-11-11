package com.example.youpi

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import android.service.notification.NotificationListenerService

/**
 * Utility to check and manage Android permissions for YouPI UPI payment detection:
 * 1. Notification Listener Access (for PhonePe, Google Pay, Paytm, BHIM, Bank notifications)
 * 2. SMS Receive Permission (for Bank UPI credit SMS messages)
 */
object YouPiPermissionHelper {

    val REQUIRED_SMS_PERMISSIONS = arrayOf(
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.READ_SMS
    )

    /**
     * Checks if the user has enabled Notification Listener Access for this app in Android Settings.
     */
    fun isNotificationAccessGranted(context: Context): Boolean {
        try {
            val enabledPackages = NotificationManagerCompat.getEnabledListenerPackages(context)
            if (enabledPackages.contains(context.packageName)) {
                return true
            }

            // Fallback check via secure settings
            val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            if (flat != null) {
                val serviceComponent = ComponentName(context, YouPiNotificationListenerService::class.java).flattenToString()
                if (flat.contains(serviceComponent) || flat.contains(context.packageName)) {
                    return true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    /**
     * Checks if the app has runtime permission to receive/read incoming SMS messages.
     */
    fun isSmsPermissionGranted(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECEIVE_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Opens the Android Notification Listener Settings page where the merchant can enable access for this POS app.
     */
    fun openNotificationListenerSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val fallbackIntent = Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
    }

    /**
     * Rebinds the NotificationListenerService if it became disconnected or unbound by Android.
     */
    fun ensureServiceConnected(context: Context) {
        if (isNotificationAccessGranted(context)) {
            try {
                val component = ComponentName(context, YouPiNotificationListenerService::class.java)
                NotificationListenerService.requestRebind(component)
            } catch (_: Throwable) {}
        }
    }

    /**
     * Opens the app's system settings page in case permissions were permanently denied.
     */
    fun openAppSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
