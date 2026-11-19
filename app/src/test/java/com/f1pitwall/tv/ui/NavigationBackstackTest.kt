package com.f1pitwall.tv.ui

import com.f1pitwall.tv.ui.navigation.NavigationBackstack
import com.f1pitwall.tv.ui.navigation.NavigationDestination
import com.f1pitwall.tv.ui.navigation.NavigationSubSection
import com.f1pitwall.tv.ui.navigation.ScreenState
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class NavigationBackstackTest {

    private lateinit var backstack: NavigationBackstack

    @Before
    fun setUp() {
        backstack = NavigationBackstack(maxDepth = 10)
    }

    @Test
    fun testEmptyBackstack() {
        assertTrue(backstack.isEmpty)
        assertEquals(0, backstack.size)
        assertNull(backstack.peek())
        assertNull(backstack.pop())
    }

    @Test
    fun testPushAndPopSingleItem() {
        val state = ScreenState(
            destination = NavigationDestination.HOME,
            subSection = NavigationSubSection.DEFAULT,
            focusedViewId = 101
        )
        backstack.push(state)

        assertFalse(backstack.isEmpty)
        assertEquals(1, backstack.size)
        assertEquals(state, backstack.peek())

        val popped = backstack.pop()
        assertEquals(state, popped)
        assertTrue(backstack.isEmpty)
        assertEquals(0, backstack.size)
    }

    @Test
    fun testLIFOOrdering() {
        val stateHome = ScreenState(NavigationDestination.HOME)
        val stateArchive = ScreenState(NavigationDestination.ARCHIVE)
        val stateSettings = ScreenState(NavigationDestination.SETTINGS)

        backstack.push(stateHome)
        backstack.push(stateArchive)
        backstack.push(stateSettings)

        assertEquals(3, backstack.size)
        assertEquals(stateSettings, backstack.pop())
        assertEquals(stateArchive, backstack.pop())
        assertEquals(stateHome, backstack.pop())
        assertTrue(backstack.isEmpty)
    }

    @Test
    fun testDuplicateConsecutiveStateDeduplication() {
        val stateHomeFirst = ScreenState(
            destination = NavigationDestination.HOME,
            subSection = NavigationSubSection.DEFAULT,
            focusedViewId = 101,
            focusedItemPosition = 0
        )
        val stateHomeUpdated = ScreenState(
            destination = NavigationDestination.HOME,
            subSection = NavigationSubSection.DEFAULT,
            focusedViewId = 101,
            focusedItemPosition = 3
        )

        backstack.push(stateHomeFirst)
        assertEquals(1, backstack.size)

        // Pushing the same destination & subsection should update, not duplicate
        backstack.push(stateHomeUpdated)
        assertEquals(1, backstack.size)

        val popped = backstack.pop()
        assertNotNull(popped)
        assertEquals(3, popped?.focusedItemPosition)
        assertTrue(backstack.isEmpty)
    }

    @Test
    fun testDistinctSubSectionsOnSameDestinationAreRetained() {
        val scheduleState = ScreenState(
            destination = NavigationDestination.ARCHIVE,
            subSection = NavigationSubSection.SCHEDULE,
            focusedViewId = 201,
            focusedItemPosition = 1
        )
        val resultsState = ScreenState(
            destination = NavigationDestination.ARCHIVE,
            subSection = NavigationSubSection.RESULTS,
            focusedViewId = 202,
            focusedItemPosition = 4
        )

        backstack.push(scheduleState)
        backstack.push(resultsState)

        assertEquals(2, backstack.size)
        assertEquals(NavigationSubSection.RESULTS, backstack.pop()?.subSection)
        assertEquals(NavigationSubSection.SCHEDULE, backstack.pop()?.subSection)
    }

    @Test
    fun testMaxDepthEnforcement() {
        val smallStack = NavigationBackstack(maxDepth = 3)
        smallStack.push(ScreenState(NavigationDestination.HOME, focusedViewId = 1))
        smallStack.push(ScreenState(NavigationDestination.PIT_WALL, focusedViewId = 2))
        smallStack.push(ScreenState(NavigationDestination.ARCHIVE, focusedViewId = 3))
        smallStack.push(ScreenState(NavigationDestination.SETTINGS, focusedViewId = 4))

        // Max depth is 3, so state with focusedViewId = 1 should have been evicted
        assertEquals(3, smallStack.size)
        assertEquals(4, smallStack.pop()?.focusedViewId)
        assertEquals(3, smallStack.pop()?.focusedViewId)
        assertEquals(2, smallStack.pop()?.focusedViewId)
        assertNull(smallStack.pop())
    }

    @Test
    fun testUserFlow_HomeToResultsToGpDetail() {
        // User on Home, clicks "Últimos Resultados" (view 501)
        val homeState = ScreenState(
            destination = NavigationDestination.HOME,
            subSection = NavigationSubSection.DEFAULT,
            focusedViewId = 501
        )
        backstack.push(homeState)

        // User is now in Archive Results, selects GP Meeting at position 2
        val archiveResultsState = ScreenState(
            destination = NavigationDestination.ARCHIVE,
            subSection = NavigationSubSection.RESULTS,
            focusedViewId = 601,
            focusedItemPosition = 2,
            meetingKey = 1250L
        )
        backstack.push(archiveResultsState)

        // User is viewing GP Detail. First Back press: returns to Archive Results
        val backToResults = backstack.pop()
        assertNotNull(backToResults)
        assertEquals(NavigationDestination.ARCHIVE, backToResults?.destination)
        assertEquals(NavigationSubSection.RESULTS, backToResults?.subSection)
        assertEquals(2, backToResults?.focusedItemPosition)
        assertEquals(1250L, backToResults?.meetingKey)

        // Second Back press: returns to Home
        val backToHome = backstack.pop()
        assertNotNull(backToHome)
        assertEquals(NavigationDestination.HOME, backToHome?.destination)
        assertEquals(501, backToHome?.focusedViewId)

        // Third Back press: backstack is empty, ready to exit application
        assertTrue(backstack.isEmpty)
    }

    @Test
    fun testUserFlow_HomeDriverCardToTelemetryToBack() {
        // User on Home in Driver Carousel, card position 4 (e.g. Hamilton #44)
        val homeDriverState = ScreenState(
            destination = NavigationDestination.HOME,
            subSection = NavigationSubSection.DEFAULT,
            focusedViewId = 701,
            focusedItemPosition = 4,
            driverNumber = 44
        )
        backstack.push(homeDriverState)

        // In Pit Wall Telemetry, pressing Back pops home state and restores exact card
        val restored = backstack.pop()
        assertNotNull(restored)
        assertEquals(NavigationDestination.HOME, restored?.destination)
        assertEquals(701, restored?.focusedViewId)
        assertEquals(4, restored?.focusedItemPosition)
        assertEquals(44, restored?.driverNumber)
        assertTrue(backstack.isEmpty)
    }

    @Test
    fun testUserFlow_SettingsEditSourceToCancel() {
        // User in Settings Sources List, selects item 1 to edit
        val settingsListState = ScreenState(
            destination = NavigationDestination.SETTINGS,
            subSection = NavigationSubSection.SOURCES_LIST,
            focusedViewId = 801,
            focusedItemPosition = 1
        )
        backstack.push(settingsListState)

        // Cancelling edit or pressing Back restores sources list at item 1
        val restored = backstack.pop()
        assertNotNull(restored)
        assertEquals(NavigationDestination.SETTINGS, restored?.destination)
        assertEquals(NavigationSubSection.SOURCES_LIST, restored?.subSection)
        assertEquals(1, restored?.focusedItemPosition)
    }

    @Test
    fun testClear() {
        backstack.push(ScreenState(NavigationDestination.HOME))
        backstack.push(ScreenState(NavigationDestination.ARCHIVE))
        assertEquals(2, backstack.size)

        backstack.clear()
        assertEquals(0, backstack.size)
        assertTrue(backstack.isEmpty)
        assertNull(backstack.pop())
    }
}
