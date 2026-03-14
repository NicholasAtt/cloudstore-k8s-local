package com.cloudstore.dao.interfaces;

import com.cloudstore.model.entities.Permission;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public interface PermissionDAO {
    
    Optional<Permission> findById(int id) throws SQLException;

    Optional<Permission> findByCategory(String category) throws SQLException;
    
    List<Permission> findAll() throws SQLException;
    
    Permission save(Permission permission) throws SQLException;
    
    Permission update(Permission permission) throws SQLException;
    
    boolean delete(int id) throws SQLException;

    boolean exists(int id) throws SQLException;
    
    boolean isInUse(int id) throws SQLException;
    
    int count() throws SQLException;
}