package com.example.drinksync.notifications

import com.example.drinksync.HydrationStore
import com.example.drinksync.MemoryPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.LocalDate

class HydrationNotifierTest {
    private val store = HydrationStore(MemoryPrefs(), today = { LocalDate.of(2026, 8, 15) })

    @Test
    fun reportsReachedMilestonesOnce() {
        store.setDailyGoal(64)
        store.addIntake(33)
        assertEquals(listOf(25, 50), HydrationNotifier.pendingMilestones(store.snapshot))
        store.markNotificationTrigger(25)
        store.markNotificationTrigger(50)
        assertEquals(emptyList<Int>(), HydrationNotifier.pendingMilestones(store.snapshot))
        store.addIntake(31)
        assertEquals(listOf(100), HydrationNotifier.pendingMilestones(store.snapshot))
    }

    @Test
    fun silentWhenDisabled() {
        store.setNotifications(false)
        store.addIntake(100)
        assertEquals(emptyList<Int>(), HydrationNotifier.pendingMilestones(store.snapshot))
    }

    @Test
    fun everyMilestoneHasAMessage() {
        HydrationNotifier.MILESTONES.forEach { assertNotNull(HydrationNotifier.message(it)) }
    }
}
