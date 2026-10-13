/*
 *  This file is part of Player Analytics (Plan).
 *
 *  Plan is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License v3 as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */
package com.djrapitops.plan.moderation;

import com.djrapitops.plan.storage.file.PlanFiles;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Fail-closed, versioned, atomic local journal; independent of the player database. */
@Singleton
public class ModerationStore {
    public static class State {
        int version = 1;
        Map<String, Operation> operations = new LinkedHashMap<>();
        Map<String, Watch> watches = new LinkedHashMap<>();
        Map<String, Long> dismissed = new LinkedHashMap<>();
    }

    public static class Operation {
        String id;
        String actor;
        String server;
        String ip;
        String reason;
        long created;
        String status = "PENDING";
        String undoneBy;
        Long undoneAt;
        List<Item> items = new ArrayList<>();
    }

    public static class Item {
        String kind;
        String target;
        String uuid;
        ModerationBackend.BanState before;
        ModerationBackend.BanState applied;
        String status = "PENDING";
        String error;
    }

    public record Watch(String uuid, String name, String note, String actor, long since, long expires,
                        List<String> originalIps) {}

    private final Path file;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private State state;

    @Inject
    public ModerationStore(PlanFiles files) {
        this(files.getDataFolder().toPath().resolve("ip-moderation.json"));
    }

    ModerationStore(Path file) {
        this.file = file;
    }

    synchronized State state() {
        if (state != null) return state;
        try {
            if (Files.exists(file)) {
                com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                if (!json.has("version") || !json.has("operations") || !json.has("watches") || !json.has("dismissed")) {
                    throw new IllegalStateException("审计文件结构不完整，操作已停止");
                }
                state = gson.fromJson(json, State.class);
            } else {
                state = new State();
            }
            if (state == null || state.version != 1 || state.operations == null || state.watches == null || state.dismissed == null) {
                state = null;
                throw new IllegalStateException("审计文件无效，封禁管理已停止。请恢复 ip-moderation.json 的备份。");
            }
            return state;
        } catch (IOException e) {
            throw new UncheckedIOException("无法读取封禁审计文件", e);
        }
    }

    synchronized State snapshot() {
        return gson.fromJson(gson.toJson(state()), State.class);
    }

    synchronized void save() {
        Path pending = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(file.getParent());
            byte[] bytes = gson.toJson(state()).getBytes(StandardCharsets.UTF_8);
            try (FileChannel channel = FileChannel.open(pending, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            Files.move(pending, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            // Do not continue with in-memory changes which were not persisted.
            state = null;
            throw new UncheckedIOException("无法持久化审计记录，操作已停止", e);
        }
    }
}
