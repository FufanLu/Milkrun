package com.milkrun.model

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@Serializable @JvmInline
public value class RouteId(
    public val value: String,
)

@Serializable @JvmInline
public value class StopId(
    public val value: String,
)

@Serializable @JvmInline
public value class ParcelId(
    public val value: String,
)
