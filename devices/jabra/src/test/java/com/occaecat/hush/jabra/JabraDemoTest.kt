package com.occaecat.hush.jabra

import com.occaecat.hush.gnp.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class JabraDemoTest {
    @Test fun elite10DemoInitializesLikeTheTestedPair() = withDemo(JabraModel.ELITE_10) { backend ->
        val s = backend.state.value
        assertEquals(JabraModel.ELITE_10, s.model)
        assertEquals("4.6.0", s.firmware)
        assertEquals(4, s.noiseCapabilities.ambientMax)
        assertEquals(0, s.noiseCapabilities.ancMax)
        assertEquals(5, s.eq!!.levels.size)
        assertEquals(3, s.batteries.size)
        assertEquals(setOf(JabraButtonContext.MUSIC, JabraButtonContext.INCOMING_CALL, JabraButtonContext.ONGOING_CALL),
            s.buttonsState!!.supportedContexts)
        assertTrue(s.buttonsState!!.slots.all { it.lockReason == null })
        assertEquals(true, s.pauseWhenRemoved)
    }

    @Test fun elite85tDemoAppliesChangesThroughTheBackend() = withDemo(JabraModel.ELITE_85T) { backend ->
        assertEquals(listOf("Right", "Case", "Left"), backend.state.value.batteries.map { it.label })
        assertEquals(true, backend.state.value.pauseWhenRemoved)
        backend.setMode(AmbientMode.AMBIENT)
        backend.setPauseWhenRemoved(false)
        backend.setSleepMinutes(60)
        backend.setEqEnabled(false)
        val s = backend.state.value
        assertEquals(AmbientMode.AMBIENT, s.noise!!.mode)
        assertEquals(false, s.pauseWhenRemoved)
        assertEquals(60, s.sleepMinutes)
        assertEquals(false, s.eq!!.enabled)
    }

    private fun withDemo(model: JabraModel, body: suspend (JabraBackend) -> Unit) = runBlocking {
        val owner = CoroutineScope(currentCoroutineContext() + SupervisorJob(currentCoroutineContext()[Job]))
        val backend = JabraBackend(JabraDemo(model), owner, model)
        try {
            backend.open()
            backend.initialize()
            body(backend)
        } finally { backend.close(); owner.cancel() }
    }
}
