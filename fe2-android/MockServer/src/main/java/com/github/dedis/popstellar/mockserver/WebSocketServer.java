package com.github.dedis.popstellar.mockserver;

import static java.lang.String.format;

import java.io.IOException;
import java.util.Locale;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.websocket.CloseReason;
import javax.websocket.DeploymentException;
import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;
import org.glassfish.tyrus.server.Server;

/**
 * Simple WebSocket server used to debug
 */
public class WebSocketServer {

  public static final String LOCALHOST = "localhost";
  public static final int PORT = 8000;

  public static void main(String[] args) {
    Server server = new Server(LOCALHOST, PORT, "", PoPServerEndpoint.class);

    try {
      server.start();
      System.out.println("Press enter to stop the server..");
      new Scanner(System.in).nextLine();
    } catch (DeploymentException e) {
      throw new RuntimeException(e);
    } finally {
      server.stop();
    }
  }

  @ServerEndpoint(value = "/")
  public static class PoPServerEndpoint {

    public static final String JSONRPC_FORMAT = "{\"jsonrpc\": \"2.0\",\"result\": 0,\"id\": %d}";
    public static final String ID_REGEX = "\"id\":(-?\\d+)";

    @OnOpen
    public void onOpen(Session session) {
      System.out.println(format("Connected with %s", session.getId()));
    }

    @OnMessage
    public void onMessage(String message, Session session) {
      Pattern pattern = Pattern.compile(ID_REGEX);
      Matcher matcher = pattern.matcher(message);
      if (matcher.find()) {
        try {
          session
              .getBasicRemote()
              .sendText(
                  String.format(Locale.US, JSONRPC_FORMAT, Integer.parseInt(matcher.group(1))));
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }

    @OnClose
    public void onClose(Session session, CloseReason reason) {
      System.out.println(
          format("Disconnected with %s, reason : %s", session.getId(), reason.toString()));
    }
  }
}
