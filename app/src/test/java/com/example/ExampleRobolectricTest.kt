package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.models.BusinessInfo
import com.example.models.MenuItem
import com.example.models.OrderItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("MyPOS", appName)
  }

  @Test
  fun `test setup business info saving and local retrieval`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repository = com.example.data.FirestoreRepository(context)
    val info = BusinessInfo(
      name = "Test Cafe",
      address = "123 Test St",
      upiId = "test@upi",
      payeeName = "Test Owner"
    )
    repository.saveBusinessInfo(info)
    val loaded = repository.getBusinessInfo()
    assertNotNull(loaded)
    assertEquals("Test Cafe", loaded?.name)
    assertEquals("123 Test St", loaded?.address)
    assertEquals("test@upi", loaded?.upiId)
    assertEquals("Test Owner", loaded?.payeeName)
  }

  @Test
  fun `test individual menu item tax rates and image saving`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repository = com.example.data.FirestoreRepository(context)

    val itemDefaultTax = MenuItem(
      id = "item_1",
      name = "Chai",
      category = "Beverages",
      price = 20.0,
      taxType = MenuItem.TAX_DEFAULT,
      imageUrl = "file:///data/chai.jpg"
    )
    val itemNoTax = MenuItem(
      id = "item_2",
      name = "Water",
      category = "Beverages",
      price = 10.0,
      taxType = MenuItem.TAX_NO_TAX,
      customTaxRate = 0.0
    )
    val itemCustomTax = MenuItem(
      id = "item_3",
      name = "Pastry",
      category = "Bakery",
      price = 100.0,
      taxType = MenuItem.TAX_CUSTOM,
      customTaxRate = 18.0,
      imageUrl = "https://example.com/pastry.jpg"
    )

    assertEquals(5.0, itemDefaultTax.effectiveTaxRate, 0.001)
    assertEquals(0.0, itemNoTax.effectiveTaxRate, 0.001)
    assertEquals(18.0, itemCustomTax.effectiveTaxRate, 0.001)

    // Save items
    repository.saveMenuItem(itemDefaultTax)
    repository.saveMenuItem(itemNoTax)
    repository.saveMenuItem(itemCustomTax)

    val loadedItems = repository.getMenuItems()
    val loadedDefault = loadedItems.find { it.id == "item_1" }
    val loadedNoTax = loadedItems.find { it.id == "item_2" }
    val loadedCustom = loadedItems.find { it.id == "item_3" }

    assertNotNull(loadedDefault)
    assertNotNull(loadedNoTax)
    assertNotNull(loadedCustom)

    assertEquals(MenuItem.TAX_DEFAULT, loadedDefault?.taxType)
    assertEquals("file:///data/chai.jpg", loadedDefault?.imageUrl)

    assertEquals(MenuItem.TAX_NO_TAX, loadedNoTax?.taxType)
    assertEquals(0.0, loadedNoTax?.effectiveTaxRate ?: -1.0, 0.001)

    assertEquals(MenuItem.TAX_CUSTOM, loadedCustom?.taxType)
    assertEquals(18.0, loadedCustom?.customTaxRate ?: -1.0, 0.001)
    assertEquals("https://example.com/pastry.jpg", loadedCustom?.imageUrl)

    // Test bill calculations with these items
    val order1 = OrderItem(item = itemDefaultTax, quantity = 2) // 20 * 2 = 40, tax = 2.0 (5%)
    val order2 = OrderItem(item = itemNoTax, quantity = 1)      // 10 * 1 = 10, tax = 0.0 (0%)
    val order3 = OrderItem(item = itemCustomTax, quantity = 1)  // 100 * 1 = 100, tax = 18.0 (18%)

    val subtotal = listOf(order1, order2, order3).sumOf { it.item.price * it.quantity }
    val totalTax = listOf(order1, order2, order3).sumOf { (it.item.price * it.quantity) * (it.item.effectiveTaxRate / 100.0) }
    val grandTotal = subtotal + totalTax

    assertEquals(150.0, subtotal, 0.001)
    assertEquals(20.0, totalTax, 0.001)
    assertEquals(170.0, grandTotal, 0.001)
  }
}
