package com.example.drinksync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HydrationStoreTest {
    private fun store(
        today: LocalDate = LocalDate.of(2026, 8, 15),
        lastReset: LocalDate = today,
        currentIntake: Int = 0,
        totalIntake: Int = 0,
        dailyGoal: Int = 64,
        hasHitGoal: Boolean = false,
        streak: Int = 0,
    ): HydrationStore {
        val memory = MemoryPrefs()
        memory.saveInt(HydrationKeys.CURRENT_INTAKE, currentIntake)
        memory.saveInt(HydrationKeys.TOTAL_INTAKE, totalIntake)
        memory.saveInt(HydrationKeys.DAILY_GOAL, dailyGoal)
        memory.saveBoolean(HydrationKeys.HAS_HIT_GOAL, hasHitGoal)
        memory.saveInt(HydrationKeys.STREAK, streak)
        memory.saveString(HydrationKeys.LAST_RESET_DATE, lastReset.toString())
        return HydrationStore(memory, today = { today })
    }

    @Test
    fun setIntakePersistsCurrentAndAdjustsTotal() {
        val store = store(currentIntake = 8, totalIntake = 20)
        store.setIntake(20)
        assertEquals(20, store.snapshot.currentIntake)
        assertEquals(32, store.snapshot.totalIntake)
        assertEquals(20, store.prefs.getInt(HydrationKeys.CURRENT_INTAKE, -1))
        assertEquals(32, store.prefs.getInt(HydrationKeys.TOTAL_INTAKE, -1))
    }

    @Test
    fun addIntakePersistsRunningTotal() {
        val store = store()
        store.addIntake(8)
        assertEquals(8, store.snapshot.currentIntake)
        assertEquals(8, store.prefs.getInt(HydrationKeys.CURRENT_INTAKE, -1))
    }

    @Test
    fun setDailyGoalIsReadableOnNextSnapshot() {
        val store = store(dailyGoal = 64)
        store.setDailyGoal(80)
        assertEquals(80, store.snapshot.dailyGoal)
    }

    @Test
    fun nextDayAfterHitIncrementsStreakAndZerosIntake() {
        val today = LocalDate.of(2026, 8, 16)
        val store = store(
            today = today,
            lastReset = LocalDate.of(2026, 8, 15),
            currentIntake = 70,
            hasHitGoal = true,
            streak = 2,
        )
        store.applyDailyResetIfNeeded()
        assertEquals(0, store.snapshot.currentIntake)
        assertEquals(3, store.snapshot.streak)
        assertFalse(store.snapshot.hasHitGoal)
        assertEquals(today.toString(), store.snapshot.lastResetDate)
    }

    @Test
    fun skippedDaysBreakStreakEvenIfLastOpenDayHitGoal() {
        val today = LocalDate.of(2026, 8, 18)
        val store = store(
            today = today,
            lastReset = LocalDate.of(2026, 8, 15),
            hasHitGoal = true,
            streak = 4,
            currentIntake = 40,
        )
        store.applyDailyResetIfNeeded()
        assertEquals(0, store.snapshot.streak)
        assertEquals(0, store.snapshot.currentIntake)
        assertEquals(today.toString(), store.snapshot.lastResetDate)
    }

    @Test
    fun sameDayDoesNotReset() {
        val store = store(currentIntake = 12, streak = 1)
        store.applyDailyResetIfNeeded()
        assertEquals(12, store.snapshot.currentIntake)
        assertEquals(1, store.snapshot.streak)
    }

    @Test
    fun firstSipUnlocksWhenIntakeBecomesPositive() {
        val store = store()
        assertFalse(store.snapshot.firstSip)
        store.addIntake(8)
        assertTrue(store.snapshot.firstSip)
    }
}
