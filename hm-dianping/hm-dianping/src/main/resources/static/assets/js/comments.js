(function() {

// ------------------------------
// Comments
// ------------------------------
async function loadComments(noteId) {
  els.commentList.innerHTML = `<p class="empty-text">正在加载评论...</p>`;
  try {
    const result = await request(`/notes/comments/of/note?noteId=${noteId}&sort=${state.commentSort}`, { raw: true });
    const comments = Array.isArray(result.data) ? result.data : [];
    updateCommentCount(result.total ?? comments.length);
    renderComments(comments);
  } catch {
    updateCommentCount(0);
    renderComments([]);
  }
}

function renderComments(comments) {
  if (!comments.length) {
    els.commentList.innerHTML = `<p class="empty-text">还没有评论，来抢第一条。</p>`;
    updateReplyComposer();
    return;
  }
  comments = hydrateReplyTargets(comments);
  els.commentList.innerHTML = comments.map(comment => `
    <article class="comment-item">
      <img class="comment-avatar" src="${normalizeImage(comment.icon) || fallbackAvatar}" alt="">
      <div class="comment-main">
        <div class="comment-head">
          <strong>${escapeHtml(comment.name || "探店用户")}</strong>
          <span>${formatTime(comment.createTime)}</span>
        </div>
        <p class="comment-copy">${escapeHtml(comment.content)}</p>
        <div class="comment-meta">
          <button class="comment-like" type="button" data-comment-id="${comment.id}">喜欢 ${comment.liked || 0}</button>
          <button class="comment-reply" type="button" data-comment-id="${comment.id}" data-parent-id="${comment.id}" data-name="${escapeHtml(comment.name || "探店用户")}">回复</button>
        </div>
        <div class="comment-tools">${renderCommentActions(comment)}</div>
        ${renderReplies(comment.replies || [], comment.id)}
      </div>
    </article>
  `).join("");
  els.commentList.querySelectorAll(".comment-like").forEach(button => {
    button.addEventListener("click", () => likeComment(button.dataset.commentId));
  });
  els.commentList.querySelectorAll(".comment-reply").forEach(button => {
    button.addEventListener("click", () => startReply(button));
  });
  els.commentList.querySelectorAll("[data-comment-delete]").forEach(button => {
    button.addEventListener("click", () => deleteComment(button.dataset.commentDelete));
  });
  els.commentList.querySelectorAll("[data-comment-report]").forEach(button => {
    button.addEventListener("click", () => reportComment(button.dataset.commentReport));
  });
  updateReplyComposer();
}

function hydrateReplyTargets(comments) {
  const nameMap = new Map();
  comments.forEach(comment => {
    nameMap.set(String(comment.id), comment.name || "探店用户");
    (comment.replies || []).forEach(reply => {
      nameMap.set(String(reply.id), reply.name || "探店用户");
    });
  });
  return comments.map(comment => ({
    ...comment,
    replies: (comment.replies || []).map(reply => ({
      ...reply,
      answerName: reply.answerName || nameMap.get(String(reply.answerId || "")) || ""
    }))
  }));
}

function renderReplies(replies, rootId) {
  if (!replies.length) return "";
  return `
    <div class="reply-list">
      ${replies.map(reply => `
        <article class="reply-item">
          <div class="comment-head">
            <strong>${escapeHtml(reply.name || "探店用户")}</strong>
            <span>${formatTime(reply.createTime)}</span>
          </div>
          <p class="comment-copy">${renderReplyTarget(reply, rootId)}${escapeHtml(reply.content)}</p>
          <div class="comment-meta">
            <button class="comment-like" type="button" data-comment-id="${reply.id}">喜欢 ${reply.liked || 0}</button>
            <button class="comment-reply" type="button" data-comment-id="${reply.id}" data-parent-id="${rootId}" data-name="${escapeHtml(reply.name || "探店用户")}">回复</button>
          </div>
          <div class="comment-tools">${renderCommentActions(reply)}</div>
        </article>
      `).join("")}
    </div>
  `;
}

function renderReplyTarget(reply, rootId) {
  const answerName = reply.answerName || "";
  if (!answerName || String(reply.answerId || "") === String(rootId || "")) return "";
  return `<span class="reply-target">回复 @${escapeHtml(answerName)}</span>`;
}

function renderCommentActions(comment) {
  if (comment.isOwner) {
    return `<button class="comment-action" type="button" data-comment-delete="${comment.id}">删除</button>`;
  }
  return `<button class="comment-action" type="button" data-comment-report="${comment.id}">举报</button>`;
}

function updateCommentCount(count) {
  const nextCount = Number(count || 0);
  els.commentCount.textContent = nextCount;
  if (state.currentNote) {
    state.currentNote.comments = nextCount;
    state.currentNote.commentCount = nextCount;
    applyNoteInteraction?.(state.currentNote, {
      comments: nextCount,
      commentCount: nextCount
    });
  }
}

function startReply(button) {
  if (!requireLogin()) return;
  state.replyTarget = {
    parentId: Number(button.dataset.parentId),
    answerId: Number(button.dataset.commentId),
    name: button.dataset.name
  };
  updateReplyComposer();
  els.commentInput.focus();
}

function clearReplyTarget() {
  state.replyTarget = null;
  updateReplyComposer();
  els.commentInput.focus();
}

function updateReplyComposer() {
  if (!els.commentForm || !els.commentInput) return;
  let bar = document.querySelector("#commentReplyBar");
  if (!bar) {
    els.commentForm.insertAdjacentHTML("beforebegin", `
      <div class="comment-reply-bar" id="commentReplyBar" hidden>
        <span></span>
        <button type="button" data-comment-reply-cancel>取消</button>
      </div>
    `);
    bar = document.querySelector("#commentReplyBar");
    bar.querySelector("[data-comment-reply-cancel]")?.addEventListener("click", clearReplyTarget);
  }
  if (state.replyTarget?.name) {
    bar.hidden = false;
    bar.querySelector("span").textContent = `正在回复 @${state.replyTarget.name}`;
    els.commentInput.placeholder = `回复 @${state.replyTarget.name}`;
  } else {
    bar.hidden = true;
    els.commentInput.placeholder = "说点什么…";
  }
}

async function likeComment(commentId) {
  if (!requireLogin()) return;
  try {
    await request(`/notes/comments/like/${commentId}`, { method: "PUT" });
    loadComments(state.currentNote.id);
  } catch {
    showStatus("评论点赞失败，请稍后再试。");
  }
}

async function deleteComment(commentId) {
  if (!state.currentNote || !requireLogin()) return;
  try {
    const data = await request(`/notes/comments/${commentId}`, { method: "DELETE" });
    updateCommentCount(data?.comments);
    loadComments(state.currentNote.id);
  } catch {
    showStatus("删除评论失败，请稍后再试。");
  }
}

async function reportComment(commentId) {
  if (!state.currentNote || !requireLogin()) return;
  try {
    const data = await request(`/notes/comments/report/${commentId}`, { method: "PUT" });
    updateCommentCount(data?.comments);
    loadComments(state.currentNote.id);
    showStatus("已提交举报。");
  } catch {
    showStatus("举报失败，请稍后再试。");
  }
}

async function submitComment(event) {
  event.preventDefault();
  if (state.commentSubmitting) return;
  if (!state.currentNote || !requireLogin()) return;
  const content = els.commentInput.value.trim();
  if (!content) return;
  state.commentSubmitting = true;
  const submitButton = els.commentForm.querySelector("button[type='submit']");
  if (submitButton) {
    submitButton.disabled = true;
    submitButton.textContent = "发送中";
  }
  try {
    if (!(await checkAiRisk(content, "comment"))) return;
    const payload = {
      noteId: state.currentNote.id,
      content,
      parentId: state.replyTarget?.parentId || 0,
      answerId: state.replyTarget?.answerId || 0
    };
    const data = await request("/notes/comments", {
      method: "POST",
      body: JSON.stringify(payload)
    });
    trackEvent("comment", { noteId: state.currentNote.id, scene: "detail" });
    updateCommentCount(data?.comments);
    state.replyTarget = null;
    els.commentInput.value = "";
    updateReplyComposer();
    loadComments(state.currentNote.id);
  } catch (error) {
    showStatus(error.message || "评论失败，请确认已登录。");
  } finally {
    state.commentSubmitting = false;
    if (submitButton) {
      submitButton.disabled = false;
      submitButton.textContent = "发送";
    }
  }
}

async function loadCommentSuggestions() {
  if (!token()) return;
  if (!state.currentNote || state.aiCommentLoadedFor === state.currentNote.id) return;
  state.aiCommentLoadedFor = state.currentNote.id;
  const host = ensureCommentAiPanel();
  host.innerHTML = `<span>正在生成自然评论建议...</span>`;
  try {
    const data = await aiFlow("/ai/flow/comment", {
      noteId: state.currentNote.id,
      title: state.currentNote.title,
      content: state.currentNote.content,
      query: state.replyTarget ? `回复 ${state.replyTarget.name || "评论"}` : "一级评论"
    });
    renderCommentSuggestions(data.answer || "");
  } catch {
    host.innerHTML = "";
  }
}

function ensureCommentAiPanel() {
  let panel = document.querySelector("#commentAiSuggestions");
  if (!panel) {
    els.commentForm.insertAdjacentHTML("beforebegin", `<div class="ai-suggestion-row" id="commentAiSuggestions"></div>`);
    panel = document.querySelector("#commentAiSuggestions");
  }
  return panel;
}

function renderCommentSuggestions(answer) {
  const panel = ensureCommentAiPanel();
  const suggestions = answer
    .split(/\n|[。；;]/)
    .map(item => item.replace(/^\d+[\.、]\s*/, "").trim())
    .filter(Boolean)
    .slice(0, 3);
  panel.innerHTML = suggestions.map(item => `<button type="button" data-comment-suggestion="${escapeHtml(item)}">${escapeHtml(item)}</button>`).join("");
  panel.querySelectorAll("[data-comment-suggestion]").forEach(button => {
    button.addEventListener("click", () => {
      els.commentInput.value = button.dataset.commentSuggestion;
      els.commentInput.focus();
    });
  });
}

// Export cross-module functions
window.loadComments = loadComments;
window.renderComments = renderComments;
window.hydrateReplyTargets = hydrateReplyTargets;
window.renderReplies = renderReplies;
window.renderReplyTarget = renderReplyTarget;
window.renderCommentActions = renderCommentActions;
window.updateCommentCount = updateCommentCount;
window.startReply = startReply;
window.clearReplyTarget = clearReplyTarget;
window.updateReplyComposer = updateReplyComposer;
window.likeComment = likeComment;
window.deleteComment = deleteComment;
window.reportComment = reportComment;
window.submitComment = submitComment;
window.loadCommentSuggestions = loadCommentSuggestions;
window.ensureCommentAiPanel = ensureCommentAiPanel;
window.renderCommentSuggestions = renderCommentSuggestions;

})();
