package com.occaecat.hush.diag

/**
 * Do Sony Bluetooth headphones look genuine? Clones copy the name and the look, but rarely Sony's control protocol
 * (MDR), Sony's Bluetooth address blocks, the identity the headset reports over MDR (model, colour, serial, update
 * service) or what Sony's update server knows about that identity. Like the EarPods check this is a fingerprint, not
 * cryptography: Sony's headphones expose no authentication challenge, so a perfect copy could pass; a failed check is
 * the meaningful result.
 */
object SonyCheck {
    enum class Result { PASS, FAIL, UNKNOWN }

    data class Item(val title: String, val detail: String, val result: Result)

    enum class Verdict {
        /** Everything matched. */
        GENUINE,
        /** Nothing contradicts it, but some parts couldn't be read (offline, protocol v2, not answered yet). */
        LIKELY,
        /** At least one check failed. */
        SUSPICIOUS,
    }

    data class Report(val verdict: Verdict, val items: List<Item>) {
        val passed get() = items.count { it.result == Result.PASS }
    }

    /** What Sony's update server said about the headset's update service (see FirmwareCheck). */
    sealed interface Server {
        /** The service exists; [models] are its Model rules, [latest] the newest firmware Sony offers. */
        data class Known(val models: List<String>, val latest: String?) : Server
        /** The server has no update service under this category and id (HTTP 404). */
        data object Unknown : Server
        /** Couldn't ask: offline, or the check hasn't run yet. */
        data object NotChecked : Server
    }

    data class Input(
        /** MODEL_NAME over MDR; null when the headset hasn't answered (yet). */
        val model: String?,
        /** The headset answered in protocol v2's layout: Sony's protocol, but Hush can't read the rest. */
        val protocolV2: Boolean = false,
        /** The Bluetooth name the headset announces itself (not the phone's alias). */
        val bluetoothName: String?,
        val address: String?,
        /** BluetoothClass.getDeviceClass(); null when unknown. */
        val deviceClass: Int? = null,
        val firmware: String?,
        /** The headset has FW_UPDATE: it reports a serial and an update service. Models without it are only checked on the rest. */
        val updates: Boolean = true,
        val serial: String?,
        /** UPDT_GET_PARAM category (2) and service id (3). */
        val category: String?,
        val service: String?,
        val color: Int?,
        val server: Server = Server.NotChecked,
    )

    /**
     * Sony's Bluetooth address blocks (IEEE MA-L, "Sony Corporation", "Sony Home Entertainment & Sound Products",
     * "Sony Video & Sound Products", "Sony Visual Products"; the PlayStation, imaging and semiconductor blocks are left
     * out), from hwdata's oui.txt, 2026-10. The XM4 reference unit is 80:99:E7.
     */
    private val SONY_OUI = """
        000095 00014A 000AD9 000E07 000FDE 0012EE 0013A9 001620 0016B8 001813 001963 001A75 001A80 001B59 001CA4 001D28
        001DBA 001E45 001EDC 001FE4 00219E 002298 002345 0023F1 0024BE 0024EF 0025E7 00EB2D 045D4B 080046 104FA8 143FA6
        18002D 1C7B21 205476 2421AB 283F69 3017C8 303926 307512 30A8DB 30F9ED 38184C 387862 3C01EF 3C0771 3C38F4 402BA1
        4040A7 40B837 44746C 44D4E0 4C21D0 50125C 54263D 544249 5453ED 58170C 581862 584822 5CB524 68764F 6C0E0D 6C23B9
        6CB227 702605 78843C 8099E7 8400D2 848EDF 84C7EA 88C9E8 8C6422 90C115 94CE2C 94DB56 9C5CF9 A0E453 AC800A AC9B0A
        B4527D B4527E B8F934 BC6E64 C43ABE CC988B D05162 D4389C D8D43C E063E5 F0BF97 F84E17 FCF152
    """.split(Regex("\\s+")).filter { it.isNotEmpty() }.toSet()

    /**
     * Sony's model catalogue and the ModelColor values each model ships in, from Sound Connect's cloud model list
     * (GetAllCloudModelInfos, cached 2026-10). Models of Hush's catalogue that Sound Connect doesn't list are missing.
     */
    internal val COLORS: Map<String, Set<Int>> = mapOf(
        "1000X THE COLLEXION" to "0,1,2", "INZONE Buds" to "0,1,2,14", "INZONE H9 II" to "0,1,2,12",
        "LinkBuds" to "0,2,9", "LinkBuds Clip" to "0,1,8,10,11,14", "LinkBuds Fit" to "0,1,2,6,8,14",
        "LinkBuds Open" to "0,1,2,14", "LinkBuds S" to "0,1,2,5,11,14", "LinkBuds UC" to "0,9",
        "MDR-XB950B1" to "0,1,4,5", "MDR-XB950N1" to "0,1,8,9", "ULT WEAR" to "0,1,2,9",
        "WF-1000X" to "0,1,10", "WF-1000XM3" to "0,1,3", "WF-1000XM4" to "0,1,3", "WF-1000XM5" to "0,1,3,6",
        "WF-1000XM6" to "0,1,3", "WF-C500" to "0,1,2,8,12", "WF-C510" to "0,1,2,5,7", "WF-C700N" to "0,1,2,8,14",
        "WF-C710N" to "0,1,2,5,6", "WF-H800" to "0,1,4,5,8,12", "WF-SP700N" to "0,1,2,5,6,7",
        "WF-SP800N" to "0,1,2,5,12", "WF-SP900" to "0,1,2,6,7", "WH-1000XM2" to "0,1,10", "WH-1000XM3" to "0,1,3",
        "WH-1000XM4" to "0,1,2,3,5", "WH-1000XM4C" to "0,1,3,14", "WH-1000XM5" to "0,1,3,5,6",
        "WH-1000XM6" to "0,1,3,5,6,8,13", "WH-CH520" to "0,1,2,5,6,7,11", "WH-CH700N" to "0,1,5,9",
        "WH-CH720N" to "0,1,2,5,6", "WH-H800" to "0,1,4,5,8,10", "WH-H810" to "0,1,4,5,8,12",
        "WH-H900N" to "0,1,4,5,8,10", "WH-H910N" to "0,1,4,5,8,12", "WH-XB700" to "0,1,5", "WH-XB900N" to "0,1,5,9",
        "WH-XB910N" to "0,1,5,9", "WI-1000X" to "0,1,10", "WI-1000XM2" to "0,1,3", "WI-C100" to "0,1,2,5,11",
        "WI-C600N" to "0,1,5,9", "WI-H700" to "0,1,4,5,8,10", "WI-SP600N" to "0,1,2,5,6,7",
    ).mapValues { (_, v) -> v.split(',').map(String::toInt).toSet() }

    /** Update services read from real units: model to (category, service id). */
    private val SERVICES = mapOf("WH-1000XM4" to ("HP002" to "MDRID294301"))

    /** Sony's headphone model names: WH-/WF-/WI-/MDR- plus the newer product lines. */
    private val sonyName = Regex("^((WH|WF|WI|MDR|YY)-[A-Z0-9]+|LinkBuds( [A-Za-z]+)?|INZONE .+|ULT .+)$")
    private val firmwarePattern = Regex("^\\d+\\.\\d+(\\.\\d+)?$")
    /** The XM4 pads its serial to 16 digits; Hush strips the zeros. */
    private val serialPattern = Regex("^[0-9]{5,16}$")

    /** Major class AUDIO_VIDEO (BluetoothClass.Device.Major.AUDIO_VIDEO). */
    private const val AUDIO_VIDEO = 0x0400

    fun oui(address: String) = address.replace(":", "").take(6).uppercase()

    fun check(input: Input): Report {
        val items = mutableListOf<Item>()
        fun add(title: String, result: Result, detail: String) { items += Item(title, detail, result) }
        fun pass(ok: Boolean) = if (ok) Result.PASS else Result.FAIL
        val model = input.model

        add("Sony control protocol", if (model != null || input.protocolV2) Result.PASS else Result.UNKNOWN, when {
            model != null -> "Answers Sony’s headphone protocol"
            input.protocolV2 -> "Answers Sony’s newer protocol (v2); the model has not been read yet"
            else -> "No answer yet"
        })

        if (model != null) {
            val catalogued = model in COLORS
            add("Model name", pass(catalogued || sonyName.matches(model)),
                if (catalogued) "$model, in Sony’s catalogue" else if (sonyName.matches(model)) "$model, Sony’s naming" else "“$model” isn’t a Sony model")
        }

        when (val name = input.bluetoothName) {
            null -> add("Bluetooth name", Result.UNKNOWN, "Not readable")
            else -> if (model == null) add("Bluetooth name", Result.UNKNOWN, "“$name”, no model to compare with")
            else add("Bluetooth name", pass(name.contains(model, ignoreCase = true)),
                if (name.contains(model, ignoreCase = true)) "“$name”" else "“$name” doesn’t match the model it reports")
        }

        when (val address = input.address) {
            null -> add("Bluetooth address", Result.UNKNOWN, "Not readable")
            else -> {
                val block = oui(address).chunked(2).joinToString(":")
                add("Bluetooth address", pass(oui(address) in SONY_OUI),
                    if (oui(address) in SONY_OUI) "$block… is registered to Sony" else "$block… isn’t one of Sony’s address blocks")
            }
        }

        input.deviceClass?.let { c ->
            add("Device class", pass(c and 0x1F00 == AUDIO_VIDEO),
                if (c and 0x1F00 == AUDIO_VIDEO) "Audio device" else "Announces itself as 0x%04X, not an audio device".format(c))
        }

        if (input.protocolV2 && model == null) return report(items)

        when (val fw = input.firmware) {
            null -> add("Firmware version", Result.UNKNOWN, "Not reported")
            else -> add("Firmware version", pass(firmwarePattern.matches(fw)), if (firmwarePattern.matches(fw)) fw else "“$fw”, not Sony’s format")
        }

        if (input.updates) when (val serial = input.serial) {
            null -> add("Serial number", Result.UNKNOWN, "Not reported")
            else -> add("Serial number", pass(serialPattern.matches(serial)),
                if (serialPattern.matches(serial)) "Sony format, ${serial.length} digits" else "Not Sony’s format")
        }

        val category = input.category
        val service = input.service
        if (!input.updates) Unit
        else if (category == null || service == null) {
            add("Update service", Result.UNKNOWN, "Not reported")
        } else {
            val shape = Regex("^HP\\d{3}$").matches(category) && Regex("^MDRID\\d+$").matches(service)
            val known = model?.let { SERVICES[it] }
            add("Update service", pass(shape && (known == null || known == (category to service))), when {
                !shape -> "“$category / $service” isn’t a Sony update service"
                known != null && known != (category to service) -> "$category / $service, expected ${known.first} / ${known.second}"
                else -> "$category / $service"
            })
        }

        val colors = model?.let { COLORS[it] }
        // Older models may not answer the colour query; that's no reason to doubt them.
        if (colors != null) {
            when (val c = input.color) {
                null -> Unit
                else -> add("Colour", pass(c in colors), if (c in colors) "A colour Sony makes the $model in" else "Colour code $c, Sony doesn’t make the $model in it")
            }
        }

        if (category != null && service != null) when (val s = input.server) {
            Server.NotChecked -> add("Sony update server", Result.UNKNOWN, "Not checked yet (needs the internet)")
            Server.Unknown -> add("Sony update server", Result.FAIL, "Sony has no update service $category / $service")
            is Server.Known -> {
                val modelOk = model == null || s.models.isEmpty() || s.models.any { it.equals(model, ignoreCase = true) }
                val fw = input.firmware
                val fwOk = fw == null || s.latest == null || compare(fw, s.latest) <= 0
                add("Sony update server", pass(modelOk && fwOk), when {
                    !modelOk -> "Sony’s service is for the ${s.models.joinToString()}, not the $model"
                    !fwOk -> "Firmware $fw is newer than anything Sony released (${s.latest})"
                    else -> "Knows this model" + (s.latest?.let { ", latest firmware $it" } ?: "")
                })
            }
        }
        return report(items)
    }

    private fun report(items: List<Item>) = Report(
        when {
            items.any { it.result == Result.FAIL } -> Verdict.SUSPICIOUS
            items.any { it.result == Result.UNKNOWN } -> Verdict.LIKELY
            else -> Verdict.GENUINE
        },
        items,
    )

    private fun compare(a: String, b: String): Int {
        val x = a.split('.').map { it.toLongOrNull() ?: 0 }
        val y = b.split('.').map { it.toLongOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = x.getOrElse(i) { 0 }.compareTo(y.getOrElse(i) { 0 })
            if (d != 0) return d
        }
        return 0
    }
}
