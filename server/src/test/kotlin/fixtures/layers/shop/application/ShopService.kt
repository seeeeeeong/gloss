package fixtures.layers.shop.application

import fixtures.layers.shop.domain.Order
import fixtures.layers.shop.infrastructure.JdbcOrders

class ShopService(
    val jdbcOrders: JdbcOrders,
) {
    fun all(): List<Order> = jdbcOrders.all()
}
