package com.me.mobgift

data class DropTestResult(
    val found: Boolean,
    val passed: Boolean,
    val lines: List<String>
)
