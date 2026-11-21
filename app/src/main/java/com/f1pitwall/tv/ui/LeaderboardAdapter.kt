package com.f1pitwall.tv.ui

import android.graphics.Color
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.f1pitwall.tv.R
import com.f1pitwall.tv.model.DriverLeaderboardItem

class LeaderboardAdapter(
    private var items: List<DriverLeaderboardItem> = emptyList(),
    private val onDriverClick: (DriverLeaderboardItem) -> Unit = {}
) : RecyclerView.Adapter<LeaderboardAdapter.ViewHolder>() {

    var onDriverKeyLeft: ((DriverLeaderboardItem) -> Boolean)? = null
    var onFirstItemKeyUp: (() -> Boolean)? = null

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        return items.getOrNull(position)?.driverNumber?.toLong() ?: position.toLong()
    }

    fun updateData(newItems: List<DriverLeaderboardItem>) {
        if (items.isEmpty()) {
            items = newItems
            notifyDataSetChanged()
            return
        }
        val diffCallback = LeaderboardDiffCallback(items, newItems)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        items = newItems
        diffResult.dispatchUpdatesTo(this)
    }

    class LeaderboardDiffCallback(
        private val oldList: List<DriverLeaderboardItem>,
        private val newList: List<DriverLeaderboardItem>
    ) : DiffUtil.Callback() {
        override fun getOldListSize(): Int = oldList.size
        override fun getNewListSize(): Int = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].driverNumber == newList[newItemPosition].driverNumber
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val o = oldList[oldItemPosition]
            val n = newList[newItemPosition]
            return o.position == n.position &&
                    o.gapToLeader == n.gapToLeader &&
                    o.interval == n.interval &&
                    o.lastLap == n.lastLap &&
                    o.s1 == n.s1 &&
                    o.s2 == n.s2 &&
                    o.s3 == n.s3 &&
                    o.tyreCompound == n.tyreCompound &&
                    o.tyreAge == n.tyreAge
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_driver_leaderboard, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvPosition: TextView = itemView.findViewById(R.id.tvPosition)
        private val viewTeamColor: View = itemView.findViewById(R.id.viewTeamColor)
        private val tvDriverAcronym: TextView = itemView.findViewById(R.id.tvDriverAcronym)
        private val tvTyreBadge: TextView = itemView.findViewById(R.id.tvTyreBadge)
        private val tvGap: TextView = itemView.findViewById(R.id.tvGap)
        private val tvLastLap: TextView = itemView.findViewById(R.id.tvLastLap)
        private val tvS1: TextView = itemView.findViewById(R.id.tvS1)
        private val tvS2: TextView = itemView.findViewById(R.id.tvS2)
        private val tvS3: TextView = itemView.findViewById(R.id.tvS3)

        fun bind(item: DriverLeaderboardItem) {
            tvPosition.text = item.position.toString()
            tvDriverAcronym.text = item.acronym
            tvGap.text = item.gapToLeader
            tvLastLap.text = item.lastLap

            // Sectors color-coding
            when {
                item.position == 1 -> {
                    tvS1.setBackgroundColor(Color.parseColor("#A855F7"))
                    tvS2.setBackgroundColor(Color.parseColor("#A855F7"))
                    tvS3.setBackgroundColor(Color.parseColor("#A855F7"))
                }
                item.position in 2..6 -> {
                    tvS1.setBackgroundColor(Color.parseColor("#22C55E"))
                    tvS2.setBackgroundColor(Color.parseColor("#22C55E"))
                    tvS3.setBackgroundColor(Color.parseColor("#22C55E"))
                }
                else -> {
                    tvS1.setBackgroundColor(Color.parseColor("#EAB308"))
                    tvS2.setBackgroundColor(Color.parseColor("#EAB308"))
                    tvS3.setBackgroundColor(Color.parseColor("#EAB308"))
                }
            }

            // Parse Team Color
            try {
                val colorHex = if (item.teamColor.startsWith("#")) item.teamColor else "#${item.teamColor}"
                viewTeamColor.setBackgroundColor(Color.parseColor(colorHex))
            } catch (e: Exception) {
                viewTeamColor.setBackgroundColor(Color.RED)
            }

            // Tyre styling
            val compound = item.tyreCompound.uppercase()
            val shortCompound = when {
                compound.contains("SOFT") -> "S"
                compound.contains("MEDIUM") -> "M"
                compound.contains("HARD") -> "H"
                compound.contains("INTER") -> "I"
                compound.contains("WET") -> "W"
                else -> "?"
            }
            tvTyreBadge.text = "$shortCompound ${item.tyreAge}"
            when (shortCompound) {
                "S" -> {
                    tvTyreBadge.setBackgroundColor(Color.parseColor("#EF4444"))
                    tvTyreBadge.setTextColor(Color.WHITE)
                }
                "M" -> {
                    tvTyreBadge.setBackgroundColor(Color.parseColor("#FACC15"))
                    tvTyreBadge.setTextColor(Color.BLACK)
                }
                "H" -> {
                    tvTyreBadge.setBackgroundColor(Color.parseColor("#FFFFFF"))
                    tvTyreBadge.setTextColor(Color.BLACK)
                }
                "I" -> {
                    tvTyreBadge.setBackgroundColor(Color.parseColor("#10B981"))
                    tvTyreBadge.setTextColor(Color.WHITE)
                }
                "W" -> {
                    tvTyreBadge.setBackgroundColor(Color.parseColor("#0EA5E9"))
                    tvTyreBadge.setTextColor(Color.WHITE)
                }
                else -> {
                    tvTyreBadge.setBackgroundColor(Color.GRAY)
                    tvTyreBadge.setTextColor(Color.WHITE)
                }
            }

            itemView.setOnClickListener {
                onDriverClick(item)
            }

            itemView.setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN) {
                    when (keyCode) {
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                            onDriverClick(item)
                            return@setOnKeyListener true
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (onDriverKeyLeft?.invoke(item) == true) return@setOnKeyListener true
                        }
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            if (bindingAdapterPosition == 0) {
                                if (onFirstItemKeyUp?.invoke() == true) return@setOnKeyListener true
                            }
                        }
                    }
                }
                false
            }
        }
    }
}
