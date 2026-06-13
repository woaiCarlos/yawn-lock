package com.example.yawnlock.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 状态机测试:模拟用户报告的 bug 场景
 * "设 10s → 中途停止 → 重进 app → 设 20s → 开始"
 *
 * 这个测试只覆盖纯逻辑路径,用来证明:
 * TimerRepository 的状态机在 stop → preview → start 序列里正确转换。
 * 真正的根因在 bubble.hide() 的 attached 标志位管理(见 hotfix 修复)。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TimerRepositoryStateTest {
    @Test
    fun fresh_repo_is_Idle_with_zero_duration() {
        val repo = TimerRepository()
        val s = repo.state.value
        assertEquals(TimerStatus.Idle, s.status)
        assertEquals(0L, s.durationMs)
        assertEquals(0L, s.remainingMs)
    }

    @Test
    fun preview_from_Idle_sets_duration_but_stays_Idle() {
        val repo = TimerRepository()
        repo.preview(10_000L)
        val s = repo.state.value
        assertEquals(TimerStatus.Idle, s.status)
        assertEquals(10_000L, s.durationMs)
        assertEquals(10_000L, s.remainingMs)
    }

    @Test
    fun start_transitions_Idle_to_Counting_with_full_remaining() {
        val repo = TimerRepository()
        repo.preview(10_000L)
        repo.start(10_000L)
        val s = repo.state.value
        assertEquals(TimerStatus.Counting, s.status)
        assertEquals(10_000L, s.durationMs)
        assertEquals(10_000L, s.remainingMs)
    }

    @Test
    fun stop_preserves_duration_and_returns_to_Idle() {
        val repo = TimerRepository()
        repo.preview(10_000L)
        repo.start(10_000L)
        repo.stop()
        val s = repo.state.value
        assertEquals(TimerStatus.Idle, s.status)
        assertEquals(10_000L, s.durationMs)  // preserved
        assertEquals(10_000L, s.remainingMs)  // reset to duration
    }

    @Test
    fun bug_scenario_stop_then_preview_20s_then_start_works() {
        // 重现用户报告的序列:10s → start → stop → preview 20s → start
        val repo = TimerRepository()

        // Step 1: 设 10s
        repo.preview(10_000L)
        // Step 2: start
        repo.start(10_000L)
        assertEquals(TimerStatus.Counting, repo.state.value.status)

        // Step 3: 中途停止
        repo.stop()
        assertEquals(TimerStatus.Idle, repo.state.value.status)
        assertEquals(10_000L, repo.state.value.durationMs)  // 保留

        // Step 4: 改 20s
        repo.preview(20_000L)
        assertEquals(TimerStatus.Idle, repo.state.value.status)
        assertEquals(20_000L, repo.state.value.durationMs)
        assertEquals(20_000L, repo.state.value.remainingMs)

        // Step 5: start
        repo.start(20_000L)
        val s = repo.state.value
        assertEquals(TimerStatus.Counting, s.status)
        assertEquals(20_000L, s.durationMs)
        assertEquals(20_000L, s.remainingMs)
    }

    @Test
    fun preview_while_Active_is_noop() {
        val repo = TimerRepository()
        repo.preview(10_000L)
        repo.start(10_000L)
        // 倒计时进行中:preview 不应改变 state
        repo.preview(99_000L)
        val s = repo.state.value
        assertEquals(TimerStatus.Counting, s.status)
        assertEquals(10_000L, s.durationMs)
    }

    @Test
    fun preview_from_Finished_resets_to_Idle() {
        // 模拟:倒计时跑完进入 Finished,然后用户调时间
        val repo = TimerRepository()
        repo.preview(10_000L)
        repo.start(10_000L)
        repo.onAlarmFired()  // simulate end
        assertEquals(TimerStatus.Finished, repo.state.value.status)

        repo.preview(20_000L)
        val s = repo.state.value
        assertEquals(TimerStatus.Idle, s.status)  // reset from Finished
        assertEquals(20_000L, s.durationMs)
        assertEquals(20_000L, s.remainingMs)
    }
}
