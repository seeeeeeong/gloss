package fixtures.modulith.declared.b

import fixtures.modulith.declared.a.application.AService
import fixtures.modulith.declared.a.domain.AEntity

/** Uses a's service, which a exposes, and a's entity, which it does not. */
class B(
    val service: AService,
    val entity: AEntity,
)
