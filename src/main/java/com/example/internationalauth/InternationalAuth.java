package com.example.internationalauth;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class InternationalAuth extends JavaPlugin {

    private static InternationalAuth instance;
    private DatabaseManager databaseManager;
    private FileConfiguration messagesConfig;
    private File messagesFile;
    private Map<UUID, Long> sessionMap;
    private int sessionTimeoutMinutes;
    private AuthPlaceholderExpansion placeholderExpansion;

    @Override
    public void onEnable() {
        instance = this;
        sessionMap = new HashMap<>();

        saveDefaultConfig();
        setupMessages();
        reloadConfigSettings();

        databaseManager = new DatabaseManager();
        if (!databaseManager.connect()) {
            getLogger().log(Level.SEVERE, "Не удалось подключиться к MySQL! Плагин отключается.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        databaseManager.createTable();

        getCommand("register").setExecutor(new AuthCommand());
        getCommand("login").setExecutor(new AuthCommand());
        getCommand("changepassword").setExecutor(new AuthCommand());
        getCommand("setkey").setExecutor(new AuthCommand());
        getCommand("auth").setExecutor(new AuthCommand());

        getServer().getPluginManager().registerEvents(new AuthListener(), this);
        getServer().getPluginManager().registerEvents(new CommandBlocker(), this);

        // Регистрация PlaceholderAPI
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            placeholderExpansion = new AuthPlaceholderExpansion(this);
            placeholderExpansion.register();
            getLogger().info("PlaceholderAPI поддержка включена!");
        } else {
            getLogger().warning("PlaceholderAPI не найден! Плейсхолдеры не будут работать.");
        }

        getLogger().info("InternationalAuth успешно включён!");
    }

    @Override
    public void onDisable() {
        if (placeholderExpansion != null) {
            placeholderExpansion.unregister();
        }
        if (databaseManager != null) {
            databaseManager.disconnect();
        }
        getLogger().info("InternationalAuth выключен.");
    }

    private void setupMessages() {
        messagesFile = new File(getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            saveResource("messages.yml", false);
        }
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
    }

    public void reloadPlugin() {
        reloadConfig();
        reloadConfigSettings();
        setupMessages();
        if (databaseManager != null) {
            databaseManager.clearCache();
        }
        getLogger().info("Плагин перезагружен");
    }

    private void reloadConfigSettings() {
        sessionTimeoutMinutes = getConfig().getInt("session-timeout", 60);
    }

    public String getMessage(String path) {
        String message = messagesConfig.getString(path);
        if (message == null) {
            return ChatColor.RED + "Сообщение не найдено: " + path;
        }
        String prefix = messagesConfig.getString("prefix", "&6[InternationalAuth] &r");
        String fullMessage = prefix + message;
        return ChatColor.translateAlternateColorCodes('&', fullMessage);
    }

    public String getRawMessage(String path) {
        String message = messagesConfig.getString(path);
        return message != null ? message : "&cСообщение не найдено: " + path;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public Map<UUID, Long> getSessionMap() {
        return sessionMap;
    }

    public int getSessionTimeoutMinutes() {
        return sessionTimeoutMinutes;
    }

    public static InternationalAuth getInstance() {
        return instance;
    }
}