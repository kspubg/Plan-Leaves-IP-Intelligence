/*
 *  This file is part of Player Analytics (Plan).
 *
 *  Plan is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License v3 as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */
package com.djrapitops.plan.storage.database.queries;

import com.djrapitops.plan.delivery.domain.IpInformation;
import com.djrapitops.plan.gathering.ip.IpRegion;
import com.djrapitops.plan.storage.database.DatabaseTestPreparer;
import com.djrapitops.plan.storage.database.queries.objects.IpInformationQueries;
import com.djrapitops.plan.storage.database.transactions.commands.CombineUserTransaction;
import com.djrapitops.plan.storage.database.transactions.commands.RemovePlayerTransaction;
import com.djrapitops.plan.storage.database.transactions.events.StoreIpInformationTransaction;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public interface IpInformationQueriesTest extends DatabaseTestPreparer {

    @Test
    default void storesHistoricalAddressesAndUpdatesLastSeen() {
        UUID player = UUID.randomUUID();
        IpRegion firstRegion = new IpRegion("中国", "广西壮族自治区", "南宁市", "ISP");
        IpRegion secondRegion = new IpRegion("中国", "内蒙古自治区", "包头市", "ISP");
        db().executeTransaction(new StoreIpInformationTransaction(player, "192.0.2.1", firstRegion, 100L));
        db().executeTransaction(new StoreIpInformationTransaction(player, "192.0.2.1", secondRegion, 200L));
        db().executeTransaction(new StoreIpInformationTransaction(player, "192.0.2.1", firstRegion, 50L));
        db().executeTransaction(new StoreIpInformationTransaction(player, "192.0.2.1", secondRegion, 200L));
        db().executeTransaction(new StoreIpInformationTransaction(player, "2001:db8::1", firstRegion, 300L));

        List<IpInformation> records = db().query(IpInformationQueries.fetchAll());
        assertEquals(2, records.size());
        IpInformation updated = records.stream().filter(record -> record.getIpAddress().equals("192.0.2.1"))
                .findFirst().orElseThrow();
        assertEquals(player, updated.getPlayerUUID());
        assertEquals("包头市", updated.getCity());
        assertEquals(50L, updated.getFirstSeen());
        assertEquals(200L, updated.getLastSeen());
    }

    @Test
    default void mergingPlayersPreservesIpHistoryAndRemovingPlayerRemovesIt() {
        UUID oldPlayer = UUID.randomUUID();
        UUID newPlayer = UUID.randomUUID();
        IpRegion region = new IpRegion("中国", "广西壮族自治区", "南宁市", "ISP");
        db().executeTransaction(new StoreIpInformationTransaction(oldPlayer, "192.0.2.1", region, 100L));
        db().executeTransaction(new StoreIpInformationTransaction(newPlayer, "192.0.2.2", region, 200L));

        db().executeTransaction(new CombineUserTransaction(oldPlayer, newPlayer));
        List<IpInformation> merged = db().query(IpInformationQueries.fetchAll());
        assertEquals(2, merged.size());
        assertTrue(merged.stream().allMatch(record -> record.getPlayerUUID().equals(newPlayer)));

        db().executeTransaction(new RemovePlayerTransaction(newPlayer));
        assertTrue(db().query(IpInformationQueries.fetchAll()).isEmpty());
    }
}
