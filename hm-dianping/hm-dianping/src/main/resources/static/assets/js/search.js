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
  state.mode = "search";
  hideProfileHome();
  document.querySelectorAll("[data-feed]").forEach(item => item.classList.remove("is-active"));
  state.query = keyword;
  state.mallQuery = keyword;
  state.searchTab = preferredTab;
  els.search.value = keyword;
  els.suggestPopover.classList.remove("is-open");
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
  state.searchMeta = results.meta || { summary: "", relatedQueries: [] };
  if (!state.searchResults[state.searchTab]?.length) {
    state.searchTab = ["notes", "videos", "products", "shops", "topics"].find(tab => state.searchResults[tab].length) || "notes";
  }
  renderUnifiedSearch();
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
    els.unifiedSearchResults.innerHTML = `${renderSearchRefinements()}${renderAiSearchInsight()}<p class="empty-text">这个分类暂时没有匹配结果。</p>`;
    bindSearchRefinements();
    return;
  }
  if (state.searchTab === "notes" || state.searchTab === "videos") {
    const grid = document.createElement("div");
    grid.className = "masonry-feed unified-note-results";
    list.forEach(note => grid.appendChild(createNoteCard(note)));
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
        <article class="product-card">
          <button type="button" data-unified-product="${product.id}">
            <div class="product-image-wrap">
              <img class="product-image" src="${normalizeImage(product.image)}" alt="${escapeHtml(product.title)}" loading="lazy">
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
        </article>
      `).join("")}
    </div>`;
  bindSearchRefinements();
  els.unifiedSearchResults.querySelectorAll("[data-unified-product]").forEach(button => {
    button.addEventListener("click", () => openProduct(button.dataset.unifiedProduct));
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
            <img src="${normalizeImage(shop.image)}" alt="${escapeHtml(shop.name)}">
            <span>
              <strong>${escapeHtml(shop.name)}</strong>
              <small>${escapeHtml(shop.area)} · ${shop.avgPrice ? `人均 ¥${shop.avgPrice}` : "价格待补充"} · ${shop.score ? `${(shop.score / 10).toFixed(1)}分` : "暂无评分"}</small>
              <em>${escapeHtml(shop.address || "地址待补充")}</em>
            </span>
          </button>
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
        </button>
      `).join("")}
    </div>`;
  bindSearchRefinements();
  els.unifiedSearchResults.querySelectorAll("[data-unified-topic]").forEach(button => {
    button.addEventListener("click", () => enterUnifiedSearch(button.dataset.unifiedTopic, "notes"));
  });
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
  const source = state.trends.map(item => item.keyword).filter(Boolean);
  const list = q ? source.filter(item => item.includes(q)).slice(0, 6) : [];
  els.suggestPopover.innerHTML = list.map(item => `<button type="button" data-suggestion="${item}">${item}</button>`).join("");
  els.suggestPopover.classList.toggle("is-open", list.length > 0 && document.activeElement === els.search);
  els.trendList.hidden = true;
  els.suggestPopover.querySelectorAll("button").forEach(button => {
    button.addEventListener("click", () => {
      state.query = button.dataset.suggestion;
      els.search.value = state.query;
      els.suggestPopover.classList.remove("is-open");
      enterUnifiedSearch(state.query);
    });
  });
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
      state.query = button.dataset.trend;
      els.search.value = state.query;
      trackEvent("search", { scene: "trend", keyword: state.query });
      enterUnifiedSearch(state.query);
    });
  });
}

// Export cross-module functions
window.enterUnifiedSearch = enterUnifiedSearch;
window.searchUnified = searchUnified;
window.renderUnifiedSearch = renderUnifiedSearch;
window.renderUnifiedSearchResults = renderUnifiedSearchResults;
window.renderUnifiedProducts = renderUnifiedProducts;
window.renderUnifiedShops = renderUnifiedShops;
window.renderUnifiedTopics = renderUnifiedTopics;
window.renderAiSearchInsight = renderAiSearchInsight;
window.renderSearchRefinements = renderSearchRefinements;
window.bindSearchRefinements = bindSearchRefinements;
window.loadSmartRecommendation = loadSmartRecommendation;
window.analyzeCurrentNote = analyzeCurrentNote;
window.renderSuggestions = renderSuggestions;
window.loadTrends = loadTrends;
window.renderTrends = renderTrends;

})();
