package north.undertow.example

import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.json.MetricsModule
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.undertow.Handlers
import io.undertow.Undertow
import io.undertow.UndertowOptions
import io.undertow.predicate.Predicates
import io.undertow.server.HttpHandler
import io.undertow.server.HttpServerExchange
import io.undertow.server.handlers.accesslog.AccessLogHandler
import io.undertow.server.handlers.accesslog.AccessLogReceiver
import io.undertow.util.Methods
import io.undertow.util.StatusCodes
import north.undertow.*
import north.undertow.builder.PipelineHandlerBuilder
import north.undertow.builder.RouterBuilder
import north.undertow.example.filters.MarshalingFilter
import north.undertow.example.filters.MetricsFilter
import north.undertow.example.filters.respondWith
import org.asynchttpclient.Dsl
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

fun main(args: Array<String>) {
    val metricRegistry = MetricRegistry()
    val objectMapper = ObjectMapper()
            .registerKotlinModule()
            .registerModule(JavaTimeModule())
            .setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE)
            .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    val asyncHttpClient = Dsl.asyncHttpClient(Dsl.config()
            .setMaxConnections(20)
            .setConnectTimeout(1000)
            .setRequestTimeout(1000)
            .build()
    )

    val accessLogHandler = AccessLogHandler(
            HttpHandler {},
            AccessLogReceiver { println(it) },
            "%h %l %u %t \"%r\" %s %b %D",
            ClassLoader.getSystemClassLoader()
    )

    val router = RouterBuilder()
            .add(Methods.GET, "/authorized", Predicates.truePredicate(), Oauth.authorized(setOf("uid"), { exchange ->
                exchange.respondWith(200, "Authorized response")
                Handled
            }))
            .add(Methods.GET, "/test/{one}", Predicates.truePredicate(), { exchange ->
                println("route")
                exchange.responseSender.send(exchange.pathParams["one"])
                Handled
            })
            .add(Methods.GET, "/async", Predicates.truePredicate(), { exchange ->
                val future = CompletableFuture.runAsync {
                    Thread.sleep(100)
                    println("async route")
                    exchange.respondWith(StatusCodes.OK, "async response")
                }
                AsyncResponse(future)
            })
            .build()

    val handler = PipelineHandlerBuilder(router)
            .requestFilter { accessLogHandler.handleRequest(it); Continue }
            .requestFilter(Oauth.requestFilter(asyncHttpClient, "http://localhost:8082/tokeninfo"))
            .requestFilter {
                println("requestFilter 1")
                Continue
            }
            .requestFilter {
                val future = CompletableFuture.runAsync {
                    Thread.sleep(100)
                    println("asyncRequestFilter")
                }
                AsyncProcessStarted(future.thenApply { Continue })
            }
            .requestFilter {
                println("requestFilter 2")
                Continue
            }
            .responseFilter(MarshalingFilter.filter(objectMapper))
            .responseFilter(MetricsFilter.filter(metricRegistry))
            .build()

    val metricsHandler = Handlers.routing()
            .get("metrics", object : HttpHandler {
                override fun handleRequest(exchange: HttpServerExchange) {
                    val responseObject = mapOf("timers" to metricRegistry.timers)
                    val response = metricsObjectMapper.writeValueAsString(responseObject)
                    exchange.responseSender.send(response)
                }

                private val metricsObjectMapper = ObjectMapper()
                        .registerModule(MetricsModule(TimeUnit.MINUTES, TimeUnit.MILLISECONDS, false))
            })

    val server = Undertow.builder()
            .addHttpListener(8080, "0.0.0.0", handler)
            .addHttpListener(8081, "0.0.0.0", metricsHandler)
            .setServerOption(UndertowOptions.RECORD_REQUEST_START_TIME, true)
            .build()

    server.start()
}
