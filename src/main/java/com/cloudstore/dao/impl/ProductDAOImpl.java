package com.cloudstore.dao.impl;

import com.cloudstore.dao.interfaces.ProductDAO;
import com.cloudstore.model.entities.Product;
import com.cloudstore.utils.DatabaseConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ProductDAOImpl implements ProductDAO {
    
    private final DatabaseConnection dbConnection;
    
    public ProductDAOImpl() throws SQLException {
        this.dbConnection = DatabaseConnection.getInstance();
    }
    
    @Override
    public Optional<Product> findById(int id) throws SQLException {
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement( "SELECT * FROM products WHERE Product_Id = ?")) {
            
            stmt.setInt(1, id);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToProduct(rs));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<Product> findByIdForUpdate(Connection conn, int id) throws SQLException {
        String selectForUpdate = "SELECT * FROM products WHERE Product_Id = ?" + " FOR UPDATE";
        try (PreparedStatement stmt = conn.prepareStatement(selectForUpdate)) {
            stmt.setInt(1, id);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToProduct(rs));
                }
            }
        }
        return Optional.empty();
    }
    
    @Override
    public List<Product> findByName(String name) throws SQLException {
        List<Product> products = new ArrayList<>();
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT * FROM products WHERE Product_Name LIKE ?")) {
            
            stmt.setString(1, "%" + name + "%");
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    products.add(mapResultSetToProduct(rs));
                }
            }
        }
        return products;
    }
    
    @Override
    public List<Product> findByCategory(String category) throws SQLException {
        List<Product> products = new ArrayList<>();
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT * FROM products WHERE Category = ?")) {
            
            stmt.setString(1, category);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    products.add(mapResultSetToProduct(rs));
                }
            }
        }
        return products;
    }
    
    @Override
    public List<Product> findAll() throws SQLException {
        List<Product> products = new ArrayList<>();
        
        try (Connection conn = dbConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM products ORDER BY Product_Id")) {
            
            while (rs.next()) {
                products.add(mapResultSetToProduct(rs));
            }
        }
        return products;
    }
    
    @Override
    public Product save(Product product) throws SQLException {
        if (exists(product.id())) {
            return update(product);
        } else {
            return insert(product);
        }
    }
    
    private Product insert(Product product) throws SQLException {
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement("INSERT INTO products (Product_Name, Category, Price, Stock_Quantity) VALUES (?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            
            stmt.setString(1, product.name());
            stmt.setString(2, product.category());
            stmt.setDouble(3, product.price());
            stmt.setInt(4, product.stock());
            
            int affectedRows = stmt.executeUpdate();
            
            if (affectedRows == 0) {
                throw new SQLException("Creation product failed, no rows inserted.");
            }
            
            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return new Product(
                        generatedKeys.getInt(1),
                        product.name(),
                        product.category(),
                        product.price(),
                        product.stock()
                    );
                } else {
                    throw new SQLException("Creation product failed, no generated key obtained.");
                }
            }
        }
    }
    
    @Override
    public Product update(Product product) throws SQLException {
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement("UPDATE products SET Product_Name = ?, Category = ?, Price = ?, Stock_Quantity = ? WHERE Product_Id = ?")) {
            
            stmt.setString(1, product.name());
            stmt.setString(2, product.category());
            stmt.setDouble(3, product.price());
            stmt.setInt(4, product.stock());
            stmt.setInt(5, product.id());
            
            int affectedRows = stmt.executeUpdate();
            
            if (affectedRows == 0) {
                throw new SQLException("Update product failed, no rows updated.");
            }
            
            return product;
        }
    }
    
    @Override
    public boolean delete(int id) throws SQLException {
        String checkTransactions = "SELECT COUNT(*) FROM transactions WHERE Product_Id = ?";
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement checkStmt = conn.prepareStatement(checkTransactions)) {
            
            checkStmt.setInt(1, id);
            
            try (ResultSet rs = checkStmt.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    throw new SQLException("Impossible to delete: there are transactions linked to this product");
                }
            }
        }
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement("DELETE FROM products WHERE Product_Id = ?")) {
            
            stmt.setInt(1, id);
            
            int affectedRows = stmt.executeUpdate();
            return affectedRows > 0;
        }
    }
    
    @Override
    public boolean exists(int id) throws SQLException {
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) FROM products WHERE Product_Id = ?")) {
            
            stmt.setInt(1, id);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }
        return false;
    }
    
    @Override
    public boolean updateStock(int productId, int newQuantity) throws SQLException {
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement("UPDATE products SET Stock_Quantity = ? WHERE Product_Id = ?")) {
            
            stmt.setInt(1, newQuantity);
            stmt.setInt(2, productId);
            
            int affectedRows = stmt.executeUpdate();
            return affectedRows > 0;
        }
    }

    @Override
    public boolean updateStock(Connection conn, int productId, int newQuantity) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement("UPDATE products SET Stock_Quantity = ? WHERE Product_Id = ?")) {
            stmt.setInt(1, newQuantity);
            stmt.setInt(2, productId);

            int affectedRows = stmt.executeUpdate();
            return affectedRows > 0;
        }
    }
    
    @Override
    public List<Product> findLowStockProducts(int threshold) throws SQLException {
        List<Product> products = new ArrayList<>();
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT * FROM products WHERE Stock_Quantity <= ?")) {
            
            stmt.setInt(1, threshold);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    products.add(mapResultSetToProduct(rs));
                }
            }
        }
        return products;
    }
    
    @Override
    public int count() throws SQLException {
        try (Connection conn = dbConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM products")) {
            
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }
    
    private Product mapResultSetToProduct(ResultSet rs) throws SQLException {
        return new Product(
            rs.getInt("Product_Id"),
            rs.getString("Product_Name"),
            rs.getString("Category"),
            rs.getDouble("Price"),
            rs.getInt("Stock_Quantity")
        );
    }
}