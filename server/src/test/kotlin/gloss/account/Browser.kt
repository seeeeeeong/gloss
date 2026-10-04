package gloss.account

import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.net.CookieManager
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/** One browser against the running server: its own cookie jar, and the CSRF token it was last given. */
class Browser(
    private val port: Int,
) {
    private val json = JsonMapper()
    private val client = HttpClient.newBuilder().cookieHandler(CookieManager()).build()
    private var csrf: Pair<String, String>? = null

    data class Response(
        val status: Int,
        val body: JsonNode?,
        val setCookie: List<String>,
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

    fun fetchCsrf() = get("/api/auth/csrf").also { remember(it.body) }

    fun signIn(
        loginId: String,
        password: String,
    ): Response {
        fetchCsrf()
        return post("/api/auth/login", mapOf("loginId" to loginId, "password" to password))
            .also { remember(it.body?.get("csrf")) }
    }

    private fun remember(token: JsonNode?) {
        if (token?.has("token") == true) csrf = token["headerName"].asString() to token["token"].asString()
    }

    private fun send(
        method: String,
        path: String,
        body: Any?,
    ): Response {
        val request =
            HttpRequest
                .newBuilder(URI("http://127.0.0.1:$port$path"))
                .header("content-type", "application/json")
                .apply { if (method != "GET") csrf?.let { (name, value) -> header(name, value) } }
                .method(
                    method,
                    body?.let { HttpRequest.BodyPublishers.ofString(json.writeValueAsString(it)) }
                        ?: HttpRequest.BodyPublishers.noBody(),
                ).build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        return Response(
            status = response.statusCode(),
            body = response.body().takeIf { it.isNotBlank() }?.let { json.readTree(it) },
            setCookie = response.headers().allValues("set-cookie"),
        )
    }
}
