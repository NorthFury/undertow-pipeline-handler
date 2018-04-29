package north.undertow.example.filters

import com.codahale.metrics.MetricRegistry
import north.undertow.ResponseFilter
import north.undertow.matchedPathTemplate
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

object MetricsFilter {
    fun filter(metricRegistry: MetricRegistry): ResponseFilter = { exchange ->
        val matchedPathTemplate = exchange.matchedPathTemplate
        if (matchedPathTemplate != null) {
            val requestStartTime = exchange.requestStartTime;
            if (requestStartTime == -1L) {
                logger.info("You need to enable UndertowOptions.RECORD_REQUEST_START_TIME to be able to gather timer metrics.")
            } else {
                val duration = System.nanoTime() - requestStartTime

                val statusCode = exchange.statusCode
                val pathPart = matchedPathTemplate.substringAfter('/').replace('/', '.')

                metricRegistry
                        .timer("$statusCode.$pathPart")
                        .update(duration, TimeUnit.NANOSECONDS)
            }
        }
    }

    private val logger = LoggerFactory.getLogger(MetricsFilter::class.java)!!
}
