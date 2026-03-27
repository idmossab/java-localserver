package server;

public final class ErrorResponses {
    private ErrorResponses() {
    }

    public static String defaultBody(int statusCode) {
        String reasonPhrase = switch (statusCode) {
            case 400 -> "Bad Request";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            case 413 -> "Payload Too Large";
            case 500 -> "Internal Server Error";
            case 504 -> "Gateway Timeout";
            default -> "HTTP Error";
        };
        return "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><title>" + statusCode + " " + reasonPhrase
                + "</title></head><body><h1>" + statusCode + " " + reasonPhrase + "</h1></body></html>";
    }
}
