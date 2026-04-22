package com.example.e_inkbiketag

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ImageListAdapter(
    private val items: List<ImageItem>,
    private val onItemSelected: (ImageItem) -> Unit
) : RecyclerView.Adapter<ImageListAdapter.ViewHolder>() {

    private var selectedPosition = -1

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameText: TextView = view.findViewById(R.id.imageNameText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_image, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.nameText.text = item.displayName

        // Apply optional color highlight
        val highlightColor = when (item.colorCode) {
            'R' -> 0x44FF0000.toInt() // Faint Red
            'O' -> 0x44FF7F00.toInt() // Faint Orange
            'Y' -> 0x44FFFF00.toInt() // Faint Yellow
            'G' -> 0x4400FF00.toInt() // Faint Green
            'B' -> 0x4480D8FF.toInt() // Faint Blue
            'I' -> 0x444B0082.toInt() // Faint Indigo
            'V' -> 0x44AA00FF.toInt() // Faint Violet
            else -> android.graphics.Color.TRANSPARENT
        }
        holder.nameText.setBackgroundColor(highlightColor)

        // Highlight the selected item
        holder.itemView.isSelected = (position == selectedPosition)
        if (position == selectedPosition) {
            val isDarkTheme = (holder.itemView.context.resources.configuration.uiMode and
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK) == 
                    android.content.res.Configuration.UI_MODE_NIGHT_YES
            
            val selectionColor = if (isDarkTheme) {
                0x44AAAAAA.toInt() // Light grey for dark mode
            } else {
                0x44444444.toInt() // Dark grey for light mode
            }
            holder.itemView.setBackgroundColor(selectionColor)
        } else {
            holder.itemView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }

        holder.itemView.setOnClickListener {
            val previous = selectedPosition
            selectedPosition = holder.bindingAdapterPosition
            notifyItemChanged(previous)
            notifyItemChanged(selectedPosition)
            onItemSelected(item)
        }
    }

    override fun getItemCount() = items.size
}