package com.milkrun.network

import com.milkrun.model.ClientId
import com.milkrun.model.Hlc
import com.milkrun.model.RouteId
import com.milkrun.protocol.PushRequest
import com.milkrun.protocol.SyncApi
import com.milkrun.sync.SeqGapException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class KtorSyncTransportTest {
    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val route = RouteId("R-1")

    private fun transport(engine: MockEngine) =
        KtorSyncTransport(HttpClient(engine) { milkrunDefaults("https://api.example.test") }) { "tok" }

    @Test
    fun sendsAuthProtocolHeaderAndQuery() = runTest {
        val engine = MockEngine { req ->
            assertEquals("Bearer tok", req.headers[HttpHeaders.Authorization])
            assertEquals("1", req.headers[SyncApi.PROTOCOL_HEADER])
            assertEquals("/v1/routes/R-1/pull", req.url.encodedPath)
            assertEquals("c1", req.url.parameters["client"])
            assertEquals("7", req.url.parameters["cursor"])
            respond(
                """{"cursor":9,"lastProcessedSeq":3,"changes":[],"serverHlc":"${Hlc(5, 0, "server").encode()}"}""",
                HttpStatusCode.OK,
                json,
            )
        }
        val pull = transport(engine).pull(route, ClientId("c1"), 7)
        assertEquals(9, pull.cursor)
        assertEquals(3, pull.lastProcessedSeq)
    }

    @Test
    fun seqGapBecomesSeqGapException() = runTest {
        val engine = MockEngine {
            respond("""{"code":"seq_gap","message":"gap","expectedSeq":4}""", HttpStatusCode.Conflict, json)
        }
        val e =
            assertFailsWith<SeqGapException> { transport(engine).push(route, PushRequest(ClientId("c1"), emptyList())) }
        assertEquals(4, e.expectedSeq)
    }

    @Test
    fun oldAppIsToldToUpgrade() = runTest {
        val engine = MockEngine {
            respond("""{"code":"unsupported_protocol","message":"update"}""", HttpStatusCode.UpgradeRequired, json)
        }
        val e = assertFailsWith<SyncHttpException> { transport(engine).pull(route, ClientId("c1"), 0) }
        assertTrue(e.upgradeRequired)
    }

    @Test
    fun serverErrorWithoutBodyIsStillAnException() = runTest {
        val engine = MockEngine { respond("upstream timeout", HttpStatusCode.BadGateway) }
        val e = assertFailsWith<SyncHttpException> { transport(engine).pull(route, ClientId("c1"), 0) }
        assertEquals(502, e.status)
    }
}
