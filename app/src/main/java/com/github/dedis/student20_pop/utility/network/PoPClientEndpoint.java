package com.github.dedis.student20_pop.utility.network;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.github.dedis.student20_pop.model.Person;

import org.glassfish.tyrus.client.ClientManager;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import javax.websocket.ClientEndpoint;
import javax.websocket.CloseReason;
import javax.websocket.DeploymentException;
import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;

/**
 * A client endpoint to a web socket using jsr 365 library
 * <p>
 * TODO link onOpen and onRemove to the UI
 */
@ClientEndpoint()
public final class PoPClientEndpoint {

    private static final String TAG = PoPClientEndpoint.class.getName();

    private static final ClientManager client = ClientManager.createClient();
    private static final Map<Session, LowLevelClientProxy> listeners = new HashMap<>();

    /**
     * Create asynchronously a new HighLevelClientProxy that will encapsulate the socket
     *
     * @param host   to connect to
     * @param issuer the person whose device issued the connection
     * @return A completable future that will complete with the proxy once connection is established.
     * If the connection cannot be established, the future with complete with an exception
     */
    public static CompletableFuture<HighLevelClientProxy> connectAsync(URI host, Person issuer) {
        CompletableFuture<HighLevelClientProxy> proxyFuture = new CompletableFuture<>();

        Thread t = new Thread(() -> {
            Looper.prepare();
            try {
                proxyFuture.complete(connect(host, issuer));
            } catch (DeploymentException e) {
                proxyFuture.completeExceptionally(e);
            }
            Looper.loop();
        });
        t.setDaemon(true);
        t.start();

        return proxyFuture;
    }

    /**
     * Create a new HighLevelClientProxy that will encapsulate the socket
     *
     * @param host   to connect to
     * @param issuer the person whose device issued the connection
     * @return the proxy holding the connection
     */
    public static HighLevelClientProxy connect(URI host, Person issuer) throws DeploymentException {
        Session session = client.connectToServer(PoPClientEndpoint.class, host);
        HighLevelClientProxy client = new HighLevelClientProxy(session, issuer);

        synchronized (listeners) {
            listeners.put(session, client.lowLevel());
        }

        return client;
    }

    public static void startPurgeRoutine(Handler handler) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                synchronized (listeners) {
                    listeners.values().forEach(LowLevelClientProxy::purge);
                    handler.postDelayed(this, LowLevelClientProxy.TIMEOUT);
                }
            }
        });
    }

    @OnOpen
    public void onOpen(Session session) {
        Log.i(TAG, "Client successfully connected to " + session.getId());
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        // Messages from a session are taken care one at the time to avoid any problem
        // In the future, we could improve this
        synchronized (listeners) {
            LowLevelClientProxy client = listeners.get(session);
            if (client == null)
                throw new IllegalArgumentException();

            client.onMessage(message);
        }
    }

    @OnClose
    public void onClose(Session session, CloseReason reason) {
        synchronized (listeners) {
            listeners.remove(session);
        }
    }
}