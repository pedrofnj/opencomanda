package com.pedroleite.opencomanda.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pedroleite.opencomanda.ui.cashregister.CashRegisterScreen
import com.pedroleite.opencomanda.ui.comandas.ComandaDetailScreen
import com.pedroleite.opencomanda.ui.comandas.NewComandaScreen
import com.pedroleite.opencomanda.ui.comandas.OpenComandasScreen
import com.pedroleite.opencomanda.ui.customers.CustomerFormScreen
import com.pedroleite.opencomanda.ui.customers.CustomerListScreen
import com.pedroleite.opencomanda.ui.fiado.CustomerFiadoScreen
import com.pedroleite.opencomanda.ui.fiado.DebtDetailScreen
import com.pedroleite.opencomanda.ui.fiado.FiadoScreen
import com.pedroleite.opencomanda.ui.home.HomeScreen
import com.pedroleite.opencomanda.ui.products.CategoryManagementScreen
import com.pedroleite.opencomanda.ui.products.ProductFormScreen
import com.pedroleite.opencomanda.ui.products.ProductListScreen
import com.pedroleite.opencomanda.ui.quicksale.QuickSaleScreen
import com.pedroleite.opencomanda.ui.stock.StockScreen

/** OpenComanda's top-level navigation graph. */
@Composable
fun OpenComandaNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Destination.Home.route) {
        composable(Destination.Home.route) {
            HomeScreen(onNavigate = { destination -> navController.navigate(destination.route) })
        }
        composable(Destination.QuickSale.route) {
            QuickSaleScreen(
                onBack = { navController.popBackStack() },
                onGoToProducts = { navController.navigate(Destination.Products.route) },
            )
        }
        composable(Destination.NewComanda.route) {
            NewComandaScreen(
                onBack = { navController.popBackStack() },
                onCreated = { comandaId ->
                    navController.navigate(Destination.ComandaDetail.createRoute(comandaId)) {
                        popUpTo(Destination.NewComanda.route) { inclusive = true }
                    }
                },
            )
        }
        composable(Destination.OpenComandas.route) {
            OpenComandasScreen(
                onBack = { navController.popBackStack() },
                onCreateComanda = { navController.navigate(Destination.NewComanda.route) },
                onOpenComanda = { comandaId -> navController.navigate(Destination.ComandaDetail.createRoute(comandaId)) },
            )
        }
        composable(
            route = Destination.ComandaDetail.route,
            arguments = listOf(navArgument(Destination.ComandaDetail.ARG_COMANDA_ID) { type = NavType.LongType }),
        ) { backStackEntry ->
            val comandaId = backStackEntry.arguments?.getLong(Destination.ComandaDetail.ARG_COMANDA_ID)
            if (comandaId != null) {
                ComandaDetailScreen(
                    comandaId = comandaId,
                    onBack = { navController.popBackStack() },
                )
            }
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
            FiadoScreen(
                onBack = { navController.popBackStack() },
                onCustomerSelected = { customerId -> navController.navigate(Destination.CustomerFiado.createRoute(customerId)) },
            )
        }
        composable(
            route = Destination.CustomerFiado.route,
            arguments = listOf(navArgument(Destination.CustomerFiado.ARG_CUSTOMER_ID) { type = NavType.LongType }),
        ) { backStackEntry ->
            val customerId = backStackEntry.arguments?.getLong(Destination.CustomerFiado.ARG_CUSTOMER_ID)
            if (customerId != null) {
                CustomerFiadoScreen(
                    customerId = customerId,
                    onBack = { navController.popBackStack() },
                    onDebtSelected = { debtId -> navController.navigate(Destination.DebtDetail.createRoute(debtId)) },
                )
            }
        }
        composable(
            route = Destination.DebtDetail.route,
            arguments = listOf(navArgument(Destination.DebtDetail.ARG_DEBT_ID) { type = NavType.LongType }),
        ) { backStackEntry ->
            val debtId = backStackEntry.arguments?.getLong(Destination.DebtDetail.ARG_DEBT_ID)
            if (debtId != null) {
                DebtDetailScreen(
                    debtId = debtId,
                    onBack = { navController.popBackStack() },
                )
            }
        }
        composable(Destination.Stock.route) {
            StockScreen(
                onBack = { navController.popBackStack() },
                onGoToProducts = { navController.navigate(Destination.Products.route) },
            )
        }
        composable(Destination.CashRegister.route) {
            CashRegisterScreen(onBack = { navController.popBackStack() })
        }
    }
}
