# Nada Backend System

A lightweight REST API built with Java 17+ and Maven for managing people, mutual connections, contact lists, and shortest paths in a social graph.

## Features

- **Create people:** Assigns unique, server-generated IDs to people.
- **Mutual connections:** Connects two people bidirectionally without creating duplicate connections.
- **Contact lookup:** Retrieves the direct contacts of a person.
- **Shortest-path search:** Uses Breadth-First Search (BFS) to find the shortest path between two people, limited to three hops.
- **Input validation:** Handles malformed JSON, invalid parameters, missing fields, and unknown people.
- **Unicode support:** Preserves names written in languages such as Hindi.

## Technology Stack

- Java 17+
- Maven
- Gson for JSON parsing and serialization
- JDK `HttpServer` for HTTP endpoints
- JUnit 5 for automated tests

## Prerequisites

Install the following before running the application:

- JDK 17 or later
- Apache Maven 3.8 or later

Verify your installations:

```bash
java -version
mvn -version
```

## Getting Started

### 1. Clone the repository

```bash
git clone https://github.com/jollyhub8278/nada-backend.git
cd nada-backend
```

### 2. Run the automated tests

```bash
mvn test
```

### 3. Start the server

```bash
mvn exec:java "-Dexec.mainClass=com.nada.App"
```

The API starts at:

`http://localhost:8080`

Keep the terminal running while making API requests. The application currently uses in-memory storage, so people and connections are reset when the server restarts.

## API Documentation

All request and response bodies use JSON unless otherwise specified.

### 1. Create a person

**Endpoint:** `POST /people`

Request:

```json
{
  "name": "Asha"
}
```

Example response — `201 Created`:

```json
{
  "id": 1,
  "name": "Asha"
}
```

The server assigns the ID automatically. Names must be non-empty and cannot consist entirely of whitespace.

### 2. Create a mutual connection

**Endpoint:** `POST /knows`

Request:

```json
{
  "a": 1,
  "b": 2
}
```

Response: `204 No Content`

The connection is bidirectional: if person 1 knows person 2, person 2 also knows person 1.

Repeated requests for an existing connection are accepted without creating duplicate contacts. Both people must exist, and a person cannot be connected to themselves.

### 3. Retrieve a person's contacts

**Endpoint:** `GET /people/{id}/contacts`

Example:

```http
GET /people/1/contacts
```

Example response — `200 OK`:

```json
[
  {
    "id": 2,
    "name": "Rohan"
  }
]
```

Returns the person's direct contacts. If the person exists but has no contacts, the endpoint returns an empty JSON array:

```json
[]
```

### 4. Find the shortest path

**Endpoint:** `GET /path?from={id}&to={id}`

Example:

```http
GET /path?from=1&to=4
```

Example response — `200 OK`:

```json
{
  "connected": true,
  "hops": 3,
  "path": [
    {
      "id": 1,
      "name": "Asha"
    },
    {
      "id": 2,
      "name": "Rohan"
    },
    {
      "id": 3,
      "name": "Diya"
    },
    {
      "id": 4,
      "name": "Kabir"
    }
  ]
}
```

The `hops` value represents the number of connections between the starting and destination people, not the number of people in the path.

If no path exists within three hops, the endpoint returns `200 OK`:

```json
{
  "connected": false
}
```

If the source and destination are the same existing person, the result has zero hops and a path containing that person.

Both `from` and `to` are required and must be integer IDs.

## Error Handling

The API uses the following HTTP status codes:

| Status | Meaning |
|---|---|
| `201 Created` | Person created successfully |
| `200 OK` | Request succeeded |
| `204 No Content` | Connection created or already exists |
| `400 Bad Request` | Invalid JSON, invalid parameters, missing fields, or invalid input |
| `404 Not Found` | Requested person or resource does not exist |
| `405 Method Not Allowed` | HTTP method is not supported for the endpoint |

Error responses use a JSON object, for example:

```json
{
  "error": "person not found"
}
```

## Implementation Details

### In-memory data model

`PersonStore` maintains:

- A map from person IDs to `Person` objects.
- A map from person IDs to sets of contact IDs.
- A counter for assigning sequential IDs.

Using a set for contact IDs prevents duplicate connections. Each successful connection updates both people's contact sets.

### Shortest-path algorithm

`PathFinder` implements Breadth-First Search (BFS).

The algorithm:

1. Starts with a path containing the source person.
2. Uses a queue to explore paths in increasing order of length.
3. Tracks visited people to avoid revisiting nodes.
4. Stops expanding paths when they reach the maximum hop count.
5. Returns the first path found to the destination, or an empty list if no qualifying path exists.

BFS finds a shortest path in an unweighted graph because it explores paths with fewer hops before paths with more hops.

For a graph with \(V\) people and \(E\) connections, standard BFS has time complexity \(O(V + E)\) and space complexity \(O(V)\), excluding the additional cost of storing complete paths in the queue.

## Testing

Run the full test suite with:

```bash
mvn test
```

The current test suite includes tests for:

- The existing application test.
- Finding a path within the three-hop limit.
- Rejecting a path that requires four hops.
- Returning a zero-hop path when the source and destination are the same person.

The implementation has also been manually checked for duplicate connections, unknown person IDs, invalid query parameters, and preservation of Hindi names.

## Limitations and Possible Improvements

- **In-memory storage:** Data is lost when the server restarts. A persistent database could be introduced if persistence is required.
- **Concurrency:** The current in-memory maps and ID counter are not designed for concurrent mutation by multiple requests. Thread-safe storage and ID generation would improve reliability.
- **Automated API testing:** Additional integration tests could verify HTTP status codes, request validation, and response bodies across all endpoints.
- **Input validation:** Query parsing and request validation could be centralized and strengthened.
- **Scalability:** For larger graphs, BFS could be optimized to reduce path-copying and memory overhead.

## AI Tools Disclosure

ChatGPT was used as an AI-assisted development tool to help understand Java concepts, reason about the implementation, troubleshoot build and runtime issues, develop the BFS solution, and prepare documentation. The implementation was run and tested locally, including automated tests and manual API checks.

## License

This repository was created for the Nada Backend Engineering assignment. No separate open-source license has been specified.
