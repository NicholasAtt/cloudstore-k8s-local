import json
import os
import requests
import sys
from error.APIError import APIError
from error.AuthError import AuthError
from error.ForbiddenError import ForbiddenError
from error.NotFoundError import NotFoundError
from error.ValidationError import ValidationError


class Linker:
    """ Linker class to interact with the backend API for CloudStore.
    
    Args:
        token (str, optional): Authentication token for API requests. Defaults to None.
    """
    
    def __init__(self, token=None):
        self.token = token
        backend_url = os.getenv('BACKEND_URL')
        if backend_url:
            self.backend_url = backend_url.rstrip("/")
        else:
            backend_scheme = os.getenv('BACKEND_SCHEME', 'http')
            backend_host = os.getenv('BACKEND_HOST', 'server')
            backend_port = os.getenv('BACKEND_PORT', '9999')
            self.backend_url = f"{backend_scheme}://{backend_host}:{backend_port}"
        print(f"Backend URL: {self.backend_url}", file=sys.stderr)
        
        self._test_connection()
    
    """ Internal method to test connectivity to the backend API. 
    
    Args:
        None
    
    Returns:
        None
    """
    def _test_connection(self):
        try:
            response = requests.get(f"{self.backend_url}/", timeout=5)
            print(f"Backend connection test: status={response.status_code}, response={response.text}", file=sys.stderr)
            if response.status_code == 200:
                print("Backend is reachable", file=sys.stderr)
            else:
                print(f"Backend returned status {response.status_code}", file=sys.stderr)
        except requests.exceptions.ConnectionError as e:
            print(f"CRITICAL: Cannot connect to backend at {self.backend_url}", file=sys.stderr)
            print(f"Error: {e}", file=sys.stderr)
            print(f"Make sure the backend container is running and reachable at {self.backend_url}", file=sys.stderr)
        except Exception as e:
            print(f"Backend test failed: {e}", file=sys.stderr)
    
    """ Internal method to call the backend API facade with proper error handling.
    
    Args:
        method_name (str): The name of the backend method to call.
        args: Positional arguments to pass to the backend method.
        token (str, optional): Authentication token for the request. Defaults to None.
    Returns:
        The result of the backend API call.
    """
    def _call_facade(self, method_name, *args, token=None):
        request = {"method": method_name, "args": args}
        
        headers = {"Content-Type": "application/json"}
        if token:
            headers["Authorization"] = f"Bearer {token}"
            
        try:
            response = requests.post(
                self.backend_url,
                json=request,
                timeout=100,
                headers=headers
            )
            
            if response.status_code != 200:
                print(f"HTTP Error: {response.status_code} - {response.text}", file=sys.stderr)
                error_msg = f"HTTP {response.status_code}: {response.text}"
                try:
                    result = response.json()
                    if result.get("error"):
                        error_msg = result["error"]
                except Exception:
                    pass
                
                if response.status_code == 400:
                    raise ValidationError(400, error_msg)
                elif response.status_code == 401:
                    raise AuthError(401, error_msg)
                elif response.status_code == 403:
                    raise ForbiddenError(403, error_msg)
                elif response.status_code == 404:
                    raise NotFoundError(404, error_msg)
                else:
                    raise APIError(response.status_code, error_msg)
            
            result = response.json()
            
            if not result.get("ok"):
                raise APIError(response.status_code, result.get("error", "Unknown error"))
            
            return result.get("data")
            
        except requests.exceptions.ConnectionError as e:
            print(f"Connection error: {e}", file=sys.stderr)
            raise RuntimeError(f"Cannot connect to backend at {self.backend_url}")
        except requests.exceptions.Timeout as e:
            print(f"Timeout error: {e}", file=sys.stderr)
            raise RuntimeError(f"Backend request timeout")
        except requests.exceptions.RequestException as e:
            print(f"HTTP error: {e}", file=sys.stderr)
            raise RuntimeError(f"Backend request failed: {e}")
        except json.JSONDecodeError as e:
            print(f"JSON decode error: {e}", file=sys.stderr)
            raise RuntimeError(f"Invalid response from backend")
    
    
    """ Internal method to call authenticated backend API methods. Raises an error if no token is available.
    
    Args:
        method_name (str): The name of the backend method to call.
        args: Positional arguments to pass to the backend method.
    Returns:
        The result of the backend API call.
    """
    def _call_authenticated_facade(self, method_name, *args):
        if not self.token:
            raise RuntimeError("Authentication required: missing token")
        return self._call_facade(method_name, *args, token=self.token)
    
    """ Authenticate a user with the given nickname and password. Returns user data if successful.
    
    Args:
        nickname (str): The user's nickname.
        password (str): The user's password.
    Returns:
        The result of the backend API call.
    """
    def authenticate_user(self, nickname, password):
        return self._call_facade("authenticateUser", nickname, password)
    
    """ List all products available in the store.
    
    Args:        
        None
    Returns:        
        The result of the backend API call.
    """
    def list_products(self):
        return self._call_facade("getAllProducts")
    
    """ List all product categories available in the store.
    
    Args:        
        None
    Returns:        
        The result of the backend API call.
    """
    def list_product_categories(self):
        return self._call_facade("getAllProductCategories")
    
    """ List products filtered by category. If category is "All" or empty, returns all products.
    
    Args:
        category (str): The category to filter products by.
    Returns:
        The result of the backend API call.
    """
    def list_products_by_category(self, category):
        if not category or category == "All":
            return self.list_products()
        return self._call_facade("findProductsByCategory", category)
    
    """ Save a new product to the store with the given details.
    
    Args:
        name (str): The name of the product.
        category (str): The category of the product.
        price (float): The price of the product.
        stock (int): The stock quantity of the product.
    Returns:
        The result of the backend API call.
    """
    def save_product(self, name, category, price, stock):
        product = {
            "id": 0,
            "name": name,
            "category": category,
            "price": float(price),
            "stock": int(stock)
        }
        return self._call_authenticated_facade("saveProduct", product)
    
    """ Delete a product from the store by its ID.
    
    Args:
        product_id (int): The ID of the product to delete.
    Returns:
        The result of the backend API call.
    """
    def delete_product(self, product_id):
        return self._call_authenticated_facade("deleteProduct", int(product_id))
    
    """ Update the stock quantity of a product by its ID.
    
    Args:
        product_id (int): The ID of the product to update.
        new_stock (int): The new stock quantity.
    Returns:
        The result of the backend API call.
    """
    def update_product_stock(self, product_id, new_stock):
        return self._call_authenticated_facade("updateProductStock", int(product_id), int(new_stock))
    
    """ Update the stock quantity of a product for sellers by its ID.
    
    Args:
        product_id (int): The ID of the product to update.
        new_stock (int): The new stock quantity.
    Returns:
        The result of the backend API call.
    """
    def update_seller_product_stock(self, product_id, new_stock):
        """Update product stock for sellers."""
        return self._call_authenticated_facade("updateSellerProductStock", int(product_id), int(new_stock))
    
    """ Find products that are low in stock based on a given threshold.
    
    Args:
        threshold (int): The stock threshold for identifying low-stock products.
    Returns:
        The result of the backend API call.
    """
    def low_stock_products(self, threshold):
        return self._call_authenticated_facade("findLowStockProducts", int(threshold))
    
    """ List all users registered in the system. Requires authentication.
    
    Args:
        None
    Returns:
        The result of the backend API call.
    """
    def list_users(self):
        return self._call_authenticated_facade("getAllUsers")
    
    """ Register a new user with the given details. Requires authentication.
    
    Args:
        nickname (str): The nickname of the user.
        name (str): The name of the user.
        surname (str): The surname of the user.
        email (str): The email of the user.
        password (str): The password of the user.
        permission_id (int): The ID of the permission to assign to the user.
    Returns:
        The result of the backend API call.
    """
    def register_user(self, nickname, name, surname, email, password, permission_id):
        user = {
            "nickname": nickname,
            "name": name,
            "surname": surname,
            "email": email,
            "password": password,
            "permission": {"id": int(permission_id)}
        }
        return self._call_facade("registerUser", user)
    
    """ List all permissions available in the system. Requires authentication.
    
    Args:
        None
    Returns:
        The result of the backend API call.
    """
    def list_permissions(self):
        return self._call_authenticated_facade("getAllPermissions")
    
    """ Save a new permission category to the system. Requires authentication.
    
    Args:
        category (str): The category of the permission to save.
    Returns:
        The result of the backend API call.
    """
    def save_permission(self, category):
        permission = {"id": 0, "category": category}
        return self._call_authenticated_facade("savePermission", permission)
    
    """ Get the first available permission ID for creating new permissions. Requires authentication.
    
    Args:
        None
    Returns:
        The result of the backend API call.
    """
    def get_first_available_permission_id(self):
        return self._call_authenticated_facade("getDefaultPermissionId")
    
    """ List recent transactions in the system. Requires authentication.
    
    Args:
        limit (int): The maximum number of transactions to retrieve.
    Returns:
        The result of the backend API call.
    """
    def list_transactions(self, limit=50):
        return self._call_authenticated_facade("findRecentTransactions", int(limit))
    
    """ Get the checkout context for a customer, including product details and pricing. Requires authentication.
    
    Args:
        customer_name (str): The name of the customer.
        items (list): A list of items in the customer's cart.
    Returns:
        The result of the backend API call.
    """
    def get_customer_checkout_context(self, customer_name, items=None):
        normalized_items = {}
        for item in items or []:
            product_id = int(item.get("product_id", 0))
            quantity = int(item.get("quantity", 0))
            if product_id > 0 and quantity > 0:
                normalized_items[product_id] = quantity
        return self._call_authenticated_facade("getCheckoutContext", customer_name, normalized_items)
    
    """ Process a cart order for a customer, including payment and city information. Requires authentication.
    
    Args:
        customer_name (str): The name of the customer.
        payment_method (str): The payment method to use.
        city (str): The city of the customer.
        items (list): A list of items in the customer's cart.
    Returns:
        The result of the backend API call.
    """
    def process_cart_order(self, customer_name, payment_method, city, items):
        normalized_items = {}
        for item in items:
            product_id = int(item.get("product_id", 0))
            quantity = int(item.get("quantity", 0))
            if product_id > 0 and quantity > 0:
                normalized_items[product_id] = quantity
        return self._call_authenticated_facade("processCartOrder", customer_name, payment_method, city, normalized_items)
    
    """ Get dashboard statistics for the authenticated user, including total products, users, transactions, and sales. Requires authentication.
    
    Args:        
        None
    Returns:        
        The result of the backend API call.
    """
    def dashboard_stats(self):
        raw = self._call_authenticated_facade("getDashboardStats")
        return {
            "total_products": raw.get("totalProducts", 0),
            "total_users": raw.get("totalUsers", 0),
            "total_transactions": raw.get("totalTransactions", 0),
            "total_permissions": raw.get("totalPermissions", 0),
            "monthly_sales": float(raw.get("monthlySales", 0.0)),
            "monthly_transactions": raw.get("monthlyTransactions", 0),
            "low_stock_products": raw.get("lowStockProducts", 0),
        }
    
    """ Get the user profile for a given nickname, including order history and total spent. Requires authentication.
    
    Args:
        nickname (str): The nickname of the user.
    Returns:
        The result of the backend API call.
    """
    def user_profile(self, nickname):
        raw = self._call_authenticated_facade("getUserProfile", nickname)
        if not raw:
            return None
        return {
            "user": raw.get("user"),
            "orders": raw.get("orderHistory", []),
            "total_orders": raw.get("totalOrders", 0),
            "total_spent": float(raw.get("totalSpent", 0.0)),
        }
    
    """ Process an order for a customer with detailed information about the product, payment, and city. Requires authentication.
    
    Args:
        customer_name (str): The name of the customer.
        product_id (int): The ID of the product.
        total_items (int): The total number of items.
        payment_method (str): The payment method to use.
        city (str): The city of the customer.
        customer_category (str): The category of the customer.
        discount (float): The discount amount.
    Returns:
        The result of the backend API call.
    """
    def process_order(self, customer_name, product_id, total_items, payment_method, city, customer_category, discount):
        products = self.list_products()
        target = next((p for p in products if int(p["id"]) == int(product_id)), None)
        if not target:
            raise ValueError(f"Product not found with ID: {product_id}")
        
        transaction = {
            "id": 0,
            "customerName": customer_name,
            "product": target["name"],
            "totalItems": int(total_items),
            "totalCost": float(target["price"]) * int(total_items),
            "paymentMethod": payment_method,
            "city": city,
            "discountApplied": 1 if float(discount) > 0 else 0,
            "customerCategory": customer_category,
            "discount": float(discount),
            "productDetails": {"id": int(product_id)}
        }
        return self._call_authenticated_facade("processOrder", customer_name, transaction)
    
    """ Get dashboard statistics for the authenticated seller, including total sales, orders, revenue, and low stock products. Requires authentication.
    
    Args:        
        None
    Returns:
        The result of the backend API call.
    """
    def get_seller_dashboard_stats(self):
        """Get dashboard statistics for the authenticated seller."""
        raw = self._call_authenticated_facade("getSellerDashboardStats")
        return {
            "total_sales": float(raw.get("totalSales", 0.0)),
            "total_orders": raw.get("totalOrders", 0),
            "total_revenue": float(raw.get("totalRevenue", 0.0)),
            "average_order_value": float(raw.get("averageOrderValue", 0.0)),
            "products_sold": raw.get("productsSold", 0),
            "low_stock_products": raw.get("lowStockProducts", 0),
        }
    
    """ Get all products associated with the authenticated seller. Requires authentication.
    
    Args:        
        None
    Returns:
        The result of the backend API call.
    """
    def get_seller_products(self):
        """Get all products associated with the authenticated seller."""
        return self._call_authenticated_facade("getSellerProducts")
    
    """ Get recent sales orders for the authenticated seller's products. Requires authentication.
    
    Args:
        limit (int): The maximum number of sales orders to retrieve.
    Returns:
        The result of the backend API call.
    """
    def get_seller_sales_orders(self, limit=50):
        """Get recent sales orders for the authenticated seller's products."""
        return self._call_authenticated_facade("getSellerSalesOrders", int(limit))
    
    """ Get top customers by total spending for the authenticated seller. Requires authentication.
    
    Args:
        limit (int): The maximum number of top customers to retrieve.
    Returns:
        The result of the backend API call.
    """
    def get_seller_top_customers(self, limit=10):
        """Get top customers by total spending for the authenticated seller."""
        return self._call_authenticated_facade("getSellerTopCustomers", int(limit))
    
    """ Fetch a single record from the backend API. This method is disabled for direct queries and should be used through the Facade.
    
    Args:
        query (str): The SQL query to execute.
        params (tuple): The parameters for the SQL query.
    Returns:
        The result of the backend API call.
    """
    def fetch_one(self, query, params=()):
        if query.strip().lower().startswith("select 1"):
            return {"ok": 1}
        raise RuntimeError("Direct queries are disabled: use the Facade")
    
    """Calls server-side logout to blacklist the token in Redis, then clears local token."""
    def logout(self):
        if not self.token:
            return
        try:
            self._call_facade("logout", self.token, token=self.token)
        except Exception as e:
            print(f"Server-side logout failed: {e}", file=sys.stderr)
        self.token = None

    """Clears the local token reference."""
    def remove_token(self):
        self.token = None
    def validate_session(self):
        """Validates the current session token with the backend."""
        return self._call_authenticated_facade("getSessionFromToken", self.token)
