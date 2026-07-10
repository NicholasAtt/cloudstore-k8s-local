import streamlit as st
import pandas as pd
import hashlib
import re
import unicodedata
from pathlib import Path
from urllib.parse import quote
from streamlit_autorefresh import st_autorefresh

from models.Linker import Linker

st.set_page_config(page_title="CloudStore", layout="wide")
st.title("CloudStore")

ASSETS_DIR = Path(__file__).resolve().parent / "assets"
ICON_ASSETS_DIR = ASSETS_DIR / "icons"
PRODUCT_ASSETS_DIR = ASSETS_DIR / "products"
SUPPORTED_IMAGE_EXTS = (".png", ".jpg", ".jpeg", ".webp", ".svg")
AUTO_REFRESH_INTERVAL_MS = 10000

def _qp_get_scalar(params, key):
    value = params.get(key)
    if isinstance(value, list):
        return value[0] if value else None
    return value

def _persist_auth_in_url(token, role, nickname):
    if not token:
        return
    try:
        st.query_params.clear()
        st.query_params["auth_token"] = token
        st.query_params["auth_role"] = role or "customer"
        st.query_params["auth_nickname"] = nickname or ""
    except Exception:
        try:
            st.experimental_set_query_params(
                auth_token=token,
                auth_role=role or "customer",
                auth_nickname=nickname or "",
            )
        except Exception:
            pass

def _clear_auth_from_url():
    try:
        st.query_params.clear()
    except Exception:
        try:
            st.experimental_set_query_params()
        except Exception:
            pass

def _restore_session_from_url():
    try:
        params = st.query_params
    except Exception:
        return False

    token = _qp_get_scalar(params, "auth_token")
    if not token:
        return False

    role = _qp_get_scalar(params, "auth_role") or "customer"
    nickname = _qp_get_scalar(params, "auth_nickname")

    try:
        linker = Linker(token=token)
        user = None
        if nickname:
            profile = linker.user_profile(nickname)
            if profile:
                user = profile.get("user")

        if not user:
            _clear_auth_from_url()
            return False

        st.session_state.auth_token = token
        st.session_state.auth_role = role
        st.session_state.auth_user = user
        st.session_state.linker = linker
        return True
    except Exception:
        _clear_auth_from_url()
        return False

def _show_table(rows):
    if not rows:
        st.info("No data available")
        return
    st.dataframe(pd.DataFrame(rows), use_container_width=True)

def _build_trending_items_df(rows):
    if not rows:
        return pd.DataFrame(columns=["Quantity"])

    frame = pd.DataFrame(rows)

    item_col = None
    for candidate in ("product", "productName", "name", "item", "product_name"):
        if candidate in frame.columns:
            item_col = candidate
            break

    if item_col is None and "productDetails" in frame.columns:
        frame["_product_name"] = frame["productDetails"].apply(
            lambda x: x.get("name") if isinstance(x, dict) else None
        )
        if frame["_product_name"].notna().any():
            item_col = "_product_name"

    qty_col = None
    for candidate in ("totalItems", "total_items", "quantity", "qty", "count"):
        if candidate in frame.columns:
            qty_col = candidate
            break

    if item_col is None:
        return pd.DataFrame(columns=["Quantity"])

    items = frame[item_col].fillna("Unknown item").astype(str)
    if qty_col is None:
        quantities = pd.Series([1] * len(frame), index=frame.index, dtype=float)
    else:
        quantities = pd.to_numeric(frame[qty_col], errors="coerce").fillna(0.0)

    plot_df = pd.DataFrame({"Item": items, "Quantity": quantities})
    plot_df = plot_df.groupby("Item", as_index=True)["Quantity"].sum().sort_values(ascending=False)
    return plot_df.to_frame()

def _render_trending_section(rows, empty_msg, missing_field_msg, ranking_caption, top_n):
    if not rows:
        st.info(empty_msg)
        return

    trending_df = _build_trending_items_df(rows)
    if trending_df.empty:
        st.warning(missing_field_msg)
        return

    top_item = trending_df.index[0]
    top_qty = float(trending_df.iloc[0]["Quantity"])
    total_qty = float(trending_df["Quantity"].sum())

    m1, m2, m3 = st.columns(3)
    m1.metric("Top item", top_item)
    m2.metric("Top quantity", int(top_qty) if top_qty.is_integer() else f"{top_qty:.2f}")
    m3.metric("Total quantity", int(total_qty) if total_qty.is_integer() else f"{total_qty:.2f}")

    st.bar_chart(trending_df.head(int(top_n)))
    st.caption(ranking_caption)

def _enable_auto_refresh(role):
    if role not in {"admin", "customer"}:
        return
    if role == "customer" and st.session_state.get("customer_pause_autorefresh"):
        return
    if st_autorefresh is None:
        return
    st_autorefresh(interval=AUTO_REFRESH_INTERVAL_MS, key="cloudstore_auto_refresh")

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
    return product.get("category") or "Uncategorized"

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
    current_linker = st.session_state.get("linker")
    if current_linker and st.session_state.get("auth_token"):
        try:
            current_linker.logout()
        except Exception:
            pass
    st.session_state.auth_user = None
    st.session_state.auth_role = None
    st.session_state.auth_token = None
    st.session_state.user_cart = {}
    st.session_state.customer_pause_autorefresh = False
    st.session_state.linker = None
    _clear_auth_from_url()

def _show_login(linker):
    st.subheader("Login")
    with st.form("login_form"):
        nickname = st.text_input("Nickname")
        password = st.text_input("Password", type="password")
        login = st.form_submit_button("Login")
        if login:
            try:
                auth_data = linker.authenticate_user(nickname, password)
                st.session_state.auth_user = auth_data.get("user")
                
                st.session_state.auth_role = auth_data.get("role", "customer")
                
                st.session_state.auth_token = auth_data.get("token")
                _persist_auth_in_url(
                    st.session_state.auth_token,
                    st.session_state.auth_role,
                    (st.session_state.auth_user or {}).get("nickname", nickname),
                )
                
                linker = Linker(token=st.session_state.auth_token)
                st.session_state.linker = linker
                
                st.success("Login successful")
                st.rerun()
            except Exception as e:
                st.error(str(e))

def _render_customer_view(linker, current_user):
    _inject_compact_shop_styles()

    c1, c2 = st.columns([4, 1])
    c1.subheader(f"Customer Shop - {current_user.get('nickname', '')}")
    if c2.button("Logout", key="logout_customer", use_container_width=True, type="secondary"):
        _reset_session_after_logout()
        st.rerun()

    st.caption("Browse products by category, choose quantities and complete checkout")

    all_products = linker.list_products()
    categories = linker.list_product_categories()
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
        products = linker.list_products_by_category(selected_category)

    # Inizializza il tab attivo se non esiste
    if st.session_state.get("customer_active_tab") not in {"Catalog", "Cart"}:
        st.session_state.customer_active_tab = "Catalog"

    # CSS per i tab stilizzati
    st.markdown(
        """
        <style>
        /* Container per i tab */
        div[data-testid="stRadio"] > div[role="radiogroup"] {
            display: flex;
            flex-direction: row;
            gap: 0;
            border-bottom: 2px solid #e0e0e0;
            padding-bottom: 0;
            margin-bottom: 1rem;
        }
        
        /* Stile per ogni tab */
        div[data-testid="stRadio"] label {
            border: none !important;
            border-bottom: 3px solid transparent !important;
            border-radius: 0 !important;
            padding: 0.5rem 1.5rem !important;
            background: transparent !important;
            margin: 0 !important;
            color: #666 !important;
            font-weight: 500 !important;
            cursor: pointer;
            transition: all 0.3s ease;
        }
        
        /* Tab attivo */
        div[data-testid="stRadio"] label[data-selected="true"] {
            background: transparent !important;
            color: #FF4B4B !important;
            border-bottom: 3px solid #FF4B4B !important;
            font-weight: 600 !important;
        }
        
        /* Hover sui tab */
        div[data-testid="stRadio"] label:hover {
            color: #FF4B4B !important;
            background: rgba(255, 75, 75, 0.05) !important;
        }
        </style>
        """,
        unsafe_allow_html=True,
    )

    active_tab = st.radio(
        "",
        ["Catalog", "Cart"],
        key="customer_active_tab",
        horizontal=True,
        label_visibility="collapsed",
    )

    if active_tab == "Catalog":
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
                            st.session_state.customer_pause_autorefresh = False
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

    else:
        st.markdown("### Cart")

        cart_items = list(st.session_state.user_cart.values())
        if not cart_items:
            st.info("Your cart is empty")
        else:
            with st.expander("🛒 Open cart items", expanded=True):
                for item in cart_items:
                    rc1, rc2 = st.columns([5, 1])
                    rc1.write(
                        f"{item['name']} | qty {item['quantity']} | EUR {item['price']:.2f} each | line EUR {(item['price'] * item['quantity']):.2f}"
                    )
                    if rc2.button("Remove", key=f"shop_remove_{item['product_id']}", use_container_width=True):
                        st.session_state.customer_pause_autorefresh = False
                        del st.session_state.user_cart[item["product_id"]]
                        st.rerun()

            if st.button("Clear cart", type="secondary", use_container_width=True):
                st.session_state.customer_pause_autorefresh = False
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
                checkout_context = linker.get_customer_checkout_context(customer_name, cart_items)
                default_customer_category = checkout_context.get("customerCategory", default_customer_category)
                db_discount = float(checkout_context.get("discount", 0.0))
                discount_source = checkout_context.get("discountSource", "recent_average_discount")
                sample_size = int(checkout_context.get("sampleSize", 0))
            except Exception as e:
                if "token" in str(e).lower():
                    raise
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
                    st.session_state.customer_pause_autorefresh = False
                    try:
                        result = linker.process_cart_order(
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
                        if "token" in str(e).lower():
                            raise
                        st.error(str(e))

def _render_admin_view(linker, current_user):
    c1, c2 = st.columns([4, 1])
    c1.subheader(f"Admin Control Panel - {current_user.get('nickname', '')}")
    if c2.button("Logout", key="logout_admin"):
        _reset_session_after_logout()
        st.rerun()

    tabs = st.tabs([
        "Overview",
        "Statistics",
        "Orders",
        "Products",
        "Users",
        "Transactions",
        "User Profile",
    ])

    with tabs[0]:
        st.subheader("Trending Items")
        limit = st.number_input("How many recent transactions", min_value=10, max_value=500, value=100, key="admin_overview_limit")
        top_n = st.number_input("Top items to show", min_value=3, max_value=30, value=10, key="admin_overview_top_n")

        try:
            recent_transactions = linker.list_transactions(int(limit))
            _render_trending_section(
                recent_transactions,
                empty_msg="No recent transactions available",
                missing_field_msg="Unable to build trending chart: product field not found in transactions",
                ranking_caption="Ranking based on total quantity purchased in recent transactions",
                top_n=int(top_n),
            )

            with st.expander("Show recent transactions table", expanded=False):
                _show_table(recent_transactions)
        except Exception as e:
            st.error(f"Failed to load recent transactions: {str(e)}")

    with tabs[1]:
        st.subheader("Aggregate Statistics")
        stats = linker.dashboard_stats()
        c1, c2, c3, c4 = st.columns(4)
        c1.metric("Products", stats["total_products"])
        c2.metric("Users", stats["total_users"])
        c3.metric("Transactions", stats["total_transactions"])
        c4.metric("Permissions", stats["total_permissions"])
        c5, c6, c7 = st.columns(3)
        c5.metric("Monthly sales", f"EUR {stats['monthly_sales']:.2f}")
        c6.metric("Monthly transactions", stats["monthly_transactions"])
        c7.metric("Low-stock products", stats["low_stock_products"])

    with tabs[2]:
        st.subheader("Process Order (atomic)")
        products = linker.list_products()
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
                        result = linker.process_order(
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

    with tabs[3]:
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
                        linker.save_product(name, category, float(price), int(stock))
                        st.success("Product saved")
                    except Exception as e:
                        st.error(str(e))

            st.markdown("### Low stock")
            threshold = st.number_input("Threshold", min_value=0, value=10)
            if st.button("Show low stock"):
                _show_table(linker.low_stock_products(int(threshold)))

        with col_b:
            st.markdown("### Product list")
            products = linker.list_products()
            _show_table(products)

            if products:
                product_ids = [p["id"] for p in products]
                delete_id = st.selectbox("Delete product ID", product_ids)
                if st.button("Delete product"):
                    try:
                        linker.delete_product(delete_id)
                        st.success("Product deleted")
                    except Exception as e:
                        st.error(str(e))

    with tabs[4]:
        st.subheader("Users")
        perms = linker.list_permissions()
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
                st.warning("No permissions available: create one from backend/admin setup")
            register = st.form_submit_button("Register user")
            if register:
                if selected_perm is None:
                    st.error("Cannot register user without a permission")
                else:
                    try:
                        linker.register_user(
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
        _show_table(linker.list_users())

    with tabs[5]:
        st.subheader("Recent transactions")
        limit = st.number_input("Limit", min_value=1, max_value=500, value=50)
        _show_table(linker.list_transactions(int(limit)))

    with tabs[6]:
        st.subheader("User Profile")
        nickname = st.text_input("Customer nickname", value="Mario Rossi")
        if st.button("Load profile"):
            profile = linker.user_profile(nickname)
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

def _render_seller_view(linker, current_user):
    """Seller dashboard and inventory management."""
    c1, c2 = st.columns([4, 1])
    c1.subheader(f"Seller Dashboard - {current_user.get('nickname', '')}")
    if c2.button("Logout", key="logout_seller", use_container_width=True, type="secondary"):
        _reset_session_after_logout()
        st.rerun()

    st.caption("Manage your inventory, track sales, and view customer insights")

    with st.sidebar:
        st.image(_user_icon_source(), width=74)
        st.markdown("### Seller Info")
        st.write(f"Nickname: {current_user.get('nickname', '')}")
        st.write(f"Name: {current_user.get('name', '')}")
        st.write(f"Email: {current_user.get('email', '')}")

    tabs = st.tabs([
        "Overview",
        "Statistics",
        "Inventory",
        "Sales Orders",
        "Top Customers",
    ])

    with tabs[0]:
        st.subheader("Trending Items")
        limit = st.number_input(
            "How many recent sales orders",
            min_value=10,
            max_value=500,
            value=100,
            key="seller_overview_limit",
        )
        top_n = st.number_input("Top items to show", min_value=3, max_value=30, value=10, key="seller_overview_top_n")

        try:
            recent_orders = linker.get_seller_sales_orders(int(limit))
            _render_trending_section(
                recent_orders,
                empty_msg="No sales orders available",
                missing_field_msg="Unable to build trending chart: product field not found in sales orders",
                ranking_caption="Ranking based on total quantity sold in recent orders",
                top_n=int(top_n),
            )

            with st.expander("Show recent sales table", expanded=False):
                _show_table(recent_orders)
        except Exception as e:
            if "token" in str(e).lower():
                raise
            st.error(f"Failed to load recent sales: {str(e)}")

    with tabs[1]:
        st.subheader("Sales Overview")
        try:
            stats = linker.get_seller_dashboard_stats()
            c1, c2, c3, c4 = st.columns(4)
            c1.metric("Total Revenue", f"EUR {stats['total_revenue']:.2f}")
            c2.metric("Orders Received", stats["total_orders"])
            c3.metric("Avg Order Value", f"EUR {stats['average_order_value']:.2f}")
            c4.metric("Products Sold", stats["products_sold"])
            
            c5, c6 = st.columns(2)
            c5.metric("Total Sales Volume", f"EUR {stats['total_sales']:.2f}")
            c6.metric("Low Stock Products", stats["low_stock_products"])
        except Exception as e:
            if "token" in str(e).lower():
                raise
            st.error(f"Failed to load dashboard stats: {str(e)}")

    with tabs[2]:
        st.subheader("Inventory Management")
        try:
            products = linker.get_seller_products()
            if not products:
                st.info("You don't have any products yet")
            else:
                for product in products:
                    product_id = int(product["id"])
                    stock = int(product["stock"])
                    price = float(product["price"])
                    category = _product_category(product)

                    with st.container(border=True):
                        p0, p1, p2 = st.columns([1.3, 5, 3.7], vertical_alignment="center")
                        p0.image(_product_image_source(product), width=100)
                        
                        p1.markdown(f"**{product['name']}**")
                        p1.caption(f"{category} | EUR {price:.2f}")
                        
                        with p2.form(f"stock_update_form_{product_id}"):
                            f1, f2 = st.columns([1.5, 1.5], vertical_alignment="bottom")
                            new_stock = f1.number_input(
                                "Stock",
                                min_value=0,
                                value=stock,
                                key=f"stock_input_{product_id}",
                            )
                            update_clicked = f2.form_submit_button(
                                "Update",
                                type="primary",
                                use_container_width=True,
                            )
                            
                            if update_clicked and new_stock != stock:
                                try:
                                    linker.update_seller_product_stock(product_id, int(new_stock))
                                    st.success(f"Stock updated to {int(new_stock)}")
                                    st.rerun()
                                except Exception as e:
                                    if "token" in str(e).lower():
                                        raise
                                    st.error(f"Failed to update stock: {str(e)}")
        except Exception as e:
            if "token" in str(e).lower():
                raise
            st.error(f"Failed to load inventory: {str(e)}")

    with tabs[3]:
        st.subheader("Recent Sales Orders")
        try:
            limit = st.number_input("Show last", min_value=5, max_value=500, value=50, key="seller_orders_limit")
            orders = linker.get_seller_sales_orders(int(limit))
            
            if not orders:
                st.info("No sales orders yet")
            else:
                display_orders = []
                total_revenue = 0.0
                for order in orders:
                    total_revenue += float(order.get("totalCost", 0.0))
                    display_orders.append({
                        "Order ID": order.get("id"),
                        "Customer": order.get("customerName"),
                        "Product": order.get("product"),
                        "Quantity": order.get("totalItems"),
                        "Total": f"EUR {float(order.get('totalCost', 0.0)):.2f}",
                        "Payment": order.get("paymentMethod"),
                        "City": order.get("city"),
                    })
                
                _show_table(display_orders)
                st.metric("Total Revenue from Displayed Orders", f"EUR {total_revenue:.2f}")
        except Exception as e:
            if "token" in str(e).lower():
                raise
            st.error(f"Failed to load sales orders: {str(e)}")

    with tabs[4]:
        st.subheader("Top Customers")
        try:
            limit = st.number_input("Show top", min_value=5, max_value=20, value=5, key="seller_customers_limit")
            customers = linker.get_seller_top_customers(int(limit))
            
            if not customers:
                st.info("No customer data yet")
            else:
                display_customers = []
                for customer in customers:
                    display_customers.append({
                        "Customer": customer.get("customerName"),
                        "Total Orders": customer.get("orderCount", 0),
                        "Total Spent": f"EUR {float(customer.get('totalSpent', 0.0)):.2f}",
                        "Last Order": customer.get("lastOrderDate", "N/A"),
                    })
                
                _show_table(display_customers)
        except Exception as e:
            if "token" in str(e).lower():
                raise
            st.error(f"Failed to load customer data: {str(e)}")

def main():
    if "user_cart" not in st.session_state:
        st.session_state.user_cart = {}
    if "auth_user" not in st.session_state:
        st.session_state.auth_user = None
    if "auth_role" not in st.session_state:
        st.session_state.auth_role = "customer"
    if "auth_token" not in st.session_state:
        st.session_state.auth_token = None
    if "customer_pause_autorefresh" not in st.session_state:
        st.session_state.customer_pause_autorefresh = False

    if st.session_state.auth_user is None and not st.session_state.auth_token:
        _restore_session_from_url()

    token = st.session_state.get("auth_token")
    linker = Linker(token=token) if token else Linker()

    try:
        linker.fetch_one("SELECT 1 as ok")
    except Exception as e:
        st.error(f"Linker connection failed: {e}")
        st.stop()

    if st.session_state.auth_user is None:
        _show_login(linker)
        return

    try:
        role = st.session_state.get("auth_role", "customer")
        _enable_auto_refresh(role)
        
        VIEWS = {
            "customer": _render_customer_view,
            "admin": _render_admin_view,
            "seller": _render_seller_view,
        }

        view_func = VIEWS.get(role, _render_customer_view)
            
        if role != "customer" and not st.session_state.auth_token:
            st.error(f"Missing {role} session credentials, please login again")
            _reset_session_after_logout()
            st.rerun()
            
        view_func(linker, st.session_state.auth_user)
    except Exception as e:
        if "token" in str(e).lower():
            _reset_session_after_logout()
            st.warning("Session expired. Please login again.")
            st.rerun()
        raise

if __name__ == "__main__":
    main()
