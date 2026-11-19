package com.f1pitwall.tv.ui.home

import android.graphics.Color
import android.graphics.PorterDuff
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.f1pitwall.tv.R
import com.f1pitwall.tv.data.model.CircuitCardItem

class CircuitCarouselAdapter(
    private var itemsList: List<CircuitCardItem> = emptyList(),
    private val onCircuitClick: (CircuitCardItem) -> Unit
) : RecyclerView.Adapter<CircuitCarouselAdapter.ViewHolder>() {

    val items: List<CircuitCardItem> get() = itemsList

    fun updateData(newItems: List<CircuitCardItem>) {
        itemsList = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_circuit_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(itemsList[position])
    }

    override fun getItemCount(): Int = itemsList.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivCircuitSilhouette: ImageView = itemView.findViewById(R.id.ivCircuitSilhouette)
        private val tvCircuitCardName: TextView = itemView.findViewById(R.id.tvCircuitCardName)
        private val tvCircuitCardLocation: TextView = itemView.findViewById(R.id.tvCircuitCardLocation)

        fun bind(circuit: CircuitCardItem) {
            tvCircuitCardName.text = circuit.circuitName
            tvCircuitCardLocation.text = "${circuit.location} • ${circuit.country}"

            // Force all circuit silhouettes to be pure white for high contrast on dark glass cards
            ivCircuitSilhouette.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)

            if (!circuit.circuitImageUrl.isNullOrEmpty()) {
                ivCircuitSilhouette.load(circuit.circuitImageUrl) {
                    crossfade(true)
                }
            } else {
                ivCircuitSilhouette.setImageDrawable(null)
            }

            itemView.setOnClickListener {
                onCircuitClick(circuit)
            }
        }
    }
}
