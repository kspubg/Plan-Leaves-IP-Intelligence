/*
 *  This file is part of Player Analytics (Plan).
 *
 *  Plan is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License v3 as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */
package com.djrapitops.plan.moderation;

import org.bukkit.OfflinePlayer;
import org.bukkit.BanEntry;
import org.bukkit.BanList;
import org.bukkit.Server;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BukkitModerationBackendTest {
    public interface CachedPlayerLookup {
        OfflinePlayer getOfflinePlayerIfCached(String name);
    }

    @Test
    void banSnapshotsUseMinecraftPersistentSecondPrecision() {
        Server server = mock(Server.class);
        BanList list = mock(BanList.class);
        BanEntry entry = mock(BanEntry.class);
        when(server.getBanList(BanList.Type.IP)).thenReturn(list);
        when(list.getBanEntry("192.0.2.1")).thenReturn(entry);
        when(entry.getReason()).thenReturn("fixture");
        when(entry.getSource()).thenReturn("Server");
        when(entry.getCreated()).thenReturn(new Date(123456));
        when(entry.getExpiration()).thenReturn(new Date(654321));
        BukkitModerationBackend backend = new BukkitModerationBackend(server, null);
        assertEquals(new ModerationBackend.BanState("fixture", "Server", 123000, 654000L),
                backend.readBan("IP", "192.0.2.1"));
    }

    @Test
    void modernCacheValidatesIdentityWithoutUuidOnlyOrNetworkLookup() {
        Server server = mock(Server.class, withSettings().extraInterfaces(CachedPlayerLookup.class));
        OfflinePlayer player = mock(OfflinePlayer.class);
        UUID uuid = UUID.randomUUID();
        when(((CachedPlayerLookup) server).getOfflinePlayerIfCached("SmokeA")).thenReturn(player);
        when(player.getName()).thenReturn("SmokeA");
        when(player.getUniqueId()).thenReturn(uuid);
        BukkitModerationBackend backend = new BukkitModerationBackend(server, null);
        backend.verifyPlayer(uuid, "SmokeA");
        assertThrows(IllegalStateException.class, () -> backend.verifyPlayer(UUID.randomUUID(), "SmokeA"));
        assertThrows(IllegalStateException.class, () -> backend.verifyPlayer(uuid, "Missing"));
        verify(server, never()).getOfflinePlayer(anyString());
        verify(server, never()).getOfflinePlayer(any(UUID.class));
    }

    @Test
    void legacyPlatformUsesUuidAndStillRejectsMissingName() {
        Server server = mock(Server.class);
        OfflinePlayer player = mock(OfflinePlayer.class);
        UUID uuid = UUID.randomUUID();
        when(server.getOfflinePlayer(uuid)).thenReturn(player);
        when(player.getName()).thenReturn("SmokeA");
        when(player.getUniqueId()).thenReturn(uuid);
        BukkitModerationBackend backend = new BukkitModerationBackend(server, null);
        backend.verifyPlayer(uuid, "SmokeA");
        when(player.getName()).thenReturn(null);
        assertThrows(IllegalStateException.class, () -> backend.verifyPlayer(uuid, "SmokeA"));
        verify(server, never()).getOfflinePlayer(anyString());
    }
}
