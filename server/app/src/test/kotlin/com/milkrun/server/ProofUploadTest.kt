package com.milkrun.server

import com.milkrun.database.JvmDatabase
import com.milkrun.model.StopId
import com.milkrun.network.milkrunDefaults
import com.milkrun.proof.KtorUploadTransport
import com.milkrun.proof.ProofUploader
import com.milkrun.protocol.DevSession
import io.ktor.client.call.body
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.post
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.flow.first
import org.junit.jupiter.api.Test
import java.io.IOException
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.random.Random
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A 3 MB photo uploaded through the real routes and FileBlobStore while the HTTP layer drops 15 %
 * of requests before they leave and 15 % of responses after the server acted, and the app is
 * "killed" (a fresh uploader on the same database) after every failure.
 */
class ProofUploadTest {
    private val jwt = JwtConfig("test-secret-that-is-long-enough-for-hs256")

    @Test
    fun aPhotoSurvivesALossyLinkAndRestartsByteForByte() = testApplication {
        val blobs = FileBlobStore(Files.createTempDirectory("blobs"))
        application { milkrun(ServerDeps(PostgresSyncStore(Postgres.dataSource), jwt, devMode = true, blobs = blobs)) }
        val session = createClient { milkrunDefaults("http://localhost") }.post(DevSession.PATH).body<DevSession>()

        val r = Random(7)
        var droppedRequests = 0
        var droppedResponses = 0
        var partPuts = 0
        val lossy = createClientPlugin("Lossy") {
            onRequest { req, _ ->
                if ("/parts/" in req.url.buildString()) partPuts++
                if (r.nextDouble() < 0.15) {
                    droppedRequests++
                    throw IOException("request lost")
                }
            }
            onResponse {
                if (r.nextDouble() < 0.15) {
                    droppedResponses++
                    throw IOException("response lost")
                }
            }
        }
        val http = createClient {
            milkrunDefaults("http://localhost")
            install(lossy)
        }

        val photo = ByteArray(3 * 1024 * 1024 + 12_345).also { Random(1).nextBytes(it) }
        val file = Files.createTempFile("pod", ".jpg").also { Files.write(it, photo) }
        val db = JvmDatabase.create()
        fun app() = ProofUploader(
            db,
            KtorUploadTransport(http) {
                session.driverToken
            },
            session.routeId,
            System::currentTimeMillis,
            Random(r.nextLong()),
        )

        val job = app().enqueue(StopId("S-001"), file.toString())
        var launches = 1
        while (true) {
            val ok = runCatching { app().drainOnce() }.isSuccess
            if (ok) break
            launches++ // the OS killed the app; the next launch starts from what SQLite says
            check(launches < 200) { "never finished" }
        }

        val done = app().jobs().first().single()
        assertTrue(done.done)
        assertEquals(13, done.parts) // 3 MB + 12 KB at 256 KiB parts
        val stored = blobs.read(session.routeId, job.uploadId)!!
        assertContentEquals(photo, stored)
        assertEquals(
            MessageDigest.getInstance("SHA-256").digest(photo).joinToString("") {
                "%02x".format(it)
            },
            job.sha256,
        )
        val stats = (
            "PROOF-UPLOAD parts=${done.parts} partPuts=$partPuts launches=$launches " +
                "droppedRequests=$droppedRequests droppedResponses=$droppedResponses"
            )
        java.io.File("build/proof-upload-stats.txt").writeText(stats + "\n")
        // Resumption means far fewer than (launches x parts) part uploads.
        assertTrue(partPuts < done.parts * 2, "resent too much: $partPuts part PUTs for ${done.parts} parts")
    }
}
