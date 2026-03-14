package com.cloudstore.dao.impl;

import com.cloudstore.dao.interfaces.PermissionDAO;
import com.cloudstore.model.entities.Permission;
import com.cloudstore.utils.DatabaseConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class PermissionDAOImpl implements PermissionDAO {
    
    private final DatabaseConnection dbConnection;
    
    public PermissionDAOImpl() throws SQLException {
        this.dbConnection = DatabaseConnection.getInstance();
    }
    
    @Override
    public Optional<Permission> findById(int id) throws SQLException {
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT * FROM permissions WHERE ID = ?")) {
            
            stmt.setInt(1, id);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToPermission(rs));
                }
            }
        }
        return Optional.empty();
    }
    
    @Override
    public Optional<Permission> findByCategory(String category) throws SQLException {
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT * FROM permissions WHERE Category = ?")) {
            
            stmt.setString(1, category);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToPermission(rs));
                }
            }
        }
        return Optional.empty();
    }
    
    @Override
    public List<Permission> findAll() throws SQLException {
        List<Permission> permissions = new ArrayList<>();
        
        try (Connection conn = dbConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM permissions ORDER BY ID")) {
            
            while (rs.next()) {
                permissions.add(mapResultSetToPermission(rs));
            }
        }
        return permissions;
    }
    
    @Override
    public Permission save(Permission permission) throws SQLException {
        if (permission.id() != 0 && exists(permission.id())) {
            return update(permission);
        } else {
            return insert(permission);
        }
    }
    
    private Permission insert(Permission permission) throws SQLException {
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement("INSERT INTO permissions (Category) VALUES (?)", Statement.RETURN_GENERATED_KEYS)) {
            
            stmt.setString(1, permission.category());
            
            int affectedRows = stmt.executeUpdate();
            
            if (affectedRows == 0) {
                throw new SQLException("Creation permission failed, no rows affected.");
            }
            
            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return new Permission(
                        generatedKeys.getInt(1),
                        permission.category()
                    );
                } else {
                    throw new SQLException("Creation permission failed, no generated key obtained.");
                }
            }
        }
    }
    
    @Override
    public Permission update(Permission permission) throws SQLException {
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement("UPDATE permissions SET Category = ? WHERE ID = ?")) {
            
            stmt.setString(1, permission.category());
            stmt.setInt(2, permission.id());
            
            int affectedRows = stmt.executeUpdate();
            
            if (affectedRows == 0) {
                throw new SQLException("Update permission failed, no rows updated.");
            }
            
            return permission;
        }
    }
    
    @Override
    public boolean delete(int id) throws SQLException {
        if (isInUse(id)) {
            throw new SQLException("Impossible to delete: the permission is assigned to users");
        }
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement("DELETE FROM permissions WHERE ID = ?")) {
            
            stmt.setInt(1, id);
            
            int affectedRows = stmt.executeUpdate();
            return affectedRows > 0;
        }
    }
    
    @Override
    public boolean exists(int id) throws SQLException {
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) FROM permissions WHERE ID = ?")) {
            
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
    public boolean isInUse(int id) throws SQLException {
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) FROM users WHERE Permission_ID = ?")) {
            
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
    public int count() throws SQLException {
        try (Connection conn = dbConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM permissions")) {
            
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }
    
    public void initializeDefaultPermissions() throws SQLException {
        String[] defaultPermissions = {"ADMIN", "MANAGER", "USER", "GUEST"};
        
        for (String category : defaultPermissions) {
            Optional<Permission> existing = findByCategory(category);
            if (existing.isEmpty()) {
                save(new Permission(0, category));
            }
        }
    }
    
    private Permission mapResultSetToPermission(ResultSet rs) throws SQLException {
        return new Permission(
            rs.getInt("ID"),
            rs.getString("Category")
        );
    }
}