// feed.js — 笔记信息流：瀑布流加载、卡片渲染、频道切换
// 依赖 utils.js（state, els, token, request, requireLogin, showStatus, hideStatus 等）
// 被引用函数：openDrawer, showContentArea, pauseFeedVideos, renderCreatorGrowth
(function() {

// ==================== 笔记流加载与渲染 ====================
// 核心数据流：fetch → normalizeNote → createNoteCard → appendNotes
async function loadNotes() {
  if (state.loading || !state.hasMore) return;
  if (state.feed === "nearby" && !state.nearbyLocation && !state.nearbyLocationLoading) {
    requestNearbyLocation();
  }
  if ((state.feed === "follow" || state.mode === "mine" || state.mode === "collections") && !token()) {
    requireLoginThen(() => switchFeed(state.feed));
    state.hasMore = false;
    els.feed.innerHTML = renderFeedEmptyState("关注动态只给登录用户看", "登录后会展示你关注作者的新笔记。", "去登录", "login");
    els.feed.classList.add("is-empty");
    bindFeedEmptyActions();
    els.loading.textContent = "登录后查看你的内容";
    return;
  }
  state.loading = true;
  if (state.page === 1) {
    renderSkeletons(8);
  } else {
    els.loading.textContent = "正在加载更多笔记...";
  }
  try {
    const data = await request(buildContentUrl());
    let notes = (Array.isArray(data?.list) ? data.list : []).map(normalizeNote);
    if (!notes.length && state.page === 1) {
      renderFeedEmptyForCurrentState();
    } else if (notes.length) {
      hideStatus();
    }
    appendNotes(notes);
    state.notes.push(...notes);
    notes.forEach(note => trackEvent("impression", { noteId: note.id, scene: state.mode }));
    loadProfileStats();
    state.page += 1;
    state.hasMore = Boolean(data?.hasMore);
  } catch (error) {
    if (state.page === 1) {
      clearSkeletons();
      showStatus(error.message || "笔记加载失败，请确认后端 /notes/feed 已启动。");
    }
    state.hasMore = false;
  } finally {
    state.loading = false;
    els.loading.textContent = state.hasMore ? "向下滚动加载更多" : "已经到底了";
  }
}
window.loadNotes = loadNotes;

function personalEmptyText() {
  if (state.mode === "mine") return "你还没有发布笔记，可以先发布第一篇。";
  if (state.mode === "collections") return "你还没有收藏笔记。";
  return "这里暂时还没有内容。";
}
window.personalEmptyText = personalEmptyText;

// 根据当前模式构建 API 请求 URL：mine → /notes/mine，collections → /notes/collections，否则 → /notes/feed
function buildContentUrl() {
  const params = new URLSearchParams({ current: String(state.page) });
  if (state.mode === "mine") {
    return `/notes/mine?${params.toString()}`;
  }
  if (state.mode === "collections") {
    return `/notes/collections?${params.toString()}`;
  }
  params.set("channel", state.feed);
  if (state.query) params.set("query", state.query);
  if (state.feed === "nearby" && state.nearbyLocation) {
    params.set("x", String(state.nearbyLocation.longitude));
    params.set("y", String(state.nearbyLocation.latitude));
  }
  return `/notes/feed?${params.toString()}`;
}
window.buildContentUrl = buildContentUrl;

function appendNotes(notes) {
  clearSkeletons();
  els.feed.classList.toggle("is-empty", !notes.length && state.page === 1);
  const fragment = document.createDocumentFragment();
  notes.forEach(note => fragment.appendChild(createNoteCard(note)));
  els.feed.appendChild(fragment);
  mergeVideoNotes(notes);
}
window.appendNotes = appendNotes;

function mergeVideoNotes(notes) {
  const exists = new Set(state.videoNotes.map(note => String(note.id)));
  notes.filter(note => note.isVideo && !exists.has(String(note.id))).forEach(note => state.videoNotes.push(note));
}
window.mergeVideoNotes = mergeVideoNotes;

function createNoteCard(note) {
  const card = document.createElement("article");
  card.className = "note-card";
  card.dataset.noteCard = String(note.id);
  const badge = note.contentType === "IMAGE" ? "" : `<span class="video-badge">${contentTypeLabel(note.contentType)}</span>`;
  const auditBadge = renderNoteAuditBadge(note);
  const productBadge = "";
  const playIndicator = note.isVideo ? `<span class="media-play-indicator" aria-hidden="true"><svg viewBox="0 0 24 24"><path d="M8 5v14l11-7L8 5Z"/></svg></span>` : "";
  const cover = note.isVideo
    ? `<video class="note-image note-video-cover" style="--ratio:${note.ratio}" src="${normalizeMedia(note.videoUrl)}" poster="${normalizeImage(note.image)}" muted playsinline preload="metadata"></video>${playIndicator}${badge}${auditBadge}${productBadge}`
    : `<img class="note-image" style="--ratio:${note.ratio}" src="${normalizeImage(note.image)}" alt="${escapeHtml(note.title)}" loading="lazy" decoding="async">${badge}${auditBadge}${productBadge}`;
  card.innerHTML = `
    <button class="note-open" type="button" aria-label="查看笔记：${escapeHtml(note.title)}">
      <div class="note-cover">
        ${cover}
      </div>
    </button>
    <div class="note-body">
      <button class="note-title note-title-button" type="button">${escapeHtml(note.title)}</button>
      <div class="note-meta">
        <span class="author-mini">
          <img class="avatar" src="${normalizeImage(note.icon)}" alt="">
          <span>${escapeHtml(note.name)}</span>
        </span>
        <span class="note-card-score" title="${feedScoreTitle(note)}">${compactCount(feedScore(note))}</span>
        <span class="note-card-actions">
          <button class="note-action note-like ${note.isLike ? "is-active" : ""}" type="button" aria-label="${note.isLike ? "取消点赞" : "点赞"}">
            <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 21.2 10.7 20C5.8 15.6 2.6 12.7 2.6 9a5 5 0 0 1 8.7-3.4A5 5 0 0 1 20 9c0 3.7-3.2 6.6-8.1 11l-1.3 1.2Z"/></svg>
            <span>${compactCount(note.liked)}</span>
          </button>
          <button class="note-action note-collect ${note.isCollect ? "is-active" : ""}" type="button" aria-label="${note.isCollect ? "取消收藏" : "收藏"}">
            <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M6 3.5h12a1 1 0 0 1 1 1v16l-7-3.6-7 3.6v-16a1 1 0 0 1 1-1Z"/></svg>
            <span>${compactCount(note.collects)}</span>
          </button>
        </span>
      </div>
    </div>
  `;
  card.querySelector(".note-open").addEventListener("click", () => {
    if (note.isVideo && typeof openVideoFullscreen === "function") openVideoFullscreen(note);
    else openDrawer(note);
  });
  card.querySelector(".note-title-button").addEventListener("click", () => openDrawer(note));
  card.querySelector(".note-like").addEventListener("click", event => {
    event.stopPropagation();
    toggleCardLike(note);
  });
  card.querySelector(".note-collect").addEventListener("click", event => {
    event.stopPropagation();
    toggleCardCollect(note);
  });
  return card;
}
window.createNoteCard = createNoteCard;

function feedScore(note) {
  const score = Number(note.score || 0);
  if (score > 0) return score;
  return Number(note.liked || 0) * 3 + Number(note.collects || 0) * 5 + Number(note.comments || 0) * 4;
}
window.feedScore = feedScore;

function feedScoreTitle(note) {
  return `热度：赞 ${note.liked || 0} / 收藏 ${note.collects || 0} / 评论 ${note.comments || 0}`;
}
window.feedScoreTitle = feedScoreTitle;

function compactCount(value) {
  const number = Number(value || 0);
  if (number >= 10000) return `${(number / 10000).toFixed(number >= 100000 ? 0 : 1)}万`;
  if (number >= 1000) return `${(number / 1000).toFixed(number >= 10000 ? 0 : 1)}k`;
  return String(number);
}
window.compactCount = compactCount;

function mergeNoteState(note) {
  const targets = [
    state.notes,
    state.videoNotes,
    state.searchResults?.notes,
    state.searchResults?.videos
  ].filter(Array.isArray);
  targets.forEach(list => {
    const match = list.find(item => String(item.id) === String(note.id));
    if (match) Object.assign(match, note);
  });
  if (state.currentNote && String(state.currentNote.id) === String(note.id)) {
    Object.assign(state.currentNote, note);
  }
}
window.mergeNoteState = mergeNoteState;

function updateNoteCards(note) {
  document.querySelectorAll(`[data-note-card="${CSS.escape(String(note.id))}"]`).forEach(card => {
    const like = card.querySelector(".note-like");
    const collect = card.querySelector(".note-collect");
    const score = card.querySelector(".note-card-score");
    if (like) {
      like.classList.toggle("is-active", Boolean(note.isLike));
      like.setAttribute("aria-label", note.isLike ? "取消点赞" : "点赞");
      like.querySelector("span").textContent = compactCount(note.liked);
    }
    if (collect) {
      collect.classList.toggle("is-active", Boolean(note.isCollect));
      collect.setAttribute("aria-label", note.isCollect ? "取消收藏" : "收藏");
      collect.querySelector("span").textContent = compactCount(note.collects);
    }
    if (score) {
      score.textContent = compactCount(feedScore(note));
      score.title = feedScoreTitle(note);
    }
  });
}
window.updateNoteCards = updateNoteCards;

function applyNoteInteraction(note, changes) {
  Object.assign(note, changes);
  mergeNoteState(note);
  updateNoteCards(note);
}
window.applyNoteInteraction = applyNoteInteraction;

async function toggleCardLike(note) {
  if (!requireLoginThen(() => toggleCardLike(note))) return;
  if (!beginNoteAction(note.id, "like")) return;
  const previous = {
    isLike: Boolean(note.isLike),
    likedByMe: Boolean(note.likedByMe),
    liked: Number(note.liked || 0),
    likedCount: Number(note.likedCount || note.liked || 0),
    score: Number(note.score || 0)
  };
  const nextLiked = !previous.isLike;
  applyNoteInteraction(note, {
    isLike: nextLiked,
    likedByMe: nextLiked,
    liked: Math.max(0, previous.liked + (nextLiked ? 1 : -1)),
    likedCount: Math.max(0, previous.liked + (nextLiked ? 1 : -1)),
    score: Math.max(0, previous.score + (nextLiked ? 3 : -3))
  });
  try {
    await request(`/notes/${note.id}/like`, { method: "PUT" });
    if (nextLiked) trackEvent("like", { noteId: note.id, scene: "feed" });
  } catch {
    applyNoteInteraction(note, previous);
    showStatus("点赞失败，请稍后再试。");
  } finally {
    endNoteAction(note.id, "like");
  }
}
window.toggleCardLike = toggleCardLike;

async function toggleCardCollect(note) {
  if (!requireLoginThen(() => toggleCardCollect(note))) return;
  if (!beginNoteAction(note.id, "collect")) return;
  const previous = {
    isCollect: Boolean(note.isCollect),
    collected: Boolean(note.collected),
    collects: Number(note.collects || 0),
    collectCount: Number(note.collectCount || note.collects || 0),
    score: Number(note.score || 0)
  };
  const nextCollected = !previous.isCollect;
  applyNoteInteraction(note, {
    isCollect: nextCollected,
    collected: nextCollected,
    collects: Math.max(0, previous.collects + (nextCollected ? 1 : -1)),
    collectCount: Math.max(0, previous.collects + (nextCollected ? 1 : -1)),
    score: Math.max(0, previous.score + (nextCollected ? 5 : -5))
  });
  try {
    await request(`/notes/${note.id}/collect/${nextCollected}`, { method: "PUT" });
    if (nextCollected) state.collected.add(String(note.id));
    else state.collected.delete(String(note.id));
    saveScopedSet("xiaohongshu_collected", state.collected);
    trackEvent(nextCollected ? "collect" : "uncollect", { noteId: note.id, scene: "feed" });
  } catch {
    applyNoteInteraction(note, previous);
    showStatus("收藏失败，请稍后再试。");
  } finally {
    endNoteAction(note.id, "collect");
  }
}
window.toggleCardCollect = toggleCardCollect;

function noteActionKey(noteId, action) {
  return `${noteId}:${action}`;
}
window.noteActionKey = noteActionKey;

function beginNoteAction(noteId, action) {
  const key = noteActionKey(noteId, action);
  if (state.pendingNoteActions.has(key)) return false;
  state.pendingNoteActions.add(key);
  setNoteActionBusy(noteId, action, true);
  return true;
}
window.beginNoteAction = beginNoteAction;

function endNoteAction(noteId, action) {
  state.pendingNoteActions.delete(noteActionKey(noteId, action));
  setNoteActionBusy(noteId, action, false);
}
window.endNoteAction = endNoteAction;

function setNoteActionBusy(noteId, action, busy) {
  const selector = action === "like" ? ".note-like" : ".note-collect";
  document.querySelectorAll(`[data-note-card="${CSS.escape(String(noteId))}"] ${selector}`).forEach(button => {
    button.disabled = busy;
    button.classList.toggle("is-busy", busy);
  });
}
window.setNoteActionBusy = setNoteActionBusy;

function noteAuditLabel(status) {
  return {
    1: "审核中",
    2: "已隐藏"
  }[Number(status)] || "";
}
window.noteAuditLabel = noteAuditLabel;

function renderNoteAuditBadge(note) {
  if (!note?.isOwner) return "";
  const label = noteAuditLabel(note.status);
  return label ? `<span class="audit-badge audit-${Number(note.status)}">${label}</span>` : "";
}
window.renderNoteAuditBadge = renderNoteAuditBadge;

// ==================== 流重置与切换 ====================
// 切换频道/分类/搜索时调用，清空当前流重新加载
function resetAndLoad(clearStatus = true) {
  hideUnifiedSearch();
  hideProfileHome();
  updateHomeCurationVisibility();
  state.page = 1;
  state.hasMore = true;
  state.notes = [];
  els.feed.innerHTML = "";
  els.feed.classList.remove("is-empty");
  if (clearStatus) hideStatus();
  loadNotes();
}
window.resetAndLoad = resetAndLoad;

function hideUnifiedSearch() {
  if (!els.unifiedSearch) return;
  els.unifiedSearch.hidden = true;
  els.feed.hidden = false;
  els.loading.hidden = false;
  updateHomeCurationVisibility();
}
window.hideUnifiedSearch = hideUnifiedSearch;

function hideProfileHome() {
  if (!els.profileHome) return;
  els.profileHome.hidden = true;
  els.feed.hidden = false;
  els.loading.hidden = false;
  updateHomeCurationVisibility();
}
window.hideProfileHome = hideProfileHome;

// ==================== 频道/个人模式切换 ====================

function switchFeed(feed) {
  showContentArea();
  setMobileTabActive("home");
  state.mode = "feed";
  state.feed = feed;
  state.query = "";
  if (els.search) els.search.value = "";
  updateFeedNavState(feed);
  resetAndLoad();
}
window.switchFeed = switchFeed;

function updateFeedNavState(feed) {
  document.querySelectorAll("[data-feed]").forEach(item => {
    const active = item.dataset.feed === feed || (item.dataset.mobileTab === "home" && item.closest(".mobile-tabbar") && state.mode === "feed");
    item.classList.toggle("is-active", active);
    if (active) item.setAttribute("aria-current", "page");
    else item.removeAttribute("aria-current");
  });
}
window.updateFeedNavState = updateFeedNavState;

function switchPersonalMode(mode) {
  if (!requireLogin()) return;
  showContentArea();
  state.mode = mode;
  state.query = "";
  els.search.value = "";
  document.querySelectorAll("[data-feed]").forEach(item => {
    item.classList.remove("is-active");
    item.removeAttribute("aria-current");
  });
  resetAndLoad();
}
window.switchPersonalMode = switchPersonalMode;

function showWallet() {
  if (!requireLogin()) return;
  const vouchers = [...state.wallet];
  showStatus(vouchers.length
    ? `我的券包：${vouchers.map(id => `券 #${id}`).join("、")}`
    : "券包还是空的，去笔记详情里的店铺卡片领取一张试试。");
}
window.showWallet = showWallet;

function requestNearbyLocation() {
  if (!navigator.geolocation) {
    showStatus("浏览器不支持定位，先展示有店铺关联的同城内容。");
    return;
  }
  state.nearbyLocationLoading = true;
  navigator.geolocation.getCurrentPosition(position => {
    state.nearbyLocation = {
      longitude: Number(position.coords.longitude),
      latitude: Number(position.coords.latitude)
    };
    state.nearbyLocationLoading = false;
    if (state.mode === "feed" && state.feed === "nearby") {
      resetAndLoad(false);
    }
  }, () => {
    state.nearbyLocationLoading = false;
    showStatus("未获取到定位，先按有店铺关联的内容推荐。");
  }, { enableHighAccuracy: false, timeout: 5000, maximumAge: 300000 });
}
window.requestNearbyLocation = requestNearbyLocation;

function renderFeedEmptyForCurrentState() {
  const content = feedEmptyContent();
  els.feed.innerHTML = renderFeedEmptyState(content.title, content.text, content.actionLabel, content.action);
  els.feed.classList.add("is-empty");
  bindFeedEmptyActions();
  hideStatus();
}
window.renderFeedEmptyForCurrentState = renderFeedEmptyForCurrentState;

function feedEmptyContent() {
  if (state.mode === "mine") {
    return { title: "还没有发布过笔记", text: "把今天去过的店、踩过的坑、买到的好物记下来。", actionLabel: "发布第一篇", action: "compose" };
  }
  if (state.mode === "collections") {
    return { title: "收藏夹还是空的", text: "看到有用的笔记点收藏，之后可以在这里继续看。", actionLabel: "去推荐流", action: "recommend" };
  }
  if (state.feed === "follow") {
    return { title: "关注流还没有动态", text: "去推荐里发现喜欢的作者，关注后这里会变成你的专属信息流。", actionLabel: "逛推荐", action: "recommend" };
  }
  if (state.feed === "nearby") {
    return { title: "附近暂时没有匹配笔记", text: state.nearbyLocation ? "当前位置周边还没有内容，可以换个关键词或看热门。" : "允许定位后会优先展示附近店铺相关笔记。", actionLabel: state.nearbyLocation ? "看热门" : "重新定位", action: state.nearbyLocation ? "hot" : "nearby-location" };
  }
  if (state.feed === "video") {
    return { title: "还没有视频内容", text: "发布视频笔记后，这里会自动出现沉浸式内容。", actionLabel: "发布视频", action: "compose-video" };
  }
  if (state.feed === "mall") {
    return { title: "好物笔记还没准备好", text: "可以先去商城看看商品，或者发布一篇商品种草。", actionLabel: "去商城", action: "mall" };
  }
  if (state.feed === "hot") {
    return { title: "热门内容正在积累", text: "多点赞、收藏和评论，热门榜会越来越准。", actionLabel: "看推荐", action: "recommend" };
  }
  return { title: "还没有匹配的笔记", text: "换个频道或关键词试试，也可以发布第一条内容。", actionLabel: "发布内容", action: "compose" };
}
window.feedEmptyContent = feedEmptyContent;

function renderFeedEmptyState(title, text, actionLabel, action) {
  return `
    <section class="feed-empty-state" aria-live="polite">
      <div class="feed-empty-mark" aria-hidden="true">
        <svg viewBox="0 0 24 24"><path d="M5 5.5A2.5 2.5 0 0 1 7.5 3h9A2.5 2.5 0 0 1 19 5.5v13.2a1 1 0 0 1-1.5.86L12 16.3l-5.5 3.26a1 1 0 0 1-1.5-.86V5.5Zm2.5-.5a.5.5 0 0 0-.5.5v11.45l4.49-2.66a1 1 0 0 1 1.02 0L17 16.95V5.5a.5.5 0 0 0-.5-.5h-9Z"/></svg>
      </div>
      <strong>${escapeHtml(title)}</strong>
      <p>${escapeHtml(text)}</p>
      ${action ? `<button class="soft-button" type="button" data-feed-empty-action="${escapeHtml(action)}">${escapeHtml(actionLabel)}</button>` : ""}
    </section>
  `;
}
window.renderFeedEmptyState = renderFeedEmptyState;

function bindFeedEmptyActions() {
  els.feed.querySelectorAll("[data-feed-empty-action]").forEach(button => {
    button.addEventListener("click", () => runFeedEmptyAction(button.dataset.feedEmptyAction));
  });
}
window.bindFeedEmptyActions = bindFeedEmptyActions;

function runFeedEmptyAction(action) {
  if (action === "login") {
    requireLoginThen(() => switchFeed("follow"));
    return;
  }
  if (action === "compose" || action === "compose-video") {
    if (!requireLoginThen(action === "compose-video" ? () => openComposer("VIDEO") : "compose")) return;
    if (action === "compose-video") openComposer("VIDEO");
    else openComposer();
    return;
  }
  if (action === "nearby-location") {
    requestNearbyLocation();
    return;
  }
  if (action === "mall") {
    switchMall();
    return;
  }
  switchFeed(action || "recommend");
}
window.runFeedEmptyAction = runFeedEmptyAction;

})();
