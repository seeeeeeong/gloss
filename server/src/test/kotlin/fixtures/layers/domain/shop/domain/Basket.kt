package fixtures.layers.domain.shop.domain

import fixtures.layers.common.security.Session

/** The model holding the signed-in session, a project type but not one the domain may use. */
class Basket(
    val session: Session,
)
