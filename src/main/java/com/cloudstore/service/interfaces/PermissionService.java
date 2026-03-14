package com.cloudstore.service.interfaces;

import com.cloudstore.model.dto.PermissionDTO;
import com.cloudstore.service.exception.ServiceException;

import java.util.List;
import java.util.Optional;

public interface PermissionService {

    Optional<PermissionDTO> findById(int id) throws ServiceException;

    Optional<PermissionDTO> findByCategory(String category) throws ServiceException;

    List<PermissionDTO> findAll() throws ServiceException;

    PermissionDTO save(PermissionDTO dto) throws ServiceException;

    boolean delete(int id) throws ServiceException;

    boolean exists(int id) throws ServiceException;

    int count() throws ServiceException;
}
