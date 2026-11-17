# Snip - URL Shortener

A full-stack URL shortener with a React and TypeScript web interface, a Java
Spring Boot REST API, and SQLite storage. Create short links with generated or
custom aliases, browse them in a paginated list, follow redirects, and delete
links.

---
## Features

- Shorten valid HTTP and HTTPS URLs.
- Generate a seven-character alias, or provide a custom alias.
- Browse URLs newest first, with pagination.
- Redirect short links to their original URLs.
- Delete a link by its alias.
- Store links in SQLite.

## Technology

- **Backend:** Java 17, Spring Boot 3, Maven, SQLite JDBC
- **Frontend:** React 18, TypeScript, Vite
- **Containerization:** Docker and Docker Compose; Nginx serves the frontend and
  proxies API requests to the backend

## Requirements

For local development:

- Java 17
- Maven 3.6 or later
- Node.js 20 and npm

For the containerized setup, install Docker Engine or Docker Desktop with the
Docker Compose plugin.

---

## Run locally

### Start the backend

From the repository root, build and test the backend:

```bash
mvn -f backend/pom.xml clean verify
```

Start the API:

```bash
mvn -f backend/pom.xml spring-boot:run
```

The API listens on <http://localhost:8080>. The default SQLite path is
`./urlshortener.db`, relative to the process working directory. Running the
command above from the repository root uses the database file in the
repository root.

### Start the frontend

In another terminal:

```bash
cd frontend
npm ci
npm run dev
```

Open <http://localhost:3000>. Vite proxies `/shorten` and `/urls` to the local
backend at `http://localhost:8080`. To use the built frontend instead, run
`npm run build` in `frontend/`; Vite writes the production files to `frontend/dist`.

---
## API

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/shorten` | Create a shortened URL |
| `GET` | `/urls` | List all shortened URLs |
| `GET` | `/{alias}` | Redirect to the full URL |
| `DELETE` | `/{alias}` | Delete a shortened URL |

The API is available directly at `http://localhost:8080` in local development.
When using Docker Compose, call it through the frontend origin,
`http://localhost:3000`; Nginx proxies API calls to the backend.

### `POST /shorten`

Create a short link. `fullUrl` is required; `customAlias` is optional.

Request:

```json
{
  "fullUrl": "https://example.com/a/long/path",
  "customAlias": "example-link"
}
```

Omit `customAlias` or pass an empty string to generate a seven-character alias.
Aliases must be 2–64 characters and contain only letters, numbers, and hyphens.
The original URL must be a valid `http` or `https` URL.

Success response (`200 OK`):

```json
{
  "shortUrl": "http://localhost:8080/example-link",
  "alias": "example-link",
  "fullUrl": "https://example.com/a/long/path"
}
```

Returns `400 Bad Request` for invalid input or an alias that is already in use.

### `GET /urls?page={page}&size={size}`

List links, ordered newest first. `page` is zero-based and defaults to `0`;
`size` defaults to `10` and must be between `1` and `100`.

Success response (`200 OK`):

```json
{
  "content": [
    {
      "alias": "example-link",
      "fullUrl": "https://example.com/a/long/path",
      "shortUrl": "http://localhost:8080/example-link"
    }
  ],
  "page": 0,
  "size": 10,
  "totalElements": 1,
  "totalPages": 1
}
```

Returns `400 Bad Request` if `page` is negative or `size` is outside the
permitted range.

### `GET /{alias}`

Redirect to the original URL with `301 Moved Permanently`. Returns `404 Not
Found` when the alias does not exist.

### `DELETE /{alias}`

Delete the link. Returns `204 No Content` on success or `404 Not Found` if the
alias does not exist.

### Try the API with curl

```bash
# Create a link
curl -i -X POST http://localhost:8080/shorten \
  -H 'Content-Type: application/json' \
  -d '{"fullUrl":"https://example.com/a/long/path"}'

# List the first page
curl 'http://localhost:8080/urls?page=0&size=10'

# Follow a short link (add -L to follow the redirect)
curl -i http://localhost:8080/{alias}

# Delete a link
curl -i -X DELETE http://localhost:8080/{alias}
```

Replace `{alias}` with the alias returned by the create request. Short URLs in
responses use the host and port of the incoming request.

---
## Docker Compose

Build and start both containers from the repository root:

```bash
docker compose up --build
```

Open the application at <http://localhost:3000>. The backend is also published
directly at <http://localhost:8080>. The frontend container serves the built
React app with Nginx and proxies API and alias requests to the backend service.

Useful commands:

```bash
# Build the images without starting the services
docker compose build

# Start in the background
docker compose up --build -d

# View service status and logs
docker compose ps
docker compose logs -f
docker compose logs -f backend
docker compose logs -f frontend

# Stop the services
docker compose down
```

The backend stores its SQLite database at `/data/urlshortener.db`. Docker Compose
mounts the named `urlshortener-data` volume at `/data`, so the database persists
when containers are recreated or images are rebuilt. `docker compose down`
keeps this volume. To intentionally delete the database and other volume data,
run `docker compose down -v`.

## Configuration

| Setting | Default | Description |
|---|---|---|
| `DATABASE_PATH` | `./urlshortener.db` | SQLite database file path for the backend |
| `SERVER_PORT` | `8080` | Backend HTTP port |

The backend Docker image sets `DATABASE_PATH=/data/urlshortener.db` and
`SERVER_PORT=8080`. Compose publishes backend port `8080` and frontend port
`3000` on the host.

## Troubleshooting

- **A port is already in use:** Stop the process using port `3000` or `8080`, or
  change the corresponding host port mapping in `docker-compose.yml`.
- **The app cannot reach the API:** Check that the backend is running and inspect
  its output with `docker compose logs backend`. For local development, start the
  backend before using the frontend.
- **A container fails to start or build:** Check service status and logs with
  `docker compose ps` and `docker compose logs`.
- **Previously saved links are missing:** Confirm the `urlshortener-data` volume
  is attached. `docker compose down -v` deletes it and the stored database; do
  not use `-v` if you need to keep your data.

---
## Tests

Run the backend tests from the repository root:

```bash
mvn -f backend/pom.xml test
```

Run the frontend tests:

```bash
cd frontend
npm ci
npm test
```
---
## Repository layout

```text
backend/       Spring Boot API, SQLite repository, and backend tests
frontend/      React application, Nginx configuration, and frontend tests
docker-compose.yml
               Local orchestration for the frontend and backend containers
urlshortener.db
               SQLite database file in the repository
```
