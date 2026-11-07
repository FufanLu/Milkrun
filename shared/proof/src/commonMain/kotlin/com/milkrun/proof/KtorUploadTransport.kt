package com.milkrun.proof

import com.milkrun.model.RouteId
import com.milkrun.network.SyncHttpException
import com.milkrun.protocol.ApiError
import com.milkrun.protocol.CreateUpload
import com.milkrun.protocol.SyncApi
import com.milkrun.protocol.Traceparent
import com.milkrun.protocol.UploadApi
import com.milkrun.protocol.UploadStatus
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess

public class KtorUploadTransport(private val client: HttpClient, private val token: suspend () -> String) :
    UploadTransport {
    override suspend fun create(route: RouteId, req: CreateUpload): UploadStatus =
        client.post(UploadApi.uploadsPath(route)) {
            common()
            contentType(ContentType.Application.Json)
            setBody(req)
        }.decode()

    override suspend fun putPart(route: RouteId, uploadId: String, part: Int, bytes: ByteArray) {
        client.put(UploadApi.partPath(route, uploadId, part)) {
            common()
            contentType(ContentType.Application.OctetStream)
            setBody(bytes)
        }.check()
    }

    override suspend fun status(route: RouteId, uploadId: String): UploadStatus =
        client.get(UploadApi.uploadPath(route, uploadId)) { common() }.decode()

    override suspend fun complete(route: RouteId, uploadId: String): UploadStatus =
        client.post(UploadApi.completePath(route, uploadId)) { common() }.decode()

    private suspend fun HttpRequestBuilder.common() {
        bearerAuth(token())
        header(SyncApi.PROTOCOL_HEADER, SyncApi.PROTOCOL_VERSION)
        header(Traceparent.HEADER, Traceparent.random().encode())
    }

    private suspend fun HttpResponse.check() {
        if (status.isSuccess()) return
        val error = runCatching { SyncApi.json.decodeFromString(ApiError.serializer(), bodyAsText()) }.getOrNull()
        throw SyncHttpException(status.value, error)
    }

    private suspend inline fun <reified T> HttpResponse.decode(): T {
        check()
        return body()
    }
}
