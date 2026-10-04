package fixtures.modulith.nested.domain.b

import fixtures.modulith.nested.domain.a.entity.AEntity
import fixtures.modulith.nested.domain.a.service.AService

/** Uses a's service, which a exposes, and a's entity, which it does not. */
class B(
    val service: AService,
    val entity: AEntity,
)
