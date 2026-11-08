package app.upmile

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import app.upmile.vehicle.DEFAULT_INVERT_SEAT
import app.upmile.vehicle.PleosVehicleSignals
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var signals: PleosVehicleSignals? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Temporary: snapshot readout until a later task replaces this screen. Created here until Graph (Task 11).
        val s = PleosVehicleSignals(applicationContext, DEFAULT_INVERT_SEAT)
        signals = s
        s.start()
        lifecycleScope.launch { s.snapshots.collect { Log.d("UpmileSnap", it.toString()) } }
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    val snapshot by s.snapshots.collectAsState()
                    Column { Text("Upmile"); Text(snapshot.toString()) }
                }
            }
        }
    }

    override fun onDestroy() {
        signals?.stop()
        super.onDestroy()
    }
}
