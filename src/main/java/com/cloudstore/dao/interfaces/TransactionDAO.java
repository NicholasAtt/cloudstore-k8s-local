package com.cloudstore.dao.interfaces;

import com.cloudstore.model.entities.Transaction;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TransactionDAO {
    
    Optional<Transaction> findById(long id) throws SQLException;
    
    List<Transaction> findByCustomer(String customerName) throws SQLException;
    
    List<Transaction> findByProduct(int productId) throws SQLException;
    
    List<Transaction> findByDateRange(LocalDateTime start, LocalDateTime end) throws SQLException;
    
    List<Transaction> findByPaymentMethod(String paymentMethod) throws SQLException;
    
    List<Transaction> findByCity(String city) throws SQLException;

    List<Transaction> findAll() throws SQLException;
    
    Transaction save(Transaction transaction) throws SQLException;

    Transaction save(Connection conn, Transaction transaction) throws SQLException;
    
    boolean delete(long id) throws SQLException;
    
    boolean exists(long id) throws SQLException;
    
    double calculateTotalSales(LocalDateTime start, LocalDateTime end) throws SQLException;
    
    int countByDateRange(LocalDateTime start, LocalDateTime end) throws SQLException;
    
    List<Transaction> findRecentTransactions(int limit) throws SQLException;
    
    int count() throws SQLException;
}