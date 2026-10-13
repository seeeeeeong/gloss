package gloss.plan.infrastructure

import gloss.plan.domain.PlanPolicy
import gloss.plan.domain.PlanPrompt
import org.springframework.stereotype.Component
import tools.jackson.core.JacksonException
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.ObjectNode

/**
 * A plan as JSON, both ways: a model's reply read into a draft, and an accepted plan written as the
 * bytes every post's prompt carries. Written with the same fields in the same order and no
 * whitespace, so one plan is always the same bytes.
 */
@Component
class PlanJson {
    private val json = JsonMapper()

    /** Null when the reply is not a JSON object. A field that is missing or of another kind is left out. */
    fun read(text: String): PlanPolicy.Draft? {
        val reply =
            try {
                json.readTree(text)
            } catch (_: JacksonException) {
                null
            }
        if (reply == null || !reply.isObject) return null
        return PlanPolicy.Draft(
            audience = reply.text("audience"),
            glossary = reply.objects("glossary").map(::termOf),
            items = reply.objects("items").map(::itemOf),
        )
    }

    private fun termOf(node: JsonNode) =
        PlanPolicy.DraftTerm(
            term = node.text("term"),
            en = node.text("en"),
            means = node.text("means"),
            at = node.int("at"),
        )

    private fun itemOf(node: JsonNode) =
        PlanPolicy.DraftItem(
            i = node.int("i"),
            kind = node.text("kind"),
            covers = node.array("covers").filter { it.isString }.map { it.asString() },
            excludes = node.objects("excludes").map { PlanPolicy.DraftExclude(it.text("what"), it.int("see")) },
            example = node.text("example"),
        )

    /** The book's title and each item's number and title go in with the plan, so a post's prompt needs only this. */
    fun write(
        subject: PlanPrompt.Subject,
        plan: PlanPolicy.Plan,
    ): String {
        val root = json.createObjectNode()
        root.putObject("book").apply {
            put("title", subject.title)
            putArray("authors").apply { subject.authors.forEach(::add) }
        }
        root.put("audience", plan.audience)
        val glossary = root.putArray("glossary")
        plan.glossary.forEach {
            glossary.addObject().apply {
                put("term", it.term)
                put("en", it.en)
                put("means", it.means)
                putInt("at", it.at)
            }
        }
        val items = root.putArray("items")
        subject.lines.zip(plan.items).forEach { (line, item) ->
            items.addObject().apply {
                put("i", line.ord)
                putInt("parent", line.parent)
                put("number", line.number)
                put("title", line.title)
                put("kind", item.kind.code)
                putArray("covers").apply { item.covers.forEach(::add) }
                val excludes = putArray("excludes")
                item.excludes.forEach {
                    excludes.addObject().apply {
                        put("what", it.what)
                        putInt("see", it.see)
                    }
                }
                put("example", item.example)
            }
        }
        return json.writeValueAsString(root)
    }

    private fun ObjectNode.putInt(
        name: String,
        value: Int?,
    ) {
        if (value == null) putNull(name) else put(name, value)
    }

    private fun JsonNode.text(name: String): String? = path(name).takeIf { it.isString }?.asString()

    private fun JsonNode.int(name: String): Int? =
        path(name).takeIf { it.isIntegralNumber && it.canConvertToInt() }?.asInt()

    private fun JsonNode.objects(name: String): List<JsonNode> = array(name).filter { it.isObject }

    // An object is iterable too, by its values: only an array is a list.
    private fun JsonNode.array(name: String): List<JsonNode> = path(name).takeIf { it.isArray }?.toList().orEmpty()
}
