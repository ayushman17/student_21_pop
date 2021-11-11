package examples.messages;

import com.intuit.karate.junit5.Karate;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.nio.file.Paths;
import java.util.Arrays;

public class MessagesRunner {

    private static Process process;

    @BeforeAll
    public static void beforeAll() throws IOException, InterruptedException {
//        String path = Paths.get("..","..", "..", "be1-go").toString();
//        String pop = Paths.get(path,"pop").toString();
//        String organizer = Paths.get(path,"cli", "organizer").toString();
//        String pk = "'J9fBzJV70Jk5c-i3277Uq4CmeL4t53WDfUghaK0HpeM='";
//        String[] cmd = {pop, "organizer", "--pk", pk, "serve", "\\k"};
//        process = Runtime.getRuntime().exec(cmd);
//        process.waitFor();
//        BufferedReader output = new BufferedReader(new InputStreamReader(process.getInputStream()));
//        System.out.println(Arrays.toString(output.lines().toArray()));
//        InetAddress IP = InetAddress.getLocalHost();
//        System.out.println("IP of my system is := " + IP.getHostAddress());
    }

    @AfterAll
    public static void afterAll() {
//        if (process.isAlive()) {
//            process.destroy();
//        }
    }

    @Karate.Test
    Karate testMessages() {
        return Karate.run("classpath:examples/messages/messages.feature");
    }
}