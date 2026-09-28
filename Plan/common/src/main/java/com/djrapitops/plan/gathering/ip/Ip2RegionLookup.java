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
import com.djrapitops.plan.storage.file.PlanFiles;
import net.playeranalytics.plugin.server.PluginLogger;
import org.lionsoul.ip2region.service.Config;
import org.lionsoul.ip2region.service.Ip2Region;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.net.InetAddress;
import java.util.Optional;

/**
 * Local IPv4 and IPv6 region resolver backed by ip2region xdb files.
 */
@Singleton
public class Ip2RegionLookup {

    private final PlanFiles files;
    private final PlanConfig config;
    private final PluginLogger logger;
    private volatile Ip2Region service;
    private volatile boolean initializationAttempted;

    @Inject
    public Ip2RegionLookup(PlanFiles files, PlanConfig config, PluginLogger logger) {
        this.files = files;
        this.config = config;
        this.logger = logger;
    }

    public Optional<IpRegion> lookup(InetAddress address) {
        if (address == null) return Optional.empty();
        Ip2Region lookupService = getService();
        if (lookupService == null) return Optional.empty();

        try {
            String result = lookupService.search(address.getHostAddress());
            if (result == null) return Optional.empty();
            String[] parts = result.split("\\|", -1);
            if (parts.length < 4) return Optional.empty();
            return Optional.of(new IpRegion(value(parts[0]), value(parts[1]), value(parts[2]), value(parts[3])));
        } catch (Exception e) {
            logger.warn("ip2region lookup failed: " + e.getMessage());
            return Optional.empty();
        }
    }

    private synchronized Ip2Region getService() {
        if (service != null || initializationAttempted) return service;
        initializationAttempted = true;

        File ipv4File = files.getFileFromPluginFolder(config.get(DataGatheringSettings.IP_INTELLIGENCE_IPV4_DATABASE));
        File ipv6File = files.getFileFromPluginFolder(config.get(DataGatheringSettings.IP_INTELLIGENCE_IPV6_DATABASE));
        if (!ipv4File.isFile() && !ipv6File.isFile()) {
            logger.warn("IP intelligence is enabled but no ip2region xdb database was found in the Plan folder.");
            return null;
        }

        try {
            Config ipv4 = ipv4File.isFile() ? Config.custom().setCachePolicy(Config.VIndexCache)
                    .setXdbPath(ipv4File.getAbsolutePath()).asV4() : null;
            Config ipv6 = ipv6File.isFile() ? Config.custom().setCachePolicy(Config.VIndexCache)
                    .setXdbPath(ipv6File.getAbsolutePath()).asV6() : null;
            service = Ip2Region.create(ipv4, ipv6);
        } catch (Exception e) {
            logger.warn("Failed to initialize ip2region: " + e.getMessage());
        }
        return service;
    }

    private String value(String value) {
        return value == null || value.isBlank() || "0".equals(value) ? "Unknown" : value;
    }
}
