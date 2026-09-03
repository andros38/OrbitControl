package android.content

import java.io.File
import java.util.Properties

/**
 * Minimal, private-data replacement for the Android Context APIs used by the
 * shared Orbit Control code.  All files stay under the current user's home
 * directory and are never synced or shared automatically.
 */
open class Context {
    private val root: File by lazy {
        File(System.getProperty("user.home"), ".orbit-control").apply { mkdirs() }
    }

    open val applicationContext: Context get() = this
    open val filesDir: File get() = File(root, "files").apply { mkdirs() }
    open val cacheDir: File get() = File(root, "cache").apply { mkdirs() }
    open val packageName: String get() = "id.orbitcontrol.windows"

    fun getSharedPreferences(name: String, @Suppress("UNUSED_PARAMETER") mode: Int): SharedPreferences =
        DesktopSharedPreferences(File(root, "preferences/$name.properties"))

    companion object {
        const val MODE_PRIVATE: Int = 0
    }
}

interface SharedPreferences {
    fun getString(key: String, defaultValue: String?): String?
    fun getBoolean(key: String, defaultValue: Boolean): Boolean
    fun getInt(key: String, defaultValue: Int): Int
    fun getLong(key: String, defaultValue: Long): Long
    fun edit(): Editor

    interface Editor {
        fun remove(key: String): Editor
        fun putString(key: String, value: String?): Editor
        fun putBoolean(key: String, value: Boolean): Editor
        fun putInt(key: String, value: Int): Editor
        fun putLong(key: String, value: Long): Editor
        fun apply()
    }
}

private class DesktopSharedPreferences(private val file: File) : SharedPreferences {
    private val lock = Any()

    override fun getString(key: String, defaultValue: String?): String? = value(key) ?: defaultValue
    override fun getBoolean(key: String, defaultValue: Boolean): Boolean = value(key)?.toBooleanStrictOrNull() ?: defaultValue
    override fun getInt(key: String, defaultValue: Int): Int = value(key)?.toIntOrNull() ?: defaultValue
    override fun getLong(key: String, defaultValue: Long): Long = value(key)?.toLongOrNull() ?: defaultValue
    override fun edit(): SharedPreferences.Editor = EditorImpl(this)

    private fun value(key: String): String? = synchronized(lock) { load().getProperty(key) }

    private fun load(): Properties = Properties().also { properties ->
        if (file.isFile) file.inputStream().buffered().use(properties::load)
    }

    private fun save(changes: Map<String, String?>, removals: Set<String>) = synchronized(lock) {
        val properties = load()
        removals.forEach(properties::remove)
        changes.forEach { (key, value) ->
            if (value == null) properties.remove(key) else properties.setProperty(key, value)
        }
        file.parentFile?.mkdirs()
        file.outputStream().buffered().use { output -> properties.store(output, "Orbit Control Windows") }
    }

    private class EditorImpl(private val owner: DesktopSharedPreferences) : SharedPreferences.Editor {
        private val changes = linkedMapOf<String, String?>()
        private val removals = linkedSetOf<String>()
        override fun remove(key: String) = apply { removals += key; changes.remove(key) }
        override fun putString(key: String, value: String?) = apply { changes[key] = value; removals.remove(key) }
        override fun putBoolean(key: String, value: Boolean) = putString(key, value.toString())
        override fun putInt(key: String, value: Int) = putString(key, value.toString())
        override fun putLong(key: String, value: Long) = putString(key, value.toString())
        override fun apply() = owner.save(changes, removals)
    }
}
