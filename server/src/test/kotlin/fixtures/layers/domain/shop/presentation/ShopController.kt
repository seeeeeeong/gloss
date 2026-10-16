package fixtures.layers.domain.shop.presentation

import fixtures.layers.domain.shop.dto.response.OrderResponse
import fixtures.layers.domain.shop.service.ShopService

class ShopController(
    val shopService: ShopService,
) {
    fun orders(): List<OrderResponse> = shopService.all()
}
