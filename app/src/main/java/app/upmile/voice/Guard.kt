package app.upmile.voice

import android.util.Log

/** Runs [block]; failures (incl. LinkageError, never VM errors) are logged once per [name] in [logged] and returned to [onFail]. */
internal inline fun guarded(tag: String, logged: MutableSet<String>, name: String, onFail: (Throwable) -> Unit = {}, block: () -> Unit) {
    try { block() } catch (e: Exception) { if (logged.add(name)) Log.w(tag, "$name failed", e); onFail(e) }
    catch (e: LinkageError) { if (logged.add(name)) Log.w(tag, "$name failed", e); onFail(e) }
}
