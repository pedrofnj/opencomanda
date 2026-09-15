package com.pedroleite.opencomanda.domain

/**
 * Lifecycle of an order. A [CLOSED] order cannot receive new items; reopening a closed
 * order is intentionally not supported yet (a future, explicitly designed action).
 */
enum class OrderStatus {
    OPEN,
    CLOSED,
    CANCELLED,
}
