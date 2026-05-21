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
    return;
  }
  try {
    const user = await request("/user/me");
    state.currentUser = user;
    renderUser(user);
    loadProfileStats();
    loadNotificationSettings?.();
    refreshNotificationBadge?.();
  } catch {
    localStorage.removeItem("hmdp_token");
    stopNotificationStream?.();
    renderUser(null);
  }
}
window.initUser = initUser;

function renderUser(user) {
  const adminButton = document.querySelector("#openAdminCenter");
  if (!user) {
    state.currentUser = null;
    document.querySelector("#loginButton").textContent = "登录";
    document.querySelector("#loginButton").classList.remove("is-logged-in");
    if (adminButton) adminButton.hidden = true;
    return;
  }
  state.currentUser = user;
  document.querySelector("#loginButton").textContent = user.nickName || "已登录";
  document.querySelector("#loginButton").classList.add("is-logged-in");
  if (adminButton) adminButton.hidden = Number(user.role || 1) < 3;
}
window.renderUser = renderUser;

function openLoginDialog() {
  if (token() && state.currentUser) {
    logout();
    return;
  }
  setLoginFeedback("");
  els.loginDialog.showModal();
  setTimeout(() => els.loginForm.elements.phone?.focus(), 0);
}
window.openLoginDialog = openLoginDialog;

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
      setLoginFeedback(`本地调试验证码已自动填入，有效期 ${Math.round((data.ttlSeconds || 120) / 60)} 分钟。`, "success");
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
    localStorage.setItem("hmdp_token", loginToken);
    if (response?.expiresInSeconds) {
      localStorage.setItem("hmdp_token_expire_at", String(Date.now() + Number(response.expiresInSeconds) * 1000));
    }
    els.loginDialog.close();
    if (response?.user) {
      renderUser(response.user);
    } else {
      await initUser();
    }
    if (state.afterLoginAction === "profile" && typeof openMyProfile === "function") {
      state.afterLoginAction = null;
      openMyProfile();
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

async function logout() {
  try {
    if (token()) {
      await request("/user/logout", { method: "POST" });
    }
  } catch {
    // 本地清理仍然要执行，避免过期 token 卡住用户。
  }
  localStorage.removeItem("hmdp_token");
  localStorage.removeItem("hmdp_token_expire_at");
  stopNotificationStream?.();
  renderUser(null);
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
