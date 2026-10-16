package fixtures.layers.domain.shop.domain

import fixtures.layers.domain.shop.service.ShopService

/** The model calling a use case. */
class Invoice(
    val shopService: ShopService,
)
