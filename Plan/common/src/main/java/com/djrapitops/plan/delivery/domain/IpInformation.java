/*
 *  This file is part of Player Analytics (Plan).
 *
 *  Plan is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License v3 as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */
package com.djrapitops.plan.delivery.domain;

import java.util.UUID;

/**
 * A locally resolved IP address observation for a player.
 */
public class IpInformation {

    private final UUID playerUUID;
    private final String playerName;
    private final String ipAddress;
    private final String country;
    private final String province;
    private final String city;
    private final String isp;
    private final long firstSeen;
    private final long lastSeen;

    public IpInformation(UUID playerUUID, String playerName, String ipAddress, String country, String province,
                         String city, String isp, long firstSeen, long lastSeen) {
        this.playerUUID = playerUUID;
        this.playerName = playerName;
        this.ipAddress = ipAddress;
        this.country = country;
        this.province = province;
        this.city = city;
        this.isp = isp;
        this.firstSeen = firstSeen;
        this.lastSeen = lastSeen;
    }

    public UUID getPlayerUUID() {return playerUUID;}
    public String getPlayerName() {return playerName;}
    public String getIpAddress() {return ipAddress;}
    public String getCountry() {return country;}
    public String getProvince() {return province;}
    public String getCity() {return city;}
    public String getIsp() {return isp;}
    public long getFirstSeen() {return firstSeen;}
    public long getLastSeen() {return lastSeen;}
}
