package com.pedroleite.opencomanda.domain

/** Settlement state of a Fiado debt, derived from its original amount minus payments so far. */
enum class DebtStatus {
    OPEN,
    PARTIALLY_PAID,
    PAID,
}
