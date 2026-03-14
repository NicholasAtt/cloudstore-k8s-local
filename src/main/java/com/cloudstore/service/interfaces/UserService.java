package com.cloudstore.service.interfaces;

import com.cloudstore.model.dto.UserDTO;
import com.cloudstore.service.exception.ServiceException;

import java.util.List;
import java.util.Optional;

public interface UserService {

    Optional<UserDTO> findByNickname(String nickname) throws ServiceException;

    Optional<UserDTO> findByEmail(String email) throws ServiceException;

    List<UserDTO> findByPermission(int permissionId) throws ServiceException;

    List<UserDTO> findAll() throws ServiceException;

    UserDTO register(UserDTO dto) throws ServiceException;

    boolean delete(String nickname) throws ServiceException;

    boolean updatePassword(String nickname, String newPassword) throws ServiceException;

    boolean updatePermission(String nickname, int permissionId) throws ServiceException;

    boolean exists(String nickname) throws ServiceException;

    int count() throws ServiceException;
}
