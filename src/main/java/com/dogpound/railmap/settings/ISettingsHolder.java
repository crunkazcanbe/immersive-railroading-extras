package com.dogpound.railmap.settings;

import java.util.List;

/** A block entity with a Settings Console (sneak-right-click it with the Signal Wrench). */
public interface ISettingsHolder {
    String settingsTitle();

    /** every option, grouped by page; INFO entries are live read-only lines (rebuilt each time) */
    List<Setting> settingDefs();

    SettingsStore settings();

    /** after a player changed something (save, resync, react) */
    default void onSettingsChanged(String key) { }
}
