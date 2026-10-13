/*
 *  This file is part of Player Analytics (Plan).
 *
 *  Plan is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License v3 as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Plan is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with Plan. If not, see <https://www.gnu.org/licenses/>.
 */
package com.djrapitops.plan.storage.database.queries;

import com.djrapitops.plan.gathering.domain.MinecraftStatistics;
import com.djrapitops.plan.storage.database.DatabaseTestPreparer;
import com.djrapitops.plan.storage.database.queries.objects.StatisticsQueries;
import com.djrapitops.plan.storage.database.transactions.events.StoreMinecraftStatisticsTransaction;
import com.djrapitops.plan.storage.database.transactions.events.StoreMinecraftStatisticsValuesTransaction;
import com.djrapitops.plan.storage.database.transactions.events.StoreServerPlayerTransaction;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

public interface MinecraftStatisticsQueriesTest extends DatabaseTestPreparer {

    default Map<String, Integer> prepareHistoricalStatistics() {
        db().executeTransaction(new StoreServerPlayerTransaction(playerUUID, () -> 1L,
                "StatisticPlayer", serverUUID(), () -> "localhost")).join();
        db().executeTransaction(new StoreMinecraftStatisticsTransaction(Set.of("historical", "current", "new"))).join();
        Map<String, Integer> ids = db().query(StatisticsQueries.fetchStatisticNameToId());
        db().executeTransaction(new StoreMinecraftStatisticsValuesTransaction(new MinecraftStatistics(
                playerUUID, serverUUID(), Map.of(ids.get("historical"), 123, ids.get("current"), 5)))).join();
        return ids;
    }

    @Test
    default void missingStatisticsRetainHistoricalValuesWhilePresentStatisticsUpdateAndInsert() {
        Map<String, Integer> ids = prepareHistoricalStatistics();
        db().executeTransaction(new StoreMinecraftStatisticsValuesTransaction(new MinecraftStatistics(
                playerUUID, serverUUID(), Map.of(ids.get("current"), 6, ids.get("new"), 7)))).join();
        assertEquals(Map.of("historical", 123, "current", 6, "new", 7),
                db().query(StatisticsQueries.fetchStatistics(playerUUID)).get(0).getStatistics());
    }

    @Test
    default void emptyStatisticsDoNotEraseHistory() {
        prepareHistoricalStatistics();
        db().executeTransaction(new StoreMinecraftStatisticsValuesTransaction(new MinecraftStatistics(
                playerUUID, serverUUID(), Map.of()))).join();
        assertEquals(Map.of("historical", 123, "current", 5),
                db().query(StatisticsQueries.fetchStatistics(playerUUID)).get(0).getStatistics());
    }

    @Test
    default void explicitZeroStatisticIsStillUpdated() {
        Map<String, Integer> ids = prepareHistoricalStatistics();
        db().executeTransaction(new StoreMinecraftStatisticsValuesTransaction(new MinecraftStatistics(
                playerUUID, serverUUID(), Map.of(ids.get("current"), 0)))).join();
        assertEquals(Map.of("historical", 123, "current", 0),
                db().query(StatisticsQueries.fetchStatistics(playerUUID)).get(0).getStatistics());
    }
}
