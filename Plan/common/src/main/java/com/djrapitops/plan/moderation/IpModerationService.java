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
import com.djrapitops.plan.settings.config.PlanConfig;
import com.djrapitops.plan.settings.config.paths.DataGatheringSettings;
import com.djrapitops.plan.storage.database.DBSystem;
import com.djrapitops.plan.storage.database.queries.objects.IpInformationQueries;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Reviewed IP groups, native bans, guarded undo, and non-transitive observation alerts. */
@Singleton
public class IpModerationService {
    public record Account(String uuid, String name, long firstSeen, long lastSeen) {}
    public record Preview(String id, String actor, String ip, String server, List<Account> accounts, long expires) {}
    public record Alert(String key, String watchedName, String uuid, String name, String ip, String evidence,
                        String note, long lastSeen) {}

    private final ModerationStore store;
    private final DBSystem dbSystem;
    private final ServerInfo serverInfo;
    private final PlanConfig config;
    private final Map<String, Preview> previews = new HashMap<>();
    private final Set<String> running = new HashSet<>();
    private volatile ModerationBackend backend;

    @Inject
    public IpModerationService(ModerationStore store, DBSystem dbSystem, ServerInfo serverInfo, PlanConfig config) {
        this.store = store;
        this.dbSystem = dbSystem;
        this.serverInfo = serverInfo;
        this.config = config;
    }

    public void setBackend(ModerationBackend backend) {
        this.backend = backend;
    }

    public List<IpInformation> records() {
        return dbSystem.getDatabase().query(IpInformationQueries.fetchAll());
    }

    public String serverId() {
        return serverInfo.getServerUUID().toString();
    }

    public Map<String, Object> status(List<IpInformation> records) {
        synchronized (store) {
            ModerationStore.State copy = store.snapshot();
            List<ModerationStore.Operation> operations = new ArrayList<>(copy.operations.values());
            Collections.reverse(operations);
            return Map.of("supported", backend != null, "server", serverId(),
                    "serverName", serverInfo.getServer().getIdentifiableName(),
                    "operations", operations, "watches", copy.watches.values(),
                    "alerts", alerts(copy, records),
                    "subnetAlerts", config.isTrue(DataGatheringSettings.IP_INTELLIGENCE_SUBNET_ALERTS));
        }
    }

    public Preview preview(String actor, String ip, String server) {
        requireServer(server);
        String normalized = literalIp(ip);
        List<Account> accounts = accounts(normalized, records());
        if (accounts.isEmpty()) throw new BadRequestException("所选 IP 没有账号记录");
        if (accounts.size() > 200) throw new BadRequestException("该 IP 关联账号超过 200 个，请先人工排查共享出口");
        Preview preview = new Preview(UUID.randomUUID().toString(), actor, normalized, server, accounts,
                System.currentTimeMillis() + 300_000);
        synchronized (previews) {
            previews.values().removeIf(value -> value.expires() < System.currentTimeMillis());
            if (previews.size() >= 1000) throw new BadRequestException("预览过多，请稍后重试");
            previews.put(preview.id(), preview);
        }
        return preview;
    }

    public CompletableFuture<ModerationStore.Operation> ban(String actor, String previewId, String reason, String server) {
        requireServer(server);
        String checkedReason = reason(reason);
        Preview preview;
        synchronized (previews) {
            preview = previews.get(previewId);
        }
        synchronized (store) {
            ModerationStore.Operation existing = store.state().operations.get(previewId);
            if (existing != null) {
                if (!existing.actor.equals(actor)) throw new BadRequestException("操作不属于当前用户");
                return CompletableFuture.completedFuture(store.snapshot().operations.get(previewId));
            }
        }
        if (preview == null || !preview.actor().equals(actor) || !preview.server().equals(server)
                || preview.expires() < System.currentTimeMillis()) {
            throw new BadRequestException("预览已失效，请重新打开封禁窗口");
        }
        List<Account> current = accounts(preview.ip(), records());
        if (!sameAccounts(preview.accounts(), current)) {
            throw new BadRequestException("关联账号或名字已变化，请重新预览后确认");
        }
        ModerationStore.Operation operation = new ModerationStore.Operation();
        operation.id = preview.id();
        operation.actor = actor;
        operation.server = server;
        operation.ip = preview.ip();
        operation.reason = checkedReason;
        operation.created = System.currentTimeMillis();
        for (Account account : current) {
            ModerationStore.Item item = new ModerationStore.Item();
            item.kind = "PLAYER";
            item.target = account.name();
            item.uuid = account.uuid();
            operation.items.add(item);
        }
        ModerationStore.Item ipItem = new ModerationStore.Item();
        ipItem.kind = "IP";
        ipItem.target = preview.ip();
        operation.items.add(ipItem);
        synchronized (store) {
            if (store.state().operations.containsKey(operation.id)) {
                return CompletableFuture.completedFuture(store.snapshot().operations.get(operation.id));
            }
            store.state().operations.put(operation.id, operation);
            store.save();
            running.add(operation.id);
        }
        return backend.submit(() -> executeBan(operation)).whenComplete((result, error) -> {
            synchronized (store) {
                running.remove(operation.id);
            }
        });
    }

    private ModerationStore.Operation executeBan(ModerationStore.Operation operation) {
        synchronized (store) {
            // Refuse the whole group before any command if its identities cannot be verified.
            for (ModerationStore.Item item : operation.items) {
                if (item.uuid == null) continue;
                try {
                    backend.verifyPlayer(UUID.fromString(item.uuid), item.target);
                } catch (RuntimeException e) {
                    item.status = "FAILED";
                    item.error = e.getMessage();
                    operation.status = "FAILED";
                    store.save();
                    return store.snapshot().operations.get(operation.id);
                }
            }
            for (ModerationStore.Item item : operation.items) {
                applyItem(operation, item);
                store.save();
            }
            operation.status = "FINISHED";
            store.save();
            return store.snapshot().operations.get(operation.id);
        }
    }

    private void applyItem(ModerationStore.Operation operation, ModerationStore.Item item) {
        try {
            if (item.uuid != null) backend.verifyPlayer(UUID.fromString(item.uuid), item.target);
            item.before = backend.readBan(item.kind, item.target);
            if (item.before != null) {
                item.status = "PREEXISTING";
                return;
            }
            item.status = "APPLYING";
            store.save();
            String markedReason = operation.reason + " [Plan:" + operation.id + "]";
            backend.ban(item.kind, item.target, markedReason);
            item.applied = backend.readBan(item.kind, item.target);
            if (item.applied == null || !markedReason.equals(item.applied.reason())) {
                throw new IllegalStateException("原生命令执行后没有确认到本次封禁；请人工检查");
            }
            item.status = "APPLIED";
        } catch (RuntimeException e) {
            // Durability failures must stop the whole operation immediately.
            if (e instanceof java.io.UncheckedIOException) throw e;
            item.status = "FAILED";
            item.error = e.getMessage();
        }
    }

    public CompletableFuture<ModerationStore.Operation> undo(String actor, String id, String server) {
        requireServer(server);
        synchronized (store) {
            ModerationStore.Operation operation = store.state().operations.get(id);
            if (operation == null || !server.equals(operation.server)) throw new BadRequestException("找不到本服务器的操作");
            if (running.contains(id)) throw new BadRequestException("操作仍在执行，请稍后刷新");
            if ("UNDONE".equals(operation.status)) return CompletableFuture.completedFuture(store.snapshot().operations.get(id));
            running.add(id);
        }
        return backend.submit(() -> executeUndo(actor, id)).whenComplete((result, error) -> {
            synchronized (store) {
                running.remove(id);
            }
        });
    }

    private ModerationStore.Operation executeUndo(String actor, String id) {
        synchronized (store) {
            ModerationStore.Operation operation = store.state().operations.get(id);
            operation.undoneBy = actor;
            operation.undoneAt = System.currentTimeMillis();
            store.save();
            for (ModerationStore.Item item : operation.items) {
                undoItem(item);
                store.save();
            }
            operation.status = operation.items.stream().anyMatch(item -> "CONFLICT".equals(item.status)
                    || "UNDO_FAILED".equals(item.status) || "APPLYING".equals(item.status)
                    || "PENDING".equals(item.status) || "FAILED".equals(item.status)) ? "UNDO_PARTIAL" : "UNDONE";
            store.save();
            return store.snapshot().operations.get(id);
        }
    }

    private void undoItem(ModerationStore.Item item) {
        if (item.before != null || item.applied == null || "UNDONE".equals(item.status)) return;
        try {
            if (item.uuid != null) backend.verifyPlayer(UUID.fromString(item.uuid), item.target);
            ModerationBackend.BanState current = backend.readBan(item.kind, item.target);
            if (current == null) {
                item.status = "UNDONE";
                return;
            }
            if (!item.applied.equals(current)) {
                item.status = "CONFLICT";
                item.error = "封禁已被其他操作修改，未解除";
                return;
            }
            // Persist ownership and intent before executing pardon, also safe after a crash.
            item.status = "UNDOING";
            store.save();
            backend.pardon(item.kind, item.target);
            if (backend.readBan(item.kind, item.target) != null) throw new IllegalStateException("原生解封未成功");
            item.status = "UNDONE";
            item.error = null;
        } catch (RuntimeException e) {
            if (e instanceof java.io.UncheckedIOException) throw e;
            item.status = "UNDO_FAILED";
            item.error = e.getMessage();
        }
    }

    public void watch(String actor, String uuid, String note, int days) {
        if (days < 1 || days > 365) throw new BadRequestException("观察天数必须为 1 到 365");
        UUID player = parseUuid(uuid);
        List<IpInformation> history = records().stream().filter(record -> record.getPlayerUUID().equals(player)).toList();
        if (history.isEmpty()) throw new BadRequestException("玩家没有 IP 记录");
        ModerationStore.Watch watch = new ModerationStore.Watch(uuid, history.get(0).getPlayerName(), reason(note), actor,
                System.currentTimeMillis(), System.currentTimeMillis() + days * 86_400_000L,
                history.stream().map(IpInformation::getIpAddress).distinct().toList());
        synchronized (store) {
            if (store.state().watches.size() >= 200 && !store.state().watches.containsKey(uuid)) {
                throw new BadRequestException("观察名单已达到 200 个，请先移除过期项");
            }
            store.state().watches.put(uuid, watch);
            store.save();
        }
    }

    public void unwatch(String uuid) {
        parseUuid(uuid);
        synchronized (store) {
            store.state().watches.remove(uuid);
            store.save();
        }
    }

    public void dismiss(String key) {
        if (key == null || key.length() > 180) throw new BadRequestException("无效的提示标识");
        synchronized (store) {
            store.state().dismissed.put(key, System.currentTimeMillis());
            store.save();
        }
    }

    private List<Alert> alerts(ModerationStore.State state, List<IpInformation> records) {
        List<Alert> alerts = new ArrayList<>();
        boolean subnetAlerts = config.isTrue(DataGatheringSettings.IP_INTELLIGENCE_SUBNET_ALERTS);
        for (ModerationStore.Watch watch : state.watches.values()) {
            if (watch.expires() < System.currentTimeMillis()) continue;
            Set<String> ips = new HashSet<>(watch.originalIps());
            records.stream().filter(record -> record.getPlayerUUID().toString().equals(watch.uuid()))
                    .map(IpInformation::getIpAddress).forEach(ips::add);
            for (IpInformation record : records) {
                addAlert(alerts, state, watch, record, ips, subnetAlerts);
            }
        }
        alerts.sort((a, b) -> Long.compare(b.lastSeen(), a.lastSeen()));
        return alerts;
    }

    private void addAlert(List<Alert> alerts, ModerationStore.State state, ModerationStore.Watch watch,
                          IpInformation record, Set<String> ips, boolean subnetAlerts) {
        if (record.getLastSeen() < watch.since()) return;
        boolean samePlayer = record.getPlayerUUID().toString().equals(watch.uuid());
        String evidence;
        if (samePlayer && !watch.originalIps().contains(record.getIpAddress())) {
            evidence = "观察账号使用新 IP";
        } else if (!samePlayer && ips.contains(record.getIpAddress())) {
            evidence = "共享观察账号的精确 IP（需人工核查）";
        } else if (!samePlayer && subnetAlerts && ips.stream().anyMatch(ip -> sameSubnet(ip, record.getIpAddress()))) {
            evidence = "相同 IPv4 /24 网段（低可信）";
        } else {
            return;
        }
        String key = watch.uuid() + "|" + record.getPlayerUUID() + "|" + record.getIpAddress() + "|" + evidence;
        if (record.getLastSeen() <= state.dismissed.getOrDefault(key, 0L)) return;
        alerts.add(new Alert(key, watch.name(), record.getPlayerUUID().toString(), record.getPlayerName(),
                record.getIpAddress(), evidence, watch.note(), record.getLastSeen()));
    }

    private boolean sameSubnet(String one, String two) {
        return one.contains(".") && two.contains(".") && !one.contains(":") && !two.contains(":")
                && one.substring(0, one.lastIndexOf('.')).equals(two.substring(0, two.lastIndexOf('.')));
    }

    private void requireServer(String server) {
        if (backend == null) throw new BadRequestException("此平台不支持原生封禁管理，请访问目标 Leaves/Paper 服务器的面板");
        if (!serverId().equals(server)) throw new BadRequestException("只允许操作当前 Plan 实例所在的服务器");
    }

    static List<Account> accounts(String ip, List<IpInformation> records) {
        Map<UUID, Account> accounts = new LinkedHashMap<>();
        for (IpInformation record : records) {
            if (!ip.equals(literalIp(record.getIpAddress()))) continue;
            String name = record.getPlayerName();
            if (name == null || !name.matches("[A-Za-z0-9_]{1,16}")) throw new BadRequestException("存在无效玩家名，无法安全调用原生命令");
            accounts.put(record.getPlayerUUID(), new Account(record.getPlayerUUID().toString(), name,
                    record.getFirstSeen(), record.getLastSeen()));
        }
        return new ArrayList<>(accounts.values());
    }

    private boolean sameAccounts(List<Account> one, List<Account> two) {
        Set<String> first = new HashSet<>();
        Set<String> second = new HashSet<>();
        one.forEach(account -> first.add(account.uuid() + ":" + account.name()));
        two.forEach(account -> second.add(account.uuid() + ":" + account.name()));
        return first.equals(second);
    }

    static String reason(String reason) {
        if (reason == null || reason.trim().isEmpty() || reason.length() > 200
                || reason.chars().anyMatch(Character::isISOControl)) throw new BadRequestException("说明不能为空，最多 200 字，且不能含控制字符或换行");
        return reason.trim();
    }

    static UUID parseUuid(String uuid) {
        try {
            return UUID.fromString(Objects.requireNonNull(uuid));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BadRequestException("无效的玩家 UUID");
        }
    }

    static String literalIp(String ip) {
        if (ip == null || ip.length() > 45 || !ip.matches("[0-9a-fA-F:.]+")) throw new BadRequestException("必须提供 IP 地址，不能使用主机名、网段或命令");
        if (!ip.contains(":") && !ip.matches("(0|[1-9][0-9]{0,2})(\\.(0|[1-9][0-9]{0,2})){3}")) {
            throw new BadRequestException("IPv4 地址格式无效");
        }
        try {
            return InetAddress.getByName(ip).getHostAddress();
        } catch (UnknownHostException e) {
            throw new BadRequestException("IP 地址格式无效");
        }
    }
}
