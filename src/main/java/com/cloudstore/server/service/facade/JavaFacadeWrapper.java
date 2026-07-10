package com.cloudstore.server.service.facade;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.core.type.TypeReference;
import com.cloudstore.server.service.exception.ServiceException;
import com.cloudstore.server.service.security.SecurityContext;
import com.cloudstore.server.model.dto.auth.AuthenticationResult;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import com.cloudstore.server.service.exception.ValidationException;
import com.cloudstore.server.service.exception.UnauthorizedException;
import com.cloudstore.server.service.exception.ForbiddenException;
import com.cloudstore.server.service.exception.ResourceNotFoundException;
import java.lang.reflect.InvocationTargetException;

import java.net.InetSocketAddress;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;


public class JavaFacadeWrapper {
    
    // Mapper for JSON serialization/deserialization
    private static final ObjectMapper MAPPER = new ObjectMapper();
    
    // Facade instance to handle business logic
    private static final CloudStoreFacade FACADE;

    // Static initializer to set up the facade and JSON mapper
    static {
        MAPPER.registerModule(new JavaTimeModule());
        try {
            FACADE = new CloudStoreFacade();
        } catch (ServiceException e) {
            throw new RuntimeException("Failed to initialize facade", e);
        }
    }
    
    /**
        * Main method to start the HTTP server and listen for incoming requests.
        * The server will handle requests to invoke methods on the CloudStoreFacade.
        * It registers graceful shutdown hooks for server resources.
        * @param args Command line arguments.
        * @throws Exception If a critical error occurs during startup.
    **/
    public static void main(String[] args) throws Exception {

        HttpServer server = startHttpServer();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutdown signal received. Closing resources gracefully...");
            if (server != null) {
                // Stop the HTTP server, waiting up to 2 seconds for active requests to finish
                server.stop(2);
            }
            System.out.println("Shutdown complete.");
        }, "Shutdown-Hook-Thread"));
        
    }

    /**
        * Creates and starts the Sun HttpServer.
        * @return The started HttpServer instance.
        * @throws IOException If an I/O error occurs during server creation.
    **/
    private static HttpServer startHttpServer() throws IOException {
        String bindAddress = System.getenv().getOrDefault("SERVER_BIND_ADDRESS", "0.0.0.0");
        int port = getIntEnv("SERVER_PORT", 9999);
        HttpServer server = HttpServer.create(new InetSocketAddress(bindAddress, port), 0);
        server.createContext("/", new FacadeHandler());
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        return server;
    }

    private static int getIntEnv(String key, int defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Invalid integer environment variable: " + key, e);
        }
    }
    
    static class FacadeHandler implements HttpHandler {
        
        /**
            * Handles incoming HTTP requests.
            * @param exchange The HttpExchange object containing request and response information.
            * @throws IOException If an I/O error occurs while handling the request.
        **/
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                if ("GET".equals(exchange.getRequestMethod())) {
                    sendJsonResponse(exchange, 200, Map.of("status", "ok"));
                    return;
                }
                
                if (!"POST".equals(exchange.getRequestMethod())) {
                    exchange.sendResponseHeaders(405, -1);
                    return;
                }

                // Parse request body
                Map<String, Object> request = MAPPER.readValue(exchange.getRequestBody(), new TypeReference<>() {});
                String methodName = (String) request.get("method");
                List<?> argsList = (List<?>) request.getOrDefault("args", List.of());
                
                // Authenticate if token provided
                String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    String token = authHeader.substring(7);
                    AuthenticationResult session = FACADE.getSessionFromToken(token);
                    SecurityContext.set(session);
                }
                
                // Invoke method and send response
                Object result = invokeMethod(methodName, argsList.toArray());
                sendJsonResponse(exchange, 200, Map.of("ok", true, "data", result != null ? result : ""));
                
            } catch (Throwable t) {
                handleException(exchange, t);
            } finally {
                SecurityContext.clear();
            }
        }

        /** 
            * Sends a JSON response with the specified status code and body.
            * @param exchange The HttpExchange object to send the response through.
            * @param statusCode The HTTP status code to set in the response.
            * @param body The body of the response, which will be serialized to JSON.
            * @throws IOException If an I/O error occurs while sending the response.
        **/
        private void sendJsonResponse(HttpExchange exchange, int statusCode, Object body) throws IOException {
            byte[] responseBytes = MAPPER.writeValueAsBytes(body);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(statusCode, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        }

        /** 
            * Handles exceptions that occur during request processing and sends an appropriate JSON error response.
            * @param exchange The HttpExchange object to send the response through.
            * @param t The Throwable that was caught during request processing.
            * @throws IOException If an I/O error occurs while sending the response.
        **/
        private void handleException(HttpExchange exchange, Throwable t) throws IOException {
            Throwable cause = (t instanceof InvocationTargetException) ? t.getCause() : t;
            int statusCode = resolveStatusCode(cause);
            
            Map<String, Object> responseBody = new HashMap<>();
            responseBody.put("ok", false);
            
            if (statusCode >= 500) {
                String trackingId = UUID.randomUUID().toString().substring(0, 8);
                System.err.printf("[ERROR][%s] %s: %s%n", trackingId, cause.getClass().getSimpleName(), cause.getMessage());
                cause.printStackTrace();
                
                responseBody.put("error", "Internal Server Error");
                responseBody.put("ref", trackingId);
            } else {
                String message = cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
                responseBody.put("error", message);
            }
            
            sendJsonResponse(exchange, statusCode, responseBody);
        }
        
        
        /**
            * Resolves the HTTP status code based on the given Throwable.
            * @param t The exception.
            * @return The HTTP status code.
         */
        private int resolveStatusCode(Throwable t) {
            if (t instanceof ValidationException || t instanceof IllegalArgumentException) {
                return 400;
            } else if (t instanceof UnauthorizedException || t instanceof SecurityException) {
                return 401;
            } else if (t instanceof ForbiddenException) {
                return 403;
            } else if (t instanceof ResourceNotFoundException) {
                return 404;
            } else if (t instanceof UnsupportedOperationException) {
                return 501;
            }
            return 500;
        }
        
        /** 
            * Invokes the specified method on the CloudStoreFacade with the given arguments.
            * @param methodName The name of the method to invoke.
            * @param args The arguments to pass to the method.
            * @return The result of the method invocation.
            * @throws Exception If an error occurs while invoking the method.
        **/
        private static Object invokeMethod(String methodName, Object[] args) throws Exception {
            Method[] methods = CloudStoreFacade.class.getMethods();
            Method targetMethod = null;
            
            // First, try to find a method that matches args.length
            for (Method m : methods) {
                if (m.getName().equals(methodName) && m.getParameterCount() == args.length) {
                    targetMethod = m;
                    break;
                }
            }
            
            if (targetMethod == null) {
                throw new IllegalArgumentException("Method not found: " + methodName + " with " + args.length + " parameters");
            }
            
            Class<?>[] paramTypes = targetMethod.getParameterTypes();
            Object[] convertedArgs = new Object[paramTypes.length];
            
            for (int i = 0; i < paramTypes.length; i++) {
                if (i < args.length && args[i] != null) {
                    convertedArgs[i] = MAPPER.convertValue(args[i], paramTypes[i]);
                }
            }
            
            return targetMethod.invoke(FACADE, convertedArgs);
        }
    }
}
