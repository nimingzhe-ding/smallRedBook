(function() {

// ------------------------------
// Mall product, cart, and order workflow
// ------------------------------

// NOTE: normalizeProduct and normalizeShop are already in utils.js, skipped here to avoid duplication.

function setMallActive(active) {
  if (active) {
    setMobileTabActive(null);
    document.querySelectorAll("[data-feed]").forEach(item => {
      const activeFeed = item.dataset.feed === "mall" && !item.closest(".mobile-tabbar");
      item.classList.toggle("is-active", activeFeed);
      if (activeFeed) item.setAttribute("aria-current", "page");
      else item.removeAttribute("aria-current");
    });
  }
}

function setVideoActive(active) {
  document.querySelectorAll("#mobileVideo").forEach(item => {
    item.classList.toggle("is-active", active);
    if (active) item.setAttribute("aria-current", "page");
    else item.removeAttribute("aria-current");
  });
  if (active) {
    setMobileTabActive("video");
    document.querySelectorAll("[data-feed]").forEach(item => {
      const activeFeed = item.dataset.feed === "video" && !item.closest(".mobile-tabbar");
      item.classList.toggle("is-active", activeFeed);
      if (activeFeed) item.setAttribute("aria-current", "page");
      else item.removeAttribute("aria-current");
    });
  }
}

function showContentArea() {
  if (typeof stopMessagePolling === "function") stopMessagePolling();
  document.body.classList.remove("commerce-mode");
  setFeedTabsVisible(true);
  els.contentArea.hidden = false;
  els.mallArea.hidden = true;
  if (els.commerceWorkspace) els.commerceWorkspace.hidden = true;
  els.videoArea.hidden = true;
  if (els.messageArea) els.messageArea.hidden = true;
  setMessageEntryActive(false);
  setMallActive(false);
  setVideoActive(false);
  pauseImmersiveVideos();
  closeDanmakuSource();
}

function switchMall() {
  if (typeof stopMessagePolling === "function") stopMessagePolling();
  document.body.classList.remove("commerce-mode");
  state.mode = "mall";
  state.mallQuery = els.search.value.trim();
  setFeedTabsVisible(false);
  els.contentArea.hidden = true;
  els.videoArea.hidden = true;
  if (els.messageArea) els.messageArea.hidden = true;
  els.mallArea.hidden = false;
  if (els.commerceWorkspace) els.commerceWorkspace.hidden = true;
  setMessageEntryActive(false);
  setMallActive(true);
  setVideoActive(false);
  pauseImmersiveVideos();
  closeDanmakuSource();
  hideStatus();
  window.scrollTo({ top: 0, behavior: "smooth" });
  loadProducts();
}

function switchVideo() {
  if (typeof stopMessagePolling === "function") stopMessagePolling();
  document.body.classList.remove("commerce-mode");
  state.mode = "video";
  setFeedTabsVisible(false);
  els.contentArea.hidden = true;
  els.mallArea.hidden = true;
  if (els.commerceWorkspace) els.commerceWorkspace.hidden = true;
  if (els.messageArea) els.messageArea.hidden = true;
  els.videoArea.hidden = false;
  setMessageEntryActive(false);
  setMallActive(false);
  setVideoActive(true);
  hideStatus();
  window.scrollTo({ top: 0, behavior: "smooth" });
  renderVideoFeed();
  setTimeout(playCurrentImmersiveVideo, 80);
}

async function loadProducts() {
  els.productGrid.innerHTML = renderProductSkeletons();
  const params = new URLSearchParams({ current: "1", category: state.mallCategory });
  if (state.mallQuery) params.set("query", state.mallQuery);
  try {
    const data = await request(`/mall/products?${params.toString()}`);
    const products = Array.isArray(data) ? data.map(normalizeProduct) : [];
    state.mallProducts = products;
    updateMallSummary(products);
    renderProducts(products);
  } catch {
    state.mallProducts = [];
    updateMallSummary([]);
    els.productGrid.innerHTML = `<p class="empty-text mall-empty">商城接口暂时不可用，请先执行商城数据库脚本并重启后端。</p>`;
  }
}

function renderProducts(products) {
  if (!products.length) {
    els.productGrid.innerHTML = renderCommerceEmpty("这个类目暂时没有商品。", "查看推荐商品", "mall-all");
    bindCommerceEmptyActions(els.productGrid);
    return;
  }
  els.productGrid.innerHTML = products.map(product => {
    const stock = Number(product.stock || 0);
    const soldOut = stock <= 0;
    return `
    <article class="product-card${soldOut ? " is-sold-out" : ""}">
      <button class="product-open" type="button" data-product-id="${product.id}">
        <div class="product-image-wrap">
          <img class="product-image" src="${commerceImage(product.image)}" alt="${escapeHtml(product.title)}" loading="lazy" onerror="${commerceImageFallbackAttr()}">
          <span class="mall-product-badge">${escapeHtml(productBadge(product))}</span>
        </div>
        <div class="product-body">
          <div class="product-meta-row">
            <span>${escapeHtml(product.category || "精选")}</span>
            ${product.score ? `<span>${formatProductScore(product.score)} 分</span>` : `<span>新品</span>`}
          </div>
          <h2>${escapeHtml(product.title)}</h2>
          <p>${escapeHtml(product.subTitle)}</p>
          <div class="product-row">
            <strong>¥${formatMoney(product.price)}</strong>
            ${product.originPrice ? `<small>¥${formatMoney(product.originPrice)}</small>` : ""}
          </div>
          <div class="product-footnote">
            <span>已售 ${product.sold}</span>
            <span>${soldOut ? "暂时售罄" : `库存 ${stock}`}</span>
          </div>
        </div>
      </button>
      <div class="product-card-actions">
        <button class="product-cart-icon" type="button" data-quick-cart="${product.id}" ${soldOut ? "disabled" : ""} aria-label="加入购物车">
          <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M7 7h12l-1.2 7.2a2 2 0 0 1-2 1.8H9.1a2 2 0 0 1-2-1.7L5.8 4H3V2h4.6L8 5h12.4L20 7H7Zm2.2 7h6.6l.8-5H8.5l.7 5ZM9 21a2 2 0 1 1 0-4 2 2 0 0 1 0 4Zm7 0a2 2 0 1 1 0-4 2 2 0 0 1 0 4Z"/></svg>
        </button>
        <button class="product-view-button" type="button" data-product-id="${product.id}" ${soldOut ? "disabled" : ""}>${soldOut ? "已售罄" : "立即购买"}</button>
      </div>
    </article>
  `;
  }).join("");
  els.productGrid.querySelectorAll("[data-product-id]").forEach(button => {
    button.addEventListener("click", () => openProduct(button.dataset.productId));
  });
  els.productGrid.querySelectorAll("[data-quick-cart]").forEach(button => {
    button.addEventListener("click", () => addToCart(button.dataset.quickCart, 1, button));
  });
}

function updateMallSummary(products) {
  const count = document.querySelector("#mallProductCount");
  if (!count) return;
  const totalSold = products.reduce((sum, product) => sum + Number(product.sold || 0), 0);
  count.textContent = products.length
    ? `${products.length} 件在售 · ${totalSold} 次购买`
    : "-- 件在售";
}

function renderProductSkeletons() {
  return Array.from({ length: 8 }).map(() => `
    <article class="product-card product-card-skeleton" aria-hidden="true">
      <div class="product-image-wrap"></div>
      <div class="product-body">
        <i></i>
        <i></i>
        <i></i>
      </div>
    </article>
  `).join("");
}

function productBadge(product) {
  if (Number(product.stock || 0) <= 0) return "售罄";
  if (Number(product.sold || 0) >= 100) return "热卖";
  if (Number(product.stock || 0) <= 10 && Number(product.stock || 0) > 0) return "少量";
  if (product.originPrice && Number(product.originPrice) > Number(product.price)) return "优惠";
  return "精选";
}

function formatProductScore(score) {
  const value = Number(score || 0);
  if (!value) return "5.0";
  return (value / 10).toFixed(1);
}

function commerceImage(src) {
  return normalizeImage(src) || window.defaultNoteImage || "";
}

function commerceImageFallbackAttr() {
  return "this.onerror=null;this.src=window.defaultNoteImage||'';this.classList.add('is-fallback');";
}

function ensureProductGuide(product) {
  let panel = document.querySelector("#productAiGuide");
  if (!panel) {
    (document.querySelector("#productDetailSections") || document.querySelector(".product-dialog-price")).insertAdjacentHTML("afterend", `
      <section class="ai-inline-card product-ai-guide" id="productAiGuide">
        <strong>购买建议</strong>
        <div class="product-ai-query">
          <input id="productAiQuestion" placeholder="比如：这个适合送女朋友吗？">
          <button class="soft-button" type="button" id="askProductGuide">发送</button>
        </div>
        <p id="productAiAnswer">可以直接问这件商品适不适合你的场景。</p>
      </section>
    `);
    document.querySelector("#productAiQuestion").addEventListener("keydown", event => {
      if (event.key === "Enter") {
        event.preventDefault();
        askProductGuide();
      }
    });
    document.querySelector("#askProductGuide")?.addEventListener("click", askProductGuide);
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

async function addToCart(productId, quantity = 1, triggerButton = null) {
  const originalText = triggerButton?.textContent;
  try {
    if (triggerButton) {
      triggerButton.disabled = true;
      triggerButton.textContent = "加入中";
    }
    await request("/mall/cart", {
      method: "POST",
      body: JSON.stringify({ productId: Number(productId), quantity })
    });
    showStatus("已加入购物车，可以继续逛或去购物车下单。");
  } catch (error) {
    showStatus(error.message || "加入购物车失败，请检查登录状态和库存。");
  } finally {
    if (triggerButton) {
      triggerButton.disabled = false;
      triggerButton.textContent = originalText || "加购";
    }
  }
}

async function createMallOrder(payload) {
  return request("/mall/orders", {
    method: "POST",
    body: JSON.stringify(payload)
  });
}

function openCommerceWorkspace(view, options = {}) {
  if (typeof stopMessagePolling === "function") stopMessagePolling();
  document.body.classList.add("commerce-mode");
  state.mode = "commerce";
  state.commerceView = view;
  setFeedTabsVisible(false);
  els.contentArea.hidden = true;
  els.mallArea.hidden = true;
  els.videoArea.hidden = true;
  if (els.messageArea) els.messageArea.hidden = true;
  setMessageEntryActive(false);
  setMallActive(true);
  setVideoActive(false);
  pauseImmersiveVideos();
  closeDanmakuSource();
  hideStatus();
  if (els.commerceWorkspace) els.commerceWorkspace.hidden = false;
  if (els.commerceWorkspaceKicker) els.commerceWorkspaceKicker.textContent = options.kicker || "交易工作台";
  if (els.commerceWorkspaceTitle) els.commerceWorkspaceTitle.textContent = options.title || "商城工作台";
  if (els.commerceWorkspaceSub) els.commerceWorkspaceSub.textContent = options.sub || "管理购物车、订单和商家运营。";
  document.querySelectorAll("[data-commerce-view]").forEach(button => {
    const active = button.dataset.commerceView === view;
    button.classList.toggle("is-active", active);
    if (active) button.setAttribute("aria-current", "page");
    else button.removeAttribute("aria-current");
  });
  window.scrollTo({ top: 0, behavior: "smooth" });
}

function closeCommerceWorkspace() {
  if (els.commerceWorkspace) els.commerceWorkspace.hidden = true;
  switchMall();
}

function renderCommerceLoading(message) {
  if (!els.commerceWorkspaceBody) return;
  els.commerceWorkspaceBody.innerHTML = `
    <div class="commerce-loading">
      <span></span>
      <p>${escapeHtml(message)}</p>
    </div>
  `;
}

function renderCommerceLoginRequired(message, buttonLabel, afterLoginAction) {
  if (!els.commerceWorkspaceBody) return;
  state.afterLoginAction = afterLoginAction;
  els.commerceWorkspaceBody.innerHTML = `
    <div class="commerce-empty commerce-login-required">
      <strong>${escapeHtml(message)}</strong>
      <p>登录后会自动回到当前商城页面继续操作。</p>
      <button class="publish-button" type="button" data-commerce-login="${escapeHtml(afterLoginAction)}">${escapeHtml(buttonLabel)}</button>
    </div>
  `;
  els.commerceWorkspaceBody.querySelector("[data-commerce-login]")?.addEventListener("click", () => {
    state.afterLoginAction = afterLoginAction;
    requireLogin();
  });
}

function isCommerceAuthError(error) {
  const message = String(error?.message || "");
  return Number(error?.status) === 401 || /401|登录|未登录|未授权/.test(message);
}

function resetCommerceAuth() {
  localStorage.removeItem("hmdp_token");
  localStorage.removeItem("hmdp_token_expire_at");
  state.currentUser = null;
  window.renderUser?.(null);
}

function renderCommerceAuthRequired(message, buttonLabel, afterLoginAction) {
  resetCommerceAuth();
  renderCommerceLoginRequired(message, buttonLabel, afterLoginAction);
}

function commerceStatusClass(status) {
  return `status-${Number(status || 0)}`;
}

async function openCartDialog() {
  openCommerceWorkspace("cart", {
    title: "购物车",
    sub: "确认商品、金额和优惠后进入结算。",
    kicker: "买家工作台"
  });
  if (!token()) {
    renderCommerceLoginRequired("登录后查看购物车和结算商品。", "登录查看购物车", "cart");
    return;
  }
  renderCommerceLoading("正在加载购物车...");
  try {
    const items = await request("/mall/cart");
    renderCartItems(Array.isArray(items) ? items : []);
  } catch (error) {
    if (isCommerceAuthError(error)) {
      renderCommerceAuthRequired("登录状态已失效，请重新登录后查看购物车。", "重新登录查看购物车", "cart");
      return;
    }
    els.commerceWorkspaceBody.innerHTML = renderCommerceEmpty(error.message || "购物车加载失败，请确认已经登录。", "返回商城", "mall");
    bindCommerceEmptyActions(els.commerceWorkspaceBody);
  }
}

function renderCartItems(items) {
  state.cartItems = Array.isArray(items) ? items : [];
  if (!items.length) {
    els.commerceWorkspaceBody.innerHTML = renderCommerceEmpty("购物车还是空的，先去商城挑一件。", "去商城看看", "mall");
    bindCommerceEmptyActions(els.commerceWorkspaceBody);
    return;
  }
  const totalAmount = items.reduce((sum, item) => sum + Number(item.totalAmount || 0), 0);
  const totalQuantity = items.reduce((sum, item) => sum + Number(item.quantity || 0), 0);
  els.commerceWorkspaceBody.innerHTML = `
    <div class="commerce-cart-layout commerce-cart-page">
      <section class="commerce-panel commerce-cart-list commerce-card-panel">
        <div class="commerce-section-head commerce-cart-header">
          <div>
            <strong>购物车</strong>
            <span>${items.length} 件商品 · 共 ${totalQuantity} 件</span>
          </div>
          <b>¥${formatMoney(totalAmount)}</b>
        </div>
        ${items.map(item => {
          const title = item.title || item.productTitle || "购物车商品";
          const image = item.image || item.productImage;
          const price = item.price ?? item.productPrice ?? 0;
          const quantity = Number(item.quantity || 1);
          const subtotal = Number(item.totalAmount ?? price * quantity);
          const productId = item.productId || item.product?.id || "";
          return `
          <article class="commerce-line-item">
            <button class="commerce-line-product" type="button" data-cart-product="${productId}" aria-label="查看${escapeHtml(title)}详情">
              <img src="${commerceImage(image)}" alt="${escapeHtml(title)}" onerror="${commerceImageFallbackAttr()}">
              <span class="commerce-item-main">
                <strong>${escapeHtml(title)}</strong>
                <span>¥${formatMoney(price)}</span>
                <small>x${quantity}</small>
                <b>小计 ¥${formatMoney(subtotal)}</b>
              </span>
            </button>
            <div class="commerce-line-actions">
              <button class="publish-button" type="button" data-cart-order="${item.id}">结算</button>
              <button class="ghost-button" type="button" data-cart-remove="${item.id}">删除</button>
            </div>
          </article>
        `;
        }).join("")}
      </section>
      <aside class="commerce-panel commerce-summary-card">
        <small>${totalQuantity} 件商品</small>
        <strong>¥${formatMoney(totalAmount)}</strong>
        <span>合计金额</span>
        <button class="publish-button" type="button" data-cart-order="${items[0].id}">结算</button>
        <button class="ghost-button" type="button" data-commerce-action="continue-shopping">继续逛商城</button>
      </aside>
    </div>
  `;
  els.commerceWorkspaceBody.querySelectorAll("[data-cart-order]").forEach(button => {
    button.addEventListener("click", () => orderFromCart(button.dataset.cartOrder));
  });
  els.commerceWorkspaceBody.querySelectorAll("[data-cart-remove]").forEach(button => {
    button.addEventListener("click", () => removeCartItem(button.dataset.cartRemove));
  });
  els.commerceWorkspaceBody.querySelectorAll("[data-cart-product]").forEach(button => {
    button.addEventListener("click", () => {
      if (button.dataset.cartProduct) openProduct(button.dataset.cartProduct);
    });
  });
  els.commerceWorkspaceBody.querySelector("[data-commerce-action='continue-shopping']")?.addEventListener("click", closeCommerceWorkspace);
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
  if (!focusOrderId) state.commerceOrderFilter = "all";
  openCommerceWorkspace("orders", {
    title: "我的订单",
    sub: "查看支付、发货、物流、售后和客服记录。",
    kicker: "买家工作台"
  });
  if (!token()) {
    renderCommerceLoginRequired("登录后查看订单、物流和售后进度。", "登录查看订单", "orders");
    return;
  }
  renderCommerceLoading("正在加载订单...");
  try {
    const orders = await request("/mall/orders");
    renderOrders(Array.isArray(orders) ? orders : [], focusOrderId);
  } catch (error) {
    if (isCommerceAuthError(error)) {
      renderCommerceAuthRequired("登录状态已失效，请重新登录后查看订单。", "重新登录查看订单", "orders");
      return;
    }
    els.commerceWorkspaceBody.innerHTML = renderCommerceError(
      "订单服务暂时不可用",
      "刚才请求订单接口失败，可以直接重试，或者先回商城继续浏览。",
      "orders"
    );
    bindCommerceEmptyActions(els.commerceWorkspaceBody);
    bindCommerceRetryActions(els.commerceWorkspaceBody);
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

function renderCommerceEmpty(message, actionLabel = "", action = "") {
  return `
    <div class="commerce-empty">
      <span class="commerce-empty-mark">空</span>
      <strong>${escapeHtml(message)}</strong>
      ${action ? `<button class="soft-button" type="button" data-empty-action="${escapeHtml(action)}">${escapeHtml(actionLabel)}</button>` : ""}
    </div>
  `;
}

function renderCommerceError(title, description, retryAction = "") {
  return `
    <div class="commerce-empty commerce-error-state">
      <span class="commerce-empty-mark">!</span>
      <strong>${escapeHtml(title)}</strong>
      <p>${escapeHtml(description)}</p>
      <div class="commerce-empty-actions">
        ${retryAction ? `<button class="publish-button" type="button" data-commerce-retry="${escapeHtml(retryAction)}">重新加载</button>` : ""}
        <button class="soft-button" type="button" data-empty-action="mall">返回商城</button>
      </div>
    </div>
  `;
}

function bindCommerceEmptyActions(root) {
  root.querySelectorAll("[data-empty-action]").forEach(button => {
    button.addEventListener("click", () => {
      const action = button.dataset.emptyAction;
      if (action === "orders") {
        openOrdersDialog();
        return;
      }
      if (action === "cart") {
        openCartDialog();
        return;
      }
      if (action === "mall" || action === "mall-all") {
        if (action === "mall-all") {
          state.mallCategory = "all";
          document.querySelectorAll("[data-mall-category]").forEach(item => {
            item.classList.toggle("is-active", item.dataset.mallCategory === "all");
          });
        }
        els.checkoutDialog?.close();
        els.productDialog?.close();
        if (els.commerceWorkspace) els.commerceWorkspace.hidden = true;
        switchMall();
      }
    });
  });
}

function bindCommerceRetryActions(root) {
  root.querySelectorAll("[data-commerce-retry]").forEach(button => {
    button.addEventListener("click", () => {
      const action = button.dataset.commerceRetry;
      if (action === "orders") openOrdersDialog();
      if (action === "cart") openCartDialog();
    });
  });
}

function renderOrders(orders, focusOrderId = null) {
  state.mallOrders = Array.isArray(orders) ? orders : [];
  const filter = state.commerceOrderFilter || "all";
  const filtered = filterOrders(state.mallOrders, filter);
  if (!state.mallOrders.length) {
    els.commerceWorkspaceBody.innerHTML = renderCommerceEmpty("还没有商城订单。", "去商城逛逛", "mall");
    bindCommerceEmptyActions(els.commerceWorkspaceBody);
    return;
  }
  els.commerceWorkspaceBody.innerHTML = `
    <section class="commerce-panel commerce-orders-page commerce-card-panel">
      <div class="commerce-section-head commerce-orders-header">
        <div>
          <strong>订单中心</strong>
          <span>${state.mallOrders.length} 笔订单 · 当前 ${filtered.length} 笔</span>
        </div>
        <button class="soft-button commerce-refresh-button" type="button" data-orders-refresh>刷新</button>
      </div>
      <div class="commerce-filter-row">
        ${[
          ["all", "全部"],
          ["pending-pay", "待支付"],
          ["pending-ship", "待发货"],
          ["shipped", "已发货"],
          ["refund", "退款中"]
        ].map(([key, label]) => `
          <button type="button" class="${filter === key ? "is-active" : ""}" data-order-filter="${key}">
            <span>${label}</span>
            <b>${orderFilterCount(state.mallOrders, key)}</b>
          </button>
        `).join("")}
      </div>
      <div class="commerce-order-list">
        ${filtered.length ? filtered.map(order => renderOrderCard(order, focusOrderId)).join("") : `
          <div class="commerce-empty commerce-filter-empty">
            <strong>这个状态下暂无订单。</strong>
            <p>换个状态看看，或者回到全部订单。</p>
            <button class="soft-button" type="button" data-order-filter-reset>查看全部</button>
          </div>
        `}
      </div>
    </section>
  `;
  els.commerceWorkspaceBody.querySelector("[data-orders-refresh]")?.addEventListener("click", () => openOrdersDialog(focusOrderId));
  els.commerceWorkspaceBody.querySelectorAll("[data-order-filter]").forEach(button => {
    button.addEventListener("click", () => {
      state.commerceOrderFilter = button.dataset.orderFilter;
      renderOrders(state.mallOrders, focusOrderId);
    });
  });
  els.commerceWorkspaceBody.querySelector("[data-order-filter-reset]")?.addEventListener("click", () => {
    state.commerceOrderFilter = "all";
    renderOrders(state.mallOrders, focusOrderId);
  });
  els.commerceWorkspaceBody.querySelectorAll("[data-order-detail]").forEach(button => {
    button.addEventListener("click", () => openOrderDetail(button.dataset.orderDetail));
  });
  els.commerceWorkspaceBody.querySelectorAll("[data-order-pay]").forEach(button => {
    button.addEventListener("click", () => payOrderFromList(button.dataset.orderPay));
  });
  els.commerceWorkspaceBody.querySelectorAll("[data-order-cancel]").forEach(button => {
    button.addEventListener("click", () => cancelOrderFromList(button.dataset.orderCancel));
  });
  els.commerceWorkspaceBody.querySelectorAll("[data-order-refund]").forEach(button => {
    button.addEventListener("click", () => applyRefundFromList(button.dataset.orderRefund));
  });
  els.commerceWorkspaceBody.querySelectorAll("[data-order-receive]").forEach(button => {
    button.addEventListener("click", () => receiveOrderFromList(button.dataset.orderReceive));
  });
  if (focusOrderId) {
    setTimeout(() => {
      els.commerceWorkspaceBody.querySelector(`[data-order-id="${focusOrderId}"]`)?.scrollIntoView({ block: "center" });
    }, 50);
  }
}

function filterOrders(orders, filter) {
  if (filter === "pending-pay") return orders.filter(order => Number(order.status) === 1);
  if (filter === "pending-ship") return orders.filter(order => [2, 3].includes(Number(order.status)));
  if (filter === "shipped") return orders.filter(order => Number(order.status) === 4);
  if (filter === "refund") return orders.filter(order => [7, 8].includes(Number(order.status)));
  return orders;
}

function orderFilterCount(orders, filter) {
  return filterOrders(orders, filter).length;
}

function renderOrderCard(order, focusOrderId = null) {
  const title = order.productTitle || "订单商品";
  return `
    <article class="commerce-order-card${String(order.id) === String(focusOrderId) ? " is-highlight" : ""}" data-order-id="${order.id}">
      <div class="commerce-order-head">
        <span>订单号 ${order.id}</span>
        <span class="commerce-status ${commerceStatusClass(order.status)}">${mallOrderStatus(order.status)}</span>
      </div>
      <div class="commerce-order-product">
        <img src="${commerceImage(order.productImage)}" alt="${escapeHtml(title)}" onerror="${commerceImageFallbackAttr()}">
        <div>
          <strong>${escapeHtml(title)}</strong>
          <small>${formatDateTime(order.createTime)}</small>
          <p>x${order.quantity || 1}</p>
        </div>
      </div>
      <div class="commerce-order-total">
        <span>实付</span>
        <strong>¥${formatMoney(order.totalAmount)}</strong>
      </div>
      <div class="commerce-row-actions">
        ${renderOrderListActions(order)}
        <button class="soft-button" type="button" data-order-detail="${order.id}">详情</button>
      </div>
    </article>
  `;
}

function renderOrderListActions(order) {
  const status = Number(order.status);
  if (status === 1) {
    return `<button class="publish-button" type="button" data-order-pay="${order.id}">去支付</button>`;
  }
  if (status === 4) {
    return `<button class="publish-button" type="button" data-order-receive="${order.id}">确认收货</button>`;
  }
  if ([2, 3].includes(status)) {
    return `<button class="ghost-button" type="button" data-order-refund="${order.id}">申请退款</button>`;
  }
  return "";
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
    openOrderDetail(orderId);
  } catch (error) {
    showStatus(error.message || "支付失败，请稍后再试。");
  }
}

async function cancelOrderFromList(orderId) {
  try {
    await request(`/mall/orders/${orderId}/cancel`, { method: "POST" });
    showStatus("订单已取消。");
    openOrderDetail(orderId);
  } catch (error) {
    showStatus(error.message || "取消订单失败。");
  }
}

async function applyRefundFromList(orderId, reason = "用户申请退款") {
  try {
    await request(`/mall/orders/${orderId}/refunds`, {
      method: "POST",
      body: JSON.stringify({ reason })
    });
    showStatus("退款申请已提交。");
    openOrderDetail(orderId);
  } catch (error) {
    showStatus(error.message || "退款申请失败。");
  }
}

async function receiveOrderFromList(orderId) {
  try {
    await request(`/mall/orders/${orderId}/receive`, { method: "POST" });
    showStatus("已确认收货。");
    openOrderDetail(orderId);
  } catch (error) {
    showStatus(error.message || "确认收货失败。");
  }
}

async function openOrderDetail(orderId) {
  if (!requireLoginThen(() => openOrderDetail(orderId))) return;
  openCommerceWorkspace("orders", {
    title: "订单详情",
    sub: "查看这笔订单的物流、优惠、售后和客服记录。",
    kicker: "买家工作台"
  });
  renderCommerceLoading("正在加载订单详情...");
  try {
    const detail = await request(`/mall/orders/${orderId}`);
    renderOrderDetail(detail);
  } catch (error) {
    if (isCommerceAuthError(error)) {
      renderCommerceAuthRequired("登录状态已失效，请重新登录后查看订单详情。", "重新登录查看订单", "orders");
      return;
    }
    els.commerceWorkspaceBody.innerHTML = renderCommerceEmpty(error.message || "订单详情加载失败", "返回订单列表", "orders");
    els.commerceWorkspaceBody.querySelector("[data-empty-action]")?.addEventListener("click", () => openOrdersDialog(orderId));
  }
}

function renderOrderDetail(detail) {
  const order = detail?.order || detail;
  if (!order?.id) {
    els.commerceWorkspaceBody.innerHTML = `<p class="empty-text">订单不存在。</p>`;
    return;
  }
  const refunds = Array.isArray(detail?.refunds) ? detail.refunds : [];
  const logistics = detail?.logistics || null;
  const voucher = detail?.voucher || null;
  const merchant = detail?.merchant || null;
  els.commerceWorkspaceBody.innerHTML = `
    <div class="order-detail-panel">
      <div class="order-detail-head">
        <button class="ghost-button" type="button" id="backToOrders">返回列表</button>
        <span>${mallOrderStatus(order.status)}</span>
      </div>
      <section class="order-detail-product">
        <img src="${commerceImage(order.productImage)}" alt="${escapeHtml(order.productTitle)}" onerror="${commerceImageFallbackAttr()}">
        <div>
          <strong>${escapeHtml(order.productTitle || "订单商品")}</strong>
          <span>${escapeHtml(order.skuName || formatSkuSpecs(order.skuSpecs) || "默认规格")}</span>
          <small>订单号 ${order.id}</small>
        </div>
      </section>
      <section class="order-detail-grid">
        <div><span>实付金额</span><strong>¥${formatMoney(order.totalAmount)}</strong></div>
        <div><span>商品数量</span><strong>${order.quantity || 1}</strong></div>
        <div><span>优惠抵扣</span><strong>¥${formatMoney(order.discountAmount)}</strong></div>
        <div><span>商家</span><strong>${escapeHtml(merchant?.name || "平台商家")}</strong></div>
      </section>
      <section class="order-detail-section">
        <strong>收货信息</strong>
        <p>${escapeHtml(order.receiverName || "")} ${escapeHtml(order.receiverPhone || "")}</p>
        <p>${escapeHtml(order.receiverAddress || "未保存收货地址")}</p>
      </section>
      <section class="order-detail-section">
        <strong>优惠信息</strong>
        <p>${voucher ? `${escapeHtml(voucher.title || "优惠券")} · 满 ¥${formatMoney(voucher.payValue)} 减 ¥${formatMoney(voucher.actualValue)}` : "这笔订单没有使用优惠券。"}</p>
      </section>
      ${renderOrderTimeline(order, logistics)}
      ${renderRefundTimeline(refunds)}
      <section class="order-detail-section">
        <strong>智能客服</strong>
        <div class="order-ai-service">
          <input data-order-ai-input="${order.id}" data-order-product="${order.productId || ""}" placeholder="继续问这笔订单的问题">
          <button class="soft-button" type="button" data-order-ai-send="${order.id}">发送</button>
          <p data-order-ai-answer="${order.id}"></p>
        </div>
      </section>
      <div class="order-detail-actions">
        ${renderOrderDetailActions(order)}
      </div>
    </div>
  `;
  document.querySelector("#backToOrders")?.addEventListener("click", () => openOrdersDialog(order.id));
  bindOrderDetailActions(order);
  const input = els.commerceWorkspaceBody.querySelector(`[data-order-ai-input="${order.id}"]`);
  input?.addEventListener("keydown", event => {
    if (event.key === "Enter") {
      event.preventDefault();
      askOrderService(order.id, input.value);
    }
  });
  els.commerceWorkspaceBody.querySelector(`[data-order-ai-send="${order.id}"]`)?.addEventListener("click", () => {
    askOrderService(order.id, input?.value || "");
  });
}

function renderOrderTimeline(order, logistics) {
  const items = [
    ["订单创建", order.createTime],
    ["支付成功", order.payTime],
    ["商家发货", order.shipTime || logistics?.shipTime],
    ["确认收货", order.receiveTime || logistics?.signedTime],
    ["取消订单", order.cancelTime],
    ["退款完成", order.refundTime]
  ].filter(([, time]) => Boolean(time));
  const logisticsText = logistics?.trackingNo || order.logisticsNo
    ? `${escapeHtml(logistics?.company || order.logisticsCompany || "物流")} · ${escapeHtml(logistics?.trackingNo || order.logisticsNo)}`
    : order.status >= 3 ? "商家发货后会同步物流单号。" : "支付后等待商家发货。";
  return `
    <section class="order-detail-section">
      <strong>物流进度</strong>
      <p>${logisticsText}</p>
      <div class="order-timeline">
        ${items.length ? items.map(([label, time]) => `
          <div><b>${escapeHtml(label)}</b><span>${formatDateTime(time)}</span></div>
        `).join("") : `<p>暂无进度记录。</p>`}
      </div>
    </section>
  `;
}

function renderRefundTimeline(refunds) {
  return `
    <section class="order-detail-section">
      <strong>售后记录</strong>
      ${refunds.length ? `
        <div class="refund-list">
          ${refunds.map(refund => `
            <article>
              <b>${refundStatusLabel(refund.status)}</b>
              <span>退款金额 ¥${formatMoney(refund.amount)} · ${escapeHtml(refund.reason || "用户申请退款")}</span>
              <small>${formatDateTime(refund.applyTime || refund.createTime)}${refund.merchantRemark ? ` · ${escapeHtml(refund.merchantRemark)}` : ""}</small>
            </article>
          `).join("")}
        </div>
      ` : `<p>暂无售后记录。</p>`}
    </section>
  `;
}

function renderOrderDetailActions(order) {
  const status = Number(order.status);
  if (status === 1) {
    return `
      <button class="publish-button" type="button" data-detail-pay="${order.id}">去支付</button>
      <button class="ghost-button" type="button" data-detail-cancel="${order.id}">取消订单</button>
    `;
  }
  if (status === 4) {
    return `
      <button class="ghost-button" type="button" data-detail-refund="${order.id}">申请退款</button>
      <button class="publish-button" type="button" data-detail-receive="${order.id}">确认收货</button>
    `;
  }
  if ([2, 3].includes(status)) {
    return `<button class="ghost-button" type="button" data-detail-refund="${order.id}">申请退款</button>`;
  }
  return `<button class="ghost-button" type="button" data-detail-cs="${order.id}">咨询客服</button>`;
}

function bindOrderDetailActions(order) {
  els.commerceWorkspaceBody.querySelector("[data-detail-pay]")?.addEventListener("click", () => payOrderFromList(order.id));
  els.commerceWorkspaceBody.querySelector("[data-detail-cancel]")?.addEventListener("click", () => cancelOrderFromList(order.id));
  els.commerceWorkspaceBody.querySelector("[data-detail-refund]")?.addEventListener("click", () => applyRefundFromList(order.id));
  els.commerceWorkspaceBody.querySelector("[data-detail-receive]")?.addEventListener("click", () => receiveOrderFromList(order.id));
  els.commerceWorkspaceBody.querySelector("[data-detail-cs]")?.addEventListener("click", () => {
    openCustomerServiceDialog();
    askCustomerServiceQuestion("这笔订单需要帮助", {
      orderId: order.id,
      productId: order.productId || null,
      voucherId: order.voucherId || null,
      scenario: "order"
    });
  });
}

function refundStatusLabel(status) {
  return {
    0: "待商家处理",
    1: "退款成功",
    2: "退款被拒绝"
  }[Number(status)] || "处理中";
}

function formatDateTime(value) {
  if (!value) return "";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return String(value);
  return date.toLocaleString("zh-CN", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit"
  });
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
  document.querySelector("#productDialogImage").src = commerceImage(product.image);
  document.querySelector("#productDialogImage").onerror = event => {
    event.currentTarget.onerror = null;
    event.currentTarget.src = window.defaultNoteImage || "";
    event.currentTarget.classList.add("is-fallback");
  };
  document.querySelector("#productDialogSub").textContent = product.subTitle;
  renderProductDetail(product);
  renderMallVouchers(state.productVouchers);
  ensureProductGuide(product);
  updateProductActionState(product, currentProductSku());
  els.productDialog.showModal();
}

function renderProductDetail(product) {
  const sku = currentProductSku();
  document.querySelector("#productDialogPrice").textContent = `¥${formatMoney(sku?.price || product.price)}`;
  document.querySelector("#productDialogStock").textContent = `库存 ${sku?.stock ?? product.stock} · 已售 ${sku?.sold ?? product.sold}`;
  updateProductActionState(product, sku);
  let container = document.querySelector("#productDetailSections");
  if (!container) {
    document.querySelector(".product-dialog-price").insertAdjacentHTML("afterend", `<div id="productDetailSections"></div>`);
    container = document.querySelector("#productDetailSections");
  }
  const images = (product.images?.length ? product.images : [product.image]).filter(Boolean);
  container.innerHTML = `
    <div class="product-gallery-thumbs">
      ${images.map((image, index) => `
        <button type="button" class="${index === 0 ? "is-active" : ""}" data-product-image="${escapeHtml(image)}">
          <img src="${commerceImage(image)}" alt="" onerror="${commerceImageFallbackAttr()}">
        </button>
      `).join("")}
    </div>
    ${renderSkuSelector(product)}
    ${renderProductMerchant(product)}
    ${renderProductReviews(product)}
  `;
  container.querySelectorAll("[data-product-image]").forEach(button => {
    button.addEventListener("click", () => {
      document.querySelector("#productDialogImage").src = commerceImage(button.dataset.productImage);
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

function updateProductActionState(product, sku = null) {
  const stock = Number(sku?.stock ?? product?.stock ?? 0);
  const soldOut = stock <= 0;
  const cartButton = document.querySelector("#addProductCart");
  const buyButton = document.querySelector("#buyProductNow");
  if (cartButton) {
    cartButton.disabled = soldOut;
    cartButton.textContent = soldOut ? "暂时售罄" : "加入购物车";
  }
  if (buyButton) {
    buyButton.disabled = soldOut;
    buyButton.textContent = soldOut ? "暂不可买" : "立即购买";
  }
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
        <img src="${commerceImage(merchant.avatar || product.image)}" alt="" onerror="${commerceImageFallbackAttr()}">
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
  const sku = currentProductSku();
  if (Number(sku?.stock ?? state.currentProduct.stock ?? 0) <= 0) {
    showStatus("这件商品暂时售罄。");
    return;
  }
  await addToCart(state.currentProduct.id, state.checkoutQuantity || 1, document.querySelector("#addProductCart"));
}

async function buyCurrentProductNow() {
  if (!state.currentProduct || !requireLogin()) return;
  const sku = currentProductSku();
  if (Number(sku?.stock ?? state.currentProduct.stock ?? 0) <= 0) {
    showStatus("这件商品暂时售罄。");
    return;
  }
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
    title: item.title || item.productTitle,
    subTitle: "购物车商品",
    images: item.image || item.productImage,
    price: item.price ?? item.productPrice,
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
    <section class="checkout-section checkout-total checkout-summary-card">
      <span>商品小计 <b>¥${formatMoney(unitPrice * quantity)}</b></span>
      <span>优惠抵扣 <b>-¥${formatMoney(discount)}</b></span>
      <strong>应付 ¥${formatMoney(total)}</strong>
    </section>
    <div class="dialog-actions checkout-actions">
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
      <img src="${commerceImage(sku?.image || product.image)}" alt="" onerror="${commerceImageFallbackAttr()}">
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
    <section class="checkout-section checkout-address-section">
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
    <section class="checkout-section checkout-coupon-section">
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
  const submitButton = els.checkoutBody.querySelector("#checkoutSubmit");
  const originalText = submitButton?.textContent;
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
    if (submitButton) {
      submitButton.disabled = true;
      submitButton.textContent = "提交中";
    }
    const order = await createMallOrder(payload);
    await payMallOrder(order.id);
    if (els.checkoutDialog.open) els.checkoutDialog.close();
    if (els.productDialog.open) els.productDialog.close();
    if (els.cartDialog.open) els.cartDialog.close();
    showStatus(`支付成功，订单号：${order.id}`);
    loadProducts();
    openOrderDetail(order.id);
  } catch (error) {
    showStatus(error.message || "提交订单失败，请检查地址、库存和优惠券。");
  } finally {
    if (submitButton && els.checkoutDialog.open) {
      submitButton.disabled = false;
      submitButton.textContent = originalText || "提交并支付";
    }
  }
}

function formatAddressText(address) {
  return [address.province, address.city, address.district, address.detailAddress]
    .filter(Boolean)
    .join("");
}

function bindCommerceWorkspaceControls() {
  document.querySelector("#closeCommerceWorkspace")?.addEventListener("click", closeCommerceWorkspace);
  document.querySelectorAll("[data-commerce-view]").forEach(button => {
    button.addEventListener("click", () => {
      const view = button.dataset.commerceView;
      if (view === "cart") openCartDialog();
      if (view === "orders") openOrdersDialog();
      if (view === "merchant") window.openMerchantCenter?.();
    });
  });
}

bindCommerceWorkspaceControls();

// Export cross-module functions
window.setMallActive = setMallActive;
window.setVideoActive = setVideoActive;
window.showContentArea = showContentArea;
window.switchMall = switchMall;
window.switchVideo = switchVideo;
window.openCommerceWorkspace = openCommerceWorkspace;
window.closeCommerceWorkspace = closeCommerceWorkspace;
window.renderCommerceLoading = renderCommerceLoading;
window.renderCommerceLoginRequired = renderCommerceLoginRequired;
window.renderCommerceAuthRequired = renderCommerceAuthRequired;
window.isCommerceAuthError = isCommerceAuthError;
window.commerceStatusClass = commerceStatusClass;
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
window.openOrderDetail = openOrderDetail;
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
