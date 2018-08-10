package com.acuitybotting.data.flow.messaging.services.client.implementation.rabbit;

import com.acuitybotting.data.flow.messaging.services.Message;
import com.acuitybotting.data.flow.messaging.services.client.MessageBuilder;
import com.acuitybotting.data.flow.messaging.services.client.MessagingChannel;
import com.acuitybotting.data.flow.messaging.services.client.MessagingQueue;
import com.acuitybotting.data.flow.messaging.services.client.exceptions.MessagingException;
import com.acuitybotting.data.flow.messaging.services.events.MessageEvent;
import com.acuitybotting.data.flow.messaging.services.futures.MessageFuture;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;

import static com.acuitybotting.data.flow.messaging.services.client.MessagingClient.*;


/**
 * Created by Zachary Herridge on 7/10/2018.
 */
public class RabbitChannel implements MessagingChannel {

    private RabbitClient rabbitClient;

    private Channel rabbitChannel;

    private Collection<RabbitQueue> queues = new CopyOnWriteArrayList<>();

    public RabbitChannel(RabbitClient rabbitClient) {
        this.rabbitClient = rabbitClient;
    }

    public void confirmState() {
        synchronized (rabbitClient.CONFIRM_STATE_LOCK){
            if (!rabbitClient.getChannels().contains(this)) return;

            if (rabbitChannel == null || !rabbitChannel.isOpen()) {
                try {
                    Connection connection = rabbitClient.getConnection();
                    if (connection == null) return;
                    rabbitChannel = connection.createChannel();
                    if (rabbitChannel.isOpen()) {
                        rabbitClient.getLog().accept("Channel opened.");
                        rabbitChannel.basicQos(10);
                    } else {
                        rabbitClient.getLog().accept("Failed to open channel connection, waiting 10 seconds and trying again.");
                    }
                } catch (Throwable e) {
                    rabbitClient.getExceptionHandler().accept(e);
                }
            }

            if (rabbitChannel != null && rabbitChannel.isOpen()) {
                for (RabbitQueue queue : queues) {
                    try {
                        queue.confirmState();
                    }
                    catch (Throwable e){
                        rabbitClient.getExceptionHandler().accept(e);
                    }
                }
            }
        }
    }

    @Override
    public MessagingQueue createQueue(String queue, boolean create) {
        return new RabbitQueue(this, queue).setCreateQueue(create);
    }

    @Override
    public MessagingChannel close() throws MessagingException {
        synchronized (rabbitClient.CONFIRM_STATE_LOCK){
            rabbitClient.getChannels().remove(this);
            try {
                rabbitChannel.close();
            }
            catch (Throwable e) {
                rabbitClient.getExceptionHandler().accept(e);
            }
        }
        return this;
    }

    @Override
    public RabbitClient getClient() {
        return rabbitClient;
    }

    @Override
    public void acknowledge(Message message) throws MessagingException {
        try {
            Channel channel = getChannel();
            if (channel == null || !channel.isOpen()) throw new MessagingException("Not connected to RabbitMQ.");
            channel.basicAck(message.getRabbitTag(), false);
            rabbitClient.getLog().accept("Awk: " + message.getRabbitTag());
        } catch (Throwable e) {
            throw new MessagingException("Error during acknowledging message: " + message + ".", e);
        }
    }

    @Override
    public Future<MessageEvent> send(MessageBuilder messageBuilder) throws MessagingException {
        Channel channel = getChannel();
        if (channel == null || !channel.isOpen()) throw new MessagingException("Not connected to RabbitMQ.");

        rabbitClient.getLog().accept("Sending to exchange '" + messageBuilder.getTargetExchange() + "' with routing '" + messageBuilder.getTargetRouting() + "' body: " + messageBuilder.getBody());

        Map<String, String> messageAttributes = new HashMap<>();
        String generatedId = null;
        if (messageBuilder.getFutureId() != null) messageAttributes.put(FUTURE_ID, messageBuilder.getFutureId());

        MessageFuture future = null;
        if (messageBuilder.getLocalQueue() != null) {
            generatedId = generateId();
            future = new MessageFuture();
            future.whenComplete((message, throwable) -> rabbitClient.getMessageFutures().remove(messageBuilder.getFutureId()));
            rabbitClient.getMessageFutures().put(generatedId, future);
            messageAttributes.put(RESPONSE_ID, generatedId);
            messageAttributes.put(RESPONSE_QUEUE, messageBuilder.getLocalQueue());
        }

        for (Map.Entry<String, String> entry : messageBuilder.getMessageAttributes().entrySet()) {
            messageAttributes.put(entry.getKey(), entry.getValue());
        }

        Message message = new Message();
        message.setId(generateId());
        message.setAttributes(messageAttributes);
        message.setBody(messageBuilder.getBody());

        try {
            channel.basicPublish(messageBuilder.getTargetExchange(), messageBuilder.getTargetRouting(), null, rabbitClient.getGson().toJson(message).getBytes());
            return future;
        } catch (Throwable e) {
            if (generatedId != null) rabbitClient.getMessageFutures().remove(generatedId);
            throw new MessagingException("Exception during message publish.", e);
        }
    }

    private String generateId() {
        return UUID.randomUUID().toString();
    }

    public Collection<RabbitQueue> getQueues() {
        return queues;
    }

    @Override
    public MessageFuture getMessageFuture(String id) {
        return rabbitClient.getMessageFutures().get(id);
    }

    public Channel getChannel() {
        return rabbitChannel;
    }
}
