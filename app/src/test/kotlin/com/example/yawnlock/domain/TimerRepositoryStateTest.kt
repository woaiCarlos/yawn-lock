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
    fun stop_preserves_duration_status_becomes_Idle() {
        // 1.0.4 起:stop 回到 Idle 但保留 durationMs,与 onAlarmFired 落地的 Finished 对齐——
        // 用户点 stop 后,Start 按钮 enabled(durationMs>0),无需重新选时间。
        val repo = TimerRepository()
        repo.preview(10_000L)
        repo.start(10_000L)
        repo.stop()
        val s = repo.state.value
        assertEquals(TimerStatus.Idle, s.status)
        assertEquals(10_000L, s.durationMs)  // 保留 preview 时设的时长
        assertEquals(10_000L, s.remainingMs) // 同时 reset remainingMs 让 Start 后从 0 计数
    }

    @Test
    fun stop_while_idle_preserves_previewed_duration() {
        // 用户在 Idle 状态(preview 过后)点 stop(罕见路径,但要稳),durationMs 应该被保留
        val repo = TimerRepository()
        repo.preview(10_000L)
        repo.stop()
        val s = repo.state.value
        assertEquals(TimerStatus.Idle, s.status)
        assertEquals(10_000L, s.durationMs)
        assertEquals(10_000L, s.remainingMs)
    }

    @Test
    fun bug_scenario_stop_then_preview_20s_then_start_works() {
        // 历史背景:这是 fix-countdown-end-cleanup 之前用户报告的 bug 序列
        // ——10s → start → stop → preview 20s → start。当时 stop 把 durationMs 清 0,
        // 必须 preview 才能重新 start;该 case 名字保留为历史印记。
        val repo = TimerRepository()

        // Step 1: 设 10s
        repo.preview(10_000L)
        // Step 2: start
        repo.start(10_000L)
        assertEquals(TimerStatus.Counting, repo.state.value.status)

        // Step 3: 中途停止(1.0.4 起 stop 保留 durationMs,不再清 0)
        repo.stop()
        assertEquals(TimerStatus.Idle, repo.state.value.status)
        assertEquals(10_000L, repo.state.value.durationMs) // 1.0.4:stop 保留
        assertEquals(10_000L, repo.state.value.remainingMs)

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
    fun preview_while_Counting_resets_to_Idle_with_new_duration() {
        // 1.0.3 起:mid-countdown preview = 「我改主意了」,完全清掉倒计时,
        // status=Idle,durationMs=newDuration,要求用户重新点 Start
        val repo = TimerRepository()
        repo.preview(30_000L)
        repo.start(30_000L)
        assertEquals(TimerStatus.Counting, repo.state.value.status)

        repo.preview(25_000L)
        val s = repo.state.value
        assertEquals(TimerStatus.Idle, s.status)  // 必须是 Idle,不再 Counting
        assertEquals(25_000L, s.durationMs)
        assertEquals(25_000L, s.remainingMs)
    }

    @Test
    fun preview_while_Paused_resets_to_Idle_with_new_duration() {
        // 1.0.3 起:Paused 状态下 preview = 清掉暂停,回 Idle+新时长
        val repo = TimerRepository()
        repo.preview(30_000L)
        repo.start(30_000L)
        repo.pause()
        assertEquals(TimerStatus.Paused, repo.state.value.status)

        repo.preview(25_000L)
        val s = repo.state.value
        assertEquals(TimerStatus.Idle, s.status)  // 必须清掉 Paused
        assertEquals(25_000L, s.durationMs)
        assertEquals(25_000L, s.remainingMs)
    }

    @Test
    fun preview_from_Counting_does_not_resync_deadline() {
        // 1.0.3 起:preview 不再保持 Counting,deadlineElapsed 不需要同步(反正下次 start() 会重设)
        // 这个测试现在不该出现 deadlineElapsed 跟 newDuration 一致的情况,
        // 而应该保持 repo 构造时的初值 0L(start 还没跑过 → 没设过 deadline)。
        val repo = TimerRepository()
        repo.preview(30_000L)
        repo.start(30_000L)
        // deadlineElapsed 在 start(30_000)时已被设为 now+30000
        repo.preview(25_000L)
        // 1.0.3 行为:不 resync,所以 deadlineElapsed 还是 start() 设的 now+30000
        val deadlineField = repo.javaClass.getDeclaredField("deadlineElapsed").apply { isAccessible = true }
        val newDeadline = deadlineField.getLong(repo)
        // 验证:deadline 应该是 start 时设的 now+30000 附近,而不是 now+25000
        val now = android.os.SystemClock.elapsedRealtime()
        val deltaFromStart = (newDeadline - now) - 30_000L
        assert(deltaFromStart in -100L..100L) { "deadlineElapsed 不应被 preview 重置,deltaFromStart=$deltaFromStart" }
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
