package north.undertow.example.filters

import com.fasterxml.jackson.databind.ObjectMapper
import io.undertow.server.HttpServerExchange
import io.undertow.util.AttachmentKey
import io.undertow.util.Headers
import io.undertow.util.StatusCodes
import north.undertow.ResponseFilter

object MarshalingFilter {
    fun filter(objectMapper: ObjectMapper): ResponseFilter = { exchange ->
        val responseObject = exchange.getAttachment(RESPONSE_OBJECT_KEY)
        if (responseObject != null) {
            val contentType = when (responseObject.value) {
                is Problem -> "application/problem+json"
                else -> "application/json"
            }

            exchange.statusCode = responseObject.statusCode
            exchange.responseHeaders.add(Headers.CONTENT_TYPE, contentType)
            exchange.responseSender.send(objectMapper.writeValueAsString(responseObject.value))
        }
    }

    val RESPONSE_OBJECT_KEY: AttachmentKey<ResponseObject> = AttachmentKey.create(ResponseObject::class.java)
}

data class ResponseObject(val statusCode: Int, val value: Any)

data class Problem(val status: Int, val title: String, val detail: String = "") {
    companion object {
        val UNAUTHORIZED = Problem(StatusCodes.UNAUTHORIZED, StatusCodes.UNAUTHORIZED_STRING)
        val FORBIDDEN = Problem(StatusCodes.FORBIDDEN, StatusCodes.FORBIDDEN_STRING)
        val CONFLICT = Problem(StatusCodes.CONFLICT, StatusCodes.CONFLICT_STRING)
        val PRECONDITION_FAILED = Problem(StatusCodes.PRECONDITION_FAILED, StatusCodes.PRECONDITION_FAILED_STRING)
        val INTERNAL_SERVER_ERROR = Problem(StatusCodes.INTERNAL_SERVER_ERROR, StatusCodes.INTERNAL_SERVER_ERROR_STRING)
        val NOT_FOUND = Problem(StatusCodes.NOT_FOUND, StatusCodes.NOT_FOUND_STRING)
    }
}

fun HttpServerExchange.respondWith(statusCode: Int, value: Any) {
    this.respondWith(ResponseObject(statusCode, value))
}

fun HttpServerExchange.respondWith(problem: Problem) {
    this.respondWith(problem.status, problem)
}

fun HttpServerExchange.respondWith(response: ResponseObject) {
    this.putAttachment(MarshalingFilter.RESPONSE_OBJECT_KEY, response)
}
