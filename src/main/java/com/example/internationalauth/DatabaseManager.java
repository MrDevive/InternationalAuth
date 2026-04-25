package com.example.internationalauth;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class DatabaseManager {

    private Connection connection;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // Кэш паролей
    private final ConcurrentHashMap<UUID, PasswordCacheEntry> passwordCache = new ConcurrentHashMap<>();

    private static class PasswordCacheEntry {
        final String passwordHash;
        final long expiryTime;

        PasswordCacheEntry(String passwordHash, long expiryTime) {
            this.passwordHash = passwordHash;
            this.expiryTime = expiryTime;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expiryTime;
        }
    }

    public boolean connect() {
        try {
            String host = InternationalAuth.getInstance().getConfig().getString("mysql.host");
            int port = InternationalAuth.getInstance().getConfig().getInt("mysql.port");
            String database = InternationalAuth.getInstance().getConfig().getString("mysql.database");
            String username = InternationalAuth.getInstance().getConfig().getString("mysql.username");
            String password = InternationalAuth.getInstance().getConfig().getString("mysql.password");

            String url = "jdbc:mysql://" + host + ":" + port + "/" + database +
                    "?useSSL=false&serverTimezone=UTC";

            connection = DriverManager.getConnection(url, username, password);
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public void createTable() {
        String sql = "CREATE TABLE IF NOT EXISTS auth_users (" +
                "id INT AUTO_INCREMENT PRIMARY KEY," +
                "uuid VARCHAR(36) NOT NULL UNIQUE," +
                "username VARCHAR(16) NOT NULL," +
                "password_hash VARCHAR(64) NOT NULL," +
                "email VARCHAR(255)," +
                "registration_date DATETIME NOT NULL," +
                "last_login DATETIME NOT NULL," +
                "last_ip VARCHAR(45) DEFAULT NULL," +
                "registration_ip VARCHAR(45) DEFAULT NULL," +
                "failed_attempts INT DEFAULT 0," +
                "blocked_until DATETIME DEFAULT NULL," +
                "secret_key VARCHAR(64) DEFAULT NULL" +
                ")";
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
            addColumnIfNotExists("last_ip", "VARCHAR(45) DEFAULT NULL");
            addColumnIfNotExists("registration_ip", "VARCHAR(45) DEFAULT NULL");
            addColumnIfNotExists("failed_attempts", "INT DEFAULT 0");
            addColumnIfNotExists("blocked_until", "DATETIME DEFAULT NULL");
            addColumnIfNotExists("secret_key", "VARCHAR(64) DEFAULT NULL");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void addColumnIfNotExists(String columnName, String columnDefinition) {
        try {
            String checkColumn = "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS " +
                    "WHERE TABLE_NAME = 'auth_users' AND COLUMN_NAME = '" + columnName + "'";
            ResultSet rs = connection.createStatement().executeQuery(checkColumn);
            if (!rs.next()) {
                String alterSql = "ALTER TABLE auth_users ADD COLUMN " + columnName + " " + columnDefinition;
                connection.createStatement().execute(alterSql);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // ==================== КЭШ ПАРОЛЕЙ ====================

    public String getPasswordHashWithCache(UUID uuid) {
        PasswordCacheEntry cached = passwordCache.get(uuid);
        if (cached != null && !cached.isExpired()) {
            return cached.passwordHash;
        }
        String hash = getPasswordHash(uuid);
        if (hash != null) {
            int cacheMinutes = InternationalAuth.getInstance().getConfig().getInt("cache.password-cache-minutes", 5);
            long expiryTime = System.currentTimeMillis() + (cacheMinutes * 60 * 1000L);
            passwordCache.put(uuid, new PasswordCacheEntry(hash, expiryTime));
        }
        return hash;
    }

    public void invalidatePasswordCache(UUID uuid) {
        passwordCache.remove(uuid);
    }

    public void clearCache() {
        passwordCache.clear();
    }

    // ==================== АСИНХРОННЫЕ МЕТОДЫ ====================

    public CompletableFuture<Boolean> isRegisteredAsync(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> isRegistered(uuid));
    }

    public CompletableFuture<Integer> getRegistrationCountByIPAsync(String ipAddress) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT COUNT(*) FROM auth_users WHERE registration_ip = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, ipAddress);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) return rs.getInt(1);
            } catch (SQLException e) { e.printStackTrace(); }
            return 0;
        });
    }

    public CompletableFuture<Boolean> registerUserAsync(UUID uuid, String username, String passwordHash, String ipAddress) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "INSERT INTO auth_users (uuid, username, password_hash, registration_date, last_login, registration_ip, last_ip) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                String now = LocalDateTime.now().format(formatter);
                pstmt.setString(1, uuid.toString());
                pstmt.setString(2, username);
                pstmt.setString(3, passwordHash);
                pstmt.setString(4, now);
                pstmt.setString(5, now);
                pstmt.setString(6, ipAddress);
                pstmt.setString(7, ipAddress);
                pstmt.executeUpdate();
                return true;
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
        });
    }

    public CompletableFuture<String> getPasswordHashWithCacheAsync(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> getPasswordHashWithCache(uuid));
    }

    public CompletableFuture<Void> updateLastLoginAndIPAsync(UUID uuid, String ipAddress) {
        return CompletableFuture.runAsync(() -> {
            String sql = "UPDATE auth_users SET last_login = ?, last_ip = ?, failed_attempts = 0, blocked_until = NULL WHERE uuid = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, LocalDateTime.now().format(formatter));
                pstmt.setString(2, ipAddress);
                pstmt.setString(3, uuid.toString());
                pstmt.executeUpdate();
            } catch (SQLException e) { e.printStackTrace(); }
        });
    }

    public CompletableFuture<Void> incrementFailedAttemptsAsync(UUID uuid) {
        return CompletableFuture.runAsync(() -> incrementFailedAttempts(uuid));
    }

    public CompletableFuture<Boolean> checkSecretKeyAsync(UUID uuid, String secretKeyHash) {
        return CompletableFuture.supplyAsync(() -> checkSecretKey(uuid, secretKeyHash));
    }

    public CompletableFuture<Void> updatePasswordWithCacheAsync(UUID uuid, String newPasswordHash) {
        return CompletableFuture.runAsync(() -> {
            updatePassword(uuid, newPasswordHash);
            invalidatePasswordCache(uuid);
        });
    }

    public CompletableFuture<Map<String, Object>> getPlayerAccountsInfo(String playerName) {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Object> result = new HashMap<>();
            try {
                String getPlayerSql = "SELECT uuid, last_ip, registration_ip FROM auth_users WHERE username = ?";
                try (PreparedStatement pstmt = connection.prepareStatement(getPlayerSql)) {
                    pstmt.setString(1, playerName);
                    ResultSet rs = pstmt.executeQuery();
                    if (!rs.next()) {
                        result.put("found", false);
                        return result;
                    }
                    String uuid = rs.getString("uuid");
                    String lastIp = rs.getString("last_ip");
                    String regIp = rs.getString("registration_ip");
                    String ipToCheck = lastIp != null ? lastIp : regIp;
                    result.put("found", true);
                    result.put("ip", ipToCheck != null ? ipToCheck : "Неизвестно");
                    if (ipToCheck != null) {
                        String findAccountsSql = "SELECT username, uuid, registration_date, last_login, last_ip " +
                                "FROM auth_users WHERE last_ip = ? OR registration_ip = ? ORDER BY registration_date ASC";
                        try (PreparedStatement pstmt2 = connection.prepareStatement(findAccountsSql)) {
                            pstmt2.setString(1, ipToCheck);
                            pstmt2.setString(2, ipToCheck);
                            ResultSet rs2 = pstmt2.executeQuery();
                            List<Map<String, String>> accounts = new ArrayList<>();
                            while (rs2.next()) {
                                Map<String, String> account = new HashMap<>();
                                account.put("username", rs2.getString("username"));
                                account.put("registration_date", rs2.getString("registration_date"));
                                account.put("last_login", rs2.getString("last_login"));
                                account.put("last_ip", rs2.getString("last_ip"));
                                accounts.add(account);
                            }
                            result.put("accounts", accounts);
                            result.put("total", accounts.size());
                        }
                    }
                }
            } catch (SQLException e) { e.printStackTrace(); }
            return result;
        });
    }

    // ==================== СИНХРОННЫЕ МЕТОДЫ ====================

    public boolean isRegistered(UUID uuid) {
        String sql = "SELECT 1 FROM auth_users WHERE uuid = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, uuid.toString());
            ResultSet rs = pstmt.executeQuery();
            return rs.next();
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    public String getPasswordHash(UUID uuid) {
        String sql = "SELECT password_hash FROM auth_users WHERE uuid = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, uuid.toString());
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) return rs.getString("password_hash");
        } catch (SQLException e) { e.printStackTrace(); }
        return null;
    }

    public boolean updatePassword(UUID uuid, String newPasswordHash) {
        String sql = "UPDATE auth_users SET password_hash = ? WHERE uuid = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, newPasswordHash);
            pstmt.setString(2, uuid.toString());
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    public void incrementFailedAttempts(UUID uuid) {
        String sql = "UPDATE auth_users SET failed_attempts = failed_attempts + 1 WHERE uuid = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, uuid.toString());
            pstmt.executeUpdate();
            checkAndBlockPlayer(uuid);
        } catch (SQLException e) { e.printStackTrace(); }
    }

    private void checkAndBlockPlayer(UUID uuid) {
        String sql = "SELECT failed_attempts FROM auth_users WHERE uuid = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, uuid.toString());
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                int attempts = rs.getInt("failed_attempts");
                int maxAttempts = InternationalAuth.getInstance().getConfig().getInt("security.max-failed-attempts", 5);
                if (attempts >= maxAttempts) {
                    int blockMinutes = InternationalAuth.getInstance().getConfig().getInt("security.block-duration-minutes", 15);
                    LocalDateTime blockUntil = LocalDateTime.now().plusMinutes(blockMinutes);
                    String updateSql = "UPDATE auth_users SET blocked_until = ? WHERE uuid = ?";
                    try (PreparedStatement pstmt2 = connection.prepareStatement(updateSql)) {
                        pstmt2.setString(1, blockUntil.format(formatter));
                        pstmt2.setString(2, uuid.toString());
                        pstmt2.executeUpdate();
                    }
                }
            }
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public boolean isPlayerBlocked(UUID uuid) {
        String sql = "SELECT blocked_until FROM auth_users WHERE uuid = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, uuid.toString());
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                String blockedUntilStr = rs.getString("blocked_until");
                if (blockedUntilStr != null) {
                    LocalDateTime blockedUntil = LocalDateTime.parse(blockedUntilStr, formatter);
                    if (blockedUntil.isAfter(LocalDateTime.now())) return true;
                    else resetFailedAttempts(uuid);
                }
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return false;
    }

    public void resetFailedAttempts(UUID uuid) {
        String sql = "UPDATE auth_users SET failed_attempts = 0, blocked_until = NULL WHERE uuid = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, uuid.toString());
            pstmt.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public String getBlockTimeRemaining(UUID uuid) {
        String sql = "SELECT blocked_until FROM auth_users WHERE uuid = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, uuid.toString());
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                String blockedUntilStr = rs.getString("blocked_until");
                if (blockedUntilStr != null) {
                    LocalDateTime blockedUntil = LocalDateTime.parse(blockedUntilStr, formatter);
                    if (blockedUntil.isAfter(LocalDateTime.now())) {
                        long minutes = java.time.Duration.between(LocalDateTime.now(), blockedUntil).toMinutes();
                        return String.valueOf(minutes);
                    }
                }
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return null;
    }

    public boolean setSecretKey(UUID uuid, String secretKeyHash) {
        String sql = "UPDATE auth_users SET secret_key = ? WHERE uuid = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, secretKeyHash);
            pstmt.setString(2, uuid.toString());
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    public boolean checkSecretKey(UUID uuid, String secretKeyHash) {
        String sql = "SELECT secret_key FROM auth_users WHERE uuid = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, uuid.toString());
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                String storedHash = rs.getString("secret_key");
                return storedHash != null && storedHash.equals(secretKeyHash);
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return false;
    }

    public boolean hasSecretKey(UUID uuid) {
        String sql = "SELECT secret_key FROM auth_users WHERE uuid = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, uuid.toString());
            ResultSet rs = pstmt.executeQuery();
            return rs.next() && rs.getString("secret_key") != null;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    public boolean resetPassword(String playerName, String newPasswordHash) {
        String sql = "UPDATE auth_users SET password_hash = ? WHERE username = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, newPasswordHash);
            pstmt.setString(2, playerName);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    public String getRegistrationDate(UUID uuid) {
        String sql = "SELECT registration_date FROM auth_users WHERE uuid = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, uuid.toString());
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) return rs.getString("registration_date");
        } catch (SQLException e) { e.printStackTrace(); }
        return null;
    }

    public Connection getConnection() { return connection; }

    public void disconnect() {
        try { if (connection != null && !connection.isClosed()) connection.close(); }
        catch (SQLException e) { e.printStackTrace(); }
    }
}