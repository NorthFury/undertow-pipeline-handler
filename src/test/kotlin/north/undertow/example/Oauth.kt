package north.undertow.example

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.undertow.util.AttachmentKey
import io.undertow.util.Headers
import north.undertow.*
import north.undertow.example.filters.Problem
import north.undertow.example.filters.respondWith
import org.asynchttpclient.AsyncHttpClient
import org.asynchttpclient.Dsl
import org.slf4j.LoggerFactory

object Oauth {
    fun requestFilter(httpClient: AsyncHttpClient, endpoint: String): RequestFilter = { exchange ->
        val authHeaders = exchange.requestHeaders[Headers.AUTHORIZATION]
        val token = authHeaders
                ?.find { it.startsWith(BEARER_PREFIX, ignoreCase = true) }
                ?.substring(BEARER_PREFIX_LENGTH)


        if (token == null) Continue else {
            val request = Dsl.get(endpoint)
                    .addQueryParam("access_token", token)
                    .build()

            val future = httpClient.executeRequest(request)
                    .toCompletableFuture()
                    .thenApply { response ->
                        try {
                            if (response.statusCode == 200) {
                                val tokenInfo = objectMapper.readValue(response.responseBodyAsStream, TokenInfo::class.java)
                                exchange.putAttachment(TOKEN_INFO_KEY, tokenInfo)
                            }

                        } catch (e: Exception) {
                            logger.error("TokenInfo parsing error", e)
                        }
                        Continue
                    }
            AsyncProcessStarted(future)
        }
    }

    fun authorized(scopes: Set<String>, handler: RouteHandler): RouteHandler = { exchange ->
        val tokenInfo = exchange.getAttachment(TOKEN_INFO_KEY)
        when {
            tokenInfo == null -> {
                exchange.respondWith(UNAUTHORIZED_PROBLEM)
                Handled
            }

            scopes.isNotEmpty() && scopes.all { it !in tokenInfo.scope } -> {
                exchange.respondWith(Problem.FORBIDDEN)
                Handled
            }

            else -> handler(exchange)
        }
    }

    private val UNAUTHORIZED_PROBLEM = Problem.UNAUTHORIZED.copy(detail = "Invalid Token")

    private const val BEARER_PREFIX = "Bearer "
    private const val BEARER_PREFIX_LENGTH = BEARER_PREFIX.length

    private val objectMapper = ObjectMapper()
            .registerModule(KotlinModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    private val logger = LoggerFactory.getLogger(Oauth::class.java)!!

    val TOKEN_INFO_KEY = AttachmentKey.create(TokenInfo::class.java)!!
}

data class TokenInfo(
        val uid: String,
        val realm: String,
        val scope: List<String>
)
