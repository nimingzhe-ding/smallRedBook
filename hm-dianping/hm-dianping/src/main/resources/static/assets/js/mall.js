(function() {

// ------------------------------
// Mall product, cart, and order workflow
// ------------------------------

// NOTE: normalizeProduct and normalizeShop are already in utils.js, skipped here to avoid duplication.

function setMallActive(active) {
  document.querySelectorAll("#mobileMall").forEach(item => item.classList.toggle("is-active", active));
  if (active) {
    setMobileTabActive("mall");
    document.querySelectorAll("[data-feed]").forEach(item => item.classList.remove("is-active"));
  }
}

function setVideoActive(active) {
  document.querySelectorAll("#mobileVideo").forEach(item => item.classList.toggle("is-active", active));
  if (active) {
    setMobileTabActive(null);
    document.querySelectorAll("[data-feed]").forEach(item => item.classList.remove("is-active"));
  }
}

function showContentArea() {
  els.contentArea.hidden = false;
  els.mallArea.hidden = true;
  els.videoArea.hidden = true;
  setMallActive(false);
  setVideoActive(false);
  pauseImmersiveVideos();
  closeDanmakuSource();
}

function switchMall() {
  state.mode = "mall";
  state.mallQuery = els.search.value.trim();
  els.contentArea.hidden = true;
  els.videoArea.hidden = true;
  els.mallArea.hidden = false;
  setMallActive(true);
  setVideoActive(false);
  pauseImmersiveVideos();
  closeDanmakuSource();
  hideStatus();
  loadProducts();
}

function switchVideo() {
  state.mode = "video";
  els.contentArea.hidden = true;
  els.mallArea.hidden = true;
  els.videoArea.hidden = false;
  setMallActive(false);
  setVideoActive(true);
  hideStatus();
  renderVideoFeed();
  setTimeout(playCurrentImmersiveVideo, 80);
}

async function loadProducts() {
  els.productGrid.innerHTML = `<p class="empty-text mall-empty">正在加载商城商品...</p>`;
  const params = new URLSearchParams({ current: "1", category: state.mallCategory });
  if (state.mallQuery) params.set("query", state.mallQuery);
  try {
    const data = await request(`/mall/products?${params.toString()}`);
    const products = Array.isArray(data) ? data.map(normalizeProduct) : [];
    state.mallProducts = products;
    renderProducts(products);
  } catch {
    state.mallProducts = [];
    els.productGrid.innerHTML = `<p class="empty-text mall-empty">商城接口暂时不可用，请先执行商城数据库脚本并重启后端。</p>`;
  }
}

function renderProducts(products) {
  if (!products.length) {
    els.productGrid.innerHTML = `<p class="empty-text mall-empty">这个类目暂时没有商品。</p>`;
    return;
  }
  els.productGrid.innerHTML = products.map(product => `
    <article class="product-card">
      <button type="button" data-product-id="${product.id}">
        <div class="product-image-wrap">
          <img class="product-image" src="${normalizeImage(product.image)}" alt="${escapeHtml(product.title)}" loading="lazy">
          <span>已售 ${product.sold}</span>
        </div>
        <div class="product-body">
          <h2>${escapeHtml(product.title)}</h2>
          <p>${escapeHtml(product.subTitle)}</p>
          <div class="product-row">
            <strong>¥${formatMoney(product.price)}</strong>
            ${product.originPrice ? `<small>¥${formatMoney(product.originPrice)}</small>` : ""}
          </div>
        </div>
      </button>
    </article>
  `).join("");
  els.productGrid.querySelectorAll("[data-product-id]").forEach(button => {
    button.addEventListener("click", () => openProduct(button.dataset.productId));
  });
}

function ensureProductGuide(product) {
  let panel = document.querySelector("#productAiGuide");
  if (!panel) {
    document.querySelector("#productDialogStock").insertAdjacentHTML("afterend", `
      <section class="ai-inline-card product-ai-guide" id="productAiGuide">
        <strong>购买建议</strong>
        <input id="productAiQuestion" placeholder="比如：这个适合送女朋友吗？">
        <p id="productAiAnswer">可以直接问这件商品适不适合你的场景。</p>
      </section>
    `);
    document.querySelector("#productAiQuestion").addEventListener("keydown", event => {
      if (event.key === "Enter") {
        event.preventDefault();
        askProductGuide();
      }
    });
  }
  document.querySelector("#productAiQuestion").value = "";
  document.querySelector("#productAiAnswer").textContent = product.merchant
    ? `来自 ${product.merchant.name || "商家"}，可结合送礼、自用、预算来问。`
    : "可以直接问这件商品适不适合你的场景。";
}

async function askProductGuide() {
  const input = document.querySelector("#productAiQuestion");
  const answer = document.querySelector("#productAiAnswer");
  const question = input?.value?.trim();
  if (!question || !state.currentProduct) return;
  answer.textContent = "正在结合商品、评价和优惠信息判断...";
  try {
    const data = await aiFlow("/ai/flow/shopping-guide", {
      productId: state.currentProduct.id,
      merchantId: state.currentProduct.merchant?.id || state.currentProduct.merchantId || null,
      voucherId: state.selectedVoucherId || state.productVouchers?.[0]?.id || null,
      query: question,
      scenario: "product",
      content: `${state.currentProduct.title} ${state.currentProduct.subTitle}`
    });
    answer.textContent = data.answer || "暂时没有生成有效建议。";
  } catch {
    answer.textContent = "导购建议暂时不可用，可以先看价格、库存和评价。";
  }
}

async function loadMallVouchers(productId) {
  try {
    const vouchers = await request(`/voucher/mall/product/${productId}`);
    state.productVouchers = Array.isArray(vouchers) ? vouchers : [];
  } catch {
    state.productVouchers = [];
  }
  renderMallVouchers(state.productVouchers);
}

async function addToCart(productId, quantity = 1) {
  try {
    await request("/mall/cart", {
      method: "POST",
      body: JSON.stringify({ productId: Number(productId), quantity })
    });
    showStatus("已加入购物车，可以继续逛或去购物车下单。");
  } catch (error) {
    showStatus(error.message || "加入购物车失败，请检查登录状态和库存。");
  }
}

async function createMallOrder(payload) {
  return request("/mall/orders", {
    method: "POST",
    body: JSON.stringify(payload)
  });
}

async function openCartDialog() {
  if (!requireLoginThen("cart")) return;
  document.querySelector("#cartDialogTitle").textContent = "购物车";
  els.cartList.innerHTML = `<p class="empty-text">正在加载购物车...</p>`;
  els.cartDialog.showModal();
  try {
    const items = await request("/mall/cart");
    renderCartItems(Array.isArray(items) ? items : []);
  } catch {
    els.cartList.innerHTML = `<p class="empty-text">购物车加载失败，请确认已经登录。</p>`;
  }
}

function renderCartItems(items) {
  state.cartItems = Array.isArray(items) ? items : [];
  if (!items.length) {
    els.cartList.innerHTML = `<p class="empty-text">购物车还是空的，先去商城挑一件。</p>`;
    return;
  }
  els.cartList.innerHTML = items.map(item => `
    <article class="cart-item">
      <img src="${normalizeImage(item.image)}" alt="${escapeHtml(item.title)}">
      <div>
        <strong>${escapeHtml(item.title)}</strong>
        <span>¥${formatMoney(item.price)} × ${item.quantity}</span>
        <small>小计 ¥${formatMoney(item.totalAmount)}</small>
      </div>
      <div class="cart-actions">
        <button class="publish-button" type="button" data-cart-order="${item.id}">下单</button>
        <button class="ghost-button" type="button" data-cart-remove="${item.id}">删除</button>
      </div>
    </article>
  `).join("");
  els.cartList.querySelectorAll("[data-cart-order]").forEach(button => {
    button.addEventListener("click", () => orderFromCart(button.dataset.cartOrder));
  });
  els.cartList.querySelectorAll("[data-cart-remove]").forEach(button => {
    button.addEventListener("click", () => removeCartItem(button.dataset.cartRemove));
  });
}

async function removeCartItem(cartItemId) {
  try {
    await request(`/mall/cart/${cartItemId}`, { method: "DELETE" });
    openCartDialog();
  } catch {
    showStatus("删除购物车商品失败，请稍后再试。");
  }
}

async function openOrdersDialog(focusOrderId = null) {
  if (!requireLoginThen("orders")) return;
  document.querySelector("#cartDialogTitle").textContent = "我的订单";
  els.cartList.innerHTML = `<p class="empty-text">正在加载订单...</p>`;
  els.cartDialog.showModal();
  try {
    const orders = await request("/mall/orders");
    renderOrders(Array.isArray(orders) ? orders : [], focusOrderId);
  } catch {
    els.cartList.innerHTML = `<p class="empty-text">订单加载失败，请确认已经登录。</p>`;
  }
}

function mallOrderStatus(status) {
  return {
    1: "待支付",
    2: "已支付",
    3: "待发货",
    4: "已发货",
    5: "已完成",
    6: "已取消",
    7: "退款中",
    8: "已退款"
  }[Number(status)] || "处理中";
}

async function payMallOrder(orderId) {
  return request(`/mall/orders/${orderId}/pay`, { method: "POST" });
}

function renderOrders(orders, focusOrderId = null) {
  if (!orders.length) {
    els.cartList.innerHTML = `<p class="empty-text">还没有商城订单。</p>`;
    return;
  }
  els.cartList.innerHTML = orders.map(order => `
    <article class="cart-item order-item${String(order.id) === String(focusOrderId) ? " is-highlight" : ""}" data-order-id="${order.id}">
      <img src="${normalizeImage(order.productImage)}" alt="${escapeHtml(order.productTitle)}">
      <div>
        <strong>${escapeHtml(order.productTitle)}</strong>
        <span>订单号 ${order.id}</span>
        <small>¥${formatMoney(order.totalAmount)} · ${mallOrderStatus(order.status)}</small>
        <div class="order-ai-service">
          <input data-order-ai-input="${order.id}" data-order-product="${order.productId || ""}" placeholder="问订单、退款、物流问题">
          <p data-order-ai-answer="${order.id}"></p>
        </div>
      </div>
      ${Number(order.status) === 1 ? `
        <div class="cart-actions">
          <button class="publish-button" type="button" data-order-pay="${order.id}">去支付</button>
        </div>
      ` : ""}
    </article>
  `).join("");
  els.cartList.querySelectorAll("[data-order-pay]").forEach(button => {
    button.addEventListener("click", () => payOrderFromList(button.dataset.orderPay));
  });
  els.cartList.querySelectorAll("[data-order-ai-input]").forEach(input => {
    input.addEventListener("keydown", event => {
      if (event.key === "Enter") {
        event.preventDefault();
        askOrderService(input.dataset.orderAiInput, input.value);
      }
    });
  });
  if (focusOrderId) {
    setTimeout(() => {
      els.cartList.querySelector(`[data-order-id="${focusOrderId}"]`)?.scrollIntoView({ block: "center" });
    }, 50);
  }
}

async function askOrderService(orderId, question) {
  const answer = document.querySelector(`[data-order-ai-answer="${orderId}"]`);
  if (!question?.trim() || !answer) return;
  const input = document.querySelector(`[data-order-ai-input="${orderId}"]`);
  answer.textContent = "正在查询订单并生成客服回复...";
  try {
    answer.textContent = await fetchCustomerServiceAnswer(question, {
      orderId: Number(orderId),
      productId: Number(input?.dataset.orderProduct || 0) || null,
      scenario: "order"
    });
  } catch {
    answer.textContent = "客服助手暂时不可用，可以稍后再试。";
  }
}

async function payOrderFromList(orderId) {
  try {
    await payMallOrder(orderId);
    showStatus(`付款成功，订单号：${orderId}`);
    openOrdersDialog();
  } catch (error) {
    showStatus(error.message || "支付失败，请稍后再试。");
  }
}

// ------------------------------
// 商品详情与结算页：接入 SKU、地址、优惠券、评价、商家和 AI 导购
// ------------------------------

async function openProduct(productId) {
  let product = state.mallProducts.find(item => String(item.id) === String(productId));
  try {
    const detail = await request(`/mall/products/${productId}`);
    product = normalizeProduct(detail?.product || detail);
    product.skus = Array.isArray(detail?.skus) ? detail.skus : [];
    product.reviews = Array.isArray(detail?.reviews) ? detail.reviews : [];
    product.coupons = Array.isArray(detail?.coupons) ? detail.coupons : [];
    product.merchant = detail?.merchant || null;
    product.categoryInfo = detail?.category || null;
    product.subCategoryInfo = detail?.subCategory || null;
    product.isFavorite = Boolean(detail?.isFavorite);
  } catch {
    if (product) product = normalizeProduct(product);
  }
  if (!product) return;
  state.currentProduct = product;
  state.customerServiceContext = buildCustomerServiceContext({
    scenario: "product",
    productId: product.id,
    merchantId: product.merchant?.id || product.merchantId || null,
    voucherId: state.selectedVoucherId || product.coupons?.[0]?.id || null
  });
  state.selectedSkuId = product.skus?.[0]?.id ? String(product.skus[0].id) : null;
  state.checkoutQuantity = 1;
  state.selectedVoucherId = null;
  state.productVouchers = product.coupons || [];
  document.querySelector("#productDialogTitle").textContent = product.title;
  document.querySelector("#productDialogImage").src = normalizeImage(product.image);
  document.querySelector("#productDialogSub").textContent = product.subTitle;
  renderProductDetail(product);
  renderMallVouchers(state.productVouchers);
  ensureProductGuide(product);
  els.productDialog.showModal();
}

function renderProductDetail(product) {
  const sku = currentProductSku();
  document.querySelector("#productDialogPrice").textContent = `¥${formatMoney(sku?.price || product.price)}`;
  document.querySelector("#productDialogStock").textContent = `库存 ${sku?.stock ?? product.stock} · 已售 ${sku?.sold ?? product.sold}`;
  let container = document.querySelector("#productDetailSections");
  if (!container) {
    document.querySelector("#productDialogStock").insertAdjacentHTML("afterend", `<div id="productDetailSections"></div>`);
    container = document.querySelector("#productDetailSections");
  }
  const images = (product.images?.length ? product.images : [product.image]).filter(Boolean);
  container.innerHTML = `
    <div class="product-gallery-thumbs">
      ${images.map((image, index) => `
        <button type="button" class="${index === 0 ? "is-active" : ""}" data-product-image="${escapeHtml(image)}">
          <img src="${normalizeImage(image)}" alt="">
        </button>
      `).join("")}
    </div>
    ${renderSkuSelector(product)}
    ${renderProductMerchant(product)}
    ${renderProductReviews(product)}
  `;
  container.querySelectorAll("[data-product-image]").forEach(button => {
    button.addEventListener("click", () => {
      document.querySelector("#productDialogImage").src = normalizeImage(button.dataset.productImage);
      container.querySelectorAll("[data-product-image]").forEach(item => item.classList.toggle("is-active", item === button));
    });
  });
  container.querySelectorAll("[data-sku-id]").forEach(button => {
    button.addEventListener("click", () => {
      state.selectedSkuId = button.dataset.skuId;
      renderProductDetail(state.currentProduct);
      renderMallVouchers(state.productVouchers);
    });
  });
  container.querySelector("[data-product-favorite]")?.addEventListener("click", () => toggleMallFavorite("PRODUCT", product.id));
  container.querySelector("[data-shop-favorite]")?.addEventListener("click", () => product.merchant && toggleMallFavorite("SHOP", product.merchant.id));
}

function renderSkuSelector(product) {
  if (!product.skus?.length) {
    return `<section class="product-detail-section"><strong>规格</strong><p>默认规格</p></section>`;
  }
  return `
    <section class="product-detail-section">
      <strong>规格</strong>
      <div class="sku-options">
        ${product.skus.map(sku => `
          <button type="button" class="${String(sku.id) === String(state.selectedSkuId) ? "is-active" : ""}" data-sku-id="${sku.id}">
            <span>${escapeHtml(sku.skuName || formatSkuSpecs(sku.specs) || "默认规格")}</span>
            <small>¥${formatMoney(sku.price)} · 库存 ${sku.stock || 0}</small>
          </button>
        `).join("")}
      </div>
    </section>
  `;
}

function renderProductMerchant(product) {
  const merchant = product.merchant;
  if (!merchant) return "";
  return `
    <section class="product-detail-section merchant-mini">
      <strong>商家</strong>
      <div>
        <img src="${normalizeImage(merchant.avatar || product.image)}" alt="">
        <span>
          <b>${escapeHtml(merchant.name || "内容商家")}</b>
          <small>${escapeHtml(merchant.description || merchant.address || "商品、优惠券和售后由商家提供")}</small>
        </span>
      </div>
      <button type="button" data-shop-favorite="${merchant.id}">收藏店铺</button>
    </section>
  `;
}

function renderProductReviews(product) {
  const reviews = product.reviews || [];
  return `
    <section class="product-detail-section">
      <strong>评价 ${reviews.length ? `(${reviews.length})` : ""}</strong>
      ${reviews.length ? reviews.slice(0, 3).map(review => `
        <article class="product-review">
          <span>${"★".repeat(Number(review.rating || 5))}</span>
          <p>${escapeHtml(review.content || "用户暂未填写评价内容")}</p>
          ${review.images ? `<small>${escapeHtml(review.images)}</small>` : ""}
        </article>
      `).join("") : `<p>还没有评价，购买后可以发布图文评价。</p>`}
      <button type="button" data-product-favorite="${product.id}">${product.isFavorite ? "已收藏商品" : "收藏商品"}</button>
    </section>
  `;
}

function currentProductSku() {
  return state.currentProduct?.skus?.find(sku => String(sku.id) === String(state.selectedSkuId)) || null;
}

function formatSkuSpecs(specs) {
  if (!specs) return "";
  try {
    const parsed = JSON.parse(specs);
    return Object.entries(parsed).map(([key, value]) => `${key}:${value}`).join(" ");
  } catch {
    return String(specs);
  }
}

async function toggleMallFavorite(targetType, targetId) {
  if (!requireLogin()) return;
  try {
    const result = await request(`/mall/favorites?targetType=${encodeURIComponent(targetType)}&targetId=${targetId}`, { method: "POST" });
    if (targetType === "PRODUCT" && state.currentProduct) state.currentProduct.isFavorite = Boolean(result);
    showStatus(result ? "收藏成功" : "已取消收藏");
    if (state.currentProduct) renderProductDetail(state.currentProduct);
  } catch (error) {
    showStatus(error.message || "收藏操作失败");
  }
}

function renderMallVouchers(vouchers) {
  if (!els.mallVoucherList) return;
  state.productVouchers = Array.isArray(vouchers) ? vouchers : [];
  if (!state.productVouchers.length) {
    els.mallVoucherList.innerHTML = `<p class="empty-text">暂无可用优惠券，结算时会自动计算最优优惠。</p>`;
    return;
  }
  els.mallVoucherList.innerHTML = state.productVouchers.map(voucher => `
    <article class="voucher-item mall-voucher-item">
      <div>
        <strong>${escapeHtml(voucher.title || "商城优惠券")}</strong>
        <span>${escapeHtml(voucher.subTitle || voucher.rules || couponScopeLabel(voucher))}</span>
      </div>
      <button type="button" data-mall-voucher="${voucher.id}">
        ${state.selectedVoucherId === String(voucher.id) ? "已选择" : "选择"}
        <small>满 ¥${formatMoney(voucher.payValue)} 减 ¥${formatMoney(voucher.actualValue)}</small>
      </button>
    </article>
  `).join("");
  els.mallVoucherList.querySelectorAll("[data-mall-voucher]").forEach(button => {
    button.addEventListener("click", () => {
      state.selectedVoucherId = state.selectedVoucherId === button.dataset.mallVoucher ? null : button.dataset.mallVoucher;
      renderMallVouchers(state.productVouchers);
    });
  });
}

function couponScopeLabel(voucher) {
  return {
    PLATFORM: "平台券",
    SHOP: "店铺券",
    PRODUCT: "商品券",
    CATEGORY: "类目券"
  }[String(voucher.scopeType || "").toUpperCase()] || "下单可用";
}

async function addCurrentProductToCart() {
  if (!state.currentProduct || !requireLogin()) return;
  await addToCart(state.currentProduct.id, state.checkoutQuantity || 1);
}

async function buyCurrentProductNow() {
  if (!state.currentProduct || !requireLogin()) return;
  openCheckout({
    source: "product",
    product: state.currentProduct,
    productId: state.currentProduct.id,
    skuId: state.selectedSkuId ? Number(state.selectedSkuId) : null,
    quantity: state.checkoutQuantity || 1,
    voucherId: state.selectedVoucherId ? Number(state.selectedVoucherId) : null
  });
}

async function buyProductNow(productId) {
  if (!requireLogin()) return;
  await openProduct(productId);
  await buyCurrentProductNow();
}

async function orderFromCart(cartItemId) {
  if (!requireLogin()) return;
  const item = state.cartItems.find(row => String(row.id) === String(cartItemId));
  const product = item ? normalizeProduct({
    id: item.productId,
    title: item.title,
    subTitle: "购物车商品",
    images: item.image,
    price: item.price,
    stock: item.stock || item.quantity,
    sold: 0
  }) : null;
  openCheckout({
    source: "cart",
    cartItemId: Number(cartItemId),
    product,
    productId: product?.id,
    quantity: Number(item?.quantity || 1)
  });
}

async function openCheckout(draft) {
  state.checkoutDraft = draft;
  state.checkoutQuantity = draft.quantity || 1;
  if (draft.source === "cart") {
    state.selectedVoucherId = null;
    state.productVouchers = [];
  }
  await loadAddressesForCheckout();
  renderCheckout();
  els.checkoutDialog.showModal();
}

async function loadAddressesForCheckout() {
  try {
    state.addresses = await request("/mall/addresses");
  } catch {
    state.addresses = [];
  }
  const selected = state.addresses.find(address => String(address.id) === String(state.selectedAddressId))
    || state.addresses.find(address => address.defaultFlag)
    || state.addresses[0];
  state.selectedAddressId = selected?.id || null;
}

function renderCheckout() {
  const draft = state.checkoutDraft;
  const product = draft.product || state.currentProduct;
  const sku = draft.skuId && product?.skus ? product.skus.find(item => String(item.id) === String(draft.skuId)) : null;
  const unitPrice = Number(sku?.price || product?.price || 0);
  const quantity = Math.max(1, Number(state.checkoutQuantity || draft.quantity || 1));
  const coupon = state.productVouchers.find(item => String(item.id) === String(draft.voucherId || state.selectedVoucherId));
  const discount = coupon && unitPrice * quantity >= Number(coupon.payValue || 0) ? Number(coupon.actualValue || 0) : 0;
  const total = Math.max(0, unitPrice * quantity - discount);
  els.checkoutBody.innerHTML = `
    ${renderCheckoutProduct(product, sku, quantity, unitPrice)}
    ${renderCheckoutAddress()}
    ${renderCheckoutCoupons(unitPrice * quantity)}
    <section class="checkout-section checkout-total">
      <span>商品小计 <b>¥${formatMoney(unitPrice * quantity)}</b></span>
      <span>优惠抵扣 <b>-¥${formatMoney(discount)}</b></span>
      <strong>应付 ¥${formatMoney(total)}</strong>
    </section>
    <div class="dialog-actions">
      <button class="ghost-button" type="button" id="checkoutCancel">取消</button>
      <button class="publish-button" type="button" id="checkoutSubmit">提交并支付</button>
    </div>
  `;
  bindCheckoutEvents();
}

function renderCheckoutProduct(product, sku, quantity, unitPrice) {
  if (!product) {
    return `<section class="checkout-section"><strong>购物车商品</strong><p>将使用购物车条目生成订单。</p></section>`;
  }
  return `
    <section class="checkout-section checkout-product">
      <img src="${normalizeImage(sku?.image || product.image)}" alt="">
      <div>
        <strong>${escapeHtml(product.title)}</strong>
        <span>${escapeHtml(sku?.skuName || formatSkuSpecs(sku?.specs) || product.subTitle || "默认规格")}</span>
        <small>¥${formatMoney(unitPrice)}</small>
      </div>
      <div class="quantity-stepper">
        <button type="button" data-qty="-1">-</button>
        <input value="${quantity}" inputmode="numeric" id="checkoutQuantity">
        <button type="button" data-qty="1">+</button>
      </div>
    </section>
  `;
}

function renderCheckoutAddress() {
  return `
    <section class="checkout-section">
      <strong>收货地址</strong>
      <div class="checkout-address-list">
        ${state.addresses.length ? state.addresses.map(address => `
          <button type="button" class="${String(address.id) === String(state.selectedAddressId) ? "is-active" : ""}" data-address-id="${address.id}">
            <b>${escapeHtml(address.receiverName || "")} ${escapeHtml(address.phone || "")}</b>
            <span>${escapeHtml(formatAddressText(address))}</span>
          </button>
        `).join("") : `<p>还没有收货地址，请先新增一个。</p>`}
      </div>
      <form class="address-form" id="checkoutAddressForm">
        <input name="receiverName" placeholder="收货人">
        <input name="phone" placeholder="手机号">
        <input name="city" placeholder="城市">
        <input name="district" placeholder="区县">
        <input name="detailAddress" placeholder="详细地址">
        <label><input type="checkbox" name="defaultFlag"> 默认地址</label>
        <button type="submit">保存地址</button>
      </form>
    </section>
  `;
}

function renderCheckoutCoupons(amount) {
  const coupons = state.productVouchers || [];
  return `
    <section class="checkout-section">
      <strong>优惠券</strong>
      <div class="checkout-coupon-list">
        <button type="button" class="${!state.selectedVoucherId ? "is-active" : ""}" data-checkout-coupon="">自动最优</button>
        ${coupons.map(coupon => `
          <button type="button" class="${String(coupon.id) === String(state.selectedVoucherId) ? "is-active" : ""}" data-checkout-coupon="${coupon.id}" ${amount < Number(coupon.payValue || 0) ? "disabled" : ""}>
            ${escapeHtml(coupon.title || couponScopeLabel(coupon))}
            <small>满 ¥${formatMoney(coupon.payValue)} 减 ¥${formatMoney(coupon.actualValue)}</small>
          </button>
        `).join("")}
      </div>
    </section>
  `;
}

function bindCheckoutEvents() {
  els.checkoutBody.querySelectorAll("[data-address-id]").forEach(button => {
    button.addEventListener("click", () => {
      state.selectedAddressId = Number(button.dataset.addressId);
      renderCheckout();
    });
  });
  els.checkoutBody.querySelectorAll("[data-checkout-coupon]").forEach(button => {
    button.addEventListener("click", () => {
      state.selectedVoucherId = button.dataset.checkoutCoupon || null;
      state.checkoutDraft.voucherId = state.selectedVoucherId ? Number(state.selectedVoucherId) : null;
      renderCheckout();
    });
  });
  els.checkoutBody.querySelectorAll("[data-qty]").forEach(button => {
    button.addEventListener("click", () => {
      state.checkoutQuantity = Math.max(1, Number(state.checkoutQuantity || 1) + Number(button.dataset.qty));
      state.checkoutDraft.quantity = state.checkoutQuantity;
      renderCheckout();
    });
  });
  els.checkoutBody.querySelector("#checkoutQuantity")?.addEventListener("change", event => {
    state.checkoutQuantity = Math.max(1, Number(event.target.value || 1));
    state.checkoutDraft.quantity = state.checkoutQuantity;
    renderCheckout();
  });
  els.checkoutBody.querySelector("#checkoutAddressForm")?.addEventListener("submit", saveCheckoutAddress);
  els.checkoutBody.querySelector("#checkoutCancel")?.addEventListener("click", () => els.checkoutDialog.close());
  els.checkoutBody.querySelector("#checkoutSubmit")?.addEventListener("click", submitCheckout);
}

async function saveCheckoutAddress(event) {
  event.preventDefault();
  const form = new FormData(event.currentTarget);
  const payload = {
    receiverName: String(form.get("receiverName") || "").trim(),
    phone: String(form.get("phone") || "").trim(),
    city: String(form.get("city") || "").trim(),
    district: String(form.get("district") || "").trim(),
    detailAddress: String(form.get("detailAddress") || "").trim(),
    defaultFlag: Boolean(form.get("defaultFlag"))
  };
  if (!payload.receiverName || !payload.phone || !payload.detailAddress) {
    showStatus("请填写收货人、手机号和详细地址。");
    return;
  }
  try {
    const list = await request("/mall/addresses", { method: "POST", body: JSON.stringify(payload) });
    state.addresses = Array.isArray(list) ? list : [];
    state.selectedAddressId = state.addresses.find(address => address.defaultFlag)?.id || state.addresses.at(-1)?.id || null;
    renderCheckout();
  } catch (error) {
    showStatus(error.message || "保存地址失败。");
  }
}

async function submitCheckout() {
  if (!state.selectedAddressId) {
    showStatus("请先选择或新增收货地址。");
    return;
  }
  const draft = state.checkoutDraft;
  const payload = {
    productId: draft.productId,
    skuId: draft.skuId,
    cartItemId: draft.cartItemId,
    addressId: Number(state.selectedAddressId),
    voucherId: state.selectedVoucherId ? Number(state.selectedVoucherId) : null,
    autoBestCoupon: !state.selectedVoucherId,
    quantity: state.checkoutQuantity || draft.quantity || 1
  };
  try {
    const order = await createMallOrder(payload);
    await payMallOrder(order.id);
    els.checkoutDialog.close();
    els.productDialog.close();
    els.cartDialog.close();
    showStatus(`支付成功，订单号：${order.id}`);
    loadProducts();
  } catch (error) {
    showStatus(error.message || "提交订单失败，请检查地址、库存和优惠券。");
  }
}

function formatAddressText(address) {
  return [address.province, address.city, address.district, address.detailAddress]
    .filter(Boolean)
    .join("");
}

// Export cross-module functions
window.setMallActive = setMallActive;
window.setVideoActive = setVideoActive;
window.showContentArea = showContentArea;
window.switchMall = switchMall;
window.switchVideo = switchVideo;
window.loadProducts = loadProducts;
window.renderProducts = renderProducts;
window.openProduct = openProduct;
window.ensureProductGuide = ensureProductGuide;
window.askProductGuide = askProductGuide;
window.loadMallVouchers = loadMallVouchers;
window.renderMallVouchers = renderMallVouchers;
window.addCurrentProductToCart = addCurrentProductToCart;
window.addToCart = addToCart;
window.buyCurrentProductNow = buyCurrentProductNow;
window.createMallOrder = createMallOrder;
window.openCartDialog = openCartDialog;
window.renderCartItems = renderCartItems;
window.orderFromCart = orderFromCart;
window.removeCartItem = removeCartItem;
window.openOrdersDialog = openOrdersDialog;
window.renderOrders = renderOrders;
window.mallOrderStatus = mallOrderStatus;
window.payMallOrder = payMallOrder;
window.buyProductNow = buyProductNow;
window.askOrderService = askOrderService;
window.payOrderFromList = payOrderFromList;
window.renderProductDetail = renderProductDetail;
window.renderSkuSelector = renderSkuSelector;
window.renderProductMerchant = renderProductMerchant;
window.renderProductReviews = renderProductReviews;
window.currentProductSku = currentProductSku;
window.formatSkuSpecs = formatSkuSpecs;
window.toggleMallFavorite = toggleMallFavorite;
window.couponScopeLabel = couponScopeLabel;
window.openCheckout = openCheckout;
window.loadAddressesForCheckout = loadAddressesForCheckout;
window.renderCheckout = renderCheckout;
window.renderCheckoutProduct = renderCheckoutProduct;
window.renderCheckoutAddress = renderCheckoutAddress;
window.renderCheckoutCoupons = renderCheckoutCoupons;
window.bindCheckoutEvents = bindCheckoutEvents;
window.saveCheckoutAddress = saveCheckoutAddress;
window.submitCheckout = submitCheckout;
window.formatAddressText = formatAddressText;

})();
