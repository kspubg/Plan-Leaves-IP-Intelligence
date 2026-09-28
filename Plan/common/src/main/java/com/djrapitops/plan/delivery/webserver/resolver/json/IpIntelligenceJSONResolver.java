/*
 *  This file is part of Player Analytics (Plan).
 *
 *  Plan is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License v3 as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */
package com.djrapitops.plan.delivery.webserver.resolver.json;

import com.djrapitops.plan.delivery.domain.auth.WebPermission;
import com.djrapitops.plan.delivery.web.resolver.Response;
import com.djrapitops.plan.delivery.web.resolver.Resolver;
import com.djrapitops.plan.delivery.web.resolver.request.Request;
import com.djrapitops.plan.delivery.web.resolver.request.WebUser;
import com.djrapitops.plan.settings.config.PlanConfig;
import com.djrapitops.plan.settings.config.paths.DataGatheringSettings;
import com.djrapitops.plan.storage.database.DBSystem;
import com.djrapitops.plan.storage.database.queries.objects.IpInformationQueries;
import com.djrapitops.plan.utilities.java.Maps;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Optional;

/**
 * Serves IP intelligence only to explicitly authorized web users.
 */
@Singleton
@Path("/v1/ipIntelligence")
public class IpIntelligenceJSONResolver implements Resolver {

    private final PlanConfig config;
    private final DBSystem dbSystem;

    @Inject
    public IpIntelligenceJSONResolver(PlanConfig config, DBSystem dbSystem) {
        this.config = config;
        this.dbSystem = dbSystem;
    }

    @Override
    public boolean canAccess(Request request) {
        return request.getUser().orElse(new WebUser("")).hasPermission(WebPermission.PAGE_IP_INTELLIGENCE);
    }

    @GET
    @Operation(
            description = "Get protected IP address association and China region intelligence data",
            responses = @ApiResponse(responseCode = "200", content = @Content()),
            requestBody = @RequestBody(content = @Content(examples = @ExampleObject()))
    )
    @Override
    public Optional<Response> resolve(Request request) {
        return Optional.of(Response.builder()
                .setJSONContent(Maps.builder(String.class, Object.class)
                        .put("enabled", config.isTrue(DataGatheringSettings.IP_INTELLIGENCE))
                        .put("records", dbSystem.getDatabase().query(IpInformationQueries.fetchAll()))
                        .build())
                .build());
    }
}
