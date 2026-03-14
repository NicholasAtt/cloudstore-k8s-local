package com.cloudstore.dao.impl;

import com.cloudstore.dao.interfaces.TransactionDAO;
import com.cloudstore.model.entities.Transaction;
import com.cloudstore.model.entities.Product;
import com.cloudstore.utils.DatabaseConnection;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class TransactionDAOImpl implements TransactionDAO {
    
    private final DatabaseConnection dbConnection;
    
    public TransactionDAOImpl() throws SQLException {
        this.dbConnection = DatabaseConnection.getInstance();
    }
    
    @Override
    public Optional<Transaction> findById(long id) throws SQLException {
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT t.*, p.* " +
                 "FROM transactions t " +
                 "JOIN products p ON t.Product_Id = p.Product_Id " +
                 "WHERE t.Transaction_ID = ?"
             )) {
            
            stmt.setLong(1, id);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToTransaction(rs));
                }
            }
        }
        return Optional.empty();
    }
    
    @Override
    public List<Transaction> findByCustomer(String customerName) throws SQLException {
        List<Transaction> transactions = new ArrayList<>();
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT t.*, p.* " +
                 "FROM transactions t " +
                 "JOIN products p ON t.Product_Id = p.Product_Id " +
                 "WHERE t.Customer_Name = ? " +
                 "ORDER BY t.Date DESC"
             )) {
            
            stmt.setString(1, customerName);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    transactions.add(mapResultSetToTransaction(rs));
                }
            }
        }
        return transactions;
    }
    
    @Override
    public List<Transaction> findByProduct(int productId) throws SQLException {
        List<Transaction> transactions = new ArrayList<>();
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT t.*, p.* " +
                 "FROM transactions t " +
                 "JOIN products p ON t.Product_Id = p.Product_Id " +
                 "WHERE t.Product_Id = ? " +
                 "ORDER BY t.Date DESC"
             )) {
            
            stmt.setInt(1, productId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    transactions.add(mapResultSetToTransaction(rs));
                }
            }
        }
        return transactions;
    }
    
    @Override
    public List<Transaction> findByDateRange(LocalDateTime start, LocalDateTime end) throws SQLException {
        List<Transaction> transactions = new ArrayList<>();
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT t.*, p.* " +
                 "FROM transactions t " +
                 "JOIN products p ON t.Product_Id = p.Product_Id " +
                 "WHERE t.Date BETWEEN ? AND ? " +
                 "ORDER BY t.Date"
             )) {
            
            stmt.setTimestamp(1, Timestamp.valueOf(start));
            stmt.setTimestamp(2, Timestamp.valueOf(end));
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    transactions.add(mapResultSetToTransaction(rs));
                }
            }
        }
        return transactions;
    }
    
    @Override
    public List<Transaction> findByPaymentMethod(String paymentMethod) throws SQLException {
        List<Transaction> transactions = new ArrayList<>();
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT t.*, p.* " +
                 "FROM transactions t " +
                 "JOIN products p ON t.Product_Id = p.Product_Id " +
                 "WHERE t.Payment_Method = ? " +
                 "ORDER BY t.Date DESC"
             )) {
            
            stmt.setString(1, paymentMethod);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    transactions.add(mapResultSetToTransaction(rs));
                }
            }
        }
        return transactions;
    }
    
    @Override
    public List<Transaction> findByCity(String city) throws SQLException {
        List<Transaction> transactions = new ArrayList<>();
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT t.*, p.* " +
                 "FROM transactions t " +
                 "JOIN products p ON t.Product_Id = p.Product_Id " +
                 "WHERE t.City = ? " +
                 "ORDER BY t.Date DESC"
             )) {
            
            stmt.setString(1, city);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    transactions.add(mapResultSetToTransaction(rs));
                }
            }
        }
        return transactions;
    }
    
    @Override
    public List<Transaction> findAll() throws SQLException {
        List<Transaction> transactions = new ArrayList<>();
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT t.*, p.* " +
                 "FROM transactions t " +
                 "JOIN products p ON t.Product_Id = p.Product_Id " +
                 "ORDER BY t.Date DESC"
             );
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                transactions.add(mapResultSetToTransaction(rs));
            }
        }
        return transactions;
    }
    
    @Override
    public Transaction save(Transaction transaction) throws SQLException {
        try (Connection conn = dbConnection.getConnection()) {
            return save(conn, transaction);
        }
    }

    @Override
    public Transaction save(Connection conn, Transaction transaction) throws SQLException {
        final int maxRetries = 5;
        int attempts = 0;

        while (true) {
            attempts++;
            long id = transaction.id();
            if (id == 0) {
                id = getNextId(conn);
            }

            try (PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO transactions (" +
                "Transaction_ID, Date, Customer_Name, Product, Total_Items, Total_Cost, " +
                "Payment_Method, City, Discount_Applied, Customer_Category, Product_Id, Discount" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
            )) {
                stmt.setLong(1, id);
                stmt.setTimestamp(2, Timestamp.valueOf(transaction.date()));
                stmt.setString(3, transaction.CustomerName());
                stmt.setString(4, transaction.Product());
                stmt.setInt(5, transaction.TotalItems());
                stmt.setDouble(6, transaction.TotalCost());
                stmt.setString(7, transaction.PaymentMethod());
                stmt.setString(8, transaction.City());
                stmt.setInt(9, transaction.DiscountApplied());
                stmt.setString(10, transaction.CustomerCategory());
                stmt.setInt(11, transaction.ProductID().id());
                stmt.setFloat(12, transaction.Discount());

                stmt.executeUpdate();
                return new Transaction(id, transaction.date(), transaction.CustomerName(),
                        transaction.Product(), transaction.TotalItems(), transaction.TotalCost(),
                        transaction.PaymentMethod(), transaction.City(), transaction.DiscountApplied(),
                        transaction.CustomerCategory(), transaction.Discount(), transaction.ProductID());
            } catch (SQLIntegrityConstraintViolationException e) {
                if (transaction.id() != 0 || attempts >= maxRetries) {
                    throw e;
                }
            }
        }
    }

    private long getNextId(Connection conn) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
            "SELECT COALESCE(MAX(Transaction_ID), 0) + 1 FROM transactions"
        );
             ResultSet rs = stmt.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 1L;
        }
    }
    
    @Override
    public boolean delete(long id) throws SQLException {
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "DELETE FROM transactions WHERE Transaction_ID = ?"
             )) {
            
            stmt.setLong(1, id);
            return stmt.executeUpdate() > 0;
        }
    }
    
    @Override
    public boolean exists(long id) throws SQLException {
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT COUNT(*) FROM transactions WHERE Transaction_ID = ?"
             )) {
            
            stmt.setLong(1, id);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }
        return false;
    }
    
    @Override
    public double calculateTotalSales(LocalDateTime start, LocalDateTime end) throws SQLException {
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT COALESCE(SUM(Total_Cost), 0) FROM transactions WHERE Date BETWEEN ? AND ?"
             )) {
            
            stmt.setTimestamp(1, Timestamp.valueOf(start));
            stmt.setTimestamp(2, Timestamp.valueOf(end));
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble(1);
                }
            }
        }
        return 0.0;
    }
    
    @Override
    public int countByDateRange(LocalDateTime start, LocalDateTime end) throws SQLException {
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT COUNT(*) FROM transactions WHERE Date BETWEEN ? AND ?"
             )) {
            
            stmt.setTimestamp(1, Timestamp.valueOf(start));
            stmt.setTimestamp(2, Timestamp.valueOf(end));
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }
    
    @Override
    public List<Transaction> findRecentTransactions(int limit) throws SQLException {
        List<Transaction> transactions = new ArrayList<>();
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT t.*, p.* " +
                 "FROM transactions t " +
                 "JOIN products p ON t.Product_Id = p.Product_Id " +
                 "ORDER BY t.Date DESC LIMIT ?"
             )) {
            
            stmt.setInt(1, limit);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    transactions.add(mapResultSetToTransaction(rs));
                }
            }
        }
        return transactions;
    }
    
    @Override
    public int count() throws SQLException {
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) FROM transactions");
             ResultSet rs = stmt.executeQuery()) {
            
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }
    
    private Transaction mapResultSetToTransaction(ResultSet rs) throws SQLException {
        Product product = new Product(
            rs.getInt("Product_Id"),
            rs.getString("Product_Name"),
            rs.getString("Category"),
            rs.getDouble("Price"),
            rs.getInt("Stock_Quantity")
        );
        
        return new Transaction(
            rs.getLong("Transaction_ID"),
            rs.getTimestamp("Date").toLocalDateTime(),
            rs.getString("Customer_Name"),
            rs.getString("Product"),
            rs.getInt("Total_Items"),
            rs.getDouble("Total_Cost"),
            rs.getString("Payment_Method"),
            rs.getString("City"),
            rs.getInt("Discount_Applied"),
            rs.getString("Customer_Category"),
            rs.getFloat("Discount"),
            product
        );
    }
}