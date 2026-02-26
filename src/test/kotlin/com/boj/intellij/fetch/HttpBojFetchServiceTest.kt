package com.boj.intellij.fetch

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class HttpBojFetchServiceTest {
    private val servers = mutableListOf<HttpServer>()

    @AfterTest
    fun tearDown() {
        servers.forEach { it.stop(0) }
        servers.clear()
    }

    @Test
    fun `fetch problem page succeeds when server expects browser-like headers`() {
        val server = createServer { exchange ->
            if (exchange.requestURI.path != "/problem/1000") {
                sendText(exchange, 404, "not found")
                return@createServer
            }

            val headers = exchange.requestHeaders
            val userAgent = headers.getFirst("User-Agent").orEmpty()
            val accept = headers.getFirst("Accept").orEmpty()
            val acceptLanguage = headers.getFirst("Accept-Language").orEmpty()
            val referer = headers.getFirst("Referer").orEmpty()

            val isBrowserLike =
                userAgent.contains("Mozilla/5.0") &&
                    accept.contains("text/html") &&
                    acceptLanguage.contains("ko") &&
                    referer == "https://www.acmicpc.net/"

            if (isBrowserLike) {
                sendText(exchange, 200, "ok-body")
            } else {
                sendText(exchange, 403, "forbidden")
            }
        }

        val service = HttpBojFetchService(problemUrlPrefix = "http://127.0.0.1:${server.address.port}/problem/")

        assertEquals("ok-body", service.fetchProblemPage("1000"))
    }

    @Test
    fun `fetch problem page retries once on 403 with alternate header profile`() {
        val requestCount = AtomicInteger(0)
        val seenUserAgents = mutableListOf<String>()
        val server = createServer { exchange ->
            if (exchange.requestURI.path != "/problem/1000") {
                sendText(exchange, 404, "not found")
                return@createServer
            }

            val userAgent = exchange.requestHeaders.getFirst("User-Agent").orEmpty()
            seenUserAgents += userAgent

            val index = requestCount.incrementAndGet()
            if (index == 1) {
                sendText(exchange, 403, "first blocked")
                return@createServer
            }

            if (index == 2 && userAgent != seenUserAgents.first()) {
                sendText(exchange, 200, "retried-ok")
            } else {
                sendText(exchange, 403, "still blocked")
            }
        }

        val service = HttpBojFetchService(problemUrlPrefix = "http://127.0.0.1:${server.address.port}/problem/")

        assertEquals("retried-ok", service.fetchProblemPage("1000"))
        assertEquals(2, requestCount.get())
        assertEquals(2, seenUserAgents.distinct().size)
    }

    @Test
    fun `fetch problem page does not retry when response is not 403`() {
        val requestCount = AtomicInteger(0)
        val server = createServer { exchange ->
            requestCount.incrementAndGet()
            sendText(exchange, 500, "internal error")
        }

        val service = HttpBojFetchService(problemUrlPrefix = "http://127.0.0.1:${server.address.port}/problem/")

        val exception = assertFailsWith<IllegalStateException> {
            service.fetchProblemPage("1000")
        }

        assertTrue(exception.message.orEmpty().contains("HTTP 500"))
        assertEquals(1, requestCount.get())
    }

    private fun createServer(handler: (HttpExchange) -> Unit): HttpServer {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            handler(exchange)
        }
        server.start()
        servers += server
        return server
    }

    private fun sendText(exchange: HttpExchange, statusCode: Int, body: String) {
        val payload = body.toByteArray()
        exchange.sendResponseHeaders(statusCode, payload.size.toLong())
        exchange.responseBody.use { output ->
            output.write(payload)
        }
        exchange.close()
    }
}
