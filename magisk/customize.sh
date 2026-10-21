#!/system/bin/sh
ui_print "Schedule Board boot launcher"
ui_print "Install and open the Schedule Board APK first."
ui_print "This module starts the app once after boot and user unlock."
set_perm "$MODPATH/service.sh" 0 0 0755
set_perm "$MODPATH/module.prop" 0 0 0644
