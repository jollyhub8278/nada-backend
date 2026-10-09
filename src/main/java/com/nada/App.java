package com.nada;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class App {

    private static final Gson gson = new Gson();
    private static final Map<Integer, Person> people = new HashMap<>();
    private static int nextId = 1;

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(
                new InetSocketAddress(8080), 0
        );

        server.createContext("/people", App::handlePeople);

        server.start();

        System.out.println("Nada API running on http://localhost:8080");
    }

    private static void handlePeople(HttpExchange exchange)
            throws IOException {

        if (!exchange.getRequestURI().getPath().equals("/people")) {
            sendJson(exchange, 404, "{\"error\":\"not found\"}");
            return;
        }

        if (!exchange.getRequestMethod().equals("POST")) {
            exchange.getResponseHeaders().set("Allow", "POST");
            sendJson(exchange, 405, "{\"error\":\"method not allowed\"}");
            return;
        }

        try {
            String body;

            try (InputStream input = exchange.getRequestBody()) {
                body = new String(
                        input.readAllBytes(),
                        StandardCharsets.UTF_8
                );
            }

            JsonObject request = JsonParser.parseString(body)
                    .getAsJsonObject();

            if (!request.has("name")
                    || !request.get("name").isJsonPrimitive()
                    || !request.get("name").getAsJsonPrimitive().isString()) {
                sendJson(exchange, 400, "{\"error\":\"name is required\"}");
                return;
            }

            String name = request.get("name").getAsString();

            if (name.isBlank()) {
                sendJson(exchange, 400, "{\"error\":\"name cannot be blank\"}");
                return;
            }

            Person person = new Person(nextId++, name);
            people.put(person.getId(), person);

            sendJson(exchange, 201, gson.toJson(person));

        } catch (RuntimeException exception) {
            sendJson(exchange, 400, "{\"error\":\"invalid JSON request\"}");
        }
    }

    private static void sendJson(
            HttpExchange exchange,
            int statusCode,
            String response
    ) throws IOException {

        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set(
                "Content-Type", "application/json; charset=UTF-8"
        );

        exchange.sendResponseHeaders(statusCode, bytes.length);

        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}