CREATE DATABASE IF NOT EXISTS `cloudstore_db`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

USE `cloudstore_db`;

CREATE TABLE IF NOT EXISTS `permissions` (
  `ID` int NOT NULL AUTO_INCREMENT,
  `Category` varchar(50) NOT NULL,
  PRIMARY KEY (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT IGNORE INTO `permissions` (`ID`, `Category`) VALUES
  (1, 'customer'),
  (2, 'seller'),
  (3, 'admin');

CREATE TABLE IF NOT EXISTS `products` (
  `Product_Id` int NOT NULL AUTO_INCREMENT,
  `Product_Name` varchar(255) NOT NULL,
  `Category` varchar(50) DEFAULT NULL,
  `Price` decimal(10,2) NOT NULL,
  `Stock_Quantity` int NOT NULL,
  PRIMARY KEY (`Product_Id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT IGNORE INTO `products` (`Product_Id`, `Product_Name`, `Category`, `Price`, `Stock_Quantity`) VALUES
  (1, 'Air Freshener', 'Cleaning', 3.99, 31),
  (2, 'Apple', 'Food', 2.99, 96),
  (3, 'Baby Wipes', 'Personal Care', 5.99, 50),
  (4, 'Banana', 'Food', 1.99, 120),
  (5, 'Bath Towels', 'Personal Care', 12.99, 40),
  (6, 'Coffee', 'Food', 8.99, 50),
  (7, 'Dish Soap', 'Cleaning', 3.49, 80),
  (8, 'Toothpaste', 'Personal Care', 4.99, 75);

CREATE TABLE IF NOT EXISTS `transactions` (
  `Transaction_ID` bigint NOT NULL,
  `Date` datetime NOT NULL,
  `Customer_Name` text NOT NULL,
  `Product` text NOT NULL,
  `Total_Items` bigint NOT NULL,
  `Total_Cost` decimal(10,2) DEFAULT NULL,
  `Payment_Method` text NOT NULL,
  `City` text,
  `Discount_Applied` tinyint(1) NOT NULL,
  `Customer_Category` text,
  `Product_Id` int NOT NULL,
  `Discount` decimal(3,2) DEFAULT '0.00',
  PRIMARY KEY (`Transaction_ID`),
  KEY `fk_transactions_product` (`Product_Id`) USING BTREE,
  CONSTRAINT `fk_transactions_product` FOREIGN KEY (`Product_Id`) REFERENCES `products` (`Product_Id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT IGNORE INTO `transactions`
  (`Transaction_ID`, `Date`, `Customer_Name`, `Product`, `Total_Items`, `Total_Cost`, `Payment_Method`, `City`, `Discount_Applied`, `Customer_Category`, `Product_Id`, `Discount`)
VALUES
  (1001000001, '2026-03-25 15:29:04', 'nicho', 'Air Freshener', 1, 3.67, 'Credit Card', 'Rome', 1, 'customer', 1, 0.08),
  (1001000002, '2026-03-25 16:46:45', 'Mario Rossi', 'Air Freshener', 1, 3.99, 'Credit Card', 'Rome', 0, 'Regular', 1, 0.00),
  (1001000006, '2026-03-25 21:09:18', 'nicho', 'Apple', 2, 5.57, 'Credit Card', 'Rome', 1, 'customer', 2, 0.07);

CREATE TABLE IF NOT EXISTS `users` (
  `Nickname` varchar(50) NOT NULL,
  `Name` varchar(50) NOT NULL,
  `Surname` varchar(50) NOT NULL,
  `Email` varchar(100) NOT NULL,
  `Password` varchar(255) NOT NULL,
  `Permission_ID` int NOT NULL,
  PRIMARY KEY (`Nickname`),
  UNIQUE KEY `Email` (`Email`),
  KEY `Permission_ID` (`Permission_ID`),
  CONSTRAINT `users_ibfk_1` FOREIGN KEY (`Permission_ID`) REFERENCES `permissions` (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT IGNORE INTO `users` (`Nickname`, `Name`, `Surname`, `Email`, `Password`, `Permission_ID`) VALUES
  ('admin', 'nicho', 'att', 'nicho@gmail.com', 'root', 3),
  ('user', 'user', 'user', 'user@gmail.com', 'user', 1),
  ('nicho', 'nicho', 'att', 'nichoatt@gmail.com', 'nicho', 2);
