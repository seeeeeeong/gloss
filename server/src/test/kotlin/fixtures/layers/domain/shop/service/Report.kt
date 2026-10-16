package fixtures.layers.domain.shop.service

import fixtures.layers.domain.shop.presentation.ShopController

/** Reaches up into the web layer. */
class Report(
    val shopController: ShopController,
)
