package com.simple.mobile.presentation

internal object LineCatalog {
    fun visible(active: List<Line>, saved: Set<Int>, nicknames: Map<Int, String>): List<Line> =
        active.map { it.copy(nickname = nicknames[it.subscription].orEmpty()) } +
            (saved - active.map { it.subscription }.toSet()).sorted().map {
                Line(it, -1, "", false, active = false, nickname = nicknames[it].orEmpty())
            }
    fun selection(current: Int, lines: List<Line>): Int =
        if (lines.any { it.subscription == current }) current
        else lines.firstOrNull { it.active && it.cubacel }?.subscription ?: lines.firstOrNull()?.subscription ?: -1
}
