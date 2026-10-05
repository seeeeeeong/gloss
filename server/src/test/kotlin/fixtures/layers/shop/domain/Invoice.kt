package fixtures.layers.shop.domain

import fixtures.layers.shop.application.ShopService

/** The model calling a use case. */
class Invoice(
    val shopService: ShopService,
)
