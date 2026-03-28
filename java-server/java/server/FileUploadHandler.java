package server;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;

public class FileUploadHandler {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final String uploadDir;
    private final Router router;

    public FileUploadHandler(String uploadDir, Router router) {
        this.uploadDir = uploadDir;
        this.router = router;
    }

    public HttpResponse handle(HttpRequest request) {
        String contentType = request.getHeaders().get("Content-Type");

        if (contentType == null || !contentType.contains("multipart/form-data")) {
            HttpResponse response = new HttpResponse();
            response.setStatus(HttpResponse.BAD_REQUEST);
            response.setBody(
                    "{\"error\": \"Content-Type must be multipart/form-data\"}".getBytes(StandardCharsets.UTF_8),
                    "application/json");
            return response;
        }

        String boundary = extractBoundary(contentType);
        if (boundary == null) {
            HttpResponse response = new HttpResponse();
            response.setStatus(HttpResponse.BAD_REQUEST);
            response.setBody("{\"error\": \"Boundary missing\"}".getBytes(StandardCharsets.UTF_8), "application/json");
            return response;
        }

        byte[] rawBytes = request.getRawBody();
        return parseMultipartBytes(rawBytes, boundary);
    }

    private String extractBoundary(String contentType) {
        String[] parts = contentType.split(";");
        for (String part : parts) {
            part = part.trim();
            if (part.startsWith("boundary=")) {
                return part.substring("boundary=".length());
            }
        }
        return null;
    }

    private HttpResponse parseMultipartBytes(byte[] body, String boundary) {
        HttpResponse response = new HttpResponse();

        byte[] boundaryBytes = ("--" + boundary).getBytes(StandardCharsets.UTF_8);
        byte[] closingBoundaryBytes = ("--" + boundary + "--").getBytes(StandardCharsets.UTF_8);
        byte[] headerEnd = "\r\n\r\n".getBytes(StandardCharsets.UTF_8);

        int fileSaved = 0;
        int pos = 0;

        while (pos < body.length) {
            // Find boundary (normal or closing)
            int boundaryPos = indexOf(body, boundaryBytes, pos);
            if (boundaryPos == -1) {
                break;
            }

            pos = boundaryPos + boundaryBytes.length;

            // If closing boundary, stop
            if (pos + 2 <= body.length && body[pos] == '-' && body[pos + 1] == '-') {
                break;
            }

            // Skip CRLF after boundary
            if (pos + 2 <= body.length && body[pos] == '\r' && body[pos + 1] == '\n') {
                pos += 2;
            } else {
                break;
            }

            // Find end of headers
            int headerEndPos = indexOf(body, headerEnd, pos);
            if (headerEndPos == -1) {
                break;
            }

            // Extract headers
            String headers = new String(body, pos, headerEndPos - pos, StandardCharsets.UTF_8);
            pos = headerEndPos + headerEnd.length;

            // Find next boundary (normal or closing) after content
            int nextBoundary = indexOf(body, boundaryBytes, pos);
            int nextClosing = indexOf(body, closingBoundaryBytes, pos);
            if (nextBoundary == -1 || (nextClosing != -1 && nextClosing < nextBoundary)) {
                nextBoundary = nextClosing;
            }

            // If neither found, set to end
            if (nextBoundary == -1) {
                nextBoundary = body.length;
            }

            // Content end excluding CRLF before boundary
            int contentEnd = nextBoundary;
            if (contentEnd - 2 >= pos && body[contentEnd - 2] == '\r' && body[contentEnd - 1] == '\n') {
                contentEnd -= 2;
            }

            // Extract filename and write directly from source buffer
            String filename = extractFilename(headers);
            if (filename != null && !filename.isEmpty()) {
                try {
                    String storedFilename = buildStoredFilename(filename);
                    String filePath = uploadDir + "/" + storedFilename;
                    Files.createDirectories(Paths.get(uploadDir));
                    try (var out = Files.newOutputStream(Paths.get(filePath))) {
                        out.write(body, pos, Math.max(0, contentEnd - pos));
                    }
                    fileSaved++;
                } catch (IOException e) {
                    return router.errorResponse(HttpResponse.INTERNAL_SERVER_ERROR, "500");
                }
            }

            // Move to next boundary and continue
            pos = nextBoundary;
        }

        if (fileSaved > 0) {
            response.setStatus(HttpResponse.OK);
            response.setBody(
                    ("{\"message\": \"" + fileSaved + " fichier(s) uploaded successfully!\"}").getBytes(StandardCharsets.UTF_8),
                    "application/json");
        } else {
            response.setStatus(HttpResponse.BAD_REQUEST);
            response.setBody("{\"error\": \"No files found in request\"}".getBytes(StandardCharsets.UTF_8),
                    "application/json");
        }

        return response;
    }

    // Find byte array f byte array
    private int indexOf(byte[] source, byte[] target, int start) {
        outer: for (int i = start; i <= source.length - target.length; i++) {
            for (int j = 0; j < target.length; j++) {
                if (source[i + j] != target[j])
                    continue outer;
            }
            return i;
        }
        return -1;
    }

    private String extractFilename(String headers) {
        for (String line : headers.split("\r\n")) {
            if (line.contains("Content-Disposition") && line.contains("filename=")) {
                int start = line.indexOf("filename=\"") + "filename=\"".length();
                int end = line.indexOf("\"", start);
                if (start > 0 && end > start) {
                    return line.substring(start, end);
                }
            }
        }
        return null;
    }

    private String buildStoredFilename(String originalFilename) {
        String safeName = Path.of(originalFilename).getFileName().toString().trim().replace(' ', '_');
        if (safeName.isEmpty()) {
            safeName = "upload";
        }

        int dotIndex = safeName.lastIndexOf('.');
        String baseName = dotIndex > 0 ? safeName.substring(0, dotIndex) : safeName;
        String extension = dotIndex > 0 ? safeName.substring(dotIndex) : "";

        return baseName + randomSuffix(24) + extension;
    }

    private String randomSuffix(int length) {
        final String alphabet = "abcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(alphabet.charAt(RANDOM.nextInt(alphabet.length())));
        }
        return builder.toString();
    }
}
