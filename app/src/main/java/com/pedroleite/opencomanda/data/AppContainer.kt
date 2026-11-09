package com.pedroleite.opencomanda.data

import android.content.Context
import com.pedroleite.opencomanda.data.auth.AccessControl
import com.pedroleite.opencomanda.data.auth.SessionAccessControl
import com.pedroleite.opencomanda.data.auth.SessionManager
import com.pedroleite.opencomanda.data.local.AppDatabase
import com.pedroleite.opencomanda.data.repository.AuthRepository
import com.pedroleite.opencomanda.data.repository.CashRegisterRepository
import com.pedroleite.opencomanda.data.repository.CategoryRepository
import com.pedroleite.opencomanda.data.repository.CustomerRepository
import com.pedroleite.opencomanda.data.repository.DebtRepository
import com.pedroleite.opencomanda.data.repository.OrderRepository
import com.pedroleite.opencomanda.data.repository.ProductRepository
import com.pedroleite.opencomanda.data.repository.UserManagementRepository

/**
 * Minimal manual dependency container. OpenComanda intentionally avoids a DI framework
 * (Hilt/Koin) at this stage of the project: the dependency graph is small (one database, a handful
 * of repositories), and a manual container keeps it simple and dependency-free until that stops
 * being true.
 *
 * Every repository that writes business data gets [accessControl], so the signed-in user's
 * permissions are checked where the data changes, not only where buttons are drawn.
 */
class AppContainer(context: Context) {
    private val database: AppDatabase = AppDatabase.build(context)

    val session: SessionManager = SessionManager()

    private val accessControl: AccessControl = SessionAccessControl(session, database.auditLogDao())

    val authRepository: AuthRepository by lazy {
        AuthRepository(
            database = database,
            userDao = database.userDao(),
            roleDao = database.roleDao(),
            settingsDao = database.securitySettingsDao(),
            auditLogDao = database.auditLogDao(),
            session = session,
        )
    }

    val userManagementRepository: UserManagementRepository by lazy {
        UserManagementRepository(
            database = database,
            userDao = database.userDao(),
            roleDao = database.roleDao(),
            auditLogDao = database.auditLogDao(),
            session = session,
            access = accessControl,
        )
    }

    val productRepository: ProductRepository by lazy { ProductRepository(database, database.productDao(), accessControl) }

    val categoryRepository: CategoryRepository by lazy { CategoryRepository(database.categoryDao(), accessControl) }

    val customerRepository: CustomerRepository by lazy { CustomerRepository(database.customerDao()) }

    val orderRepository: OrderRepository by lazy {
        OrderRepository(
            database = database,
            orderDao = database.orderDao(),
            orderItemDao = database.orderItemDao(),
            paymentDao = database.paymentDao(),
            debtDao = database.debtDao(),
            productDao = database.productDao(),
            cashSessionDao = database.cashSessionDao(),
            accessControl = accessControl,
        )
    }

    val debtRepository: DebtRepository by lazy {
        DebtRepository(database, database.debtDao(), database.debtPaymentDao(), database.cashSessionDao(), accessControl)
    }

    val cashRegisterRepository: CashRegisterRepository by lazy {
        CashRegisterRepository(database, database.cashSessionDao(), database.paymentDao(), database.debtPaymentDao(), accessControl)
    }
}
