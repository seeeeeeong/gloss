package gloss.common.util

import java.util.Collections
import java.util.IdentityHashMap

/** Types and frames of a failure and its causes, without messages: a message may carry a secret or a reader's text. */
fun traceOf(exception: Throwable): String {
    // Two exceptions may name each other as cause; each is traced once.
    val seen = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
    return generateSequence(exception) { it.cause }.takeWhile { seen.add(it) }.joinToString("") { cause ->
        "\n${cause.javaClass.name}" + cause.stackTrace.joinToString("") { "\n\tat $it" }
    }
}
