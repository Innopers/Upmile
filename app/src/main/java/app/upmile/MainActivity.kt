package app.upmile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import app.upmile.ui.AppNav
import app.upmile.ui.theme.UpmileTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Graph.signals.start() // idempotent; Graph owns the lifetime, so the drive keeps its signals when this closes
        setContent { UpmileTheme { AppNav() } }
    }
}
