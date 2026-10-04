package gloss

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.modulith.Modulithic

/** Each package directly under `gloss` is an application module (docs/design/architecture.md). */
@Modulithic
@SpringBootApplication
class GlossApplication

@Suppress("SpreadOperator") // Copies the arguments once, at startup.
fun main(args: Array<String>) {
    runApplication<GlossApplication>(*args)
}
