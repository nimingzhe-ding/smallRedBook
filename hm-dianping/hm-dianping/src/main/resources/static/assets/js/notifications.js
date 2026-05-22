// notifications.js — Notification badge, dialog, and profile management
// Depends on utils.js (state, els, token, request, requireLogin, showStatus, normalizeImage,
//   escapeHtml, formatTime, fallbackAvatar, normalizeNote)
// Depends on feed.js (createNoteCard)
// References: openDrawer, openOrdersDialog, hideUnifiedSearch, showContentArea, renderCreatorGrowth
(function() {

// ------------------------------
// Notifications
// ------------------------------
async function refreshNotificationBadge() {
  if (!token() || !els.notificationBadge) {
    if (els.notificationBadge) els.notificationBadge.hidden = true;
    return;
  }
  try {
    const [notificationResult, messageResult] = await Promise.allSettled([
      request("/notifications/unread-count"),
      request("/messages/unread-count")
    ]);
    const notificationCount = notificationResult.status === "fulfilled"
      ? Number(notificationResult.value?.count || 0)
      : 0;
    const messageCount = messageResult.status === "fulfilled"
      ? Number(messageResult.value?.count || 0)
      : 0;
    const count = notificationCount + messageCount;
    els.notificationBadge.textContent = count > 99 ? "99+" : String(count);
    els.notificationBadge.hidden = count <= 0;
  } catch {
    els.notificationBadge.hidden = true;
  }
}
window.refreshNotificationBadge = refreshNotificationBadge;

async function openNotificationDialog() {
  if (!requireLoginThen("notifications")) return;
  setMobileTabActive("messages");
  switchMessageArea();
  switchMessageMode(state.messageMode || "dm");
  await loadDmConversations();
  renderDmThread();
  startMessagePolling();
  if (!state.notificationSettings) {
    loadNotificationSettings();
  }
  loadNotifications();
}
window.openNotificationDialog = openNotificationDialog;

function switchMessageArea() {
  state.mode = "messages";
  setFeedTabsVisible(false);
  els.contentArea.hidden = true;
  els.mallArea.hidden = true;
  els.videoArea.hidden = true;
  if (els.messageArea) els.messageArea.hidden = false;
  setMobileTabActive("messages");
  setMessageEntryActive(true);
  setMallActive(false);
  setVideoActive(false);
  pauseImmersiveVideos();
  closeDanmakuSource();
  hideStatus();
  window.scrollTo({ top: 0, behavior: "smooth" });
  startMessagePolling();
}
window.switchMessageArea = switchMessageArea;

async function loadNotifications() {
  if (!els.notificationList) return;
  els.notificationList.innerHTML = `<p class="empty-text">正在加载消息...</p>`;
  const unreadOnly = state.notificationFilter === "unread";
  const category = ["interaction", "order", "audit", "system"].includes(state.notificationFilter)
    ? state.notificationFilter
    : "all";
  try {
    const list = await request(`/notifications?unreadOnly=${unreadOnly}&category=${category}`);
    renderNotifications(Array.isArray(list) ? list : []);
  } catch {
    els.notificationList.innerHTML = `<p class="empty-text">消息加载失败，请稍后再试。</p>`;
  }
}
window.loadNotifications = loadNotifications;

function renderNotifications(list) {
  if (!list.length) {
    els.notificationList.innerHTML = `<p class="empty-text">暂时还没有消息。</p>`;
    return;
  }
  els.notificationList.innerHTML = list.map(item => {
    const payload = notificationPayload(item);
    const noteId = notificationTargetNoteId(item, payload);
    const orderId = notificationTargetOrderId(item, payload);
    const actorUserId = item.actorUserId || payload.actorUserId || "";
    const actorName = payload.actorName || payload.nickName || payload.nickname || "";
    const actorIcon = payload.actorIcon || payload.icon || "";
    return `
    <article class="notification-item${item.readFlag ? "" : " is-unread"}" data-id="${item.id}" data-note-id="${noteId || ""}" data-order-id="${orderId || ""}" data-actor-user-id="${actorUserId || ""}" data-actor-name="${escapeHtml(actorName)}" data-actor-icon="${escapeHtml(actorIcon)}" data-type="${escapeHtml(item.type || "")}">
      <div class="notification-item-body">
        <strong>${escapeHtml(item.title || notificationTypeLabel(item.type))}</strong>
        <span>${escapeHtml(item.content || "")}</span>
        <small><span class="notification-type-tag tag-${(item.type || "").split("_")[0].toLowerCase()}">${notificationTypeLabel(item.type)}</span> · ${formatTime(item.createTime)}</small>
      </div>
      <div class="notification-item-actions">
        ${actorUserId ? `<button class="notification-action-btn notification-dm-btn" data-action="dm" title="发私信">私信</button>` : ""}
        ${item.readFlag ? "" : `<button class="notification-action-btn" data-action="read" title="标记已读"><svg viewBox="0 0 24 24" width="16" height="16"><path d="M9 16.2 4.8 12l-1.4 1.4L9 19 21 7l-1.4-1.4z" fill="currentColor"/></svg></button>`}
        <button class="notification-action-btn" data-action="delete" title="删除"><svg viewBox="0 0 24 24" width="16" height="16"><path d="M19 6.4 17.6 5 12 10.6 6.4 5 5 6.4 10.6 12 5 17.6 6.4 19 12 13.4 17.6 19 19 17.6 13.4 12z" fill="currentColor"/></svg></button>
      </div>
    </article>
  `;
  }).join("");
}
window.renderNotifications = renderNotifications;

function notificationPayload(item) {
  const payload = item?.payload;
  if (!payload) return {};
  if (typeof payload === "object") return payload;
  try {
    return JSON.parse(payload);
  } catch {
    return {};
  }
}
window.notificationPayload = notificationPayload;

function notificationTargetNoteId(item, payload = notificationPayload(item)) {
  return item?.noteId || item?.blogId || payload.noteId || payload.blogId || "";
}
window.notificationTargetNoteId = notificationTargetNoteId;

function notificationTargetOrderId(item, payload = notificationPayload(item)) {
  return item?.orderId || payload.orderId || "";
}
window.notificationTargetOrderId = notificationTargetOrderId;

function notificationTypeLabel(type) {
  return {
    LIKE: "点赞",
    COLLECT: "收藏",
    COMMENT: "评论",
    REPLY: "回复",
    FOLLOW: "关注",
    ORDER_CREATED: "订单",
    ORDER_PAID: "订单",
    ORDER_SHIPPED: "发货",
    ORDER_COMPLETED: "完成",
    ORDER_CANCELLED: "取消",
    ORDER_REFUNDING: "退款",
    ORDER_REFUND_APPROVED: "退款",
    ORDER_REFUND_REJECTED: "退款",
    AUDIT_NOTE_RESTORED: "笔记审核",
    AUDIT_NOTE_HIDDEN: "笔记审核",
    AUDIT_COMMENT_RESTORED: "评论审核",
    AUDIT_COMMENT_HIDDEN: "评论审核",
    AUDIT_DANMAKU_RESTORED: "弹幕审核",
    AUDIT_DANMAKU_HIDDEN: "弹幕审核"
  }[type] || "通知";
}
window.notificationTypeLabel = notificationTypeLabel;

function notificationCategoryLabel(type) {
  const value = String(type || "").toUpperCase();
  if (value.startsWith("ORDER_")) return "订单";
  if (value.startsWith("AUDIT_")) return "审核";
  if (["LIKE", "COLLECT", "COMMENT", "REPLY", "FOLLOW"].includes(value)) return "互动";
  return "系统";
}
window.notificationCategoryLabel = notificationCategoryLabel;

// ------------------------------
// Direct messages (local first)
// ------------------------------
function dmStoreKey() {
  const userId = state.currentUser?.id || "guest";
  return `hmdp_dm_${userId}`;
}
window.dmStoreKey = dmStoreKey;

function loadDmConversations() {
  try {
    state.dmConversations = JSON.parse(localStorage.getItem(dmStoreKey()) || "[]");
  } catch {
    state.dmConversations = [];
  }
  if (!Array.isArray(state.dmConversations)) state.dmConversations = [];
  if (!state.dmConversations.length) {
    state.dmConversations = defaultDmConversations();
    saveDmConversations();
  }
  if (!state.activeDmId || !state.dmConversations.some(item => item.id === state.activeDmId)) {
    state.activeDmId = state.dmConversations[0]?.id || null;
  }
}
window.loadDmConversations = loadDmConversations;

function defaultDmConversations() {
  const now = new Date().toISOString();
  return [
    {
      id: "assistant",
      userId: "",
      name: "平台小助手",
      icon: "",
      unread: 0,
      messages: [
        { id: crypto.randomUUID(), from: "them", text: "可以从互动通知里点“私信”，也可以输入用户 ID 开始聊天。", time: now }
      ],
      updatedAt: now
    }
  ];
}

function saveDmConversations() {
  localStorage.setItem(dmStoreKey(), JSON.stringify(state.dmConversations || []));
}
window.saveDmConversations = saveDmConversations;

function switchMessageMode(mode) {
  state.messageMode = mode === "notifications" ? "notifications" : "dm";
  els.messageArea?.classList.toggle("is-notification-mode", state.messageMode === "notifications");
  els.messageModeDm?.classList.toggle("is-active", state.messageMode === "dm");
  els.messageModeNotify?.classList.toggle("is-active", state.messageMode === "notifications");
  if (els.dmPane) els.dmPane.hidden = state.messageMode !== "dm";
  if (els.notificationPane) els.notificationPane.hidden = state.messageMode !== "notifications";
}
window.switchMessageMode = switchMessageMode;

function findDmConversation(id) {
  return state.dmConversations.find(item => item.id === id);
}
window.findDmConversation = findDmConversation;

function upsertDmConversation(contact) {
  loadDmConversations();
  const userId = String(contact?.userId || "").trim();
  const name = String(contact?.name || contact?.nickName || "").trim();
  const id = userId ? `user-${userId}` : `contact-${name || Date.now()}`;
  let conversation = findDmConversation(id);
  if (!conversation) {
    conversation = {
      id,
      userId,
      name: name || (userId ? `用户 ${userId}` : "新会话"),
      icon: contact?.icon || "",
      unread: 0,
      messages: [],
      updatedAt: new Date().toISOString()
    };
    state.dmConversations.unshift(conversation);
  } else {
    if (name) conversation.name = name;
    if (contact?.icon) conversation.icon = contact.icon;
  }
  state.activeDmId = conversation.id;
  saveDmConversations();
  renderDmConversations();
  renderDmThread();
  switchMessageMode("dm");
  return conversation;
}
window.upsertDmConversation = upsertDmConversation;

function renderDmConversations() {
  if (!els.dmConversationList) return;
  loadDmConversations();
  if (!state.dmConversations.length) {
    els.dmConversationList.innerHTML = `<p class="empty-text">还没有私信。</p>`;
    return;
  }
  els.dmConversationList.innerHTML = state.dmConversations
    .slice()
    .sort((a, b) => new Date(b.updatedAt || 0) - new Date(a.updatedAt || 0))
    .map(item => {
      const latest = item.messages?.[item.messages.length - 1];
      return `
        <button class="dm-conversation${item.id === state.activeDmId ? " is-active" : ""}" type="button" data-dm-id="${escapeHtml(item.id)}">
          <img src="${normalizeImage(item.icon) || fallbackAvatar}" alt="">
          <span>
            <strong>${escapeHtml(item.name || "探店用户")}</strong>
            <small>${escapeHtml(latest?.text || "还没有聊天记录")}</small>
          </span>
          ${item.unread ? `<b>${item.unread > 99 ? "99+" : item.unread}</b>` : ""}
        </button>
      `;
    }).join("");
}
window.renderDmConversations = renderDmConversations;

function renderDmThread() {
  if (!els.dmThread) return;
  const conversation = findDmConversation(state.activeDmId);
  if (!conversation) {
    els.dmChatName.textContent = "选择一个会话";
    els.dmChatMeta.textContent = "从左侧选择私信，或输入用户 ID 新建聊天";
    els.dmThread.innerHTML = `<div class="dm-empty">还没有选择聊天。</div>`;
    els.dmInput.disabled = true;
    return;
  }
  conversation.unread = 0;
  saveDmConversations();
  els.dmInput.disabled = false;
  els.dmChatName.textContent = conversation.name || "探店用户";
  els.dmChatMeta.textContent = conversation.userId ? `用户 ID ${conversation.userId}` : "本地会话";
  const messages = conversation.messages || [];
  els.dmThread.innerHTML = messages.length ? messages.map(message => `
    <div class="dm-message ${message.from === "me" ? "is-me" : "is-them"}">
      <p>${escapeHtml(message.text)}</p>
      <span>${formatTime(message.time)}</span>
    </div>
  `).join("") : `<div class="dm-empty">还没有聊天记录，先打个招呼。</div>`;
  els.dmThread.scrollTop = els.dmThread.scrollHeight;
  renderDmConversations();
}
window.renderDmThread = renderDmThread;

function selectDmConversation(id) {
  state.activeDmId = id;
  renderDmThread();
}
window.selectDmConversation = selectDmConversation;

function sendDmMessage(text) {
  const value = String(text || "").trim();
  const conversation = findDmConversation(state.activeDmId);
  if (!value || !conversation) return;
  conversation.messages = conversation.messages || [];
  conversation.messages.push({
    id: crypto.randomUUID(),
    from: "me",
    text: value,
    time: new Date().toISOString()
  });
  conversation.updatedAt = new Date().toISOString();
  saveDmConversations();
  renderDmThread();
}
window.sendDmMessage = sendDmMessage;

function startDmFromInput() {
  const raw = String(els.dmSearchInput?.value || "").trim();
  if (!raw) {
    showStatus("输入用户 ID 或昵称后再发起私信。");
    return;
  }
  const isId = /^\d+$/.test(raw);
  upsertDmConversation({ userId: isId ? raw : "", name: isId ? `用户 ${raw}` : raw });
  if (els.dmSearchInput) els.dmSearchInput.value = "";
}
window.startDmFromInput = startDmFromInput;

function startDmFromNotification(item) {
  const actorUserId = item?.dataset.actorUserId;
  if (!actorUserId) return;
  upsertDmConversation({
    userId: actorUserId,
    name: item.dataset.actorName || `用户 ${actorUserId}`,
    icon: item.dataset.actorIcon || ""
  });
}
window.startDmFromNotification = startDmFromNotification;

function clearActiveDmConversation() {
  const conversation = findDmConversation(state.activeDmId);
  if (!conversation) return;
  conversation.messages = [];
  conversation.updatedAt = new Date().toISOString();
  saveDmConversations();
  renderDmThread();
}
window.clearActiveDmConversation = clearActiveDmConversation;

// ------------------------------
// Direct messages (real backend)
// ------------------------------
async function loadDmConversations(options = {}) {
  if (!token() || !els.dmConversationList) return;
  const silent = Boolean(options.silent);
  if (!silent && !state.dmConversations.length) {
    els.dmConversationList.innerHTML = `<p class="empty-text">正在加载私信...</p>`;
  }
  try {
    const list = await request("/messages/conversations");
    state.dmConversations = Array.isArray(list) ? list.map(normalizeDmConversation) : [];
    if (state.activeDmId && !state.dmConversations.some(item => String(item.id) === String(state.activeDmId))) {
      state.activeDmId = null;
      state.activeDmConversation = null;
      state.dmMessages = [];
    }
    if (!state.activeDmId && state.dmConversations.length && !isSmallScreen()) {
      state.activeDmId = state.dmConversations[0].id;
      state.activeDmConversation = state.dmConversations[0];
      loadDmThread({ silent: true });
    } else if (state.activeDmId) {
      state.activeDmConversation = findDmConversation(state.activeDmId) || state.activeDmConversation;
    }
    renderDmConversations();
    renderDmThread();
    refreshNotificationBadge();
  } catch (error) {
    if (!silent) {
      els.dmConversationList.innerHTML = `<p class="empty-text">${escapeHtml(error.message || "私信加载失败")}</p>`;
    }
  }
}
window.loadDmConversations = loadDmConversations;

function switchMessageMode(mode) {
  state.messageMode = mode === "notifications" ? "notifications" : "dm";
  els.messageArea?.classList.toggle("is-notification-mode", state.messageMode === "notifications");
  els.messageArea?.classList.toggle("is-dm-chat-open", state.messageMode === "dm" && state.dmMobileChatOpen);
  els.messageModeDm?.classList.toggle("is-active", state.messageMode === "dm");
  els.messageModeNotify?.classList.toggle("is-active", state.messageMode === "notifications");
  if (els.dmPane) els.dmPane.hidden = state.messageMode !== "dm";
  if (els.notificationPane) els.notificationPane.hidden = state.messageMode !== "notifications";
  if (state.messageMode === "dm") {
    startMessagePolling();
    if (state.activeDmId) startDmThreadPolling();
  } else {
    stopDmThreadPolling();
  }
}
window.switchMessageMode = switchMessageMode;

function findDmConversation(id) {
  return state.dmConversations.find(item => String(item.id) === String(id));
}
window.findDmConversation = findDmConversation;

function renderDmConversations() {
  if (!els.dmConversationList) return;
  if (!state.dmConversations.length) {
    els.dmConversationList.innerHTML = `<p class="empty-text">还没有私信，搜索用户开始聊天。</p>`;
    return;
  }
  els.dmConversationList.innerHTML = state.dmConversations.map(item => `
    <button class="dm-conversation${String(item.id) === String(state.activeDmId) ? " is-active" : ""}" type="button" data-dm-id="${escapeHtml(item.id)}">
      <img src="${normalizeImage(item.peerIcon) || fallbackAvatar}" alt="">
      <span>
        <strong>${escapeHtml(item.peerNickName || "探店用户")}</strong>
        <small>${escapeHtml(item.lastMessageContent || "还没有聊天记录")}</small>
      </span>
      <em>${formatDmTime(item.lastMessageTime || item.updatedAt)}</em>
      ${item.unreadCount ? `<b>${item.unreadCount > 99 ? "99+" : item.unreadCount}</b>` : ""}
    </button>
  `).join("");
}
window.renderDmConversations = renderDmConversations;

function renderDmThread() {
  if (!els.dmThread) return;
  const conversation = state.activeDmConversation || findDmConversation(state.activeDmId);
  state.activeDmConversation = conversation || null;
  els.messageArea?.classList.toggle("is-dm-chat-open", state.messageMode === "dm" && state.dmMobileChatOpen);
  if (!conversation) {
    if (els.dmChatName) els.dmChatName.textContent = "选择一个会话";
    if (els.dmChatMeta) els.dmChatMeta.textContent = "搜索用户或从左侧会话开始聊天";
    if (els.dmPeerAvatar) els.dmPeerAvatar.src = fallbackAvatar;
    if (els.dmOpenProfile) els.dmOpenProfile.hidden = true;
    els.dmThread.innerHTML = `<div class="dm-empty">还没有选择聊天。</div>`;
    if (els.dmInput) els.dmInput.disabled = true;
    return;
  }
  if (els.dmInput) els.dmInput.disabled = false;
  if (els.dmChatName) els.dmChatName.textContent = conversation.peerNickName || "探店用户";
  if (els.dmChatMeta) els.dmChatMeta.textContent = `ID ${conversation.peerUserId || ""}`;
  if (els.dmPeerAvatar) els.dmPeerAvatar.src = normalizeImage(conversation.peerIcon) || fallbackAvatar;
  if (els.dmOpenProfile) els.dmOpenProfile.hidden = !conversation.peerUserId;
  if (els.clearDmConversation) els.clearDmConversation.hidden = true;
  const messages = state.dmMessages || [];
  els.dmThread.innerHTML = messages.length ? messages.map(message => `
    <div class="dm-message ${message.isMe ? "is-me" : "is-them"}">
      <p>${escapeHtml(message.content || "")}</p>
      <span>${formatTime(message.createTime)}</span>
    </div>
  `).join("") : `<div class="dm-empty">还没有聊天记录，先打个招呼。</div>`;
  els.dmThread.scrollTop = els.dmThread.scrollHeight;
  renderDmConversations();
}
window.renderDmThread = renderDmThread;

async function selectDmConversation(id) {
  const conversation = findDmConversation(id);
  if (!conversation) return;
  state.activeDmId = conversation.id;
  state.activeDmConversation = conversation;
  state.dmMobileChatOpen = true;
  renderDmConversations();
  await loadDmThread();
  startDmThreadPolling();
}
window.selectDmConversation = selectDmConversation;

async function loadDmThread(options = {}) {
  if (!state.activeDmId || state.messageMode !== "dm") return;
  try {
    const messages = await request(`/messages/conversations/${state.activeDmId}/messages?limit=30`);
    state.dmMessages = Array.isArray(messages) ? messages : [];
    await request(`/messages/conversations/${state.activeDmId}/read`, { method: "POST" });
    const conversation = findDmConversation(state.activeDmId);
    if (conversation) conversation.unreadCount = 0;
    renderDmThread();
    refreshNotificationBadge();
  } catch (error) {
    if (!options.silent) showStatus(error.message || "聊天加载失败");
  }
}
window.loadDmThread = loadDmThread;

async function sendDmMessage(text) {
  const value = String(text || "").trim();
  if (!value || !state.activeDmId) return;
  try {
    await request(`/messages/conversations/${state.activeDmId}/messages`, {
      method: "POST",
      body: JSON.stringify({ content: value })
    });
    if (els.dmInput) els.dmInput.value = "";
    await Promise.all([loadDmThread({ silent: true }), loadDmConversations({ silent: true })]);
  } catch (error) {
    showStatus(error.message || "私信发送失败");
  }
}
window.sendDmMessage = sendDmMessage;

async function startDmFromInput() {
  await searchDmUsers();
}
window.startDmFromInput = startDmFromInput;

async function searchDmUsers() {
  const keyword = String(els.dmSearchInput?.value || "").trim();
  if (!els.dmSearchResults) return;
  if (!keyword) {
    els.dmSearchResults.hidden = true;
    els.dmSearchResults.innerHTML = "";
    return;
  }
  els.dmSearchResults.hidden = false;
  els.dmSearchResults.innerHTML = `<p class="empty-text">正在搜索...</p>`;
  try {
    const users = await request(`/messages/users?keyword=${encodeURIComponent(keyword)}`);
    state.dmUsers = Array.isArray(users) ? users : [];
    renderDmSearchResults();
  } catch (error) {
    els.dmSearchResults.innerHTML = `<p class="empty-text">${escapeHtml(error.message || "用户搜索失败")}</p>`;
  }
}
window.searchDmUsers = searchDmUsers;

function renderDmSearchResults() {
  if (!els.dmSearchResults) return;
  if (!state.dmUsers.length) {
    els.dmSearchResults.innerHTML = `<p class="empty-text">没有找到用户。</p>`;
    return;
  }
  els.dmSearchResults.innerHTML = state.dmUsers.map(user => `
    <button type="button" class="dm-user-result" data-dm-user-id="${user.id}">
      <img src="${normalizeImage(user.icon) || fallbackAvatar}" alt="">
      <span><strong>${escapeHtml(user.nickName || "探店用户")}</strong><small>ID ${user.id}</small></span>
      <em>私信</em>
    </button>
  `).join("");
}
window.renderDmSearchResults = renderDmSearchResults;

async function openDmWithUser(userId) {
  if (!userId || !requireLogin()) return;
  try {
    switchMessageArea();
    switchMessageMode("dm");
    const conversation = normalizeDmConversation(await request("/messages/conversations", {
      method: "POST",
      body: JSON.stringify({ peerUserId: Number(userId) })
    }));
    state.activeDmId = conversation.id;
    state.activeDmConversation = conversation;
    state.dmMobileChatOpen = true;
    if (els.dmSearchInput) els.dmSearchInput.value = "";
    if (els.dmSearchResults) {
      els.dmSearchResults.hidden = true;
      els.dmSearchResults.innerHTML = "";
    }
    await loadDmConversations({ silent: true });
    await loadDmThread({ silent: true });
    startDmThreadPolling();
  } catch (error) {
    showStatus(error.message || "无法发起私信");
  }
}
window.openDmWithUser = openDmWithUser;

function startDmFromNotification(item) {
  const actorUserId = item?.dataset.actorUserId;
  if (actorUserId) openDmWithUser(actorUserId);
}
window.startDmFromNotification = startDmFromNotification;

function clearActiveDmConversation() {
  state.dmMessages = [];
  renderDmThread();
}
window.clearActiveDmConversation = clearActiveDmConversation;

function closeDmMobileChat() {
  state.dmMobileChatOpen = false;
  els.messageArea?.classList.remove("is-dm-chat-open");
}
window.closeDmMobileChat = closeDmMobileChat;

function startMessagePolling() {
  if (!token() || state.mode !== "messages") return;
  if (!state.messagePollTimer) {
    state.messagePollTimer = setInterval(() => {
      if (state.mode === "messages") loadDmConversations({ silent: true });
    }, 5000);
  }
  if (state.messageMode === "dm" && state.activeDmId) startDmThreadPolling();
}
window.startMessagePolling = startMessagePolling;

function stopMessagePolling() {
  if (state.messagePollTimer) clearInterval(state.messagePollTimer);
  state.messagePollTimer = null;
  stopDmThreadPolling();
}
window.stopMessagePolling = stopMessagePolling;

function startDmThreadPolling() {
  if (!token() || state.mode !== "messages" || state.messageMode !== "dm" || !state.activeDmId) return;
  if (state.dmThreadPollTimer) return;
  state.dmThreadPollTimer = setInterval(() => {
    if (state.mode === "messages" && state.messageMode === "dm" && state.activeDmId) {
      loadDmThread({ silent: true });
    }
  }, 3000);
}
window.startDmThreadPolling = startDmThreadPolling;

function stopDmThreadPolling() {
  if (state.dmThreadPollTimer) clearInterval(state.dmThreadPollTimer);
  state.dmThreadPollTimer = null;
}
window.stopDmThreadPolling = stopDmThreadPolling;

function normalizeDmConversation(item) {
  item = item || {};
  const peer = item.peerUser || {};
  return {
    id: item.id,
    peerUserId: item.peerUserId || peer.id,
    peerNickName: item.peerNickName || peer.nickName || "探店用户",
    peerIcon: item.peerIcon || peer.icon || "",
    lastMessageContent: item.lastMessageContent || "",
    lastMessageTime: item.lastMessageTime || null,
    unreadCount: Number(item.unreadCount || 0),
    updatedAt: item.updatedAt || item.updateTime || item.lastMessageTime || null
  };
}
window.normalizeDmConversation = normalizeDmConversation;

function formatDmTime(value) {
  if (!value) return "";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "";
  const now = new Date();
  const sameDay = date.toDateString() === now.toDateString();
  return sameDay
    ? date.toLocaleTimeString("zh-CN", { hour: "2-digit", minute: "2-digit" })
    : date.toLocaleDateString("zh-CN", { month: "2-digit", day: "2-digit" });
}
window.formatDmTime = formatDmTime;

function isSmallScreen() {
  return window.matchMedia && window.matchMedia("(max-width: 760px)").matches;
}
window.isSmallScreen = isSmallScreen;

async function loadNotificationSettings() {
  if (!token() || !els.notificationSettings) return;
  try {
    state.notificationSettings = await request("/notifications/settings");
    renderNotificationSettings();
    startNotificationStream();
  } catch {
    // 设置加载失败不影响通知列表浏览。
  }
}
window.loadNotificationSettings = loadNotificationSettings;

function renderNotificationSettings() {
  if (!els.notificationSettings || !state.notificationSettings) return;
  const setting = state.notificationSettings;
  const options = [
    ["interactionEnabled", "互动"],
    ["orderEnabled", "订单"],
    ["auditEnabled", "审核"],
    ["systemEnabled", "系统"],
    ["realtimeEnabled", "实时"]
  ];
  els.notificationSettings.innerHTML = options.map(([key, label]) => `
    <label class="notification-setting-toggle">
      <input type="checkbox" data-notification-setting="${key}" ${setting[key] ? "checked" : ""}>
      <span>${label}</span>
    </label>
  `).join("");
}
window.renderNotificationSettings = renderNotificationSettings;

async function updateNotificationSetting(key, value) {
  if (!key || !requireLogin()) return;
  try {
    state.notificationSettings = await request("/notifications/settings", {
      method: "PUT",
      body: JSON.stringify({ [key]: value })
    });
    renderNotificationSettings();
    if (key === "realtimeEnabled") {
      if (value) startNotificationStream(true);
      else stopNotificationStream();
    }
  } catch (error) {
    showStatus(error.message || "通知设置保存失败。");
    loadNotificationSettings();
  }
}
window.updateNotificationSetting = updateNotificationSetting;

function startNotificationStream(force = false) {
  if (!token() || !state.notificationSettings?.realtimeEnabled || !window.EventSource) return;
  if (!force && state.notificationStream && state.notificationStreamToken === token()) return;
  stopNotificationStream();
  state.notificationStreamToken = token();
  const stream = new EventSource(apiUrl(`/notifications/stream?token=${encodeURIComponent(token())}`));
  stream.addEventListener("notification", event => {
    try {
      const data = JSON.parse(event.data || "{}");
      applyRealtimeNotification(data);
    } catch {
      refreshNotificationBadge();
    }
  });
  stream.onerror = () => {
    stopNotificationStream();
  };
  state.notificationStream = stream;
}
window.startNotificationStream = startNotificationStream;

function stopNotificationStream() {
  if (state.notificationStream) {
    state.notificationStream.close();
  }
  state.notificationStream = null;
  state.notificationStreamToken = null;
}
window.stopNotificationStream = stopNotificationStream;

function applyRealtimeNotification(data) {
  refreshNotificationBadge();
  if (state.mode === "messages") {
    loadNotifications();
  }
}
window.applyRealtimeNotification = applyRealtimeNotification;

async function markNotificationsRead() {
  if (!requireLogin()) return;
  await request("/notifications/read", { method: "POST" });
  await refreshNotificationBadge();
  await loadNotifications();
}
window.markNotificationsRead = markNotificationsRead;

async function markSingleNotificationRead(id) {
  try {
    await request(`/notifications/${id}/read`, { method: "POST" });
    await refreshNotificationBadge();
    const item = els.notificationList.querySelector(`[data-id="${id}"]`);
    if (item) {
      item.classList.remove("is-unread");
      const readBtn = item.querySelector('[data-action="read"]');
      if (readBtn) readBtn.remove();
    }
  } catch { /* ignore */ }
}
window.markSingleNotificationRead = markSingleNotificationRead;

async function deleteNotification(id) {
  try {
    await request(`/notifications/${id}`, { method: "DELETE" });
    await refreshNotificationBadge();
    const item = els.notificationList.querySelector(`[data-id="${id}"]`);
    if (item) {
      item.style.opacity = "0";
      item.style.transform = "translateX(100%)";
      setTimeout(() => {
        item.remove();
        if (!els.notificationList.querySelector(".notification-item")) {
          els.notificationList.innerHTML = `<p class="empty-text">暂时还没有消息。</p>`;
        }
      }, 250);
    }
  } catch { /* ignore */ }
}
window.deleteNotification = deleteNotification;

function navigateFromNotification(item) {
  const noteId = item.dataset.noteId;
  const orderId = item.dataset.orderId;
  const actorUserId = item.dataset.actorUserId;
  const type = item.dataset.type || "";
  if (item.dataset.id) {
    markSingleNotificationRead(Number(item.dataset.id));
  }
  if (els.messageArea) els.messageArea.hidden = true;
  setMessageEntryActive(false);
  if (noteId && isNoteNotificationType(type)) {
    showContentArea();
    openDrawer({ id: Number(noteId) });
  } else if (orderId && type.startsWith("ORDER_")) {
    switchMall();
    if (typeof openOrderDetail === "function") openOrderDetail(Number(orderId));
    else openOrdersDialog(Number(orderId));
  } else if (actorUserId && type === "FOLLOW") {
    openUserProfile(actorUserId);
  }
}
window.navigateFromNotification = navigateFromNotification;

function isNoteNotificationType(type) {
  return ["LIKE", "COLLECT", "COMMENT", "REPLY"].includes(type) || type.startsWith("AUDIT_");
}
window.isNoteNotificationType = isNoteNotificationType;

// ------------------------------
// Profile
// ------------------------------
async function loadProfileStats() {
  if (!token()) return;
  try {
    const profile = await request("/profiles/me");
    state.currentProfile = profile;
  } catch {
    // 统计接口异常时保留当前用户基础信息，不影响浏览主流程。
  }
}
window.loadProfileStats = loadProfileStats;

async function openMyProfile(tab = "works") {
  setMobileTabActive("profile");
  if (!token()) {
    state.afterLoginAction = "profile";
    requireLogin();
    return;
  }
  state.query = "";
  els.search.value = "";
  await openUserProfile(null, tab);
}
window.openMyProfile = openMyProfile;

async function openUserProfile(userId, tab = "works") {
  if (typeof stopMessagePolling === "function") stopMessagePolling();
  showContentArea();
  setFeedTabsVisible(false);
  setMobileTabActive("profile");
  hideUnifiedSearch();
  state.mode = "profile";
  state.profileTab = tab;
  els.feed.hidden = true;
  els.loading.hidden = true;
  els.profileHome.hidden = false;
  els.profileHomeResults.innerHTML = `<p class="empty-text">正在加载主页...</p>`;
  document.querySelectorAll("[data-feed]").forEach(item => item.classList.remove("is-active"));
  try {
    const profile = await request(userId ? `/profiles/${userId}` : "/profiles/me");
    state.currentProfile = profile;
    renderProfileHome(profile);
    await loadProfileTab(tab);
  } catch (error) {
    els.profileHomeResults.innerHTML = `<p class="empty-text">${escapeHtml(error.message || "主页加载失败")}</p>`;
  }
}
window.openUserProfile = openUserProfile;

function renderProfileHome(profile) {
  document.querySelector("#profileHomeAvatar").src = normalizeImage(profile.icon) || fallbackAvatar;
  document.querySelector("#profileHomeName").textContent = profile.nickName || "探店用户";
  document.querySelector("#profileHomeIntro").textContent = profile.introduce || "这个人还没有写简介。";
  document.querySelector("#profileHomeMeta").textContent = `${profile.city || "城市未填写"} · ID ${profile.userId}`;
  renderCreatorGrowth(document.querySelector("#profileGrowth"), profile.creatorGrowth);
  document.querySelector("#profileHomeWorks").textContent = profile.notes || 0;
  document.querySelector("#profileHomeCollections").textContent = profile.collects || 0;
  document.querySelector("#profileHomeLiked").textContent = profile.likes || 0;
  document.querySelector("#profileHomeFollowing").textContent = profile.following || 0;
  document.querySelector("#profileHomeFollowers").textContent = profile.followers || 0;
  const actionButton = document.querySelector("#editProfileButton");
  actionButton.hidden = false;
  actionButton.textContent = profile.isMe ? "编辑资料" : (profile.isFollow ? "已关注" : "关注");
  actionButton.classList.toggle("is-following", Boolean(!profile.isMe && profile.isFollow));
  if (els.profileMessageButton) {
    els.profileMessageButton.hidden = Boolean(profile.isMe);
  }
  renderProfileTabs();
}
window.renderProfileHome = renderProfileHome;

function renderProfileTabs() {
  const tabs = [
    ["works", "作品"],
    ["collections", "收藏"],
    ["liked", "点赞"],
    ["following", "关注"],
    ["followers", "粉丝"]
  ];
  els.profileHomeTabs.innerHTML = tabs.map(([key, label]) => `
    <button type="button" class="${state.profileTab === key ? "is-active" : ""}" data-profile-tab="${key}">${label}</button>
  `).join("");
  els.profileHomeTabs.querySelectorAll("[data-profile-tab]").forEach(button => {
    button.addEventListener("click", () => loadProfileTab(button.dataset.profileTab));
  });
  document.querySelectorAll(".profile-home-stats [data-profile-tab]").forEach(button => {
    button.classList.toggle("is-active", button.dataset.profileTab === state.profileTab);
  });
}
window.renderProfileTabs = renderProfileTabs;

async function loadProfileTab(tab) {
  state.profileTab = tab;
  renderProfileTabs();
  const userId = state.currentProfile?.userId || state.currentUser?.id;
  if (!userId) return;
  els.profileHomeResults.innerHTML = `<p class="empty-text">正在加载...</p>`;
  if (["works", "collections", "liked"].includes(tab)) {
    const data = await request(`${profileNotesPath(tab, userId)}?current=1`);
    const notes = (Array.isArray(data?.list) ? data.list : []).map(normalizeNote);
    renderProfileNotes(notes);
    return;
  }
  const users = await request(`/notes/user/${userId}/${tab}?current=1`);
  renderProfileUsers(Array.isArray(users) ? users : []);
}
window.loadProfileTab = loadProfileTab;

function profileNotesPath(tab, userId) {
  if (state.currentProfile?.isMe) {
    return {
      works: "/notes/mine",
      collections: "/notes/collections",
      liked: "/notes/liked"
    }[tab];
  }
  return {
    works: `/notes/user/${userId}`,
    collections: `/notes/user/${userId}/collections`,
    liked: `/notes/user/${userId}/liked`
  }[tab];
}
window.profileNotesPath = profileNotesPath;

function renderProfileNotes(notes) {
  if (!notes.length) {
    els.profileHomeResults.innerHTML = `<p class="empty-text">这里暂时还没有内容。</p>`;
    return;
  }
  const grid = document.createElement("div");
  grid.className = "masonry-feed profile-note-results";
  notes.forEach(note => grid.appendChild(createNoteCard(note)));
  els.profileHomeResults.innerHTML = "";
  els.profileHomeResults.appendChild(grid);
}
window.renderProfileNotes = renderProfileNotes;

function renderProfileUsers(users) {
  if (!users.length) {
    els.profileHomeResults.innerHTML = `<p class="empty-text">这里暂时还没有用户。</p>`;
    return;
  }
  els.profileHomeResults.innerHTML = `
    <div class="profile-user-list">
      ${users.map(user => `
        <article class="profile-user-row" data-profile-user="${user.id}">
          <img src="${normalizeImage(user.icon) || fallbackAvatar}" alt="">
          <span>
            <strong>${escapeHtml(user.nickName || "探店用户")}</strong>
            <small>ID ${user.id}</small>
          </span>
          <div class="profile-user-actions">
            <button type="button" data-profile-open="${user.id}">主页</button>
            ${String(user.id) === String(state.currentUser?.id || "") ? "" : `<button type="button" data-profile-dm="${user.id}">私信</button>`}
          </div>
        </article>
      `).join("")}
    </div>`;
  els.profileHomeResults.querySelectorAll("[data-profile-user]").forEach(row => {
    row.addEventListener("click", () => openUserProfile(row.dataset.profileUser));
  });
  els.profileHomeResults.querySelectorAll("[data-profile-open]").forEach(button => {
    button.addEventListener("click", event => {
      event.stopPropagation();
      openUserProfile(button.dataset.profileOpen);
    });
  });
  els.profileHomeResults.querySelectorAll("[data-profile-dm]").forEach(button => {
    button.addEventListener("click", event => {
      event.stopPropagation();
      openDmWithUser(button.dataset.profileDm);
    });
  });
}
window.renderProfileUsers = renderProfileUsers;

function openProfileEdit() {
  const profile = state.currentProfile || {};
  if (!profile.isMe) {
    toggleProfileFollow();
    return;
  }
  els.profileEditForm.elements.nickName.value = profile.nickName || "";
  els.profileEditForm.elements.icon.value = profile.icon || "";
  els.profileEditForm.elements.city.value = profile.city || "";
  els.profileEditForm.elements.introduce.value = profile.introduce || "";
  els.profileEditDialog.showModal();
}
window.openProfileEdit = openProfileEdit;

async function toggleProfileFollow() {
  const profile = state.currentProfile;
  if (!profile?.userId || profile.isMe || !requireLogin()) return;
  const nextFollow = !profile.isFollow;
  try {
    await request(`/follow/${profile.userId}/${nextFollow}`, { method: "PUT" });
    const updated = await request(`/profiles/${profile.userId}`);
    state.currentProfile = updated;
    renderProfileHome(updated);
    await loadProfileTab(state.profileTab || "works");
  } catch (error) {
    showStatus(error.message || "关注操作失败。");
  }
}
window.toggleProfileFollow = toggleProfileFollow;

async function submitProfileEdit(event) {
  if (event.submitter && event.submitter.value === "cancel") return;
  event.preventDefault();
  const form = new FormData(els.profileEditForm);
  try {
    const profile = await request("/profiles/me", {
      method: "PUT",
      body: JSON.stringify({
        nickName: form.get("nickName"),
        icon: form.get("icon"),
        city: form.get("city"),
        introduce: form.get("introduce")
      })
    });
    state.currentProfile = profile;
    renderProfileHome(profile);
    state.currentUser = { ...(state.currentUser || {}), id: profile.userId, nickName: profile.nickName, icon: profile.icon };
    renderUser(state.currentUser);
    els.profileEditDialog.close();
    showStatus("个人资料已更新。");
  } catch (error) {
    showStatus(error.message || "资料保存失败。");
  }
}
window.submitProfileEdit = submitProfileEdit;

})();
