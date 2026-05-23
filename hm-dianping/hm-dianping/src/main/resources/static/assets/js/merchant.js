(function() {
  // ------------------------------
  // Merchant workspace: apply, dashboard, products, vouchers, orders
  // ------------------------------

  async function openMerchantCenter() {
    openCommerceWorkspace("merchant", {
      title: "商家中心",
      sub: "管理商品、订单、优惠券和店铺成交表现。",
      kicker: "运营工作台"
    });
    if (!token()) {
      renderCommerceLoginRequired?.("登录后开通或管理商家中心。", "登录进入商家中心", "merchant");
      return;
    }
    renderCommerceLoading("正在加载商家中心...");
    await loadMerchantCenter();
  }

  async function loadMerchantCenter() {
    try {
      state.merchant = await request("/merchant/mine");
    } catch (error) {
      if (window.isCommerceAuthError?.(error)) {
        window.renderCommerceAuthRequired?.("登录状态已失效，请重新登录后进入商家中心。", "重新登录进入商家中心", "merchant");
        return;
      }
      state.merchant = null;
    }
    if (!state.merchant) {
      renderMerchantApply();
      return;
    }
    await Promise.all([loadMerchantProducts(), loadMerchantOrders()]);
    renderMerchantDashboard(state.merchantTab || "overview");
  }

  async function loadMerchantProducts() {
    try {
      state.merchantProducts = await request("/merchant/products");
    } catch {
      state.merchantProducts = [];
    }
  }

  async function loadMerchantOrders() {
    try {
      state.merchantOrders = await request("/merchant/orders");
    } catch {
      state.merchantOrders = [];
    }
  }

  function merchantMetrics() {
    const paidOrders = state.merchantOrders.filter(order => Number(order.status) >= 2 && Number(order.status) !== 6);
    return {
      productCount: state.merchantProducts.length,
      orderCount: state.merchantOrders.length,
      pendingShip: state.merchantOrders.filter(order => [2, 3].includes(Number(order.status))).length,
      revenue: paidOrders.reduce((sum, order) => sum + Number(order.totalAmount || 0), 0)
    };
  }

  function renderMerchantApply() {
    els.commerceWorkspaceBody.innerHTML = `
      <div class="commerce-apply-layout">
        <section class="commerce-panel commerce-apply-copy">
          <small>开通商家中心</small>
          <strong>把内容种草转成可履约的商品生意</strong>
          <p>开通后可以发布商品、管理库存、创建优惠券，并处理商城订单。</p>
        </section>
        <form class="commerce-panel merchant-form commerce-form" id="merchantApplyForm">
          <label><span>店铺名称</span><input name="name" required maxlength="80" placeholder="比如：探店优选旗舰店"></label>
          <label class="merchant-avatar-upload">
            <span>店铺头像</span>
            <input id="merchantAvatarFile" name="avatarFile" type="file" accept="image/*">
            <input name="avatar" type="hidden">
            <b>选择图片</b>
            <small>可选，提交时自动上传。</small>
          </label>
          <div class="merchant-avatar-preview" id="merchantAvatarPreview"></div>
          <div class="commerce-form-grid">
            <label><span>客服电话</span><input name="phone" placeholder="比如：400-100-1204"></label>
            <label><span>发货地址</span><input name="address" placeholder="比如：杭州内容电商产业园"></label>
          </div>
          <label><span>店铺简介</span><textarea name="description" rows="4" placeholder="介绍你的商品定位和服务"></textarea></label>
          <button class="publish-button" type="submit">开通商家中心</button>
        </form>
      </div>
    `;
    document.querySelector("#merchantApplyForm").addEventListener("submit", submitMerchantApply);
    document.querySelector("#merchantAvatarFile")?.addEventListener("change", renderMerchantAvatarPreview);
  }

  async function submitMerchantApply(event) {
    event.preventDefault();
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    const payload = {
      name: String(form.get("name") || "").trim(),
      avatar: String(form.get("avatar") || "").trim(),
      phone: String(form.get("phone") || "").trim(),
      address: String(form.get("address") || "").trim(),
      description: String(form.get("description") || "").trim()
    };
    const submitButton = formElement.querySelector(".publish-button");
    try {
      if (submitButton) {
        submitButton.disabled = true;
        submitButton.textContent = "提交中";
      }
      payload.avatar = await uploadMerchantSingleImage(formElement.elements.avatarFile?.files?.[0], payload.avatar);
      await request("/merchant/apply", {
        method: "POST",
        body: JSON.stringify(payload)
      });
      showStatus("商家中心已开通。");
      await loadMerchantCenter();
    } catch (error) {
      showStatus(error.message || "开通商家中心失败。");
    } finally {
      if (submitButton) {
        submitButton.disabled = false;
        submitButton.textContent = "开通商家中心";
      }
    }
  }

  function renderMerchantDashboard(tab = "overview") {
    state.merchantTab = tab;
    const metrics = merchantMetrics();
    els.commerceWorkspaceBody.innerHTML = `
      <section class="commerce-panel merchant-hero">
        <div>
          <small>当前店铺</small>
          <strong>${escapeHtml(state.merchant.name)}</strong>
          <span>${escapeHtml(state.merchant.description || "内容电商商家")}</span>
        </div>
        <div class="commerce-kpi-grid">
          ${commerceKpi("商品", metrics.productCount)}
          ${commerceKpi("订单", metrics.orderCount)}
          ${commerceKpi("成交额", `¥${formatMoney(metrics.revenue)}`)}
          ${commerceKpi("待发货", metrics.pendingShip)}
        </div>
      </section>
      <nav class="commerce-subtabs" aria-label="商家中心分区">
        ${[
          ["overview", "概览"],
          ["products", "商品管理"],
          ["orders", "订单处理"],
          ["vouchers", "优惠券"]
        ].map(([key, label]) => `
          <button type="button" class="${tab === key ? "is-active" : ""}" data-merchant-tab="${key}">${label}</button>
        `).join("")}
      </nav>
      <div class="merchant-workspace-panel">
        ${renderMerchantTab(tab, metrics)}
      </div>
    `;
    bindMerchantDashboardEvents();
  }

  function commerceKpi(label, value) {
    return `<span><b>${escapeHtml(value)}</b><small>${escapeHtml(label)}</small></span>`;
  }

  function renderMerchantTab(tab, metrics) {
    if (tab === "products") return renderMerchantProductManager();
    if (tab === "orders") return renderMerchantOrderManager();
    if (tab === "vouchers") return renderMerchantVoucherManager();
    return `
      <div class="commerce-overview-grid">
        <section class="commerce-panel">
          <div class="commerce-section-head">
            <div><strong>今日待办</strong><span>优先处理会影响履约体验的事项</span></div>
          </div>
          <div class="commerce-task-list">
            <button type="button" data-merchant-tab="orders"><b>${metrics.pendingShip}</b><span>待发货订单</span></button>
            <button type="button" data-merchant-tab="products"><b>${state.merchantProducts.filter(product => Number(product.stock || 0) <= 5).length}</b><span>低库存商品</span></button>
            <button type="button" data-merchant-tab="vouchers"><b>券</b><span>创建促销优惠</span></button>
          </div>
        </section>
        <section class="commerce-panel">
          <div class="commerce-section-head">
            <div><strong>最近订单</strong><span>最新交易动态</span></div>
            <button class="soft-button" type="button" data-merchant-tab="orders">查看全部</button>
          </div>
          <div class="commerce-compact-list">
            ${state.merchantOrders.slice(0, 5).map(renderMerchantOrderRow).join("") || `<p class="empty-text">还没有订单。</p>`}
          </div>
        </section>
      </div>
    `;
  }

  function renderMerchantProductManager() {
    return `
      <div class="commerce-manager-grid">
        <form class="commerce-panel commerce-form" id="merchantProductForm">
          <div class="commerce-section-head">
            <div><strong>发布商品</strong><span>上传图片，填写价格和库存即可上架</span></div>
          </div>
          <label>商品标题<input name="title" required maxlength="128" placeholder="比如：城市露营咖啡礼盒"></label>
          <label>卖点<input name="subTitle" maxlength="255" placeholder="一句话说明规格、亮点或适合场景"></label>
          <label class="merchant-image-upload">
            <span>商品图片</span>
            <input id="merchantProductImages" name="imageFiles" type="file" accept="image/*" multiple required>
            <input name="images" type="hidden">
            <b>选择图片</b>
            <small>支持多张图片，发布时自动上传。</small>
          </label>
          <div class="merchant-image-preview" id="merchantProductPreview"></div>
          <div class="commerce-form-grid">
            <label>价格(元)<input name="priceYuan" type="number" min="0.01" step="0.01" required></label>
            <label>库存<input name="stock" type="number" min="0" required></label>
            <label>类目
              <select name="category">
                <option value="food">美食套餐</option>
                <option value="coffee">咖啡</option>
                <option value="gear">装备</option>
                <option value="all">其他</option>
              </select>
            </label>
          </div>
          <button class="publish-button" type="submit">发布商品</button>
        </form>
        <section class="commerce-panel">
          <div class="commerce-section-head">
            <div><strong>商品管理</strong><span>${state.merchantProducts.length} 件商品</span></div>
          </div>
          <div class="commerce-table-list">${renderMerchantProducts()}</div>
        </section>
      </div>
    `;
  }

  function renderMerchantProducts() {
    if (!state.merchantProducts.length) return `<p class="empty-text">还没有发布商品。</p>`;
    return state.merchantProducts.map(product => `
      <article class="commerce-admin-row">
        <img src="${commerceImage(String(product.images || "").split(",")[0])}" alt="${escapeHtml(product.title)}" onerror="${commerceImageFallbackAttr()}">
        <div>
          <strong>${escapeHtml(product.title)}</strong>
          <span>¥${formatMoney(product.price)} · 库存 ${product.stock} · 已售 ${product.sold || 0}</span>
        </div>
        <span class="commerce-status ${Number(product.status) === 1 ? "status-2" : "status-6"}">${Number(product.status) === 1 ? "上架中" : "已下架"}</span>
        <button class="ghost-button" type="button" data-product-toggle="${product.id}" data-next-status="${Number(product.status) === 1 ? 0 : 1}">
          ${Number(product.status) === 1 ? "下架" : "上架"}
        </button>
      </article>
    `).join("");
  }

  function renderMerchantOrderManager() {
    return `
      <section class="commerce-panel">
        <div class="commerce-section-head">
          <div><strong>订单处理</strong><span>处理支付、发货和售后中的订单</span></div>
        </div>
        <div class="commerce-table-list">${renderMerchantOrders()}</div>
      </section>
    `;
  }

  function renderMerchantOrders() {
    if (!state.merchantOrders.length) return `<p class="empty-text">还没有订单。</p>`;
    return state.merchantOrders.map(renderMerchantOrderRow).join("");
  }

  function renderMerchantOrderRow(order) {
    return `
      <article class="commerce-admin-row">
        <img src="${commerceImage(order.productImage)}" alt="${escapeHtml(order.productTitle)}" onerror="${commerceImageFallbackAttr()}">
        <div>
          <strong>${escapeHtml(order.productTitle)}</strong>
          <span>订单号 ${order.id} · ¥${formatMoney(order.totalAmount)}</span>
        </div>
        <span class="commerce-status ${commerceStatusClass(order.status)}">${mallOrderStatus(order.status)}</span>
        ${Number(order.status) === 2 ? `<button class="publish-button" type="button" data-order-ship="${order.id}">发货</button>` : `<button class="soft-button" type="button" disabled>无需处理</button>`}
      </article>
    `;
  }

  function renderMerchantVoucherManager() {
    return `
      <form class="commerce-panel commerce-form" id="merchantVoucherForm">
        <div class="commerce-section-head">
          <div><strong>创建优惠券</strong><span>支持全店可用或绑定单个商品</span></div>
        </div>
        <label>优惠券标题<input name="title" required maxlength="80" placeholder="比如：满50减10"></label>
        <label>副标题<input name="subTitle" maxlength="120" placeholder="比如：商城全店可用"></label>
        <div class="commerce-form-grid">
          <label>使用门槛(元)<input name="payValueYuan" type="number" min="0" step="0.01" value="50"></label>
          <label>优惠金额(元)<input name="actualValueYuan" type="number" min="0.01" step="0.01" value="10" required></label>
          <label>绑定商品
            <select name="productId">
              <option value="">全店可用</option>
              ${state.merchantProducts.map(product => `<option value="${product.id}">${escapeHtml(product.title)}</option>`).join("")}
            </select>
          </label>
          <label>规则<input name="rules" placeholder="不与其他券叠加"></label>
        </div>
        <button class="publish-button" type="submit">创建优惠券</button>
      </form>
    `;
  }

  function bindMerchantDashboardEvents() {
    els.commerceWorkspaceBody.querySelectorAll("[data-merchant-tab]").forEach(button => {
      button.addEventListener("click", () => renderMerchantDashboard(button.dataset.merchantTab));
    });
    els.commerceWorkspaceBody.querySelector("#merchantProductForm")?.addEventListener("submit", submitMerchantProduct);
    els.commerceWorkspaceBody.querySelector("#merchantVoucherForm")?.addEventListener("submit", submitMerchantVoucher);
    els.commerceWorkspaceBody.querySelector("#merchantProductImages")?.addEventListener("change", renderMerchantProductImagePreview);
    bindMerchantAssistant();
    els.commerceWorkspaceBody.querySelectorAll("[data-product-toggle]").forEach(button => {
      button.addEventListener("click", () => toggleMerchantProduct(button.dataset.productToggle, button.dataset.nextStatus));
    });
    els.commerceWorkspaceBody.querySelectorAll("[data-order-ship]").forEach(button => {
      button.addEventListener("click", () => shipMerchantOrder(button.dataset.orderShip));
    });
  }

  function bindMerchantAssistant() {
    const form = els.commerceWorkspaceBody.querySelector("#merchantProductForm");
    if (!form) return;
    let timer;
    const trigger = () => {
      window.clearTimeout(timer);
      timer = window.setTimeout(() => loadMerchantCopy(form), 1000);
    };
    form.elements.title?.addEventListener("input", trigger);
    form.elements.subTitle?.addEventListener("input", trigger);
  }

  async function loadMerchantCopy(form) {
    const title = String(form.elements.title?.value || "").trim();
    const subTitle = String(form.elements.subTitle?.value || "").trim();
    if (title.length + subTitle.length < 8) return;
    let panel = document.querySelector("#merchantAiCopy");
    if (!panel) {
      form.querySelector(".publish-button").insertAdjacentHTML("beforebegin", `
        <section class="ai-inline-card merchant-ai-copy" id="merchantAiCopy">
          <strong>商家文案建议</strong>
          <p>正在生成标题、卖点和优惠券文案...</p>
        </section>
      `);
      panel = document.querySelector("#merchantAiCopy");
    }
    try {
      const data = await aiFlow("/ai/flow/merchant-copy", {
        content: `${title}\n${subTitle}`,
        scenario: "商品发布"
      });
      panel.innerHTML = `<strong>商家文案建议</strong><p>${escapeHtml(data.answer || "暂时没有建议。")}</p>`;
    } catch {
      panel.innerHTML = `<strong>商家文案建议</strong><p>商家文案建议暂时不可用。</p>`;
    }
  }

  function renderMerchantProductImagePreview(event) {
    const preview = els.commerceWorkspaceBody.querySelector("#merchantProductPreview");
    if (!preview) return;
    const files = Array.from(event.currentTarget.files || []);
    preview.innerHTML = files.length
      ? files.map(file => `<img src="${URL.createObjectURL(file)}" alt="${escapeHtml(file.name)}">`).join("")
      : "";
  }

  function renderMerchantAvatarPreview(event) {
    const preview = els.commerceWorkspaceBody.querySelector("#merchantAvatarPreview");
    const file = event.currentTarget.files?.[0];
    if (!preview) return;
    preview.innerHTML = file ? `<img src="${URL.createObjectURL(file)}" alt="${escapeHtml(file.name)}">` : "";
  }

  async function uploadMerchantSingleImage(file, fallback = "") {
    if (!file) return String(fallback || "").trim();
    const formData = new FormData();
    formData.append("file", file);
    const result = await request("/upload/note", { method: "POST", body: formData });
    return typeof uploadResultUrl === "function" ? uploadResultUrl(result) : merchantUploadResultUrl(result);
  }

  async function uploadMerchantProductImages(formElement) {
    const files = Array.from(formElement.elements.imageFiles?.files || []);
    if (!files.length) return String(formElement.elements.images?.value || "").trim();
    const uploaded = [];
    for (const file of files) {
      const formData = new FormData();
      formData.append("file", file);
      const result = await request("/upload/note", { method: "POST", body: formData });
      uploaded.push(typeof uploadResultUrl === "function" ? uploadResultUrl(result) : merchantUploadResultUrl(result));
    }
    return uploaded.filter(Boolean).join(",");
  }

  function merchantUploadResultUrl(result) {
    if (typeof result === "string") return "/imgs" + result;
    return result?.url || (result?.objectName ? "/imgs" + result.objectName : "");
  }

  async function submitMerchantProduct(event) {
    event.preventDefault();
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    const submitButton = formElement.querySelector(".publish-button");
    const payload = {
      title: String(form.get("title") || "").trim(),
      subTitle: String(form.get("subTitle") || "").trim(),
      images: "",
      price: Math.round(Number(form.get("priceYuan") || 0) * 100),
      originPrice: null,
      stock: Number(form.get("stock") || 0),
      category: form.get("category"),
      status: 1
    };
    try {
      if (submitButton) {
        submitButton.disabled = true;
        submitButton.textContent = "上传图片中";
      }
      payload.images = await uploadMerchantProductImages(formElement);
      if (submitButton) submitButton.textContent = "发布中";
      await request("/merchant/products", {
        method: "POST",
        body: JSON.stringify(payload)
      });
      showStatus("商品已发布。");
      state.merchantTab = "products";
      await loadMerchantCenter();
      loadProducts();
    } catch (error) {
      showStatus(error.message || "商品发布失败。");
    } finally {
      if (submitButton) {
        submitButton.disabled = false;
        submitButton.textContent = "发布商品";
      }
    }
  }

  async function submitMerchantVoucher(event) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const productId = String(form.get("productId") || "").trim();
    const payload = {
      productId: productId ? Number(productId) : null,
      title: String(form.get("title") || "").trim(),
      subTitle: String(form.get("subTitle") || "").trim(),
      rules: String(form.get("rules") || "").trim() || "商城订单可用，不与其他券叠加",
      payValue: Math.round(Number(form.get("payValueYuan") || 0) * 100),
      actualValue: Math.round(Number(form.get("actualValueYuan") || 0) * 100)
    };
    try {
      await request("/merchant/vouchers", {
        method: "POST",
        body: JSON.stringify(payload)
      });
      showStatus("商城优惠券已创建。");
      event.currentTarget.reset();
    } catch (error) {
      showStatus(error.message || "优惠券创建失败。");
    }
  }

  async function toggleMerchantProduct(productId, nextStatus) {
    try {
      await request(`/merchant/products/${productId}`, {
        method: "PUT",
        body: JSON.stringify({ status: Number(nextStatus) })
      });
      showStatus(Number(nextStatus) === 1 ? "商品已上架。" : "商品已下架。");
      state.merchantTab = "products";
      await loadMerchantCenter();
      loadProducts();
    } catch (error) {
      showStatus(error.message || "商品状态更新失败。");
    }
  }

  async function shipMerchantOrder(orderId) {
    try {
      await request(`/merchant/orders/${orderId}/ship`, { method: "POST" });
      showStatus(`订单 ${orderId} 已发货。`);
      state.merchantTab = "orders";
      await loadMerchantCenter();
    } catch (error) {
      showStatus(error.message || "发货失败。");
    }
  }

  window.openMerchantCenter = openMerchantCenter;
})();
