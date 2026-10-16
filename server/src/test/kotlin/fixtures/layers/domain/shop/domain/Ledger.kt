package fixtures.layers.domain.shop.domain

import org.springframework.jdbc.core.simple.JdbcClient

/** The model writing SQL itself. */
class Ledger(
    val jdbcClient: JdbcClient,
)
