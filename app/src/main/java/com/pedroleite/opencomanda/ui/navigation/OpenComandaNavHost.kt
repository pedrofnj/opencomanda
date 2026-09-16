package com.pedroleite.opencomanda.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pedroleite.opencomanda.R
import com.pedroleite.opencomanda.ui.components.PlaceholderScreen
import com.pedroleite.opencomanda.ui.customers.CustomerFormScreen
import com.pedroleite.opencomanda.ui.customers.CustomerListScreen
import com.pedroleite.opencomanda.ui.home.HomeScreen
import com.pedroleite.opencomanda.ui.products.CategoryManagementScreen
import com.pedroleite.opencomanda.ui.products.ProductFormScreen
import com.pedroleite.opencomanda.ui.products.ProductListScreen

/**
 * OpenComanda's top-level navigation graph. Every non-Home destination is wired to a
 * [PlaceholderScreen] for now; a future increment swaps each placeholder for the real screen
 * without touching this graph's structure.
 */
@Composable
fun OpenComandaNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Destination.Home.route) {
        composable(Destination.Home.route) {
            HomeScreen(onNavigate = { destination -> navController.navigate(destination.route) })
        }
        composable(Destination.QuickSale.route) {
            PlaceholderScreen(
                title = stringResource(R.string.action_quick_sale),
                onBack = { navController.popBackStack() },
            )
        }
        composable(Destination.NewComanda.route) {
            PlaceholderScreen(
                title = stringResource(R.string.action_new_comanda),
                onBack = { navController.popBackStack() },
            )
        }
        composable(Destination.OpenComandas.route) {
            PlaceholderScreen(
                title = stringResource(R.string.action_open_comandas),
                onBack = { navController.popBackStack() },
            )
        }
        composable(Destination.Products.route) {
            ProductListScreen(
                onBack = { navController.popBackStack() },
                onCreateProduct = { navController.navigate(Destination.ProductForm.createRoute()) },
                onEditProduct = { productId ->
                    navController.navigate(Destination.ProductForm.createRoute(productId))
                },
                onManageCategories = { navController.navigate(Destination.CategoryManagement.route) },
            )
        }
        composable(Destination.CategoryManagement.route) {
            CategoryManagementScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = Destination.ProductForm.route,
            arguments = listOf(
                navArgument(Destination.ProductForm.ARG_PRODUCT_ID) {
                    type = NavType.LongType
                    defaultValue = Destination.ProductForm.NO_PRODUCT_ID
                },
            ),
        ) { backStackEntry ->
            val productId = backStackEntry.arguments
                ?.getLong(Destination.ProductForm.ARG_PRODUCT_ID)
                ?.takeIf { it != Destination.ProductForm.NO_PRODUCT_ID }
            ProductFormScreen(
                productId = productId,
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
            )
        }
        composable(Destination.Customers.route) {
            CustomerListScreen(
                onBack = { navController.popBackStack() },
                onCreateCustomer = { navController.navigate(Destination.CustomerForm.createRoute()) },
                onEditCustomer = { customerId ->
                    navController.navigate(Destination.CustomerForm.createRoute(customerId))
                },
            )
        }
        composable(
            route = Destination.CustomerForm.route,
            arguments = listOf(
                navArgument(Destination.CustomerForm.ARG_CUSTOMER_ID) {
                    type = NavType.LongType
                    defaultValue = Destination.CustomerForm.NO_CUSTOMER_ID
                },
            ),
        ) { backStackEntry ->
            val customerId = backStackEntry.arguments
                ?.getLong(Destination.CustomerForm.ARG_CUSTOMER_ID)
                ?.takeIf { it != Destination.CustomerForm.NO_CUSTOMER_ID }
            CustomerFormScreen(
                customerId = customerId,
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
            )
        }
        composable(Destination.Fiado.route) {
            PlaceholderScreen(
                title = stringResource(R.string.action_fiado),
                onBack = { navController.popBackStack() },
            )
        }
        composable(Destination.CashRegister.route) {
            PlaceholderScreen(
                title = stringResource(R.string.action_cash_register),
                onBack = { navController.popBackStack() },
            )
        }
    }
}
