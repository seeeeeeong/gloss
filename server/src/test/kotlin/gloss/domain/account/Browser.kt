package gloss.domain.account

import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.net.CookieManager
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.random.Random

/** One browser against the running server: its own cookie jar and address. */
class Browser(
    private val port: Int,
    // Each browser its own address, so sign-in limits counted per address do not add up across tests.
    val ip: String = "10.${Random.nextInt(256)}.${Random.nextInt(256)}.${Random.nextInt(1, 255)}",
) {
    private val json = JsonMapper()
    private val cookies = CookieManager()
    private val client = HttpClient.newBuilder().cookieHandler(cookies).build()

    data class Response(
        val status: Int,
        val body: JsonNode?,
        val setCookie: List<String>,
        val headers: Map<String, List<String>> = emptyMap(),
    ) {
        val code: String? get() = body?.get("code")?.asString()
    }

    fun get(path: String) = send("GET", path, null)

    fun post(
        path: String,
        body: Any? = null,
    ) = send("POST", path, body)

    fun put(
        path: String,
        body: Any,
    ) = send("PUT", path, body)

    fun delete(path: String) = send("DELETE", path, null)

    /** A POST whose answer is read as it came, not as JSON: a stream of server-sent events. */
    fun postForText(
        path: String,
        body: Any,
    ): Text {
        val response = client.send(request("POST", path, body), HttpResponse.BodyHandlers.ofString())
        return Text(response.statusCode(), response.body(), response.headers().firstValue("content-type").orElse(null))
    }

    data class Text(
        val status: Int,
        val body: String,
        val contentType: String?,
    )

    fun fetchCsrf() = get("/api/auth/csrf")

    fun signIn(
        loginId: String,
        password: String,
    ): Response {
        fetchCsrf()
        return post("/api/auth/login", mapOf("loginId" to loginId, "password" to password))
    }

    /** What the page does: reads the CSRF cookie and sends it back as a header. */
    private fun csrfToken(): String? =
        cookies.cookieStore.cookies
            .firstOrNull { it.name == "XSRF-TOKEN" }
            ?.value

    private fun send(
        method: String,
        path: String,
        body: Any?,
    ): Response {
        val response = client.send(request(method, path, body), HttpResponse.BodyHandlers.ofString())
        return Response(
            status = response.statusCode(),
            body = response.body().takeIf { it.isNotBlank() }?.let { json.readTree(it) },
            setCookie = response.headers().allValues("set-cookie"),
            headers = response.headers().map(),
        )
    }

    private fun request(
        method: String,
        path: String,
        body: Any?,
    ): HttpRequest =
        HttpRequest
            .newBuilder(URI("http://127.0.0.1:$port$path"))
            .header("content-type", "application/json")
            .header("x-forwarded-for", ip)
            .apply { if (method != "GET") csrfToken()?.let { header("X-XSRF-TOKEN", it) } }
            .method(
                method,
                body?.let { HttpRequest.BodyPublishers.ofString(json.writeValueAsString(it)) }
                    ?: HttpRequest.BodyPublishers.noBody(),
            ).build()
}
