package com.milkrun.server

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.milkrun.model.RouteId
import io.ktor.server.auth.jwt.JWTPrincipal
import java.util.Date
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/** Who is calling. Drivers are scoped to one route for one shift; dispatchers see their station. */
public sealed interface Caller {
    public val subject: String

    public data class Driver(override val subject: String, val route: RouteId) : Caller

    public data class Dispatcher(override val subject: String) : Caller
}

/**
 * HS256 tokens for local and test use. In production the issuer would be the identity provider
 * (Cognito user pool for drivers, corporate SSO for dispatchers) and the server would verify
 * RS256 against its JWKS; only [JwtConfig.verifier] would change.
 */
public class JwtConfig(
    secret: String,
    public val issuer: String = "milkrun",
    public val audience: String = "milkrun-api",
) {
    init {
        require(secret.length >= MIN_SECRET) { "JWT secret must be at least $MIN_SECRET characters" }
    }

    private val algorithm = Algorithm.HMAC256(secret)

    public val verifier: JWTVerifier = JWT.require(algorithm).withIssuer(issuer).withAudience(audience).build()

    public fun issue(caller: Caller, ttl: Duration = 12.hours, now: Long = System.currentTimeMillis()): String {
        val b = JWT.create()
            .withIssuer(issuer)
            .withAudience(audience)
            .withSubject(caller.subject)
            .withIssuedAt(Date(now))
            .withExpiresAt(Date(now + ttl.inWholeMilliseconds))
        return when (caller) {
            is Caller.Driver -> b.withClaim(ROLE, DRIVER).withClaim(ROUTE, caller.route.value)
            is Caller.Dispatcher -> b.withClaim(ROLE, DISPATCHER)
        }.sign(algorithm)
    }

    public companion object {
        public const val ROLE: String = "role"
        public const val ROUTE: String = "route"
        public const val DRIVER: String = "driver"
        public const val DISPATCHER: String = "dispatcher"
        private const val MIN_SECRET = 32

        public fun caller(p: JWTPrincipal): Caller? {
            val sub = p.payload.subject ?: return null
            return when (p.payload.getClaim(ROLE).asString()) {
                DRIVER -> p.payload.getClaim(ROUTE).asString()?.let { Caller.Driver(sub, RouteId(it)) }
                DISPATCHER -> Caller.Dispatcher(sub)
                else -> null
            }
        }
    }
}
