/*
 *  This file is part of Player Analytics (Plan).
 *
 *  Plan is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License v3 as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */
package com.djrapitops.plan.moderation;

import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

/** Platform boundary. All ban-list and command access occurs on the server thread. */
public interface ModerationBackend {
    record BanState(String reason, String source, long created, Long expires) {}

    <T> CompletableFuture<T> submit(Callable<T> action);

    void verifyPlayer(UUID uuid, String name);

    BanState readBan(String kind, String target);

    void ban(String kind, String target, String reason);

    void pardon(String kind, String target);
}
