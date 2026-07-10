package com.cloudstore.server.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.rabbitmq.client.MessageProperties;
import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.cloudstore.server.service.exception.ServiceException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeoutException;

/**
    * MessagePipeline handles the connection to RabbitMQ and provides
    * functionality to publish order messages to the exchange.
 **/
public class MessagePipeline implements AutoCloseable {
    
    private static final String EXCHANGE_NAME = "cloudstore.orders.exchange";
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());
    
    private final Connection connection;

    /**
        * Constructor for MessagePipeline.
        * Initializes the RabbitMQ connection.
        * @throws ServiceException If initialization fails due to connection or timeout issues.
    **/
    public MessagePipeline() throws ServiceException {
        ConnectionFactory factory = RabbitMqConnectionFactoryProvider.create();
        
        try {
            this.connection = factory.newConnection("CloudStore:Publisher");
        } catch (IOException | TimeoutException e) {
            throw new ServiceException("Failed to initialize RabbitMQ connection", e);
        }
    }

    /**
        * Publishes an order message to the RabbitMQ exchange.
        * @param routingKey The routing key to use for the message.
        * @param messageId The unique identifier for the message to ensure idempotency.
        * @param payload The message payload to be sent.
        * @throws ServiceException If publishing the message to the exchange fails.
    **/
    public void publishOrderMessage(String routingKey, String messageId, Object payload) throws ServiceException {
        try (Channel channel = connection.createChannel()) {
            channel.exchangeDeclare(EXCHANGE_NAME, BuiltinExchangeType.DIRECT, true);

            channel.confirmSelect();
            
            String message = MAPPER.writeValueAsString(payload);
            
            channel.basicPublish(EXCHANGE_NAME, routingKey, 
                MessageProperties.PERSISTENT_BASIC.builder()
                    .messageId(messageId)
                    .contentType("application/json")
                    .build(), 
                message.getBytes(StandardCharsets.UTF_8));
            
            channel.waitForConfirmsOrDie(5000);
            
        } catch (Exception e) {
            throw new ServiceException("Failed to publish message to Exchange: " + EXCHANGE_NAME, e);
        }
    }

    /**
         * Closes the underlying RabbitMQ connection.
         * @throws Exception If an error occurs during connection closure.
    **/
    @Override
    public void close() throws Exception {
        if (connection != null && connection.isOpen()) connection.close();
    }
}
