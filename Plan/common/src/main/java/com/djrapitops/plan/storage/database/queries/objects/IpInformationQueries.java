/*
 *  This file is part of Player Analytics (Plan).
 *
 *  Plan is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License v3 as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */
package com.djrapitops.plan.storage.database.queries.objects;

import com.djrapitops.plan.delivery.domain.IpInformation;
import com.djrapitops.plan.storage.database.queries.Query;
import com.djrapitops.plan.storage.database.queries.QueryAllStatement;
import com.djrapitops.plan.storage.database.queries.QueryStatement;
import com.djrapitops.plan.storage.database.sql.tables.IpInformationTable;
import com.djrapitops.plan.storage.database.sql.tables.UsersTable;

import java.sql.ResultSet;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.djrapitops.plan.storage.database.sql.building.Sql.*;

/**
 * Queries for protected IP intelligence data.
 */
public final class IpInformationQueries {

    public record Row(int id, IpInformation information) {}

    private IpInformationQueries() {}

    public static Query<List<IpInformation>> fetchAll() {
        String sql = SELECT + "u." + UsersTable.USER_UUID + ',' + "u." + UsersTable.USER_NAME + ',' +
                "i." + IpInformationTable.IP_ADDRESS + ',' + "i." + IpInformationTable.COUNTRY + ',' +
                "i." + IpInformationTable.PROVINCE + ',' + "i." + IpInformationTable.CITY + ',' +
                "i." + IpInformationTable.ISP + ',' + "i." + IpInformationTable.FIRST_SEEN + ',' +
                "i." + IpInformationTable.LAST_SEEN + FROM + IpInformationTable.TABLE_NAME + " i" +
                INNER_JOIN + UsersTable.TABLE_NAME + " u ON i." + IpInformationTable.USER_ID + "=u." + UsersTable.ID +
                ORDER_BY + "i." + IpInformationTable.LAST_SEEN + " DESC";
        return new QueryAllStatement<>(sql, 5000) {
            @Override
            public List<IpInformation> processResults(ResultSet set) throws SQLException {
                List<IpInformation> information = new ArrayList<>();
                while (set.next()) {
                    information.add(new IpInformation(
                            UUID.fromString(set.getString(UsersTable.USER_UUID)),
                            set.getString(UsersTable.USER_NAME),
                            set.getString(IpInformationTable.IP_ADDRESS),
                            set.getString(IpInformationTable.COUNTRY),
                            set.getString(IpInformationTable.PROVINCE),
                            set.getString(IpInformationTable.CITY),
                            set.getString(IpInformationTable.ISP),
                            set.getLong(IpInformationTable.FIRST_SEEN),
                            set.getLong(IpInformationTable.LAST_SEEN)
                    ));
                }
                return information;
            }
        };
    }

    public static Query<List<Row>> fetchAfterId(int id, int limit) {
        String sql = SELECT + "i." + IpInformationTable.ID + ",u." + UsersTable.USER_UUID + ",u." + UsersTable.USER_NAME +
                ",i." + IpInformationTable.IP_ADDRESS + ",i." + IpInformationTable.COUNTRY +
                ",i." + IpInformationTable.PROVINCE + ",i." + IpInformationTable.CITY +
                ",i." + IpInformationTable.ISP + ",i." + IpInformationTable.FIRST_SEEN +
                ",i." + IpInformationTable.LAST_SEEN + FROM + IpInformationTable.TABLE_NAME + " i" +
                INNER_JOIN + UsersTable.TABLE_NAME + " u ON i." + IpInformationTable.USER_ID + "=u." + UsersTable.ID +
                WHERE + "i." + IpInformationTable.ID + ">?" + ORDER_BY + "i." + IpInformationTable.ID + " ASC LIMIT ?";
        return new QueryStatement<>(sql, limit) {
            @Override
            public void prepare(PreparedStatement statement) throws SQLException {
                statement.setInt(1, id);
                statement.setInt(2, limit);
            }

            @Override
            public List<Row> processResults(ResultSet set) throws SQLException {
                List<Row> rows = new ArrayList<>();
                while (set.next()) {
                    rows.add(new Row(set.getInt(IpInformationTable.ID), new IpInformation(
                            UUID.fromString(set.getString(UsersTable.USER_UUID)),
                            set.getString(UsersTable.USER_NAME), set.getString(IpInformationTable.IP_ADDRESS),
                            set.getString(IpInformationTable.COUNTRY), set.getString(IpInformationTable.PROVINCE),
                            set.getString(IpInformationTable.CITY), set.getString(IpInformationTable.ISP),
                            set.getLong(IpInformationTable.FIRST_SEEN), set.getLong(IpInformationTable.LAST_SEEN))));
                }
                return rows;
            }
        };
    }
}
