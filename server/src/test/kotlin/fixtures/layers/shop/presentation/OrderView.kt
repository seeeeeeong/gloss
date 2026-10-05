package fixtures.layers.shop.presentation

import fixtures.layers.shop.infrastructure.JdbcOrders

/** Skips the application layer to read storage. */
class OrderView(
    val jdbcOrders: JdbcOrders,
)
