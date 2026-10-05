package gloss.apikey.domain

import java.util.Base64

/** Reads `1:<base64>,2:<base64>`: the key encrypting readers' API keys, by version. */
object MasterKeyParser {
    // AES-256.
    private const val KEY_BYTES = 32

    fun parse(text: String): Map<Int, ByteArray> {
        val entries = text.split(',').map(String::trim).filter(String::isNotEmpty)
        // Without a master key a reader's API key could only be stored in the clear.
        require(entries.isNotEmpty()) { "GLOSS_API_KEY_MASTER_KEYS is empty; set version:base64 pairs" }
        val pairs =
            entries.map { entry ->
                val version = entry.substringBefore(':', "").toIntOrNull()
                require(version != null && version > 0) { "A master key entry is not version:base64" }
                val key = runCatching { Base64.getDecoder().decode(entry.substringAfter(':')) }.getOrNull()
                require(key != null && key.size == KEY_BYTES) { "Master key $version is not 32 bytes of base64" }
                version to key
            }
        require(pairs.map { it.first }.toSet().size == pairs.size) { "A master key version is given twice" }
        return pairs.toMap()
    }
}
