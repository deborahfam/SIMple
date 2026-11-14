package com.simple.mobile.presentation

import org.junit.Assert.*
import org.junit.Test

class LineCatalogTest {
    @Test fun inactiveHistoryStaysSelectableWithoutPhonePermission() {
        val lines = LineCatalog.visible(emptyList(), setOf(7, 8), mapOf(7 to "Personal"))
        assertEquals("Personal", lines.first().label)
        assertTrue(lines.all { !it.active && !it.cubacel })
        assertEquals(8, LineCatalog.selection(8, lines))
    }
    @Test fun returningSimIsNotDuplicatedAndKeepsItsNickname() {
        val lines = LineCatalog.visible(listOf(Line(7, 0, "Cubacel", true)), setOf(7, 8), mapOf(7 to "Trabajo"))
        assertEquals(2, lines.size)
        assertTrue(lines.first().active)
        assertEquals("Trabajo", lines.first().label)
        assertEquals(7, LineCatalog.selection(-1, lines))
    }
    @Test fun deletingSelectedArchiveFallsBackToActiveLine() {
        val lines = LineCatalog.visible(listOf(Line(7, 0, "Cubacel", true)), emptySet(), emptyMap())
        assertEquals(7, LineCatalog.selection(8, lines))
        assertEquals(-1, LineCatalog.selection(8, emptyList()))
    }
}
