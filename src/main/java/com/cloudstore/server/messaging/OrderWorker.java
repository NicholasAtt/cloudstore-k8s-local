package com.cloudstore.server.messaging;

import com.cloudstore.server.messaging.exception.MessagingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;
import com.cloudstore.server.service.interfaces.CartService;
import com.cloudstore.server.model.dto.TransactionDTO;
import com.cloudstore.server.model.entities.Transaction;
import com.cloudstore.server.service.mapper.DTOMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;

/**
    * OrderWorker is a runnable component that listens for order messages
    * from RabbitMQ and processes them using the CartService.
**/
public class OrderWorker implements Runnable, AutoCloseable {
    
    private static final Logger log = LoggerFactory.getLogger(OrderWorker.class);

    private static final String EXCHANGE_NAME = "cloudstore.orders.exchange";
    private static final String QUEUE_NAME = "order_pipeline";
    
    private static final String DLX_NAME = "cloudstore.orders.dlx";
    private static final String DLQ_NAME = "order_pipeline_dlq";
    
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());
    private static final int MAX_CACHE_SIZE = 10000;
    
    private final ConnectionFactory factory;
    private final Set<String> processedMessageIds;
    
    private final CartService cartService;
    
    private Connection connection;
    private Channel channel;

    /**
        * Single constructor that applies Dependency Injection.
        * @param cartService The CartService instance to use for processing orders.
    **/
    public OrderWorker(CartService cartService) {
        if (cartService == null) {
            throw new IllegalArgumentException("CartService cannot be null");
        }
        this.cartService = cartService;
        
        this.factory = RabbitMqConnectionFactoryProvider.create();
        
        // Enable automatic recovery for potential network drops after the initial startup
        this.factory.setAutomaticRecoveryEnabled(true);
        this.factory.setTopologyRecoveryEnabled(true);
        
        // Define the underlying map with LRU (Least Recently Used) eviction logic
        Map<String, Boolean> map = new LinkedHashMap<>(MAX_CACHE_SIZE, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                // Automatically remove the eldest entry when the map exceeds MAX_CACHE_SIZE
                return size() > MAX_CACHE_SIZE;
            }
        };

        // Wrap the map as a Set to support the .add() operation via Collections.newSetFromMap
        this.processedMessageIds = Collections.synchronizedSet(Collections.newSetFromMap(map));
    }

    /**
        * Starts the worker, connecting to RabbitMQ and consuming messages.
        * Includes a retry loop to overcome potential RabbitMQ startup latency.
    **/
    @Override
    public void run() {
        int retries = 0;
        int maxRetries = 15;

        while (retries < maxRetries) {
            try {
                this.connection = factory.newConnection("CloudStore:OrderWorker");
                this.channel = connection.createChannel();

                channel.exchangeDeclare(EXCHANGE_NAME, BuiltinExchangeType.DIRECT, true);
                channel.exchangeDeclare(DLX_NAME, BuiltinExchangeType.FANOUT, true);
                
                Map<String, Object> queueArgs = new HashMap<>();
                queueArgs.put("x-dead-letter-exchange", DLX_NAME);
                channel.queueDeclare(QUEUE_NAME, true, false, false, queueArgs);
                
                channel.queueDeclare(DLQ_NAME, true, false, false, null);
                channel.queueBind(DLQ_NAME, DLX_NAME, "");
                
                channel.queueBind(QUEUE_NAME, EXCHANGE_NAME, "order.single");
                channel.queueBind(QUEUE_NAME, EXCHANGE_NAME, "order.cart");

                channel.basicQos(1);
                
                log.info("OrderWorker successfully connected and bound. Waiting for messages...");

                DeliverCallback deliverCallback = (consumerTag, delivery) -> {
                    String messageBody = new String(delivery.getBody(), StandardCharsets.UTF_8);
                    long deliveryTag = delivery.getEnvelope().getDeliveryTag();
                    String routingKey = delivery.getEnvelope().getRoutingKey();
                    String messageId = delivery.getProperties().getMessageId();
                    
                    try {
                        if (messageId != null) {
                            if (!processedMessageIds.add(messageId)) {
                                log.debug("Duplicate message ignored: {}", messageId);
                                channel.basicAck(deliveryTag, false);
                                return;
                            }
                        } else {
                            log.warn("Received message without messageId. Idempotency not guaranteed.");
                        }

                        handleMessage(routingKey, messageBody);
                        channel.basicAck(deliveryTag, false);
                    } catch (Exception e) {
                        log.error("Worker failed to process message. NACKing to DLQ...", e);
                        channel.basicNack(deliveryTag, false, false);
                    }
                };
                
                channel.basicConsume(QUEUE_NAME, false, deliverCallback, consumerTag -> { });
                return;

            } catch (Exception e) {
                retries++;
                
                log.warn("OrderWorker connection failed on startup (Attempt {}/{}). Reason: {}. Retrying in 5s...", 
                         retries, maxRetries, e.getClass().getSimpleName());
                
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new MessagingException("Worker thread interrupted during connection retry", ie);
                }
            }
        }
        
        throw new MessagingException("OrderWorker failed to start after " + maxRetries + " attempts. Shutting down.");
    }

    /**
        * Handles an incoming message based on its routing key.
        * @param routingKey The routing key of the message.
        * @param messageBody The body of the message as a JSON string.
        * @throws Exception If processing the message fails.
    **/
    private void handleMessage(String routingKey, String messageBody) throws Exception {
        Map<String, Object> payload = MAPPER.readValue(messageBody, new TypeReference<>() {});

        switch (routingKey) {
            case "order.single":
                TransactionDTO dto = MAPPER.convertValue(payload.get("dto"), TransactionDTO.class);
                Transaction entity = DTOMapper.toEntity(dto);
                cartService.processSingleOrder(entity);
                break;
                
            case "order.cart":
                String customerName = (String) payload.get("customerName");
                String paymentMethod = (String) payload.get("paymentMethod");
                String city = (String) payload.get("city");
                Map<Integer, Integer> items = MAPPER.convertValue(payload.get("items"), new TypeReference<>() {});
                
                cartService.processCartOrder(customerName, paymentMethod, city, items);
                break;
                
            default:
                throw new IllegalArgumentException("Unmapped routing key received: " + routingKey);
        }
    }

    /**
        * Closes the OrderWorker channels and connections.
    **/
    @Override
    public void close() {
        try {
            if (channel != null && channel.isOpen()) channel.close();
            if (connection != null && connection.isOpen()) connection.close();
        } catch (Exception e) {
            log.error("Error closing OrderWorker RabbitMQ resources", e);
        }
    }
}
