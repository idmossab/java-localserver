# java-server scaffold

Architecture-only scaffold for a Java HTTP/1.1 server using NIO. No runtime logic implemented yet.

## Structure
- `src/Main.java`: entry point placeholder
- `src/Server.java`: server lifecycle
- `src/Router.java`: route dispatch
- `src/CGIHandler.java`: CGI execution placeholder
- `src/ConfigLoader.java`: config parsing placeholder
- `src/ErrorResponses.java`: default error body helper
- `src/RequestContext.java`: per-connection/request context
- `src/utils/Session.java`: session model placeholder
- `src/utils/Cookie.java`: cookie helper placeholder
- `config.json`: sample configuration
- `error_pages/`: default error HTML

## Next steps
- Implement NIO selector loop in `src/Server.java`
- Build HTTP parser and response writer
- Implement routing, static file serving, CGI, uploads
- Add tests and load testing scripts
