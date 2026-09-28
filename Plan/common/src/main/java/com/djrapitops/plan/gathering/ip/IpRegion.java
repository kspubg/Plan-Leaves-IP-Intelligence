/*
 *  This file is part of Player Analytics (Plan).
 *
 *  Plan is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License v3 as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */
package com.djrapitops.plan.gathering.ip;

/**
 * Region data returned by an offline IP database.
 */
public class IpRegion {

    private final String country;
    private final String province;
    private final String city;
    private final String isp;

    public IpRegion(String country, String province, String city, String isp) {
        this.country = country;
        this.province = province;
        this.city = city;
        this.isp = isp;
    }

    public static IpRegion unknown() {return new IpRegion("Unknown", "Unknown", "Unknown", "Unknown");}

    public String getCountry() {return country;}
    public String getProvince() {return province;}
    public String getCity() {return city;}
    public String getIsp() {return isp;}
}
