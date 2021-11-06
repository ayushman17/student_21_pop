package examples.messages;

import com.intuit.karate.junit5.Karate;

public class MessagesRunner {

    @Karate.Test
    Karate testUsers() {
        return Karate.run("messages").relativeTo(getClass());
    }
}