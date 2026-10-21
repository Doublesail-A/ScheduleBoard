package com.doublesail.scheduleboard.startup

enum class BootAction { NONE, SYSTEM_HOME, OPEN_ACTIVITY, SHOW_NOTIFICATION }

/** Android 10+ blocks ordinary background Activity launches, including at boot. */
fun bootAction(autoStart: Boolean, isDefaultHome: Boolean, apiLevel: Int): BootAction = when {
    !autoStart -> BootAction.NONE
    isDefaultHome -> BootAction.SYSTEM_HOME
    apiLevel < 29 -> BootAction.OPEN_ACTIVITY
    else -> BootAction.SHOW_NOTIFICATION
}
