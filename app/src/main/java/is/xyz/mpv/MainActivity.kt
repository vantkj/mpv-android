package `is`.xyz.mpv

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import `is`.xyz.mpv.drive.DriveBrowserActivity
import `is`.xyz.mpv.drive.CrashCatcher

class MainActivity : AppCompatActivity(R.layout.activity_main) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashCatcher.install(this)

        supportActionBar?.setTitle(R.string.mpv_activity)

        // The original plan was to have the file/doc picker live as fragments
        // under here but that requires refactoring I'm really not willing to figure out now.
        // ~sfan5, 2022-06-30

        if (savedInstanceState == null) {
            with (supportFragmentManager.beginTransaction()) {
                setReorderingAllowed(true)
                add(R.id.fragment_container_view, MainScreenFragment())
                commit()
            }
        }

        val trace = CrashCatcher.getLastCrash(this)
        if (trace != null) {
            val scroll = android.widget.ScrollView(this)
            val tv = android.widget.TextView(this).apply {
                text = trace
                textSize = 11f
                setPadding(24, 24, 24, 24)
                setTextIsSelectable(true)
            }
            scroll.addView(tv)
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("App sempat crash — ini detailnya")
                .setView(scroll)
                .setPositiveButton("Copy") { _, _ ->
                    val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("crash", trace))
                    android.widget.Toast.makeText(this, "Disalin ke clipboard", android.widget.Toast.LENGTH_SHORT).show()
                    CrashCatcher.clear(this)
                }
                .setNegativeButton("Tutup") { _, _ -> CrashCatcher.clear(this) }
                .setCancelable(false)
                .show()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_drive_browser) {
            startActivity(Intent(this, DriveBrowserActivity::class.java))
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
