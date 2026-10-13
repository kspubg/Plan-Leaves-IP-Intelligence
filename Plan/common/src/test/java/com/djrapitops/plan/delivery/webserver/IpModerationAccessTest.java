/*
 *  This file is part of Player Analytics (Plan).
 *
 *  Plan is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License v3 as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */
package com.djrapitops.plan.delivery.webserver;

import com.djrapitops.plan.delivery.web.resolver.Response;
import com.djrapitops.plan.delivery.web.resolver.request.Request;
import com.djrapitops.plan.delivery.web.resolver.request.URIPath;
import com.djrapitops.plan.delivery.web.resolver.request.URIQuery;
import com.djrapitops.plan.delivery.web.resolver.request.WebUser;
import com.djrapitops.plan.delivery.webserver.http.WebServer;
import com.djrapitops.plan.delivery.webserver.resolver.auth.LoginResolver;
import com.djrapitops.plan.delivery.webserver.resolver.json.IpModerationJSONResolver;
import com.djrapitops.plan.moderation.IpModerationService;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IpModerationAccessTest {
    private WebServer web;
    private IpModerationService service;
    private IpModerationJSONResolver resolver;
    private final WebUser admin = new WebUser("Console", null, "admin", List.of("page.ip.intelligence", "manage.ip.moderation"));

    @BeforeEach
    void setup() {
        web = mock(WebServer.class);
        when(web.isAuthRequired()).thenReturn(true);
        service = mock(IpModerationService.class);
        when(service.records()).thenReturn(List.of());
        when(service.status(List.of())).thenReturn(Map.of("supported", false));
        when(service.serverId()).thenReturn("local");
        resolver = new IpModerationJSONResolver(service, () -> web);
    }

    private Request request(String method, WebUser user, Map<String, String> headers, String body) {
        return new Request(method, new URIPath("/v1/ipModeration"), new URIQuery(""), user, headers,
                body.getBytes(StandardCharsets.UTF_8), "127.0.0.1");
    }

    private String token() {
        Response response = resolver.resolve(request("GET", admin, Map.of("Cookie", "auth=fixture"), "")).orElseThrow();
        return JsonParser.parseString(response.getAsString()).getAsJsonObject().get("csrf").getAsString();
    }

    private Map<String, String> headers() {
        return new HashMap<>(Map.of("Cookie", "auth=fixture", "Content-Type", "application/json", "X-Plan-CSRF", token(),
                "Origin", "http://localhost:24365", "Host", "localhost:24365"));
    }

    @Test
    void noLoginNoPermissionOrDisabledAuthenticationRejectsAccess() {
        assertEquals(403, resolver.resolve(request("GET", null, Map.of(), "")).orElseThrow().getCode());
        WebUser reader = new WebUser("Reader", null, "reader", List.of("page.ip.intelligence"));
        assertEquals(403, resolver.resolve(request("GET", reader, Map.of("Cookie", "auth=fixture"), "")).orElseThrow().getCode());
        when(web.isAuthRequired()).thenReturn(false);
        assertEquals(403, resolver.resolve(request("GET", admin, Map.of("Cookie", "auth=fixture"), "")).orElseThrow().getCode());
        verifyNoInteractions(service);
    }

    @Test
    void csrfIsSessionBoundAndCrossOriginRequestsAreRejected() {
        Map<String, String> headers = headers();
        headers.remove("X-Plan-CSRF");
        String body = "{\"action\":\"watch\",\"server\":\"local\",\"uuid\":\"fixture\",\"note\":\"test\"}";
        assertEquals(403, resolver.resolve(request("POST", admin, headers, body)).orElseThrow().getCode());
        headers = headers();
        headers.put("Cookie", "auth=different-session");
        assertEquals(403, resolver.resolve(request("POST", admin, headers, body)).orElseThrow().getCode());
        headers = headers();
        headers.put("Origin", "http://unrelated.example");
        assertEquals(403, resolver.resolve(request("POST", admin, headers, body)).orElseThrow().getCode());
        verify(service, never()).watch(anyString(), anyString(), anyString(), anyInt());
    }

    @Test
    void validTokenOnlyAllowsAnAuthorizedMutation() {
        Map<String, String> mixedCase = headers();
        mixedCase.put("x-Plan-cSrf", mixedCase.remove("X-Plan-CSRF"));
        Response response = resolver.resolve(request("POST", admin, mixedCase,
                "{\"action\":\"watch\",\"server\":\"local\",\"uuid\":\"fixture\",\"note\":\"test\",\"days\":30}")).orElseThrow();
        assertEquals(200, response.getCode());
        verify(service).watch("admin", "fixture", "test", 30);
        assertEquals("no-store", response.getHeaders().get("Cache-Control"));
    }

    @Test
    void loginCookiesRetainSecureOnHttpsAndWorkWithExplicitHttpLogin() {
        LoginResolver login = new LoginResolver(null, null, () -> web);
        when(web.isUsingHTTPS()).thenReturn(false);
        assertFalse(login.getResponse("fixture").getHeaders().get("Set-Cookie").contains("Secure;"));
        assertTrue(login.getResponse("fixture").getHeaders().get("Set-Cookie").contains("HTTPOnly;"));
        when(web.isUsingHTTPS()).thenReturn(true);
        assertTrue(login.getResponse("fixture").getHeaders().get("Set-Cookie").contains("Secure;"));
    }
}
