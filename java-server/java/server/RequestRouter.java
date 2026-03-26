package server;

public interface RequestRouter {
    HttpResponse route(HttpRequest request);
}
