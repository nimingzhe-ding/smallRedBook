// admin.js - lightweight operations console for content governance
(function() {

async function openAdminCenter() {
  if (!requireLogin()) return;
  document.querySelector("#merchantDialogTitle").textContent = "运营后台";
  els.merchantPanel.innerHTML = `<p class="empty-text">正在加载运营数据...</p>`;
  els.merchantDialog.showModal();
  try {
    const [dashboard, notes, comments, danmaku] = await Promise.all([
      request("/admin/dashboard"),
      request("/admin/notes/reported"),
      request("/admin/comments/reported"),
      request("/admin/danmaku/reported")
    ]);
    renderAdminPanel(
      dashboard || {},
      Array.isArray(notes) ? notes : [],
      Array.isArray(comments) ? comments : [],
      Array.isArray(danmaku) ? danmaku : []
    );
  } catch (error) {
    els.merchantPanel.innerHTML = `<p class="empty-text">${escapeHtml(error.message || "运营后台加载失败，请确认管理员权限。")}</p>`;
  }
}
window.openAdminCenter = openAdminCenter;

function renderAdminPanel(dashboard, notes, comments, danmaku) {
  els.merchantPanel.innerHTML = `
    <section class="merchant-dashboard">
      ${adminMetric("笔记", dashboard.notes)}
      ${adminMetric("举报笔记", dashboard.reportedNotes)}
      ${adminMetric("举报评论", dashboard.reportedComments)}
      ${adminMetric("举报弹幕", dashboard.reportedDanmaku)}
      ${adminMetric("隐藏内容", Number(dashboard.hiddenNotes || 0) + Number(dashboard.hiddenComments || 0) + Number(dashboard.hiddenDanmaku || 0))}
      ${adminMetric("订单", dashboard.orders)}
    </section>
    ${renderReviewSection("举报笔记审核", "notes", notes, renderReportedNote)}
    ${renderReviewSection("举报评论审核", "comments", comments, renderReportedComment)}
    ${renderReviewSection("举报弹幕审核", "danmaku", danmaku, renderReportedDanmaku)}
  `;
  els.merchantPanel.querySelectorAll("[data-admin-review-action]").forEach(button => {
    button.addEventListener("click", () => handleReviewAction(
      button.dataset.adminReviewType,
      button.dataset.adminReviewAction,
      button.dataset.reviewId
    ));
  });
}

function adminMetric(label, value) {
  return `<span><strong>${Number(value || 0)}</strong>${escapeHtml(label)}</span>`;
}

function renderReviewSection(title, type, items, renderer) {
  return `
    <section class="merchant-section">
      <h3>${escapeHtml(title)}</h3>
      <div class="merchant-list admin-review-list">
        ${items.length ? items.map(item => renderer(item, type)).join("") : `<p class="empty-text">当前没有待处理举报。</p>`}
      </div>
    </section>
  `;
}

function renderReportedNote(note, type) {
  return renderReviewItem({
    id: note.id,
    type,
    title: note.title || "未命名笔记",
    meta: `${note.userName || "探店用户"} · 笔记 #${note.id} · ${formatTime(note.updateTime || note.createTime)}`,
    content: note.content || ""
  });
}

function renderReportedComment(comment, type) {
  return renderReviewItem({
    id: comment.id,
    type,
    title: comment.content || "",
    meta: `${comment.userName || "探店用户"} · ${comment.noteTitle || "未命名笔记"} · 评论 #${comment.id} · ${formatTime(comment.updateTime || comment.createTime)}`,
    content: `笔记 #${comment.noteId}`
  });
}

function renderReportedDanmaku(danmaku, type) {
  return renderReviewItem({
    id: danmaku.id,
    type,
    title: danmaku.content || "",
    meta: `${danmaku.userName || "探店用户"} · ${danmaku.noteTitle || "未命名笔记"} · ${danmaku.videoSecond || 0}s`,
    content: `弹幕 #${danmaku.id} · 笔记 #${danmaku.noteId}`
  });
}

function renderReviewItem(item) {
  return `
    <article class="merchant-item">
      <div>
        <strong>${escapeHtml(item.title)}</strong>
        <span>${escapeHtml(item.meta)}</span>
        <small>${escapeHtml(item.content)}</small>
      </div>
      <div class="cart-actions">
        <button class="ghost-button" type="button" data-admin-review-type="${item.type}" data-admin-review-action="restore" data-review-id="${item.id}">恢复</button>
        <button class="publish-button" type="button" data-admin-review-type="${item.type}" data-admin-review-action="hide" data-review-id="${item.id}">隐藏</button>
      </div>
    </article>
  `;
}

async function handleReviewAction(type, action, id) {
  if (!type || !action || !id) return;
  try {
    await request(`/admin/${type}/${id}/${action}`, { method: "POST" });
    showStatus(action === "restore" ? "内容已恢复。" : "内容已隐藏。");
    openAdminCenter();
  } catch (error) {
    showStatus(error.message || "处理失败，请稍后再试。");
  }
}

})();
