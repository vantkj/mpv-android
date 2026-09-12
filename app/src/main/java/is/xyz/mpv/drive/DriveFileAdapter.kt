package `is`.xyz.mpv.drive

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class DriveFileAdapter(
    private val ctx: Context,
    private val onFolderClick: (DriveFile) -> Unit,
    private val onFileClick: (DriveFile) -> Unit,
    private val onStarClick: (DriveFile) -> Unit,
    private val onWatchClick: (DriveFile) -> Unit
) : RecyclerView.Adapter<DriveFileAdapter.VH>() {

    var items: List<DriveFile> = emptyList()
        set(value) { field = value; notifyDataSetChanged() }

    class VH(val root: LinearLayout) : RecyclerView.ViewHolder(root) {
        val name = TextView(root.context)
        val sub = TextView(root.context)
        val icon = ImageView(root.context)
        val actionBtn = ImageView(root.context)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val density = parent.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        val vh = VH(row)

        vh.icon.layoutParams = LinearLayout.LayoutParams(dp(28), dp(28)).apply { marginEnd = dp(12) }
        row.addView(vh.icon)

        val textCol = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        vh.name.apply { setTypeface(typeface, Typeface.BOLD); textSize = 15f }
        vh.sub.apply { textSize = 12f; setTextColor(Color.GRAY) }
        textCol.addView(vh.name)
        textCol.addView(vh.sub)
        row.addView(textCol)

        vh.actionBtn.layoutParams = LinearLayout.LayoutParams(dp(32), dp(32)).apply { marginStart = dp(8) }
        vh.actionBtn.setPadding(dp(4), dp(4), dp(4), dp(4))
        row.addView(vh.actionBtn)

        return vh
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val f = items[position]
        holder.name.text = f.name

        if (f.isFolder) {
            holder.icon.setImageResource(android.R.drawable.ic_menu_recent_history) // ganti dgn drawable folder milikmu
            holder.sub.text = "Folder" + if (f.shared) " · Dibagikan" else ""

            val fav = TokenStore.isFavorite(ctx, f.id)
            holder.actionBtn.setImageResource(
                if (fav) android.R.drawable.btn_star_big_on else android.R.drawable.btn_star_big_off
            )
            holder.actionBtn.setOnClickListener { onStarClick(f) }
            holder.root.setOnClickListener { onFolderClick(f) }
        } else {
            holder.icon.setImageResource(android.R.drawable.ic_menu_slideshow) // ganti dgn drawable video milikmu
            holder.sub.text = humanSize(f.size) + if (f.shared) " · Dibagikan" else ""

            val status = TokenStore.getWatchStatus(ctx, f.id)
            holder.actionBtn.setImageResource(
                when (status) {
                    "finished" -> android.R.drawable.checkbox_on_background
                    "watching" -> android.R.drawable.ic_menu_recent_history
                    else -> android.R.drawable.checkbox_off_background
                }
            )
            holder.actionBtn.setOnClickListener { onWatchClick(f) }
            holder.root.setOnClickListener { onFileClick(f) }
        }
    }

    override fun getItemCount() = items.size
}
