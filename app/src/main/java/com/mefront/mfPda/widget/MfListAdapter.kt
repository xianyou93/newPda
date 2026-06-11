package com.mefront.mfPda.widget

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

/**
 * 极简 RecyclerView Adapter 基类，避免每个页面都写一遍。
 */
abstract class MfListAdapter<T>(private val layoutRes: Int) :
    RecyclerView.Adapter<MfListAdapter.VH>() {

    protected val items: MutableList<T> = mutableListOf()

    fun submit(list: List<T>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    fun append(list: List<T>) {
        val start = items.size
        items.addAll(list)
        notifyItemRangeInserted(start, list.size)
    }

    fun clear() {
        items.clear()
        notifyDataSetChanged()
    }

    fun itemAt(position: Int): T? = if (position in items.indices) items[position] else null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(layoutRes, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.bind(item, position)
    }

    override fun getItemCount(): Int = items.size

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        fun bind(any: Any?, position: Int) {
            (any as? Bindable)?.onBind(this, position)
        }
    }

    interface Bindable {
        fun onBind(vh: VH, position: Int)
    }
}
