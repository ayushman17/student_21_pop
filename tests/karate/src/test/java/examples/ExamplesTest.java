package examples;

import com.intuit.karate.junit5.Karate;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

public class ExamplesTest {

    private static Server server;

    @BeforeAll
    public static void beforeAll() {
        server = new Server();
        server.start();
    }

    @AfterAll
    public static void afterAll() {
        server.stop();
    }
    
    // this will run all *.feature files that exist in sub-directories
    // see https://github.com/intuit/karate#naming-conventions   
    @Karate.Test
    Karate testAll() {
        return Karate.run().relativeTo(getClass());
    }
}