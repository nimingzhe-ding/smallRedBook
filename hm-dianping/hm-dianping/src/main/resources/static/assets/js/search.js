(function() {

// ------------------------------
// 统一搜索：笔记、视频、商品、商家和话题
// ------------------------------
async function enterUnifiedSearch(query, preferredTab = "notes", loadAi = true) {
  const keyword = String(query || "").trim();
  if (!keyword) {
    els.suggestPopover.classList.remove("is-open");
    els.trendList.hidden = true;
    state.mode = "feed";
    resetAndLoad();
    return;
  }
  showContentArea();
  setFeedTabsVisible(false);
  state.mode = "search";
  updateHomeCurationVisibility();
  hideProfileHome();
  document.querySelectorAll("[data-feed]").forEach(item => item.classList.remove("is-active"));
  state.query = keyword;
  state.mallQuery = keyword;
  state.searchTab = preferredTab;
  els.search.value = keyword;
  els.suggestPopover.classList.remove("is-open");
  els.trendList.hidden = true;
  els.feed.hidden = true;
  els.loading.hidden = true;
  els.unifiedSearch.hidden = false;
  els.unifiedSearchTitle.textContent = `搜索「${keyword}」`;
  els.unifiedSearchSummary.textContent = "正在同时搜索笔记、视频、商品、商家和话题";
  els.unifiedSearchTabs.innerHTML = "";
  els.unifiedSearchResults.innerHTML = `<p class="empty-text">正在搜索...</p>`;
  trackEvent("search", { scene: "unified", keyword });

  const results = await searchUnified(keyword);
  if (state.mode !== "search" || els.search.value.trim() !== keyword) {
    return;
  }
  state.searchResults = results;
  state.searchProductNotes = results.productNotes || {};
  state.searchMeta = results.meta || { summary: "", relatedQueries: [] };
  if (!state.searchResults[state.searchTab]?.length) {
    state.searchTab = ["notes", "videos", "products", "shops", "topics"].find(tab => state.searchResults[tab].length) || "notes";
  }
  renderUnifiedSearch();
  loadSearchHistory();
  if (loadAi) loadSmartRecommendation(keyword);
}

async function searchUnified(keyword) {
  try {
    const params = new URLSearchParams({ current: "1", query: keyword });
    const data = await request(`/notes/search?${params.toString()}`);
    return {
      notes: Array.isArray(data?.notes) ? data.notes.map(normalizeNote) : [],
      videos: Array.isArray(data?.videos) ? data.videos.map(normalizeNote) : [],
      products: Array.isArray(data?.products) ? data.products.map(normalizeProduct) : [],
      productNotes: normalizeProductNoteMap(data?.productNotes),
      shops: Array.isArray(data?.shops) ? data.shops.map(normalizeShop) : [],
      topics: Array.isArray(data?.topics) ? data.topics : [],
      meta: {
        summary: data?.summary || "",
        relatedQueries: Array.isArray(data?.relatedQueries) ? data.relatedQueries : []
      }
    };
  } catch {
    return {
      notes: [],
      videos: [],
      products: [],
      productNotes: {},
      shops: [],
      topics: [],
      meta: { summary: "", relatedQueries: [] }
    };
  }
}

function renderUnifiedSearch() {
  const tabs = [
    { key: "notes", label: "笔记" },
    { key: "videos", label: "视频" },
    { key: "products", label: "商品" },
    { key: "shops", label: "商家" },
    { key: "topics", label: "话题" }
  ];
  const total = tabs.reduce((sum, tab) => sum + state.searchResults[tab.key].length, 0);
  els.unifiedSearchSummary.textContent = state.searchMeta?.summary || `共找到 ${total} 条结果`;
  els.unifiedSearchTabs.innerHTML = tabs.map(tab => `
    <button type="button" class="${state.searchTab === tab.key ? "is-active" : ""}" data-search-tab="${tab.key}">
      ${tab.label}<small>${state.searchResults[tab.key].length}</small>
    </button>
  `).join("");
  els.unifiedSearchTabs.querySelectorAll("[data-search-tab]").forEach(button => {
    button.addEventListener("click", () => {
      state.searchTab = button.dataset.searchTab;
      renderUnifiedSearch();
    });
  });
  renderUnifiedSearchResults();
}

function renderUnifiedSearchResults() {
  const list = state.searchResults[state.searchTab] || [];
  if (!list.length) {
    els.unifiedSearchResults.innerHTML = `${renderSearchRefinements()}${renderAiSearchInsight()}${renderSearchEmptyState()}`;
    bindSearchRefinements();
    bindSearchDiscoveryActions(els.unifiedSearchResults);
    return;
  }
  if (state.searchTab === "notes" || state.searchTab === "videos") {
    const grid = document.createElement("div");
    grid.className = "masonry-feed unified-note-results";
    list.forEach(note => grid.appendChild(renderSearchNoteCard(note)));
    els.unifiedSearchResults.innerHTML = renderSearchRefinements() + renderAiSearchInsight();
    bindSearchRefinements();
    els.unifiedSearchResults.appendChild(grid);
    return;
  }
  if (state.searchTab === "products") {
    renderUnifiedProducts(list);
    return;
  }
  if (state.searchTab === "shops") {
    renderUnifiedShops(list);
    return;
  }
  renderUnifiedTopics(list);
}

function renderUnifiedProducts(products) {
  els.unifiedSearchResults.innerHTML = `
    ${renderSearchRefinements()}
    ${renderAiSearchInsight()}
    <div class="unified-product-grid">
      ${products.map(product => `
        <article class="product-card search-product-card">
          <button type="button" data-unified-product="${product.id}">
            <div class="product-image-wrap">
              <img class="product-image" src="${commerceImage(product.image)}" alt="${escapeHtml(product.title)}" loading="lazy" onerror="${commerceImageFallbackAttr()}">
              <span>已售 ${product.sold}</span>
            </div>
            <div class="product-body">
              <h2>${escapeHtml(product.title)}</h2>
              <p>${escapeHtml(product.subTitle)}</p>
              <div class="product-row">
                <strong>¥${formatMoney(product.price)}</strong>
                ${product.originPrice ? `<small>¥${formatMoney(product.originPrice)}</small>` : ""}
              </div>
            </div>
          </button>
          ${renderSearchReason(buildSearchReason("product", product))}
          ${renderProductRelatedNotes(product)}
        </article>
      `).join("")}
    </div>`;
  bindSearchRefinements();
  els.unifiedSearchResults.querySelectorAll("[data-unified-product]").forEach(button => {
    button.addEventListener("click", () => openProduct(button.dataset.unifiedProduct));
  });
  els.unifiedSearchResults.querySelectorAll("[data-product-note]").forEach(button => {
    button.addEventListener("click", () => {
      const note = findSearchProductNote(button.dataset.productNote);
      if (note) openDrawer(note);
    });
  });
}

function renderUnifiedShops(shops) {
  els.unifiedSearchResults.innerHTML = `
    ${renderSearchRefinements()}
    ${renderAiSearchInsight()}
    <div class="unified-list">
      ${shops.map(shop => `
        <article class="unified-row">
          <button type="button" data-unified-shop="${shop.id}">
            <img src="${normalizeImage(shop.image)}" alt="${escapeHtml(shop.name)}" onerror="this.onerror=null;this.src='${defaultNoteImage}'">
            <span>
              <strong>${escapeHtml(shop.name)}</strong>
              <small>${escapeHtml(shop.area)} · ${shop.avgPrice ? `人均 ¥${shop.avgPrice}` : "价格待补充"} · ${shop.score ? `${(shop.score / 10).toFixed(1)}分` : "暂无评分"}</small>
              <em>${escapeHtml(shop.address || "地址待补充")}</em>
            </span>
          </button>
          ${renderSearchReason(buildSearchReason("shop", shop))}
        </article>
      `).join("")}
    </div>`;
  bindSearchRefinements();
  els.unifiedSearchResults.querySelectorAll("[data-unified-shop]").forEach(button => {
    const shop = shops.find(item => String(item.id) === String(button.dataset.unifiedShop));
    button.addEventListener("click", () => openShopDialog(shop));
  });
}

function renderUnifiedTopics(topics) {
  els.unifiedSearchResults.innerHTML = `
    ${renderSearchRefinements()}
    ${renderAiSearchInsight()}
    <div class="unified-topic-grid">
      ${topics.map(topic => `
        <button type="button" data-unified-topic="${escapeHtml(topic.keyword)}">
          <strong>#${escapeHtml(topic.keyword)}</strong>
          <span>${topic.heat ? `热度 ${topic.heat}` : "继续探索这个话题"}</span>
          <em>${escapeHtml(buildSearchReason("topic", topic))}</em>
        </button>
      `).join("")}
    </div>`;
  bindSearchRefinements();
  els.unifiedSearchResults.querySelectorAll("[data-unified-topic]").forEach(button => {
    button.addEventListener("click", () => enterUnifiedSearch(button.dataset.unifiedTopic, "notes"));
  });
}

function normalizeProductNoteMap(map) {
  const result = {};
  Object.entries(map || {}).forEach(([productId, notes]) => {
    result[String(productId)] = Array.isArray(notes) ? notes.map(normalizeNote) : [];
  });
  return result;
}

function renderSearchNoteCard(note) {
  const card = createNoteCard(note);
  card.insertAdjacentHTML("beforeend", renderSearchReason(buildSearchReason("note", note)));
  return card;
}

function renderSearchReason(reason) {
  return reason ? `<p class="search-result-reason">${escapeHtml(reason)}</p>` : "";
}

function buildSearchReason(type, item) {
  const keyword = String(state.query || "").trim();
  if (type === "note") {
    if (item.products?.length) return `关联了 ${item.products.length} 件同款商品，适合边看内容边比较`;
    if (keyword && String(item.tags || "").includes(keyword)) return `标签命中「${keyword}」，按互动热度排序`;
    if (keyword && String(item.title || "").includes(keyword)) return `标题直接匹配「${keyword}」`;
    if (item.isVideo) return "视频内容优先展示，适合快速判断现场体验";
    if (Number(item.liked || 0) + Number(item.comments || 0) > 0) return "互动数据更高，优先推荐给你";
    return "按内容相关度和发布时间综合排序";
  }
  if (type === "product") {
    if (keyword && String(item.title || "").includes(keyword)) return `商品标题匹配「${keyword}」`;
    if (keyword && String(item.category || "").includes(keyword)) return `来自「${item.category}」类目`;
    if (Number(item.sold || 0) > 0) return `已售 ${item.sold}，可结合相关笔记判断`;
    if (Number(item.stock || 0) > 0) return `当前库存 ${item.stock}，可继续查看详情`;
    return "按商品标题、销量、优惠和库存综合排序";
  }
  if (type === "shop") {
    if (keyword && String(item.area || "").includes(keyword)) return `商圈匹配「${keyword}」`;
    if (Number(item.score || 0) > 0) return `评分 ${(Number(item.score) / 10).toFixed(1)}，按热度靠前`;
    return "按店铺名称、地址和销量综合排序";
  }
  if (type === "topic") {
    return item.heat ? `近期热度 ${item.heat}，继续探索相关笔记` : "可作为新的搜索方向";
  }
  return "";
}

function renderProductRelatedNotes(product) {
  const notes = state.searchProductNotes[String(product.id)] || [];
  if (!notes.length) return "";
  return `
    <div class="search-product-notes">
      <span>相关笔记</span>
      ${notes.slice(0, 2).map(note => `
        <button type="button" data-product-note="${note.id}">
          <img src="${normalizeImage(note.image)}" alt="">
          <strong>${escapeHtml(note.title)}</strong>
        </button>
      `).join("")}
    </div>
  `;
}

function findSearchProductNote(noteId) {
  return Object.values(state.searchProductNotes || {})
    .flat()
    .find(note => String(note.id) === String(noteId));
}

function renderAiSearchInsight() {
  return state.aiSearchInsight
    ? `<section class="ai-inline-card"><strong>搜索理解</strong><p>${escapeHtml(state.aiSearchInsight)}</p></section>`
    : "";
}

async function loadSmartRecommendation(question) {
  if (!token()) return;
  state.aiSearchInsight = "";
  if (state.mode !== "search") return;
  renderUnifiedSearch();
  try {
    const data = await aiFlow("/ai/flow/search", { query: question, scenario: state.searchTab });
    state.aiSearchInsight = data.answer || "";
  } catch {
    state.aiSearchInsight = "";
  }
  if (state.mode === "search" && els.search.value.trim() === String(question || "").trim()) {
    renderUnifiedSearch();
  }
}

// Note: this is the SECOND (and authoritative) definition of analyzeCurrentNote
async function analyzeCurrentNote(note) {
  els.noteSmart.hidden = false;
  els.noteSmartText.textContent = "正在总结这篇笔记的亮点、避雷点、价格和适合人群...";
  try {
    const data = await aiFlow("/ai/flow/note-summary", {
      noteId: note.id,
      title: note.title,
      content: note.content
    });
    els.noteSmartText.textContent = data.answer || data.content || "暂时没有生成有效总结。";
  } catch {
    els.noteSmartText.textContent = "智能看点暂时不可用，正文内容仍可正常查看。";
  }
}

// ------------------------------
// Search suggestions and trend ranking
// ------------------------------
function renderSuggestions(value = "") {
  const q = value.trim();
  window.clearTimeout(state.suggestionTimer);
  if (!q) {
    renderSearchDiscovery();
    return;
  }
  const fallback = searchKeywordPool().filter(item => item.includes(q)).slice(0, 8);
  renderSuggestionList(fallback);
  state.suggestionTimer = window.setTimeout(() => loadRemoteSuggestions(q), 180);
}

function searchKeywordPool() {
  return [
    ...state.searchHistory,
    ...state.hotSearches.map(item => item.keyword),
    ...state.trends.map(item => item.keyword)
  ].filter(Boolean).filter((item, index, list) => list.indexOf(item) === index);
}

function renderSuggestionList(list) {
  els.trendList.hidden = true;
  els.suggestPopover.innerHTML = list.length
    ? `<div class="suggest-section">${list.map(item => `<button type="button" data-suggestion="${escapeHtml(item)}">${escapeHtml(item)}</button>`).join("")}</div>`
    : "";
  els.suggestPopover.classList.toggle("is-open", list.length > 0 && document.activeElement === els.search);
  els.suggestPopover.querySelectorAll("[data-suggestion]").forEach(button => {
    button.addEventListener("click", () => runSearchKeyword(button.dataset.suggestion));
  });
}

async function loadRemoteSuggestions(q) {
  try {
    const list = await request(`/notes/suggestions?prefix=${encodeURIComponent(q)}`);
    if (document.activeElement === els.search && els.search.value.trim() === q) {
      renderSuggestionList(Array.isArray(list) ? list : []);
    }
  } catch {
    // 本地趋势兜底已经展示，不影响输入体验。
  }
}

async function loadTrends() {
  try {
    const data = await request("/notes/trends");
    state.trends = Array.isArray(data) ? data : [];
  } catch {
    state.trends = [];
  }
  renderTrends();
}

async function loadHotSearches() {
  try {
    const data = await request("/notes/hot-search");
    state.hotSearches = Array.isArray(data) ? data : [];
  } catch {
    state.hotSearches = [];
  }
}

async function loadSearchHistory() {
  if (!token()) {
    state.searchHistory = readScopedJson("hmdp_search_history", [], "guest");
    return state.searchHistory;
  }
  try {
    const data = await request("/notes/search-history");
    state.searchHistory = Array.isArray(data) ? data : [];
  } catch {
    state.searchHistory = [];
  }
  return state.searchHistory;
}

async function deleteSearchHistoryItem(keyword) {
  const value = String(keyword || "").trim();
  if (!value) return;
  if (token()) {
    try {
      await request(`/notes/search-history?keyword=${encodeURIComponent(value)}`, { method: "DELETE" });
    } catch {
      // 本地也同步移除，避免 UI 卡住。
    }
  }
  state.searchHistory = state.searchHistory.filter(item => item !== value);
  writeScopedJson("hmdp_search_history", state.searchHistory, token() ? undefined : "guest");
  renderSearchDiscovery();
}

async function clearSearchHistoryAll() {
  if (token()) {
    try {
      await request("/notes/search-history/all", { method: "DELETE" });
    } catch {
      // 本地清空仍继续。
    }
  }
  state.searchHistory = [];
  removeScopedStorage("hmdp_search_history", token() ? undefined : "guest");
  renderSearchDiscovery();
}

function rememberLocalSearch(keyword) {
  const value = String(keyword || "").trim();
  if (!value) return;
  state.searchHistory = [value, ...state.searchHistory.filter(item => item !== value)].slice(0, 10);
  if (!token()) {
    writeScopedJson("hmdp_search_history", state.searchHistory, "guest");
  }
}

function runSearchKeyword(keyword, preferredTab = "notes") {
  const value = String(keyword || "").trim();
  if (!value) return;
  rememberLocalSearch(value);
  state.query = value;
  els.search.value = value;
  els.suggestPopover.classList.remove("is-open");
  els.trendList.hidden = true;
  enterUnifiedSearch(value, preferredTab);
}

function renderSearchRefinements() {
  const related = state.searchMeta?.relatedQueries || [];
  if (!related.length) return "";
  return `
    <section class="search-refinements">
      ${related.map(item => `<button type="button" data-search-refine="${escapeHtml(item)}">${escapeHtml(item)}</button>`).join("")}
    </section>`;
}

function bindSearchRefinements() {
  els.unifiedSearchResults.querySelectorAll("[data-search-refine]").forEach(button => {
    button.addEventListener("click", () => enterUnifiedSearch(button.dataset.searchRefine, "notes"));
  });
}

function renderTrends() {
  if (!els.trendList) return;
  var hasTrends = state.trends && state.trends.length > 0;
  els.trendList.hidden = true;
  if (!hasTrends) return;
  els.trendList.innerHTML = state.trends.slice(0, 6).map((item, index) => `
    <button type="button" data-trend="${escapeHtml(item.keyword)}">
      <strong>${index + 1}</strong>
      <span>${escapeHtml(item.keyword)}</span>
      <small>${item.heat || ""}</small>
    </button>
  `).join("");
  els.trendList.querySelectorAll("button").forEach(button => {
    button.addEventListener("click", () => {
      trackEvent("search", { scene: "trend", keyword: button.dataset.trend });
      runSearchKeyword(button.dataset.trend);
    });
  });
}

async function renderSearchDiscovery() {
  const valueWhenRequested = els.search.value.trim();
  await Promise.all([loadSearchHistory(), state.hotSearches.length ? Promise.resolve() : loadHotSearches()]);
  if (els.search.value.trim() !== valueWhenRequested) return;
  const history = state.searchHistory.slice(0, 8);
  const hot = (state.hotSearches.length ? state.hotSearches : state.trends).slice(0, 8);
  if (!history.length && !hot.length) {
    els.suggestPopover.classList.remove("is-open");
    els.trendList.hidden = true;
    return;
  }
  els.trendList.hidden = true;
  els.suggestPopover.innerHTML = `
    ${history.length ? `
      <section class="search-discovery-section">
        <div><strong>最近搜索</strong><button type="button" data-search-history-clear>清空</button></div>
        <div class="search-chip-row">
          ${history.map(item => `
            <span class="search-history-chip">
              <button type="button" data-search-history="${escapeHtml(item)}">${escapeHtml(item)}</button>
              <button type="button" data-search-history-delete="${escapeHtml(item)}" aria-label="删除 ${escapeHtml(item)}">×</button>
            </span>
          `).join("")}
        </div>
      </section>
    ` : ""}
    ${hot.length ? `
      <section class="search-discovery-section">
        <div><strong>热门搜索</strong></div>
        <div class="search-hot-grid">
          ${hot.map((item, index) => `
            <button type="button" data-search-hot="${escapeHtml(item.keyword)}">
              <b>${index + 1}</b><span>${escapeHtml(item.keyword)}</span><small>${item.heat || ""}</small>
            </button>
          `).join("")}
        </div>
      </section>
    ` : ""}
  `;
  els.suggestPopover.classList.toggle("is-open", document.activeElement === els.search);
  bindSearchDiscoveryActions(els.suggestPopover);
}

function bindSearchDiscoveryActions(root) {
  root.querySelectorAll("[data-search-history], [data-search-hot], [data-search-empty]").forEach(button => {
    button.addEventListener("click", () => runSearchKeyword(button.dataset.searchHistory || button.dataset.searchHot || button.dataset.searchEmpty));
  });
  root.querySelectorAll("[data-search-history-delete]").forEach(button => {
    button.addEventListener("click", event => {
      event.stopPropagation();
      deleteSearchHistoryItem(button.dataset.searchHistoryDelete);
    });
  });
  root.querySelector("[data-search-history-clear]")?.addEventListener("click", clearSearchHistoryAll);
}

function renderSearchEmptyState() {
  const related = state.searchMeta?.relatedQueries || [];
  const hot = (state.hotSearches.length ? state.hotSearches : state.trends).map(item => item.keyword).filter(Boolean);
  const suggestions = [...related, ...hot].filter((item, index, list) => list.indexOf(item) === index).slice(0, 8);
  return `
    <section class="search-empty-state">
      <strong>这个分类暂时没有匹配结果</strong>
      <p>可以换个关键词，或者看看这些正在被搜索的内容。</p>
      ${suggestions.length ? `
        <div>
          ${suggestions.map(item => `<button type="button" data-search-empty="${escapeHtml(item)}">${escapeHtml(item)}</button>`).join("")}
        </div>
      ` : ""}
    </section>
  `;
}

// Export cross-module functions
window.enterUnifiedSearch = enterUnifiedSearch;
window.searchUnified = searchUnified;
window.renderUnifiedSearch = renderUnifiedSearch;
window.renderUnifiedSearchResults = renderUnifiedSearchResults;
window.renderUnifiedProducts = renderUnifiedProducts;
window.renderUnifiedShops = renderUnifiedShops;
window.renderUnifiedTopics = renderUnifiedTopics;
window.normalizeProductNoteMap = normalizeProductNoteMap;
window.renderSearchNoteCard = renderSearchNoteCard;
window.renderSearchReason = renderSearchReason;
window.buildSearchReason = buildSearchReason;
window.renderProductRelatedNotes = renderProductRelatedNotes;
window.findSearchProductNote = findSearchProductNote;
window.renderAiSearchInsight = renderAiSearchInsight;
window.renderSearchRefinements = renderSearchRefinements;
window.bindSearchRefinements = bindSearchRefinements;
window.loadSmartRecommendation = loadSmartRecommendation;
window.analyzeCurrentNote = analyzeCurrentNote;
window.renderSuggestions = renderSuggestions;
window.loadTrends = loadTrends;
window.renderTrends = renderTrends;
window.loadHotSearches = loadHotSearches;
window.loadSearchHistory = loadSearchHistory;
window.deleteSearchHistoryItem = deleteSearchHistoryItem;
window.clearSearchHistoryAll = clearSearchHistoryAll;
window.renderSearchDiscovery = renderSearchDiscovery;
window.runSearchKeyword = runSearchKeyword;
window.renderSearchEmptyState = renderSearchEmptyState;

})();
