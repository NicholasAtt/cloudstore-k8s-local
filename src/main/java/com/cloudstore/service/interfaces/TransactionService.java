package com.cloudstore.service.interfaces;

import com.cloudstore.model.dto.TransactionDTO;
import com.cloudstore.service.exception.ServiceException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TransactionService {

    Optional<TransactionDTO> findById(long id) throws ServiceException;

    List<TransactionDTO> findByCustomer(String customerName) throws ServiceException;

    List<TransactionDTO> findByProduct(int productId) throws ServiceException;

    List<TransactionDTO> findRecentByProduct(int productId, int limit) throws ServiceException;

    List<TransactionDTO> findByDateRange(LocalDateTime start, LocalDateTime end) throws ServiceException;

    List<TransactionDTO> findByPaymentMethod(String paymentMethod) throws ServiceException;

    List<TransactionDTO> findByCity(String city) throws ServiceException;

    List<TransactionDTO> findAll() throws ServiceException;

    TransactionDTO save(TransactionDTO dto) throws ServiceException;

    boolean delete(long id) throws ServiceException;

    double calculateTotalSales(LocalDateTime start, LocalDateTime end) throws ServiceException;

    int countByDateRange(LocalDateTime start, LocalDateTime end) throws ServiceException;

    List<TransactionDTO> findRecentTransactions(int limit) throws ServiceException;

    boolean exists(long id) throws ServiceException;

    int count() throws ServiceException;
}
