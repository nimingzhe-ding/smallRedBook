// feed.js — 笔记信息流：瀑布流加载、卡片渲染、频道切换
// 依赖 utils.js（state, els, token, request, requireLogin, showStatus, hideStatus 等）
// 被引用函数：openDrawer, showContentArea, pauseFeedVideos, renderCreatorGrowth
(function() {

// ==================== 笔记流加载与渲染 ====================
// 核心数据流：fetch → normalizeNote → createNoteCard → appendNotes
async function loadNotes() {
  if (state.loading || !state.hasMore) return;
  if ((state.feed === "follow" || state.mode === "mine" || state.mode === "collections") && !token()) {
    requireLogin();
    state.hasMore = false;
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
      showStatus(state.mode === "feed" ? "还没有匹配的笔记，换个频道或关键词试试。" : personalEmptyText());
    } else if (notes.length) {
      hideStatus();
    }
    appendNotes(notes);
    state.notes.push(...notes);
    notes.forEach(note => trackEvent("impression", { blogId: note.id, scene: state.mode }));
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
  return state.mode === "mine" ? "你还没有发布笔记，可以先发布第一篇。" : "你还没有收藏笔记。";
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
  return `/notes/feed?${params.toString()}`;
}
window.buildContentUrl = buildContentUrl;

function appendNotes(notes) {
  clearSkeletons();
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
  const button = document.createElement("button");
  button.type = "button";
  button.addEventListener("click", () => openDrawer(note));
  const badge = note.contentType === "IMAGE" ? "" : `<span class="video-badge">${contentTypeLabel(note.contentType)}</span>`;
  const cover = note.isVideo
    ? `<video class="note-image note-video-cover" style="--ratio:${note.ratio}" src="${normalizeMedia(note.videoUrl)}" poster="${normalizeImage(note.image)}" muted playsinline preload="metadata"></video>${badge}`
    : `<img class="note-image" style="--ratio:${note.ratio}" src="${normalizeImage(note.image)}" alt="${escapeHtml(note.title)}" loading="lazy">${badge}`;
  button.innerHTML = `
    <div class="note-cover">
      ${cover}
    </div>
    <div class="note-body">
      <h2 class="note-title">${escapeHtml(note.title)}</h2>
      <div class="note-meta">
        <span class="author-mini">
          <img class="avatar" src="${normalizeImage(note.icon)}" alt="">
          <span>${escapeHtml(note.name)}</span>
        </span>
        <span class="like-count"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 21.2 10.7 20C5.8 15.6 2.6 12.7 2.6 9a5 5 0 0 1 8.7-3.4A5 5 0 0 1 20 9c0 3.7-3.2 6.6-8.1 11l-1.3 1.2Z"/></svg>${note.liked}</span>
      </div>
    </div>
  `;
  card.appendChild(button);
  return card;
}
window.createNoteCard = createNoteCard;

// ==================== 流重置与切换 ====================
// 切换频道/分类/搜索时调用，清空当前流重新加载
function resetAndLoad(clearStatus = true) {
  hideUnifiedSearch();
  hideProfileHome();
  state.page = 1;
  state.hasMore = true;
  state.notes = [];
  els.feed.innerHTML = "";
  if (clearStatus) hideStatus();
  loadNotes();
}
window.resetAndLoad = resetAndLoad;

function hideUnifiedSearch() {
  if (!els.unifiedSearch) return;
  els.unifiedSearch.hidden = true;
  els.feed.hidden = false;
  els.loading.hidden = false;
}
window.hideUnifiedSearch = hideUnifiedSearch;

function hideProfileHome() {
  if (!els.profileHome) return;
  els.profileHome.hidden = true;
  els.feed.hidden = false;
  els.loading.hidden = false;
}
window.hideProfileHome = hideProfileHome;

// ==================== 频道/个人模式切换 ====================

function switchFeed(feed) {
  showContentArea();
  setMobileTabActive("home");
  state.mode = "feed";
  state.feed = feed;
  document.querySelectorAll("[data-feed]").forEach(item => {
    item.classList.toggle("is-active", item.dataset.feed === feed || (item.dataset.mobileTab === "home" && item.closest(".mobile-tabbar")));
  });
  resetAndLoad();
}
window.switchFeed = switchFeed;

function switchPersonalMode(mode) {
  if (!requireLogin()) return;
  showContentArea();
  state.mode = mode;
  state.query = "";
  els.search.value = "";
  document.querySelectorAll("[data-feed]").forEach(item => item.classList.remove("is-active"));
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

})();
