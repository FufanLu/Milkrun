package com.milkrun.server

import com.milkrun.model.RouteId
import com.milkrun.protocol.CreateUpload
import com.milkrun.protocol.SyncApi
import com.milkrun.protocol.UploadApi
import com.milkrun.protocol.UploadStatus
import kotlinx.serialization.Serializable
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest

/**
 * Object storage for proof-of-delivery photos, shaped like S3 multipart upload so the production
 * implementation is a thin adapter (CreateMultipartUpload / UploadPart / CompleteMultipartUpload).
 * [FileBlobStore] keeps everything on local disk for development and tests.
 */
public interface BlobStore {
    /** Idempotent: the same [CreateUpload] twice returns the same session. */
    public fun create(route: RouteId, req: CreateUpload): UploadStatus

    /** Idempotent per part: a retried part overwrites itself with identical bytes. */
    public fun putPart(route: RouteId, uploadId: String, part: Int, bytes: ByteArray)

    public fun status(route: RouteId, uploadId: String): UploadStatus?

    /** Assembles the parts, checks size and SHA-256, and makes the object readable. */
    public fun complete(route: RouteId, uploadId: String): UploadStatus

    public fun read(route: RouteId, uploadId: String): ByteArray?
}

public class UploadException(public val code: String, message: String) : RuntimeException(message)

public class FileBlobStore(private val root: Path) : BlobStore {
    @Serializable
    private data class Meta(val req: CreateUpload, val complete: Boolean = false)

    private val json = SyncApi.json

    private fun dir(route: RouteId, id: String): Path {
        require(id.matches(Regex("[A-Za-z0-9-]{8,64}"))) { "bad upload id" }
        return root.resolve(route.value).resolve(id)
    }

    private fun meta(d: Path): Meta? = d.resolve("meta.json").takeIf(Files::exists)
        ?.let { json.decodeFromString(Meta.serializer(), Files.readString(it)) }

    private fun writeMeta(d: Path, m: Meta) {
        val tmp = d.resolve("meta.json.tmp")
        Files.writeString(tmp, json.encodeToString(Meta.serializer(), m))
        Files.move(tmp, d.resolve("meta.json"), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    @Synchronized
    override fun create(route: RouteId, req: CreateUpload): UploadStatus {
        if (req.size !in
            1..UploadApi.MAX_SIZE
        ) {
            throw UploadException("bad_size", "size must be 1..${UploadApi.MAX_SIZE}")
        }
        val d = dir(route, req.uploadId)
        val existing = meta(d)
        if (existing != null) {
            if (existing.req !=
                req
            ) {
                throw UploadException("conflict", "upload ${req.uploadId} exists with different metadata")
            }
        } else {
            Files.createDirectories(d.resolve("parts"))
            writeMeta(d, Meta(req))
        }
        return status(route, req.uploadId)!!
    }

    override fun putPart(route: RouteId, uploadId: String, part: Int, bytes: ByteArray) {
        val d = dir(route, uploadId)
        val m = meta(d) ?: throw UploadException("unknown_upload", "no upload $uploadId")
        val parts = UploadApi.partCount(m.req.size)
        if (part !in 0 until parts) throw UploadException("bad_part", "part $part not in 0..${parts - 1}")
        val expected = if (part ==
            parts - 1
        ) {
            m.req.size - part.toLong() * UploadApi.PART_SIZE
        } else {
            UploadApi.PART_SIZE.toLong()
        }
        if (bytes.size.toLong() !=
            expected
        ) {
            throw UploadException("bad_part", "part $part must be $expected bytes, got ${bytes.size}")
        }
        // Write-then-rename so a request cut off mid-body never leaves a half part behind.
        val tmp = d.resolve("parts").resolve("$part.tmp-${System.nanoTime()}")
        Files.write(tmp, bytes, StandardOpenOption.CREATE_NEW)
        Files.move(
            tmp,
            d.resolve("parts").resolve("$part"),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
    }

    override fun status(route: RouteId, uploadId: String): UploadStatus? {
        val d = dir(route, uploadId)
        val m = meta(d) ?: return null
        val parts = UploadApi.partCount(m.req.size)
        val received = (0 until parts).filter { Files.exists(d.resolve("parts").resolve("$it")) }
        return UploadStatus(uploadId, UploadApi.PART_SIZE, parts, received, m.complete)
    }

    @Synchronized
    override fun complete(route: RouteId, uploadId: String): UploadStatus {
        val d = dir(route, uploadId)
        val m = meta(d) ?: throw UploadException("unknown_upload", "no upload $uploadId")
        if (m.complete) return status(route, uploadId)!!
        val st = status(route, uploadId)!!
        val missing = (0 until st.parts) - st.received.toSet()
        if (missing.isNotEmpty()) throw UploadException("incomplete", "missing parts $missing")
        val digest = MessageDigest.getInstance("SHA-256")
        val tmp = d.resolve("object.tmp")
        Files.newOutputStream(tmp).use { out ->
            for (p in 0 until st.parts) {
                val bytes = Files.readAllBytes(d.resolve("parts").resolve("$p"))
                digest.update(bytes)
                out.write(bytes)
            }
        }
        val sha = digest.digest().joinToString("") { "%02x".format(it) }
        if (sha != m.req.sha256) {
            Files.delete(tmp)
            throw UploadException("hash_mismatch", "assembled SHA-256 $sha does not match ${m.req.sha256}")
        }
        Files.move(tmp, d.resolve("object"), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        writeMeta(d, m.copy(complete = true))
        return status(route, uploadId)!!
    }

    override fun read(route: RouteId, uploadId: String): ByteArray? =
        dir(route, uploadId).resolve("object").takeIf(Files::exists)?.let(Files::readAllBytes)
}
