package `is`.xyz.mpv.drive

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import `is`.xyz.mpv.MPVActivity // sesuaikan kalau nama package/class berbeda di fork-mu

class DriveBrowserActivity : AppCompatActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var adapter: DriveFileAdapter
    private lateinit var searchInput: EditText
    private lateinit var backBtn: Button
    private lateinit var tabDrive: Button
    private lateinit var tabFav: Button
    private lateinit var subtitleText: TextView
    private lateinit var progress: ProgressBar

    private var currentFolderId = "root"
    private var currentItems: List<DriveFile> = emptyList()
    private val folderStack = ArrayDeque<Pair<String, String>>() // id to name
    private var onFavTab = false

    private val loginLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) loadRoot()
        else Toast.makeText(this, "Perlu login Google Drive dulu", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildLayout())

        adapter = DriveFileAdapter(
            ctx = this,
            onFolderClick = { openFolder(it.id, it.name, pushStack = true) },
            onFileClick = { playFile(it) },
            onStarClick = { TokenStore.toggleFavorite(this, it); adapter.notifyDataSetChanged() },
            onWatchClick = { showWatchMenu(it) }
        )
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter
        attachSwipeBack()

        tabDrive.setOnClickListener { onFavTab = false; folderStack.clear(); ensureLoggedIn { loadRoot() } }
        tabFav.setOnClickListener { showFavoritesTab() }
        backBtn.setOnClickListener { goBack() }

        searchInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                val q = s?.toString()?.trim()?.lowercase() ?: ""
                adapter.items = if (q.isEmpty()) currentItems else currentItems.filter { it.name.lowercase().contains(q) }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        ensureLoggedIn { loadRoot() }
    }

    override fun onBackPressed() {
        if (folderStack.isNotEmpty() && !onFavTab) goBack() else super.onBackPressed()
    }

    // ---------------- Auth ----------------

    private fun ensureLoggedIn(onReady: () -> Unit) {
        if (TokenStore.getValidAccessToken(this) != null || TokenStore.getRefreshToken(this) != null) {
            onReady()
        } else {
            loginLauncher.launch(Intent(this, DriveLoginActivity::class.java))
        }
    }

    /** Balikin access_token valid, refresh diam-diam kalau perlu. Panggil dari background thread. */
    private fun getFreshTokenBlocking(): String? {
        TokenStore.getValidAccessToken(this)?.let { return it }
        val rt = TokenStore.getRefreshToken(this) ?: return null
        return try {
            val result = DriveApi.refreshAccessToken(rt)
            TokenStore.saveAccessTokenOnly(this, result.accessToken, result.expiresIn)
            result.accessToken
        } catch (e: Exception) { null }
    }

    // ---------------- Navigasi folder ----------------

    private fun loadRoot() = openFolder("root", "My Drive", pushStack = false)

    private fun openFolder(folderId: String, folderName: String, pushStack: Boolean) {
        onFavTab = false
        tabDrive.isSelected = true; tabFav.isSelected = false
        if (pushStack) folderStack.addLast(folderId to folderName)
        currentFolderId = folderId
        subtitleText.text = folderName
        backBtn.visibility = if (folderStack.isNotEmpty()) ViewGroup.VISIBLE else ViewGroup.GONE
        progress.visibility = ViewGroup.VISIBLE

        Thread {
            try {
                val token = getFreshTokenBlocking() ?: throw AuthExpiredException()
                val baseFilter = "trashed = false and (mimeType contains 'video/' or mimeType = 'application/vnd.google-apps.folder')"

                val items: List<DriveFile> = if (folderId == "root") {
                    val own = DriveApi.listFiles(token, "'root' in parents and $baseFilter")
                    val shared = DriveApi.listFiles(token, "sharedWithMe = true and $baseFilter")
                    val seen = HashSet<String>()
                    val merged = mutableListOf<DriveFile>()
                    for (f in own) if (seen.add(f.id)) merged.add(f)
                    for (f in shared) if (seen.add(f.id)) merged.add(f.copy(shared = true))
                    merged
                } else {
                    DriveApi.listFiles(token, "'$folderId' in parents and $baseFilter")
                }

                runOnUiThread {
                    currentItems = items
                    searchInput.setText("")
                    adapter.items = items
                    progress.visibility = ViewGroup.GONE
                }
            } catch (e: AuthExpiredException) {
                TokenStore.clearAccessToken(this)
                runOnUiThread {
                    progress.visibility = ViewGroup.GONE
                    ensureLoggedIn { openFolder(folderId, folderName, pushStack = false) }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progress.visibility = ViewGroup.GONE
                    Toast.makeText(this, "Gagal ambil folder: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun goBack() {
        if (folderStack.isNotEmpty()) folderStack.removeLast()
        val prev = folderStack.lastOrNull()
        if (prev != null) openFolder(prev.first, prev.second, pushStack = false)
        else loadRoot()
    }

    // ---------------- Tab Favorit ----------------

    private fun showFavoritesTab() {
        onFavTab = true
        tabDrive.isSelected = false; tabFav.isSelected = true
        folderStack.clear()
        currentFolderId = "__favorites__"
        subtitleText.text = "Favorit"
        backBtn.visibility = ViewGroup.GONE
        currentItems = TokenStore.getFavorites(this)
        searchInput.setText("")
        adapter.items = currentItems
    }

    // ---------------- Playback ----------------

    private fun playFile(file: DriveFile) {
        val url = DriveApi.buildStreamUrl(file.id)
        val intent = Intent(this, MPVActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse(url)
        }
        startActivity(intent)
    }

    private fun showWatchMenu(file: DriveFile) {
        val options = arrayOf("Belum ditonton", "Sedang ditonton", "Selesai")
        val values = arrayOf("none", "watching", "finished")
        val current = values.indexOf(TokenStore.getWatchStatus(this, file.id)).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle(file.name)
            .setSingleChoiceItems(options, current) { dialog, which ->
                TokenStore.setWatchStatus(this, file.id, values[which])
                adapter.notifyDataSetChanged()
                dialog.dismiss()
            }
            .show()
    }

    // ---------------- Swipe kanan buat kembali ----------------

    private fun attachSwipeBack() {
        val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (e1 == null) return false
                val dx = e2.x - e1.x
                val dy = e2.y - e1.y
                if (dx > 100 && kotlin.math.abs(dy) < 80 && !onFavTab && folderStack.isNotEmpty()) {
                    goBack(); return true
                }
                return false
            }
        })
        recycler.setOnTouchListener { v, event -> detector.onTouchEvent(event); false }
    }

    // ---------------- Layout (programmatic, no XML needed) ----------------

    private fun buildLayout(): ViewGroup {
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }

        val tabRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        tabDrive = Button(this).apply { text = "My Drive" }
        tabFav = Button(this).apply { text = "★ Favorit" }
        tabRow.addView(tabDrive, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        tabRow.addView(tabFav, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(tabRow)

        searchInput = EditText(this).apply { hint = "Cari video…" }
        root.addView(searchInput)

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        backBtn = Button(this).apply { text = "← Kembali"; visibility = ViewGroup.GONE }
        subtitleText = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            gravity = Gravity.CENTER
            textSize = 14f
        }
        headerRow.addView(backBtn)
        headerRow.addView(subtitleText)
        root.addView(headerRow)

        progress = ProgressBar(this).apply { visibility = ViewGroup.GONE }
        root.addView(progress)

        recycler = RecyclerView(this)
        root.addView(recycler, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        return root
    }
}
