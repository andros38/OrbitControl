package android.util

import java.util.Base64 as JavaBase64

object Base64 {
    const val NO_WRAP: Int = 2
    fun encodeToString(input: ByteArray, @Suppress("UNUSED_PARAMETER") flags: Int): String =
        JavaBase64.getEncoder().encodeToString(input)
}

object Log {
    fun d(tag: String, message: String): Int = write("DEBUG", tag, message, null)
    fun w(tag: String, message: String): Int = write("WARN", tag, message, null)
    fun e(tag: String, message: String, error: Throwable? = null): Int = write("ERROR", tag, message, error)
    private fun write(level: String, tag: String, message: String, error: Throwable?): Int {
        System.err.println("[$level][$tag] $message")
        error?.printStackTrace(System.err)
        return 0
    }
}
