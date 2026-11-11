#!/bin/bash
sed -i '/horizontalArrangement = Arrangement.End/c\
                    horizontalArrangement = Arrangement.spacedBy(12.dp)\
                ) {\
                    OutlinedButton(\
                        onClick = {\
                            val currentBill = Bill(\
                                customerPhone = phone,\
                                items = orderItems,\
                                subTotal = subTotal,\
                                gst = gst,\
                                total = total\
                            )\
                            onCloseBill(currentBill)\
                        },\
                        modifier = Modifier\
                            .weight(1f)\
                            .height(48.dp),\
                        shape = CircleShape\
                    ) {\
                        Text("Close Bill")\
                    }' app/src/main/java/com/example/ui/BillScreen.kt
