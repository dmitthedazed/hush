package com.occaecat.hush.jabra.firmware

/** Durable installation state. Transfer offsets are deliberately never resumed after process death. */
enum class FirmwareUpdateStatus { TRANSFERRING, AWAITING_CASE, VERIFYING, SUCCEEDED, FAILED, INTERRUPTED }

data class JabraFirmwareUpdateState(
    val productId: Int,
    val targetVersion: String,
    val address: String,
    val status: FirmwareUpdateStatus,
    val progress: JabraFirmwareProgress? = null,
    val error: String? = null,
) {
    // Cancellation is only offered before erasing the first partition. Closing a socket after that
    // point is an interruption, not a rollback of the bytes already staged on the headset.
    val canCancel: Boolean get() = status == FirmwareUpdateStatus.TRANSFERRING &&
        (progress == null || progress.stage == JabraFirmwareStage.PREPARING)
    val active: Boolean get() = status in setOf(FirmwareUpdateStatus.TRANSFERRING,
        FirmwareUpdateStatus.AWAITING_CASE, FirmwareUpdateStatus.VERIFYING)
    fun afterRestart() = when (status) {
        FirmwareUpdateStatus.TRANSFERRING -> copy(status = FirmwareUpdateStatus.INTERRUPTED,
            error = "The app stopped during transfer. Nothing will be retransmitted automatically. Check the installed version before starting another update.")
        FirmwareUpdateStatus.VERIFYING -> copy(status = FirmwareUpdateStatus.AWAITING_CASE)
        else -> this
    }
    fun matches(address: String, productId: Int) = this.address.equals(address, ignoreCase = true) && this.productId == productId
    fun journalFailure() = copy(status = FirmwareUpdateStatus.INTERRUPTED,
        error = "Hush could not save the installation status. No further firmware writes will be sent. Check the installed version before trying again.")
    fun confirms(address: String, productId: Int, version: String) = matches(address, productId) &&
        numericVersion(version)?.let { it == numericVersion(targetVersion) } == true

    companion object {
        fun numericVersion(value: String): List<Int>? = Regex("^(\\d+)\\.(\\d+)\\.(\\d+)$")
            .matchEntire(value.trim())?.groupValues?.drop(1)?.map { it.toIntOrNull() ?: return null }
        fun isNewer(candidate: String, current: String): Boolean {
            val a = numericVersion(candidate) ?: return false
            val b = numericVersion(current) ?: return false
            for (i in a.indices) if (a[i] != b[i]) return a[i] > b[i]
            return false
        }
    }
}
