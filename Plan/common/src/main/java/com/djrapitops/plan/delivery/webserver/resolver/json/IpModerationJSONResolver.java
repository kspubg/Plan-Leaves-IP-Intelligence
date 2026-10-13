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
import com.djrapitops.plan.delivery.web.resolver.AsyncResolver;
import com.djrapitops.plan.delivery.web.resolver.Response;
import com.djrapitops.plan.delivery.web.resolver.exception.BadRequestException;
import com.djrapitops.plan.delivery.web.resolver.exception.MethodNotAllowedException;
import com.djrapitops.plan.delivery.web.resolver.request.Request;
import com.djrapitops.plan.delivery.web.resolver.request.WebUser;
import com.djrapitops.plan.delivery.webserver.http.WebServer;
import com.djrapitops.plan.moderation.IpModerationService;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import dagger.Lazy;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.apache.commons.codec.digest.DigestUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Authenticated moderation only, even if Plan's general authentication is disabled. */
@Singleton
@Path("/v1/ipModeration")
public class IpModerationJSONResolver implements AsyncResolver {
    private static class ForbiddenException extends SecurityException {
        ForbiddenException(String message) {
            super(message);
        }
    }
    public record Input(String action, String server, String ip, String previewId, String reason,
                        String operationId, String uuid, String note, Integer days, String alertKey) {}

    private final IpModerationService service;
    private final Lazy<WebServer> webServer;
    private final String csrfSecret = UUID.randomUUID().toString() + UUID.randomUUID();

    @Inject
    public IpModerationJSONResolver(IpModerationService service, Lazy<WebServer> webServer) {
        this.service = service;
        this.webServer = webServer;
    }

    @Override
    public boolean canAccess(Request request) {
        return webServer.get().isAuthRequired() && request.getUser().filter(user ->
                user.hasPermission(WebPermission.PAGE_IP_INTELLIGENCE)
                        && (user.hasPermission(WebPermission.MANAGE_IP_MODERATION)
                        || user.hasPermission(WebPermission.MANAGE_IP_MODERATION_UNDO))).isPresent();
    }

    @GET
    @POST
    @Override
    public CompletableFuture<Optional<Response>> resolveAsync(Request request) {
        return request.getRequestBodyAsync().thenComposeAsync(body -> dispatch(request, body))
                .handle((value, error) -> {
                    Throwable cause = error;
                    while (cause instanceof java.util.concurrent.CompletionException) cause = cause.getCause();
                    if (cause instanceof ForbiddenException) {
                        return Optional.of(Response.builder().setStatus(403).setJSONContent(Map.of("error", cause.getMessage()))
                                .setHeader("Cache-Control", "no-store").build());
                    }
                    if (cause != null) throw new java.util.concurrent.CompletionException(cause);
                    return Optional.of(Response.builder().setJSONContent(value).setHeader("Cache-Control", "no-store").build());
                });
    }

    private CompletableFuture<Object> dispatch(Request request, byte[] body) {
        if (!canAccess(request)) throw new ForbiddenException("需要登录，并拥有 IP 分析及封禁管理权限");
        WebUser user = request.getUser().orElseThrow(() -> new ForbiddenException("请登录"));
        if ("GET".equals(request.getMethod())) {
            Map<String, Object> result = new HashMap<>(service.status(service.records()));
            result.put("csrf", csrf(request, user));
            return CompletableFuture.completedFuture(result);
        }
        if (!"POST".equals(request.getMethod())) throw new MethodNotAllowedException("POST");
        validateMutation(request, user);
        if (body.length > 8192) throw new BadRequestException("请求过大");
        Input input;
        try {
            input = new Gson().fromJson(new String(body, StandardCharsets.UTF_8), Input.class);
        } catch (JsonParseException e) {
            throw new BadRequestException("请求必须为有效 JSON");
        }
        if (input == null || input.action() == null) throw new BadRequestException("缺少操作类型");
        if (!service.serverId().equals(input.server())) throw new BadRequestException("目标服务器与当前实例不匹配");
        if ("undo".equals(input.action())) {
            requirePermission(user, WebPermission.MANAGE_IP_MODERATION_UNDO);
            return service.undo(user.getUsername(), input.operationId(), input.server()).thenApply(value -> value);
        }
        requirePermission(user, WebPermission.MANAGE_IP_MODERATION);
        return switch (input.action()) {
            case "preview" -> CompletableFuture.completedFuture(service.preview(user.getUsername(), input.ip(), input.server()));
            case "ban" -> service.ban(user.getUsername(), input.previewId(), input.reason(), input.server()).thenApply(value -> value);
            case "watch" -> {
                service.watch(user.getUsername(), input.uuid(), input.note(), input.days() == null ? 30 : input.days());
                yield CompletableFuture.completedFuture(Map.of("success", true));
            }
            case "unwatch" -> {
                service.unwatch(input.uuid());
                yield CompletableFuture.completedFuture(Map.of("success", true));
            }
            case "dismiss" -> {
                service.dismiss(input.alertKey());
                yield CompletableFuture.completedFuture(Map.of("success", true));
            }
            default -> throw new BadRequestException("不支持的操作");
        };
    }

    private void requirePermission(WebUser user, WebPermission permission) {
        if (!user.hasPermission(permission)) throw new ForbiddenException("没有执行此操作的权限");
    }

    private String header(Request request, String name) {
        return request.getHeader(name).or(() -> request.getHeader(name.toLowerCase(java.util.Locale.ROOT))).orElse("");
    }

    private String csrf(Request request, WebUser user) {
        String auth = java.util.Arrays.stream(header(request, "Cookie").split(";"))
                .map(String::trim).filter(cookie -> cookie.startsWith("auth=")).findFirst().orElse("");
        if (auth.isEmpty()) throw new ForbiddenException("此操作需要有效的登录会话");
        return DigestUtils.sha256Hex(csrfSecret + "|" + user.getUsername() + "|" + auth);
    }

    private void validateMutation(Request request, WebUser user) {
        String token = header(request, "X-Plan-CSRF");
        if (!MessageDigest.isEqual(csrf(request, user).getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8))) {
            throw new ForbiddenException("安全校验失败，请刷新面板后重试");
        }
        if (!header(request, "Content-Type").toLowerCase(java.util.Locale.ROOT).startsWith("application/json")) {
            throw new BadRequestException("只接受 application/json");
        }
        String origin = header(request, "Origin");
        if (!origin.isEmpty() && !sameHost(origin, header(request, "Host"))) {
            throw new ForbiddenException("不接受跨站封禁请求");
        }
        if ("cross-site".equals(header(request, "Sec-Fetch-Site"))) throw new ForbiddenException("不接受跨站请求");
    }

    private boolean sameHost(String origin, String host) {
        try {
            URI uri = URI.create(origin);
            return ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                    && uri.getRawAuthority() != null && uri.getRawAuthority().equalsIgnoreCase(host);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
