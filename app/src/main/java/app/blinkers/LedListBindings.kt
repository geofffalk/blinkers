package app.blinkers


import androidx.databinding.BindingAdapter
import androidx.recyclerview.widget.RecyclerView
import app.blinkers.data.Led


@BindingAdapter("app:items")
fun setItems(listView: RecyclerView, items: List<LedViewState>?) {
    items?.let {
        (listView.adapter as LedListAdapter).submitList(items)
    }
}