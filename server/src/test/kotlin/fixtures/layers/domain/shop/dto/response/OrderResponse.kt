package fixtures.layers.domain.shop.dto.response

import fixtures.layers.domain.shop.domain.Order

/** Built from the model, which a dto may read. */
data class OrderResponse(
    val id: Long,
) {
    companion object {
        fun from(order: Order) = OrderResponse(order.id)
    }
}
