// auth.js — User session, login dialog, and profile rendering
// Depends on utils.js (state, els, token, request, showStatus, requireLogin, resetAndLoad)
(function() {

// ------------------------------
// User session and profile panel
// ------------------------------
async function initUser() {
  if (!token()) {
    renderUser(null);
    return;
  }
  try {
    const user = await request("/user/me");
    state.currentUser = user;
    renderUser(user);
    loadProfileStats();
  } catch {
    localStorage.removeItem("hmdp_token");
    renderUser(null);
  }
}
window.initUser = initUser;

function renderUser(user) {
  const adminButton = document.querySelector("#openAdminCenter");
  if (!user) {
    document.querySelector("#loginButton").textContent = "登录";
    if (adminButton) adminButton.hidden = true;
    return;
  }
  document.querySelector("#loginButton").textContent = "已登录";
  if (adminButton) adminButton.hidden = Number(user.role || 1) < 3;
}
window.renderUser = renderUser;

// ------------------------------
// Login workflow
// ------------------------------
async function sendCode() {
  const phone = els.loginForm.elements.phone.value.trim();
  if (!phone) return;
  try {
    await request(`/user/code?phone=${encodeURIComponent(phone)}`, { method: "POST" });
    showStatus("验证码已生成，请查看后端日志。");
  } catch {
    showStatus("验证码发送失败，请检查手机号格式。");
  }
}
window.sendCode = sendCode;

async function submitLogin(event) {
  if (event.submitter && event.submitter.value === "cancel") return;
  event.preventDefault();
  const payload = {
    phone: els.loginForm.elements.phone.value.trim(),
    code: els.loginForm.elements.code.value.trim()
  };
  try {
    const loginToken = await request("/user/login", {
      method: "POST",
      body: JSON.stringify(payload)
    });
    localStorage.setItem("hmdp_token", loginToken);
    els.loginDialog.close();
    await initUser();
    if (state.afterLoginAction === "profile" && typeof openMyProfile === "function") {
      state.afterLoginAction = null;
      openMyProfile();
      return;
    }
    state.afterLoginAction = null;
    resetAndLoad();
  } catch {
    showStatus("登录失败，请确认验证码正确。");
  }
}
window.submitLogin = submitLogin;

})();
