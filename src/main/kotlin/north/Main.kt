package north

import io.undertow.Undertow
import io.undertow.predicate.Predicates
import io.undertow.util.AttachmentKey
import io.undertow.util.Methods
import north.undertow.*
import north.undertow.builder.PipelineHandlerBuilder
import north.undertow.builder.RouterBuilder
import java.util.concurrent.CompletableFuture

fun main(args: Array<String>) {
    val RESPONSE_KEY = AttachmentKey.create(String::class.java)

    val router = RouterBuilder()
            .add(Methods.GET, "/test/{one}", Predicates.truePredicate(), { exchange ->
                println("route")
                exchange.responseSender.send(exchange.pathParams["one"])
                Handled
            })
            .add(Methods.GET, "/async", Predicates.truePredicate(), { exchange ->
                val future = CompletableFuture.runAsync {
                    Thread.sleep(100)
                    println("async route")
                    exchange.putAttachment(RESPONSE_KEY, "async response")
                }
                AsyncResponse(future)
            })
            .build()

    val handler = PipelineHandlerBuilder(router)
            .before {
                println("requestFilter 1")
                Continue
            }
            .before {
                val future = CompletableFuture.runAsync {
                    Thread.sleep(100)
                    println("auth")
                }
                AsyncProcessStarted(future.thenApply { Continue })
            }
            .before {
                println("requestFilter 2")
                Continue
            }
            .after {
                println("responseFilter: " + it.matchedPathTemplate)
            }
            .after { exchange ->
                exchange.getAttachment(RESPONSE_KEY)?.also {
                    println("responseFilter response attachment: $it")
                    exchange.responseSender.send(it)
                }
            }
            .build()

    val server = Undertow.builder()
            .addHttpListener(8080, "0.0.0.0", handler)
            .build()

    server.start()
}
