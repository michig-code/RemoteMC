package de.remotemc.plugin.core;

import java.util.List;

/** config.yml of RemoteMC (Bukkit's config on Paper/Spigot, a YAML file on Fabric). */
public interface Settings {
    String getString(String path, String def);

    int getInt(String path, int def);

    long getLong(String path, long def);

    boolean getBoolean(String path, boolean def);

    List<String> getStringList(String path);

    /** Reads the file again (/remotemc reload). */
    void reload();
}
