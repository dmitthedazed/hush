package com.occaecat.hush.jabra.firmware

import org.junit.Assert.*
import org.junit.Test

class JabraFirmwareUpdateStateTest {
    private fun operation(status: FirmwareUpdateStatus) = JabraFirmwareUpdateState(
        11846, "4.7.0", "test-device", status)

    @Test fun transferNeverResumesAfterProcessDeath() {
        val state = operation(FirmwareUpdateStatus.TRANSFERRING)
            .copy(progress = JabraFirmwareProgress(JabraFirmwareStage.COMMITTING, 99)).afterRestart()
        assertEquals(FirmwareUpdateStatus.INTERRUPTED, state.status)
        assertFalse(state.active)
        assertFalse(state.canCancel)
        assertNotNull(state.error)
    }

    @Test fun postCommitRecoveryOnlyWaitsForVersionVerification() {
        assertEquals(FirmwareUpdateStatus.AWAITING_CASE,
            operation(FirmwareUpdateStatus.VERIFYING).afterRestart().status)
        val waiting = operation(FirmwareUpdateStatus.AWAITING_CASE)
        assertEquals(waiting, waiting.afterRestart())
        assertTrue(waiting.active)
        assertFalse(waiting.canCancel)
        assertEquals(FirmwareUpdateStatus.SUCCEEDED, operation(FirmwareUpdateStatus.SUCCEEDED).afterRestart().status)
    }

    @Test fun cancellingIsOnlyOfferedBeforeFirstMutation() {
        val update = operation(FirmwareUpdateStatus.TRANSFERRING)
        assertTrue(update.canCancel)
        assertTrue(update.copy(progress = JabraFirmwareProgress(JabraFirmwareStage.PREPARING, 0)).canCancel)
        for (stage in JabraFirmwareStage.entries.filter { it != JabraFirmwareStage.PREPARING }) {
            assertFalse(update.copy(progress = JabraFirmwareProgress(stage, 0)).canCancel)
        }
    }

    @Test fun onlyExactDeviceAndTargetVersionConfirmInstallation() {
        val update = operation(FirmwareUpdateStatus.AWAITING_CASE)
        assertTrue(update.confirms("TEST-DEVICE", 11846, "4.7.0"))
        assertFalse(update.confirms("other-device", 11846, "4.7.0"))
        assertFalse(update.confirms("test-device", 9421, "4.7.0"))
        assertFalse(update.confirms("test-device", 11846, "4.6.0"))
        assertFalse(update.confirms("test-device", 11846, "4.8.0"))
        assertFalse(update.copy(targetVersion = "invalid").confirms("test-device", 11846, "invalid"))
    }

    @Test fun stalePackagesReinstallsDowngradesAndUnknownVersionsCannotStart() {
        assertTrue(JabraFirmwareUpdateState.isNewer("4.10.0", "4.9.0"))
        assertTrue(JabraFirmwareUpdateState.isNewer("5.0.0", "4.10.5"))
        assertFalse(JabraFirmwareUpdateState.isNewer("4.6.0", "4.6.0"))
        assertFalse(JabraFirmwareUpdateState.isNewer("4.6.0", "4.7.0"))
        assertFalse(JabraFirmwareUpdateState.isNewer("4.6.0", "unknown"))
        assertFalse(JabraFirmwareUpdateState.isNewer("99999999999999.6.0", "4.6.0"))
    }

    @Test fun journalFailureCannotTrapRecoveryBehindAnActiveOperation() {
        for (status in FirmwareUpdateStatus.entries) {
            val recovered = operation(status).journalFailure()
            assertEquals(FirmwareUpdateStatus.INTERRUPTED, recovered.status)
            assertFalse(recovered.active)
            assertFalse(recovered.canCancel)
            assertTrue(recovered.confirms("test-device", 11846, "4.7.0"))
            assertNotNull(recovered.error)
        }
    }
}
