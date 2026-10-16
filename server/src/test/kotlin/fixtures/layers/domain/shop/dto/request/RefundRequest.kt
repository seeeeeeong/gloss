package fixtures.layers.domain.shop.dto.request

import fixtures.layers.domain.shop.service.ShopService

/** Carries a use case instead of data. */
class RefundRequest(
    val shopService: ShopService,
)
