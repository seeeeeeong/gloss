package fixtures.layers.domain.shop.domain

import fixtures.layers.common.util.Ids
import jakarta.persistence.Entity
import jakarta.persistence.Id

/** Uses the shared id maker, which the domain may. */
@Entity
class Order(
    @Id val id: Long = Ids.next(),
)
