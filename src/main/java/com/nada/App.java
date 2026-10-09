
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

public class App {

    private static final Gson gson = new Gson();
    private static final PersonStore store = new PersonStore();

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(
                new InetSocketAddress(8080), 0
        );

        server.createContext("/people", App::handlePeople);
        server.createContext("/knows", App::handleKnows);

        server.start();

        System.out.println(
                "Nada API running on http://localhost:8080"
        );
    }

    private static void handlePeople(HttpExchange exchange)
            throws IOException {

        if (!exchange.getRequestURI().getPath().equals("/people")) {
            sendJson(exchange, 404, "{\"error\":\"not found\"}");
            return;
        }

        if (!exchange.getRequestMethod().equals("POST")) {
            exchange.getResponseHeaders().set("Allow", "POST");
            sendJson(exchange, 405,
                    "{\"error\":\"method not allowed\"}");
            return;
        }

        try {
            JsonObject request = readJson(exchange);

            if (!request.has("name")
                    || !request.get("name").isJsonPrimitive()
                    || !request.get("name").getAsJsonPrimitive().isString()) {
                sendJson(exchange, 400,
                        "{\"error\":\"name is required\"}");
                return;
            }

            String name = request.get("name").getAsString();

            if (name.isBlank()) {
                sendJson(exchange, 400,
                        "{\"error\":\"name cannot be blank\"}");
                return;
            }

            Person person = store.createPerson(name);
            sendJson(exchange, 201, gson.toJson(person));

        } catch (RuntimeException exception) {
            sendJson(exchange, 400,
                    "{\"error\":\"invalid JSON request\"}");
        }
    }

    private static void handleKnows(HttpExchange exchange)
            throws IOException {

        if (!exchange.getRequestURI().getPath().equals("/knows")) {
            sendJson(exchange, 404, "{\"error\":\"not found\"}");
            return;
        }

        if (!exchange.getRequestMethod().equals("POST")) {
            exchange.getResponseHeaders().set("Allow", "POST");
            sendJson(exchange, 405,
                    "{\"error\":\"method not allowed\"}");
            return;
        }

        try {
            JsonObject request = readJson(exchange);

            if (!request.has("a") || !request.has("b")
                    || !isInteger(request.get("a"))
                    || !isInteger(request.get("b"))) {
                sendJson(exchange, 400,
                        "{\"error\":\"a and b must be integer IDs\"}");
                return;
            }

            int a = request.get("a").getAsInt();
            int b = request.get("b").getAsInt();

            if (a == b) {
                sendJson(exchange, 400,
                        "{\"error\":\"a and b cannot be the same person\"}");
                return;
            }

            if (!store.knowsPerson(a) || !store.knowsPerson(b)) {
                sendJson(exchange, 404,
                        "{\"error\":\"person not found\"}");
                return;
            }

            store.connect(a, b);

            // 204 means success with no response body.
            exchange.sendResponseHeaders(204, -1);

        } catch (RuntimeException exception) {
            sendJson(exchange, 400,
                    "{\"error\":\"invalid JSON request\"}");
        }
    }

    private static boolean isInteger(
            com.google.gson.JsonElement element) {
        return element != null
                && element.isJsonPrimitive()
                && element.getAsJsonPrimitive().isNumber()
                && element.getAsJsonPrimitive()
                        .getAsString().matches("-?\\d+");
    }

    private static JsonObject readJson(HttpExchange exchange)
            throws IOException {

        String body;

        try (InputStream input = exchange.getRequestBody()) {
            body = new String(
                    input.readAllBytes(),
                    StandardCharsets.UTF_8
            );
        }

        return JsonParser.parseString(body).getAsJsonObject();
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
