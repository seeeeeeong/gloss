package fixtures.layers.domain.shop.presentation

import fixtures.layers.domain.shop.infrastructure.JdbcOrders

/** Skips the service layer to read storage. */
class OrderView(
    val jdbcOrders: JdbcOrders,
)
