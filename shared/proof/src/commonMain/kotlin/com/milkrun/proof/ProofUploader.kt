package com.milkrun.proof

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.milkrun.database.MilkrunDatabase
import com.milkrun.model.RouteId
import com.milkrun.model.StopId
import com.milkrun.protocol.CreateUpload
import com.milkrun.protocol.UploadApi
import com.milkrun.protocol.UploadStatus
import com.milkrun.sync.Backoff
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okio.FileSystem
import okio.HashingSink
import okio.Path
import okio.Path.Companion.toPath
import okio.blackholeSink
import okio.buffer
import okio.use
import kotlin.random.Random

/** How the uploader reaches the server; [KtorUploadTransport] in production, fakes in tests. */
public interface UploadTransport {
    public suspend fun create(route: RouteId, req: CreateUpload): UploadStatus

    public suspend fun putPart(route: RouteId, uploadId: String, part: Int, bytes: ByteArray)

    public suspend fun status(route: RouteId, uploadId: String): UploadStatus

    public suspend fun complete(route: RouteId, uploadId: String): UploadStatus
}

public data class UploadJob(
    val uploadId: String,
    val stopId: StopId,
    val size: Long,
    val sha256: String,
    val partsSent: Int,
    val parts: Int,
    val done: Boolean,
)

/**
 * Resumable proof-of-delivery uploads. A photo is hashed and queued in SQLite at capture time, so
 * a killed app resumes where it left off. Draining asks the server which parts it already holds
 * and sends only the rest, so a 3 MB photo over a flaky link costs at most one extra part per
 * failure, never a restart from byte zero.
 */
public class ProofUploader(
    private val db: MilkrunDatabase,
    private val transport: UploadTransport,
    private val route: RouteId,
    private val wallClock: () -> Long,
    random: Random = Random.Default,
    private val fs: FileSystem = FileSystem.SYSTEM,
    private val io: CoroutineDispatcher = Dispatchers.Default,
) {
    private val mutex = Mutex()
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private val backoff = Backoff(random = random)
    private val ids = random

    public fun jobs(): Flow<List<UploadJob>> = db.uploadQueries.allJobs().asFlow().mapToList(io).map { rows ->
        rows.map {
            UploadJob(
                it.upload_id,
                StopId(it.stop_id),
                it.size,
                it.sha256,
                it.parts_sent.toInt(),
                UploadApi.partCount(it.size),
                it.state == "done",
            )
        }
    }

    /** Hashes [path] and queues it. Returns the job; the caller records the AttachProof mutation. */
    public suspend fun enqueue(stop: StopId, path: String, contentType: String = "image/jpeg"): UploadJob =
        withContext(io) {
            val p = path.toPath()
            val size = fs.metadata(p).size ?: error("no size for $path")
            require(size in 1..UploadApi.MAX_SIZE) { "photo must be 1..${UploadApi.MAX_SIZE} bytes" }
            val sha = HashingSink.sha256(blackholeSink()).use { sink ->
                fs.source(p).buffer().use { it.readAll(sink) }
                sink.hash.hex()
            }
            val id = newId()
            db.uploadQueries.insertJob(id, stop.value, path, size, sha, contentType, wallClock())
            wake.trySend(Unit)
            UploadJob(id, stop, size, sha, 0, UploadApi.partCount(size), false)
        }

    /** One pass over every pending job. Throws on the first transport failure; progress is kept. */
    public suspend fun drainOnce(): Unit = mutex.withLock {
        val pending = withContext(io) { db.uploadQueries.pendingJobs().executeAsList() }
        for (job in pending) {
            val req = CreateUpload(job.upload_id, StopId(job.stop_id), job.size, job.sha256, job.content_type)
            var st = transport.create(route, req) // idempotent: a lost response is just sent again
            if (!st.complete) {
                val have = st.received.toSet()
                val path = job.path.toPath()
                for (part in 0 until st.parts) {
                    if (part in have) continue
                    transport.putPart(route, job.upload_id, part, readPart(path, part, job.size))
                    withContext(io) { db.uploadQueries.markProgress((part + 1).toLong(), job.upload_id) }
                }
                st = transport.complete(route, job.upload_id)
            }
            withContext(io) { db.uploadQueries.markDone(st.parts.toLong(), job.upload_id) }
        }
    }

    public fun requestDrain() {
        wake.trySend(Unit)
    }

    /** Drain loop: on failure sleep with full-jitter backoff; a new photo or regained signal wakes it. */
    public suspend fun run(): Nothing {
        while (true) {
            val sleep = try {
                drainOnce()
                backoff.reset()
                IDLE_MILLIS
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception) {
                backoff.next().inWholeMilliseconds
            }
            withTimeoutOrNull(sleep) { wake.receive() }
        }
    }

    private fun readPart(path: Path, part: Int, size: Long): ByteArray {
        val offset = part.toLong() * UploadApi.PART_SIZE
        val length = minOf(UploadApi.PART_SIZE.toLong(), size - offset).toInt()
        return fs.openReadOnly(path).use { handle ->
            val out = ByteArray(length)
            var read = 0
            while (read < length) {
                val n = handle.read(offset + read, out, read, length - read)
                check(n > 0) { "photo file shrank while uploading" }
                read += n
            }
            out
        }
    }

    private fun newId(): String {
        val alphabet = "0123456789abcdefghjkmnpqrstvwxyz"
        return "pod-" + buildString { repeat(20) { append(alphabet[ids.nextInt(alphabet.length)]) } }
    }

    private companion object {
        const val IDLE_MILLIS = 5 * 60_000L
    }
}
