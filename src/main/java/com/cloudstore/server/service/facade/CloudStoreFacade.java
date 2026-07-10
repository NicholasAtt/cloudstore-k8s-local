package com.cloudstore.server.service.facade;

import com.cloudstore.server.model.dto.*;
import com.cloudstore.server.model.domain.OrderSubmissionResult;
import com.cloudstore.server.model.dto.auth.AuthenticationResult;
import com.cloudstore.server.model.dto.auth.LoginResult;
import com.cloudstore.server.service.exception.ServiceException;
import com.cloudstore.server.service.interfaces.*;
import com.cloudstore.server.service.mapper.DTOMapper;
import com.cloudstore.server.service.auth.rbac.AccessControl;
import com.cloudstore.server.service.impl.*;
import com.cloudstore.server.model.auth.Role;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;


public class CloudStoreFacade {

    private final PermissionService permissionService;      // Service for managing permissions
    private final ProductService productService;            // Service for managing products
    private final UserService userService;                  // Service for managing users
    private final TransactionService transactionService;    // Service for managing transactions
    private final AuthService authService;                  // Service for handling authentication and authorization
    private final CartService cartService;                  // Service for managing shopping cart operations
    private final DashboardService dashboardService;        // Service for providing dashboard statistics and user profiles
    private final AccessControl accessControl;              // Service for role-based access control
    private final OrderProcessingService orderProcessingService; // Application service for async order dispatch

    // Default constructor that initializes all services with their default implementations
    public CloudStoreFacade() throws ServiceException {
        try {
            this.permissionService = new PermissionServiceImpl();
            this.productService = new ProductServiceImpl();
            this.userService = new UserServiceImpl();
            this.transactionService = new TransactionServiceImpl();
            this.authService = new AuthServiceImpl();
            this.cartService = new CartServiceImpl();
            this.dashboardService = new DashboardServiceImpl();
            this.accessControl = new AccessControl();
            this.orderProcessingService = new OrderProcessingServiceImpl();
        } catch (Exception e) {
            throw new ServiceException("Unable to initialize CloudStoreFacade", e);
        }
    }

    /**
         * Constructor for CloudStoreFacade that allows for dependency injection.
         * This is primarily used for unit testing or when a custom configuration of services is required.
         * @param permissionService The service for managing permissions.
         * @param productService The service for managing products.
         * @param userService The service for managing users.
         * @param transactionService The service for managing transactions.
         * @param authService The service for handling authentication.
         * @param cartService The service for managing shopping cart operations.
         * @param dashboardService The service for dashboard statistics.
         * @param accessControl The service for role-based access control.
         * @param orderProcessingService The application service for async order dispatch.
    **/
    public CloudStoreFacade(PermissionService permissionService, ProductService productService,
                            UserService userService, TransactionService transactionService,
                            AuthService authService, CartService cartService,
                            DashboardService dashboardService, AccessControl accessControl,
                            OrderProcessingService orderProcessingService) {
        this.permissionService = permissionService;
        this.productService = productService;
        this.userService = userService;
        this.transactionService = transactionService;
        this.authService = authService;
        this.cartService = cartService;
        this.dashboardService = dashboardService;
        this.accessControl = accessControl;
        this.orderProcessingService = orderProcessingService;
    }

    // Convenience constructor that initializes OrderProcessingService with defaults
    public CloudStoreFacade(PermissionService permissionService, ProductService productService,
                            UserService userService, TransactionService transactionService,
                            AuthService authService, CartService cartService,
                            DashboardService dashboardService, AccessControl accessControl) {
        this(permissionService, productService, userService, transactionService,
                authService, cartService, dashboardService, accessControl,
                new OrderProcessingServiceImpl());
    }

    // RBAC

    @FunctionalInterface
    public interface RoleBasedInterface<T> {
        T get() throws ServiceException;
    }

    private <T> T adminMethod(RoleBasedInterface<T> action) throws ServiceException {
        this.accessControl.requireRole(Role.ADMIN);
        return action.get();
    }

    private <T> T sellerMethod(RoleBasedInterface<T> action) throws ServiceException {
        this.accessControl.requireRole(Role.SELLER);
        return action.get();
    }

    private <T> T customerMethod(String nickname, RoleBasedInterface<T> action) throws ServiceException {
        AuthenticationResult caller = this.accessControl.requireRole(Role.CUSTOMER);
        requireSelfOrAdmin(caller, nickname);
        return action.get();
    }

    private void requireSelfOrAdmin(AuthenticationResult caller, String nickname) throws ServiceException {
        if (caller.getNickname().equals(nickname))
            return;
        if (accessControl.resolveRole(caller.getRoles()).hasAccessTo(Role.ADMIN))
            return;
        throw new ServiceException("Access denied: you can only access your own resources");
    }

    // ADMIN

    public Optional<PermissionDTO> findPermissionById(int id) throws ServiceException {
        return adminMethod(() -> permissionService.findById(id).map(DTOMapper::toDTO));
    }

    public Optional<PermissionDTO> findPermissionByCategory(String category) throws ServiceException {
        return adminMethod(() -> permissionService.findByCategory(category).map(DTOMapper::toDTO));
    }

    public List<PermissionDTO> getAllPermissions() throws ServiceException {
        return adminMethod(() -> permissionService.findAll().stream().map(DTOMapper::toDTO).toList());
    }

    public PermissionDTO savePermission(PermissionDTO dto) throws ServiceException {
        return adminMethod(() -> DTOMapper.toDTO(permissionService.save(DTOMapper.toEntity(dto))));
    }

    public boolean deletePermission(int id) throws ServiceException {
        return adminMethod(() -> permissionService.delete(id));
    }

    public int countPermissions() throws ServiceException {
        return adminMethod(() -> permissionService.count());
    }

    public ProductDTO saveProduct(ProductDTO dto) throws ServiceException {
        return adminMethod(() -> DTOMapper.toDTO(productService.save(DTOMapper.toEntity(dto))));
    }

    public boolean deleteProduct(int id) throws ServiceException {
        return adminMethod(() -> productService.delete(id));
    }

    public boolean updateProductStock(int productId, int newQuantity) throws ServiceException {
        return adminMethod(() -> productService.updateStock(productId, newQuantity));
    }

    public List<ProductDTO> findLowStockProducts(int threshold) throws ServiceException {
        return adminMethod(() -> productService.findLowStockProducts(threshold).stream().map(DTOMapper::toDTO).toList());
    }

    public int countProducts() throws ServiceException {
        return adminMethod(() -> productService.count());
    }

    public List<UserDTO> findUsersByPermission(int permissionId) throws ServiceException {
        return adminMethod(() -> userService.findByPermission(permissionId).stream().map(DTOMapper::toDTO).toList());
    }

    public List<UserDTO> getAllUsers() throws ServiceException {
        return adminMethod(() -> userService.findAll().stream().map(DTOMapper::toDTO).toList());
    }

    public boolean deleteUser(String nickname) throws ServiceException {
        return adminMethod(() -> userService.delete(nickname));
    }

    public boolean updateUserPassword(String nickname, String newPassword) throws ServiceException {
        return adminMethod(() -> userService.updatePassword(nickname, newPassword));
    }

    public boolean updateUserPermission(String nickname, int newPermissionId) throws ServiceException {
        return adminMethod(() -> userService.updatePermission(nickname, newPermissionId));
    }

    public int countUsers() throws ServiceException {
        return adminMethod(() -> userService.count());
    }

    public Optional<TransactionDTO> findTransactionById(long id) throws ServiceException {
        return adminMethod(() -> transactionService.findById(id).map(DTOMapper::toDTO));
    }

    public List<TransactionDTO> findTransactionsByProduct(int productId) throws ServiceException {
        return adminMethod(() -> transactionService.findByProduct(productId).stream().map(DTOMapper::toDTO).toList());
    }

    public List<TransactionDTO> findRecentTransactionsByProduct(int productId, int limit) throws ServiceException {
        return adminMethod(() -> transactionService.findRecentByProduct(productId, limit).stream().map(DTOMapper::toDTO).toList());
    }

    public List<TransactionDTO> findTransactionsByDateRange(LocalDateTime start, LocalDateTime end) throws ServiceException {
        return adminMethod(() -> transactionService.findByDateRange(start, end).stream().map(DTOMapper::toDTO).toList());
    }

    public List<TransactionDTO> findTransactionsByPaymentMethod(String paymentMethod) throws ServiceException {
        return adminMethod(() -> transactionService.findByPaymentMethod(paymentMethod).stream().map(DTOMapper::toDTO).toList());
    }

    public List<TransactionDTO> findTransactionsByCity(String city) throws ServiceException {
        return adminMethod(() -> transactionService.findByCity(city).stream().map(DTOMapper::toDTO).toList());
    }

    public List<TransactionDTO> getAllTransactions() throws ServiceException {
        return adminMethod(() -> transactionService.findAll().stream().map(DTOMapper::toDTO).toList());
    }

    public TransactionDTO saveTransaction(TransactionDTO dto) throws ServiceException {
        return adminMethod(() -> DTOMapper.toDTO(transactionService.save(DTOMapper.toEntity(dto))));
    }

    public boolean deleteTransaction(long id) throws ServiceException {
        return adminMethod(() -> transactionService.delete(id));
    }

    public double calculateTotalSales(LocalDateTime start, LocalDateTime end) throws ServiceException {
        return adminMethod(() -> transactionService.calculateTotalSales(start, end));
    }

    public int countTransactionsByDateRange(LocalDateTime start, LocalDateTime end) throws ServiceException {
        return adminMethod(() -> transactionService.countByDateRange(start, end));
    }

    public List<TransactionDTO> findRecentTransactions(int limit) throws ServiceException {
        return adminMethod(() -> transactionService.findRecentTransactions(limit).stream().map(DTOMapper::toDTO).toList());
    }

    public int countTransactions() throws ServiceException {
        return adminMethod(() -> transactionService.count());
    }

    public DashboardStatsDTO getDashboardStats() throws ServiceException {
        return adminMethod(() -> DTOMapper.toDTO(dashboardService.getDashboardStats()));
    }

    // PRODUCT

    public Optional<ProductDTO> findProductById(int id) throws ServiceException {
        return productService.findById(id).map(DTOMapper::toDTO);
    }

    public List<ProductDTO> findProductsByName(String name) throws ServiceException {
        return productService.findByName(name).stream().map(DTOMapper::toDTO).toList();
    }

    public List<ProductDTO> findProductsByCategory(String category) throws ServiceException {
        return productService.findByCategory(category).stream().map(DTOMapper::toDTO).toList();
    }

    public List<ProductDTO> getAllProducts() throws ServiceException {
        return productService.findAll().stream().map(DTOMapper::toDTO).toList();
    }

    public List<String> getAllProductCategories() throws ServiceException {
        return productService.findAllCategories();
    }

    public boolean productExists(int id) throws ServiceException {
        return productService.exists(id);
    }

    // USER
    
    public Optional<UserDTO> findUserByNickname(String nickname) throws ServiceException {
        return userService.findByNickname(nickname).map(DTOMapper::toDTO);
    }

    public Optional<UserDTO> findUserByEmail(String email) throws ServiceException {
        return userService.findByEmail(email).map(DTOMapper::toDTO);
    }

    public UserDTO registerUser(UserDTO dto) throws ServiceException {
        return DTOMapper.toDTO(userService.register(DTOMapper.toEntity(dto)));
    }

    public boolean userExists(String nickname) throws ServiceException {
        return userService.exists(nickname);
    }

    public String resolveCustomerCategory(String customerName) throws ServiceException {
        return userService.resolveCustomerCategory(customerName);
    }
    
    // CUSTOMER

    public List<TransactionDTO> findTransactionsByCustomer(String customerName) throws ServiceException {
        return customerMethod(customerName, () -> transactionService.findByCustomer(customerName).stream().map(DTOMapper::toDTO).toList());
    }

    public CheckoutContextDTO getCheckoutContext(String customerName, Map<Integer, Integer> items) throws ServiceException {
        return customerMethod(customerName, () -> DTOMapper.toDTO(cartService.getCheckoutContext(customerName, items)));
    }


    public OrderSubmissionResult processOrder(String customerName, TransactionDTO dto) throws ServiceException {
        return customerMethod(customerName, () -> orderProcessingService.submitSingleOrder(dto));
    }

    public OrderSubmissionResult processCartOrder(String customerName, String paymentMethod,
                                                  String city, Map<Integer, Integer> items) throws ServiceException {
        return customerMethod(customerName,
                () -> orderProcessingService.submitCartOrder(customerName, paymentMethod, city, items));
    }

    public UserProfileDTO getUserProfile(String nickname) throws ServiceException {
        return customerMethod(nickname, () -> DTOMapper.toDTO(dashboardService.getUserProfile(nickname)));
    }

    // UTILITIES
    
    public int getDefaultPermissionId() throws ServiceException {
        return adminMethod(() -> permissionService.findAll().stream()
                .findFirst()
                .orElseThrow(() -> new ServiceException("No permission found"))
                .id());
    }

    public boolean permissionExists(int id) throws ServiceException {
        return permissionService.exists(id);
    }

    public boolean transactionExists(long id) throws ServiceException {
        return transactionService.exists(id);
    }

    // SELLER

    public SellerDashboardStatsDTO getSellerDashboardStats() throws ServiceException {
        return sellerMethod(() -> DTOMapper.toDTO(dashboardService.getSellerDashboardStats()));
    }

    public List<ProductDTO> getSellerProducts() throws ServiceException {
        return sellerMethod(() -> dashboardService.getSellerProducts().stream().map(DTOMapper::toDTO).toList());
    }

    public List<SalesOrderSummaryDTO> getSellerSalesOrders(int limit) throws ServiceException {
        return sellerMethod(() -> dashboardService.getSellerSalesOrders(limit).stream().map(DTOMapper::toDTO).toList());
    }

    public List<TopCustomerSummaryDTO> getSellerTopCustomers(int limit) throws ServiceException {
        return sellerMethod(() -> dashboardService.getSellerTopCustomers(limit).stream().map(DTOMapper::toDTO).toList());
    }

    public boolean updateSellerProductStock(int productId, int newQuantity) throws ServiceException {
        return sellerMethod(() -> productService.updateStock(productId, newQuantity));
    }

    // AUTH

    public LoginResult authenticateUser(String nickname, String password) throws ServiceException {
        return authService.authenticateUser(nickname, password);
    }

    public AuthenticationResult getSessionFromToken(String token) throws ServiceException {
        return authService.getSessionFromToken(token);
    }

    public void logout(String token) throws ServiceException {
        authService.logout(token);
    }
}
