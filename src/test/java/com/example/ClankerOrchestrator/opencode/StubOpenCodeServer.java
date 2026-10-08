package com.example.ClankerOrchestrator.opencode;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

public class StubOpenCodeServer implements AutoCloseable {

    public final List<String> requests = new CopyOnWriteArrayList<String>();
    private final HttpServer server;
    private final AtomicInteger sessionCounter = new AtomicInteger();
    private volatile String reply = "hello";
    private volatile boolean healthy = true;
    private volatile long messageDelayMillis = 0;
    private volatile int permissionReplyStatus = 200;
    private volatile int apiPermissionReplyStatus = 200;
    private volatile int legacyPermissionReplyStatus = 200;

    public StubOpenCodeServer() throws IOException {
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        this.server.createContext("/", this::Handle);
        this.server.start();
    }

    public int Port() {
        return server.getAddress().getPort();
    }

    public void SetReply(String reply) {
        this.reply = reply;
    }

    public void SetHealthy(boolean healthy) {
        this.healthy = healthy;
    }

    public void SetMessageDelay(long millis) {
        this.messageDelayMillis = millis;
    }

    public void SetPermissionReplyStatus(int status) {
        this.permissionReplyStatus = status;
    }

    public void SetApiPermissionReplyStatus(int status) {
        this.apiPermissionReplyStatus = status;
    }

    public void SetLegacyPermissionReplyStatus(int status) {
        this.legacyPermissionReplyStatus = status;
    }

    public long RequestCount() {
        return requests.size();
    }

    private void Handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        requests.add(exchange.getRequestMethod() + " " + path + " " + body);

        if(path.equals("/global/health")) {
            Respond(exchange, healthy ? 200 : 503, "{\"healthy\":true,\"version\":\"stub\"}");

        } else if(path.equals("/session")) {
            Respond(exchange, 200, "{\"id\":\"ses_stub" + sessionCounter.incrementAndGet() + "\",\"title\":\"x\"}");

        } else if(path.startsWith("/permission/") && path.endsWith("/reply")) {
            RespondPermission(exchange, permissionReplyStatus);

        } else if(path.startsWith("/api/session/") && path.endsWith("/reply")) {
            RespondPermission(exchange, apiPermissionReplyStatus);

        } else if(path.matches(".*/session/[^/]+/permissions/[^/]+")) {
            RespondPermission(exchange, legacyPermissionReplyStatus);

        } else if(path.contains("/message")) {
            if(exchange.getRequestMethod().equals("GET")) {
                Respond(exchange, 200, ToolsBody());
                return;
            }

            Sleep(messageDelayMillis);
            Respond(exchange, 200, ReplyBody());

        } else {
            Respond(exchange, 404, "{\"error\":\"not found\"}");
        }
    }

    private void RespondPermission(HttpExchange exchange, int status) throws IOException {
        if(status == 200) {
            Respond(exchange, 200, "true");
        } else {
            Respond(exchange, status, "{\"error\":\"stub status " + status + "\"}");
        }
    }

    private String ReplyBody() {
        return "{\"info\":{\"role\":\"assistant\"},\"parts\":["
            + "{\"type\":\"text\",\"text\":\"" + reply + "\"}"
            + "]}";
    }

    private String ToolsBody() {
        return "[{\"info\":{\"role\":\"assistant\"},\"parts\":["
            + "{\"type\":\"tool\",\"tool\":\"bash\"},"
            + "{\"type\":\"tool\",\"tool\":\"write\"},"
            + "{\"type\":\"tool\",\"tool\":\"bash\"}"
            + "]}]";
    }

    private void Sleep(long millis) {
        if(millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch(InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void Respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);

        try(OutputStream stream = exchange.getResponseBody()) {
            stream.write(bytes);
        }
    }

    @Override
    public void close() {
        this.server.stop(0);
    }
}
