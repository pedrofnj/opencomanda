package com.pedroleite.opencomanda.data

import android.content.Context
import com.pedroleite.opencomanda.data.local.AppDatabase
import com.pedroleite.opencomanda.data.repository.CashRegisterRepository
import com.pedroleite.opencomanda.data.repository.CategoryRepository
import com.pedroleite.opencomanda.data.repository.CustomerRepository
import com.pedroleite.opencomanda.data.repository.DebtRepository
import com.pedroleite.opencomanda.data.repository.OrderRepository
import com.pedroleite.opencomanda.data.repository.ProductRepository

/**
 * Minimal manual dependency container. OpenComanda intentionally avoids a DI framework
 * (Hilt/Koin) at this stage of the project: the dependency graph is small (one database, five
 * repositories), and a manual container keeps it simple and dependency-free until that stops
 * being true.
 */
class AppContainer(context: Context) {
    private val database: AppDatabase = AppDatabase.build(context)

    val productRepository: ProductRepository by lazy { ProductRepository(database, database.productDao()) }

    val categoryRepository: CategoryRepository by lazy { CategoryRepository(database.categoryDao()) }

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
        )
    }

    val debtRepository: DebtRepository by lazy {
        DebtRepository(database, database.debtDao(), database.debtPaymentDao(), database.cashSessionDao())
    }

    val cashRegisterRepository: CashRegisterRepository by lazy {
        CashRegisterRepository(database, database.cashSessionDao(), database.paymentDao(), database.debtPaymentDao())
    }
}
