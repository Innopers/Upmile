package app.upmile

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import app.upmile.core.gleo.GleoIntents
import app.upmile.core.vehicle.Gear
import app.upmile.ui.AppNav
import app.upmile.ui.coverShown
import app.upmile.ui.theme.UpmileTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class MainActivity : ComponentActivity() {
    private var gleoRoute by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Graph.signals.start() // idempotent; Graph owns the lifetime, so the drive keeps its signals when this closes
        setContent { UpmileTheme { AppNav(gleoRoute) { gleoRoute = null } } }
        if (savedInstanceState == null) handleGleo(intent) // a recreated activity must not replay the request
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleGleo(intent)
    }

    /** Gleo launches us with the function name as the action; answer within its 7 s window. */
    private fun handleGleo(intent: Intent) {
        val action = intent.action ?: return
        if (GleoIntents.routeFor(action, emptyMap()) == null) return // a plain launcher start: no 2 s wait
        val params = GleoIntents.params(intent.getStringExtra("ACTION_PARAM")).toMutableMap()
        // The docs also allow parameters as individual extras.
        if ("destination" !in params) intent.getStringExtra("destination")?.let { params["destination"] = it }
        // 체험 모드 always shows the commute, so the reply must describe it.
        if (Graph.demo.value) params["destination"] = "commute"
        lifecycleScope.launch {
            // On a cold start the snapshot can still be UNKNOWN, which would read as "driving".
            val gear = withTimeoutOrNull(GEAR_WAIT_MS) { Graph.signals.snapshots.first { it.gear != Gear.UNKNOWN }.gear }
                ?: Graph.signals.snapshots.value.gear
            val reply = GleoIntents.reply(action, params, parked = !coverShown(Graph.restricted.value, gear)) ?: return@launch
            reply.route?.let { gleoRoute = it }
            val json = reply.json()
            Log.i(TAG, json)
            try {
                sendBroadcast(
                    Intent("ai.pleos.playground.intent.action.APP_ACTION_RESPONSE")
                        .putExtra("packageName", packageName).putExtra("response", json),
                )
            } catch (e: Exception) {
                Log.w(TAG, "Gleo reply not sent", e)
            }
        }
    }

    private companion object {
        const val TAG = "Gleo"
        const val GEAR_WAIT_MS = 2_000L
    }
}
