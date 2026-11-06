package com.milkrun.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.milkrun.android.ui.route.RouteScreen
import com.milkrun.android.ui.stop.StopScreen
import com.milkrun.android.ui.theme.LocalMilkrunColors
import com.milkrun.android.ui.theme.MilkrunTheme
import com.milkrun.android.ui.theme.Scheme
import com.milkrun.android.ui.theme.Space
import com.milkrun.model.StopId
import com.milkrun.presentation.MilkrunSession
import com.milkrun.sync.RecordResult
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = application as MilkrunApp
        setContent {
            val ctx = LocalContext.current
            var sun by remember { mutableStateOf(Prefs.sunMode(ctx)) }
            MilkrunTheme(if (sun) Scheme.SUN else null) {
                val session by produceState<Result<MilkrunSession>?>(null) {
                    value = runCatching { app.session.await() }
                }
                when (val s = session) {
                    null -> Splash("Loading your route")

                    else -> s.fold(
                        onSuccess = {
                            App(it, sun) {
                                sun = !sun
                                Prefs.setSunMode(ctx, sun)
                            }
                        },
                        onFailure = {
                            Splash("Couldn't reach the station to load a route. Check signal and reopen the app.")
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun Splash(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            message,
            style = MaterialTheme.typography.bodyLarge,
            color = LocalMilkrunColors.current.ink,
            modifier = Modifier.padding(Space.xl),
        )
    }
}

@Composable
private fun App(session: MilkrunSession, sun: Boolean, onToggleSun: () -> Unit) {
    val nav = rememberNavController()
    val model = session.screen
    val scope = rememberCoroutineScope()
    LifecycleEventEffect(Lifecycle.Event.ON_START) { model.refresh() }

    NavHost(nav, startDestination = "route") {
        composable("route") {
            val state by model.state.collectAsState()
            var refreshing by remember { mutableStateOf(false) }
            RouteScreen(
                state = state,
                sunMode = sun,
                onToggleSun = onToggleSun,
                onOpenStop = { nav.navigate("stop/${it.value}") },
                onArrive = { id ->
                    scope.launch {
                        model.arrive(id)
                        nav.navigate("stop/${id.value}")
                    }
                },
                onDismiss = model::dismiss,
                onRefresh = {
                    refreshing = true
                    model.refresh()
                    scope.launch {
                        kotlinx.coroutines.delay(600)
                        refreshing = false
                    }
                },
                refreshing = refreshing,
            )
        }
        composable("stop/{id}") { entry ->
            val id = StopId(entry.arguments?.getString("id").orEmpty())
            val detail by remember(id) { model.stop(id) }.collectAsState(initial = null)
            val snackbar = remember { SnackbarHostState() }
            val d = detail ?: return@composable
            fun report(r: RecordResult, done: Boolean = false) {
                model.refusal(r, d.card.number)?.let { scope.launch { snackbar.showSnackbar(it) } }
                if (done && r is RecordResult.Recorded) nav.popBackStack()
            }
            StopScreen(
                state = d,
                snackbar = snackbar,
                onBack = { nav.popBackStack() },
                onArrive = { scope.launch { report(model.arrive(id)) } },
                onScan = { p -> scope.launch { report(model.scan(id, p)) } },
                onDeliver = { h ->
                    scope.launch { report(model.deliver(id, d.parcels.map { it.id }, h), done = true) }
                },
                onAttempt = { r -> scope.launch { report(model.attempt(id, r), done = true) } },
                onSaveNote = { t -> scope.launch { report(model.setNote(id, t)) } },
            )
            LaunchedEffect(id) { }
        }
    }
}
