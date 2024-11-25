package website.amwp.backup.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class BackupConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("drivebackup/config.json");
    private static BackupConfig instance;

    // Config fields
    private boolean isAuthenticated = false;
    private long backupInterval = 3600000; // 1 hour in milliseconds
    private List<String> worldsToBackup = new ArrayList<>();
    private boolean backupMods = true;
    private String lastBackupTime = "";

    public static BackupConfig getInstance() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    public static BackupConfig load() {
        try {
            if (!CONFIG_PATH.toFile().exists()) {
                BackupConfig defaultConfig = new BackupConfig();
                defaultConfig.save();
                return defaultConfig;
            }

            try (Reader reader = new FileReader(CONFIG_PATH.toFile())) {
                return GSON.fromJson(reader, BackupConfig.class);
            }
        } catch (IOException e) {
            e.printStackTrace();
            return new BackupConfig();
        }
    }

    public void save() {
        try {
            CONFIG_PATH.getParent().toFile().mkdirs();
            try (Writer writer = new FileWriter(CONFIG_PATH.toFile())) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Getters and setters
    public boolean isAuthenticated() {
        return isAuthenticated;
    }

    public void setAuthenticated(boolean authenticated) {
        isAuthenticated = authenticated;
        save();
    }

    public long getBackupInterval() {
        return backupInterval;
    }

    public void setBackupInterval(long backupInterval) {
        this.backupInterval = backupInterval;
        save();
    }

    public List<String> getWorldsToBackup() {
        return worldsToBackup;
    }

    public void addWorldToBackup(String worldName) {
        if (!worldsToBackup.contains(worldName)) {
            worldsToBackup.add(worldName);
            save();
        }
    }

    public void removeWorldFromBackup(String worldName) {
        if (worldsToBackup.remove(worldName)) {
            save();
        }
    }

    public boolean isBackupMods() {
        return backupMods;
    }

    public void setBackupMods(boolean backupMods) {
        this.backupMods = backupMods;
        save();
    }

    public String getLastBackupTime() {
        return lastBackupTime;
    }

    public void setLastBackupTime(String lastBackupTime) {
        this.lastBackupTime = lastBackupTime;
        save();
    }
} 