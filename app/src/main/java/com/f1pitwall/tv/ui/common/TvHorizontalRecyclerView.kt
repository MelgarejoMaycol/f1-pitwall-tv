package com.f1pitwall.tv.ui.common

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.recyclerview.widget.RecyclerView

/**
 * Custom horizontal RecyclerView optimized for Android TV remote (D-pad) navigation.
 * - Prevents unexpected focus jumping out to other sections when pressing LEFT at item 0 or RIGHT at the last item.
 * - Smoothly delegates UP and DOWN navigation to adjacent vertical sections.
 * - Auto-scrolls so focused items remain comfortably visible on TV screens.
 */
class TvHorizontalRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {

    init {
        isFocusable = false
        descendantFocusability = FOCUS_AFTER_DESCENDANTS
        setHasFixedSize(true)
    }

    override fun focusSearch(focused: View, direction: Int): View? {
        val nextFocus = super.focusSearch(focused, direction)

        if (direction == View.FOCUS_LEFT) {
            val itemView = findContainingItemView(focused) ?: focused
            val pos = getChildAdapterPosition(itemView)
            if (pos == 0 && (nextFocus == null || !isChildOfThis(nextFocus))) {
                return focused // Trap focus cleanly on the first item
            }
        } else if (direction == View.FOCUS_RIGHT) {
            val total = adapter?.itemCount ?: 0
            val itemView = findContainingItemView(focused) ?: focused
            val pos = getChildAdapterPosition(itemView)
            if (pos >= total - 1 && (nextFocus == null || !isChildOfThis(nextFocus))) {
                return focused // Trap focus cleanly on the last item
            }
        }

        return nextFocus
    }

    override fun requestChildFocus(child: View?, focused: View?) {
        super.requestChildFocus(child, focused)
        if (child != null) {
            val pos = getChildAdapterPosition(child)
            if (pos != NO_POSITION) {
                smoothScrollToPosition(pos)
            }
        }
    }

    private fun isChildOfThis(view: View): Boolean {
        var p = view.parent
        while (p != null) {
            if (p == this) return true
            p = p.parent
        }
        return false
    }
}
