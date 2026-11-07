package com.milkrun.network

import com.milkrun.model.ClientId
import com.milkrun.model.RouteId
import com.milkrun.protocol.ApiError
import com.milkrun.protocol.ErrorCode
import com.milkrun.protocol.PullResponse
import com.milkrun.protocol.PushRequest
import com.milkrun.protocol.PushResponse
import com.milkrun.protocol.SyncApi
import com.milkrun.protocol.Traceparent
import com.milkrun.sync.SeqGapException
import com.milkrun.sync.SyncTransport
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json

/** A non-2xx answer from the server. [error] is the decoded body when the server sent one. */
public class SyncHttpException(public val status: Int, public val error: ApiError?) :
    RuntimeException("HTTP $status ${error?.code ?: ""} ${error?.message ?: ""}".trim()) {
    /** 426: this build of the app is too old for the server and must be updated. */
    public val upgradeRequired: Boolean get() = status == HttpStatusCode.UpgradeRequired.value
}

/**
 * [SyncTransport] over HTTPS. Retries are *not* configured here: the sync engine owns retry and
 * backoff, and layering a second retry policy underneath would multiply attempts.
 *
 * @param token returns the current bearer token; called per request so a refreshed token is
 *   picked up without rebuilding the client.
 */
public class KtorSyncTransport(private val client: HttpClient, private val token: suspend () -> String) :
    SyncTransport {
    override suspend fun push(route: RouteId, request: PushRequest): PushResponse {
        val response = client.post(SyncApi.pushPath(route)) {
            common()
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        return response.decode()
    }

    override suspend fun pull(route: RouteId, client: ClientId, cursor: Long): PullResponse {
        val response = this.client.get(SyncApi.pullPath(route)) {
            common()
            parameter("client", client.value)
            parameter("cursor", cursor)
        }
        return response.decode()
    }

    private suspend fun HttpRequestBuilder.common() {
        bearerAuth(token())
        header(SyncApi.PROTOCOL_HEADER, SyncApi.PROTOCOL_VERSION)
        // A fresh trace per request: the server logs under it and echoes it in `traceresponse`.
        header(Traceparent.HEADER, Traceparent.random().encode())
    }

    private suspend inline fun <reified T> HttpResponse.decode(): T {
        if (status.isSuccess()) return body()
        val error = runCatching { SyncApi.json.decodeFromString(ApiError.serializer(), bodyAsText()) }.getOrNull()
        val expected = error?.expectedSeq
        if (error?.code == ErrorCode.SEQ_GAP && expected != null) throw SeqGapException(expected)
        throw SyncHttpException(status.value, error)
    }
}

/**
 * Installs what [KtorSyncTransport] relies on: JSON with the protocol's settings, timeouts and the
 * base URL. Platforms pass their engine (`OkHttp` on Android, `Darwin` on iOS, `CIO` on the JVM).
 */
public fun HttpClientConfig<*>.milkrunDefaults(baseUrl: String, timeoutMillis: Long = 15_000) {
    expectSuccess = false
    install(ContentNegotiation) { json(SyncApi.json) }
    install(HttpTimeout) {
        connectTimeoutMillis = 5_000
        requestTimeoutMillis = timeoutMillis
        socketTimeoutMillis = timeoutMillis
    }
    defaultRequest { url(baseUrl) }
}
