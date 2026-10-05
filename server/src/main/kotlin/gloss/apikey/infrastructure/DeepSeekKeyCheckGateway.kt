package gloss.apikey.infrastructure

import gloss.apikey.domain.ApiKeySecret
import gloss.apikey.domain.KeyCheckGateway
import gloss.apikey.domain.KeyCheckResult
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatusCode
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import java.net.http.HttpClient
import java.time.Duration

/**
 * Checks a key with `GET /user/balance`, which runs no model and so spends no tokens; DeepSeek
 * answers 401 for a wrong key (api-docs.deepseek.com, error codes). Never logs the key.
 */
@Component
class DeepSeekKeyCheckGateway(
    @Value("\${gloss.deepseek.base-url}") baseUrl: String,
) : KeyCheckGateway {
    private val log = LoggerFactory.getLogger(javaClass)

    // The reader waits on the save while this runs: a short wait, then "try again".
    private val restClient =
        RestClient
            .builder()
            .baseUrl(baseUrl)
            .requestFactory(
                JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build())
                    .apply { setReadTimeout(READ_TIMEOUT) },
            ).build()

    override fun check(secret: ApiKeySecret): KeyCheckResult =
        try {
            restClient
                .get()
                .uri("/user/balance")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${secret.value}")
                .exchange { _, response -> resultOf(response.statusCode) }
        } catch (e: ResourceAccessException) {
            log.warn("DeepSeek key check got no answer: {}", e.javaClass.simpleName)
            KeyCheckResult.Unavailable
        }

    private fun resultOf(status: HttpStatusCode): KeyCheckResult =
        when {
            status.is2xxSuccessful -> {
                KeyCheckResult.Valid
            }

            status.value() == UNAUTHORIZED -> {
                KeyCheckResult.Rejected
            }

            else -> {
                log.warn("DeepSeek key check answered {}", status.value())
                KeyCheckResult.Unavailable
            }
        }

    private companion object {
        // A TCP and TLS handshake with a healthy API takes well under a second; three allows a slow
        // link without leaving the reader waiting on a host that is down.
        val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(3)

        // The balance call runs no model, so it answers quickly; five is as long as a save form
        // should hang before saying "try again".
        val READ_TIMEOUT: Duration = Duration.ofSeconds(5)
        const val UNAUTHORIZED = 401
    }
}
