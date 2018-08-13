package com.acuitybotting.data.flow.messaging.services.client;

import java.util.function.Consumer;

/**
 * Created by Zachary Herridge on 7/2/2018.
 */
public interface MessagingClient {

    String FUTURE_ID = "futureId";
    String RESPONSE_ID = "responseId";
    String RESPONSE_QUEUE = "responseQueue";

    void auth(String endpoint, String port, String username, String password);

    void connect(String connectionId);

    Consumer<Throwable> getExceptionHandler();

    boolean isConnected();

    MessagingChannel openChannel();
}
