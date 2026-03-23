package server;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class ConfigLoader {
    private String host;
    private int port;
    private String wwwRoot;
    private long maxBodySize;
    private final Map<String, String> errorPages;

    public ConfigLoader(String configPath) throws IOException {
        errorPages = new HashMap<>();
        load(configPath);
    }

    private void load(String configPath) throws IOException {
        String content = new String(Files.readAllBytes(Paths.get(configPath)));

        // Parse host
        host = extractString(content, "host");

        // Parse port
        String portStr = extractString(content, "port");
        port = Integer.parseInt(portStr);

        // Parse wwwRoot
        wwwRoot = extractString(content, "wwwRoot");

        // Parse maxBodySize
        String maxBody = extractString(content, "maxBodySize");
        maxBodySize = Long.parseLong(maxBody);

        // Parse errorPages
        int errorStart = content.indexOf("\"errorPages\"");
        int errorOpen  = content.indexOf("{", errorStart);
        int errorClose = content.indexOf("}", errorOpen);
        String errorBlock = content.substring(errorOpen + 1, errorClose);

        // Parse error pages
        String[] codes = {"400", "404", "403", "405", "413", "500"};
        for (String code : codes) {
            String path = extractFromBlock(errorBlock, code);
            if (path != null) {
                errorPages.put(code, path);
            }
        }
    }

    private String extractString(String json, String key) {
        String search = "\"" + key + "\"";
        int keyIndex = json.indexOf(search);
        if (keyIndex == -1) return null;

        int colonIndex = json.indexOf(":", keyIndex);
        int valueStart = colonIndex + 1;

        // Skip spaces
        while (valueStart < json.length() && json.charAt(valueStart) == ' ') {
            valueStart++;
        }

        if (json.charAt(valueStart) == '"') {
            int start = valueStart + 1;
            int end   = json.indexOf("\"", start);
            return json.substring(start, end);
        } else {
            int end = valueStart;
            while (end < json.length() && (Character.isDigit(json.charAt(end)))) {
                end++;
            }
            return json.substring(valueStart, end);
        }
    }

    private String extractFromBlock(String block, String key) {
        String search = "\"" + key + "\"";
        int keyIndex  = block.indexOf(search);
        if (keyIndex == -1) return null;

        int colonIndex = block.indexOf(":", keyIndex);
        int valueStart = colonIndex + 1;

        while (valueStart < block.length() && block.charAt(valueStart) == ' ') {
            valueStart++;
        }

        if (block.charAt(valueStart) == '"') {
            int start = valueStart + 1;
            int end   = block.indexOf("\"", start);
            return block.substring(start, end);
        }
        return null;
    }

    // Getters
    public String getHost()                    { return host; }
    public int getPort()                       { return port; }
    public String getWwwRoot()                 { return wwwRoot; }
    public long getMaxBodySize()               { return maxBodySize; }
    public Map<String, String> getErrorPages() { return errorPages; }
    public String getErrorPage(String code)    { return errorPages.get(code); }
}