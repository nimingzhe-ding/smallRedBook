// customer-service.js — global smart customer service and order support
(function() {

function openCustomerServiceDialog(question = "") {
  state.customerServiceContext = buildCustomerServiceContext(state.customerServiceContext || {});
  renderCustomerServiceContext(state.customerServiceContext);
  renderCustomerQuickActions(state.customerServiceContext);
  renderCustomerWelcome();
  els.customerServiceInput.value = question || "";
  els.customerServiceDialog.showModal();
  setTimeout(() => els.customerServiceInput.focus(), 0);
}
window.openCustomerServiceDialog = openCustomerServiceDialog;

function renderCustomerWelcome() {
  if (els.customerServiceMessages.dataset.ready === "true") return;
  els.customerServiceMessages.dataset.ready = "true";
  els.customerServiceMessages.innerHTML = `
    <article class="cs-message cs-assistant">
      <strong>智能客服</strong>
      <p>我可以帮你查订单状态、退款规则、物流进度、优惠券和商品库存。涉及个人订单时，请先登录。</p>
    </article>`;
}

async function submitCustomerService(event) {
  event.preventDefault();
  const question = els.customerServiceInput.value.trim();
  if (!question) return;
  els.customerServiceInput.value = "";
  await askCustomerServiceQuestion(question);
}
window.submitCustomerService = submitCustomerService;

async function askCustomerServiceQuestion(question, context = {}) {
  const mergedContext = buildCustomerServiceContext(context);
  state.customerServiceContext = mergedContext;
  renderCustomerServiceContext(mergedContext);
  renderCustomerQuickActions(mergedContext);
  renderCustomerWelcome();
  appendCustomerMessage("user", question);
  const pending = appendCustomerMessage("assistant", "正在查询相关信息...");
  try {
    const response = await fetchCustomerServiceResponse(question, mergedContext);
    pending.querySelector("p").textContent = response.answer;
    renderCustomerMessageActions(pending, response.actions || []);
    renderCustomerServiceContext({ ...mergedContext, serverContext: response.context });
    return response.answer;
  } catch (error) {
    const message = error.message || "客服助手暂时不可用，请稍后再试。";
    pending.querySelector("p").textContent = message;
    return message;
  }
}
window.askCustomerServiceQuestion = askCustomerServiceQuestion;

async function fetchCustomerServiceResponse(question, context = {}) {
  const mergedContext = buildCustomerServiceContext(context);
  const data = await aiFlow("/ai/flow/customer-service", {
    query: question,
    orderId: mergedContext.orderId || null,
    productId: mergedContext.productId || null,
    merchantId: mergedContext.merchantId || null,
    voucherId: mergedContext.voucherId || null,
    scenario: mergedContext.scenario || "customer-service"
  });
  return {
    ...data,
    answer: data.answer || "暂时没有生成有效回复。",
    actions: Array.isArray(data.actions) ? data.actions : [],
    context: data.context || null
  };
}
window.fetchCustomerServiceResponse = fetchCustomerServiceResponse;

async function fetchCustomerServiceAnswer(question, context = {}) {
  const data = await fetchCustomerServiceResponse(question, context);
  return data.answer;
}
window.fetchCustomerServiceAnswer = fetchCustomerServiceAnswer;

function buildCustomerServiceContext(context = {}) {
  const product = state.currentProduct || null;
  const currentProductId = product?.id;
  const selectedVoucherId = state.selectedVoucherId || state.productVouchers?.[0]?.id;
  return {
    scenario: context.scenario || (currentProductId ? "product" : "customer-service"),
    orderId: context.orderId || null,
    productId: context.productId || currentProductId || null,
    merchantId: context.merchantId || product?.merchant?.id || product?.merchantId || null,
    voucherId: context.voucherId || selectedVoucherId || null,
    productTitle: context.productTitle || product?.title || null,
    productStock: context.productStock ?? product?.stock ?? null,
    orderStatus: context.orderStatus || null,
    serverContext: context.serverContext || null
  };
}
window.buildCustomerServiceContext = buildCustomerServiceContext;

function renderCustomerServiceContext(context = {}) {
  if (!els.customerServiceContext) return;
  const server = context.serverContext || {};
  const chips = [];
  const scenario = server.pageScenario || context.scenario || "customer-service";
  chips.push(`场景：${customerScenarioLabel(scenario)}`);
  if (server.loggedIn === false) chips.push("用户：未登录");
  else if (server.loggedIn === true || state.currentUser) chips.push("用户：已登录");
  const orderId = server.orderId || context.orderId;
  if (orderId) chips.push(`订单：#${orderId}`);
  if (server.orderStatus || context.orderStatus) chips.push(`状态：${server.orderStatus || context.orderStatus}`);
  const productTitle = server.productTitle || context.productTitle;
  const productId = server.productId || context.productId;
  if (productTitle) chips.push(`商品：${productTitle}`);
  else if (productId) chips.push(`商品：#${productId}`);
  const stock = server.productStock ?? context.productStock;
  if (stock !== null && stock !== undefined) chips.push(`库存：${stock}`);
  if (server.voucherTitle) chips.push(`优惠：${server.voucherTitle}`);
  els.customerServiceContext.innerHTML = chips.map(chip => `<span>${escapeHtml(chip)}</span>`).join("");
}

function customerScenarioLabel(scenario) {
  return {
    order: "订单",
    product: "商品",
    checkout: "结算",
    customer_service: "客服",
    "customer-service": "客服"
  }[String(scenario || "").toLowerCase()] || "客服";
}

function renderCustomerQuickActions(context = {}) {
  const container = document.querySelector(".customer-service-quick");
  if (!container) return;
  const scenario = String(context.scenario || "").toLowerCase();
  const questions = scenario === "order" || context.orderId
    ? [
        ["订单状态", "这笔订单现在是什么状态？"],
        ["退款", "这笔订单怎么申请退款？"],
        ["物流", "这笔订单物流到哪了？"],
        ["取消", "这笔订单能取消吗？"]
      ]
    : context.productId
      ? [
          ["库存", "这个商品还有库存吗？"],
          ["优惠", "这个商品有什么优惠？"],
          ["退款", "买了以后怎么退款？"],
          ["物流", "多久发货？"]
        ]
      : [
          ["退款", "怎么申请退款？"],
          ["物流", "怎么查看物流？"],
          ["优惠券", "优惠券怎么用？"],
          ["库存", "商品库存怎么查？"]
        ];
  container.innerHTML = questions.map(([label, value]) =>
    `<button type="button" data-cs-question="${escapeHtml(value)}">${escapeHtml(label)}</button>`
  ).join("");
  bindCustomerQuickButtons(container);
}

function bindCustomerQuickButtons(container = document) {
  container.querySelectorAll("[data-cs-question]").forEach(button => {
    button.addEventListener("click", () => {
      askCustomerServiceQuestion(button.dataset.csQuestion);
    });
  });
}
window.bindCustomerQuickButtons = bindCustomerQuickButtons;

function appendCustomerMessage(role, text) {
  const item = document.createElement("article");
  item.className = `cs-message cs-${role}`;
  item.innerHTML = `
    <strong>${role === "user" ? "我" : "智能客服"}</strong>
    <p>${escapeHtml(text)}</p>
  `;
  els.customerServiceMessages.appendChild(item);
  els.customerServiceMessages.scrollTop = els.customerServiceMessages.scrollHeight;
  return item;
}

window.appendCustomerMessage = appendCustomerMessage;

function renderCustomerMessageActions(messageEl, actions) {
  if (!messageEl || !actions.length) return;
  const wrap = document.createElement("div");
  wrap.className = "cs-actions";
  actions.forEach(action => {
    if (!action?.code || !action?.label) return;
    const button = document.createElement("button");
    button.type = "button";
    button.textContent = action.label;
    button.addEventListener("click", () => runCustomerServiceAction(action));
    wrap.appendChild(button);
  });
  if (wrap.children.length) messageEl.appendChild(wrap);
}

async function runCustomerServiceAction(action) {
  const code = action.code;
  if (code === "login") {
    els.customerServiceDialog.close();
    els.loginDialog.showModal();
    return;
  }
  if (code === "openOrders") {
    els.customerServiceDialog.close();
    if (typeof openOrdersDialog === "function") openOrdersDialog(action.orderId || null);
    return;
  }
  if (code === "openProduct" && action.productId) {
    els.customerServiceDialog.close();
    if (typeof switchMall === "function") switchMall();
    if (typeof openProduct === "function") openProduct(action.productId);
    return;
  }
  if (code === "payOrder" && action.orderId) {
    await customerOrderOperation(action.orderId, "pay", "支付成功。");
    return;
  }
  if (code === "cancelOrder" && action.orderId) {
    await customerOrderOperation(action.orderId, "cancel", "订单已取消。");
    return;
  }
  if (code === "applyRefund" && action.orderId) {
    await customerOrderOperation(action.orderId, "refund", "退款申请已提交。");
    return;
  }
  if (code === "viewLogistics") {
    askCustomerServiceQuestion("这笔订单物流到哪了？", {
      orderId: action.orderId || null,
      productId: action.productId || null,
      voucherId: action.voucherId || null,
      scenario: "order"
    });
  }
}
window.runCustomerServiceAction = runCustomerServiceAction;

async function customerOrderOperation(orderId, operation, successMessage) {
  try {
    await request(`/mall/orders/${orderId}/${operation}`, { method: "POST" });
    showStatus(successMessage);
    await askCustomerServiceQuestion("这笔订单现在是什么状态？", { orderId, scenario: "order" });
  } catch (error) {
    showStatus(error.message || "订单操作失败，请稍后再试。");
  }
}

})();
