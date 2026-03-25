package service.facade;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.core.type.TypeReference;
import service.exception.ServiceException;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.net.InetSocketAddress;
import java.io.*;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.Executors;
import service.security.SecurityContext;
import model.dto.auth.AuthenticationResult;

public class JavaFacadeWrapper {
    
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final CloudStoreFacade FACADE;
    private static final int PORT = 9999;
    
    static {
        MAPPER.registerModule(new JavaTimeModule());
        try {
            System.err.println("Initializing CloudStoreFacade...");
            FACADE = new CloudStoreFacade();
            System.err.println("CloudStoreFacade initialized successfully");
        } catch (ServiceException e) {
            System.err.println("Failed to initialize facade: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to initialize facade", e);
        }
    }
    
    public static void main(String[] args) throws Exception {
        System.err.println("HTTP Server starting on port " + PORT);
        
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/", new FacadeHandler());
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        
        System.err.println("HTTP Server started on http://localhost:" + PORT);
        System.err.println("Waiting for requests...");
    }
    
    static class FacadeHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Gestisci richieste GET per health check
            if ("GET".equals(exchange.getRequestMethod())) {
                String response = "{\"status\": \"ok\"}";
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.getBytes().length);
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
                return;
            }
            
            // Solo POST per le chiamate RPC
            if (!"POST".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            
            try {
                // Leggi il body della richiesta
                InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), "utf-8");
                BufferedReader br = new BufferedReader(isr);
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
                String requestBody = sb.toString();
                System.err.println("Received request: " + requestBody);
                
                Map<String, Object> request = MAPPER.readValue(requestBody, new TypeReference<Map<String, Object>>() {});
                String methodName = (String) request.get("method");
                List<?> argsList = (List<?>) request.getOrDefault("args", new ArrayList<>());
                Object[] argsArray = argsList.toArray();
                
                String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    String token = authHeader.substring(7);
                    try {
                        AuthenticationResult session = FACADE.getSessionFromToken(token);
                        SecurityContext.set(session);
                    } catch (Exception e) {
                        System.err.println("Invalid token: " + e.getMessage());
                        sendErrorResponse(exchange, "Unauthorized: " + e.getMessage(), 401);
                        return;
                    }
                }
                
                System.err.println("Invoking: " + methodName + " with args: " + Arrays.toString(argsArray));
                Object result = invokeMethod(methodName, argsArray);
                System.err.println("Result: " + result);
                
                Map<String, Object> response = new HashMap<>();
                response.put("ok", true);
                response.put("data", result);
                
                String responseJson = MAPPER.writeValueAsString(response);
                System.err.println("Sending response: " + responseJson);
                
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, responseJson.getBytes().length);
                OutputStream os = exchange.getResponseBody();
                os.write(responseJson.getBytes());
                os.close();
                
            } catch (Exception e) {
                System.err.println("Error processing request: " + e.getMessage());
                e.printStackTrace();
                
                sendErrorResponse(exchange, e.getMessage(), 500);
            } finally {
                SecurityContext.clear();
            }
        }
        
        private void sendErrorResponse(HttpExchange exchange, String message, int statusCode) throws IOException {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("ok", false);
            errorResponse.put("error", message);
            String errorJson = MAPPER.writeValueAsString(errorResponse);
            
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(statusCode, errorJson.getBytes().length);
            OutputStream os = exchange.getResponseBody();
            os.write(errorJson.getBytes());
            os.close();
        }
        
        private static Object invokeMethod(String methodName, Object[] args) throws Exception {
            Method[] methods = CloudStoreFacade.class.getMethods();
            Method targetMethod = null;
            
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