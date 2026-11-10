package fr.gateremote.mg4

import fr.gateremote.mg4.data.Gate
import fr.gateremote.mg4.data.GateAction
import fr.gateremote.mg4.data.GateJson
import fr.gateremote.mg4.data.GateRepository
import fr.gateremote.mg4.logic.ActionResult
import fr.gateremote.mg4.logic.HttpExecutor
import fr.gateremote.mg4.logic.distanceMeters
import fr.gateremote.mg4.logic.formatDistance
import fr.gateremote.mg4.logic.parseHeaders
import fr.gateremote.mg4.logic.rankGates
import fr.gateremote.mg4.logic.validateHttp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread

class LogicTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val home = Gate("home", "Maison", 48.8584, 2.2945, radiusMeters = 150)
    private val office = Gate("office", "Bureau", 48.8738, 2.2950, radiusMeters = 150)

    @Test
    fun `distance between Eiffel Tower and Arc de Triomphe is about 1,7 km`() {
        val d = distanceMeters(48.8584, 2.2945, 48.8738, 2.2950)
        assertEquals(1712.0, d, 15.0)
        assertEquals(0.0, distanceMeters(48.0, 2.0, 48.0, 2.0), 0.001)
    }

    @Test
    fun `gates are ranked by distance and flagged when inside their radius`() {
        // ~55 m au nord de « Maison »
        val ranked = rankGates(listOf(office, home), 48.8589, 2.2945)
        assertEquals(listOf("home", "office"), ranked.map { it.gate.id })
        assertTrue(ranked[0].isNearby)
        assertFalse(ranked[1].isNearby)
    }

    @Test
    fun `radius decides whether a gate is nearby`() {
        val tight = home.copy(radiusMeters = 30)
        assertFalse(rankGates(listOf(tight), 48.8589, 2.2945).single().isNearby)
    }

    @Test
    fun `without position gates are sorted by name and none is nearby`() {
        val ranked = rankGates(listOf(home, office), null, null)
        assertEquals(listOf("Bureau", "Maison"), ranked.map { it.gate.name })
        assertTrue(ranked.none { it.isNearby })
        assertNull(ranked.first().distanceMeters)
    }

    @Test
    fun `distances are formatted in metres then kilometres`() {
        assertEquals("120 m", formatDistance(120.4))
        assertEquals("1,7 km", formatDistance(1712.0))
    }

    @Test
    fun `headers are parsed one per line and malformed lines are skipped`() {
        val parsed = parseHeaders("Authorization: Bearer a:b\n\n  X-Key:  42 \nnonsense")
        assertEquals(listOf("Authorization" to "Bearer a:b", "X-Key" to "42"), parsed)
    }

    @Test
    fun `http actions are validated before being sent`() {
        assertNotNull(validateHttp(GateAction.Http("")))
        assertNotNull(validateHttp(GateAction.Http("ftp://example.org")))
        assertNotNull(validateHttp(GateAction.Http("http://example.org", method = "DELETE")))
        assertNull(validateHttp(GateAction.Http("http://192.168.1.20/relay/0?turn=on")))
        assertTrue(HttpExecutor.execute(GateAction.Http("portail")) is ActionResult.Failure)
    }

    @Test
    fun `configuration survives a JSON round trip`() {
        val gates = listOf(
            home.copy(
                openAction = GateAction.Http("https://example.org/open", "POST", "X-Key: 1", "{\"on\":true}"),
                closeAction = GateAction.LaunchApp("com.example.intercom"),
            ),
            office,
        )
        assertEquals(gates, GateJson.decode(GateJson.encode(gates)))
    }

    @Test
    fun `repository persists gates and keeps an unreadable file aside`() {
        val file = folder.newFile("gates.json").apply { delete() }
        val repository = GateRepository(file)
        repository.save(home)
        repository.save(office)
        repository.save(home.copy(name = "Chez moi"))
        repository.delete("office")
        assertEquals(listOf(home.copy(name = "Chez moi")), GateRepository(file).gates.value)

        file.writeText("pas du json")
        assertTrue(GateRepository(file).gates.value.isEmpty())
        assertEquals("pas du json", folder.root.resolve("gates.json.invalide").readText())
    }

    /** Mini-serveur HTTP : répond 200 sur /open, 401 ailleurs, et retient la dernière requête reçue. */
    private class TinyServer : AutoCloseable {
        private val socket = ServerSocket(0, 5, InetAddress.getLoopbackAddress())
        val port: Int get() = socket.localPort
        @Volatile var lastRequest = ""

        private val thread = thread(isDaemon = true) {
            while (!socket.isClosed) {
                try {
                    socket.accept().use { client ->
                        val input = client.getInputStream().bufferedReader()
                        val head = generateSequence { input.readLine() }.takeWhile { it.isNotEmpty() }.toList()
                        val length = head.firstOrNull { it.startsWith("Content-Length:", ignoreCase = true) }
                            ?.substringAfter(':')?.trim()?.toInt() ?: 0
                        val body = CharArray(length).also { if (length > 0) input.read(it) }.concatToString()
                        lastRequest = (head + body).joinToString("\n")
                        val status = if (head.first().contains(" /open ")) "200 OK" else "401 Unauthorized"
                        client.getOutputStream().write(
                            "HTTP/1.1 $status\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray()
                        )
                    }
                } catch (e: Exception) {
                    // socket fermé : fin du test
                }
            }
        }

        override fun close() {
            socket.close()
            thread.join(2000)
        }
    }

    @Test
    fun `http executor sends method, headers and body and reports the outcome`() {
        val server = TinyServer()
        val base = "http://127.0.0.1:${server.port}"
        server.use {
            val ok = HttpExecutor.execute(GateAction.Http("$base/open", "POST", "X-Key: secret", "{\"on\":true}"))
            assertTrue(ok.message, ok is ActionResult.Success)
            val request = server.lastRequest
            assertTrue(request, request.startsWith("POST /open "))
            assertTrue(request, request.contains("X-Key: secret"))
            assertTrue(request, request.contains("Content-Type: application/json"))
            assertTrue(request, request.endsWith("{\"on\":true}"))

            val get = HttpExecutor.execute(GateAction.Http("$base/open"))
            assertTrue(get.message, get is ActionResult.Success)
            assertTrue(server.lastRequest.startsWith("GET /open "))

            val denied = HttpExecutor.execute(GateAction.Http("$base/denied"))
            assertTrue(denied is ActionResult.Failure)
            assertTrue(denied.message, denied.message.contains("401"))
        }

        val unreachable = HttpExecutor.execute(GateAction.Http("$base/open"))
        assertTrue(unreachable is ActionResult.Failure)
    }
}
