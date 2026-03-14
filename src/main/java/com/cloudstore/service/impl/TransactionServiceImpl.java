package com.cloudstore.service.impl;

import com.cloudstore.dao.impl.TransactionDAOImpl;
import com.cloudstore.dao.interfaces.TransactionDAO;
import com.cloudstore.model.dto.TransactionDTO;
import com.cloudstore.model.entities.Transaction;
import com.cloudstore.service.exception.ServiceException;
import com.cloudstore.service.interfaces.TransactionService;
import com.cloudstore.service.mapper.DTOMapper;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class TransactionServiceImpl implements TransactionService {

    private final TransactionDAO transactionDAO;

    public TransactionServiceImpl() throws ServiceException {
        try {
            this.transactionDAO = new TransactionDAOImpl();
        } catch (SQLException e) {
            throw new ServiceException("Unable to initialize TransactionService", e);
        }
    }

    public TransactionServiceImpl(TransactionDAO transactionDAO) {
        this.transactionDAO = transactionDAO;
    }

    @Override
    public Optional<TransactionDTO> findById(long id) throws ServiceException {
        try {
            return transactionDAO.findById(id).map(DTOMapper::toDTO);
        } catch (SQLException e) {
            throw new ServiceException("Error retrieving transaction ID: " + id, e);
        }
    }

    @Override
    public List<TransactionDTO> findByCustomer(String customerName) throws ServiceException {
        try {
            return transactionDAO.findByCustomer(customerName).stream()
                    .map(DTOMapper::toDTO)
                    .collect(Collectors.toList());
        } catch (SQLException e) {
            throw new ServiceException("Error searching transactions by customer: " + customerName, e);
        }
    }

    @Override
    public List<TransactionDTO> findByProduct(int productId) throws ServiceException {
        try {
            return transactionDAO.findByProduct(productId).stream()
                    .map(DTOMapper::toDTO)
                    .collect(Collectors.toList());
        } catch (SQLException e) {
            throw new ServiceException("Error searching transactions by product ID: " + productId, e);
        }
    }

    @Override
    public List<TransactionDTO> findByDateRange(LocalDateTime start, LocalDateTime end) throws ServiceException {
        if (start.isAfter(end)) {
            throw new ServiceException("Start date cannot be after end date");
        }
        try {
            return transactionDAO.findByDateRange(start, end).stream()
                    .map(DTOMapper::toDTO)
                    .collect(Collectors.toList());
        } catch (SQLException e) {
            throw new ServiceException("Error searching transactions by date range", e);
        }
    }

    @Override
    public List<TransactionDTO> findByPaymentMethod(String paymentMethod) throws ServiceException {
        try {
            return transactionDAO.findByPaymentMethod(paymentMethod).stream()
                    .map(DTOMapper::toDTO)
                    .collect(Collectors.toList());
        } catch (SQLException e) {
            throw new ServiceException("Error searching transactions by payment method", e);
        }
    }

    @Override
    public List<TransactionDTO> findByCity(String city) throws ServiceException {
        try {
            return transactionDAO.findByCity(city).stream()
                    .map(DTOMapper::toDTO)
                    .collect(Collectors.toList());
        } catch (SQLException e) {
            throw new ServiceException("Error searching transactions by city: " + city, e);
        }
    }

    @Override
    public List<TransactionDTO> findAll() throws ServiceException {
        try {
            return transactionDAO.findAll().stream()
                    .map(DTOMapper::toDTO)
                    .collect(Collectors.toList());
        } catch (SQLException e) {
            throw new ServiceException("Error retrieving transactions", e);
        }
    }

    @Override
    public TransactionDTO save(TransactionDTO dto) throws ServiceException {
        validate(dto);
        try {
            Transaction saved = transactionDAO.save(DTOMapper.toEntity(dto));
            return DTOMapper.toDTO(saved);
        } catch (SQLException e) {
            throw new ServiceException("Error saving transaction", e);
        }
    }

    @Override
    public boolean delete(long id) throws ServiceException {
        try {
            return transactionDAO.delete(id);
        } catch (SQLException e) {
            throw new ServiceException("Error deleting transaction ID: " + id, e);
        }
    }

    @Override
    public double calculateTotalSales(LocalDateTime start, LocalDateTime end) throws ServiceException {
        if (start.isAfter(end)) {
            throw new ServiceException("Start date cannot be after end date");
        }
        try {
            return transactionDAO.calculateTotalSales(start, end);
        } catch (SQLException e) {
            throw new ServiceException("Error calculating total sales", e);
        }
    }

    @Override
    public int countByDateRange(LocalDateTime start, LocalDateTime end) throws ServiceException {
        if (start.isAfter(end)) {
            throw new ServiceException("Start date cannot be after end date");
        }
        try {
            return transactionDAO.countByDateRange(start, end);
        } catch (SQLException e) {
            throw new ServiceException("Error counting transactions by date range", e);
        }
    }

    @Override
    public List<TransactionDTO> findRecentTransactions(int limit) throws ServiceException {
        if (limit <= 0) {
            throw new ServiceException("Limit must be a positive number");
        }
        try {
            return transactionDAO.findRecentTransactions(limit).stream()
                    .map(DTOMapper::toDTO)
                    .collect(Collectors.toList());
        } catch (SQLException e) {
            throw new ServiceException("Error retrieving recent transactions", e);
        }
    }

    @Override
    public boolean exists(long id) throws ServiceException {
        try {
            return transactionDAO.exists(id);
        } catch (SQLException e) {
            throw new ServiceException("Error checking transaction existence", e);
        }
    }

    @Override
    public int count() throws ServiceException {
        try {
            return transactionDAO.count();
        } catch (SQLException e) {
            throw new ServiceException("Error counting transactions", e);
        }
    }

    private void validate(TransactionDTO dto) throws ServiceException {
        if (dto.getCustomerName() == null || dto.getCustomerName().isBlank()) {
            throw new ServiceException("Customer name cannot be empty");
        }
        if (dto.getTotalItems() <= 0) {
            throw new ServiceException("Number of items must be greater than zero");
        }
        if (dto.getTotalCost() < 0) {
            throw new ServiceException("Total cost cannot be negative");
        }
        if (dto.getProductDetails() == null) {
            throw new ServiceException("Transaction must be associated with a product");
        }
    }
}
