package com.f1pitwall.tv.ui.navigation

enum class NavigationDestination {
    HOME,
    PIT_WALL,
    ARCHIVE,
    SETTINGS
}

enum class NavigationSubSection {
    DEFAULT,
    SCHEDULE,
    RESULTS,
    TIMING_TOWER,
    TELEMETRY_PANEL,
    SOURCES_LIST,
    EDIT_SOURCE
}

data class ScreenState(
    val destination: NavigationDestination,
    val subSection: NavigationSubSection = NavigationSubSection.DEFAULT,
    val focusedViewId: Int? = null,
    val focusedItemPosition: Int? = null,
    val meetingKey: Long? = null,
    val driverNumber: Int? = null,
    val selectedYear: Int? = null
)

class NavigationBackstack(private val maxDepth: Int = 25) {
    private val stack = ArrayDeque<ScreenState>()

    val size: Int get() = stack.size
    val isEmpty: Boolean get() = stack.isEmpty()
    val history: List<ScreenState> get() = stack.toList()

    fun push(state: ScreenState) {
        // Prevent duplicate consecutive states for the same destination & subsection
        val current = stack.firstOrNull()
        if (current != null && current.destination == state.destination && current.subSection == state.subSection) {
            // Update the top state with the newer focus/selection rather than creating a trap
            stack.removeFirst()
        }
        stack.addFirst(state)
        while (stack.size > maxDepth) {
            stack.removeLast()
        }
    }

    fun pop(): ScreenState? {
        return if (stack.isNotEmpty()) stack.removeFirst() else null
    }

    fun peek(): ScreenState? {
        return stack.firstOrNull()
    }

    fun clear() {
        stack.clear()
    }
}
