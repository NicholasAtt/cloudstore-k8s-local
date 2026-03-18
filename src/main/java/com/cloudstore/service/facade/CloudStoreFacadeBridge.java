package com.cloudstore.service.facade;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.cloudstore.model.dto.PermissionDTO;
import com.cloudstore.model.dto.ProductDTO;
import com.cloudstore.model.dto.TransactionDTO;
import com.cloudstore.model.dto.UserDTO;
import com.cloudstore.service.exception.ServiceException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;


public class CloudStoreFacadeBridge {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Map<String, CommandHandler> COMMAND_HANDLERS = buildCommandHandlers();

    @FunctionalInterface
    private interface CommandHandler {
        JsonNode handle(CloudStoreFacade facade, String payload) throws Exception;
    }

    static {
        MAPPER.registerModule(new JavaTimeModule());
    }

    private static Map<String, CommandHandler> buildCommandHandlers() {
        Map<String, CommandHandler> handlers = new HashMap<>();

        handlers.put("dashboard_stats", (facade, payload) -> {
            JsonNode req = parseAuthenticatedRequest(payload);
            ensureAdmin(facade, req);
            return MAPPER.valueToTree(facade.getDashboardStats());
        });
        handlers.put("list_permissions", (facade, payload) -> {
            JsonNode req = parseAuthenticatedRequest(payload);
            ensureAdmin(facade, req);
            return MAPPER.valueToTree(facade.getAllPermissions());
        });
        handlers.put("save_permission", (facade, payload) -> {
            JsonNode req = parseAuthenticatedRequest(payload);
            ensureAdmin(facade, req);
            PermissionDTO dto = MAPPER.treeToValue(req.path("data"), PermissionDTO.class);
            return MAPPER.valueToTree(facade.savePermission(dto));
        });

        handlers.put("list_products", (facade, payload) -> MAPPER.valueToTree(facade.getAllProducts()));
        handlers.put("list_product_categories", (facade, payload) -> MAPPER.valueToTree(facade.getProductCategories()));
        handlers.put("list_products_by_category", (facade, payload) -> MAPPER.valueToTree(facade.findProductsByCategory(payload)));
        handlers.put("save_product", (facade, payload) -> {
            JsonNode req = parseAuthenticatedRequest(payload);
            ensureAdmin(facade, req);
            ProductDTO dto = MAPPER.treeToValue(req.path("data"), ProductDTO.class);
            return MAPPER.valueToTree(facade.saveProduct(dto));
        });
        handlers.put("delete_product", (facade, payload) -> {
            JsonNode req = parseAuthenticatedRequest(payload);
            ensureAdmin(facade, req);
            int productId = req.path("data").path("productId").asInt();
            return MAPPER.valueToTree(facade.deleteProduct(productId));
        });
        handlers.put("low_stock", (facade, payload) -> {
            JsonNode req = parseAuthenticatedRequest(payload);
            ensureAdmin(facade, req);
            int threshold = req.path("data").path("threshold").asInt();
            return MAPPER.valueToTree(facade.findLowStockProducts(threshold));
        });

        handlers.put("list_users", (facade, payload) -> {
            JsonNode req = parseAuthenticatedRequest(payload);
            ensureAdmin(facade, req);
            return MAPPER.valueToTree(facade.getAllUsers());
        });
        handlers.put("register_user", (facade, payload) -> {
            JsonNode req = parseAuthenticatedRequest(payload);
            ensureAdmin(facade, req);
            UserDTO dto = MAPPER.treeToValue(req.path("data"), UserDTO.class);
            return MAPPER.valueToTree(facade.registerUser(dto));
        });
        handlers.put("authenticate_user", (facade, payload) -> {
            JsonNode req = MAPPER.readTree(payload);
            String nickname = req.path("nickname").asText();
            String password = req.path("password").asText();
            return MAPPER.valueToTree(facade.authenticateUser(nickname, password));
        });
        handlers.put("get_customer_checkout_context", (facade, payload) -> {
            JsonNode req = MAPPER.readTree(payload);
            String customerName = req.path("customerName").asText();
            Map<Integer, Integer> items = new HashMap<>();
            JsonNode rawItems = req.path("items");
            if (rawItems.isArray()) {
                for (JsonNode item : rawItems) {
                    int productId = item.path("productId").asInt();
                    int quantity = item.path("quantity").asInt();
                    if (productId > 0 && quantity > 0) {
                        items.merge(productId, quantity, Integer::sum);
                    }
                }
            }
            return MAPPER.valueToTree(facade.getCustomerCheckoutContext(customerName, items));
        });

        handlers.put("list_transactions", (facade, payload) -> {
            JsonNode req = parseAuthenticatedRequest(payload);
            ensureAdmin(facade, req);
            int limit = req.path("data").path("limit").asInt();
            return MAPPER.valueToTree(facade.findRecentTransactions(limit));
        });
        handlers.put("user_profile", (facade, payload) -> {
            JsonNode req = parseAuthenticatedRequest(payload);
            ensureAdmin(facade, req);
            String nickname = req.path("data").path("nickname").asText();
            return MAPPER.valueToTree(facade.getUserProfile(nickname));
        });
        handlers.put("process_order", (facade, payload) -> {
            JsonNode req = parseAuthenticatedRequest(payload);
            ensureAdmin(facade, req);
            TransactionDTO dto = MAPPER.treeToValue(req.path("data"), TransactionDTO.class);
            return MAPPER.valueToTree(facade.processOrder(dto));
        });
        handlers.put("process_cart", (facade, payload) -> {
            JsonNode req = MAPPER.readTree(payload);
            String customerName = req.path("customerName").asText();
            String paymentMethod = req.path("paymentMethod").asText();
            String city = req.path("city").asText();

            Map<Integer, Integer> items = new HashMap<>();
            JsonNode rawItems = req.path("items");
            if (rawItems.isArray()) {
                for (JsonNode item : rawItems) {
                    int productId = item.path("productId").asInt();
                    int quantity = item.path("quantity").asInt();
                    if (productId > 0 && quantity > 0) {
                        items.merge(productId, quantity, Integer::sum);
                    }
                }
            }

            return MAPPER.valueToTree(
                    facade.processCartOrder(customerName, paymentMethod, city, items)
            );
        });

        handlers.put("total_sales", (facade, payload) -> {
            JsonNode req = parseAuthenticatedRequest(payload);
            ensureAdmin(facade, req);
            JsonNode data = req.path("data");
            LocalDateTime start = LocalDateTime.parse(data.get("start").asText());
            LocalDateTime end = LocalDateTime.parse(data.get("end").asText());
            return MAPPER.valueToTree(facade.calculateTotalSales(start, end));
        });

        return handlers;
    }

    public static void main(String[] args) {
        try {
            if (args.length < 1) {
                throw new IllegalArgumentException("Missing command");
            }

            String command = args[0];
            String payload = readStdin().trim();

            CloudStoreFacade facade = new CloudStoreFacade();
            JsonNode data = executeCommand(facade, command, payload);

            MAPPER.writeValue(System.out, MAPPER.createObjectNode()
                    .put("ok", true)
                    .set("data", data));
        } catch (Exception e) {
            try {
                MAPPER.writeValue(System.out, MAPPER.createObjectNode()
                        .put("ok", false)
                        .put("error", e.getMessage()));
            } catch (IOException ignored) {
                System.out.print("{\"ok\":false,\"error\":\"Serialization error\"}");
            }
            System.exit(1);
        }
    }

    private static JsonNode executeCommand(CloudStoreFacade facade, String command, String payload) throws Exception {
        CommandHandler handler = COMMAND_HANDLERS.get(command);
        if (handler == null) {
            throw new IllegalArgumentException("Unsupported command: " + command);
        }
        return handler.handle(facade, payload);
    }

    private static String readStdin() throws IOException {
        return new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
    }

    private static JsonNode parseAuthenticatedRequest(String payload) throws IOException {
        JsonNode req = MAPPER.readTree(payload);
        if (!req.has("auth")) {
            throw new IllegalArgumentException("Missing auth section");
        }
        return req;
    }

    private static void ensureAdmin(CloudStoreFacade facade, JsonNode req) throws ServiceException {
        JsonNode auth = req.path("auth");
        String nickname = auth.path("nickname").asText();
        String password = auth.path("password").asText();
        facade.assertAdminAccess(nickname, password);
    }
}
