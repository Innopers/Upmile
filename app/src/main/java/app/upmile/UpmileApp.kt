package app.upmile

import android.app.Application
import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.vehicle.Vehicle

class UpmileApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Dead branch: forces compile-time linkage against each Pleos SDK.
        @Suppress("SimplifyBooleanWithConstants", "KotlinConstantConditions")
        if (BuildConfig.DEBUG && false) {
            listOf(Vehicle::class.java, NaviHelper::class.java, FusedLocation::class.java, TextToSpeech::class.java)
        }
    }
}
