package gloss.common.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.boot.env.PropertySourceLoader
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.support.SpringFactoriesLoader
import java.nio.file.Path
import kotlin.io.path.writeText

class DotenvPropertySourceLoaderTest {
    @TempDir
    lateinit var dir: Path

    private fun load(text: String): Map<String, Any?> {
        val file = dir.resolve(".env").apply { writeText(text, Charsets.UTF_8) }
        val source = DotenvPropertySourceLoader().load(".env", FileSystemResource(file)).single()
        return listOf("NAME", "PASSWORD", "URL", "EMPTY", "# COMMENTED").associateWith { source.getProperty(it) }
    }

    @Test
    fun `a value outside ASCII reads as written`() {
        assertThat(load("NAME=이신성\n")["NAME"]).isEqualTo("이신성")
    }

    @Test
    fun `a backslash is kept, not taken as an escape`() {
        assertThat(load("PASSWORD=correct\\horse\\battery\n")["PASSWORD"]).isEqualTo("correct\\horse\\battery")
    }

    @Test
    fun `spaces around a value are part of it`() {
        assertThat(load("PASSWORD= correct horse battery \n")["PASSWORD"]).isEqualTo(" correct horse battery ")
    }

    @Test
    fun `only the first equals sign splits the line`() {
        assertThat(load("URL=jdbc:postgresql://db/gloss?a=b\n")["URL"]).isEqualTo("jdbc:postgresql://db/gloss?a=b")
    }

    @Test
    fun `comments and blank lines are skipped, an empty value is kept`() {
        val values = load("# COMMENTED=1\n\nEMPTY=\r\n")

        assertThat(values["# COMMENTED"]).isNull()
        assertThat(values["EMPTY"]).isEqualTo("")
    }

    @Test
    fun `spring boot finds it for the dotenv extension`() {
        val loaders = SpringFactoriesLoader.loadFactories(PropertySourceLoader::class.java, null)

        assertThat(loaders.filterIsInstance<DotenvPropertySourceLoader>().single().fileExtensions)
            .containsExactly("dotenv")
    }
}
