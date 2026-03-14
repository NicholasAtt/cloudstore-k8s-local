package com.cloudstore.dao.interfaces;

import com.cloudstore.model.entities.User;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public interface UserDAO {
    
    Optional<User> findByNickname(String nickname) throws SQLException;
    
    Optional<User> findByEmail(String email) throws SQLException;

    List<User> findByPermission(int permissionId) throws SQLException;
    
    List<User> findAll() throws SQLException;
    
    User save(User user) throws SQLException;
    
    User update(User user) throws SQLException;

    boolean delete(String nickname) throws SQLException;
    
    boolean exists(String nickname) throws SQLException;

    boolean emailExists(String email) throws SQLException;
    
    boolean updatePassword(String nickname, String newPassword) throws SQLException;

    boolean updatePermission(String nickname, int newPermissionId) throws SQLException;

    int count() throws SQLException;
    
    int countByPermission(int permissionId) throws SQLException;
}