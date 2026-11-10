package bukin.labadb

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.WindowInsets

/** Placeholder screen until the real app is built. */
class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // With target API 36 the window is always edge to edge on Android 15 and later:
        // keep the content clear of the status bar, navigation bar and camera cutout.
        findViewById<View>(R.id.root).setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsets.CONSUMED
        }
    }
}
