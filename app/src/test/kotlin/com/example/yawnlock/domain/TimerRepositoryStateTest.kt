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
    fun preview_while_Counting_resets_to_new_full_duration() {
        // Bug 复现 1:用户设 30s → 启动 → 滚轮改 25s → 期望从 25s 开始
        val repo = TimerRepository()
        repo.preview(30_000L)
        repo.start(30_000L)
        assertEquals(TimerStatus.Counting, repo.state.value.status)

        repo.preview(25_000L)
        val s = repo.state.value
        assertEquals(TimerStatus.Counting, s.status)
        assertEquals(25_000L, s.durationMs)  // 新的总时长
        assertEquals(25_000L, s.remainingMs)  // 重置 = 新总时长
    }

    @Test
    fun preview_while_Counting_handles_smaller_and_larger_values() {
        // Bug 复现 2:用户设 45s → 启动 → 滚轮改 15s → 期望从 15s 开始
        val repo = TimerRepository()
        repo.preview(45_000L)
        repo.start(45_000L)
        repo.preview(15_000L)
        val s = repo.state.value
        assertEquals(TimerStatus.Counting, s.status)
        assertEquals(15_000L, s.durationMs)
        assertEquals(15_000L, s.remainingMs)
    }

    @Test
    fun preview_while_Paused_resets_to_new_full_duration() {
        val repo = TimerRepository()
        repo.preview(30_000L)
        repo.start(30_000L)
        repo.pause()
        assertEquals(TimerStatus.Paused, repo.state.value.status)

        repo.preview(25_000L)
        val s = repo.state.value
        assertEquals(TimerStatus.Paused, s.status)  // 保持暂停
        assertEquals(25_000L, s.durationMs)
        assertEquals(25_000L, s.remainingMs)
    }

    @Test
    fun preview_from_Counting_keeps_status_but_resyncs_deadline() {
        // 保证 fix 之后 deadlineElapsed 跟新 durationMs 同步,否则下次 tick() 会用旧 deadline
        val repo = TimerRepository()
        repo.preview(30_000L)
        repo.start(30_000L)

        repo.preview(25_000L)
        // 反射读 deadlineElapsed
        val deadlineField = repo.javaClass.getDeclaredField("deadlineElapsed").apply { isAccessible = true }
        val now = android.os.SystemClock.elapsedRealtime()
        val newDeadline = deadlineField.getLong(repo)
        // 新 deadline 应该是 now 附近(now+25000,允许 Robolectric 时间精度误差)
        val delta = (newDeadline - now) - 25_000L
        assert(delta in -100L..100L) { "deadlineElapsed 没跟新 durationMs 同步,delta=$delta" }
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
