package com.cloudstore.service.facade;

import com.cloudstore.dao.impl.PermissionDAOImpl;
import com.cloudstore.dao.impl.ProductDAOImpl;
import com.cloudstore.dao.impl.TransactionDAOImpl;
import com.cloudstore.dao.impl.UserDAOImpl;
import com.cloudstore.dao.interfaces.ProductDAO;
import com.cloudstore.dao.interfaces.TransactionDAO;
import com.cloudstore.model.entities.Product;
import com.cloudstore.model.entities.Transaction;
import com.cloudstore.model.dto.*;
import com.cloudstore.service.exception.ServiceException;
import com.cloudstore.service.impl.*;
import com.cloudstore.service.interfaces.*;
import com.cloudstore.service.mapper.DTOMapper;
import com.cloudstore.utils.DatabaseConnection;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;


public class CloudStoreFacade {

    private final PermissionService permissionService;
    private final ProductService productService;
    private final UserService userService;
    private final TransactionService transactionService;
    private final ProductDAO productDAO;
    private final TransactionDAO transactionDAO;
    private final DatabaseConnection dbConnection;

    public CloudStoreFacade() throws ServiceException {
        try {
            PermissionDAOImpl permissionDAO = new PermissionDAOImpl();
            ProductDAOImpl productDAO = new ProductDAOImpl();
            UserDAOImpl userDAO = new UserDAOImpl();
            TransactionDAOImpl transactionDAO = new TransactionDAOImpl();

            this.permissionService = new PermissionServiceImpl(permissionDAO);
            this.productService = new ProductServiceImpl(productDAO);
            this.userService = new UserServiceImpl(userDAO, permissionDAO);
            this.transactionService = new TransactionServiceImpl(transactionDAO);
            this.productDAO = productDAO;
            this.transactionDAO = transactionDAO;
            this.dbConnection = DatabaseConnection.getInstance();
        } catch (SQLException e) {
            throw new ServiceException("Impossible to initialize CloudStoreFacade", e);
        }
    }

    public CloudStoreFacade(
            PermissionService permissionService,
            ProductService productService,
            UserService userService,
            TransactionService transactionService,
            ProductDAO productDAO,
            TransactionDAO transactionDAO,
            DatabaseConnection dbConnection) {
        this.permissionService = permissionService;
        this.productService = productService;
        this.userService = userService;
        this.transactionService = transactionService;
        this.productDAO = productDAO;
        this.transactionDAO = transactionDAO;
        this.dbConnection = dbConnection;
    }

    public Optional<PermissionDTO> findPermissionById(int id) throws ServiceException {
        return permissionService.findById(id);
    }

    public Optional<PermissionDTO> findPermissionByCategory(String category) throws ServiceException {
        return permissionService.findByCategory(category);
    }

    public List<PermissionDTO> getAllPermissions() throws ServiceException {
        return permissionService.findAll();
    }

    public PermissionDTO savePermission(PermissionDTO dto) throws ServiceException {
        return permissionService.save(dto);
    }

    public boolean deletePermission(int id) throws ServiceException {
        return permissionService.delete(id);
    }

    public Optional<ProductDTO> findProductById(int id) throws ServiceException {
        return productService.findById(id);
    }

    public List<ProductDTO> findProductsByName(String name) throws ServiceException {
        return productService.findByName(name);
    }

    public List<ProductDTO> findProductsByCategory(String category) throws ServiceException {
        return productService.findByCategory(category);
    }

    public List<ProductDTO> getAllProducts() throws ServiceException {
        return productService.findAll();
    }

    public List<String> getProductCategories() throws ServiceException {
        return productService.findAll().stream()
                .map(ProductDTO::getDescription)
                .filter(category -> category != null && !category.isBlank())
                .map(String::trim)
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());
    }

    public ProductDTO saveProduct(ProductDTO dto) throws ServiceException {
        return productService.save(dto);
    }

    public boolean deleteProduct(int id) throws ServiceException {
        return productService.delete(id);
    }

    public boolean updateProductStock(int productId, int newQuantity) throws ServiceException {
        return productService.updateStock(productId, newQuantity);
    }

    public List<ProductDTO> findLowStockProducts(int threshold) throws ServiceException {
        return productService.findLowStockProducts(threshold);
    }

    public Optional<UserDTO> findUserByNickname(String nickname) throws ServiceException {
        return userService.findByNickname(nickname);
    }

    public Optional<UserDTO> findUserByEmail(String email) throws ServiceException {
        return userService.findByEmail(email);
    }

    public List<UserDTO> findUsersByPermission(int permissionId) throws ServiceException {
        return userService.findByPermission(permissionId);
    }

    public List<UserDTO> getAllUsers() throws ServiceException {
        return userService.findAll();
    }

    public Map<String, Object> authenticateUser(String nickname, String password) throws ServiceException {
        UserDTO user = authenticateCredentials(nickname, password);

        String permissionCategory = user.getPermission() != null ? user.getPermission().getCategory() : "";
        boolean isAdmin = permissionCategory != null
                && permissionCategory.toLowerCase(Locale.ROOT).contains("admin");

        Map<String, Object> safeUser = new HashMap<>();
        safeUser.put("nickname", user.getNickname());
        safeUser.put("name", user.getName());
        safeUser.put("surname", user.getSurname());
        safeUser.put("email", user.getEmail());
        safeUser.put("permission", user.getPermission());

        Map<String, Object> result = new HashMap<>();
        result.put("user", safeUser);
        result.put("role", isAdmin ? "admin" : "customer");
        result.put("isAdmin", isAdmin);
        return result;
    }

    public void assertAdminAccess(String nickname, String password) throws ServiceException {
        UserDTO user = authenticateCredentials(nickname, password);
        String permissionCategory = user.getPermission() != null ? user.getPermission().getCategory() : "";
        boolean isAdmin = permissionCategory != null
                && permissionCategory.toLowerCase(Locale.ROOT).contains("admin");
        if (!isAdmin) {
            throw new ServiceException("Admin access required");
        }
    }

    private UserDTO authenticateCredentials(String nickname, String password) throws ServiceException {
        if (nickname == null || nickname.isBlank()) {
            throw new ServiceException("Nickname cannot be empty");
        }
        if (password == null || password.isBlank()) {
            throw new ServiceException("Password cannot be empty");
        }

        UserDTO user = userService.findByNickname(nickname)
                .orElseThrow(() -> new ServiceException("Invalid credentials"));

        if (!password.equals(user.getPassword())) {
            throw new ServiceException("Invalid credentials");
        }
        return user;
    }

    public Map<String, Object> getCustomerCheckoutContext(String customerName, Map<Integer, Integer> items) throws ServiceException {
        if (customerName == null || customerName.isBlank()) {
            throw new ServiceException("Customer name cannot be empty");
        }

        String customerCategory = resolveCustomerCategory(customerName);
        int sampleWindow = 5;
        Map<String, Object> discountContext = resolveDiscountForCartItems(items, sampleWindow);
        float discount = (float) discountContext.get("discount");
        int sampleSize = (int) discountContext.get("sampleSize");
        String discountSource = (String) discountContext.get("discountSource");

        Map<String, Object> context = new HashMap<>();
        context.put("customerName", customerName);
        context.put("customerCategory", customerCategory);
        context.put("discount", discount);
        context.put("discountApplied", discount > 0 ? 1 : 0);
        context.put("discountSource", discountSource);
        context.put("sampleSize", sampleSize);
        context.put("sampleWindow", sampleWindow);
        return context;
    }

    public UserDTO registerUser(UserDTO dto) throws ServiceException {
        return userService.register(dto);
    }

    public boolean deleteUser(String nickname) throws ServiceException {
        return userService.delete(nickname);
    }

    public boolean updateUserPassword(String nickname, String newPassword) throws ServiceException {
        return userService.updatePassword(nickname, newPassword);
    }

    public boolean updateUserPermission(String nickname, int newPermissionId) throws ServiceException {
        return userService.updatePermission(nickname, newPermissionId);
    }

    public Optional<TransactionDTO> findTransactionById(long id) throws ServiceException {
        return transactionService.findById(id);
    }

    public List<TransactionDTO> findTransactionsByCustomer(String customerName) throws ServiceException {
        return transactionService.findByCustomer(customerName);
    }

    public List<TransactionDTO> findTransactionsByProduct(int productId) throws ServiceException {
        return transactionService.findByProduct(productId);
    }

    public List<TransactionDTO> findTransactionsByDateRange(LocalDateTime start, LocalDateTime end) throws ServiceException {
        return transactionService.findByDateRange(start, end);
    }

    public List<TransactionDTO> findTransactionsByPaymentMethod(String paymentMethod) throws ServiceException {
        return transactionService.findByPaymentMethod(paymentMethod);
    }

    public List<TransactionDTO> findTransactionsByCity(String city) throws ServiceException {
        return transactionService.findByCity(city);
    }

    public List<TransactionDTO> getAllTransactions() throws ServiceException {
        return transactionService.findAll();
    }

    public TransactionDTO saveTransaction(TransactionDTO dto) throws ServiceException {
        return transactionService.save(dto);
    }

    public boolean deleteTransaction(long id) throws ServiceException {
        return transactionService.delete(id);
    }

    public double calculateTotalSales(LocalDateTime start, LocalDateTime end) throws ServiceException {
        return transactionService.calculateTotalSales(start, end);
    }

    public int countTransactionsByDateRange(LocalDateTime start, LocalDateTime end) throws ServiceException {
        return transactionService.countByDateRange(start, end);
    }

    public List<TransactionDTO> findRecentTransactions(int limit) throws ServiceException {
        return transactionService.findRecentTransactions(limit);
    }

    public TransactionDTO processOrder(TransactionDTO dto) throws ServiceException {

        if (dto.getDate() == null) {
            dto.setDate(LocalDateTime.now());
        }

        if (dto.getProductDetails() == null) {
            throw new ServiceException("Transaction must specify a product");
        }
        if (dto.getTotalItems() <= 0) {
            throw new ServiceException("Number of items must be greater than zero");
        }
        int productId = dto.getProductDetails().getId();

        float normalizedDiscount = Math.max(0.0f, Math.min(dto.getDiscount(), 1.0f));
        dto.setDiscount(normalizedDiscount);
        dto.setDiscountApplied(normalizedDiscount > 0 ? 1 : 0);

        try (Connection conn = dbConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Product product = productDAO.findByIdForUpdate(conn, productId)
                    .orElseThrow(() -> new ServiceException("Product not found with ID: " + productId));

                if (product.stock() < dto.getTotalItems()) {
                    throw new ServiceException(String.format(
                        "Insufficient stock for '%s': available %d, requested %d",
                            product.name(), product.stock(), dto.getTotalItems()));
                }

                double grossTotal = product.price() * dto.getTotalItems();
                double netTotal = grossTotal * (1 - normalizedDiscount);
                dto.setProduct(product.name());
                dto.setTotalCost(netTotal);

                Transaction saved = transactionDAO.save(conn, DTOMapper.toEntity(dto));
                boolean stockUpdated = productDAO.updateStock(conn, productId, product.stock() - dto.getTotalItems());
                if (!stockUpdated) {
                    throw new ServiceException("Stock update failed for product ID: " + productId);
                }

                conn.commit();
                return DTOMapper.toDTO(saved);
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new ServiceException("Error during atomic order processing", e);
        }
    }

        public Map<String, Object> processCartOrder(
            String customerName,
            String paymentMethod,
            String city,
            Map<Integer, Integer> items) throws ServiceException {

        if (customerName == null || customerName.isBlank()) {
            throw new ServiceException("Customer name cannot be empty");
        }
        if (items == null || items.isEmpty()) {
            throw new ServiceException("Cart is empty");
        }

        String customerCategory = resolveCustomerCategory(customerName);
        Map<String, Object> discountContext = resolveDiscountForCartItems(items, 5);
        float normalizedDiscount = (float) discountContext.get("discount");
        LocalDateTime now = LocalDateTime.now();

        List<Map.Entry<Integer, Integer>> sortedItems = new ArrayList<>(items.entrySet());
        sortedItems.sort(Comparator.comparingInt(Map.Entry::getKey));

        try (Connection conn = dbConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                List<TransactionDTO> createdTransactions = new ArrayList<>();
                double cartTotal = 0.0;
                int totalItems = 0;

                for (Map.Entry<Integer, Integer> entry : sortedItems) {
                    int productId = entry.getKey();
                    int quantity = entry.getValue();

                    if (quantity <= 0) {
                        throw new ServiceException("Quantity must be greater than zero for product ID: " + productId);
                    }

                    Product product = productDAO.findByIdForUpdate(conn, productId)
                            .orElseThrow(() -> new ServiceException("Product not found with ID: " + productId));

                    if (product.stock() < quantity) {
                        throw new ServiceException(String.format(
                                "Insufficient stock for '%s': available %d, requested %d",
                                product.name(), product.stock(), quantity));
                    }

                    double lineTotal = product.price() * quantity * (1 - normalizedDiscount);
                    TransactionDTO dto = new TransactionDTO();
                    dto.setDate(now);
                    dto.setCustomerName(customerName);
                    dto.setProduct(product.name());
                    dto.setTotalItems(quantity);
                    dto.setTotalCost(lineTotal);
                    dto.setPaymentMethod(paymentMethod);
                    dto.setCity(city);
                    dto.setDiscountApplied(normalizedDiscount > 0 ? 1 : 0);
                    dto.setCustomerCategory(customerCategory);
                    dto.setDiscount(normalizedDiscount);
                    dto.setProductDetails(new ProductDTO(product.id(), product.name(), product.category(), product.price(), product.stock()));

                    Transaction saved = transactionDAO.save(conn, DTOMapper.toEntity(dto));
                    boolean stockUpdated = productDAO.updateStock(conn, productId, product.stock() - quantity);
                    if (!stockUpdated) {
                        throw new ServiceException("Stock update failed for product ID: " + productId);
                    }

                    TransactionDTO savedDto = DTOMapper.toDTO(saved);
                    createdTransactions.add(savedDto);
                    cartTotal += savedDto.getTotalCost();
                    totalItems += savedDto.getTotalItems();
                }

                conn.commit();

                Map<String, Object> result = new HashMap<>();
                result.put("transactions", createdTransactions);
                result.put("totalItems", totalItems);
                result.put("cartTotal", cartTotal);
                result.put("lines", createdTransactions.size());
                return result;
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new ServiceException("Error during atomic cart processing", e);
        }
    }

    private String resolveCustomerCategory(String customerName) throws ServiceException {
        Optional<UserDTO> userOpt = userService.findByNickname(customerName);
        if (userOpt.isPresent()) {
            UserDTO user = userOpt.get();
            if (user.getPermission() != null && user.getPermission().getCategory() != null
                    && !user.getPermission().getCategory().isBlank()) {
                return user.getPermission().getCategory();
            }
        }
        return "Customer";
    }

    private Map<String, Object> resolveDiscountForCartItems(Map<Integer, Integer> items, int sampleWindow) throws ServiceException {
        Map<String, Object> result = new HashMap<>();
        if (items == null || items.isEmpty()) {
            result.put("discount", 0.0f);
            result.put("sampleSize", 0);
            result.put("discountSource", "no_cart_items");
            return result;
        }

        int limit = Math.max(1, sampleWindow);
        double weightedDiscountSum = 0.0;
        int weightedQuantity = 0;
        int sampleSize = 0;

        // Compute average discount from recent transactions for each product and weight by cart quantity.
        for (Map.Entry<Integer, Integer> entry : items.entrySet()) {
            int productId = entry.getKey();
            int quantity = Math.max(0, entry.getValue());
            if (quantity <= 0) {
                continue;
            }

            List<TransactionDTO> productTransactions = resolveRecentTransactionsForProductDiscount(productId, limit);
            if (productTransactions.isEmpty()) {
                continue;
            }

            double productAverage = productTransactions.stream()
                    .mapToDouble(tx -> Math.max(0.0f, Math.min(tx.getDiscount(), 1.0f)))
                    .average()
                    .orElse(0.0);

            weightedDiscountSum += productAverage * quantity;
            weightedQuantity += quantity;
            sampleSize += productTransactions.size();
        }

        float discount = weightedQuantity > 0
                ? (float) Math.max(0.0, Math.min(weightedDiscountSum / weightedQuantity, 1.0))
                : 0.0f;

        result.put("discount", discount);
        result.put("sampleSize", sampleSize);
        result.put("discountSource", weightedQuantity > 0 ? "recent_product_average_discount" : "no_product_history");
        return result;
    }

    private List<TransactionDTO> resolveRecentTransactionsForProductDiscount(int productId, int maxItems) throws ServiceException {
        return transactionService.findRecentByProduct(productId, Math.max(1, maxItems));
    }

    public Map<String, Object> getDashboardStats() throws ServiceException {
        LocalDateTime startOfMonth = LocalDateTime.now()
                .withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime now = LocalDateTime.now();

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalProducts",      productService.count());
        stats.put("totalUsers",         userService.count());
        stats.put("totalTransactions",  transactionService.count());
        stats.put("totalPermissions",   permissionService.count());
        stats.put("monthlySales",       transactionService.calculateTotalSales(startOfMonth, now));
        stats.put("monthlyTransactions",transactionService.countByDateRange(startOfMonth, now));
        stats.put("lowStockProducts",   productService.findLowStockProducts(10).size());

        return stats;
    }

    public Map<String, Object> getUserProfile(String nickname) throws ServiceException {
        Optional<UserDTO> userOpt = userService.findByNickname(nickname);
        if (userOpt.isEmpty()) {
            return null;
        }
        List<TransactionDTO> orders = transactionService.findByCustomer(nickname);
        double totalSpent = orders.stream().mapToDouble(TransactionDTO::getTotalCost).sum();

        Map<String, Object> profile = new HashMap<>();
        profile.put("user",         userOpt.get());
        profile.put("orderHistory", orders);
        profile.put("totalOrders",  orders.size());
        profile.put("totalSpent",   totalSpent);

        return profile;
    }

    public int getFirstAvailablePermissionId() throws ServiceException {
        return permissionService.findAll().stream()
                .findFirst()
                .orElseThrow(() -> new ServiceException("No permission found in database"))
                .getId();
    }

    public boolean permissionExists(int id) throws ServiceException  { return permissionService.exists(id); }
    public boolean productExists(int id) throws ServiceException     { return productService.exists(id); }
    public boolean userExists(String nickname) throws ServiceException{ return userService.exists(nickname); }
    public boolean transactionExists(long id) throws ServiceException { return transactionService.exists(id); }

    public int countPermissions() throws ServiceException  { return permissionService.count(); }
    public int countProducts() throws ServiceException     { return productService.count(); }
    public int countUsers() throws ServiceException        { return userService.count(); }
    public int countTransactions() throws ServiceException { return transactionService.count(); }
}

