package com.custom.tvlauncher

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AppAdapter(
    private val apps: List<AppInfo>,
    private val onAppClick: (AppInfo) -> Unit,
    private val onAppLongClick: (AppInfo) -> Unit
) : RecyclerView.Adapter<AppAdapter.AppViewHolder>() {

    inner class AppViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tileContainer: View = view.findViewById(R.id.tileContainer)
        val bannerImage: ImageView = view.findViewById(R.id.bannerImage)
        val tvPinBadge: TextView = view.findViewById(R.id.tvPinBadge)
        val appLabel: TextView = view.findViewById(R.id.appLabel)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_tile, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val app = apps[position]
        holder.bannerImage.setImageDrawable(app.bannerOrIcon)
        holder.bannerImage.scaleType = if (app.isBanner) {
            ImageView.ScaleType.CENTER_CROP
        } else {
            ImageView.ScaleType.FIT_CENTER
        }
        holder.tvPinBadge.visibility = if (app.isPinned) View.VISIBLE else View.GONE
        holder.appLabel.text = app.label

        holder.itemView.setOnFocusChangeListener { v, hasFocus ->
            holder.tileContainer.isSelected = hasFocus
            holder.appLabel.visibility = if (hasFocus) View.VISIBLE else View.INVISIBLE
            val scale = if (hasFocus) 1.05f else 1.0f
            v.animate().scaleX(scale).scaleY(scale).setDuration(140).start()
            v.elevation = if (hasFocus) 14f else 2f
        }

        holder.itemView.setOnClickListener {
            onAppClick(app)
        }

        holder.itemView.setOnLongClickListener {
            onAppLongClick(app)
            true
        }
    }

    override fun getItemCount(): Int = apps.size
}
