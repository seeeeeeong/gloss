package fixtures.layers.shop.infrastructure

import fixtures.layers.shop.domain.Order

class JdbcOrders {
    fun all(): List<Order> = emptyList()
}
