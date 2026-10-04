package gloss

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.modulith.Modulithic

@Modulithic
@SpringBootApplication
class GlossApplication

@Suppress("SpreadOperator") // Once, at startup.
fun main(args: Array<String>) {
    runApplication<GlossApplication>(*args)
}
