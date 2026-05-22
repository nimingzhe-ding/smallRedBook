// utils.js — 全局状态、DOM 引用、共享工具函数
// 加载顺序：第一个加载，auth.js / feed.js / detail.js 等都依赖它
(function() {
// ==================== 全局状态 ====================
// 集中管理所有页面的运行时状态，避免分散在各模块中难以追踪
window.state = {
  notes: [],
  page: 1,
  loading: false,
  hasMore: true,
  query: "",
  feed: "recommend",
  mode: "feed",
  mallCategory: "all",
  mallQuery: "",
  searchTab: "notes",
  searchResults: { notes: [], videos: [], products: [], shops: [], topics: [] },
  searchProductNotes: {},
  searchMeta: { summary: "", relatedQueries: [] },
  mallProducts: [],
  cartItems: [],
  videoNotes: [],
  videoObserver: null,
  danmakuSource: null,
  danmakuSourceNoteId: null,
  danmakuStore: {},
  danmakuEnabled: JSON.parse(localStorage.getItem("hmdp_danmaku_enabled") || "true"),
  danmakuSpeed: Number(localStorage.getItem("hmdp_danmaku_speed") || 8.5),
  danmakuOpacity: Number(localStorage.getItem("hmdp_danmaku_opacity") || 0.9),
  danmakuBlockWords: localStorage.getItem("hmdp_danmaku_block_words") || "",
  danmakuHotOnly: JSON.parse(localStorage.getItem("hmdp_danmaku_hot_only") || "false"),
  videoMuted: JSON.parse(localStorage.getItem("hmdp_video_muted") || "true"),
  videoAutoplay: JSON.parse(localStorage.getItem("hmdp_video_autoplay") || "true"),
  merchant: null,
  merchantProducts: [],
  merchantOrders: [],
  productVouchers: [],
  selectedVoucherId: null,
  currentProduct: null,
  selectedSkuId: null,
  checkoutQuantity: 1,
  addresses: [],
  selectedAddressId: null,
  checkoutDraft: null,
  currentNote: null,
  currentProfile: null,
  profileTab: "works",
  currentUser: null,
  pendingNoteActions: new Set(),
  commentSubmitting: false,
  replyTarget: null,
  commentSort: "hot",
  nearbyLocation: null,
  nearbyLocationLoading: false,
  editingNoteId: null,
  trends: [],
  hotSearches: [],
  searchHistory: [],
  suggestionTimer: null,
  wallet: new Set(JSON.parse(localStorage.getItem("hmdp_wallet") || "[]")),
  collected: new Set(JSON.parse(localStorage.getItem("hmdp_collected") || "[]")),
  followed: new Set(JSON.parse(localStorage.getItem("hmdp_followed") || "[]")),
  aiSessionId: localStorage.getItem("hmdp_ai_session") || crypto.randomUUID(),
  notificationFilter: "all",
  aiSearchInsight: "",
  aiComposerTimer: null,
  aiCommentLoadedFor: null,
  afterLoginAction: null,
  notificationSettings: null,
  notificationStream: null,
  notificationStreamToken: null
};

localStorage.setItem("hmdp_ai_session", state.aiSessionId);

// ==================== DOM 引用缓存 ====================
// 页面加载时一次性获取所有 DOM 元素的引用，避免各处重复 querySelector
window.els = {
  feed: document.querySelector("#noteFeed"),
  loading: document.querySelector("#feedLoading"),
  status: document.querySelector("#statusBanner"),
  searchForm: document.querySelector("#searchForm"),
  search: document.querySelector("#searchInput"),
  suggestPopover: document.querySelector("#suggestPopover"),
  unifiedSearch: document.querySelector("#unifiedSearch"),
  unifiedSearchTitle: document.querySelector("#unifiedSearchTitle"),
  unifiedSearchSummary: document.querySelector("#unifiedSearchSummary"),
  unifiedSearchTabs: document.querySelector("#unifiedSearchTabs"),
  unifiedSearchResults: document.querySelector("#unifiedSearchResults"),
  profileHome: document.querySelector("#profileHome"),
  profileHomeResults: document.querySelector("#profileHomeResults"),
  profileHomeTabs: document.querySelector("#profileHomeTabs"),
  profileEditDialog: document.querySelector("#profileEditDialog"),
  profileEditForm: document.querySelector("#profileEditForm"),
  contentArea: document.querySelector(".content-area"),
  mallArea: document.querySelector("#mallArea"),
  videoArea: document.querySelector("#videoArea"),
  videoFeed: document.querySelector("#videoFeed"),
  productGrid: document.querySelector("#productGrid"),
  drawer: document.querySelector("#detailDrawer"),
  drawerMedia: document.querySelector("#drawerMedia"),
  drawerThumbs: document.querySelector("#drawerThumbs"),
  drawerAuthorGrowth: document.querySelector("#drawerAuthorGrowth"),
  noteProductBridge: document.querySelector("#noteProductBridge"),
  noteProductList: document.querySelector("#noteProductList"),
  noteProductCount: document.querySelector("#noteProductCount"),
  noteRelated: document.querySelector("#noteRelated"),
  noteRelatedList: document.querySelector("#noteRelatedList"),
  composer: document.querySelector("#composerDialog"),
  composerTitle: document.querySelector("#composerDialog h2"),
  composerDraftBar: document.querySelector("#composerDraftBar"),
  composerDraftText: document.querySelector("#composerDraftText"),
  clearComposerDraft: document.querySelector("#clearComposerDraft"),
  shopDialog: document.querySelector("#shopDialog"),
  productDialog: document.querySelector("#productDialog"),
  checkoutDialog: document.querySelector("#checkoutDialog"),
  checkoutBody: document.querySelector("#checkoutBody"),
  cartDialog: document.querySelector("#cartDialog"),
  notificationDialog: document.querySelector("#notificationDialog"),
  notificationList: document.querySelector("#notificationList"),
  notificationBadge: document.querySelector("#notificationBadge"),
  notificationSettings: document.querySelector("#notificationSettings"),
  accountPopover: document.querySelector("#accountPopover"),
  accountAvatar: document.querySelector("#accountAvatar"),
  accountName: document.querySelector("#accountName"),
  accountMeta: document.querySelector("#accountMeta"),
  customerServiceDialog: document.querySelector("#customerServiceDialog"),
  customerServiceContext: document.querySelector("#customerServiceContext"),
  customerServiceMessages: document.querySelector("#customerServiceMessages"),
  customerServiceForm: document.querySelector("#customerServiceForm"),
  customerServiceInput: document.querySelector("#customerServiceInput"),
  merchantDialog: document.querySelector("#merchantDialog"),
  voucherList: document.querySelector("#voucherList"),
  mallVoucherList: document.querySelector("#mallVoucherList"),
  cartList: document.querySelector("#cartList"),
  merchantPanel: document.querySelector("#merchantPanel"),
  composerForm: document.querySelector("#composerForm"),
  contentTypeInputs: document.querySelectorAll("input[name='contentType']"),
  contentFields: document.querySelectorAll("[data-content-field]"),
  videoUploadTip: document.querySelector("[data-video-upload-tip]"),
  imageFiles: document.querySelector("#imageFiles"),
  videoFile: document.querySelector("#videoFile"),
  uploadPreview: document.querySelector("#uploadPreview"),
  videoPreview: document.querySelector("#videoPreview"),
  loginDialog: document.querySelector("#loginDialog"),
  loginForm: document.querySelector("#loginForm"),
  loginFeedback: document.querySelector("#loginFeedback"),
  loginSubmitButton: document.querySelector("#loginSubmitButton"),
  noteSmart: document.querySelector("#noteSmart"),
  noteSmartText: document.querySelector("#noteSmartText"),
  shopBridge: document.querySelector("#shopBridge"),
  commentForm: document.querySelector("#commentForm"),
  commentInput: document.querySelector("#commentInput"),
  commentList: document.querySelector("#commentList"),
  commentCount: document.querySelector("#commentCount"),
  trendList: document.querySelector("#trendList"),
  toastContainer: document.querySelector("#toastContainer")
};

function setMobileTabActive(tab) {
  document.querySelectorAll(".mobile-tabbar [data-mobile-tab]").forEach(button => {
    const active = Boolean(tab) && button.dataset.mobileTab === tab;
    button.classList.toggle("is-active", active);
    if (active) button.setAttribute("aria-current", "page");
    else button.removeAttribute("aria-current");
  });
}
window.setMobileTabActive = setMobileTabActive;

// ==================== Toast 通知 ====================
// 非阻塞式轻提示，自动消失，支持 info / success / error 三种类型
function showToast(message, type = "info", duration = 2500) {
  const container = els.toastContainer;
  if (!container) return;
  const toast = document.createElement("div");
  toast.className = `toast toast-${type}`;
  toast.textContent = message;
  container.appendChild(toast);
  setTimeout(() => {
    toast.classList.add("toast-out");
    toast.addEventListener("animationend", () => toast.remove());
  }, duration);
}
window.showToast = showToast;

// ==================== 骨架屏 ====================
// 首次加载时显示占位卡片，提升感知性能
function renderSkeletons(count = 8) {
  els.feed.classList.remove("is-empty");
  let html = "";
  for (let i = 0; i < count; i++) {
    const ratio = [0.8, 1.0, 1.2, 1.4][i % 4];
    html += `
      <div class="skeleton-card">
        <div class="skeleton skeleton-cover" style="aspect-ratio: 3 / ${3 * ratio}"></div>
        <div class="skeleton skeleton-line"></div>
        <div class="skeleton skeleton-line"></div>
        <div class="skeleton-meta">
          <div class="skeleton skeleton-avatar"></div>
          <div class="skeleton skeleton-name"></div>
        </div>
      </div>`;
  }
  els.feed.innerHTML = html;
}
window.renderSkeletons = renderSkeletons;

function clearSkeletons() {
  els.feed.querySelectorAll(".skeleton-card").forEach(el => el.remove());
}
window.clearSkeletons = clearSkeletons;

// ==================== 媒体资源兜底 ====================
// 当后端未返回图片/头像时使用的默认占位图，非假内容数据
var fallbackAvatar = "https://images.unsplash.com/photo-1494790108377-be9c29b29330?auto=format&fit=crop&w=120&q=80";
window.fallbackAvatar = fallbackAvatar;

var defaultNoteImage = "https://images.unsplash.com/photo-1517248135467-4c7edcad34c4?auto=format&fit=crop&w=900&q=80";
window.defaultNoteImage = defaultNoteImage;

// ==================== HTTP 请求与格式化工具 ====================
// 自动处理 Token 注入、API 路径拼接、错误统一处理

const API_ORIGIN = (() => {
  const configured = localStorage.getItem("hmdp_api_origin");
  if (configured) return configured.replace(/\/$/, "");
  if (location.port === "8082" || location.port === "5500" || location.port === "5173") {
    return `${location.protocol}//${location.hostname}:8081`;
  }
  return "";
})();
window.API_ORIGIN = API_ORIGIN;

function apiUrl(url) {
  if (!url || /^https?:\/\//i.test(url)) return url;
  return `${API_ORIGIN}${url.startsWith("/") ? url : `/${url}`}`;
}
window.apiUrl = apiUrl;

function token() {
  return localStorage.getItem("hmdp_token") || "";
}
window.token = token;

// 统一请求方法：自动拼接 API 前缀、注入 Authorization header、解析 JSON 响应
async function request(url, options = {}) {
  const headers = new Headers(options.headers || {});
  if (token()) headers.set("authorization", token());
  if (options.body && !(options.body instanceof FormData)) {
    headers.set("Content-Type", "application/json");
  }
  const response = await fetch(apiUrl(url), { ...options, headers });
  if (!response.ok) throw new Error(`HTTP ${response.status}`);
  const result = await response.json();
  if (result.success === false) throw new Error(result.errorMsg || "请求失败");
  if (options.raw) return result;
  return result.data;
}
window.request = request;

// AI 专用请求方法：自动注入 sessionId
async function aiFlow(url, payload = {}) {
  return request(url, {
    method: "POST",
    body: JSON.stringify({
      sessionId: state.aiSessionId,
      ...payload
    })
  });
}
window.aiFlow = aiFlow;

async function checkAiRisk(content, scenario) {
  if (!String(content || "").trim()) return true;
  try {
    const data = await aiFlow("/ai/flow/risk-check", { content, scenario });
    const answer = String(data.answer || "");
    if (answer.includes("风险等级：高") || answer.includes("高风险")) {
      showStatus(`内容可能存在风险：${answer}`);
      return false;
    }
  } catch {
    // 风控接口降级时不阻塞用户流程，后端仍可继续做兜底校验。
  }
  return true;
}
window.checkAiRisk = checkAiRisk;

// 笔记数据标准化：补全图片/视频/作者信息，统一前后端字段差异
function normalizeNote(note, index = 0) {
  note = note || {};
  const id = note.id ?? note.noteId;
  const images = String(note.images || "").split(",").map(item => item.trim()).filter(Boolean);
  const image = note.cover || images[0] || defaultNoteImage;
  const parsedContent = parseVideoContent(note.content || "");
  const videoUrl = normalizeMedia(note.videoUrl || note.video || parsedContent.videoUrl);
  const contentType = normalizeContentType(note.mediaType || note.contentType, videoUrl);
  const likedCount = Number(note.likedCount ?? note.liked ?? 0);
  const commentCount = Number(note.commentCount ?? note.comments ?? 0);
  const collectCount = Number(note.collectCount ?? note.collects ?? 0);
  const isLike = Boolean(note.isLike ?? note.likedByMe ?? false);
  const isCollect = Boolean(note.isCollect ?? note.collected ?? state.collected.has(String(id)));
  const isFollow = Boolean(note.isFollow ?? note.followed ?? false);
  const authorId = note.userId ?? note.authorId;
  const authorName = note.name || note.authorName || `探店用户 ${authorId || ""}`.trim();
  const authorIcon = normalizeImage(note.icon || note.authorIcon) || fallbackAvatar;
  return {
    ...note,
    id,
    noteId: id,
    image,
    images,
    contentType,
    mediaType: contentType,
    userId: authorId,
    authorId,
    name: authorName,
    authorName,
    icon: authorIcon,
    authorIcon,
    liked: likedCount,
    likedCount,
    comments: commentCount,
    commentCount,
    collects: collectCount,
    collectCount,
    status: Number(note.status || 0),
    auditRemark: note.auditRemark || "",
    content: parsedContent.content,
    videoUrl,
    isVideo: ["VIDEO", "LIVE"].includes(contentType) && Boolean(videoUrl),
    isLike,
    isCollect,
    isFollow,
    likedByMe: isLike,
    collected: isCollect,
    followed: isFollow,
    score: Number(note.score || 0),
    products: Array.isArray(note.products) ? note.products.map(normalizeProduct) : [],
    creatorGrowth: note.creatorGrowth || null,
    relatedNotes: Array.isArray(note.relatedNotes) ? note.relatedNotes.map(item => normalizeNote(item)) : [],
    isOwner: Boolean(note.isOwner || (state.currentUser?.id && Number(authorId) === Number(state.currentUser.id))),
    tags: note.tags || "",
    ratio: [0.78, 1, 1.14, 1.28, 0.92][index % 5]
  };
}
window.normalizeNote = normalizeNote;

function normalizeContentType(contentType, videoUrl) {
  const normalized = String(contentType || "").trim().toUpperCase();
  const supported = ["IMAGE", "VIDEO", "LIVE", "PRODUCT_NOTE"];
  if (supported.includes(normalized)) return normalized;
  return videoUrl ? "VIDEO" : "IMAGE";
}
window.normalizeContentType = normalizeContentType;

function contentTypeLabel(contentType) {
  return {
    IMAGE: "图文",
    VIDEO: "视频",
    LIVE: "直播",
    PRODUCT_NOTE: "种草"
  }[contentType] || "图文";
}
window.contentTypeLabel = contentTypeLabel;

function parseVideoContent(content) {
  const text = stripHtml(content || "");
  const match = text.match(/#video:([^\s]+)/i);
  return {
    videoUrl: match ? match[1].trim() : "",
    content: match ? text.replace(match[0], "").trim() : text
  };
}
window.parseVideoContent = parseVideoContent;

function normalizeMedia(src) {
  if (!src) return "";
  if (/^https?:\/\//.test(src)) return src;
  if (src.startsWith("/")) return src;
  return `/imgs/${src}`;
}
window.normalizeMedia = normalizeMedia;

function normalizeImage(src) {
  if (!src) return "";
  if (/^https?:\/\//.test(src)) return src;
  if (src.startsWith("/imgs/")) return src;
  if (src.startsWith("/")) return src;
  return `/imgs/${src}`;
}
window.normalizeImage = normalizeImage;

function stripHtml(value) {
  const temp = document.createElement("div");
  temp.innerHTML = String(value).replaceAll("<br/>", "\n").replaceAll("<br>", "\n");
  return temp.textContent || temp.innerText || "";
}
window.stripHtml = stripHtml;

function showStatus(message) {
  els.status.textContent = message;
  els.status.hidden = false;
}
window.showStatus = showStatus;

// 用户行为采集：曝光/点击/搜索等事件异步上报，失败不影响主流程
function trackEvent(eventType, options = {}) {
  request("/note-event", {
    method: "POST",
    body: JSON.stringify({
      eventType,
      noteId: options.noteId || options.blogId || null,
      scene: options.scene || state.feed,
      keyword: options.keyword || state.query || null
    })
  }).catch(() => {
    // 行为采集不影响主流程，失败时静默忽略。
  });
}
window.trackEvent = trackEvent;

function hideStatus() {
  els.status.hidden = true;
}
window.hideStatus = hideStatus;

function requireLogin() {
  if (token()) return true;
  setLoginFeedback?.("");
  els.loginDialog.showModal();
  return false;
}
window.requireLogin = requireLogin;

function requireLoginThen(action) {
  if (token()) return true;
  state.afterLoginAction = action || null;
  requireLogin();
  return false;
}
window.requireLoginThen = requireLoginThen;

// 商品数据标准化：补全图片、价格、库存等字段
function normalizeProduct(product) {
  const images = String(product.images || "").split(",").map(item => item.trim()).filter(Boolean);
  return {
    ...product,
    images,
    image: normalizeImage(images[0]) || defaultNoteImage,
    title: product.title || "精选商品",
    subTitle: product.subTitle || product.sub_title || "内容同款好物",
    price: Number(product.price || 0),
    originPrice: Number(product.originPrice || product.origin_price || 0),
    stock: Number(product.stock || 0),
    sold: Number(product.sold || 0),
    category: product.category || "all"
  };
}
window.normalizeProduct = normalizeProduct;

// 店铺数据标准化：补全图片、地址、评分、销量等字段
function normalizeShop(shop) {
  const images = String(shop.images || "").split(",").map(item => item.trim()).filter(Boolean);
  return {
    ...shop,
    image: normalizeImage(images[0]) || defaultNoteImage,
    name: shop.name || "本地商家",
    area: shop.area || "本地生活",
    address: shop.address || "",
    avgPrice: Number(shop.avgPrice || 0),
    sold: Number(shop.sold || 0),
    score: Number(shop.score || 0)
  };
}
window.normalizeShop = normalizeShop;

function formatTime(value) {
  if (!value) return "刚刚";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "刚刚";
  return date.toLocaleDateString("zh-CN", { month: "short", day: "numeric" });
}
window.formatTime = formatTime;

function escapeHtml(value) {
  return String(value || "").replace(/[&<>"']/g, char => ({
    "&": "&amp;",
    "<": "&lt;",
    ">": "&gt;",
    '"': "&quot;",
    "'": "&#039;"
  })[char]);
}
window.escapeHtml = escapeHtml;

function formatMoney(value) {
  const number = Number(value || 0);
  return Number.isInteger(number / 100) ? String(number / 100) : (number / 100).toFixed(2);
}
window.formatMoney = formatMoney;

})();
