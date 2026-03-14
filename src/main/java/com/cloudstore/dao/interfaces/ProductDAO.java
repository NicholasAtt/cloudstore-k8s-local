package com.cloudstore.dao.interfaces;

import com.cloudstore.model.entities.Product;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public interface ProductDAO {
    
    Optional<Product> findById(int id) throws SQLException;

    Optional<Product> findByIdForUpdate(Connection conn, int id) throws SQLException;
    
    List<Product> findByName(String name) throws SQLException;
    
    List<Product> findByCategory(String category) throws SQLException;
    
    List<Product> findAll() throws SQLException;
    
    Product save(Product product) throws SQLException;
    
    Product update(Product product) throws SQLException;
    
    boolean delete(int id) throws SQLException;
    
    boolean exists(int id) throws SQLException;
    
    boolean updateStock(int productId, int newQuantity) throws SQLException;

    boolean updateStock(Connection conn, int productId, int newQuantity) throws SQLException;
    
    List<Product> findLowStockProducts(int threshold) throws SQLException;
    
    int count() throws SQLException;
}