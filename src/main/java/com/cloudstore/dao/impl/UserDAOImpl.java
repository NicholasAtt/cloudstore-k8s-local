package com.cloudstore.dao.impl;

import com.cloudstore.dao.interfaces.UserDAO;
import com.cloudstore.model.entities.User;
import com.cloudstore.model.entities.Permission;
import com.cloudstore.utils.DatabaseConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class UserDAOImpl implements UserDAO {
    
    private final DatabaseConnection dbConnection;
    
    public UserDAOImpl() throws SQLException {
        this.dbConnection = DatabaseConnection.getInstance();
    }
    
    @Override
    public Optional<User> findByNickname(String nickname) throws SQLException {
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT u.*, p.ID as p_id, p.Category as p_category " +
                 "FROM users u " +
                 "JOIN permissions p ON u.Permission_ID = p.ID " +
                 "WHERE u.Nickname = ?"
             )) {
            
            stmt.setString(1, nickname);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToUser(rs));
                }
            }
        }
        return Optional.empty();
    }
    
    @Override
    public Optional<User> findByEmail(String email) throws SQLException {
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT u.*, p.ID as p_id, p.Category as p_category " +
                 "FROM users u " +
                 "JOIN permissions p ON u.Permission_ID = p.ID " +
                 "WHERE u.Email = ?"
             )) {
            
            stmt.setString(1, email);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToUser(rs));
                }
            }
        }
        return Optional.empty();
    }
    
    @Override
    public List<User> findByPermission(int permissionId) throws SQLException {
        List<User> users = new ArrayList<>();
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT u.*, p.ID as p_id, p.Category as p_category " +
                 "FROM users u " +
                 "JOIN permissions p ON u.Permission_ID = p.ID " +
                 "WHERE u.Permission_ID = ?"
             )) {
            
            stmt.setInt(1, permissionId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    users.add(mapResultSetToUser(rs));
                }
            }
        }
        return users;
    }
    
    @Override
    public List<User> findAll() throws SQLException {
        List<User> users = new ArrayList<>();
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT u.*, p.ID as p_id, p.Category as p_category " +
                 "FROM users u " +
                 "JOIN permissions p ON u.Permission_ID = p.ID " +
                 "ORDER BY u.Nickname"
             );
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                users.add(mapResultSetToUser(rs));
            }
        }
        return users;
    }
    
    @Override
    public User save(User user) throws SQLException {
        if (exists(user.nickname())) {
            return update(user);
        } else {
            return insert(user);
        }
    }
    
    private User insert(User user) throws SQLException {
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "INSERT INTO users (Nickname, Name, Surname, Email, Password, Permission_ID) " +
                 "VALUES (?, ?, ?, ?, ?, ?)"
             )) {
            
            stmt.setString(1, user.nickname());
            stmt.setString(2, user.name());
            stmt.setString(3, user.surname());
            stmt.setString(4, user.email());
            stmt.setString(5, user.password());
            stmt.setInt(6, user.PermissionID().id());
            
            int affectedRows = stmt.executeUpdate();
            
            if (affectedRows == 0) {
                throw new SQLException("Creazione utente fallita, nessuna riga inserita.");
            }
            
            return user;
        }
    }
    
    @Override
    public User update(User user) throws SQLException {
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "UPDATE users SET Name = ?, Surname = ?, Email = ?, Password = ?, Permission_ID = ? " +
                 "WHERE Nickname = ?"
             )) {
            
            stmt.setString(1, user.name());
            stmt.setString(2, user.surname());
            stmt.setString(3, user.email());
            stmt.setString(4, user.password());
            stmt.setInt(5, user.PermissionID().id());
            stmt.setString(6, user.nickname());
            
            int affectedRows = stmt.executeUpdate();
            
            if (affectedRows == 0) {
                throw new SQLException("Aggiornamento utente fallito, nessuna riga aggiornata.");
            }
            
            return user;
        }
    }
    
    @Override
    public boolean delete(String nickname) throws SQLException {
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement checkStmt = conn.prepareStatement(
                 "SELECT COUNT(*) FROM transactions WHERE Customer_Name = ?"
             )) {
            
            checkStmt.setString(1, nickname);
            
            try (ResultSet rs = checkStmt.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    throw new SQLException("Impossibile eliminare: ci sono transazioni collegate a questo utente");
                }
            }
        }
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "DELETE FROM users WHERE Nickname = ?"
             )) {
            
            stmt.setString(1, nickname);
            
            int affectedRows = stmt.executeUpdate();
            return affectedRows > 0;
        }
    }
    
    @Override
    public boolean exists(String nickname) throws SQLException {
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT COUNT(*) FROM users WHERE Nickname = ?"
             )) {
            
            stmt.setString(1, nickname);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }
        return false;
    }
    
    @Override
    public boolean emailExists(String email) throws SQLException {
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT COUNT(*) FROM users WHERE Email = ?"
             )) {
            
            stmt.setString(1, email);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }
        return false;
    }
    
    @Override
    public boolean updatePassword(String nickname, String newPassword) throws SQLException {
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "UPDATE users SET Password = ? WHERE Nickname = ?"
             )) {
            
            stmt.setString(1, newPassword);
            stmt.setString(2, nickname);
            
            int affectedRows = stmt.executeUpdate();
            return affectedRows > 0;
        }
    }
    
    @Override
    public boolean updatePermission(String nickname, int newPermissionId) throws SQLException {
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "UPDATE users SET Permission_ID = ? WHERE Nickname = ?"
             )) {
            
            stmt.setInt(1, newPermissionId);
            stmt.setString(2, nickname);
            
            int affectedRows = stmt.executeUpdate();
            return affectedRows > 0;
        }
    }
    
    @Override
    public int count() throws SQLException {
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) FROM users");
             ResultSet rs = stmt.executeQuery()) {
            
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }
    
    @Override
    public int countByPermission(int permissionId) throws SQLException {
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT COUNT(*) FROM users WHERE Permission_ID = ?"
             )) {
            
            stmt.setInt(1, permissionId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }
    
    private User mapResultSetToUser(ResultSet rs) throws SQLException {
        Permission permission = new Permission(
            rs.getInt("p_id"),
            rs.getString("p_category")
        );
        
        return new User(
            rs.getString("Nickname"),
            rs.getString("Name"),
            rs.getString("Surname"),
            rs.getString("Email"),
            rs.getString("Password"),
            permission
        );
    }
}