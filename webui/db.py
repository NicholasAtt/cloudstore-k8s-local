import json
import os
import shlex
import subprocess
from datetime import datetime
from pathlib import Path


BRIDGE_MAIN_CLASS = "com.cloudstore.service.facade.CloudStoreFacadeBridge"

class CloudStoreDB:
    def __init__(self):
        default_java_cmd = self._detect_default_java_cmd()
        self.java_cmd = os.getenv(
            "FACADE_JAVA_CMD",
            default_java_cmd,
        )
        self.auth_nickname = None
        self.auth_password = None

    def _detect_default_java_cmd(self):
        docker_classes = Path("/app/target/classes")
        if docker_classes.exists():
            return f"java -cp /app/target/classes:/app/target/dependency/* {BRIDGE_MAIN_CLASS}"

        target_dir = Path(__file__).resolve().parent.parent / "target"
        classpath_entries = []

        local_classes = target_dir / "classes"
        if local_classes.exists():
            classpath_entries.append(str(local_classes))

        local_deps = target_dir / "dependency"
        if local_deps.exists():
            classpath_entries.append(f"{local_deps}/*")

        jar_candidates = sorted(
            (p for p in target_dir.glob("cloudstore-app-*.jar") if not p.name.startswith("original-")),
            key=lambda p: p.stat().st_mtime,
            reverse=True,
        )
        if jar_candidates:
            classpath_entries.append(str(jar_candidates[0]))

        if classpath_entries:
            return f"java -cp {':'.join(classpath_entries)} {BRIDGE_MAIN_CLASS}"

        return f"java -cp /app/target/classes:/app/target/dependency/* {BRIDGE_MAIN_CLASS}"

    def _call_facade(self, command, payload=""):
        cmd = shlex.split(self.java_cmd) + [command]
        result = subprocess.run(
            cmd,
            input=payload,
            text=True,
            capture_output=True,
            check=False,
        )

        stdout = (result.stdout or "").strip()
        if not stdout:
            raise RuntimeError(result.stderr.strip() or "No response from RemoteFacade")

        try:
            response = json.loads(stdout)
        except json.JSONDecodeError as exc:
            raise RuntimeError(f"Invalid response from RemoteFacade: {stdout}") from exc

        if result.returncode != 0 or not response.get("ok", False):
            raise RuntimeError(response.get("error") or result.stderr.strip() or "Error from RemoteFacade")

        return response.get("data")

    def fetch_one(self, query, params=()):
        if query.strip().lower().startswith("select 1"):
            return {"ok": 1}
        raise RuntimeError("Direct queries are disabled: use the RemoteFacade")

    def set_auth_context(self, nickname, password):
        self.auth_nickname = nickname
        self.auth_password = password

    def _call_admin_facade(self, command, data=None):
        if not self.auth_nickname or not self.auth_password:
            raise RuntimeError("Admin authentication context not set")
        payload = json.dumps(
            {
                "auth": {"nickname": self.auth_nickname, "password": self.auth_password},
                "data": {} if data is None else data,
            }
        )
        return self._call_facade(command, payload)

    def list_permissions(self):
        return self._call_admin_facade("list_permissions")

    def save_permission(self, category):
        return self._call_admin_facade("save_permission", {"id": 0, "category": category})

    def list_products(self):
        return self._call_facade("list_products")

    def list_product_categories(self):
        categories = self._call_facade("list_product_categories")
        return categories or []

    def list_products_by_category(self, category):
        if category is None or not str(category).strip():
            return self.list_products()
        return self._call_facade("list_products_by_category", str(category).strip())

    def save_product(self, name, category, price, stock):
        payload = {
            "id": 0,
            "name": name,
            "description": category,
            "price": float(price),
            "stock": int(stock),
        }
        return self._call_admin_facade("save_product", payload)

    def delete_product(self, product_id):
        return self._call_admin_facade("delete_product", {"productId": int(product_id)})

    def update_product_stock(self, product_id, new_stock):
        products = self.list_products()
        target = next((p for p in products if int(p["id"]) == int(product_id)), None)
        if not target:
            raise ValueError(f"Product not found with ID: {product_id}")
        payload = {
            "id": int(target["id"]),
            "name": target["name"],
            "description": target.get("description", target.get("category", "")),
            "price": float(target["price"]),
            "stock": int(new_stock),
        }
        return self._call_admin_facade("save_product", payload)

    def low_stock_products(self, threshold):
        return self._call_admin_facade("low_stock", {"threshold": int(threshold)})

    def list_users(self):
        return self._call_admin_facade("list_users")

    def register_user(self, nickname, name, surname, email, password, permission_id):
        payload = {
            "nickname": nickname,
            "name": name,
            "surname": surname,
            "email": email,
            "password": password,
            "permission": {"id": int(permission_id)},
        }

        return self._call_admin_facade("register_user", payload)

    def authenticate_user(self, nickname, password):
        payload = json.dumps({"nickname": nickname, "password": password})
        return self._call_facade("authenticate_user", payload)

    def get_customer_checkout_context(self, customer_name, items=None):
        normalized_items = []
        for item in items or []:
            product_id = int(item.get("product_id", item.get("productId", 0)))
            quantity = int(item.get("quantity", 0))
            if product_id > 0 and quantity > 0:
                normalized_items.append({"productId": product_id, "quantity": quantity})

        payload = json.dumps({"customerName": customer_name, "items": normalized_items})
        return self._call_facade("get_customer_checkout_context", payload)

    def list_transactions(self, limit=50):
        return self._call_admin_facade("list_transactions", {"limit": int(limit)})

    def dashboard_stats(self):
        raw = self._call_admin_facade("dashboard_stats")
        return {
            "total_products": raw.get("total_products", raw.get("totalProducts", 0)),
            "total_users": raw.get("total_users", raw.get("totalUsers", 0)),
            "total_transactions": raw.get("total_transactions", raw.get("totalTransactions", 0)),
            "total_permissions": raw.get("total_permissions", raw.get("totalPermissions", 0)),
            "monthly_sales": float(raw.get("monthly_sales", raw.get("monthlySales", 0.0))),
            "monthly_transactions": raw.get("monthly_transactions", raw.get("monthlyTransactions", 0)),
            "low_stock_products": raw.get("low_stock_products", raw.get("lowStockProducts", 0)),
        }

    def user_profile(self, nickname):
        raw = self._call_admin_facade("user_profile", {"nickname": nickname})
        if not raw:
            return None
        return {
            "user": raw.get("user"),
            "orders": raw.get("orders", raw.get("orderHistory", [])),
            "total_orders": raw.get("total_orders", raw.get("totalOrders", 0)),
            "total_spent": float(raw.get("total_spent", raw.get("totalSpent", 0.0))),
        }

    def process_order(
        self,
        customer_name,
        product_id,
        total_items,
        payment_method,
        city,
        customer_category,
        discount,
    ):
        products = self.list_products()
        target = next((p for p in products if int(p["id"]) == int(product_id)), None)
        if not target:
            raise ValueError(f"Product not found with ID: {product_id}")

        payload = {
            "id": 0,
            "date": datetime.now().isoformat(),
            "customerName": customer_name,
            "product": target["name"],
            "totalItems": int(total_items),
            "totalCost": float(target["price"]) * int(total_items),
            "paymentMethod": payment_method,
            "city": city,
            "discountApplied": 1 if float(discount) > 0 else 0,
            "customerCategory": customer_category,
            "discount": float(discount),
            "productDetails": {"id": int(product_id)},
        }
        return self._call_admin_facade("process_order", payload)

    def process_cart_order(
        self,
        customer_name,
        payment_method,
        city,
        items,
    ):
        normalized_items = []
        for item in items:
            product_id = int(item.get("product_id", 0))
            quantity = int(item.get("quantity", 0))
            if product_id > 0 and quantity > 0:
                normalized_items.append({"productId": product_id, "quantity": quantity})

        payload = json.dumps(
            {
                "customerName": customer_name,
                "paymentMethod": payment_method,
                "city": city,
                "items": normalized_items,
            }
        )
        return self._call_facade("process_cart", payload)
