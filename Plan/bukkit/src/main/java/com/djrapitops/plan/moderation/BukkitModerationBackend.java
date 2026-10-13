/*
 *  This file is part of Player Analytics (Plan).
 *
 *  Plan is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License v3 as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */
package com.djrapitops.plan.moderation;

import net.playeranalytics.plugin.scheduling.RunnableFactory;
import org.bukkit.BanEntry;
import org.bukkit.BanList;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;

import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

/** Console-only, fixed native commands on the platform's server/global scheduler. */
public class BukkitModerationBackend implements ModerationBackend {
    private final Server server;
    private final RunnableFactory scheduler;

    public BukkitModerationBackend(Server server, RunnableFactory scheduler) {
        this.server = server;
        this.scheduler = scheduler;
    }

    @Override
    public <T> CompletableFuture<T> submit(Callable<T> action) {
        CompletableFuture<T> future = new CompletableFuture<>();
        try {
            scheduler.create(() -> {
                try {
                    future.complete(action.call());
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            }).runTask();
        } catch (RuntimeException e) {
            future.completeExceptionally(e);
        }
        return future;
    }

    @Override
    public void verifyPlayer(UUID uuid, String name) {
        OfflinePlayer player;
        try {
            // Modern Paper resolves names from its cache without network lookups. UUID-only
            // OfflinePlayer objects can have no name even when usercache contains the profile.
            Object cached = server.getClass().getMethod("getOfflinePlayerIfCached", String.class).invoke(server, name);
            player = cached instanceof OfflinePlayer offline ? offline : null;
        } catch (NoSuchMethodException legacyPlatform) {
            player = server.getOfflinePlayer(uuid);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("无法安全读取服务器玩家缓存，请人工核对", e);
        }
        if (player == null || !uuid.equals(player.getUniqueId())
                || player.getName() == null || !player.getName().equalsIgnoreCase(name)) {
            throw new IllegalStateException("服务器缓存中的玩家 UUID 与游戏名不匹配，请人工核对");
        }
    }

    @Override
    public BanState readBan(String kind, String target) {
        BanEntry entry = server.getBanList("IP".equals(kind) ? BanList.Type.IP : BanList.Type.NAME).getBanEntry(target);
        if (entry == null) return null;
        // Minecraft serializes these timestamps to whole seconds. Normalize snapshots to
        // that persistent precision so a restart does not look like an administrator edit.
        return new BanState(entry.getReason(), entry.getSource(), seconds(entry.getCreated().getTime()),
                entry.getExpiration() == null ? null : seconds(entry.getExpiration().getTime()));
    }

    private long seconds(long millis) {
        return Math.floorDiv(millis, 1000L) * 1000L;
    }

    @Override
    public void ban(String kind, String target, String reason) {
        command(("IP".equals(kind) ? "minecraft:ban-ip " : "minecraft:ban ") + target + " " + reason);
    }

    @Override
    public void pardon(String kind, String target) {
        command(("IP".equals(kind) ? "minecraft:pardon-ip " : "minecraft:pardon ") + target);
    }

    private void command(String command) {
        if (!server.dispatchCommand(server.getConsoleSender(), command)) {
            throw new IllegalStateException("服务器未执行原生封禁命令");
        }
    }
}
