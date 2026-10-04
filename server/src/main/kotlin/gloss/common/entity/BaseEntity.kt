package gloss.common.entity

import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import jakarta.persistence.Version
import org.hibernate.Hibernate
import java.util.UUID

@MappedSuperclass
class BaseEntity(
    @field:Id
    val id: UUID,
) {
    // Also how Spring Data tells a new entity from a stored one, since the id is assigned up front.
    @Version
    var version: Long? = null
        protected set

    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is BaseEntity && Hibernate.getClass(this) == Hibernate.getClass(other) && id == other.id)

    override fun hashCode(): Int = id.hashCode()
}
