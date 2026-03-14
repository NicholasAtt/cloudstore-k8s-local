package com.cloudstore.service.impl;

import com.cloudstore.dao.impl.ProductDAOImpl;
import com.cloudstore.dao.interfaces.ProductDAO;
import com.cloudstore.model.dto.ProductDTO;
import com.cloudstore.model.entities.Product;
import com.cloudstore.service.exception.ServiceException;
import com.cloudstore.service.interfaces.ProductService;
import com.cloudstore.service.mapper.DTOMapper;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class ProductServiceImpl implements ProductService {

    private final ProductDAO productDAO;

    public ProductServiceImpl() throws ServiceException {
        try {
            this.productDAO = new ProductDAOImpl();
        } catch (SQLException e) {
            throw new ServiceException("Unable to initialize ProductService", e);
        }
    }

    public ProductServiceImpl(ProductDAO productDAO) {
        this.productDAO = productDAO;
    }

    @Override
    public Optional<ProductDTO> findById(int id) throws ServiceException {
        try {
            return productDAO.findById(id).map(DTOMapper::toDTO);
        } catch (SQLException e) {
            throw new ServiceException("Error retrieving product with ID: " + id, e);
        }
    }

    @Override
    public List<ProductDTO> findByName(String name) throws ServiceException {
        try {
            return productDAO.findByName(name).stream()
                    .map(DTOMapper::toDTO)
                    .collect(Collectors.toList());
        } catch (SQLException e) {
            throw new ServiceException("Error searching products by name: " + name, e);
        }
    }

    @Override
    public List<ProductDTO> findByCategory(String category) throws ServiceException {
        try {
            return productDAO.findByCategory(category).stream()
                    .map(DTOMapper::toDTO)
                    .collect(Collectors.toList());
        } catch (SQLException e) {
            throw new ServiceException("Error searching products by category: " + category, e);
        }
    }

    @Override
    public List<ProductDTO> findAll() throws ServiceException {
        try {
            return productDAO.findAll().stream()
                    .map(DTOMapper::toDTO)
                    .collect(Collectors.toList());
        } catch (SQLException e) {
            throw new ServiceException("Error retrieving products", e);
        }
    }

    @Override
    public ProductDTO save(ProductDTO dto) throws ServiceException {
        validate(dto);
        try {
            Product saved = productDAO.save(DTOMapper.toEntity(dto));
            return DTOMapper.toDTO(saved);
        } catch (SQLException e) {
            throw new ServiceException("Error saving product", e);
        }
    }

    @Override
    public boolean delete(int id) throws ServiceException {
        try {
            return productDAO.delete(id);
        } catch (SQLException e) {
            // DAO throws a human-readable SQLException message when related transactions exist.
            throw new ServiceException(e.getMessage(), e);
        }
    }

    @Override
    public boolean updateStock(int productId, int newQuantity) throws ServiceException {
        if (newQuantity < 0) {
            throw new ServiceException("Stock quantity cannot be negative");
        }
        try {
            return productDAO.updateStock(productId, newQuantity);
        } catch (SQLException e) {
            throw new ServiceException("Error updating stock for product ID: " + productId, e);
        }
    }

    @Override
    public List<ProductDTO> findLowStockProducts(int threshold) throws ServiceException {
        if (threshold < 0) {
            throw new ServiceException("Stock threshold cannot be negative");
        }
        try {
            return productDAO.findLowStockProducts(threshold).stream()
                    .map(DTOMapper::toDTO)
                    .collect(Collectors.toList());
        } catch (SQLException e) {
            throw new ServiceException("Error searching low-stock products", e);
        }
    }

    @Override
    public boolean exists(int id) throws ServiceException {
        try {
            return productDAO.exists(id);
        } catch (SQLException e) {
            throw new ServiceException("Error checking product existence", e);
        }
    }

    @Override
    public int count() throws ServiceException {
        try {
            return productDAO.count();
        } catch (SQLException e) {
            throw new ServiceException("Error counting products", e);
        }
    }

    private void validate(ProductDTO dto) throws ServiceException {
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new ServiceException("Product name cannot be empty");
        }
        if (dto.getPrice() < 0) {
            throw new ServiceException("Product price cannot be negative");
        }
        if (dto.getStock() < 0) {
            throw new ServiceException("Product stock cannot be negative");
        }
    }
}
