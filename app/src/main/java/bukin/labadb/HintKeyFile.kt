package bukin.labadb

import android.content.Context
import bukin.labadb.core.HintKey
import java.io.File
import java.io.IOException

/**
 * The hint key: `files/hint.key` in the app's private storage, 64 hex characters. It is copied
 * there over adb (run-as) and the app only ever reads it: no key entry, no export, never logged
 * or shown (the screen says only "Key set: yes/no"), never backed up (see the backup rules).
 */
object HintKeyFile {

    private const val NAME = "hint.key"

    /** 64 hex characters and a newline or two. Anything much bigger is not a key file. */
    private const val MAX_BYTES = 256L

    /** The 32 raw key bytes, or null for "no key". Callers zero the array once they're done. */
    fun read(context: Context): ByteArray? {
        // filesDir also creates the folder, so the run-as copy has somewhere to go.
        val file = File(context.filesDir, NAME)
        return try {
            if (!file.isFile || file.length() > MAX_BYTES) null else HintKey.parse(file.readText(Charsets.US_ASCII))
        } catch (e: IOException) {
            null
        } catch (e: SecurityException) {
            null
        }
    }

    fun isSet(context: Context): Boolean {
        val key = read(context) ?: return false
        key.fill(0)
        return true
    }
}
