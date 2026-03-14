package com.cloudstore.service.interfaces;

import com.cloudstore.model.dto.ProductDTO;
import com.cloudstore.service.exception.ServiceException;

import java.util.List;
import java.util.Optional;

public interface ProductService {

    Optional<ProductDTO> findById(int id) throws ServiceException;

    List<ProductDTO> findByName(String name) throws ServiceException;

    List<ProductDTO> findByCategory(String category) throws ServiceException;

    List<ProductDTO> findAll() throws ServiceException;

    ProductDTO save(ProductDTO dto) throws ServiceException;

    boolean delete(int id) throws ServiceException;

    boolean updateStock(int productId, int newQuantity) throws ServiceException;

    List<ProductDTO> findLowStockProducts(int threshold) throws ServiceException;

    boolean exists(int id) throws ServiceException;

    int count() throws ServiceException;
}
