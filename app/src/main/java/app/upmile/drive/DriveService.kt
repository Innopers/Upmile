package app.upmile.drive

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import app.upmile.BuildConfig
import app.upmile.Graph
import app.upmile.core.drive.DriveLoop
import app.upmile.core.drive.DriveTracker
import app.upmile.core.record.Record
import app.upmile.core.route.SegmentFinder
import app.upmile.core.vehicle.Gear
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** The drive is over once its arrival is in the record (even if the UI already cleared unseenSessionId) and the car is in P. */
internal fun driveFinished(record: Record, sessionId: String, gear: Gear): Boolean =
    sessionId in record.processedSessionIds && gear == Gear.P

/**
 * One drive: ticks [DriveLoop] at a fixed 5 Hz with the latest vehicle snapshot and position, then stops itself once
 * the arrival is saved and the car is in P. A second start while a drive runs is ignored.
 */
class DriveService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var drive: Job? = null
    private val logged = mutableSetOf<String>() // touched by the drive coroutine only

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        promote() // every startForegroundService must be answered, even an ignored one
        if (drive?.isActive == true) { Log.i(TAG, "drive already running; start ignored"); return START_NOT_STICKY }
        val destinationId = intent?.getStringExtra(EXTRA_DESTINATION)
        if (destinationId == null) { stopSelf(); return START_NOT_STICKY }
        val rehearsed = intent.getStringArrayExtra(EXTRA_REHEARSED)?.toSet().orEmpty()
        // Main thread: the SDK adapters register their listeners here. Restarting the vehicle signals picks up a
        // CAR_SPEED grant made after app start.
        Graph.signals.stop()
        Graph.signals.start()
        Graph.location.start()
        _running.value = true
        drive = scope.launch { run(destinationId, rehearsed) }
        return START_NOT_STICKY
    }

    private suspend fun run(destinationId: String, rehearsed: Set<String>) {
        val sessionId = UUID.randomUUID().toString()
        val loop = try {
            val route = Graph.routes.route(destinationId)
            val segments = SegmentFinder.find(route, Graph.routes.destinations().first { it.id == destinationId }.hasGarage)
            Log.i(TAG, "drive $sessionId to $destinationId: ${segments.map { it.id }}, rehearsed $rehearsed")
            DriveLoop(DriveTracker(route, segments), Graph.voice, Graph.store, Graph.regions, sessionId, rehearsed, System::currentTimeMillis)
        } catch (e: Exception) {
            Log.e(TAG, "cannot start drive to $destinationId", e)
            stopSelf()
            return
        }
        var n = 0L
        while (scope.isActive) {
            val s = Graph.signals.snapshots.value
            val p = Graph.location.location.value
            try { loop.tick(s, p) } catch (e: Exception) { if (logged.add("tick")) Log.w(TAG, "tick failed", e) }
            loop.lastError?.let { if (logged.add("save")) Log.w(TAG, "record save failed; retrying every tick", it) }
            // debug builds only: the app is hidden in D, so this is how a test drive is followed
            if (BuildConfig.DEBUG && n++ % STATUS_EVERY == 0L) Log.d(TAG, "tick gear=${s.gear} v=${s.speedMps} seated=${s.driverSeated} at=$p")
            if (driveFinished(Graph.store.records.value, sessionId, s.gear)) {
                Log.i(TAG, "drive $sessionId saved; stopping")
                stopSelf()
                return
            }
            delay(TICK_MS)
        }
    }

    private fun promote() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "주행 안내", NotificationManager.IMPORTANCE_LOW))
        // Without POST_NOTIFICATIONS the notification is just not shown; the service still runs.
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("Upmile 주행 안내 중")
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        else startForeground(NOTIFICATION_ID, n)
    }

    override fun onDestroy() {
        scope.cancel()
        Graph.location.stop()
        _running.value = false
        super.onDestroy()
    }

    companion object {
        private const val TAG = "DriveService"
        private const val EXTRA_DESTINATION = "destinationId"
        private const val EXTRA_REHEARSED = "rehearsedIds"
        private const val CHANNEL = "drive"
        private const val NOTIFICATION_ID = 1
        private const val TICK_MS = 200L       // fixed 5 Hz
        private const val STATUS_EVERY = 25L   // one status log line per 5 s

        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running.asStateFlow()

        fun start(context: Context, destinationId: String, rehearsedIds: Collection<String>) {
            context.startForegroundService(
                Intent(context, DriveService::class.java)
                    .putExtra(EXTRA_DESTINATION, destinationId)
                    .putExtra(EXTRA_REHEARSED, rehearsedIds.toTypedArray())
            )
        }

        fun stop(context: Context) { context.stopService(Intent(context, DriveService::class.java)) }
    }
}
