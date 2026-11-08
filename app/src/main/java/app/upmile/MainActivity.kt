package app.upmile

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import app.upmile.core.route.SegmentFinder
import app.upmile.drive.DriveService
import app.upmile.voice.MusicPlayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var music: MusicPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Temporary: debug readout and triggers until Task 13's screens replace this activity.
        Graph.signals.start() // idempotent; Graph owns the lifetime, so the drive keeps its signals when this closes
        lifecycleScope.launch { Graph.signals.snapshots.collect { Log.d("UpmileSnap", it.toString()) } }
        val m = MusicPlayer(applicationContext).also { music = it }
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    val snapshot by Graph.signals.snapshots.collectAsState()
                    val location by Graph.location.location.collectAsState()
                    val restricted by Graph.restricted.collectAsState()
                    val running by DriveService.running.collectAsState()
                    val record by Graph.store.records.collectAsState()
                    Column {
                        Text("Upmile"); Text(snapshot.toString())
                        Text("위치 $location · 주행 규제 $restricted · 주행 서비스 $running")
                        Text("unseenSessionId ${record.unseenSessionId} · cards ${record.cards.size}")
                        Row {
                            Button({ Graph.voice.say("첫 번째 문장입니다."); Graph.voice.say("두 번째 문장입니다.") }) { Text("음성 테스트") }
                            Button({
                                m.play("music/calm_start.m4a")
                                lifecycleScope.launch { delay(10_000); m.fadeOutAndStop() }
                            }) { Text("음악 테스트") }
                            Button({
                                val route = Graph.routes.route(COMMUTE)
                                val garage = Graph.routes.destinations().first { it.id == COMMUTE }.hasGarage
                                // as in runDemo: every commute segment counts as rehearsed
                                DriveService.start(this@MainActivity, COMMUTE, SegmentFinder.find(route, garage).map { it.id })
                            }) { Text("주행 시작") }
                            Button({ DriveService.stop(this@MainActivity) }) { Text("주행 종료") }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        music?.release()
        super.onDestroy()
    }

    private companion object { const val COMMUTE = "commute" }
}
