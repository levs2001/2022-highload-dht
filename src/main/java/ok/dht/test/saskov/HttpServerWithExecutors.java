package ok.dht.test.saskov;

import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.server.AcceptorConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * HttpServer with executors that handle queries instead of selectors.
 * Selectors just send task in ExecutorPool.
 */
public class HttpServerWithExecutors extends HttpServer {

    private static final Logger log = LoggerFactory.getLogger(HttpServerWithExecutors.class);
    private final ExecutorService executorService = Executors.newFixedThreadPool(16);

    // After stopping the server we should give executorService time to finish all tasks.
    // To avoid new tasks during that time I use this value.
    private volatile boolean isStopped;

    public HttpServerWithExecutors(int port) throws IOException {
        super(createConfigFromPort(port));
    }

    @Override
    public void handleRequest(Request request, HttpSession session) throws IOException {
        if (isStopped) {
            return;
        }

        executorService.execute(() -> {
                    try {
                        super.handleRequest(request, session);
                    } catch (IOException e) {
                        log.error("Exception in request handling, by executor service", e);
                    }
                }
        );
    }

    @Override
    public synchronized void stop() {
        isStopped = true;
        executorService.shutdown();
        super.stop();
    }

    @Override
    public void handleDefault(Request request, HttpSession session) throws IOException {
        String resp = request.getMethod() == Request.METHOD_POST ?
                Response.METHOD_NOT_ALLOWED : Response.BAD_REQUEST;
        Response response = new Response(resp, Response.EMPTY);
        session.sendResponse(response);
    }

    private static HttpServerConfig createConfigFromPort(int port) {
        HttpServerConfig httpConfig = new HttpServerConfig();
        AcceptorConfig acceptor = new AcceptorConfig();
        acceptor.port = port;
        acceptor.reusePort = true;
        httpConfig.acceptors = new AcceptorConfig[]{acceptor};
        return httpConfig;
    }
}
