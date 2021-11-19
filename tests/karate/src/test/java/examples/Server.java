package examples;

import java.io.IOException;
import java.nio.file.Paths;

public class Server {

    private Process process;

    public Server() {
        process = null;
    }

    public void start() {
        try {
            process = Runtime.getRuntime().exec(getCmd());
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void stop() {
        if (process.isAlive()) {
            process.destroy();
        }
    }

    private String[] getCmd() {
        return ("scala".equals(System.getProperty("karate.env"))) ? scalaCmd() : goCmd();
    }

    private String[] goCmd() {
        String path = Paths.get("..", "..", "be1-go").toString();
        String pop = Paths.get(path, "pop").toString();
        String pk = "J9fBzJV70Jk5c-i3277Uq4CmeL4t53WDfUghaK0HpeM=";
        return new String[]{pop, "organizer", "--pk", pk, "serve"};
    }

    private String[] scalaCmd() {
        String path = Paths.get("..", "..", "be2-scala").toString();
        String pathConfig = Paths.get("src", "main", "scala", "ch", "epfl", "pop", "config").toString();
        String config = "-Dscala.config=" + pathConfig;
        return new String[]{"cd", path, ";", "sbt", config, "run"};
    }
}
