
package com.nada;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import com.sun.net.httpserver.HttpServer;

import static org.junit.jupiter.api.Assertions.*;

class PathFinderTest {

    private HttpServer server;
    private String baseUrl;
    private final HttpClient client = HttpClient.newHttpClient();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(
                new InetSocketAddress("localhost", 0), 0);

        PersonStore store = new PersonStore();

        server.createContext("/people", exchange -> {
            try {
                String path = exchange.getRequestURI().getPath();

                if (path.startsWith("/people/")) {
                    String idText = path.substring("/people/".length());
                    int id = Integer.parseInt(idText);

                    if (!store.knowsPerson(id)) {
                        byte[] body = "{\"error\":\"person not found\"}"
                                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
                        exchange.sendResponseHeaders(404, body.length);
                        try (var out = exchange.getResponseBody()) {
                            out.write(body);
                        }
                        return;
                    }

                    var contacts = new java.util.ArrayList<Person>();
                    for (int contactId : store.getContactIds(id)) {
                        contacts.add(store.getPerson(contactId));
                    }

                    byte[] body = new com.google.gson.Gson()
                            .toJson(contacts)
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8);

                    exchange.getResponseHeaders().set(
                            "Content-Type", "application/json; charset=UTF-8");
                    exchange.sendResponseHeaders(200, body.length);
                    try (var out = exchange.getResponseBody()) {
                        out.write(body);
                    }
                    return;
                }

                if (!exchange.getRequestMethod().equals("POST")) {
                    exchange.sendResponseHeaders(405, -1);
                    return;
                }

                String body = new String(
                        exchange.getRequestBody().readAllBytes(),
                        java.nio.charset.StandardCharsets.UTF_8);

                JsonObject request = JsonParser.parseString(body).getAsJsonObject();
                Person person = store.createPerson(request.get("name").getAsString());

                byte[] response = new com.google.gson.Gson()
                        .toJson(person)
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8);

                exchange.getResponseHeaders().set(
                        "Content-Type", "application/json; charset=UTF-8");
                exchange.sendResponseHeaders(201, response.length);
                try (var out = exchange.getResponseBody()) {
                    out.write(response);
                }
            } catch (Exception exception) {
                byte[] body = "{\"error\":\"bad request\"}"
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(400, body.length);
                try (var out = exchange.getResponseBody()) {
                    out.write(body);
                }
            }
        });

        server.createContext("/knows", exchange -> {
            try {
                JsonObject request = JsonParser.parseString(
                        new String(
                                exchange.getRequestBody().readAllBytes(),
                                java.nio.charset.StandardCharsets.UTF_8))
                        .getAsJsonObject();

                boolean connected = store.connect(
                        request.get("a").getAsInt(),
                        request.get("b").getAsInt());

                if (!connected) {
                    exchange.sendResponseHeaders(400, -1);
                    return;
                }

                exchange.sendResponseHeaders(204, -1);
            } finally {
                exchange.close();
            }
        });

        server.createContext("/path", exchange -> {
            try {
                String query = exchange.getRequestURI().getQuery();
                String fromText = null;
                String toText = null;

                if (query != null) {
                    for (String parameter : query.split("&")) {
                        String[] parts = parameter.split("=", 2);
                        if (parts.length == 2) {
                            if (parts[0].equals("from")) {
                                fromText = parts[1];
                            } else if (parts[0].equals("to")) {
                                toText = parts[1];
                            }
                        }
                    }
                }

                int from = Integer.parseInt(fromText);
                int to = Integer.parseInt(toText);

                if (!store.knowsPerson(from) || !store.knowsPerson(to)) {
                    byte[] body = "{\"error\":\"person not found\"}"
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(404, body.length);
                    try (var out = exchange.getResponseBody()) {
                        out.write(body);
                    }
                    return;
                }

                var path = new PathFinder(store)
                        .findShortestPath(from, to, 3);

                JsonObject response = new JsonObject();

                if (path.isEmpty()) {
                    response.addProperty("connected", false);
                } else {
                    response.addProperty("connected", true);
                    response.addProperty("hops", path.size() - 1);

                    JsonArray people = new JsonArray();
                    for (int id : path) {
                        Person person = store.getPerson(id);
                        JsonObject item = new JsonObject();
                        item.addProperty("id", person.getId());
                        item.addProperty("name", person.getName());
                        people.add(item);
                    }
                    response.add("path", people);
                }

                byte[] body = response.toString()
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set(
                        "Content-Type", "application/json; charset=UTF-8");
                exchange.sendResponseHeaders(200, body.length);
                try (var out = exchange.getResponseBody()) {
                    out.write(body);
                }
            } catch (Exception exception) {
                byte[] body = "{\"error\":\"invalid parameters\"}"
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(400, body.length);
                try (var out = exchange.getResponseBody()) {
                    out.write(body);
                }
            } finally {
                exchange.close();
            }
        });

        server.start();
        baseUrl = "http://localhost:" +
                server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void pathEndpointAcceptsExactlyThreeHops() throws Exception {
        createChain(4);

        HttpResponse<String> response = getPath(1, 4);

        assertEquals(200, response.statusCode());

        JsonObject json = JsonParser.parseString(response.body())
                .getAsJsonObject();

        assertTrue(json.get("connected").getAsBoolean());
        assertEquals(3, json.get("hops").getAsInt());
        assertEquals(4, json.getAsJsonArray("path").size());
    }

    @Test
    void pathEndpointRejectsFourHops() throws Exception {
        createChain(5);

        HttpResponse<String> response = getPath(1, 5);

        assertEquals(200, response.statusCode());

        JsonObject json = JsonParser.parseString(response.body())
                .getAsJsonObject();

        assertFalse(json.get("connected").getAsBoolean());
        assertFalse(json.has("hops"));
        assertFalse(json.has("path"));
    }

    @Test
    void pathEndpointReturnsZeroHopsForSamePerson() throws Exception {
        createChain(1);

        HttpResponse<String> response = getPath(1, 1);

        assertEquals(200, response.statusCode());

        JsonObject json = JsonParser.parseString(response.body())
                .getAsJsonObject();

        assertTrue(json.get("connected").getAsBoolean());
        assertEquals(0, json.get("hops").getAsInt());
        assertEquals(1, json.getAsJsonArray("path").size());
    }

    private void createChain(int count) throws Exception {
        for (int i = 1; i <= count; i++) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/people"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            "{\"name\":\"Person " + i + "\"}"))
                    .build();

            HttpResponse<String> response = client.send(
                    request, HttpResponse.BodyHandlers.ofString());

            assertEquals(201, response.statusCode());

            if (i > 1) {
                HttpRequest connection = HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/knows"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"a\":" + (i - 1) + ",\"b\":" + i + "}"))
                        .build();

                HttpResponse<String> connectionResponse = client.send(
                        connection, HttpResponse.BodyHandlers.ofString());

                assertEquals(204, connectionResponse.statusCode());
            }
        }
    }

    private HttpResponse<String> getPath(int from, int to)
            throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(
                        baseUrl + "/path?from=" + from + "&to=" + to))
                .GET()
                .build();

        return client.send(
                request, HttpResponse.BodyHandlers.ofString());
    }
}