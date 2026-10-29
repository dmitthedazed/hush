package com.occaecat.hush.jabra

import kotlinx.coroutines.CancellationException
import java.io.IOException

enum class JabraButtonContext(val id: Int, val label: String) {
    MUSIC(0, "Music"), INCOMING_CALL(1, "Incoming calls"), ONGOING_CALL(2, "During calls");
}

enum class JabraButtonSide(val id: Int, val label: String) { LEFT(0, "Left"), RIGHT(1, "Right") }
enum class JabraButtonGesture(val id: Int, val label: String) {
    SINGLE(0, "Single press"), DOUBLE(1, "Double press"), TRIPLE(2, "Triple press");
}

data class JabraButtonSlot(val context: JabraButtonContext, val side: JabraButtonSide, val gesture: JabraButtonGesture) {
    val label: String get() = "${side.label} · ${gesture.label.lowercase()}"
}

enum class JabraButtonAction(val id: Int, val label: String) {
    NONE(0, "No action"), PLAY_PAUSE(1, "Play / pause"), NEXT_TRACK(2, "Next track"),
    PREVIOUS_TRACK(3, "Previous track"), VOICE_ASSISTANT(4, "Voice assistant"), SOUND_MODES(5, "Sound modes"),
    REJECT_CALL(6, "Reject call"), ACCEPT_END_CURRENT(7, "Accept / end current call"),
    ACCEPT_HOLD_CURRENT(8, "Accept / hold current call"), END_RESUME_HELD(9, "End / resume held call"),
    HOLD_RESUME_HELD(10, "Hold / resume held call"), MUTE(11, "Mute microphone"), SIDETONE(12, "Sidetone");

    companion object {
        fun label(id: Int): String = entries.firstOrNull { it.id == id }?.label ?: when (id) {
            13 -> "Reserved for voice assistant"
            14 -> "Spotify Tap"
            else -> "Reserved (${id.toString(16).padStart(2, '0')})"
        }
    }
}

data class JabraButtonAssignment(
    val slot: JabraButtonSlot,
    /** Retain unknown/reserved wire values rather than substituting an editable action. */
    val action: Int,
    val allowedActions: List<JabraButtonAction>,
    val lockReason: String? = null,
) {
    val actionLabel: String get() = JabraButtonAction.label(action)
}

data class JabraButtonState(
    val supportedContexts: Set<JabraButtonContext> = emptySet(),
    val slots: List<JabraButtonAssignment> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
) {
    fun canReset(context: JabraButtonContext): Boolean = !loading && error == null &&
        context in supportedContexts && slots.any { it.slot.context == context } &&
        slots.filter { it.slot.context == context }.all { it.lockReason == null }
}

/** A completed readback is still useful when the device did not apply the requested assignment. */
internal class JabraButtonConfirmationException(val observed: JabraButtonState, message: String) : IOException(message)

/** Model Forms and GNP schemas from Sound+ 5.21.0. No long-press or volume write schema is exposed. */
internal object JabraButtonProtocol {
    const val CURRENT = 0x139a
    const val DEFAULTS = 0x139b
    const val CONTEXTS = 0x13ad
    const val SPOTIFY = 0x13e5
    const val ASSISTANT = 0x0d4d

    fun allowed(context: JabraButtonContext): List<JabraButtonAction> {
        val ids = when (context) {
            JabraButtonContext.MUSIC -> setOf(0, 1, 2, 3, 4, 5)
            JabraButtonContext.INCOMING_CALL -> setOf(0, 6, 7, 8, 11, 12)
            JabraButtonContext.ONGOING_CALL -> setOf(0, 9, 10, 11, 12)
        }
        return JabraButtonAction.entries.filter { it.id in ids }
    }

    fun contexts(payload: ByteArray): Set<JabraButtonContext> {
        if (payload.size > 16 || payload.toSet().size != payload.size) malformed()
        // A future context must not acquire the action menu of one of these three contexts.
        return payload.asSequence().mapNotNull { value -> JabraButtonContext.entries.firstOrNull { it.id == value.u() } }.toSet()
    }

    fun records(payload: ByteArray, context: JabraButtonContext): Map<JabraButtonSlot, Int> {
        if (payload.isEmpty() || payload.size > 24 || payload.size % 4 != 0) malformed()
        val result = linkedMapOf<JabraButtonSlot, Int>()
        for (i in payload.indices step 4) {
            if (payload[i].u() != context.id) malformed()
            val side = JabraButtonSide.entries.firstOrNull { it.id == payload[i + 1].u() } ?: malformed()
            val gesture = JabraButtonGesture.entries.firstOrNull { it.id == payload[i + 2].u() } ?: malformed()
            val slot = JabraButtonSlot(context, side, gesture)
            if (result.put(slot, payload[i + 3].u()) != null) malformed()
        }
        return result
    }

    fun write(slot: JabraButtonSlot, action: Int) = byteArrayOf(
        slot.context.id.toByte(), slot.side.id.toByte(), slot.gesture.id.toByte(), action.toByte(),
    )

    private fun malformed(): Nothing = throw IOException("The headphones returned an invalid button configuration")
}

/** The backend serializes these complete transactions and owns loading/error publication. */
internal class JabraButtons(private val client: GnpClient) {
    suspend fun read(): JabraButtonState {
        val model = JabraProtocol.model(client.read(JabraProtocol.PRODUCT_ID))
        val contexts = JabraButtonProtocol.contexts(client.read(JabraButtonProtocol.CONTEXTS))
        val records = linkedMapOf<JabraButtonSlot, Int>()
        for (context in contexts) records.putAll(JabraButtonProtocol.records(
            client.read(JabraButtonProtocol.CURRENT, byteArrayOf(context.id.toByte())), context,
        ))
        // Only Elite 10's model Forms advertise Spotify Tap and Google Bisto. Unknown state
        // leaves assignments visible, but does not authorize changing reserved controls.
        val spotify = if (model == JabraModel.ELITE_10) optionalByte(JabraButtonProtocol.SPOTIFY)
            ?.takeIf { it in 0..1 } else 0
        val assistant = if (model == JabraModel.ELITE_10)
            optionalByte(JabraButtonProtocol.ASSISTANT, byteArrayOf(3))?.takeIf { it in setOf(0, 0x10, 0x11) } else 0
        val unknownLocks = spotify == null || assistant == null
        val bisto = spotify == 0 && assistant == 0x11
        val slots = records.entries.sortedWith(compareBy({ it.key.context.id }, { it.key.side.id }, { it.key.gesture.id }))
            .map { (slot, action) ->
                val allowed = JabraButtonProtocol.allowed(slot.context).filterNot {
                    bisto && slot.context == JabraButtonContext.MUSIC && it == JabraButtonAction.VOICE_ASSISTANT
                }
                val reason = when {
                    action == 13 -> "Reserved by the voice assistant. Manage it in Jabra Sound+."
                    action == 14 -> "Reserved for Spotify Tap. Manage it in Jabra Sound+."
                    action !in JabraButtonProtocol.allowed(slot.context).map { it.id } -> "This assignment is reserved by the headphones."
                    unknownLocks -> "Could not check assistant and Spotify controls. Refresh before changing buttons."
                    // Sound+ removes the other left controls from its editable list while Spotify is active.
                    spotify == 1 && slot.side == JabraButtonSide.LEFT -> "Managed by Spotify Tap. Change Spotify Tap in Jabra Sound+ first."
                    bisto && slot.context == JabraButtonContext.MUSIC && slot.side == JabraButtonSide.RIGHT &&
                        slot.gesture == JabraButtonGesture.SINGLE -> "Reserved by Google Assistant. Manage it in Jabra Sound+."
                    else -> null
                }
                JabraButtonAssignment(slot, action, if (reason == null) allowed else emptyList(), reason)
            }
        return JabraButtonState(contexts, slots)
    }

    suspend fun set(slot: JabraButtonSlot, action: Int): JabraButtonState {
        val before = read()
        val assignment = before.slots.firstOrNull { it.slot == slot }
            ?: throw IOException("This button is not supported by these headphones")
        if (assignment.lockReason != null) throw IOException(assignment.lockReason)
        require(assignment.allowedActions.any { it.id == action }) { "This action is not available for this button" }
        if (assignment.action == action) return before
        client.write(JabraButtonProtocol.CURRENT, JabraButtonProtocol.write(slot, action))
        val confirmed = read()
        if (confirmed.slots.firstOrNull { it.slot == slot }?.action != action)
            throw JabraButtonConfirmationException(confirmed,
                "The headphones did not apply the button change. Their current assignments are shown; refresh before trying again.")
        return confirmed
    }

    suspend fun reset(context: JabraButtonContext): JabraButtonState {
        val before = read()
        if (!before.canReset(context)) throw IOException("This context has reserved or unavailable controls and cannot be reset here")
        val defaults = JabraButtonProtocol.records(
            client.read(JabraButtonProtocol.DEFAULTS, byteArrayOf(context.id.toByte())), context,
        )
        val current = before.slots.filter { it.slot.context == context }.associate { it.slot to it.action }
        if (defaults.keys != current.keys || defaults.values.any { value -> JabraButtonProtocol.allowed(context).none { it.id == value } })
            throw IOException("The headphones returned incompatible default button assignments")
        client.write(JabraButtonProtocol.DEFAULTS, byteArrayOf(context.id.toByte()))
        val confirmed = read()
        if (confirmed.slots.filter { it.slot.context == context }.associate { it.slot to it.action } != defaults)
            throw JabraButtonConfirmationException(confirmed,
                "The headphones did not apply the button reset. Their current assignments are shown; refresh before trying again.")
        return confirmed
    }

    private suspend fun optionalByte(topic: Int, payload: ByteArray = byteArrayOf()): Int? = try {
        client.read(topic, payload).singleOrNull()?.u()
    } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
}
