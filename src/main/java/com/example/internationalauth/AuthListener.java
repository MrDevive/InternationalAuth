package com.example.internationalauth;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AuthListener implements Listener {

    private final Map<UUID, BukkitTask> loginTasks = new HashMap<>();

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        boolean isRegistered = InternationalAuth.getInstance().getDatabaseManager().isRegistered(uuid);

        // Проверяем авто-логин по UUID (сохраняется ли сессия между заходами)
        boolean autoLogin = InternationalAuth.getInstance().getConfig().getBoolean("security.auto-login-by-uuid", false);

        if (autoLogin && isRegistered) {
            // Автоматический вход по UUID
            InternationalAuth.getInstance().getSessionMap().put(uuid, System.currentTimeMillis());
            String regDate = InternationalAuth.getInstance().getDatabaseManager().getRegistrationDate(uuid);
            player.sendMessage(InternationalAuth.getInstance().getMessage("login-success"));
            if (regDate != null) {
                player.sendMessage(InternationalAuth.getInstance().getMessage("info-registration-date").replace("{date}", regDate));
            }
            return;
        }

        int timeoutSeconds = InternationalAuth.getInstance().getConfig().getInt("security.login-timeout-seconds", 30);
        BukkitTask task = Bukkit.getScheduler().runTaskLater(InternationalAuth.getInstance(), () -> {
            if (!InternationalAuth.getInstance().getSessionMap().containsKey(uuid)) {
                player.kickPlayer(InternationalAuth.getInstance().getMessage("login-timeout-kick"));
                loginTasks.remove(uuid);
            }
        }, timeoutSeconds * 20L);
        loginTasks.put(uuid, task);

        if (!isRegistered) {
            player.sendMessage(InternationalAuth.getInstance().getMessage("login-not-registered"));
        } else {
            player.sendMessage(InternationalAuth.getInstance().getMessage("login-usage"));
            String regDate = InternationalAuth.getInstance().getDatabaseManager().getRegistrationDate(uuid);
            if (regDate != null) {
                player.sendMessage(InternationalAuth.getInstance().getMessage("info-registration-date").replace("{date}", regDate));
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        InternationalAuth.getInstance().getSessionMap().remove(event.getPlayer().getUniqueId());
        BukkitTask task = loginTasks.remove(event.getPlayer().getUniqueId());
        if (task != null) task.cancel();
    }

    private boolean isAuthorized(Player player) {
        UUID uuid = player.getUniqueId();
        if (!InternationalAuth.getInstance().getSessionMap().containsKey(uuid)) return false;
        long loginTime = InternationalAuth.getInstance().getSessionMap().get(uuid);
        long currentTime = System.currentTimeMillis();
        long timeoutMillis = InternationalAuth.getInstance().getSessionTimeoutMinutes() * 60 * 1000L;
        if (currentTime - loginTime > timeoutMillis) {
            InternationalAuth.getInstance().getSessionMap().remove(uuid);
            player.sendMessage(InternationalAuth.getInstance().getMessage("session-expired"));
            return false;
        }
        return true;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        boolean isReg = InternationalAuth.getInstance().getDatabaseManager().isRegistered(player.getUniqueId());
        if (!isAuthorized(player) && isReg) {
            if (event.getFrom().getBlockX() != event.getTo().getBlockX() ||
                    event.getFrom().getBlockZ() != event.getTo().getBlockZ()) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!isAuthorized(player)) {
            event.setCancelled(true);
            player.sendMessage(InternationalAuth.getInstance().getMessage("not-authorized"));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!isAuthorized(event.getPlayer())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(InternationalAuth.getInstance().getMessage("not-authorized"));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!isAuthorized(event.getPlayer())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(InternationalAuth.getInstance().getMessage("not-authorized"));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            if (!isAuthorized(player)) {
                event.setCancelled(true);
                player.sendMessage(InternationalAuth.getInstance().getMessage("not-authorized"));
            }
        }
    }
}