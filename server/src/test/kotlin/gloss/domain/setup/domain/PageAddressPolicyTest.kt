package gloss.domain.setup.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PageAddressPolicyTest {
    private fun v4(vararg bytes: Int) = bytes.map { it.toByte() }.toByteArray()

    private fun v6(text: String): ByteArray {
        val (head, tail) = if ("::" in text) text.split("::") else listOf(text, "")
        val left = head.split(':').filter { it.isNotEmpty() }
        val right = tail.split(':').filter { it.isNotEmpty() }
        val groups = left + List(8 - left.size - right.size) { "0" } + right
        return groups
            .map { it.toInt(16) }
            .flatMap { listOf((it shr 8).toByte(), it.toByte()) }
            .toByteArray()
    }

    @Test
    fun `only https on its usual port, to a named host`() {
        assertThat(PageAddressPolicy.allows("https", "example.com", -1)).isTrue()
        assertThat(PageAddressPolicy.allows("HTTPS", "example.com", 443)).isTrue()
        assertThat(PageAddressPolicy.allows("http", "example.com", -1)).isFalse()
        assertThat(PageAddressPolicy.allows("file", null, -1)).isFalse()
        assertThat(PageAddressPolicy.allows("https", "example.com", 8443)).isFalse()
        assertThat(PageAddressPolicy.allows("https", "", -1)).isFalse()
    }

    @Test
    fun `public IPv4 addresses may be read`() {
        assertThat(PageAddressPolicy.isPublic(v4(93, 184, 216, 34))).isTrue()
        assertThat(PageAddressPolicy.isPublic(v4(140, 82, 112, 3))).isTrue()
        assertThat(PageAddressPolicy.isPublic(v4(172, 32, 0, 1))).isTrue()
        assertThat(PageAddressPolicy.isPublic(v4(100, 128, 0, 1))).isTrue()
    }

    @Test
    fun `private, loopback, link-local, shared, reserved and multicast IPv4 addresses never are`() {
        val blocked =
            listOf(
                v4(0, 0, 0, 0),
                v4(10, 1, 2, 3),
                v4(100, 64, 0, 1),
                v4(127, 0, 0, 1),
                v4(169, 254, 169, 254),
                v4(172, 16, 0, 1),
                v4(172, 31, 255, 255),
                v4(192, 0, 0, 170),
                v4(192, 0, 2, 1),
                v4(192, 168, 0, 1),
                v4(198, 18, 0, 1),
                v4(198, 51, 100, 7),
                v4(203, 0, 113, 9),
                v4(224, 0, 0, 1),
                v4(240, 0, 0, 1),
                v4(255, 255, 255, 255),
            )

        assertThat(blocked.map(PageAddressPolicy::isPublic)).containsOnly(false)
    }

    @Test
    fun `an IPv6 address must be global unicast, and one carrying an IPv4 address is judged by it`() {
        assertThat(PageAddressPolicy.isPublic(v6("2606:4700::6810:84e5"))).isTrue()
        assertThat(PageAddressPolicy.isPublic(v6("::ffff:5db8:d822"))).isTrue()
        val blocked =
            listOf(
                "::",
                "::1",
                "fe80::1",
                "fc00::1",
                "fd12:3456::1",
                "ff02::1",
                "2001:db8::1",
                "2001::1",
                "::ffff:7f00:1",
                "::ffff:a9fe:a9fe",
                "64:ff9b::a00:1",
                "2002:c0a8:101::1",
                "::7f00:1",
            ).map(::v6)

        assertThat(blocked.map(PageAddressPolicy::isPublic)).containsOnly(false)
    }

    @Test
    fun `an address of any other length is refused`() {
        assertThat(PageAddressPolicy.isPublic(ByteArray(5))).isFalse()
    }
}
