package com.sethv.fintrack.core.ui.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.LocalGroceryStore
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.ui.graphics.vector.ImageVector
import com.sethv.fintrack.core.model.ExpenseCategory

/**
 * Canonical icon mapping. Anywhere we render a category icon should call this.
 * Previously TransactionItem and ExpenseListScreen had two slightly different maps.
 */
fun categoryIcon(category: ExpenseCategory): ImageVector = when (category) {
    ExpenseCategory.FOOD -> Icons.Rounded.Restaurant
    ExpenseCategory.GROCERIES -> Icons.Rounded.LocalGroceryStore
    ExpenseCategory.SHOPPING -> Icons.Rounded.ShoppingBag
    ExpenseCategory.FUEL -> Icons.Rounded.LocalGasStation
    ExpenseCategory.TRANSPORT -> Icons.Rounded.DirectionsCar
    ExpenseCategory.BILLS -> Icons.Rounded.AccountBalance
    ExpenseCategory.ENTERTAINMENT -> Icons.Rounded.Movie
    ExpenseCategory.HEALTHCARE -> Icons.Rounded.LocalHospital
    ExpenseCategory.TRAVEL -> Icons.Rounded.Flight
    ExpenseCategory.RENT -> Icons.Rounded.Home
    ExpenseCategory.SUBSCRIPTION -> Icons.Rounded.Subscriptions
    ExpenseCategory.OTHERS -> Icons.Rounded.MoreHoriz
}