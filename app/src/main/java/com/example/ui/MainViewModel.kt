package com.example.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.FirestoreRepository
import com.example.models.BusinessInfo
import com.example.models.MenuItem
import com.example.models.Bill
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = FirestoreRepository(application)
    var auth: FirebaseAuth? = null

    private val _isUserLoggedIn = MutableStateFlow(false)
    val isUserLoggedIn: StateFlow<Boolean> = _isUserLoggedIn.asStateFlow()

    private val _businessInfo = MutableStateFlow<BusinessInfo?>(null)
    val businessInfo: StateFlow<BusinessInfo?> = _businessInfo.asStateFlow()

    private val _menuItems = MutableStateFlow<List<MenuItem>>(emptyList())
    val menuItems: StateFlow<List<MenuItem>> = _menuItems.asStateFlow()

    private val _bills = MutableStateFlow<List<Bill>>(emptyList())
    val bills: StateFlow<List<Bill>> = _bills.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _firebaseError = MutableStateFlow<String?>(null)
    val firebaseError: StateFlow<String?> = _firebaseError.asStateFlow()

    init {
        try {
            auth = FirebaseAuth.getInstance()
            _isUserLoggedIn.value = auth?.currentUser != null

            auth?.addAuthStateListener { firebaseAuth ->
                val loggedIn = firebaseAuth.currentUser != null
                _isUserLoggedIn.value = loggedIn
                if (loggedIn) {
                    loadInitialData()
                } else {
                    _businessInfo.value = null
                    _menuItems.value = emptyList()
                    _bills.value = emptyList()
                }
            }

            loadInitialData()
        } catch (e: Exception) {
            Log.e("MainViewModel", "Firebase initialization error", e)
            loadInitialData()
        }
    }

    fun loadInitialData() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _businessInfo.value = repository.getBusinessInfo()
                _menuItems.value = repository.getMenuItems()
                _bills.value = repository.getBills()
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error loading initial data", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun saveBusinessInfo(info: BusinessInfo) {
        viewModelScope.launch {
            try {
                repository.saveBusinessInfo(info)
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error saving business info", e)
            }
            _businessInfo.value = info
        }
    }

    fun addMenuItem(item: MenuItem) {
        viewModelScope.launch {
            try {
                repository.saveMenuItem(item)
                _menuItems.value = repository.getMenuItems()
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error adding menu item", e)
            }
        }
    }

    fun deleteMenuItem(itemId: String) {
        viewModelScope.launch {
            try {
                repository.deleteMenuItem(itemId)
                _menuItems.value = repository.getMenuItems()
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error deleting menu item", e)
            }
        }
    }
    
    fun saveBill(bill: Bill) {
        viewModelScope.launch {
            try {
                repository.saveBill(bill)
                _bills.value = repository.getBills()
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error saving bill", e)
            }
        }
    }
    
    fun clearTodaysBills() {
        viewModelScope.launch {
            try {
                repository.deleteTodaysBills()
                _bills.value = repository.getBills()
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error clearing bills", e)
            }
        }
    }
    
    fun resetApp() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.wipeUserData()
                _businessInfo.value = null
                _menuItems.value = emptyList()
                _bills.value = emptyList()
                signOut()
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error resetting app", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun signOut() {
        try {
            auth?.signOut()
        } catch (e: Exception) {
            Log.e("MainViewModel", "Error signing out", e)
        }
    }
}
