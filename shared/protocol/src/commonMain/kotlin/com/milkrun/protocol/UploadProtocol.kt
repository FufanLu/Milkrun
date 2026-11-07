package com.milkrun.protocol

import com.milkrun.model.RouteId
import com.milkrun.model.StopId
import kotlinx.serialization.Serializable

/**
 * Resumable proof-of-delivery uploads, modelled on S3 multipart upload:
 *
 * ```
 * POST /v1/routes/{r}/uploads                 CreateUpload    -> UploadStatus   (idempotent on uploadId)
 * PUT  /v1/routes/{r}/uploads/{id}/parts/{n}  raw bytes       -> 204            (idempotent per part)
 * GET  /v1/routes/{r}/uploads/{id}                            -> UploadStatus   (which parts arrived)
 * POST /v1/routes/{r}/uploads/{id}/complete                   -> UploadStatus   (size + SHA-256 verified)
 * ```
 *
 * The phone chooses the upload id, so a create whose response was lost is simply sent again.
 */
public object UploadApi {
    public const val PART_SIZE: Int = 256 * 1024
    public const val MAX_SIZE: Long = 25L * 1024 * 1024

    public fun uploadsPath(route: RouteId): String = "/v1/routes/${route.value}/uploads"

    public fun uploadPath(route: RouteId, id: String): String = "${uploadsPath(route)}/$id"

    public fun partPath(route: RouteId, id: String, part: Int): String = "${uploadPath(route, id)}/parts/$part"

    public fun completePath(route: RouteId, id: String): String = "${uploadPath(route, id)}/complete"

    public fun partCount(size: Long, partSize: Int = PART_SIZE): Int = ((size + partSize - 1) / partSize).toInt()
}

@Serializable
public data class CreateUpload(
    val uploadId: String,
    val stopId: StopId,
    val size: Long,
    val sha256: String,
    val contentType: String,
)

@Serializable
public data class UploadStatus(
    val uploadId: String,
    val partSize: Int,
    val parts: Int,
    val received: List<Int>,
    val complete: Boolean,
)
