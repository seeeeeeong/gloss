package gloss.common.config

import org.springframework.boot.env.PropertySourceLoader
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.PropertySource
import org.springframework.core.io.Resource

/**
 * Reads .env as KEY=VALUE lines in UTF-8, each value as written. The properties loader would
 * decode it as ISO-8859-1 and drop backslashes, garbling a name or a bootstrap password.
 */
class DotenvPropertySourceLoader : PropertySourceLoader {
    override fun getFileExtensions(): Array<String> = arrayOf("dotenv")

    override fun load(
        name: String,
        resource: Resource,
    ): List<PropertySource<*>> {
        val values =
            resource.inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines
                    .filter { '=' in it && !it.trimStart().startsWith("#") }
                    .associate { it.substringBefore('=').trim() to it.substringAfter('=') }
            }
        return listOf(MapPropertySource(name, values))
    }
}
