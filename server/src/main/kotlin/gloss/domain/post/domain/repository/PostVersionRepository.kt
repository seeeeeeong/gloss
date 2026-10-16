package gloss.domain.post.domain.repository

import gloss.domain.post.domain.PostVersion
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PostVersionRepository : JpaRepository<PostVersion, UUID> {
    fun findByIdAndAccountId(
        id: UUID,
        accountId: UUID,
    ): PostVersion?

    fun findAllByIdInAndAccountId(
        ids: Collection<UUID>,
        accountId: UUID,
    ): List<PostVersion>

    fun findTopByPostIdAndAccountIdOrderByVersionNoDesc(
        postId: UUID,
        accountId: UUID,
    ): PostVersion?
}
