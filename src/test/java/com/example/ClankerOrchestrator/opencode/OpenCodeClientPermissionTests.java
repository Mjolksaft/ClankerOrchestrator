package com.example.ClankerOrchestrator.opencode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.NoSuchElementException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

class OpenCodeClientPermissionTests {

    private StubOpenCodeServer server;
    private OpenCodeClient client;

    @BeforeEach
    void SetUp() throws IOException {
        server = new StubOpenCodeServer();
        client = new OpenCodeClient(JsonMapper.builder().build(), "127.0.0.1", server.Port(), "", 10);
    }

    @AfterEach
    void TearDown() {
        server.close();
    }

    @Test
    void ReplyPermissionUsesPrimaryRoute() {
        client.ReplyPermission("ses_1", "per_1", "once", null);

        assertTrue(server.requests.contains("POST /permission/per_1/reply {\"reply\":\"once\"}"), server.requests.toString());
    }

    @Test
    void ReplyPermissionSendsMessageWhenProvided() {
        client.ReplyPermission("ses_1", "per_1", "reject", "do not delete that");

        assertTrue(server.requests.contains("POST /permission/per_1/reply {\"reply\":\"reject\",\"message\":\"do not delete that\"}"), server.requests.toString());
    }

    @Test
    void ReplyPermissionFallsBackToApiRoute() {
        server.SetPermissionReplyStatus(404);

        client.ReplyPermission("ses_1", "per_1", "always", null);

        assertTrue(server.requests.contains("POST /api/session/ses_1/permission/per_1/reply {\"reply\":\"always\"}"), server.requests.toString());
    }

    @Test
    void ReplyPermissionFallsBackToLegacyRoute() {
        server.SetPermissionReplyStatus(404);
        server.SetApiPermissionReplyStatus(404);

        client.ReplyPermission("ses_1", "per_1", "reject", null);

        assertTrue(server.requests.contains("POST /session/ses_1/permissions/per_1 {\"response\":\"reject\"}"), server.requests.toString());
    }

    @Test
    void ReplyPermissionThrowsNotFoundWhenNoRouteKnowsTheRequest() {
        server.SetPermissionReplyStatus(404);
        server.SetApiPermissionReplyStatus(404);
        server.SetLegacyPermissionReplyStatus(404);

        NoSuchElementException exception = assertThrows(
            NoSuchElementException.class,
            () -> client.ReplyPermission("ses_1", "per_1", "once", null)
        );

        assertTrue(exception.getMessage().contains("per_1"), exception.getMessage());
    }

    @Test
    void ReplyPermissionThrowsWhenOpencodeRejectsTheReply() {
        server.SetPermissionReplyStatus(500);

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> client.ReplyPermission("ses_1", "per_1", "once", null)
        );

        assertTrue(exception.getMessage().contains("500"), exception.getMessage());
    }

    @Test
    void ReplyPermissionStopsAtFirstWorkingRoute() {
        server.SetPermissionReplyStatus(404);

        client.ReplyPermission("ses_1", "per_1", "once", null);

        long apiReplies = server.requests.stream().filter(r -> r.startsWith("POST /api/")).count();
        long legacyReplies = server.requests.stream().filter(r -> r.contains("/permissions/")).count();
        assertEquals(1, apiReplies);
        assertEquals(0, legacyReplies);
    }
}
