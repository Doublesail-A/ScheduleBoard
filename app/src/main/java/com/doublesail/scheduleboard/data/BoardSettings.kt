package com.doublesail.scheduleboard.data

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class BoardSettings(
    val keepScreenOn: Boolean = true,
    val immersive: Boolean = true,
    val dynamicColor: Boolean = true,
    val autoStart: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
)
