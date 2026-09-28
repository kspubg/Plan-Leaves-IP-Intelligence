/*
 *  This file is part of Player Analytics (Plan).
 *
 *  Plan is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License v3 as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */
package com.djrapitops.plan.storage.database.transactions.events;

import com.djrapitops.plan.gathering.ip.IpRegion;
import com.djrapitops.plan.storage.database.queries.DataStoreQueries;
import com.djrapitops.plan.storage.database.queries.PlayerFetchQueries;
import com.djrapitops.plan.storage.database.queries.QueryStatement;
import com.djrapitops.plan.storage.database.sql.tables.IpInformationTable;
import com.djrapitops.plan.storage.database.sql.tables.UsersTable;
import com.djrapitops.plan.storage.database.transactions.ExecStatement;
import com.djrapitops.plan.storage.database.transactions.Transaction;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Creates or refreshes a player IP observation.
 */
public class StoreIpInformationTransaction extends Transaction {

    private final UUID playerUUID;
    private final String ipAddress;
    private final IpRegion region;
    private final long firstSeen;
    private final long lastSeen;

    public StoreIpInformationTransaction(UUID playerUUID, String ipAddress, IpRegion region, long time) {
        this(playerUUID, ipAddress, region, time, time);
    }

    public StoreIpInformationTransaction(UUID playerUUID, String ipAddress, IpRegion region, long firstSeen, long lastSeen) {
        this.playerUUID = playerUUID;
        this.ipAddress = ipAddress;
        this.region = region;
        this.firstSeen = firstSeen;
        this.lastSeen = lastSeen;
    }

    @Override
    protected void performOperations() {
        if (Boolean.FALSE.equals(query(PlayerFetchQueries.isPlayerRegistered(playerUUID)))) {
            execute(DataStoreQueries.registerBaseUser(playerUUID, firstSeen, playerUUID.toString()));
        }

        if (Boolean.TRUE.equals(query(existingAddress()))) execute(updateStatement());
        else execute(insertStatement());
    }

    private QueryStatement<Boolean> existingAddress() {
        String sql = "SELECT 1 FROM " + IpInformationTable.TABLE_NAME + " WHERE " + IpInformationTable.USER_ID + "=" +
                UsersTable.SELECT_USER_ID + " AND " + IpInformationTable.IP_ADDRESS + "=?";
        return new QueryStatement<>(sql) {
            @Override
            public void prepare(PreparedStatement statement) throws SQLException {
                statement.setString(1, playerUUID.toString());
                statement.setString(2, ipAddress);
            }

            @Override
            public Boolean processResults(ResultSet set) throws SQLException {
                return set.next();
            }
        };
    }

    private ExecStatement updateStatement() {
        String sql = "UPDATE " + IpInformationTable.TABLE_NAME + " SET " +
                IpInformationTable.COUNTRY + "=CASE WHEN " + IpInformationTable.LAST_SEEN + "<=? THEN ? ELSE " + IpInformationTable.COUNTRY + " END," +
                IpInformationTable.PROVINCE + "=CASE WHEN " + IpInformationTable.LAST_SEEN + "<=? THEN ? ELSE " + IpInformationTable.PROVINCE + " END," +
                IpInformationTable.CITY + "=CASE WHEN " + IpInformationTable.LAST_SEEN + "<=? THEN ? ELSE " + IpInformationTable.CITY + " END," +
                IpInformationTable.ISP + "=CASE WHEN " + IpInformationTable.LAST_SEEN + "<=? THEN ? ELSE " + IpInformationTable.ISP + " END," +
                IpInformationTable.FIRST_SEEN + "=CASE WHEN " + IpInformationTable.FIRST_SEEN + ">? THEN ? ELSE " + IpInformationTable.FIRST_SEEN + " END," +
                IpInformationTable.LAST_SEEN + "=CASE WHEN " + IpInformationTable.LAST_SEEN + "<? THEN ? ELSE " + IpInformationTable.LAST_SEEN + " END WHERE " + IpInformationTable.USER_ID + "=" +
                UsersTable.SELECT_USER_ID + " AND " + IpInformationTable.IP_ADDRESS + "=?";
        return new ExecStatement(sql) {
            @Override
            public void prepare(PreparedStatement statement) throws SQLException {
                statement.setLong(1, lastSeen);
                statement.setString(2, region.getCountry());
                statement.setLong(3, lastSeen);
                statement.setString(4, region.getProvince());
                statement.setLong(5, lastSeen);
                statement.setString(6, region.getCity());
                statement.setLong(7, lastSeen);
                statement.setString(8, region.getIsp());
                statement.setLong(9, firstSeen);
                statement.setLong(10, firstSeen);
                statement.setLong(11, lastSeen);
                statement.setLong(12, lastSeen);
                statement.setString(13, playerUUID.toString());
                statement.setString(14, ipAddress);
            }
        };
    }

    private ExecStatement insertStatement() {
        String sql = "INSERT INTO " + IpInformationTable.TABLE_NAME + " (" + IpInformationTable.USER_ID + ',' +
                IpInformationTable.IP_ADDRESS + ',' + IpInformationTable.COUNTRY + ',' + IpInformationTable.PROVINCE + ',' +
                IpInformationTable.CITY + ',' + IpInformationTable.ISP + ',' + IpInformationTable.FIRST_SEEN + ',' +
                IpInformationTable.LAST_SEEN + ") VALUES (" + UsersTable.SELECT_USER_ID + ", ?, ?, ?, ?, ?, ?, ?)";
        return new ExecStatement(sql) {
            @Override
            public void prepare(PreparedStatement statement) throws SQLException {
                statement.setString(1, playerUUID.toString());
                statement.setString(2, ipAddress);
                statement.setString(3, region.getCountry());
                statement.setString(4, region.getProvince());
                statement.setString(5, region.getCity());
                statement.setString(6, region.getIsp());
                statement.setLong(7, firstSeen);
                statement.setLong(8, lastSeen);
            }
        };
    }
}
