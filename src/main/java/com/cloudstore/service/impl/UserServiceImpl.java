package com.cloudstore.service.impl;

import com.cloudstore.dao.impl.PermissionDAOImpl;
import com.cloudstore.dao.impl.UserDAOImpl;
import com.cloudstore.dao.interfaces.PermissionDAO;
import com.cloudstore.dao.interfaces.UserDAO;
import com.cloudstore.model.dto.UserDTO;
import com.cloudstore.model.entities.User;
import com.cloudstore.service.exception.ServiceException;
import com.cloudstore.service.interfaces.UserService;
import com.cloudstore.service.mapper.DTOMapper;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class UserServiceImpl implements UserService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final int MIN_PASSWORD_LENGTH = 8;

    private final UserDAO userDAO;
    private final PermissionDAO permissionDAO;

    public UserServiceImpl() throws ServiceException {
        try {
            this.userDAO = new UserDAOImpl();
            this.permissionDAO = new PermissionDAOImpl();
        } catch (SQLException e) {
            throw new ServiceException("Unable to initialize UserService", e);
        }
    }

    public UserServiceImpl(UserDAO userDAO, PermissionDAO permissionDAO) {
        this.userDAO = userDAO;
        this.permissionDAO = permissionDAO;
    }

    @Override
    public Optional<UserDTO> findByNickname(String nickname) throws ServiceException {
        try {
            return userDAO.findByNickname(nickname).map(DTOMapper::toDTO);
        } catch (SQLException e) {
            throw new ServiceException("Error retrieving user: " + nickname, e);
        }
    }

    @Override
    public Optional<UserDTO> findByEmail(String email) throws ServiceException {
        try {
            return userDAO.findByEmail(email).map(DTOMapper::toDTO);
        } catch (SQLException e) {
            throw new ServiceException("Error retrieving user by email: " + email, e);
        }
    }

    @Override
    public List<UserDTO> findByPermission(int permissionId) throws ServiceException {
        try {
            return userDAO.findByPermission(permissionId).stream()
                    .map(DTOMapper::toDTO)
                    .collect(Collectors.toList());
        } catch (SQLException e) {
            throw new ServiceException("Error searching users by permission ID: " + permissionId, e);
        }
    }

    @Override
    public List<UserDTO> findAll() throws ServiceException {
        try {
            return userDAO.findAll().stream()
                    .map(DTOMapper::toDTO)
                    .collect(Collectors.toList());
        } catch (SQLException e) {
            throw new ServiceException("Error retrieving users", e);
        }
    }

    @Override
    public UserDTO register(UserDTO dto) throws ServiceException {
        validate(dto);
        try {
            if (userDAO.exists(dto.getNickname())) {
                throw new ServiceException("Nickname already in use: " + dto.getNickname());
            }
            if (userDAO.emailExists(dto.getEmail())) {
                throw new ServiceException("Email already registered: " + dto.getEmail());
            }
            if (dto.getPermission() == null || !permissionDAO.exists(dto.getPermission().getId())) {
                throw new ServiceException("Permission not found with ID: "
                        + (dto.getPermission() != null ? dto.getPermission().getId() : "null"));
            }
            User saved = userDAO.save(DTOMapper.toEntity(dto));
            return DTOMapper.toDTO(saved);
        } catch (ServiceException e) {
            throw e;
        } catch (SQLException e) {
            throw new ServiceException("Error registering user", e);
        }
    }

    @Override
    public boolean delete(String nickname) throws ServiceException {
        try {
            return userDAO.delete(nickname);
        } catch (SQLException e) {
            throw new ServiceException(e.getMessage(), e);
        }
    }

    @Override
    public boolean updatePassword(String nickname, String newPassword) throws ServiceException {
        if (newPassword == null || newPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new ServiceException("Password must contain at least " + MIN_PASSWORD_LENGTH + " characters");
        }
        try {
            return userDAO.updatePassword(nickname, newPassword);
        } catch (SQLException e) {
            throw new ServiceException("Error updating password for: " + nickname, e);
        }
    }

    @Override
    public boolean updatePermission(String nickname, int permissionId) throws ServiceException {
        try {
            return userDAO.updatePermission(nickname, permissionId);
        } catch (SQLException e) {
            throw new ServiceException("Error updating permission for: " + nickname, e);
        }
    }

    @Override
    public boolean exists(String nickname) throws ServiceException {
        try {
            return userDAO.exists(nickname);
        } catch (SQLException e) {
            throw new ServiceException("Error checking user existence", e);
        }
    }

    @Override
    public int count() throws ServiceException {
        try {
            return userDAO.count();
        } catch (SQLException e) {
            throw new ServiceException("Error counting users", e);
        }
    }

    private void validate(UserDTO dto) throws ServiceException {
        if (dto.getNickname() == null || dto.getNickname().isBlank()) {
            throw new ServiceException("Nickname cannot be empty");
        }
        if (dto.getEmail() == null || !EMAIL_PATTERN.matcher(dto.getEmail()).matches()) {
            throw new ServiceException("Invalid email format: " + dto.getEmail());
        }
        if (dto.getPassword() == null || dto.getPassword().length() < MIN_PASSWORD_LENGTH) {
            throw new ServiceException("Password must contain at least " + MIN_PASSWORD_LENGTH + " characters");
        }
    }
}
