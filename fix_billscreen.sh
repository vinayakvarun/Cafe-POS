#!/bin/bash
sed -i '/onCloseBill: (Bill) -> Unit,/d' app/src/main/java/com/example/ui/BillScreen.kt
sed -i 's/onBack: () -> Unit,/onBack: () -> Unit,\n    onCloseBill: (Bill) -> Unit,/g' app/src/main/java/com/example/ui/BillScreen.kt
