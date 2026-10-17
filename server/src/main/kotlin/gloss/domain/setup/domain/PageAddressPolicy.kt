package gloss.domain.setup.domain

/**
 * Where the server may read a page from for the model: a public web page over HTTPS on its usual port,
 * never an address inside the network it runs in (OWASP SSRF Prevention Cheat Sheet). Ranges are
 * IANA's special-purpose registries (RFC 6890, RFC 8190); an IPv6 address must be global unicast
 * (2000::/3), and one that carries an IPv4 address is judged by it.
 */
object PageAddressPolicy {
    const val SCHEME = "https"
    const val PORT = 443

    // A link the model copies is short; past this it is a payload, not an address.
    const val MAX_URL = 2_000

    private const val IPV4 = 4
    private const val IPV6 = 16
    private const val BYTE = 0xFF

    /** [port] is -1 when the address names none. */
    fun allows(
        scheme: String?,
        host: String?,
        port: Int,
    ): Boolean = scheme.equals(SCHEME, ignoreCase = true) && !host.isNullOrEmpty() && (port == -1 || port == PORT)

    /** Whether [address], 4 or 16 bytes as a resolver gives them, may be connected to. */
    fun isPublic(address: ByteArray): Boolean =
        when (address.size) {
            IPV4 -> isPublicV4(address.map { it.toInt() and BYTE })
            IPV6 -> isPublicV6(address.map { it.toInt() and BYTE })
            else -> false
        }

    /** A block of addresses: [prefix] bits of [first] fixed. */
    private class Block(
        val first: List<Int>,
        val prefix: Int,
    ) {
        fun holds(address: List<Int>): Boolean =
            first.indices.all { i ->
                val bits = (prefix - i * Byte.SIZE_BITS).coerceIn(0, Byte.SIZE_BITS)
                val mask = (BYTE shl (Byte.SIZE_BITS - bits)) and BYTE
                (address[i] and mask) == (first[i] and mask)
            }
    }

    /** "10.0.0.0/8" as a block. */
    private fun v4(cidr: String): Block {
        val (address, prefix) = cidr.split('/')
        return Block(address.split('.').map(String::toInt), prefix.toInt())
    }

    // Not the open internet: this network, private, shared (CGNAT), loopback, link-local (where cloud
    // metadata answers), protocol assignments, documentation, 6to4 relay, benchmarking, multicast,
    // reserved and broadcast.
    private val V4_BLOCKED =
        listOf(
            "0.0.0.0/8",
            "10.0.0.0/8",
            "100.64.0.0/10",
            "127.0.0.0/8",
            "169.254.0.0/16",
            "172.16.0.0/12",
            "192.0.0.0/24",
            "192.0.2.0/24",
            "192.88.99.0/24",
            "192.168.0.0/16",
            "198.18.0.0/15",
            "198.51.100.0/24",
            "203.0.113.0/24",
            "224.0.0.0/4",
            "240.0.0.0/4",
        ).map(::v4)

    private fun v6(
        vararg first: Int,
        prefix: Int,
    ) = Block(first.toList() + List(IPV6 - first.size) { 0 }, prefix)

    private val GLOBAL_UNICAST = v6(0x20, prefix = 3)

    // Inside 2000::/3 yet not one host's own address: Teredo (2001::/32) hides an IPv4 address,
    // documentation (2001:db8::/32), ORCHIDv2 (2001:20::/28) and benchmarking (2001:2::/48) reach none.
    private val V6_BLOCKED =
        listOf(
            v6(0x20, 0x01, 0x00, 0x00, prefix = 32),
            v6(0x20, 0x01, 0x0d, 0xb8, prefix = 32),
            v6(0x20, 0x01, 0x00, 0x20, prefix = 28),
            v6(0x20, 0x01, 0x00, 0x02, prefix = 48),
        )

    private val MAPPED = v6(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, BYTE, BYTE, prefix = 96)
    private val NAT64 = v6(0x00, 0x64, 0xff, 0x9b, prefix = 96)
    private val SIX_TO_FOUR = v6(0x20, 0x02, prefix = 16)
    private const val SIX_TO_FOUR_AT = 2

    private fun isPublicV4(address: List<Int>): Boolean = V4_BLOCKED.none { it.holds(address) }

    private fun isPublicV6(address: List<Int>): Boolean =
        when {
            MAPPED.holds(address) || NAT64.holds(address) -> isPublicV4(address.takeLast(IPV4))
            SIX_TO_FOUR.holds(address) -> isPublicV4(address.subList(SIX_TO_FOUR_AT, SIX_TO_FOUR_AT + IPV4))
            else -> GLOBAL_UNICAST.holds(address) && V6_BLOCKED.none { it.holds(address) }
        }
}
