package server;

import java.util.LinkedHashMap;
import java.util.Map;

public final class VirtualHostRouter implements RequestRouter {
    private final LinkedHashMap<String, RequestRouter> routersByServerName;
    private final RequestRouter defaultRouter;

    public VirtualHostRouter(LinkedHashMap<String, RequestRouter> routersByServerName, RequestRouter defaultRouter) {
        this.routersByServerName = new LinkedHashMap<>(routersByServerName);
        this.defaultRouter = defaultRouter;
    }

    @Override
    public HttpResponse route(HttpRequest request) {
        String hostHeader = request.getHeaders().get("Host");
        if (hostHeader != null) {
            String requestedServerName = normalizeHost(hostHeader);
            RequestRouter router = routersByServerName.get(requestedServerName);
            if (router != null) {
                return router.route(request);
            }
        }

        return defaultRouter.route(request);
    }

    private String normalizeHost(String hostHeader) {
        String trimmed = hostHeader.trim();
        int separatorIndex = trimmed.indexOf(':');
        if (separatorIndex >= 0) {
            return trimmed.substring(0, separatorIndex);
        }
        return trimmed;
    }

    public static VirtualHostRouter fromSingle(String serverName, RequestRouter router) {
        LinkedHashMap<String, RequestRouter> routers = new LinkedHashMap<>();
        routers.put(serverName, router);
        return new VirtualHostRouter(routers, router);
    }

    public static VirtualHostRouter merge(VirtualHostRouter existing, String serverName, RequestRouter router) {
        LinkedHashMap<String, RequestRouter> mergedRouters = new LinkedHashMap<>();

        for (Map.Entry<String, RequestRouter> entry : existing.routersByServerName.entrySet()) {
            mergedRouters.put(entry.getKey(), entry.getValue());
        }
        mergedRouters.put(serverName, router);

        return new VirtualHostRouter(mergedRouters, existing.defaultRouter);
    }
}
