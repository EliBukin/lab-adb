package bukin.labadb

import android.util.Log
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** One background thread for every check, so two checks never overlap and never double-write. */
object Worker {

    private val executor: ExecutorService = Executors.newSingleThreadExecutor { task -> Thread(task, "LabAdb-check") }

    fun execute(task: () -> Unit) {
        executor.execute {
            try {
                task()
            } catch (e: RuntimeException) {
                Log.e(Store.TAG, "Check failed", e)
            }
        }
    }
}
