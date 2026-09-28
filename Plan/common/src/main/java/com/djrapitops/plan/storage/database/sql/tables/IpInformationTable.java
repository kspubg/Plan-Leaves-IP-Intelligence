/*
 *  This file is part of Player Analytics (Plan).
 *
 *  Plan is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License v3 as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */
package com.djrapitops.plan.storage.database.sql.tables;

import com.djrapitops.plan.storage.database.DBType;
import com.djrapitops.plan.storage.database.sql.building.CreateTableBuilder;
import com.djrapitops.plan.storage.database.sql.building.Sql;

/**
 * Stores raw IP addresses and their locally resolved region details.
 */
public final class IpInformationTable {

    public static final String TABLE_NAME = "plan_ip_information";
    public static final String ID = "id";
    public static final String USER_ID = "user_id";
    public static final String IP_ADDRESS = "ip_address";
    public static final String COUNTRY = "country";
    public static final String PROVINCE = "province";
    public static final String CITY = "city";
    public static final String ISP = "isp";
    public static final String FIRST_SEEN = "first_seen";
    public static final String LAST_SEEN = "last_seen";

    private IpInformationTable() {}

    public static String createTableSQL(DBType dbType) {
        return CreateTableBuilder.create(TABLE_NAME, dbType)
                .column(ID, Sql.INT).primaryKey()
                .column(USER_ID, Sql.INT).notNull()
                .column(IP_ADDRESS, Sql.varchar(64)).notNull()
                .column(COUNTRY, Sql.varchar(80)).notNull()
                .column(PROVINCE, Sql.varchar(80)).notNull()
                .column(CITY, Sql.varchar(80)).notNull()
                .column(ISP, Sql.varchar(160)).notNull()
                .column(FIRST_SEEN, Sql.LONG).notNull()
                .column(LAST_SEEN, Sql.LONG).notNull()
                .foreignKey(USER_ID, UsersTable.TABLE_NAME, UsersTable.ID)
                .toString();
    }
}
