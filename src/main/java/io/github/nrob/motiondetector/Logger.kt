package io.github.nrob.motiondetector

/**
 * Platform-independent logging interface.
 *
 * Enables MotionController to log without depending on Android Log,
 * console output, or any specific logging framework.
 *
 * Implementation examples:
 * - Android: delegate to android.util.Log
 * - Console: println with formatting
 * - Production: Timber, Logback, SLF4J, etc.
 *
 * The setContext() method allows attaching metadata (user ID, session, etc.)
 * for correlation in production logging systems like Sentry or Datadog.
 */
interface Logger {
    fun d(tag: String, message: String)
    fun w(tag: String, message: String, throwable: Throwable? = null)
    fun e(tag: String, message: String, throwable: Throwable? = null)
    fun setContext(key: String, value: Any)
}