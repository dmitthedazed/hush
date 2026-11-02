package com.occaecat.hush.sim

import com.occaecat.hush.mdr.MdrEvent
import com.occaecat.hush.mdr.v1.MdrV1
import com.occaecat.hush.mdr.v1.SonyReference
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Hand-written replies are ones Sound Connect's own classes read (and, for table 1, write back unchanged), and
 * every reply of every profile decodes in our parser.
 */
class SimProfilesTest {
    private fun Reply.key() = Triple(table, payload[0], payload.getOrNull(1))

    private fun sonyCheck(sony: SonyReference, r: Reply): String? = when (val res = sony.read(r.table, r.payload)) {
        is SonyReference.Result.Rejected -> "$r: ${res.why}"
        is SonyReference.Result.Read -> res.rewritten?.takeUnless { it.contentEquals(r.payload) }?.let { "$r rewritten" }
    }

    private fun check(profile: List<Reply>) {
        val unknown = profile.filter { MdrV1.parse(it.table, it.payload) is MdrEvent.Unknown }
        assertEquals(emptyList<Reply>(), unknown)

        val sony = SonyReference.instance
        assumeTrue("tools/sonyref/sound-connect.jar not made", sony != null)
        // Some of Sony's classes cannot round-trip even what the real XM4 sent (SYSTEM wearing status, the
        // assignable list, text-less play names): for those keys the reference has nothing to say.
        val opaque = Profiles.xm4().filter { sonyCheck(sony!!, it) != null }.map { it.key() }.toSet()
        val bad = profile.distinct().filter { it.key() !in opaque }.mapNotNull { sonyCheck(sony!!, it) }
        assertEquals(emptyList<String>(), bad)
    }

    @Test fun xm4() = check(Profiles.xm4())
    @Test fun xm3() = check(Profiles.xm3())
    @Test fun wf1000xm3() = check(Profiles.wf1000xm3())
}
