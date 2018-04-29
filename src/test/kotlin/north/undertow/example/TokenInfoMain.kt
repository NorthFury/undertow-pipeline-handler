package north.undertow.example

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.undertow.Handlers
import io.undertow.Undertow
import io.undertow.util.StatusCodes

fun main(args: Array<String>) {
    val objectMapper = ObjectMapper()
            .registerKotlinModule()
            .setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE)

    val handler = Handlers.routing()
            .get("/tokeninfo") { exchange ->
                val tokenString = exchange.queryParameters["access_token"]?.first ?: ""

                val splitToken = tokenString.split('~')
                if (splitToken.size == 3) {
                    val token = TokenInfo(
                            uid = splitToken[0],
                            realm = splitToken[1],
                            scope = splitToken[2].split(',')
                                    .filter(String::isNotEmpty)
                    )
                    exchange.responseSender.send(objectMapper.writeValueAsString(token))
                } else {
                    exchange.statusCode = StatusCodes.BAD_REQUEST
                    exchange.responseSender.send(StatusCodes.BAD_REQUEST_STRING)
                }
            }

    Undertow.builder()
            .addHttpListener(8082, "0.0.0.0", handler)
            .build()
            .start()
}
