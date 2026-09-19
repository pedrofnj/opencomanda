package com.pedroleite.opencomanda.domain

/** Whether a cash-register session is currently taking payments or has been closed out. */
enum class CashSessionStatus {
    OPEN,
    CLOSED,
}
