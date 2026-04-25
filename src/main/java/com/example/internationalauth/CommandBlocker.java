package com.example.internationalauth;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class CommandBlocker implements Listener {

    private static final Set<String> HIDDEN_COMMANDS = new HashSet<>(Arrays.asList(
            "register", "reg", "login", "l", "changepassword", "setkey"
    ));

    // Скрытие команд игроков
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage().toLowerCase();

        for (String cmd : HIDDEN_COMMANDS) {
            if (message.startsWith("/" + cmd) || message.startsWith("/" + cmd + " ")) {
                event.setCancelled(true);
                String commandLine = event.getMessage().substring(1);

                // Логируем в консоль замаскированно
                String[] parts = commandLine.split(" ");
                if (parts.length > 1) {
                    InternationalAuth.getInstance().getLogger().info(player.getName() + " issued server command: /" + parts[0] + " ******");
                } else {
                    InternationalAuth.getInstance().getLogger().info(player.getName() + " issued server command: /" + commandLine);
                }

                Bukkit.getScheduler().runTask(InternationalAuth.getInstance(), () -> {
                    player.getServer().dispatchCommand(player, commandLine);
                });
                return;
            }
        }
    }

    // Скрытие команд из консоли (админских)
    @EventHandler(priority = EventPriority.LOWEST)
    public void onServerCommand(ServerCommandEvent event) {
        String command = event.getCommand().toLowerCase();

        for (String cmd : HIDDEN_COMMANDS) {
            if (command.startsWith(cmd) || command.startsWith(cmd + " ")) {
                String[] parts = command.split(" ");
                if (parts.length > 1) {
                    InternationalAuth.getInstance().getLogger().info("CONSOLE issued server command: " + parts[0] + " ******");
                } else {
                    InternationalAuth.getInstance().getLogger().info("CONSOLE issued server command: " + command);
                }

                event.setCancelled(true);

                Bukkit.getScheduler().runTask(InternationalAuth.getInstance(), () -> {
                    Bukkit.dispatchCommand(event.getSender(), command);
                });
                return;
            }
        }
    }
}