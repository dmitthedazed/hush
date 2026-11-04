package com.occaecat.hush.devices

/**
 * The rules every brand module and controller keeps, as plain checks: unit tests assert them for every module and
 * debug builds fail fast on them, so a new brand that breaks the contract breaks on the developer's desk.
 * Each function returns what is wrong, empty when nothing is.
 */
object Contract {
    private val idPattern = Regex("[a-z0-9-]+")

    fun check(module: DeviceModule): List<String> = buildList {
        val at = "module ${module.id}"
        if (!idPattern.matches(module.id)) add("$at: id must be lowercase letters, digits and dashes (it prefixes device keys)")
        if (module.brand.isBlank()) add("$at: brand is blank")
        module.models.groupBy { it.id }.filterValues { it.size > 1 }.keys.forEach { add("$at: model id $it is used twice") }
        module.models.forEach { m ->
            if (m.name.isBlank()) add("$at: model ${m.id} has no name")
            if (m.howTo.isBlank()) add("$at: model ${m.id} has no how-to (the add screen shows it)")
            if (module.hasDemo(m) && !demoCandidate(module.id, m).key.startsWith("${module.id}:")) add("$at: demo key of ${m.id}")
        }
    }

    fun check(c: DeviceController): List<String> = buildList {
        val s = c.state.value
        if (s.name.isBlank()) add("state: name is blank")
        s.batteries.filter { it.level !in 0..100 }.forEach { add("state: battery ${it.part} at ${it.level}%") }
        c.noise?.let { n ->
            if (n.modes.isEmpty()) add("noise: no modes (leave noise null instead)")
            dupes(n.modes.map { it.id }).forEach { add("noise: mode id $it is used twice") }
            n.state.value?.let { ui -> if (n.modes.none { it.id == ui.active }) add("noise: active mode ${ui.active} is not one of the modes") }
            if (n.providedBy == Source.PHONE) add("noise: the phone can't cancel noise; report what the device does")
        }
        c.equalizer?.let { eq -> dupes(eq.presets.value.flatMap { g -> g.presets.map { it.id } }).forEach { add("equalizer: preset id $it is used twice") } }
        c.quick?.let { q ->
            dupes(q.modes.value.map { it.id }).forEach { add("quick: mode id $it is used twice") }
            dupes(q.toggles.value.map { it.id }).forEach { add("quick: toggle id $it is used twice") }
        }
    }

    /** Settings are composed, so they are checked where they are drawn. */
    fun check(groups: List<SettingsGroup>): List<String> = groups.flatMap { g ->
        g.items.filter { it.title.isBlank() }.map { "settings: a row in ${g.title ?: "an untitled group"} has no title" }
    }

    private fun dupes(ids: List<String>) = ids.groupBy { it }.filterValues { it.size > 1 }.keys
}
