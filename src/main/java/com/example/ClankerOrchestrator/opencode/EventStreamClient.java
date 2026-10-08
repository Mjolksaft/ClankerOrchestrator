package com.example.ClankerOrchestrator.opencode;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class EventStreamClient implements AutoCloseable {
    private final HttpClient http;
    private final ObjectMapper mapper;
    private final String baseUrl;
    private final String directory;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private volatile boolean running = false;

    public EventStreamClient(
        ObjectMapper mapper,
        @Value("${opencode.host:127.0.0.1}") String host,
        @Value("${opencode.port:4096}") int port,
        @Value("${opencode.directory:${user.dir}}") String directory
    ) {
        this.mapper = mapper;
        this.http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();
        this.baseUrl = "http://" + host + ":" + port;
        this.directory = directory;
    }

    public synchronized void start(Consumer<JsonNode> eventConsumer) {
        if (running) {
            return;
        }
        running = true;
        executor.submit(() -> {
            while (running) {
                try {
                    HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + WithDirectory("/global/event")))
                        .timeout(Duration.ofSeconds(60))
                        .header("Accept", "text/event-stream")
                        .GET()
                        .build();
                    HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
                    if (response.statusCode() != 200) {
                        Thread.sleep(2000);
                        continue;
                    }
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                        String line;
                        StringBuilder data = new StringBuilder();
                        while (running && (line = reader.readLine()) != null) {
                            if (line.startsWith("data:")) {
                                String d = line.substring(5);
                                if (d.startsWith(" ")) {
                                    d = d.substring(1);
                                }
                                data.append(d);
                            } else if (line.isBlank()) {
                                if (data.length() > 0) {
                                    try {
                                        JsonNode node = mapper.readTree(data.toString());
                                        eventConsumer.accept(node);
                                    } catch (Exception ignored) {
                                    }
                                    data.setLength(0);
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        });
    }

    public void stop() {
        running = false;
    }

    private String WithDirectory(String path) {
        if (directory == null || directory.isBlank()) {
            return path;
        }
        String separator = path.contains("?") ? "&" : "?";
        return path + separator + "directory=" + URLEncoder.encode(directory, StandardCharsets.UTF_8);
    }

    @Override
    public void close() {
        stop();
        executor.shutdownNow();
    }
}
