package com.example.data

import android.content.Context
import android.util.Log
import com.example.models.BusinessInfo
import com.example.models.MenuItem
import com.example.models.Bill
import com.example.models.OrderItem
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

class FirestoreRepository(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("pos_app_local_data", Context.MODE_PRIVATE)

    private val db: FirebaseFirestore? by lazy {
        try {
            FirebaseFirestore.getInstance()
        } catch (e: Exception) {
            Log.w("FirestoreRepository", "Firestore not available: ${e.message}")
            null
        }
    }

    private val auth: FirebaseAuth? by lazy {
        try {
            FirebaseAuth.getInstance()
        } catch (e: Exception) {
            Log.w("FirestoreRepository", "FirebaseAuth not available: ${e.message}")
            null
        }
    }

    private val currentUserId: String?
        get() = auth?.currentUser?.uid

    suspend fun getBusinessInfo(): BusinessInfo? {
        // 1. Check local cache first
        val localName = prefs.getString("biz_name", null)
        val localAddress = prefs.getString("biz_address", "") ?: ""
        val localUpi = prefs.getString("biz_upi", "") ?: ""
        val localPayee = prefs.getString("biz_payee", "") ?: ""

        val localInfo = if (!localName.isNullOrBlank()) {
            BusinessInfo(localName, localAddress, localUpi, localPayee)
        } else {
            null
        }

        // 2. Fetch from Firestore if user logged in
        val uid = currentUserId
        if (uid != null && db != null) {
            try {
                val doc = db?.collection("users")?.document(uid)?.get()?.await()
                if (doc != null && doc.exists()) {
                    val remoteInfo = doc.toObject(BusinessInfo::class.java)
                    if (remoteInfo != null && remoteInfo.name.isNotBlank()) {
                        saveBusinessInfoLocal(remoteInfo)
                        return remoteInfo
                    }
                }
            } catch (e: Exception) {
                Log.w("FirestoreRepository", "Error reading business info from Firestore: ${e.message}")
            }
        }

        return localInfo
    }

    suspend fun saveBusinessInfo(info: BusinessInfo) {
        // 1. Always save locally so data is preserved and app never crashes
        saveBusinessInfoLocal(info)

        // 2. Safely sync to Firestore
        val uid = currentUserId
        if (uid != null && db != null) {
            try {
                db?.collection("users")?.document(uid)?.set(info)?.await()
            } catch (e: Exception) {
                Log.w("FirestoreRepository", "Firestore saveBusinessInfo sync failed: ${e.message}")
            }
        }
    }

    private fun saveBusinessInfoLocal(info: BusinessInfo) {
        prefs.edit()
            .putString("biz_name", info.name)
            .putString("biz_address", info.address)
            .putString("biz_upi", info.upiId)
            .putString("biz_payee", info.payeeName)
            .apply()
    }

    suspend fun getMenuItems(): List<MenuItem> {
        val localItems = getLocalMenuItems()

        val uid = currentUserId
        if (uid != null && db != null) {
            try {
                val snapshot = db?.collection("users")?.document(uid)?.collection("menu")?.get()?.await()
                if (snapshot != null && !snapshot.isEmpty) {
                    val remoteItems = snapshot.toObjects(MenuItem::class.java)
                    if (remoteItems.isNotEmpty()) {
                        saveLocalMenuItems(remoteItems)
                        return remoteItems
                    }
                }
            } catch (e: Exception) {
                Log.w("FirestoreRepository", "Firestore getMenuItems sync failed: ${e.message}")
            }
        }

        if (localItems.isEmpty()) {
            val defaults = getDefaultMenuItems()
            saveLocalMenuItems(defaults)
            if (uid != null && db != null) {
                for (item in defaults) {
                    try {
                        db?.collection("users")?.document(uid)?.collection("menu")?.document(item.id)?.set(item)
                    } catch (e: Exception) {
                        // ignore background sync errors
                    }
                }
            }
            return defaults
        }

        return localItems
    }

    suspend fun saveMenuItem(item: MenuItem) {
        val id = if (item.id.isBlank()) java.util.UUID.randomUUID().toString() else item.id
        val finalItem = item.copy(id = id)

        val current = getLocalMenuItems().toMutableList()
        val index = current.indexOfFirst { it.id == id }
        if (index >= 0) {
            current[index] = finalItem
        } else {
            current.add(finalItem)
        }
        saveLocalMenuItems(current)

        val uid = currentUserId
        if (uid != null && db != null) {
            try {
                db?.collection("users")?.document(uid)?.collection("menu")?.document(id)?.set(finalItem)?.await()
            } catch (e: Exception) {
                Log.w("FirestoreRepository", "Firestore saveMenuItem sync failed: ${e.message}")
            }
        }
    }

    suspend fun deleteMenuItem(itemId: String) {
        val current = getLocalMenuItems().filterNot { it.id == itemId }
        saveLocalMenuItems(current)

        val uid = currentUserId
        if (uid != null && db != null) {
            try {
                db?.collection("users")?.document(uid)?.collection("menu")?.document(itemId)?.delete()?.await()
            } catch (e: Exception) {
                Log.w("FirestoreRepository", "Firestore deleteMenuItem sync failed: ${e.message}")
            }
        }
    }

    suspend fun saveBill(bill: Bill) {
        val id = if (bill.id.isBlank()) "BILL_${System.currentTimeMillis()}" else bill.id
        val finalBill = bill.copy(id = id)

        val current = getLocalBills().toMutableList()
        current.add(0, finalBill)
        saveLocalBills(current)

        val uid = currentUserId
        if (uid != null && db != null) {
            try {
                db?.collection("users")?.document(uid)?.collection("bills")?.document(id)?.set(finalBill)?.await()
            } catch (e: Exception) {
                Log.w("FirestoreRepository", "Firestore saveBill sync failed: ${e.message}")
            }
        }
    }

    suspend fun getBills(): List<Bill> {
        val localBills = getLocalBills()

        val uid = currentUserId
        if (uid != null && db != null) {
            try {
                val snapshot = db?.collection("users")?.document(uid)?.collection("bills")
                    ?.orderBy("timestamp", Query.Direction.DESCENDING)
                    ?.get()?.await()
                if (snapshot != null && !snapshot.isEmpty) {
                    val remoteBills = snapshot.toObjects(Bill::class.java)
                    if (remoteBills.isNotEmpty()) {
                        saveLocalBills(remoteBills)
                        return remoteBills
                    }
                }
            } catch (e: Exception) {
                Log.w("FirestoreRepository", "Firestore getBills sync failed: ${e.message}")
            }
        }

        return localBills
    }

    suspend fun deleteTodaysBills() {
        val todayStart = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis

        val remaining = getLocalBills().filter { it.timestamp < todayStart }
        saveLocalBills(remaining)

        val uid = currentUserId
        if (uid != null && db != null) {
            try {
                val snapshot = db?.collection("users")?.document(uid)?.collection("bills")
                    ?.whereGreaterThanOrEqualTo("timestamp", todayStart)
                    ?.get()
                    ?.await()
                if (snapshot != null) {
                    for (doc in snapshot.documents) {
                        doc.reference.delete().await()
                    }
                }
            } catch (e: Exception) {
                Log.w("FirestoreRepository", "Firestore deleteTodaysBills sync failed: ${e.message}")
            }
        }
    }

    suspend fun wipeUserData() {
        prefs.edit().clear().apply()

        val uid = currentUserId
        if (uid != null && db != null) {
            try {
                val menuDocs = db?.collection("users")?.document(uid)?.collection("menu")?.get()?.await()
                if (menuDocs != null) {
                    for (doc in menuDocs.documents) doc.reference.delete().await()
                }
                val billDocs = db?.collection("users")?.document(uid)?.collection("bills")?.get()?.await()
                if (billDocs != null) {
                    for (doc in billDocs.documents) doc.reference.delete().await()
                }
                db?.collection("users")?.document(uid)?.delete()?.await()
            } catch (e: Exception) {
                Log.w("FirestoreRepository", "Firestore wipeUserData sync failed: ${e.message}")
            }
        }
    }

    private fun getLocalMenuItems(): List<MenuItem> {
        val raw = prefs.getString("menu_items_json", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<MenuItem>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i)
                if (obj != null) {
                    list.add(
                        MenuItem(
                            id = obj.optString("id", ""),
                            category = obj.optString("category", ""),
                            name = obj.optString("name", ""),
                            price = obj.optDouble("price", 0.0),
                            taxType = obj.optString("taxType", MenuItem.TAX_DEFAULT),
                            customTaxRate = obj.optDouble("customTaxRate", 5.0),
                            imageUrl = obj.optString("imageUrl", "")
                        )
                    )
                }
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveLocalMenuItems(items: List<MenuItem>) {
        val arr = JSONArray()
        for (item in items) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("category", item.category)
                put("name", item.name)
                put("price", item.price)
                put("taxType", item.taxType)
                put("customTaxRate", item.customTaxRate)
                put("imageUrl", item.imageUrl)
            }
            arr.put(obj)
        }
        prefs.edit().putString("menu_items_json", arr.toString()).apply()
    }

    private fun getLocalBills(): List<Bill> {
        val raw = prefs.getString("bills_json", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<Bill>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i)
                if (obj != null) {
                    val itemsList = mutableListOf<OrderItem>()
                    val itemsArr = obj.optJSONArray("items")
                    if (itemsArr != null) {
                        for (j in 0 until itemsArr.length()) {
                            val oiObj = itemsArr.optJSONObject(j)
                            if (oiObj != null) {
                                val itemObj = oiObj.optJSONObject("item")
                                val menuItem = if (itemObj != null) {
                                    MenuItem(
                                        id = itemObj.optString("id", ""),
                                        category = itemObj.optString("category", ""),
                                        name = itemObj.optString("name", ""),
                                        price = itemObj.optDouble("price", 0.0),
                                        taxType = itemObj.optString("taxType", MenuItem.TAX_DEFAULT),
                                        customTaxRate = itemObj.optDouble("customTaxRate", 5.0),
                                        imageUrl = itemObj.optString("imageUrl", "")
                                    )
                                } else {
                                    MenuItem()
                                }
                                val qty = oiObj.optInt("quantity", 1)
                                itemsList.add(OrderItem(menuItem, qty))
                            }
                        }
                    }
                    list.add(
                        Bill(
                            id = obj.optString("id", ""),
                            timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                            customerPhone = obj.optString("customerPhone", ""),
                            items = itemsList,
                            subTotal = obj.optDouble("subTotal", 0.0),
                            gst = obj.optDouble("gst", 0.0),
                            total = obj.optDouble("total", 0.0),
                            paymentStatus = obj.optString("paymentStatus", "PAID"),
                            utrNumber = obj.optString("utrNumber", "")
                        )
                    )
                }
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveLocalBills(bills: List<Bill>) {
        val arr = JSONArray()
        for (bill in bills) {
            val itemsArr = JSONArray()
            for (oi in bill.items) {
                val itemObj = JSONObject().apply {
                    put("id", oi.item.id)
                    put("category", oi.item.category)
                    put("name", oi.item.name)
                    put("price", oi.item.price)
                    put("taxType", oi.item.taxType)
                    put("customTaxRate", oi.item.customTaxRate)
                    put("imageUrl", oi.item.imageUrl)
                }
                val oiObj = JSONObject().apply {
                    put("item", itemObj)
                    put("quantity", oi.quantity)
                }
                itemsArr.put(oiObj)
            }
            val obj = JSONObject().apply {
                put("id", bill.id)
                put("timestamp", bill.timestamp)
                put("customerPhone", bill.customerPhone)
                put("items", itemsArr)
                put("subTotal", bill.subTotal)
                put("gst", bill.gst)
                put("total", bill.total)
                put("paymentStatus", bill.paymentStatus)
                put("utrNumber", bill.utrNumber)
            }
            arr.put(obj)
        }
        prefs.edit().putString("bills_json", arr.toString()).apply()
    }

    private fun getDefaultMenuItems(): List<MenuItem> = listOf(
        MenuItem(id = "m1", category = "Beverages", name = "Masala Chai", price = 20.0, taxType = MenuItem.TAX_DEFAULT, customTaxRate = 5.0),
        MenuItem(id = "m2", category = "Beverages", name = "Filter Coffee", price = 30.0, taxType = MenuItem.TAX_DEFAULT, customTaxRate = 5.0),
        MenuItem(id = "m3", category = "Beverages", name = "Cold Coffee", price = 60.0, taxType = MenuItem.TAX_DEFAULT, customTaxRate = 5.0),
        MenuItem(id = "m4", category = "Snacks", name = "Veg Grilled Sandwich", price = 70.0, taxType = MenuItem.TAX_DEFAULT, customTaxRate = 5.0),
        MenuItem(id = "m5", category = "Snacks", name = "Cheese Corn Sandwich", price = 90.0, taxType = MenuItem.TAX_CUSTOM, customTaxRate = 12.0),
        MenuItem(id = "m6", category = "Snacks", name = "Veg Burger", price = 80.0, taxType = MenuItem.TAX_DEFAULT, customTaxRate = 5.0),
        MenuItem(id = "m7", category = "Snacks", name = "French Fries", price = 70.0, taxType = MenuItem.TAX_DEFAULT, customTaxRate = 5.0),
        MenuItem(id = "m8", category = "Quick Bites", name = "Samosa (2 pcs)", price = 30.0, taxType = MenuItem.TAX_NO_TAX, customTaxRate = 0.0),
        MenuItem(id = "m9", category = "Quick Bites", name = "Paneer Roll", price = 90.0, taxType = MenuItem.TAX_DEFAULT, customTaxRate = 5.0)
    )
}
