package fixtures.layers.domain.shop.service

import fixtures.layers.domain.shop.dto.response.OrderResponse
import fixtures.layers.domain.shop.infrastructure.JdbcOrders

class ShopService(
    val jdbcOrders: JdbcOrders,
) {
    fun all(): List<OrderResponse> = jdbcOrders.all().map(OrderResponse::from)
}
