package com.pedroleite.opencomanda.ui.navigation

/**
 * OpenComanda's top-level destinations. Each carries its own route string so the whole app
 * has a single source of truth for route identifiers instead of repeating string literals.
 */
sealed class Destination(val route: String) {
    data object Home : Destination("home")
    data object QuickSale : Destination("quick_sale")
    data object NewComanda : Destination("new_comanda")
    data object OpenComandas : Destination("open_comandas")
    data object Products : Destination("products")
    data object Customers : Destination("customers")
    data object Fiado : Destination("fiado")
    data object CashRegister : Destination("cash_register")

    /** Create Product and Edit Product share one destination/screen; [ARG_PRODUCT_ID] is absent for create. */
    data object ProductForm : Destination("product_form?productId={productId}") {
        const val ARG_PRODUCT_ID = "productId"
        const val NO_PRODUCT_ID = -1L

        fun createRoute(productId: Long? = null): String =
            if (productId != null) "product_form?productId=$productId" else "product_form"
    }

    /** Reached from Products — categories are managed alongside products, not as their own
     *  top-level Home destination. */
    data object CategoryManagement : Destination("category_management")
}
