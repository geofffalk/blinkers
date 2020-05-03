package app.blinkers


import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import app.blinkers.data.Led
import app.blinkers.databinding.LedItemBinding

class LedListAdapter(private val viewModel: ControllerViewModel) : ListAdapter<Led, LedListAdapter.ViewHolder>(LedDiffCallback()) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder.from(parent)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(viewModel, item)
    }

    class ViewHolder private constructor(val binding: LedItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(viewModel: ControllerViewModel, item: Led) {

            binding.viewmodel = viewModel
            binding.led = item
            binding.executePendingBindings()
        }

        companion object {
            fun from(parent: ViewGroup): ViewHolder {
                val layoutInflater = LayoutInflater.from(parent.context)
                val binding = LedItemBinding.inflate(layoutInflater, parent, false)

                return ViewHolder(binding)
            }
        }
    }
}

class LedDiffCallback : DiffUtil.ItemCallback<Led>() {
    override fun areItemsTheSame(oldItem: Led, newItem: Led): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: Led, newItem: Led): Boolean {
        return oldItem == newItem
    }
}