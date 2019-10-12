package ru.mail.polis.service.seliverstov;

import one.nio.http.HttpServer;
import one.nio.http.Param;
import one.nio.http.Path;
import one.nio.http.Request;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Response;
import one.nio.server.AcceptorConfig;
import org.jetbrains.annotations.NotNull;
import ru.mail.polis.dao.DAO;
import ru.mail.polis.dao.NoSuchElementExceptionLite;
import ru.mail.polis.service.Service;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class ServiceImpl extends HttpServer implements Service {
    private DAO dao;

    public ServiceImpl(int port,
                       @NotNull DAO dao) throws IOException {
        super(getConfig(port));
        this.dao = dao;
    }

    /** response status
     * @return - response
     */
    @Path("/v0/status")
    public Response status() {
        return Response.ok("OK");
    }

    /** response entity
     * @param request - requests: GET, PUT, DELETE
     * @param id      - id element
     * @return - response
     */
    @Path("/v0/entity")
    public Response entity(@Param("id") final String id, @NotNull final Request request) {
        if (id == null || id.isEmpty()) {
            return new Response(Response.BAD_REQUEST, "Id must be not null".getBytes(StandardCharsets.UTF_8));
        }

        try {
            final var key = ByteBuffer.wrap(id.getBytes(StandardCharsets.UTF_8));
            switch (request.getMethod()) {
                case Request.METHOD_GET: {
                    try {
                        final ByteBuffer value = dao.get(key);
                        final ByteBuffer duplicate = value.duplicate();
                        final var body = new byte[duplicate.remaining()];
                        duplicate.get(body);
                        return new Response(Response.OK, body);
                    } catch (NoSuchElementExceptionLite | IOException ex) {
                        return new Response(Response.NOT_FOUND, Response.EMPTY);
                    }
                }

                case Request.METHOD_PUT: {
                    dao.upsert(key, ByteBuffer.wrap(request.getBody()));
                    return new Response(Response.CREATED, Response.EMPTY);
                }

                case Request.METHOD_DELETE: {
                    dao.remove(key);
                    return new Response(Response.ACCEPTED, Response.EMPTY);
                }

                default:
                    return new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY);
            }
        } catch (final Exception ex) {
            return new Response(Response.INTERNAL_ERROR, Response.EMPTY);
        }
    }

    @Override
    public void handleDefault(final Request request, final HttpSession session) throws IOException {
        final var response = new Response(Response.BAD_REQUEST, Response.EMPTY);
        session.sendResponse(response);
    }

    private static HttpServerConfig getConfig(final int port) {
        if (port <= 1024 || 65536 <= port) {
            throw new IllegalArgumentException("invalid port");
        }
        final AcceptorConfig acceptor = new AcceptorConfig();
        acceptor.port = port;
        final HttpServerConfig config = new HttpServerConfig();
        config.acceptors = new AcceptorConfig[]{acceptor};
        return config;
    }
}