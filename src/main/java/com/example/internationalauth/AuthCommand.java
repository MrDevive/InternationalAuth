package com.example.internationalauth;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;

public class AuthCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String commandName = cmd.getName().toLowerCase();

        if (commandName.equals("register") || commandName.equals("reg")) {
            return handleRegister(sender, args);
        } else if (commandName.equals("login") || commandName.equals("l")) {
            return handleLogin(sender, args);
        } else if (commandName.equals("changepassword")) {
            return handleChangePasswordWithKey(sender, args);
        } else if (commandName.equals("setkey")) {
            return handleSetKey(sender, args);
        } else if (commandName.equals("auth")) {
            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                return handleAuthReload(sender, args);
            } else if (args.length > 0 && args[0].equalsIgnoreCase("reset")) {
                return handleAuthReset(sender, args);
            } else if (args.length > 0 && args[0].equalsIgnoreCase("clearcache")) {
                return handleClearCache(sender, args);
            } else if (args.length > 0 && args[0].equalsIgnoreCase("accounts")) {
                return handleAccounts(sender, args);
            } else {
                sender.sendMessage("§cИспользование: /auth reload | /auth reset <ник> | /auth clearcache | /auth accounts <ник>");
                return true;
            }
        }
        return true;
    }

    private boolean handleClearCache(CommandSender sender, String[] args) {
        if (!sender.hasPermission("internationalauth.admin")) {
            sender.sendMessage("§cНедостаточно прав");
            return true;
        }
        InternationalAuth.getInstance().getDatabaseManager().clearCache();
        sender.sendMessage("§aКэш паролей очищен!");
        return true;
    }

    private boolean handleAccounts(CommandSender sender, String[] args) {
        if (!sender.hasPermission("internationalauth.admin")) {
            sender.sendMessage("§cНедостаточно прав");
            return true;
        }
        if (args.length != 2) {
            sender.sendMessage("§cИспользование: /auth accounts <ник>");
            return true;
        }
        String playerName = args[1];
        sender.sendMessage("§6§lИнформация об аккаунтах игрока §e" + playerName + "§6§l:");
        sender.sendMessage("§7Поиск аккаунтов по IP...");

        InternationalAuth.getInstance().getDatabaseManager().getPlayerAccountsInfo(playerName).thenAccept(result -> {
            if (!(boolean) result.getOrDefault("found", false)) {
                sender.sendMessage("§cИгрок " + playerName + " не найден в базе данных!");
                return;
            }
            String ip = (String) result.get("ip");
            int total = (int) result.getOrDefault("total", 0);
            sender.sendMessage("§7━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            sender.sendMessage("§eIP адрес: §f" + ip);
            sender.sendMessage("§eВсего аккаунтов с этого IP: §f" + total);
            sender.sendMessage("§7━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            if (total > 0) {
                @SuppressWarnings("unchecked")
                java.util.List<Map<String, String>> accounts = (java.util.List<Map<String, String>>) result.get("accounts");
                sender.sendMessage("§6§lСписок аккаунтов:");
                for (int i = 0; i < accounts.size(); i++) {
                    Map<String, String> acc = accounts.get(i);
                    sender.sendMessage("§7" + (i + 1) + ". §f" + acc.get("username"));
                    sender.sendMessage("§8   Регистрация: §7" + acc.get("registration_date"));
                    sender.sendMessage("§8   Последний вход: §7" + acc.get("last_login"));
                    sender.sendMessage("§8   Последний IP: §7" + acc.get("last_ip"));
                    if (i < accounts.size() - 1) sender.sendMessage("§8   §7-----");
                }
                if (total > 1) {
                    sender.sendMessage("§7━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                    sender.sendMessage("§cВнимание! У игрока есть мульти-аккаунты!");
                    sender.sendMessage("§7Рекомендуется проверить игрока на соблюдение правил.");
                }
            }
            sender.sendMessage("§7━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        }).exceptionally(throwable -> {
            sender.sendMessage("§cОшибка при поиске: " + throwable.getMessage());
            return null;
        });
        return true;
    }

    private boolean handleRegister(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Только для игроков");
            return true;
        }
        if (args.length != 2) {
            player.sendMessage(InternationalAuth.getInstance().getMessage("register-usage"));
            return true;
        }
        String playerIP = player.getAddress().getAddress().getHostAddress();

        InternationalAuth.getInstance().getDatabaseManager().isRegisteredAsync(player.getUniqueId()).thenAccept(isRegistered -> {
            if (isRegistered) {
                player.sendMessage(InternationalAuth.getInstance().getMessage("register-already-registered"));
                return;
            }
            InternationalAuth.getInstance().getDatabaseManager().getRegistrationCountByIPAsync(playerIP).thenAccept(count -> {
                int maxReg = InternationalAuth.getInstance().getConfig().getInt("security.max-registrations-per-ip", 3);
                if (count >= maxReg && !player.hasPermission("internationalauth.bypass.iplimit")) {
                    player.sendMessage(InternationalAuth.getInstance().getMessage("register-ip-limit")
                            .replace("{current}", String.valueOf(count))
                            .replace("{max}", String.valueOf(maxReg)));
                    return;
                }
                Bukkit.getScheduler().runTask(InternationalAuth.getInstance(), () -> {
                    if (!args[0].equals(args[1])) {
                        player.sendMessage(InternationalAuth.getInstance().getMessage("register-passwords-mismatch"));
                        return;
                    }
                    if (args[0].length() < PasswordUtil.getMinLength()) {
                        player.sendMessage(InternationalAuth.getInstance().getMessage("register-password-too-short")
                                .replace("{min}", String.valueOf(PasswordUtil.getMinLength())));
                        return;
                    }
                    if (args[0].length() > PasswordUtil.getMaxLength()) {
                        player.sendMessage(InternationalAuth.getInstance().getMessage("register-password-too-long")
                                .replace("{max}", String.valueOf(PasswordUtil.getMaxLength())));
                        return;
                    }
                    if (PasswordUtil.isPasswordBlacklisted(args[0])) {
                        player.sendMessage(InternationalAuth.getInstance().getMessage("register-password-blacklisted"));
                        return;
                    }
                    if (!PasswordUtil.isPasswordMatchPattern(args[0])) {
                        String description = PasswordUtil.getPatternDescription();
                        player.sendMessage(InternationalAuth.getInstance().getMessage("register-password-invalid-pattern")
                                .replace("{description}", description));
                        return;
                    }
                    String passwordHash = PasswordUtil.hashPassword(args[0]);
                    InternationalAuth.getInstance().getDatabaseManager()
                            .registerUserAsync(player.getUniqueId(), player.getName(), passwordHash, playerIP)
                            .thenAccept(success -> {
                                if (success) {
                                    player.sendMessage(InternationalAuth.getInstance().getMessage("register-success"));
                                    // АВТОМАТИЧЕСКИЙ ВХОД ПОСЛЕ РЕГИСТРАЦИИ
                                    InternationalAuth.getInstance().getSessionMap().put(player.getUniqueId(), System.currentTimeMillis());
                                    player.sendMessage(InternationalAuth.getInstance().getMessage("login-success"));
                                } else {
                                    player.sendMessage(InternationalAuth.getInstance().getMessage("database-error"));
                                }
                            });
                });
            });
        });
        return true;
    }

    private boolean handleLogin(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) return true;
        if (args.length != 1) {
            player.sendMessage(InternationalAuth.getInstance().getMessage("login-usage"));
            return true;
        }

        // Если уже авторизован
        if (InternationalAuth.getInstance().getSessionMap().containsKey(player.getUniqueId())) {
            player.sendMessage(InternationalAuth.getInstance().getMessage("login-already-authorized"));
            return true;
        }

        Bukkit.getScheduler().runTaskAsynchronously(InternationalAuth.getInstance(), () -> {
            if (InternationalAuth.getInstance().getDatabaseManager().isPlayerBlocked(player.getUniqueId())) {
                String timeLeft = InternationalAuth.getInstance().getDatabaseManager().getBlockTimeRemaining(player.getUniqueId());
                player.sendMessage(InternationalAuth.getInstance().getMessage("blocked-try-later")
                        .replace("{minutes}", timeLeft != null ? timeLeft : "15"));
                return;
            }
            InternationalAuth.getInstance().getDatabaseManager().getPasswordHashWithCacheAsync(player.getUniqueId()).thenAccept(storedHash -> {
                if (storedHash == null) {
                    player.sendMessage(InternationalAuth.getInstance().getMessage("login-not-registered"));
                    return;
                }
                String inputHash = PasswordUtil.hashPassword(args[0]);
                if (storedHash.equals(inputHash)) {
                    String playerIP = player.getAddress().getAddress().getHostAddress();
                    InternationalAuth.getInstance().getDatabaseManager().updateLastLoginAndIPAsync(player.getUniqueId(), playerIP);
                    InternationalAuth.getInstance().getSessionMap().put(player.getUniqueId(), System.currentTimeMillis());
                    player.sendMessage(InternationalAuth.getInstance().getMessage("login-success"));
                } else {
                    InternationalAuth.getInstance().getDatabaseManager().incrementFailedAttemptsAsync(player.getUniqueId());
                    try {
                        String sql = "SELECT failed_attempts FROM auth_users WHERE uuid = ?";
                        PreparedStatement pstmt = InternationalAuth.getInstance().getDatabaseManager().getConnection().prepareStatement(sql);
                        pstmt.setString(1, player.getUniqueId().toString());
                        ResultSet rs = pstmt.executeQuery();
                        if (rs.next()) {
                            int attempts = rs.getInt("failed_attempts");
                            int maxAttempts = InternationalAuth.getInstance().getConfig().getInt("security.max-failed-attempts", 5);
                            int remaining = maxAttempts - attempts;
                            if (remaining > 0) {
                                player.sendMessage(InternationalAuth.getInstance().getMessage("attempts-remaining")
                                        .replace("{attempts}", String.valueOf(remaining)));
                            }
                        }
                    } catch (Exception e) { e.printStackTrace(); }
                    player.sendMessage(InternationalAuth.getInstance().getMessage("login-wrong-password"));
                }
            });
        });
        return true;
    }

    private boolean handleChangePasswordWithKey(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) return true;
        if (args.length != 2) {
            player.sendMessage(InternationalAuth.getInstance().getMessage("changepassword-key-usage"));
            return true;
        }
        String newPassword = args[0];
        String secretKey = args[1];
        Bukkit.getScheduler().runTaskAsynchronously(InternationalAuth.getInstance(), () -> {
            if (newPassword.length() < PasswordUtil.getMinLength()) {
                player.sendMessage(InternationalAuth.getInstance().getMessage("register-password-too-short")
                        .replace("{min}", String.valueOf(PasswordUtil.getMinLength())));
                return;
            }
            if (newPassword.length() > PasswordUtil.getMaxLength()) {
                player.sendMessage(InternationalAuth.getInstance().getMessage("register-password-too-long")
                        .replace("{max}", String.valueOf(PasswordUtil.getMaxLength())));
                return;
            }
            if (PasswordUtil.isPasswordBlacklisted(newPassword)) {
                player.sendMessage(InternationalAuth.getInstance().getMessage("register-password-blacklisted"));
                return;
            }
            if (!PasswordUtil.isPasswordMatchPattern(newPassword)) {
                String description = PasswordUtil.getPatternDescription();
                player.sendMessage(InternationalAuth.getInstance().getMessage("register-password-invalid-pattern")
                        .replace("{description}", description));
                return;
            }
            String keyHash = PasswordUtil.hashPassword(secretKey);
            InternationalAuth.getInstance().getDatabaseManager().checkSecretKeyAsync(player.getUniqueId(), keyHash).thenAccept(isValid -> {
                if (!isValid) {
                    player.sendMessage(InternationalAuth.getInstance().getMessage("changepassword-wrong-key"));
                    return;
                }
                InternationalAuth.getInstance().getDatabaseManager()
                        .updatePasswordWithCacheAsync(player.getUniqueId(), PasswordUtil.hashPassword(newPassword))
                        .thenRun(() -> {
                            player.sendMessage(InternationalAuth.getInstance().getMessage("changepassword-success"));
                            // После смены пароля — остаёмся авторизованным
                        });
            });
        });
        return true;
    }

    private boolean handleSetKey(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) return true;
        if (args.length != 2) {
            player.sendMessage(InternationalAuth.getInstance().getMessage("setkey-usage"));
            return true;
        }
        if (InternationalAuth.getInstance().getDatabaseManager().hasSecretKey(player.getUniqueId())) {
            player.sendMessage(InternationalAuth.getInstance().getMessage("setkey-already-set"));
            return true;
        }
        if (!args[0].equals(args[1])) {
            player.sendMessage(InternationalAuth.getInstance().getMessage("setkey-mismatch"));
            return true;
        }
        if (args[0].length() < 8) {
            player.sendMessage(InternationalAuth.getInstance().getMessage("setkey-too-short"));
            return true;
        }
        if (args[0].length() > 16) {
            player.sendMessage(InternationalAuth.getInstance().getMessage("setkey-too-long"));
            return true;
        }
        String keyHash = PasswordUtil.hashPassword(args[0]);
        InternationalAuth.getInstance().getDatabaseManager().setSecretKey(player.getUniqueId(), keyHash);
        player.sendMessage(InternationalAuth.getInstance().getMessage("setkey-success"));
        return true;
    }

    private boolean handleAuthReload(CommandSender sender, String[] args) {
        if (!sender.hasPermission("internationalauth.admin")) {
            sender.sendMessage("§cНедостаточно прав");
            return true;
        }
        InternationalAuth.getInstance().reloadPlugin();
        sender.sendMessage(InternationalAuth.getInstance().getMessage("reload-success"));
        return true;
    }

    private boolean handleAuthReset(CommandSender sender, String[] args) {
        if (!sender.hasPermission("internationalauth.admin")) {
            sender.sendMessage("§cНедостаточно прав");
            return true;
        }
        if (args.length != 2) {
            sender.sendMessage(InternationalAuth.getInstance().getMessage("auth-reset-usage"));
            return true;
        }
        String playerName = args[1];
        String newPassword = generateRandomPassword();
        boolean success = InternationalAuth.getInstance().getDatabaseManager()
                .resetPassword(playerName, PasswordUtil.hashPassword(newPassword));
        if (success) {
            sender.sendMessage(InternationalAuth.getInstance().getMessage("auth-reset-success").replace("{player}", playerName));
            sender.sendMessage("§aНовый пароль: §f" + newPassword);
        } else {
            sender.sendMessage(InternationalAuth.getInstance().getMessage("auth-player-not-found"));
        }
        return true;
    }

    private String generateRandomPassword() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) sb.append(chars.charAt((int)(Math.random() * chars.length())));
        return sb.toString();
    }
}