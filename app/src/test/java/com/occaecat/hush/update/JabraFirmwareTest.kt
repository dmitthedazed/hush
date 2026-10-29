package com.occaecat.hush.update

import org.junit.Assert.*
import org.junit.Test
import org.junit.Assume.assumeTrue
import java.io.File
import com.occaecat.hush.jabra.firmware.JabraFirmwareFile
import com.occaecat.hush.jabra.firmware.JabraFirmwarePackage
import com.occaecat.hush.jabra.firmware.JabraFirmwareVersion

class JabraFirmwareTest {
    private val en = 0x0409
    private fun outer(model: String = "Jabra Elite 10", languages: String = "<language id=\"0x0409\">English</language>",
                      url: String = "https://www.jabra.com/Firmware_Data/test-v9.8.7.zip") = """
        <firmwareVersionInfo><productName>$model</productName><version>9.8.7</version>
        <downloadUrl>$url</downloadUrl><minFirmwareUpdaterAppVersion>5.0.0.0</minFirmwareUpdaterAppVersion>
        <headsetLanguages>$languages</headsetLanguages></firmwareVersionInfo>
    """.trimIndent().toByteArray()

    private fun part(partition: Int, language: String = "0x0409", name: String = "part-$partition-$language.bin", order: Int? = null,
                     signed: Boolean = false) = """
        <file name="$name"><content gnsigned="$signed">firmware</content><version>9.8.7</version><target>headset</target>
        <language id="$language">Test language</language><partition>$partition</partition><crc>0xffffffff</crc>
        ${order?.let { "<updateOrder>$it</updateOrder>" }.orEmpty()}</file>
    """.trimIndent()

    private fun vector(parts: String, product: String = "0x2E46") = """
        <buildVector version="9.8.7"><partialUploadAllowed>FALSE</partialUploadAllowed><maxPreloadCount>10</maxPreloadCount>
        <targetUsbPids><usbPid>$product</usbPid></targetUsbPids><files>$parts</files></buildVector>
    """.trimIndent().toByteArray()

    private fun rejected(block: () -> Unit) { assertTrue("Expected invalid firmware metadata to be rejected", runCatching(block).isFailure) }

    @Test fun manifestUsesVerifiedProductAndNumericVersionOrdering() {
        val manifest = JabraFirmware.parseManifest(outer(), 11846)
        assertEquals("https://www.jabra.com/Firmware_Data/test-v9.8.7/", manifest.directory)
        assertEquals("English", manifest.languages[en])
        assertTrue(manifest.available("9.7.255"))
        assertFalse(manifest.available("9.8.7"))
        assertFalse(manifest.available("10.0.0"))
        rejected { JabraFirmware.parseManifest(outer(), 9421) }
        rejected { JabraFirmware.parseManifest(outer(), 1) }
    }

    @Test fun URLsCannotLeaveOfficialHttpsHostsOrEscapeThePackageDirectory() {
        listOf("http://www.jabra.com/a", "https://www.jabra.com.evil.test/a", "https://user@www.jabra.com/a",
            "https://www.jabra.com:8443/a", "https://www.jabra.com/a?redirect=evil", "file:///a").forEach { url ->
            rejected { JabraFirmware.allowedUrl(url) }
        }
        rejected { JabraFirmware.parseManifest(outer(url = "https://www.jabra.com/Firmware_Data/../test.zip"), 11846) }
    }

    @Test fun externalEntitiesAndDuplicateIdentityFieldsAreRejected() {
        rejected { JabraFirmware.parseManifest(("<!DOCTYPE firmwareVersionInfo [<!ENTITY x SYSTEM 'file:///secret'>]>" + outer().toString(Charsets.UTF_8)).toByteArray(), 11846) }
        rejected { JabraFirmware.parseManifest(outer().toString(Charsets.UTF_8).replace("</productName>", "</productName><productName>Jabra Elite 10</productName>").toByteArray(), 11846) }
        rejected { JabraFirmware.parseManifest(outer(languages = "<language id=\"1033\">English</language><language id=\"0x0409\">Duplicate</language>"), 11846) }
    }

    @Test fun onlyCurrentLanguageAndCommonFilesAreSelectedWithoutChangingOrder() {
        val manifest = JabraFirmware.parseManifest(outer("Jabra Elite 85t"), 9421)
        val partitions = listOf(0, 1, 4, 5, 6, 7, 9, 254)
        val parts = partitions.joinToString("") { part(it, if (it == 1) "0" else "0x0409") } + part(0, "0x0407")
        val parsed = JabraFirmware.parseVector(vector(parts, "0x24CD"), manifest, en)
        assertEquals(partitions, parsed.parts.map { it.partition })
        assertEquals(0, parsed.parts[1].languageId)
        assertFalse(parsed.partialUploadAllowed)
        assertEquals(10, parsed.preloadCount)
    }

    @Test fun explicitUpdateOrderIsStableAndSignedHeaderOffsetIsPreserved() {
        val manifest = JabraFirmware.parseManifest(outer("Jabra Elite 85t"), 9421)
        val parts = listOf(0, 1, 4, 5, 6, 7, 9, 254).joinToString("") { part(it, order = if (it == 0) 1 else 0, signed = it == 0) }
        val parsed = JabraFirmware.parseVector(vector(parts, "0x24CD"), manifest, en)
        assertEquals(listOf(1, 4, 5, 6, 7, 9, 254, 0), parsed.parts.map { it.partition })
        assertEquals(52, parsed.parts.last().crcOffset)
    }

    @Test fun missingDuplicateOrWrongProductPartitionsNeverBecomeAPackage() {
        val manifest = JabraFirmware.parseManifest(outer(), 11846)
        rejected { JabraFirmware.parseVector(vector(part(0), "0x24CD"), manifest, en) }
        rejected { JabraFirmware.parseVector(vector(part(0) + part(0, name = "other.bin")), manifest, en) }
        rejected { JabraFirmware.parseVector(vector(part(0, "0x0407")), manifest, en) }
        rejected { JabraFirmware.parseVector(vector(part(0) + part(1, "0x0407")), manifest, en) }
        rejected { JabraFirmware.parseVector(vector(part(0, name = "../escape.bin")), manifest, en) }
        rejected { JabraFirmware.parseVector(vector(part(0)).toString(Charsets.UTF_8).replace("9.8.7", "9.8.6").toByteArray(), manifest, en) }
        val elite85t = JabraFirmware.parseManifest(outer("Jabra Elite 85t"), 9421)
        rejected { JabraFirmware.parseVector(vector(part(0), "0x24CD"), elite85t, en) }
    }

    @Test fun futureUpdaterProfilesAndInvalidNumbersFailClosed() {
        rejected { JabraFirmware.parseManifest(outer().toString(Charsets.UTF_8).replace("5.0.0.0", "5.1.0.0").toByteArray(), 11846) }
        val manifest = JabraFirmware.parseManifest(outer(), 11846)
        rejected { JabraFirmware.parseVector(vector(part(0)).toString(Charsets.UTF_8).replace("0xffffffff", "-1").toByteArray(), manifest, en) }
        rejected { JabraFirmware.parseVector(vector(part(0)).toString(Charsets.UTF_8).replace("<maxPreloadCount>10", "<maxPreloadCount>0").toByteArray(), manifest, en) }
    }

    /** Optional local research evidence; no manufacturer metadata or binary is copied into the repository. */
    @Test fun publishedManifestsAndEveryLanguageMatchTheKnownPartitionProfilesWhenAvailable() {
        val directory = File(System.getProperty("java.io.tmpdir"), "hush-jabra-settings-research")
        assumeTrue(listOf(11846, 9421).all { File(directory, "manifest-$it.xml").isFile && File(directory, "package-$it-info.xml").isFile })
        for (product in listOf(11846, 9421)) {
            val manifest = JabraFirmware.parseManifest(File(directory, "manifest-$product.xml").readBytes(), product)
            val metadata = File(directory, "package-$product-info.xml").readBytes()
            assertEquals(6, manifest.languages.size)
            for (language in manifest.languages.keys) {
                val vector = JabraFirmware.parseVector(metadata, manifest, language)
                assertEquals(if (product == 11846) 1 else 8, vector.parts.size)
                assertTrue(vector.parts.all { it.languageId == language })
                assertFalse(vector.partialUploadAllowed)
                assertEquals(10, vector.preloadCount)
            }
        }
    }

    @Test fun allNinePublishedEnglishFilesPassTheProductionVerifierWhenAvailable() {
        val directory = File(System.getProperty("java.io.tmpdir"), "hush-jabra-settings-research")
        assumeTrue(listOf(11846, 9421).all { File(directory, "manifest-$it.xml").isFile && File(directory, "package-$it-info.xml").isFile })
        var verifiedCount = 0
        for (product in listOf(11846, 9421)) {
            val manifest = JabraFirmware.parseManifest(File(directory, "manifest-$product.xml").readBytes(), product)
            val vector = JabraFirmware.parseVector(File(directory, "package-$product-info.xml").readBytes(), manifest, en)
            val downloaded = File(directory, "firmware-downloads/$product")
            assumeTrue(vector.parts.all { File(downloaded, it.name).isFile })
            val files = vector.parts.map { part ->
                JabraFirmwareFile.verified(part.partition, JabraFirmwareVersion.parse(part.version, part.languageId),
                    part.crc, File(downloaded, part.name).readBytes(), part.crcOffset).also { verifiedCount++ }
            }
            val prepared = JabraFirmwarePackage.verified(product, manifest.version, en, files,
                vector.preloadCount, vector.partialUploadAllowed, "local-reference-fixture")
            assertEquals(if (product == 11846) 2_466_080L else 1_872_768L, prepared.totalBytes)
        }
        assertEquals(9, verifiedCount)
    }
}
