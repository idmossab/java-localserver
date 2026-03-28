# Java Local Server

This project is a small HTTP server written in Java.
It reads its configuration from `config.json`, serves static files, supports route-based roots, custom error pages, simple session-based login/logout, file upload, and CGI execution for Python scripts.

## What This Project Does

- Parses a custom JSON config file
- Starts an HTTP server on the configured host and port
- Serves static files from route-specific root directories
- Supports directory listing with `autoindex`
- Uses custom HTML error pages for `400`, `403`, `404`, `405`, `413`, and `500`
- Supports CGI execution for `.py` files
- Supports simple login, profile, and logout with cookies/sessions
- Supports multipart file upload
- Runs on a single Java event-loop thread

## Project Structure

```text
java-server/
├── config.json
├── config.backup.json
├── run.sh
├── README.md
├── errors/
│   ├── 400.html
│   ├── 403.html
│   ├── 404.html
│   ├── 405.html
│   ├── 413.html
│   └── 500.html
├── root/
│   ├── root.html
│   ├── app/test.py
│   ├── best/root.html
│   └── best/match/root.html
├── www/
│   ├── index.html
│   ├── login.html
│   ├── profile.html
│   ├── upload.html
│   └── cgi_test.py
└── java/
    ├── config/
    ├── server/
    └── utils/
```

## Main Java Files

- `java/server/Main.java`: loads `config.json`, builds routers, and starts the server
- `java/server/Server.java`: non-blocking socket server using `Selector`
- `java/server/Router.java`: route matching, static files, CGI, auth routes, uploads, and errors
- `java/server/CGIHandler.java`: runs CGI scripts and builds CGI environment variables
- `java/server/FileUploadHandler.java`: parses multipart uploads and saves files
- `java/server/HttpRequest.java`: parses the raw HTTP request
- `java/server/HttpResponse.java`: builds the HTTP response
- `java/server/RequestContext.java`: internal CGI request/response context
- `java/config/ParsingHandler.java`: parses and validates `config.json`
- `java/config/ConfigLoader.java`: exposes loaded config values to the server
- `java/utils/Cookie.java`: cookie parsing/building
- `java/utils/Session.java`: in-memory session store

## Current Config

The server currently reads this kind of configuration from `config.json`:

- host: `127.0.0.1`
- port: `8080`
- server name: `localhost`
- body size limit: `100000000000`
- timeout: `60` seconds

Configured routes:

- `/`
  uses root directory `root`
  allows `GET`
  `autoindex` enabled

- `/best`
  uses root directory `www`
  allows `GET`
  default file `index.html`
  `autoindex` enabled

Configured CGI:

- `.py` scripts use `/usr/bin/python3`
- `cgi_root` is currently `root`

Configured custom error pages:

- `400 -> errors/400.html`
- `403 -> errors/403.html`
- `404 -> errors/404.html`
- `405 -> errors/405.html`
- `413 -> errors/413.html`
- `500 -> errors/500.html`

## Available Features

### 1. Static File Serving

The router resolves the best matching route, computes the effective root directory, and serves files from disk.

Examples:

- `GET /`
- `GET /best`
- `GET /best/index.html`

### 2. Autoindex / Directory Listing

If the requested path is a directory and `autoindex` is enabled, the server generates an HTML listing of files and folders.

### 3. Default File Resolution

If a route points to a directory and a default file exists, the server serves it.

Example:

- `/best` can serve `www/index.html`

### 4. CGI Support

Python CGI scripts are supported through the CGI handler.

The CGI handler:

- checks the target script path
- selects the interpreter from `config.json`
- launches the script with `ProcessBuilder`
- sends request body to stdin
- sets CGI environment variables
- reads headers/body from script output
- returns the CGI response to the client

Example CGI file in this project:

- `www/cgi_test.py`

### 5. Login, Profile, and Logout

Simple session-based auth is included.

Routes:

- `POST /login`
- `GET /profile`
- `DELETE /logout`

Behavior:

- login creates a session and sends a cookie
- profile reads the session from the cookie
- logout expires the cookie

### 6. File Upload

The server supports multipart upload on:

- `POST /upload`

Uploaded files are saved under:

- `<root>/uploads`

The upload handler parses `multipart/form-data` manually from raw bytes.

### 7. Custom Error Pages

When an error happens, the router tries to load the matching error page from the configured `errors/` directory.

Supported codes in the project:

- `400`
- `403`
- `404`
- `405`
- `413`
- `500`

## Single-Thread Model

This server works on a single Java thread for the event loop.

- `Server.java` uses one `Selector`
- requests are accepted and read in the same loop
- there is no Java thread pool or worker thread system

Important note:

- CGI starts an external process
- while the Java server waits for that CGI process, the event loop is blocked

So the Java server is single-threaded, even though CGI runs as a child process.

## How To Run

From the `java-server` folder:

```bash
sh run.sh
```

Or manually:

```bash
javac -d build java/config/*.java java/utils/*.java java/server/*.java
java -cp build server.Main
```

## Example Requests

Open in browser:

```text
http://127.0.0.1:8080/
http://127.0.0.1:8080/best
http://127.0.0.1:8080/best/index.html
http://127.0.0.1:8080/best/cgi_test.py
```

Test CGI with curl:

```bash
curl -i http://127.0.0.1:8080/best/cgi_test.py
```

Test login:

```bash
curl -i -X POST http://127.0.0.1:8080/login -d "username=mo"
```

Test profile with cookie:

```bash
curl -i http://127.0.0.1:8080/profile --cookie "sessionId=YOUR_SESSION_ID"
```

Test logout:

```bash
curl -i -X DELETE http://127.0.0.1:8080/logout --cookie "sessionId=YOUR_SESSION_ID"
```

Test upload:

```bash
curl -i -X POST http://127.0.0.1:8080/upload \
  -F "file=@/path/to/file.txt"
```

## Notes

- The project uses a custom JSON parser instead of an external library
- Sessions are stored in memory
- Config values are validated in `ParsingHandler`
- The current server behavior depends heavily on `config.json`
- CGI for Python depends on the configured interpreter path existing on the machine

## Summary

This project is a custom Java HTTP server with:

- config-driven routing
- static file serving
- autoindex
- custom error pages
- Python CGI support
- login/logout/profile with sessions
- multipart upload
- single-threaded Java event loop
