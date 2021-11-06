package examples;

import com.intuit.karate.junit5.Karate;
import org.junit.After;
import org.junit.Before;

import java.io.IOException;
import java.nio.file.Paths;

class ExamplesTest {

//    private static Process process;
//
//    @Before
//    public void setUp() throws IOException {
//        String pop = Paths.get("..", "..", "be1-go", "pop").toString();
//        String pk = "'J9fBzJV70Jk5c-i3277Uq4CmeL4t53WDfUghaK0HpeM='";
//        String[] cmd = {pop, "organizer", "--pk", pk, "serve"};
//        process = Runtime.getRuntime().exec(cmd);
//    }
//
//    @After
//    public void tearDown() {
//        process.destroy();
//    }
    
    // this will run all *.feature files that exist in sub-directories
    // see https://github.com/intuit/karate#naming-conventions   
    @Karate.Test
    Karate testAll() {
        return Karate.run().relativeTo(getClass());
    }
}