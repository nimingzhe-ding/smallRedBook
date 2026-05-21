// customer-service.js — global smart customer service and order support
(function() {

function openCustomerServiceDialog(question = "") {
  state.customerServiceContext = buildCustomerServiceContext(state.customerServiceContext || {});
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
  renderCustomerWelcome();
  appendCustomerMessage("user", question);
  const pending = appendCustomerMessage("assistant", "正在查询相关信息...");
  try {
    const answer = await fetchCustomerServiceAnswer(question, context);
    pending.querySelector("p").textContent = answer;
    return answer;
  } catch (error) {
    const message = error.message || "客服助手暂时不可用，请稍后再试。";
    pending.querySelector("p").textContent = message;
    return message;
  }
}
window.askCustomerServiceQuestion = askCustomerServiceQuestion;

async function fetchCustomerServiceAnswer(question, context = {}) {
  const mergedContext = buildCustomerServiceContext(context);
  const data = await aiFlow("/ai/flow/customer-service", {
    query: question,
    orderId: mergedContext.orderId || null,
    productId: mergedContext.productId || null,
    merchantId: mergedContext.merchantId || null,
    voucherId: mergedContext.voucherId || null,
    scenario: mergedContext.scenario || "customer-service"
  });
  return data.answer || "暂时没有生成有效回复。";
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
    voucherId: context.voucherId || selectedVoucherId || null
  };
}
window.buildCustomerServiceContext = buildCustomerServiceContext;

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

})();
