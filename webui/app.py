import streamlit as st
import pandas as pd
import hashlib
import re
import unicodedata
from pathlib import Path
from urllib.parse import quote
from uuid import uuid4

from db import CloudStoreDB

st.set_page_config(page_title="CloudStore", layout="wide")
st.title("CloudStore")

ASSETS_DIR = Path(__file__).resolve().parent / "assets"
ICON_ASSETS_DIR = ASSETS_DIR / "icons"
PRODUCT_ASSETS_DIR = ASSETS_DIR / "products"
SUPPORTED_IMAGE_EXTS = (".png", ".jpg", ".jpeg", ".webp", ".svg")


@st.cache_resource
def _auth_sessions():
    return {}

def _get_browser_session_id():
    sid = st.query_params.get("sid")
    if isinstance(sid, list):
        return sid[0] if sid else None
    return sid

def _set_browser_session_id(sid):
    st.query_params["sid"] = sid

def _clear_browser_session_id():
    if "sid" in st.query_params:
        del st.query_params["sid"]

def _persist_auth_session(user, role, password):
    sid = uuid4().hex
    _auth_sessions()[sid] = {
        "user": user,
        "role": role,
        "password": password,
    }
    st.session_state.auth_sid = sid
    _set_browser_session_id(sid)

def _restore_auth_session_if_needed():
    if st.session_state.auth_user is not None:
        return

    sid = _get_browser_session_id()
    if not sid:
        return

    saved = _auth_sessions().get(sid)
    if not saved:
        _clear_browser_session_id()
        return

    st.session_state.auth_user = saved.get("user")
    st.session_state.auth_role = saved.get("role")
    st.session_state.auth_password = saved.get("password")
    st.session_state.auth_sid = sid

def _show_table(rows):
    if not rows:
        st.info("No data available")
        return
    st.dataframe(pd.DataFrame(rows), use_container_width=True)

def _inject_compact_shop_styles():
    st.markdown(
        """
        <style>
        /* Reduce vertical spacing between product cards */
        div[data-testid="stVerticalBlock"] > div:has(> div[data-testid="stVerticalBlockBorderWrapper"]) {
            margin-bottom: 0.35rem;
        }
        div[data-testid="stFormSubmitButton"] button {
            height: 2.35rem;
            min-height: 2.35rem;
            padding-top: 0.2rem;
            padding-bottom: 0.2rem;
            font-weight: 600;
        }
        </style>
        """,
        unsafe_allow_html=True,
    )

def _product_category(product):
    return product.get("description") or product.get("category") or "Uncategorized"

def _slugify(text):
    normalized = unicodedata.normalize("NFKD", str(text or ""))
    ascii_text = normalized.encode("ascii", "ignore").decode("ascii")
    return re.sub(r"[^a-z0-9]+", "-", ascii_text.lower()).strip("-")

def _user_icon_data_uri():
    svg = """
        <svg xmlns='http://www.w3.org/2000/svg' width='128' height='128' viewBox='0 0 128 128'>
            <circle cx='64' cy='64' r='62' fill='#f2f4f8' stroke='#d0d5dd' stroke-width='3'/>
            <circle cx='64' cy='46' r='22' fill='#98a2b3'/>
            <path d='M24 108c2-24 19-34 40-34s38 10 40 34' fill='#98a2b3'/>
        </svg>
        """.strip()
    return f"data:image/svg+xml;utf8,{quote(svg)}"

def _user_icon_source():
    for name in ("user", "customer", "profile", "avatar"):
        for ext in SUPPORTED_IMAGE_EXTS:
            path = ICON_ASSETS_DIR / f"{name}{ext}"
            if path.exists():
                return str(path)
    return _user_icon_data_uri()

def _product_image_data_uri(name, category):
    seed = f"{name}|{category}"
    color = hashlib.md5(seed.encode("utf-8")).hexdigest()[:6]
    initials = "".join(part[0] for part in str(name).split()[:2]).upper() or "P"
    svg = f"""
        <svg xmlns='http://www.w3.org/2000/svg' width='180' height='120' viewBox='0 0 180 120'>
            <rect x='0' y='0' width='180' height='120' rx='14' fill='#{color}'/>
            <rect x='8' y='8' width='164' height='104' rx='10' fill='rgba(255,255,255,0.20)'/>
            <text x='90' y='62' text-anchor='middle' font-size='34' font-family='Verdana, sans-serif' fill='white' font-weight='bold'>{initials}</text>
        </svg>
        """.strip()
    return f"data:image/svg+xml;utf8,{quote(svg)}"

def _product_image_source(product):
    product_id = product.get("id")
    name_slug = _slugify(product.get("name"))
    category_slug = _slugify(_product_category(product))
    candidates = []

    if product_id is not None:
        candidates.append(str(product_id))
    if name_slug:
        candidates.append(name_slug)
    if category_slug and name_slug:
        candidates.append(f"{category_slug}_{name_slug}")

    if PRODUCT_ASSETS_DIR.exists():
        files = [p for p in PRODUCT_ASSETS_DIR.iterdir() if p.is_file()]

        for candidate in candidates:
            for ext in SUPPORTED_IMAGE_EXTS:
                path = PRODUCT_ASSETS_DIR / f"{candidate}{ext}"
                if path.exists():
                    return str(path)

        lowered_candidates = {c.lower() for c in candidates}
        for path in files:
            if path.suffix.lower() not in SUPPORTED_IMAGE_EXTS:
                continue
            stem = path.stem.lower()
            filename = path.name.lower()
            if stem in lowered_candidates:
                return str(path)
            for candidate in lowered_candidates:
                if filename == f"{candidate}{path.suffix.lower()}":
                    return str(path)

    return _product_image_data_uri(product.get("name", "Product"), _product_category(product))


def _reset_session_after_logout():
    sid = st.session_state.get("auth_sid") or _get_browser_session_id()
    if sid:
        _auth_sessions().pop(sid, None)
    _clear_browser_session_id()

    st.session_state.auth_user = None
    st.session_state.auth_role = None
    st.session_state.auth_password = None
    st.session_state.auth_sid = None
    st.session_state.user_cart = {}


def _show_login(db):
    st.subheader("Login")
    st.caption("Access is role-based: customers see the shop, admins see the control panel")
    with st.form("login_form"):
        nickname = st.text_input("Nickname")
        password = st.text_input("Password", type="password")
        login = st.form_submit_button("Login")
        if login:
            try:
                auth_data = db.authenticate_user(nickname, password)
                st.session_state.auth_user = auth_data.get("user")
                st.session_state.auth_role = auth_data.get("role", "customer")
                st.session_state.auth_password = password
                _persist_auth_session(
                    st.session_state.auth_user,
                    st.session_state.auth_role,
                    st.session_state.auth_password,
                )
                st.success("Login successful")
                st.rerun()
            except Exception as e:
                st.error(str(e))


def _render_customer_view(db, current_user):
    _inject_compact_shop_styles()

    c1, c2 = st.columns([4, 1])
    c1.subheader(f"Customer Shop - {current_user.get('nickname', '')}")
    if c2.button("Logout", key="logout_customer", use_container_width=True, type="secondary"):
        _reset_session_after_logout()
        st.rerun()

    st.caption("Browse products by category, choose quantities and complete checkout")

    all_products = db.list_products()
    categories = db.list_product_categories()
    if not categories:
        categories = sorted({
            _product_category(p).strip()
            for p in all_products
            if _product_category(p).strip()
        }, key=str.lower)

    with st.sidebar:
        st.image(_user_icon_source(), width=74)
        st.markdown("### Account")
        st.write(f"Nickname: {current_user.get('nickname', '')}")
        permission = current_user.get("permission") or {}
        st.write(f"Role: {permission.get('category', 'Customer')}")
        st.divider()
        st.markdown("### Product categories")
        category_counts = {
            c: sum(1 for p in all_products if _product_category(p).strip().lower() == c.lower())
            for c in categories
        }

        category_options = ["All"] + categories
        selected_category = st.selectbox(
            "Choose a category",
            category_options,
            key="shop_category",
            format_func=lambda x: "All categories" if x == "All" else f"{x} ({category_counts.get(x, 0)})",
        )

    if selected_category == "All":
        products = all_products
    else:
        products = db.list_products_by_category(selected_category)

    shop_tab, cart_tab = st.tabs(["Catalog", "🛒 Cart"])

    with shop_tab:
        st.markdown(f"### Products in category: {selected_category}")

        if not products:
            st.info("No products found for this category")
        else:
            for product in products:
                product_id = int(product["id"])
                stock = int(product["stock"])
                price = float(product["price"])
                category = _product_category(product)

                with st.container(border=True):
                    p0, p1, p2 = st.columns([1.3, 5.3, 3.4], vertical_alignment="center")
                    p0.image(_product_image_source(product), width=120)
                    p1.markdown(f"**{product['name']}**")
                    p1.caption(f"{category}  |  EUR {price:.2f}  |  Stock {stock}")

                    with p2.form(f"shop_add_form_{product_id}"):
                        f1, f2 = st.columns([1.35, 1.65], vertical_alignment="bottom")
                        qty = f1.number_input(
                            "Quantity",
                            min_value=1,
                            max_value=max(stock, 1),
                            value=1,
                            key=f"shop_qty_{product_id}",
                            disabled=stock <= 0,
                        )
                        add_clicked = f2.form_submit_button(
                            "Add",
                            disabled=stock <= 0,
                            type="primary",
                            use_container_width=True,
                        )

                        if add_clicked:
                            existing_qty = st.session_state.user_cart.get(product_id, {}).get("quantity", 0)
                            requested_total = existing_qty + int(qty)
                            if requested_total > stock:
                                st.error(
                                    f"Cannot add quantity {int(qty)} for {product['name']}: requested {requested_total}, available {stock}"
                                )
                            else:
                                st.session_state.user_cart[product_id] = {
                                    "product_id": product_id,
                                    "name": product["name"],
                                    "category": category,
                                    "price": price,
                                    "quantity": requested_total,
                                    "stock": stock,
                                }
                                st.success(f"Added {int(qty)} x {product['name']} to cart")

    with cart_tab:
        st.markdown("### Cart")

        cart_items = list(st.session_state.user_cart.values())
        if not cart_items:
            st.info("Your cart is empty")
            return

        with st.expander("🛒 Open cart items", expanded=True):
            for item in cart_items:
                rc1, rc2 = st.columns([5, 1])
                rc1.write(
                    f"{item['name']} | qty {item['quantity']} | EUR {item['price']:.2f} each | line EUR {(item['price'] * item['quantity']):.2f}"
                )
                if rc2.button("Remove", key=f"shop_remove_{item['product_id']}", use_container_width=True):
                    del st.session_state.user_cart[item["product_id"]]
                    st.rerun()

        if st.button("Clear cart", type="secondary", use_container_width=True):
            st.session_state.user_cart = {}
            st.rerun()

        cart_rows = []
        subtotal = 0.0
        total_items = 0
        for item in cart_items:
            line_total = item["price"] * item["quantity"]
            subtotal += line_total
            total_items += item["quantity"]
            cart_rows.append(
                {
                    "product_id": item["product_id"],
                    "name": item["name"],
                    "category": item["category"],
                    "quantity": item["quantity"],
                    "unit_price": item["price"],
                    "line_total": line_total,
                }
            )

        _show_table(cart_rows)
        m1, m2 = st.columns(2)
        m1.metric("Cart items", total_items)
        m2.metric("Subtotal", f"EUR {subtotal:.2f}")

        permission = current_user.get("permission") or {}
        default_customer_category = permission.get("category", "Customer")
        customer_name = current_user.get("nickname", "")
        try:
            checkout_context = db.get_customer_checkout_context(customer_name, cart_items)
            default_customer_category = checkout_context.get("customerCategory", default_customer_category)
            db_discount = float(checkout_context.get("discount", 0.0))
            discount_source = checkout_context.get("discountSource", "recent_average_discount")
            sample_size = int(checkout_context.get("sampleSize", 0))
        except Exception as e:
            st.warning(f"Unable to retrieve discount from DB, using 0.0: {e}")
            db_discount = 0.0
            discount_source = "fallback_zero"
            sample_size = 0

        net_total = subtotal * (1 - db_discount)
        cctx1, cctx2, cctx3 = st.columns(3)
        cctx1.metric("Discount from DB", f"{db_discount * 100:.1f}%")
        cctx2.metric("Category", default_customer_category)
        cctx3.metric("Total after discount", f"EUR {net_total:.2f}")

        st.caption(
            f"Discount rule: weighted average of field 'discount' from the most recent DB transactions of the products currently in cart (sample={sample_size}, source={discount_source})."
        )

        st.markdown("#### Checkout")
        with st.form("shop_checkout_form"):
            customer_name = st.text_input("Customer", value=customer_name, disabled=True)
            payment_method = st.selectbox("Payment method", ["Credit Card", "Cash", "Bank Transfer"])
            city = st.text_input("City", value="Rome")
            st.text_input("Customer category (from DB)", value=default_customer_category, disabled=True)
            st.number_input("Discount (from DB)", min_value=0.0, max_value=1.0, value=db_discount, disabled=True)
            checkout = st.form_submit_button("Complete transaction", type="primary", use_container_width=True)

            if checkout:
                try:
                    result = db.process_cart_order(
                        customer_name=customer_name,
                        payment_method=payment_method,
                        city=city,
                        items=cart_items,
                    )
                    st.success("Transaction completed successfully")
                    s1, s2, s3 = st.columns(3)
                    s1.metric("Items processed", int(result.get("totalItems", 0)))
                    s2.metric("Total paid", f"EUR {float(result.get('cartTotal', 0.0)):.2f}")
                    s3.metric("Order lines", int(result.get("lines", 0)))
                    if result.get("transactions"):
                        st.markdown("#### Order summary")
                        summary_rows = [
                            {
                                "product": tx.get("product"),
                                "quantity": tx.get("totalItems"),
                                "line_total": tx.get("totalCost"),
                                "payment": tx.get("paymentMethod"),
                            }
                            for tx in result["transactions"]
                        ]
                        _show_table(summary_rows)
                    st.session_state.user_cart = {}
                except Exception as e:
                    st.error(str(e))


def _render_admin_view(db, current_user):
    db.set_auth_context(current_user.get("nickname"), st.session_state.auth_password)

    c1, c2 = st.columns([4, 1])
    c1.subheader(f"Admin Control Panel - {current_user.get('nickname', '')}")
    if c2.button("Logout", key="logout_admin"):
        _reset_session_after_logout()
        st.rerun()

    tabs = st.tabs([
        "Dashboard",
        "Orders",
        "Products",
        "Users",
        "Permissions",
        "Transactions",
        "User Profile",
    ])

    with tabs[0]:
        st.subheader("Aggregate Statistics")
        stats = db.dashboard_stats()
        c1, c2, c3, c4 = st.columns(4)
        c1.metric("Products", stats["total_products"])
        c2.metric("Users", stats["total_users"])
        c3.metric("Transactions", stats["total_transactions"])
        c4.metric("Permissions", stats["total_permissions"])
        c5, c6, c7 = st.columns(3)
        c5.metric("Monthly sales", f"EUR {stats['monthly_sales']:.2f}")
        c6.metric("Monthly transactions", stats["monthly_transactions"])
        c7.metric("Low-stock products", stats["low_stock_products"])

    with tabs[1]:
        st.subheader("Process Order (atomic)")
        products = db.list_products()
        if not products:
            st.warning("No products available. Add one in the Products tab.")
        else:
            product_map = {
                f"#{p['id']} - {p['name']} (stock {p['stock']}, EUR {float(p['price']):.2f})": p["id"]
                for p in products
            }
            with st.form("process_order_form"):
                customer_name = st.text_input("Customer", value="Mario Rossi")
                selected_product = st.selectbox("Product", list(product_map.keys()))
                total_items = st.number_input("Quantity", min_value=1, value=1)
                payment_method = st.selectbox("Payment method", ["Credit Card", "Cash", "Bank Transfer"])
                city = st.text_input("City", value="Rome")
                customer_category = st.text_input("Customer category", value="Regular")
                discount = st.number_input("Discount (0.0 - 1.0)", min_value=0.0, max_value=1.0, value=0.0)
                submitted = st.form_submit_button("Process order")

                if submitted:
                    try:
                        result = db.process_order(
                            customer_name=customer_name,
                            product_id=product_map[selected_product],
                            total_items=int(total_items),
                            payment_method=payment_method,
                            city=city,
                            customer_category=customer_category,
                            discount=float(discount),
                        )
                        st.success("Order processed successfully")
                        a1, a2, a3 = st.columns(3)
                        a1.metric("Items", int(result.get("totalItems", 0)))
                        a2.metric("Total", f"EUR {float(result.get('totalCost', 0.0)):.2f}")
                        a3.metric("Transaction ID", int(result.get("id", 0)))
                    except Exception as e:
                        st.error(str(e))

    with tabs[2]:
        st.subheader("Products")
        col_a, col_b = st.columns([1, 2])

        with col_a:
            st.markdown("### New product")
            with st.form("new_product_form"):
                name = st.text_input("Product name")
                category = st.text_input("Category")
                price = st.number_input("Price", min_value=0.0, value=1.0)
                stock = st.number_input("Stock", min_value=0, value=0)
                save_product = st.form_submit_button("Save product")
                if save_product:
                    try:
                        db.save_product(name, category, float(price), int(stock))
                        st.success("Product saved")
                    except Exception as e:
                        st.error(str(e))

            st.markdown("### Low stock")
            threshold = st.number_input("Threshold", min_value=0, value=10)
            if st.button("Show low stock"):
                _show_table(db.low_stock_products(int(threshold)))

        with col_b:
            st.markdown("### Product list")
            products = db.list_products()
            _show_table(products)

            if products:
                product_ids = [p["id"] for p in products]
                delete_id = st.selectbox("Delete product ID", product_ids)
                if st.button("Delete product"):
                    try:
                        db.delete_product(delete_id)
                        st.success("Product deleted")
                    except Exception as e:
                        st.error(str(e))

    with tabs[3]:
        st.subheader("Users")
        perms = db.list_permissions()
        perm_options = {f"#{p['id']} - {p['category']}": p["id"] for p in perms}

        with st.form("register_user_form"):
            nickname = st.text_input("Nickname")
            name = st.text_input("Name")
            surname = st.text_input("Surname")
            email = st.text_input("Email")
            password = st.text_input("Password", type="password")
            if perm_options:
                selected_perm = st.selectbox("Permission", list(perm_options.keys()))
            else:
                selected_perm = None
                st.warning("No permissions available: create one in the Permissions tab")
            register = st.form_submit_button("Register user")
            if register:
                if selected_perm is None:
                    st.error("Cannot register user without a permission")
                else:
                    try:
                        db.register_user(
                            nickname=nickname,
                            name=name,
                            surname=surname,
                            email=email,
                            password=password,
                            permission_id=perm_options[selected_perm],
                        )
                        st.success("User registered")
                    except Exception as e:
                        st.error(str(e))

        st.markdown("### User list")
        _show_table(db.list_users())

    with tabs[4]:
        st.subheader("Permissions")
        with st.form("new_permission_form"):
            category = st.text_input("Permission category")
            save_perm = st.form_submit_button("Create permission")
            if save_perm:
                try:
                    db.save_permission(category)
                    st.success("Permission created")
                except Exception as e:
                    st.error(str(e))

        st.markdown("### Permission list")
        _show_table(db.list_permissions())

    with tabs[5]:
        st.subheader("Recent transactions")
        limit = st.number_input("Limit", min_value=1, max_value=500, value=50)
        _show_table(db.list_transactions(int(limit)))

    with tabs[6]:
        st.subheader("User Profile")
        nickname = st.text_input("Customer nickname", value="Mario Rossi")
        if st.button("Load profile"):
            profile = db.user_profile(nickname)
            if not profile:
                st.info("User not found")
            else:
                st.markdown("### User data")
                user = profile["user"]
                st.write(f"Nickname: {user.get('nickname', '-')}")
                st.write(f"Name: {user.get('name', '-')}")
                st.write(f"Email: {user.get('email', '-')}")
                st.markdown("### Statistics")
                c1, c2 = st.columns(2)
                c1.metric("Total orders", profile["total_orders"])
                c2.metric("Total spent", f"EUR {profile['total_spent']:.2f}")
                st.markdown("### Order history")
                _show_table(profile["orders"])


def main():
    db = CloudStoreDB()

    if "user_cart" not in st.session_state:
        st.session_state.user_cart = {}
    if "auth_user" not in st.session_state:
        st.session_state.auth_user = None
    if "auth_role" not in st.session_state:
        st.session_state.auth_role = None
    if "auth_password" not in st.session_state:
        st.session_state.auth_password = None
    if "auth_sid" not in st.session_state:
        st.session_state.auth_sid = None

    _restore_auth_session_if_needed()

    try:
        db.fetch_one("SELECT 1 as ok")
    except Exception as e:
        st.error(f"DB connection failed: {e}")
        st.stop()

    if st.session_state.auth_user is None:
        _show_login(db)
        return

    if st.session_state.auth_role == "admin":
        if not st.session_state.auth_password:
            st.error("Missing admin session credentials, please login again")
            _reset_session_after_logout()
            st.rerun()
        _render_admin_view(db, st.session_state.auth_user)
    else:
        _render_customer_view(db, st.session_state.auth_user)


if __name__ == "__main__":
    main()
