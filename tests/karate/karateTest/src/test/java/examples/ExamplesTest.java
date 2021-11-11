package examples;

import com.intuit.karate.junit5.Karate;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.nio.file.Paths;

public class ExamplesTest {

    private static Process process;

    @BeforeAll
    public static void beforeAll() throws IOException {
        String path = Paths.get("..","..", "..", "be1-go").toString();
        String pop = Paths.get(path,"pop").toString();
        String organizer = Paths.get(path,"cli", "organizer").toString();
        String pk = "'J9fBzJV70Jk5c-i3277Uq4CmeL4t53WDfUghaK0HpeM='";
        String[] cmd = {pop, "organizer", "--pk", pk, "serve"};
        process = Runtime.getRuntime().exec(cmd);
        InetAddress IP = InetAddress.getLocalHost();
        System.out.println("IP of my system is := " + IP.getHostAddress());
        BufferedReader output = new BufferedReader(new InputStreamReader(process.getInputStream()));
        System.out.println(output);
    }

    @AfterAll
    public static void afterAll() {
        if (process.isAlive()) {
            process.destroy();
        }
    }
    
    // this will run all *.feature files that exist in sub-directories
    // see https://github.com/intuit/karate#naming-conventions   
    @Karate.Test
    Karate testAll() {
        return Karate.run().relativeTo(getClass());
    }
}