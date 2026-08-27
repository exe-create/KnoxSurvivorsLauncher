package com.knoxsurvivors.launcher;

import java.util.Locale;

enum Platform {
    WINDOWS,
    LINUX,
    MAC;

    static Platform current() throws LauncherException {
        String name = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (name.contains("win")) return WINDOWS;
        if (name.contains("mac")) return MAC;
        if (name.contains("linux")) return LINUX;
        throw new LauncherException("This operating system is not currently supported: " + name);
    }
}
