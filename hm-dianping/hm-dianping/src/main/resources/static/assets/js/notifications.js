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
    const data = await request("/notifications/unread-count");
    const count = Number(data?.count || 0);
    els.notificationBadge.textContent = count > 99 ? "99+" : String(count);
    els.notificationBadge.hidden = count <= 0;
  } catch {
    els.notificationBadge.hidden = true;
  }
}
window.refreshNotificationBadge = refreshNotificationBadge;

async function openNotificationDialog() {
  if (!requireLogin()) return;
  setMobileTabActive("messages");
  els.notificationList.innerHTML = `<p class="empty-text">正在加载消息...</p>`;
  els.notificationDialog.showModal();
  const unreadOnly = state.notificationFilter === "unread";
  try {
    const list = await request(`/notifications?unreadOnly=${unreadOnly}`);
    renderNotifications(Array.isArray(list) ? list : []);
  } catch {
    els.notificationList.innerHTML = `<p class="empty-text">消息加载失败，请稍后再试。</p>`;
  }
}
window.openNotificationDialog = openNotificationDialog;

function renderNotifications(list) {
  if (!list.length) {
    els.notificationList.innerHTML = `<p class="empty-text">暂时还没有消息。</p>`;
    return;
  }
  els.notificationList.innerHTML = list.map(item => `
    <article class="notification-item${item.readFlag ? "" : " is-unread"}" data-id="${item.id}" data-note-id="${item.noteId || item.blogId || ""}" data-order-id="${item.orderId || ""}" data-type="${item.type || ""}">
      <div class="notification-item-body">
        <strong>${escapeHtml(item.title || notificationTypeLabel(item.type))}</strong>
        <span>${escapeHtml(item.content || "")}</span>
        <small><span class="notification-type-tag tag-${(item.type || "").split("_")[0].toLowerCase()}">${notificationTypeLabel(item.type)}</span> · ${formatTime(item.createTime)}</small>
      </div>
      <div class="notification-item-actions">
        ${item.readFlag ? "" : `<button class="notification-action-btn" data-action="read" title="标记已读"><svg viewBox="0 0 24 24" width="16" height="16"><path d="M9 16.2 4.8 12l-1.4 1.4L9 19 21 7l-1.4-1.4z" fill="currentColor"/></svg></button>`}
        <button class="notification-action-btn" data-action="delete" title="删除"><svg viewBox="0 0 24 24" width="16" height="16"><path d="M19 6.4 17.6 5 12 10.6 6.4 5 5 6.4 10.6 12 5 17.6 6.4 19 12 13.4 17.6 19 19 17.6 13.4 12z" fill="currentColor"/></svg></button>
      </div>
    </article>
  `).join("");
}
window.renderNotifications = renderNotifications;

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
    ORDER_REFUND_REJECTED: "退款"
  }[type] || "通知";
}
window.notificationTypeLabel = notificationTypeLabel;

async function markNotificationsRead() {
  if (!requireLogin()) return;
  await request("/notifications/read", { method: "POST" });
  await refreshNotificationBadge();
  await openNotificationDialog();
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
  const type = item.dataset.type;
  els.notificationDialog.close();
  if (noteId && (type === "LIKE" || type === "COLLECT" || type === "COMMENT" || type === "REPLY")) {
    openDrawer({ id: Number(noteId) });
  } else if (orderId && type.startsWith("ORDER_")) {
    openOrdersDialog();
  }
}
window.navigateFromNotification = navigateFromNotification;

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
  showContentArea();
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
        </article>
      `).join("")}
    </div>`;
  els.profileHomeResults.querySelectorAll("[data-profile-user]").forEach(row => {
    row.addEventListener("click", () => openUserProfile(row.dataset.profileUser));
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
