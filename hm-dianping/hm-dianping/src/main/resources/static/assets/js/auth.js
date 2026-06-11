// auth.js — User session, login dialog, and profile rendering
// Depends on utils.js (state, els, token, request, showStatus, requireLogin, resetAndLoad)
(function() {

let codeCountdownTimer = null;

// ------------------------------
// User session and profile panel
// ------------------------------
async function initUser() {
  if (!token()) {
    stopNotificationStream?.();
    renderUser(null);
    reloadScopedLocalState?.();
    return;
  }
  try {
    const user = await loadAccount();
    state.currentUser = user;
    reloadScopedLocalState?.();
    renderUser(user);
    loadProfileStats();
    loadNotificationSettings?.();
    refreshNotificationBadge?.();
  } catch {
    localStorage.removeItem("xiaohongshu_token");
    stopNotificationStream?.();
    renderUser(null);
    reloadScopedLocalState?.();
  }
}
window.initUser = initUser;

async function loadAccount() {
  try {
    const account = await request("/user/account");
    if (account?.id) return account;
  } catch {
    // 兼容旧接口，账号摘要失败时退回基础用户信息。
  }
  return request("/user/me");
}
window.loadAccount = loadAccount;

function renderUser(user) {
  const adminButton = document.querySelector("#openAdminCenter");
  const loginButton = document.querySelector("#loginButton");
  if (!user) {
    state.currentUser = null;
    loginButton.textContent = "登录";
    loginButton.classList.remove("is-logged-in");
    loginButton.innerHTML = "登录";
    if (els.accountAvatar) els.accountAvatar.src = fallbackAvatar;
    if (els.accountName) els.accountName.textContent = "未登录";
    if (els.accountMeta) els.accountMeta.textContent = "登录后同步你的内容和订单";
    hideAccountPopover();
    if (adminButton) adminButton.hidden = true;
    return;
  }
  state.currentUser = user;
  loginButton.classList.add("is-logged-in");
  loginButton.innerHTML = `
    <img src="${normalizeImage(user.icon) || fallbackAvatar}" alt="">
    <span>${escapeHtml(user.nickName || "已登录")}</span>
  `;
  if (els.accountAvatar) els.accountAvatar.src = normalizeImage(user.icon) || fallbackAvatar;
  if (els.accountName) els.accountName.textContent = user.nickName || "已登录";
  if (els.accountMeta) els.accountMeta.textContent = user.maskedPhone || roleLabel(user.role);
  if (adminButton) adminButton.hidden = Number(user.role || 1) < 3;
}
window.renderUser = renderUser;

function openLoginDialog() {
  if (token() && state.currentUser) {
    toggleAccountPopover();
    return;
  }
  setLoginFeedback("");
  els.loginDialog.showModal();
  setTimeout(() => els.loginForm.elements.phone?.focus(), 0);
}
window.openLoginDialog = openLoginDialog;

function toggleAccountPopover(force) {
  if (!els.accountPopover) return;
  if (!token() || !state.currentUser) {
    els.accountPopover.hidden = true;
    return;
  }
  els.accountPopover.hidden = force === undefined ? !els.accountPopover.hidden : !force;
}
window.toggleAccountPopover = toggleAccountPopover;

function hideAccountPopover() {
  if (els.accountPopover) els.accountPopover.hidden = true;
}
window.hideAccountPopover = hideAccountPopover;

function roleLabel(role) {
  return {
    1: "普通用户",
    2: "商家用户",
    3: "运营管理员"
  }[Number(role || 1)] || "普通用户";
}
window.roleLabel = roleLabel;

// ------------------------------
// Login workflow
// ------------------------------
async function sendCode() {
  const phone = els.loginForm.elements.phone.value.trim();
  if (!phone) {
    setLoginFeedback("请先输入手机号。", "error");
    return;
  }
  const button = document.querySelector("#sendCodeButton");
  if (button?.disabled) return;
  try {
    setLoginFeedback("正在获取验证码...");
    const data = await request(`/user/code?phone=${encodeURIComponent(phone)}`, { method: "POST" });
    if (data?.debugCode) {
      els.loginForm.elements.code.value = data.debugCode;
      setLoginFeedback(`验证码已填入，有效期 ${Math.round((data.ttlSeconds || 120) / 60)} 分钟。`, "success");
    } else {
      setLoginFeedback("验证码已发送，请注意查收。", "success");
    }
    startCodeCountdown(Number(data?.cooldownSeconds || 60));
  } catch (error) {
    setLoginFeedback(error.message || "验证码发送失败，请检查手机号格式。", "error");
  }
}
window.sendCode = sendCode;

async function submitLogin(event) {
  if (event.submitter && event.submitter.value === "cancel") return;
  event.preventDefault();
  setLoginFeedback("");
  const payload = {
    phone: els.loginForm.elements.phone.value.trim(),
    code: els.loginForm.elements.code.value.trim()
  };
  if (!payload.phone || !payload.code) {
    setLoginFeedback("请输入手机号和验证码。", "error");
    return;
  }
  if (els.loginSubmitButton) {
    els.loginSubmitButton.disabled = true;
    els.loginSubmitButton.textContent = "登录中";
  }
  try {
    const response = await request("/user/login", {
      method: "POST",
      body: JSON.stringify(payload)
    });
    const loginToken = typeof response === "string" ? response : response?.token;
    if (!loginToken) throw new Error("登录响应缺少 token");
    localStorage.setItem("xiaohongshu_token", loginToken);
    if (response?.expiresInSeconds) {
      localStorage.setItem("xiaohongshu_token_expire_at", String(Date.now() + Number(response.expiresInSeconds) * 1000));
    }
    els.loginDialog.close();
    if (response?.user) {
      renderUser(response.user);
      reloadScopedLocalState?.();
    } else {
      await initUser();
    }
    if (state.afterLoginAction) {
      const action = state.afterLoginAction;
      state.afterLoginAction = null;
      runAfterLoginAction(action);
      return;
    }
    state.afterLoginAction = null;
    showStatus("登录成功。");
    resetAndLoad();
  } catch (error) {
    setLoginFeedback(error.message || "登录失败，请确认验证码正确。", "error");
  } finally {
    if (els.loginSubmitButton) {
      els.loginSubmitButton.disabled = false;
      els.loginSubmitButton.textContent = "登录";
    }
  }
}
window.submitLogin = submitLogin;

function runAfterLoginAction(action) {
  if (typeof action === "function") {
    action();
    return;
  }
  if (action === "profile" && typeof openMyProfile === "function") {
    openMyProfile();
    return;
  }
  if (action === "compose" && typeof openComposer === "function") {
    openComposer();
    return;
  }
  if (action === "compose-video" && typeof openComposer === "function") {
    openComposer("VIDEO");
    return;
  }
  if (action === "orders" && typeof openOrdersDialog === "function") {
    openOrdersDialog();
    return;
  }
  if (action === "cart" && typeof openCartDialog === "function") {
    openCartDialog();
    return;
  }
  if (action === "merchant" && typeof openMerchantCenter === "function") {
    openMerchantCenter();
    return;
  }
  if (action === "notifications" && typeof openNotificationDialog === "function") {
    openNotificationDialog();
  }
}
window.runAfterLoginAction = runAfterLoginAction;

function handleAccountAction(action) {
  hideAccountPopover();
  if (action === "profile") {
    openMyProfile?.();
    return;
  }
  if (action === "edit") {
    if (state.currentProfile?.isMe) {
      openProfileEdit?.();
    } else {
      openUserProfile?.(null).then(() => openProfileEdit?.());
    }
    return;
  }
  if (action === "orders") {
    openOrdersDialog?.();
    return;
  }
  if (action === "notifications") {
    openNotificationDialog?.();
    switchMessageMode?.("notifications");
    if (els.notificationSettings) els.notificationSettings.hidden = false;
    loadNotificationSettings?.();
    return;
  }
  if (action === "logout") {
    logout();
  }
}
window.handleAccountAction = handleAccountAction;

async function logout() {
  try {
    if (token()) {
      await request("/user/logout", { method: "POST" });
    }
  } catch {
    // 本地清理仍然要执行，避免过期 token 卡住用户。
  }
  localStorage.removeItem("xiaohongshu_token");
  localStorage.removeItem("xiaohongshu_token_expire_at");
  stopNotificationStream?.();
  renderUser(null);
  reloadScopedLocalState?.();
  state.currentProfile = null;
  showStatus("已退出登录。");
}
window.logout = logout;

function startCodeCountdown(seconds) {
  const button = document.querySelector("#sendCodeButton");
  if (!button) return;
  clearInterval(codeCountdownTimer);
  let remain = Math.max(1, Number(seconds || 60));
  button.disabled = true;
  button.textContent = `${remain}s 后重发`;
  codeCountdownTimer = setInterval(() => {
    remain -= 1;
    if (remain <= 0) {
      clearInterval(codeCountdownTimer);
      button.disabled = false;
      button.textContent = "获取验证码";
      return;
    }
    button.textContent = `${remain}s 后重发`;
  }, 1000);
}

function setLoginFeedback(message, type = "info") {
  if (!els.loginFeedback) return;
  els.loginFeedback.textContent = message || "";
  els.loginFeedback.dataset.type = type;
  els.loginFeedback.hidden = !message;
}
window.setLoginFeedback = setLoginFeedback;

})();
