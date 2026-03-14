package com.cloudstore.service.impl;

import com.cloudstore.dao.impl.PermissionDAOImpl;
import com.cloudstore.dao.interfaces.PermissionDAO;
import com.cloudstore.model.dto.PermissionDTO;
import com.cloudstore.model.entities.Permission;
import com.cloudstore.service.exception.ServiceException;
import com.cloudstore.service.interfaces.PermissionService;
import com.cloudstore.service.mapper.DTOMapper;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class PermissionServiceImpl implements PermissionService {

    private final PermissionDAO permissionDAO;

    public PermissionServiceImpl() throws ServiceException {
        try {
            this.permissionDAO = new PermissionDAOImpl();
        } catch (SQLException e) {
            throw new ServiceException("Unable to initialize PermissionService", e);
        }
    }

    public PermissionServiceImpl(PermissionDAO permissionDAO) {
        this.permissionDAO = permissionDAO;
    }

    @Override
    public Optional<PermissionDTO> findById(int id) throws ServiceException {
        try {
            return permissionDAO.findById(id).map(DTOMapper::toDTO);
        } catch (SQLException e) {
            throw new ServiceException("Error retrieving permission with ID: " + id, e);
        }
    }

    @Override
    public Optional<PermissionDTO> findByCategory(String category) throws ServiceException {
        try {
            return permissionDAO.findByCategory(category).map(DTOMapper::toDTO);
        } catch (SQLException e) {
            throw new ServiceException("Error retrieving permission by category: " + category, e);
        }
    }

    @Override
    public List<PermissionDTO> findAll() throws ServiceException {
        try {
            return permissionDAO.findAll().stream()
                    .map(DTOMapper::toDTO)
                    .collect(Collectors.toList());
        } catch (SQLException e) {
            throw new ServiceException("Error retrieving permissions", e);
        }
    }

    @Override
    public PermissionDTO save(PermissionDTO dto) throws ServiceException {
        if (dto.getCategory() == null || dto.getCategory().isBlank()) {
            throw new ServiceException("Permission category cannot be empty");
        }
        try {
            Permission saved = permissionDAO.save(DTOMapper.toEntity(dto));
            return DTOMapper.toDTO(saved);
        } catch (SQLException e) {
            throw new ServiceException("Error saving permission", e);
        }
    }

    @Override
    public boolean delete(int id) throws ServiceException {
        try {
            if (permissionDAO.isInUse(id)) {
                throw new ServiceException("Cannot delete: permission is assigned to at least one user");
            }
            return permissionDAO.delete(id);
        } catch (SQLException e) {
            throw new ServiceException("Error deleting permission with ID: " + id, e);
        }
    }

    @Override
    public boolean exists(int id) throws ServiceException {
        try {
            return permissionDAO.exists(id);
        } catch (SQLException e) {
            throw new ServiceException("Error checking permission existence", e);
        }
    }

    @Override
    public int count() throws ServiceException {
        try {
            return permissionDAO.count();
        } catch (SQLException e) {
            throw new ServiceException("Error counting permissions", e);
        }
    }
}
