package com.milkrun.android

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleStartEffect
import com.milkrun.presentation.RouteScreenModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Feeds location fixes to the shared arrival detector while the app is on screen.
 *
 * Foreground only, by design: the driver has the route open while driving, and the
 * foreground-only permission is a much smaller ask than background location (no extra Play review,
 * no "allow all the time" prompt). Without permission the app works exactly as before, with an
 * Arrived button.
 *
 * Uses the platform `LocationManager`, so the app does not need Play Services. On API 31+ that
 * means the fused provider; on older phones, GPS.
 */
@Composable
fun ArrivalWatcher(model: RouteScreenModel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var granted by remember { mutableStateOf(hasPermission(ctx)) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) ask.launch(Manifest.permission.ACCESS_FINE_LOCATION) }
    var started by remember { mutableStateOf(false) }
    LifecycleStartEffect(Unit) {
        started = true
        onStopOrDispose { started = false }
    }
    DisposableEffect(granted, started) {
        val lm = ctx.getSystemService(LocationManager::class.java)
        val listener = LocationListener { fix: Location ->
            scope.launch { model.onLocation(fix.latitude, fix.longitude, fix.accuracy.toDouble(), fix.time) }
        }
        val on = granted && started && lm != null && subscribe(lm, listener)
        onDispose { if (on) lm?.removeUpdates(listener) }
    }
    // A parked phone stops reporting fixes; the dwell completes on the clock.
    LaunchedEffect(granted, started) {
        while (granted && started) {
            delay(INTERVAL_MS)
            model.onLocationTick(System.currentTimeMillis())
        }
    }
}

private const val INTERVAL_MS = 5_000L

private fun hasPermission(ctx: Context) =
    ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

@SuppressLint("MissingPermission") // checked by the caller through `granted`
private fun subscribe(lm: LocationManager, listener: LocationListener): Boolean {
    val provider = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && lm.hasProvider(LocationManager.FUSED_PROVIDER) ->
            LocationManager.FUSED_PROVIDER

        lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER

        else -> return false
    }
    lm.requestLocationUpdates(provider, INTERVAL_MS, 0f, listener, Looper.getMainLooper())
    return true
}
