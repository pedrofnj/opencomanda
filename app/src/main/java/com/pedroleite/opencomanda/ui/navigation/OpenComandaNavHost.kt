package com.pedroleite.opencomanda.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pedroleite.opencomanda.R
import com.pedroleite.opencomanda.ui.components.PlaceholderScreen
import com.pedroleite.opencomanda.ui.home.HomeScreen

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
            PlaceholderScreen(
                title = stringResource(R.string.action_products),
                onBack = { navController.popBackStack() },
            )
        }
        composable(Destination.Customers.route) {
            PlaceholderScreen(
                title = stringResource(R.string.action_customers),
                onBack = { navController.popBackStack() },
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
