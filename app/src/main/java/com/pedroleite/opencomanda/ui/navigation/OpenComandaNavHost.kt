package com.pedroleite.opencomanda.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pedroleite.opencomanda.domain.auth.Permission
import com.pedroleite.opencomanda.ui.admin.AccountScreen
import com.pedroleite.opencomanda.ui.admin.AuditLogScreen
import com.pedroleite.opencomanda.ui.admin.RoleFormScreen
import com.pedroleite.opencomanda.ui.admin.RolesScreen
import com.pedroleite.opencomanda.ui.admin.SettingsScreen
import com.pedroleite.opencomanda.ui.admin.UserFormScreen
import com.pedroleite.opencomanda.ui.admin.UsersScreen
import com.pedroleite.opencomanda.ui.auth.RequireAnyPermission
import com.pedroleite.opencomanda.ui.auth.RequirePermission
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

/**
 * OpenComanda's top-level navigation graph. Every destination that needs a permission is wrapped in
 * [RequirePermission], so even a stale or crafted route never shows a screen the signed-in user may
 * not use (the repositories refuse the writes as well). [onLock] and [onSignOut] come from the
 * session owner — see [com.pedroleite.opencomanda.ui.auth.AuthGate].
 */
@Composable
fun OpenComandaNavHost(
    navController: NavHostController = rememberNavController(),
    onLock: () -> Unit = {},
    onSignOut: () -> Unit = {},
) {
    val back: () -> Unit = { navController.popBackStack() }
    NavHost(navController = navController, startDestination = Destination.Home.route) {
        composable(Destination.Home.route) {
            HomeScreen(
                onNavigate = { destination -> navController.navigate(destination.route) },
                onOpenComanda = { comandaId -> navController.navigate(Destination.ComandaDetail.createRoute(comandaId)) },
                onLock = onLock,
                onSignOut = onSignOut,
            )
        }
        composable(Destination.QuickSale.route) {
            RequirePermission(Permission.SALES_CREATE, onBack = back) {
                QuickSaleScreen(
                    onBack = { navController.popBackStack() },
                    onGoToProducts = { navController.navigate(Destination.Products.route) },
                )
            }
        }
        composable(Destination.NewComanda.route) {
            RequirePermission(Permission.COMANDA_MANAGE, onBack = back) {
                NewComandaScreen(
                    onBack = { navController.popBackStack() },
                    onCreated = { comandaId ->
                        navController.navigate(Destination.ComandaDetail.createRoute(comandaId)) {
                            popUpTo(Destination.NewComanda.route) { inclusive = true }
                        }
                    },
                )
            }
        }
        composable(Destination.OpenComandas.route) {
            RequirePermission(Permission.COMANDA_MANAGE, onBack = back) {
                OpenComandasScreen(
                    onBack = { navController.popBackStack() },
                    onCreateComanda = { navController.navigate(Destination.NewComanda.route) },
                    onOpenComanda = { comandaId -> navController.navigate(Destination.ComandaDetail.createRoute(comandaId)) },
                )
            }
        }
        composable(
            route = Destination.ComandaDetail.route,
            arguments = listOf(navArgument(Destination.ComandaDetail.ARG_COMANDA_ID) { type = NavType.LongType }),
        ) { backStackEntry ->
            val comandaId = backStackEntry.arguments?.getLong(Destination.ComandaDetail.ARG_COMANDA_ID)
            if (comandaId != null) {
                RequirePermission(Permission.COMANDA_MANAGE, onBack = back) {
                    ComandaDetailScreen(
                        comandaId = comandaId,
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }
        composable(Destination.Products.route) {
            RequirePermission(Permission.PRODUCTS_MANAGE, onBack = back) {
                ProductListScreen(
                    onBack = { navController.popBackStack() },
                    onCreateProduct = { navController.navigate(Destination.ProductForm.createRoute()) },
                    onEditProduct = { productId ->
                        navController.navigate(Destination.ProductForm.createRoute(productId))
                    },
                    onManageCategories = { navController.navigate(Destination.CategoryManagement.route) },
                )
            }
        }
        composable(Destination.CategoryManagement.route) {
            RequirePermission(Permission.PRODUCTS_MANAGE, onBack = back) {
                CategoryManagementScreen(onBack = { navController.popBackStack() })
            }
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
            RequirePermission(Permission.PRODUCTS_MANAGE, onBack = back) {
                ProductFormScreen(
                    productId = productId,
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() },
                )
            }
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
            RequirePermission(Permission.FIADO_VIEW, onBack = back) {
                FiadoScreen(
                    onBack = { navController.popBackStack() },
                    onCustomerSelected = { customerId -> navController.navigate(Destination.CustomerFiado.createRoute(customerId)) },
                )
            }
        }
        composable(
            route = Destination.CustomerFiado.route,
            arguments = listOf(navArgument(Destination.CustomerFiado.ARG_CUSTOMER_ID) { type = NavType.LongType }),
        ) { backStackEntry ->
            val customerId = backStackEntry.arguments?.getLong(Destination.CustomerFiado.ARG_CUSTOMER_ID)
            if (customerId != null) {
                RequirePermission(Permission.FIADO_VIEW, onBack = back) {
                    CustomerFiadoScreen(
                        customerId = customerId,
                        onBack = { navController.popBackStack() },
                        onDebtSelected = { debtId -> navController.navigate(Destination.DebtDetail.createRoute(debtId)) },
                    )
                }
            }
        }
        composable(
            route = Destination.DebtDetail.route,
            arguments = listOf(navArgument(Destination.DebtDetail.ARG_DEBT_ID) { type = NavType.LongType }),
        ) { backStackEntry ->
            val debtId = backStackEntry.arguments?.getLong(Destination.DebtDetail.ARG_DEBT_ID)
            if (debtId != null) {
                RequirePermission(Permission.FIADO_VIEW, onBack = back) {
                    DebtDetailScreen(
                        debtId = debtId,
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }
        composable(Destination.Stock.route) {
            RequirePermission(Permission.STOCK_VIEW, onBack = back) {
                StockScreen(
                    onBack = { navController.popBackStack() },
                    onGoToProducts = { navController.navigate(Destination.Products.route) },
                )
            }
        }
        composable(Destination.CashRegister.route) {
            RequireAnyPermission(setOf(Permission.CASH_OPEN, Permission.CASH_CLOSE, Permission.REPORTS_VIEW), onBack = back) {
                CashRegisterScreen(onBack = { navController.popBackStack() })
            }
        }
        composable(Destination.Account.route) {
            AccountScreen(onBack = back)
        }
        composable(Destination.Users.route) {
            RequirePermission(Permission.USERS_MANAGE, onBack = back) {
                UsersScreen(
                    onBack = back,
                    onCreate = { navController.navigate(Destination.UserForm.createRoute()) },
                    onEdit = { userId -> navController.navigate(Destination.UserForm.createRoute(userId)) },
                )
            }
        }
        composable(
            route = Destination.UserForm.route,
            arguments = listOf(
                navArgument(Destination.UserForm.ARG_USER_ID) {
                    type = NavType.LongType
                    defaultValue = Destination.UserForm.NO_USER_ID
                },
            ),
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getLong(Destination.UserForm.ARG_USER_ID)
                ?.takeIf { it != Destination.UserForm.NO_USER_ID }
            RequirePermission(Permission.USERS_MANAGE, onBack = back) {
                UserFormScreen(userId = userId, onBack = back, onSaved = back)
            }
        }
        composable(Destination.Roles.route) {
            RequirePermission(Permission.USERS_MANAGE, onBack = back) {
                RolesScreen(
                    onBack = back,
                    onCreate = { navController.navigate(Destination.RoleForm.createRoute()) },
                    onOpen = { roleId -> navController.navigate(Destination.RoleForm.createRoute(roleId)) },
                )
            }
        }
        composable(
            route = Destination.RoleForm.route,
            arguments = listOf(
                navArgument(Destination.RoleForm.ARG_ROLE_ID) {
                    type = NavType.LongType
                    defaultValue = Destination.RoleForm.NO_ROLE_ID
                },
            ),
        ) { backStackEntry ->
            val roleId = backStackEntry.arguments?.getLong(Destination.RoleForm.ARG_ROLE_ID)
                ?.takeIf { it != Destination.RoleForm.NO_ROLE_ID }
            RequirePermission(Permission.USERS_MANAGE, onBack = back) {
                RoleFormScreen(roleId = roleId, onBack = back, onDone = back)
            }
        }
        composable(Destination.Settings.route) {
            RequirePermission(Permission.SETTINGS_MANAGE, onBack = back) {
                SettingsScreen(onBack = back)
            }
        }
        composable(Destination.AuditLog.route) {
            RequirePermission(Permission.USERS_MANAGE, onBack = back) {
                AuditLogScreen(onBack = back)
            }
        }
    }
}
