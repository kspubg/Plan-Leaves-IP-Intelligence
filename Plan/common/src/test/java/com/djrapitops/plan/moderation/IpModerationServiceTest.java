/*
 *  This file is part of Player Analytics (Plan).
 *
 *  Plan is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License v3 as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */
package com.djrapitops.plan.moderation;

import com.djrapitops.plan.delivery.domain.IpInformation;
import com.djrapitops.plan.delivery.web.resolver.exception.BadRequestException;
import com.djrapitops.plan.identification.ServerInfo;
import com.djrapitops.plan.identification.ServerUUID;
import com.djrapitops.plan.settings.config.PlanConfig;
import com.djrapitops.plan.storage.database.DBSystem;
import com.djrapitops.plan.storage.database.Database;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class IpModerationServiceTest {
    @TempDir
    Path folder;
    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();
    private final UUID c = UUID.randomUUID();
    private final ServerUUID server = ServerUUID.randomUUID();
    private final List<IpInformation> records = new ArrayList<>();
    private final FakeBackend backend = new FakeBackend();
    private DBSystem dbSystem;
    private ServerInfo serverInfo;
    private PlanConfig config;
    private ModerationStore store;
    private IpModerationService service;

    @BeforeEach
    void setup() {
        dbSystem = mock(DBSystem.class);
        Database database = mock(Database.class);
        when(dbSystem.getDatabase()).thenReturn(database);
        doReturn(records).when(database).query(any());
        serverInfo = mock(ServerInfo.class);
        when(serverInfo.getServerUUID()).thenReturn(server);
        com.djrapitops.plan.identification.Server identity = mock(com.djrapitops.plan.identification.Server.class);
        when(identity.getIdentifiableName()).thenReturn("SmokeServer");
        when(serverInfo.getServer()).thenReturn(identity);
        config = mock(PlanConfig.class);
        store = new ModerationStore(folder.resolve("ip-moderation.json"));
        service = new IpModerationService(store, dbSystem, serverInfo, config);
        service.setBackend(backend);
        records.add(record(a, "SmokeA", "192.0.2.1", 1));
        records.add(record(b, "SmokeB", "192.0.2.1", 1));
        records.add(record(b, "SmokeB", "192.0.2.2", 1));
        records.add(record(c, "SmokeC", "192.0.2.2", 1));
    }

    private IpInformation record(UUID uuid, String name, String ip, long lastSeen) {
        return new IpInformation(uuid, name, ip, "中国", "广西壮族自治区", "南宁市", "Test", 1, lastSeen);
    }

    private ModerationStore.Operation ban() {
        IpModerationService.Preview preview = service.preview("admin", "192.0.2.1", server.toString());
        return service.ban("admin", preview.id(), "smoke", server.toString()).join();
    }

    @Test
    void exactGroupDoesNotTransitivelyBanOtherIpsOrPlayers() {
        ModerationStore.Operation operation = ban();
        assertEquals(3, operation.items.size());
        assertEquals(3, backend.bans.size());
        assertFalse(backend.bans.containsKey("PLAYER:SmokeC"));
        assertFalse(backend.bans.containsKey("IP:192.0.2.2"));
    }

    @Test
    void undoSurvivesRestartAndKeepsPreexistingBan() {
        ModerationBackend.BanState existing = new ModerationBackend.BanState("old reason", "another admin", 1, null);
        backend.bans.put("PLAYER:SmokeB", existing);
        ModerationStore.Operation operation = ban();
        service = new IpModerationService(new ModerationStore(folder.resolve("ip-moderation.json")), dbSystem, serverInfo, config);
        service.setBackend(backend);
        service.undo("reviewer", operation.id, server.toString()).join();
        assertEquals(Map.of("PLAYER:SmokeB", existing), backend.bans);
    }

    @Test
    void undoKeepsLaterBanAndReportsConflict() {
        ModerationStore.Operation operation = ban();
        ModerationBackend.BanState later = new ModerationBackend.BanState("later ban", "other admin", 999, null);
        backend.bans.put("PLAYER:SmokeA", later);
        ModerationStore.Operation result = service.undo("reviewer", operation.id, server.toString()).join();
        assertEquals("UNDO_PARTIAL", result.status);
        assertEquals(Map.of("PLAYER:SmokeA", later), backend.bans);
    }

    @Test
    void duplicateSubmitCannotReapplyAnUndoneBan() {
        ModerationStore.Operation operation = ban();
        service.ban("admin", operation.id, "again", server.toString()).join();
        assertEquals(3, backend.commands);
        service.undo("admin", operation.id, server.toString()).join();
        service.undo("admin", operation.id, server.toString()).join();
        service.ban("admin", operation.id, "again", server.toString()).join();
        assertEquals(6, backend.commands);
        assertTrue(backend.bans.isEmpty());
    }

    @Test
    void changedPreviewAndWrongServerAreRejected() {
        IpModerationService.Preview preview = service.preview("admin", "192.0.2.1", server.toString());
        records.add(record(c, "SmokeC", "192.0.2.1", 2));
        assertThrows(BadRequestException.class, () -> service.ban("admin", preview.id(), "reason", server.toString()));
        assertThrows(BadRequestException.class, () -> service.preview("admin", "192.0.2.1", UUID.randomUUID().toString()));
        assertEquals(0, backend.commands);
    }

    @Test
    void commandFailureIsReportedWithoutPretendingAllSucceeded() {
        backend.failTarget = "SmokeB";
        ModerationStore.Operation result = ban();
        assertEquals("FAILED", result.items.get(1).status);
        assertEquals("APPLIED", result.items.get(0).status);
        service.undo("admin", result.id, server.toString()).join();
        assertTrue(backend.bans.isEmpty());
    }

    @Test
    void noCommandExecutesWhenJournalCannotBeSaved() throws Exception {
        Path parent = folder.resolve("not-a-directory");
        Files.writeString(parent, "fixture");
        service = new IpModerationService(new ModerationStore(parent.resolve("audit.json")), dbSystem, serverInfo, config);
        service.setBackend(backend);
        assertThrows(java.io.UncheckedIOException.class, this::ban);
        assertEquals(0, backend.commands);
    }

    @Test
    void observationUsesNewConnectionsWithoutTransitiveOrSubnetMatchesByDefault() {
        service.watch("admin", a.toString(), "watch", 30);
        long now = System.currentTimeMillis() + 100;
        records.add(record(a, "SmokeA", "192.0.2.3", now));
        records.add(record(b, "SmokeB", "192.0.2.3", now));
        records.add(record(b, "SmokeB", "198.51.100.1", now));
        records.add(record(c, "SmokeC", "198.51.100.1", now));
        assertEquals(2, ((List<?>) service.status(records).get("alerts")).size());
        service.unwatch(a.toString());
        assertTrue(((List<?>) service.status(records).get("alerts")).isEmpty());
    }

    @Test
    void invalidTargetsAndMultilineReasonsAreRejected() {
        for (String input : List.of("example.org", "127.1", "192.0.2.1/24", "192.0.2.1\nstop", "999.1.1.1")) {
            assertThrows(BadRequestException.class, () -> IpModerationService.literalIp(input));
        }
        assertEquals("2001:db8:0:0:0:0:0:1", IpModerationService.literalIp("2001:db8::1"));
        assertThrows(BadRequestException.class, () -> IpModerationService.reason("reason\nstop"));
    }

    @Test
    void unverifiableAccountPreventsEveryNativeCommandIncludingIpBan() {
        backend.invalidPlayer = true;
        ModerationStore.Operation operation = ban();
        assertEquals("FAILED", operation.status);
        assertEquals(0, backend.commands);
        assertTrue(backend.bans.isEmpty());
    }

    private static class FakeBackend implements ModerationBackend {
        final Map<String, BanState> bans = new HashMap<>();
        int commands;
        String failTarget;
        boolean invalidPlayer;

        @Override
        public <T> CompletableFuture<T> submit(Callable<T> action) {
            try {
                return CompletableFuture.completedFuture(action.call());
            } catch (Exception e) {
                return CompletableFuture.failedFuture(e);
            }
        }

        @Override
        public void verifyPlayer(UUID uuid, String name) {
            if (invalidPlayer) throw new IllegalStateException("fixture invalid identity");
        }

        @Override
        public BanState readBan(String kind, String target) {
            return bans.get(kind + ":" + target);
        }

        @Override
        public void ban(String kind, String target, String reason) {
            if (target.equals(failTarget)) throw new IllegalStateException("fixture command failure");
            commands++;
            bans.put(kind + ":" + target, new BanState(reason, "Server", commands, null));
        }

        @Override
        public void pardon(String kind, String target) {
            commands++;
            bans.remove(kind + ":" + target);
        }
    }
}
