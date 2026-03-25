package service.interfaces;

import model.dto.auth.LoginResult;
import model.dto.auth.AuthenticationResult;
import service.exception.ServiceException;

public interface AuthService {
    LoginResult authenticateUser(String nickname, String password) throws ServiceException;
    AuthenticationResult getSessionFromToken(String token) throws ServiceException;
}