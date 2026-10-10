package com.example.launcherswitcher

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.launcherswitcher.databinding.ItemLauncherBinding

class LauncherAdapter(
    private var currentComponent: String?,
    private val onClick: (LauncherInfo) -> Unit
) : ListAdapter<LauncherInfo, LauncherAdapter.ViewHolder>(LauncherDiffCallback()) {

    class ViewHolder(val binding: ItemLauncherBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemLauncherBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val binding = holder.binding

        binding.icon.setImageDrawable(item.icon)
        binding.label.text = item.label
        binding.packageName.text = item.component.packageName

        val isCurrent = item.component.flattenToString() == currentComponent
        binding.currentBadge.visibility = if (isCurrent) android.view.View.VISIBLE else android.view.View.GONE
        binding.root.isActivated = isCurrent
        binding.root.setOnClickListener { onClick(item) }
    }

    fun updateCurrent(component: String?) {
        if (currentComponent != component) {
            currentComponent = component
            notifyDataSetChanged()
        }
    }

    companion object {
        private class LauncherDiffCallback : DiffUtil.ItemCallback<LauncherInfo>() {
            override fun areItemsTheSame(oldItem: LauncherInfo, newItem: LauncherInfo) =
                oldItem.component == newItem.component

            override fun areContentsTheSame(oldItem: LauncherInfo, newItem: LauncherInfo) =
                oldItem == newItem
        }
    }
}
