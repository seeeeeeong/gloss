package fixtures.modulith.declared.b

import fixtures.modulith.declared.a.domain.AEntity
import fixtures.modulith.declared.a.service.AService

/** Uses a's service, which a exposes, and a's entity, which it does not. */
class B(
    val service: AService,
    val entity: AEntity,
)
