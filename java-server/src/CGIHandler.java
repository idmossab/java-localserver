import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public final class CGIHandler {
    private static final long POLL_INTERVAL_MILLIS = 25L;

    private final ConfigLoader config;

    public CGIHandler(ConfigLoader config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    public void execute(RequestContext ctx) {
        Objects.requireNonNull(ctx, "ctx");

        try {
            RequestContext.RouteMatch routeMatch = ctx.requireRouteMatch();
            if (!routeMatch.isCgiEnabled()) {
                throw new IllegalStateException("Request route is not configured for CGI execution");
            }

            Path scriptPath = requireReadableScript(routeMatch.getScriptPath());
            String extension = requireExtension(routeMatch.getCgiExtension(), scriptPath);
            String interpreter = config.getCgiInterpreter(extension);
            if (interpreter == null || interpreter.isBlank()) {
                throw new IllegalStateException("No configured interpreter for CGI extension: " + extension);
            }

            ProcessBuilder builder = new ProcessBuilder(interpreter, scriptPath.toString());
            Path workingDirectory = routeMatch.getWorkingDirectory() == null
                    ? scriptPath.getParent()
                    : routeMatch.getWorkingDirectory().toAbsolutePath().normalize();
            if (workingDirectory != null) {
                builder.directory(workingDirectory.toFile());
            }
            builder.redirectErrorStream(true);
            populateEnvironment(builder.environment(), ctx, routeMatch, scriptPath);

            Process process = builder.start();
            writeDecodedBody(process, ctx.getRequestBody());

            CGIOutput output = captureOutput(process, config.getCgiTimeoutMillis());
            if (output.exitCode != 0) {
                throw new IllegalStateException("CGI process exited with code " + output.exitCode);
            }

            ctx.setResponseStatus(output.statusCode);
            for (Map.Entry<String, List<String>> headerEntry : output.headers.entrySet()) {
                for (String value : headerEntry.getValue()) {
                    ctx.addResponseHeader(headerEntry.getKey(), value);
                }
            }
            ctx.setResponseBody(output.body);
        } catch (Exception e) {
            ctx.addNote("CGI failure: " + e.getMessage());
            ctx.setResponseStatus(500);
            ctx.addResponseHeader("Content-Type", "text/html; charset=UTF-8");
            ctx.setResponseBody(ErrorResponses.defaultBody(500));
        }
    }

    private static Path requireReadableScript(Path scriptPath) {
        if (scriptPath == null) {
            throw new IllegalStateException("CGI script path is missing");
        }
        Path normalized = scriptPath.toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalized) || !Files.isReadable(normalized)) {
            throw new IllegalStateException("CGI script is not readable: " + normalized);
        }
        return normalized;
    }

    private static String requireExtension(String configuredExtension, Path scriptPath) {
        if (configuredExtension != null && !configuredExtension.isBlank()) {
            return configuredExtension;
        }
        String fileName = scriptPath.getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0) {
            throw new IllegalStateException("CGI script has no extension: " + fileName);
        }
        return fileName.substring(dotIndex);
    }

    private void populateEnvironment(
            Map<String, String> environment,
            RequestContext ctx,
            RequestContext.RouteMatch routeMatch,
            Path scriptPath) {
        environment.put("GATEWAY_INTERFACE", "CGI/1.1");
        environment.put("SERVER_PROTOCOL", "HTTP/1.1");
        environment.put("REQUEST_METHOD", ctx.getMethod());
        environment.put("REQUEST_URI", ctx.getRawTarget());
        environment.put("QUERY_STRING", ctx.getQueryString());
        environment.put("SCRIPT_NAME", routeMatch.getScriptName() == null ? scriptPath.getFileName().toString() : routeMatch.getScriptName());
        environment.put("SCRIPT_FILENAME", scriptPath.toString());
        environment.put("PATH_INFO", resolvePathInfo(routeMatch, scriptPath));
        environment.put("SERVER_NAME", ctx.getServerName());
        environment.put("SERVER_PORT", Integer.toString(ctx.getServerPort()));
        environment.put("DOCUMENT_ROOT", config.getProjectRoot().toString());

        String contentType = ctx.getHeader("content-type");
        if (contentType != null && !contentType.isBlank()) {
            environment.put("CONTENT_TYPE", contentType);
        }

        byte[] body = ctx.getRequestBody();
        if (body.length > 0) {
            environment.put("CONTENT_LENGTH", Integer.toString(body.length));
        } else {
            environment.remove("CONTENT_LENGTH");
        }

        String cookieHeader = ctx.getHeader("cookie");
        if (cookieHeader != null && !cookieHeader.isBlank()) {
            environment.put("HTTP_COOKIE", cookieHeader);
        }

        for (Map.Entry<String, String> headerEntry : ctx.getRequestHeaders().entrySet()) {
            String headerName = headerEntry.getKey();
            if (headerName.equals("content-type") || headerName.equals("content-length") || headerName.equals("cookie")) {
                continue;
            }
            String cgiName = "HTTP_" + headerName.toUpperCase().replace('-', '_');
            environment.put(cgiName, headerEntry.getValue());
        }
    }

    private static String resolvePathInfo(RequestContext.RouteMatch routeMatch, Path scriptPath) {
        Path resolvedPathInfo = routeMatch.getPathInfoFilePath();
        if (resolvedPathInfo != null) {
            return resolvedPathInfo.toString();
        }
        if (routeMatch.getPathInfo() != null && !routeMatch.getPathInfo().isBlank()) {
            return scriptPath.getParent().resolve(routeMatch.getPathInfo().replaceFirst("^/", "")).normalize().toString();
        }
        return scriptPath.toString();
    }

    private static void writeDecodedBody(Process process, byte[] body) throws IOException {
        try (OutputStream outputStream = process.getOutputStream()) {
            if (body != null && body.length > 0) {
                outputStream.write(body);
            }
            outputStream.flush();
        }
    }

    private static CGIOutput captureOutput(Process process, long timeoutMillis) throws IOException, InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        ByteArrayOutputStream rawOutput = new ByteArrayOutputStream();
        try (InputStream inputStream = process.getInputStream()) {
            boolean finished = false;
            while (System.nanoTime() < deadline) {
                drainAvailable(inputStream, rawOutput);
                if (process.waitFor(POLL_INTERVAL_MILLIS, TimeUnit.MILLISECONDS)) {
                    finished = true;
                    break;
                }
            }
            if (!finished) {
                process.destroy();
                if (!process.waitFor(POLL_INTERVAL_MILLIS, TimeUnit.MILLISECONDS)) {
                    process.destroyForcibly();
                    process.waitFor(POLL_INTERVAL_MILLIS, TimeUnit.MILLISECONDS);
                }
                throw new IllegalStateException("CGI process timed out after " + timeoutMillis + "ms");
            }
            drainRemaining(inputStream, rawOutput);
        }
        return parseOutput(rawOutput.toByteArray(), process.exitValue());
    }

    private static void drainAvailable(InputStream inputStream, ByteArrayOutputStream sink) throws IOException {
        int available = inputStream.available();
        while (available > 0) {
            byte[] chunk = inputStream.readNBytes(Math.min(available, 4096));
            if (chunk.length == 0) {
                break;
            }
            sink.write(chunk);
            available = inputStream.available();
        }
    }

    private static void drainRemaining(InputStream inputStream, ByteArrayOutputStream sink) throws IOException {
        byte[] chunk;
        while ((chunk = inputStream.readNBytes(4096)).length > 0) {
            sink.write(chunk);
        }
    }

    private static CGIOutput parseOutput(byte[] rawOutput, int exitCode) {
        int headerLength = findHeaderSectionLength(rawOutput);
        if (headerLength < 0) {
            Map<String, List<String>> headers = new LinkedHashMap<>();
            headers.put("Content-Type", List.of("text/plain; charset=UTF-8"));
            return new CGIOutput(200, headers, rawOutput, exitCode);
        }

        String headerText = new String(rawOutput, 0, headerLength, StandardCharsets.UTF_8);
        byte[] body = new byte[rawOutput.length - headerLength];
        System.arraycopy(rawOutput, headerLength, body, 0, body.length);

        Map<String, List<String>> headers = new LinkedHashMap<>();
        int statusCode = 200;
        String[] lines = headerText.split("\\r?\\n");
        for (String line : lines) {
            if (line.isBlank()) {
                continue;
            }
            int separator = line.indexOf(':');
            if (separator <= 0) {
                continue;
            }
            String name = line.substring(0, separator).trim();
            String value = line.substring(separator + 1).trim();
            if (name.equalsIgnoreCase("Status")) {
                statusCode = parseStatusCode(value);
                continue;
            }
            headers.computeIfAbsent(name, unused -> new ArrayList<>()).add(value);
        }

        if (headers.isEmpty()) {
            headers.put("Content-Type", List.of("text/plain; charset=UTF-8"));
        }
        return new CGIOutput(statusCode, headers, body, exitCode);
    }

    private static int findHeaderSectionLength(byte[] rawOutput) {
        for (int i = 0; i < rawOutput.length - 3; i++) {
            if (rawOutput[i] == '\r' && rawOutput[i + 1] == '\n'
                    && rawOutput[i + 2] == '\r' && rawOutput[i + 3] == '\n') {
                return i + 4;
            }
        }
        for (int i = 0; i < rawOutput.length - 1; i++) {
            if (rawOutput[i] == '\n' && rawOutput[i + 1] == '\n') {
                return i + 2;
            }
        }
        return -1;
    }

    private static int parseStatusCode(String value) {
        String[] parts = value.split("\\s+", 2);
        try {
            return Integer.parseInt(parts[0]);
        } catch (NumberFormatException e) {
            return 200;
        }
    }

    private static final class CGIOutput {
        private final int statusCode;
        private final Map<String, List<String>> headers;
        private final byte[] body;
        private final int exitCode;

        private CGIOutput(int statusCode, Map<String, List<String>> headers, byte[] body, int exitCode) {
            this.statusCode = statusCode;
            this.headers = headers;
            this.body = body;
            this.exitCode = exitCode;
        }
    }
}
