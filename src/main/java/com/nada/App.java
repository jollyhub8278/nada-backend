
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
        server.createContext("/people/", App::handlePersonContacts);
        server.createContext("/knows", App::handleKnows);
        server.createContext("/path", App::handlePath);

        server.start();

        System.out.println(
                "Nada API running on http://localhost:8080"
        );
    }

    
private static void handlePath(HttpExchange exchange)
        throws IOException {

    if (!exchange.getRequestMethod().equals("GET")) {
        exchange.getResponseHeaders().set("Allow", "GET");
        sendJson(exchange, 405,
                "{\"error\":\"method not allowed\"}");
        return;
    }

    String query = exchange.getRequestURI().getQuery();

    if (query == null) {
        sendJson(exchange, 400,
                "{\"error\":\"from and to are required\"}");
        return;
    }

    String fromText = null;
    String toText = null;

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

    final int from;
    final int to;

    try {
        if (fromText == null || toText == null) {
            throw new NumberFormatException();
        }

        from = Integer.parseInt(fromText);
        to = Integer.parseInt(toText);

    } catch (NumberFormatException exception) {
        sendJson(exchange, 400,
                "{\"error\":\"from and to must be integer IDs\"}");
        return;
    }

    if (!store.knowsPerson(from) || !store.knowsPerson(to)) {
        sendJson(exchange, 404,
                "{\"error\":\"person not found\"}");
        return;
    }

    PathFinder finder = new PathFinder(store);

    var path = finder.findShortestPath(from, to, 3);

    if (path.isEmpty()) {
        sendJson(exchange, 200,
                "{\"connected\":false}");
        return;
    }

    var response = new java.util.LinkedHashMap<String, Object>();
    response.put("connected", true);
    response.put("hops", path.size() - 1);

    var peopleOnPath = new java.util.ArrayList<Person>();

    for (int id : path) {
        peopleOnPath.add(store.getPerson(id));
    }

    response.put("path", peopleOnPath);

    sendJson(exchange, 200, gson.toJson(response));
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

private static void handlePersonContacts(HttpExchange exchange)
        throws IOException {

    if (!exchange.getRequestMethod().equals("GET")) {
        exchange.getResponseHeaders().set("Allow", "GET");
        sendJson(exchange, 405,
                "{\"error\":\"method not allowed\"}");
        return;
    }

    String path = exchange.getRequestURI().getPath();
    String prefix = "/people/";
    String suffix = "/contacts";

    if (!path.startsWith(prefix) || !path.endsWith(suffix)) {
        sendJson(exchange, 404, "{\"error\":\"not found\"}");
        return;
    }

    String idText = path.substring(
            prefix.length(),
            path.length() - suffix.length()
    );

    final int id;

    try {
        id = Integer.parseInt(idText);
    } catch (NumberFormatException exception) {
        sendJson(exchange, 400,
                "{\"error\":\"invalid person ID\"}");
        return;
    }

    if (!store.knowsPerson(id)) {
        sendJson(exchange, 404,
                "{\"error\":\"person not found\"}");
        return;
    }

    var contactIds = store.getContactIds(id);
    var result = new java.util.ArrayList<Person>();

    for (int contactId : contactIds) {
        result.add(store.getPerson(contactId));
    }

    sendJson(exchange, 200, gson.toJson(result));
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
