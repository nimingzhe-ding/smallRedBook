// admin.js — lightweight operations console for content governance
(function() {

async function openAdminCenter() {
  if (!requireLogin()) return;
  document.querySelector("#merchantDialogTitle").textContent = "运营后台";
  els.merchantPanel.innerHTML = `<p class="empty-text">正在加载运营数据...</p>`;
  els.merchantDialog.showModal();
  try {
    const dashboard = await request("/admin/dashboard");
    const reported = await request("/admin/comments/reported");
    renderAdminPanel(dashboard || {}, Array.isArray(reported) ? reported : []);
  } catch (error) {
    els.merchantPanel.innerHTML = `<p class="empty-text">${escapeHtml(error.message || "运营后台加载失败，请确认管理员权限。")}</p>`;
  }
}
window.openAdminCenter = openAdminCenter;

function renderAdminPanel(dashboard, comments) {
  els.merchantPanel.innerHTML = `
    <section class="merchant-dashboard">
      ${adminMetric("笔记", dashboard.notes)}
      ${adminMetric("举报评论", dashboard.reportedComments)}
      ${adminMetric("隐藏评论", dashboard.hiddenComments)}
      ${adminMetric("行为事件", dashboard.events)}
      ${adminMetric("商品", dashboard.products)}
      ${adminMetric("订单", dashboard.orders)}
    </section>
    <section class="merchant-section">
      <h3>举报评论审核</h3>
      <div class="merchant-list admin-review-list">
        ${renderReportedComments(comments)}
      </div>
    </section>
  `;
  els.merchantPanel.querySelectorAll("[data-admin-comment-action]").forEach(button => {
    button.addEventListener("click", () => handleReportedComment(
      button.dataset.adminCommentAction,
      button.dataset.commentId
    ));
  });
}

function adminMetric(label, value) {
  return `<span><strong>${Number(value || 0)}</strong>${escapeHtml(label)}</span>`;
}

function renderReportedComments(comments) {
  if (!comments.length) {
    return `<p class="empty-text">当前没有待处理举报。</p>`;
  }
  return comments.map(comment => `
    <article class="merchant-item">
      <div>
        <strong>${escapeHtml(comment.content || "")}</strong>
        <span>${escapeHtml(comment.userName || "探店用户")} · ${escapeHtml(comment.noteTitle || "未命名笔记")}</span>
        <small>评论 #${comment.id} · 笔记 #${comment.noteId} · ${formatTime(comment.updateTime || comment.createTime)}</small>
      </div>
      <div class="cart-actions">
        <button class="ghost-button" type="button" data-admin-comment-action="restore" data-comment-id="${comment.id}">恢复</button>
        <button class="publish-button" type="button" data-admin-comment-action="hide" data-comment-id="${comment.id}">隐藏</button>
      </div>
    </article>
  `).join("");
}

async function handleReportedComment(action, commentId) {
  if (!commentId) return;
  try {
    await request(`/admin/comments/${commentId}/${action}`, { method: "POST" });
    showStatus(action === "restore" ? "评论已恢复。" : "评论已隐藏。");
    openAdminCenter();
  } catch (error) {
    showStatus(error.message || "处理失败，请稍后再试。");
  }
}

})();
