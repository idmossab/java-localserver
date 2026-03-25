package server;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;

public class FileUploadHandler {

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
            response.setBody("{\"error\": \"Content-Type khasso ykun multipart/form-data\"}".getBytes(StandardCharsets.UTF_8), "application/json");
            return response;
        }

        String boundary = extractBoundary(contentType);
        if (boundary == null) {
            HttpResponse response = new HttpResponse();
            response.setStatus(HttpResponse.BAD_REQUEST);
            response.setBody("{\"error\": \"Boundary malsqa\"}".getBytes(StandardCharsets.UTF_8), "application/json");
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

        byte[] boundaryBytes  = ("--" + boundary).getBytes(StandardCharsets.UTF_8);
        byte[] headerEnd      = "\r\n\r\n".getBytes(StandardCharsets.UTF_8);

        int fileSaved = 0;
        int pos       = 0;

        while (pos < body.length) {
            // Find boundary
            int boundaryPos = indexOf(body, boundaryBytes, pos);
            if (boundaryPos == -1) break;

            pos = boundaryPos + boundaryBytes.length;

            // Skip \r\n b3d boundary
            if (pos + 2 <= body.length &&
                body[pos] == '\r' && body[pos + 1] == '\n') {
                pos += 2;
            } else {
                break; // End boundary
            }

            // Find end of headers
            int headerEndPos = indexOf(body, headerEnd, pos);
            if (headerEndPos == -1) break;

            // Extract headers
            String headers = new String(body, pos, headerEndPos - pos, StandardCharsets.UTF_8);
            pos = headerEndPos + headerEnd.length;

            // Find next boundary
            int nextBoundary = indexOf(body, boundaryBytes, pos);
            if (nextBoundary == -1) break;

            // Content = bytes bin pos o next boundary
            int contentEnd = nextBoundary - 2; // -2 = \r\n 9bel boundary
            byte[] content = Arrays.copyOfRange(body, pos, contentEnd);

            // Extract filename
            String filename = extractFilename(headers);
            if (filename != null && !filename.isEmpty()) {
                try {
                    String filePath = uploadDir + "/" + filename;
                    Files.write(Paths.get(filePath), content);
                    fileSaved++;
                } catch (IOException e) {
                    return router.errorResponse(HttpResponse.INTERNAL_SERVER_ERROR, "500");
                }
            }

            pos = nextBoundary;
        }

        if (fileSaved > 0) {
            response.setStatus(HttpResponse.OK);
            response.setBody(("{\"message\": \"" + fileSaved + " fichier(s) mh7fdin!\"}").getBytes(StandardCharsets.UTF_8), "application/json");
        } else {
            response.setStatus(HttpResponse.BAD_REQUEST);
            response.setBody("{\"error\": \"Ma lqina 7ta fichier f request\"}".getBytes(StandardCharsets.UTF_8), "application/json");
        }

        return response;
    }

    // Find byte array f byte array
    private int indexOf(byte[] source, byte[] target, int start) {
        outer:
        for (int i = start; i <= source.length - target.length; i++) {
            for (int j = 0; j < target.length; j++) {
                if (source[i + j] != target[j]) continue outer;
            }
            return i;
        }
        return -1;
    }

    private String extractFilename(String headers) {
        for (String line : headers.split("\r\n")) {
            if (line.contains("Content-Disposition") && line.contains("filename=")) {
                int start = line.indexOf("filename=\"") + "filename=\"".length();
                int end   = line.indexOf("\"", start);
                if (start > 0 && end > start) {
                    return line.substring(start, end);
                }
            }
        }
        return null;
    }
}