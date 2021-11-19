package examples.messages;

import com.intuit.karate.junit5.Karate;

public class MessagesRunner {

    @Karate.Test
    Karate testMessages() {
        return Karate.run("classpath:examples/messages/messages.feature");
    }
}