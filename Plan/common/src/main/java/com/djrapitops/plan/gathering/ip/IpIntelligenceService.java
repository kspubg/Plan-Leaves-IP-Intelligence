/*
 *  This file is part of Player Analytics (Plan).
 *
 *  Plan is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License v3 as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */
package com.djrapitops.plan.gathering.ip;

import com.djrapitops.plan.settings.config.PlanConfig;
import com.djrapitops.plan.settings.config.paths.DataGatheringSettings;
import com.djrapitops.plan.storage.database.DBSystem;
import com.djrapitops.plan.storage.database.transactions.events.StoreIpInformationTransaction;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.net.InetAddress;
import java.util.UUID;

/**
 * Stores IP observations without exposing them through public Plan endpoints.
 */
@Singleton
public class IpIntelligenceService {

    private final PlanConfig config;
    private final DBSystem dbSystem;
    private final Ip2RegionLookup lookup;

    @Inject
    public IpIntelligenceService(PlanConfig config, DBSystem dbSystem, Ip2RegionLookup lookup) {
        this.config = config;
        this.dbSystem = dbSystem;
        this.lookup = lookup;
    }

    public void store(UUID playerUUID, InetAddress address, long time) {
        if (config.isFalse(DataGatheringSettings.IP_INTELLIGENCE)) return;
        IpRegion region = lookup.lookup(address).orElseGet(IpRegion::unknown);
        dbSystem.getDatabase().executeTransaction(
                new StoreIpInformationTransaction(playerUUID, address.getHostAddress(), region, time)
        );
    }
}
