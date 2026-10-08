package com.example.ClankerOrchestrator.opencode;

import com.example.ClankerOrchestrator.exception.OpenCodeUnavailableException;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component 
public class OpenCodeClient {

    private final HttpClient http;
    private final ObjectMapper mapper;
    private final String baseUrl;
    private final String directory;
    private final Duration timeout;

    public OpenCodeClient(
        ObjectMapper mapper,
        @Value("${opencode.host:127.0.0.1}") String host,
        @Value("${opencode.port:4096}") int port,
        @Value("${opencode.directory:${user.dir}}") String directory,
        @Value("${opencode.timeout-seconds:600}") long timeoutSeconds
    ) {
        this.mapper = mapper;
        this.http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();
        this.baseUrl = "http://" + host + ":" + port;
        this.directory = directory;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
    }

    public String BaseUrl() {
        return baseUrl;
    }

    public boolean IsHealthy() {
        try {
            HttpResponse<String> response = Send("GET", "/global/health", null, Duration.ofSeconds(5));

            return response.statusCode() == 200 && response.body().contains("\"healthy\":true");

        } catch (Exception e) {
            return false;
        }
    }

    public String CreateSession(String title) {
        HttpResponse<String> response = Post("/session", Map.of("title", title));
        JsonNode node = Read(response);

        if(!node.hasNonNull("id")) {
            throw new IllegalStateException("opencode did not return a session id: " + response.body());
        }

        return node.get("id").asText();
    }

    public PromptReply Prompt(String sessionId, String prompt) {
        Map<String, Object> part = new LinkedHashMap<String, Object>();
        part.put("type", "text");
        part.put("text", prompt);

        HttpResponse<String> response = Post("/session/" + sessionId + "/message", Map.of("parts", List.of(part)));
        JsonNode node = Read(response);

        List<String> tools = new ArrayList<String>();
        StringBuilder text = new StringBuilder();
        for(JsonNode part2 : node.path("parts")) {
            String type = part2.path("type").asText();

            if(type.equals("text")) {
                if(text.length() > 0) {
                    text.append(System.lineSeparator());
                }
                text.append(part2.path("text").asText());

            } else if(type.equals("tool")) {
                Add(tools, part2.path("tool").asText());
            }
        }

        return new PromptReply(text.toString(), tools, Error(node.path("info").path("error")));
    }

    public List<String> Tools(String sessionId) {
        try {
            JsonNode node = Read(Send("GET", "/session/" + sessionId + "/message?limit=20", null, timeout));

            List<String> tools = new ArrayList<String>();
            for(JsonNode message : node) {
                for(JsonNode part : message.path("parts")) {
                    if(part.path("type").asText().equals("tool")) {
                        Add(tools, part.path("tool").asText());
                    }
                }
            }

            return tools;

        } catch (Exception e) {
            return List.of();
        }
    }

    public void ReplyPermission(String sessionId, String requestId, String reply, String message) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("reply", reply);
        if(message != null && !message.isBlank()) {
            body.put("message", message);
        }

        String id = Encode(requestId);

        HttpResponse<String> response = Post("/permission/" + id + "/reply", body);
        if(response.statusCode() != 404) {
            RequirePermissionReply(response);
            return;
        }

        response = Post("/api/session/" + Encode(sessionId) + "/permission/" + id + "/reply", body);
        if(response.statusCode() != 404) {
            RequirePermissionReply(response);
            return;
        }

        response = Post("/session/" + Encode(sessionId) + "/permissions/" + id, Map.of("response", reply));
        if(response.statusCode() == 404) {
            throw new NoSuchElementException("permission request " + requestId + " is no longer pending");
        }

        RequirePermissionReply(response);
    }

    private void RequirePermissionReply(HttpResponse<String> response) {
        if(response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("opencode rejected the permission reply: " + response.statusCode() + ": " + response.body());
        }
    }

    private String Encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private void Add(List<String> tools, String tool) {
        if(!tool.isBlank() && !tools.contains(tool)) {
            tools.add(tool);
        }
    }

    private HttpResponse<String> Post(String path, Object body) {
        String json = mapper.writeValueAsString(body);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + WithDirectory(path)))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

            return http.send(request, HttpResponse.BodyHandlers.ofString());

        } catch (java.net.ConnectException e) {
            throw new OpenCodeUnavailableException("opencode server is not running at " + baseUrl);

        } catch (java.net.http.HttpTimeoutException e) {
            throw new OpenCodeUnavailableException("opencode did not respond within " + timeout.toSeconds() + "s");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OpenCodeUnavailableException("Request to opencode was interrupted");

        } catch (Exception e) {
            throw new OpenCodeUnavailableException("Request to opencode failed: " + e.getMessage());
        }
    }

    private HttpResponse<String> Send(String method, String path, String body, Duration requestTimeout) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + WithDirectory(path)))
            .timeout(requestTimeout);

        if(body == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        }

        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String DirectoryQuery() {
        if(directory == null || directory.isBlank()) {
            return "";
        }

        return "?directory=" + URLEncoder.encode(directory, StandardCharsets.UTF_8);
    }

    private String WithDirectory(String path) {
        if(directory == null || directory.isBlank()) {
            return path;
        }

        String separator = path.contains("?") ? "&" : "?";

        return path + separator + DirectoryQuery().substring(1);
    }

    private JsonNode Read(HttpResponse<String> response) {
        if(response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("opencode returned " + response.statusCode() + ": " + response.body());
        }

        return mapper.readTree(response.body());
    }

    private String Error(JsonNode error) {
        if(error.isMissingNode() || error.isNull()) {
            return null;
        }

        JsonNode message = error.path("data").path("message");
        String name = error.path("name").asText("Error");

        return message.isMissingNode() ? name : name + ": " + message.asText();
    }

    public record PromptReply(String text, List<String> tools, String error) {
    }
}