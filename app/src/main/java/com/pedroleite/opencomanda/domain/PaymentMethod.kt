package com.pedroleite.opencomanda.domain

/**
 * How money was actually received for an order or a Fiado debt payment.
 *
 * Fiado is deliberately NOT a value here: closing an order as Fiado does not produce a
 * payment (no money changes hands at that moment) — it produces a debt instead. See
 * [com.pedroleite.opencomanda.data.local.entity.PaymentEntity] and
 * [com.pedroleite.opencomanda.data.repository.OrderRepository.closeOrderAsFiado].
 */
enum class PaymentMethod {
    CASH,
    DEBIT,
    CREDIT,
    PIX,
    OTHER,
}
