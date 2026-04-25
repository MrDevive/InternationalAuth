package com.example.internationalauth;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class AuthPlaceholderExpansion extends PlaceholderExpansion {

    private final InternationalAuth plugin;

    public AuthPlaceholderExpansion(InternationalAuth plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "internationalauth";
    }

    @Override
    public @NotNull String getAuthor() {
        return String.join(", ", plugin.getDescription().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) return "";
        UUID uuid = player.getUniqueId();

        switch (params.toLowerCase()) {
            case "registration_date":
                String regDate = plugin.getDatabaseManager().getRegistrationDate(uuid);
                return regDate != null ? regDate : "Не зарегистрирован";
            case "registration_date_formatted":
                String date = plugin.getDatabaseManager().getRegistrationDate(uuid);
                if (date == null) return "Не зарегистрирован";
                return formatDate(date);
            case "last_login":
                return getLastLogin(uuid);
            case "last_ip":
                return getLastIP(uuid);
            case "is_registered":
                return plugin.getDatabaseManager().isRegistered(uuid) ? "Да" : "Нет";
            case "is_authorized":
                return plugin.getSessionMap().containsKey(uuid) ? "Да" : "Нет";
            case "has_secret_key":
                return plugin.getDatabaseManager().hasSecretKey(uuid) ? "Да" : "Нет";
            case "failed_attempts":
                return String.valueOf(getFailedAttempts(uuid));
            default:
                return null;
        }
    }

    private String formatDate(String dateTime) {
        if (dateTime == null || dateTime.isEmpty()) return "Неизвестно";
        try {
            String[] parts = dateTime.split(" ");
            String[] dateParts = parts[0].split("-");
            return dateParts[2] + "." + dateParts[1] + "." + dateParts[0] + " " + parts[1];
        } catch (Exception e) {
            return dateTime;
        }
    }

    private String getLastLogin(UUID uuid) {
        String sql = "SELECT last_login FROM auth_users WHERE uuid = ?";
        try (java.sql.PreparedStatement pstmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            pstmt.setString(1, uuid.toString());
            java.sql.ResultSet rs = pstmt.executeQuery();
            if (rs.next()) return formatDate(rs.getString("last_login"));
        } catch (Exception e) { e.printStackTrace(); }
        return "Неизвестно";
    }

    private String getLastIP(UUID uuid) {
        String sql = "SELECT last_ip FROM auth_users WHERE uuid = ?";
        try (java.sql.PreparedStatement pstmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            pstmt.setString(1, uuid.toString());
            java.sql.ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                String ip = rs.getString("last_ip");
                return ip != null && !ip.isEmpty() ? ip : "Неизвестно";
            }
        } catch (Exception e) { e.printStackTrace(); }
        return "Неизвестно";
    }

    private int getFailedAttempts(UUID uuid) {
        String sql = "SELECT failed_attempts FROM auth_users WHERE uuid = ?";
        try (java.sql.PreparedStatement pstmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            pstmt.setString(1, uuid.toString());
            java.sql.ResultSet rs = pstmt.executeQuery();
            if (rs.next()) return rs.getInt("failed_attempts");
        } catch (Exception e) { e.printStackTrace(); }
        return 0;
    }
}