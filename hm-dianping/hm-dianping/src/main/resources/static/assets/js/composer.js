(function() {
  // ------------------------------
  // 发布流程：按内容类型上传素材并提交笔记
  // References: state, els, request, requireLogin, showStatus,
  //   normalizeContentType, normalizeMedia, escapeHtml, checkAiRisk,
  //   aiFlow, resetAndLoad, closeDrawer
  // ------------------------------
  var COMPOSER_DRAFT_KEY = "hmdp_composer_draft";

  function composerDraftKey() {
    return scopedStorageKey(COMPOSER_DRAFT_KEY);
  }

  function getComposerContentType() {
    return normalizeContentType(els.composerForm.elements.contentType?.value, "");
  }

  function setComposerSubmitText(text) {
    var submitButton = els.composerForm.querySelector("[data-composer-submit]");
    if (!submitButton) submitButton = els.composerForm.querySelector(".publish-button");
    if (submitButton) submitButton.textContent = text;
  }

  function splitComposerTags(value) {
    return String(value || "")
      .split(/[,，#\s]+/)
      .map(function(item) { return item.trim().replace(/^#+/, ""); })
      .filter(Boolean);
  }

  function normalizeComposerTags(value) {
    return [...new Set(splitComposerTags(value))]
      .slice(0, 8)
      .join(",");
  }

  function syncComposerTopicPresets() {
    var tagInput = els.composerForm.elements.tags;
    if (!tagInput) return;
    var selected = splitComposerTags(tagInput.value);
    document.querySelectorAll("[data-topic-preset]").forEach(function(button) {
      button.classList.toggle("is-active", selected.includes(button.dataset.topicPreset));
    });
  }

  function addComposerTopicPreset(topic) {
    var tagInput = els.composerForm.elements.tags;
    if (!tagInput || !topic) return;
    var tags = splitComposerTags(tagInput.value);
    if (!tags.includes(topic)) tags.push(topic);
    tagInput.value = tags.slice(0, 8).join(",");
    syncComposerTopicPresets();
    saveComposerDraft(true);
  }

  function saveComposerDraftManually() {
    if (state.editingNoteId) {
      showStatus("编辑内容请直接点保存修改。");
      return;
    }
    saveComposerDraft(true);
    showStatus(hasComposerDraft() ? "草稿已保存。" : "先写一点内容，再保存草稿。");
  }

  function readComposerDraft() {
    try {
      return JSON.parse(localStorage.getItem(composerDraftKey()) || "null");
    } catch {
      return null;
    }
  }

  function hasComposerDraft(draft) {
    draft = draft === undefined ? readComposerDraft() : draft;
    if (!draft) return false;
    return ["title", "images", "videoUrl", "shopId", "productIds", "tags", "topics", "content"]
      .some(key => String(draft[key] || "").trim());
  }

  function collectComposerDraft() {
    var form = els.composerForm.elements;
    return {
      contentType: getComposerContentType(),
      title: form.title?.value || "",
      images: form.images?.value || "",
      videoUrl: form.videoUrl?.value || "",
      shopId: form.shopId?.value || "",
      productIds: form.productIds?.value || "",
      tags: form.tags?.value || "",
      topics: form.topics?.value || "",
      content: form.content?.value || "",
      updatedAt: new Date().toISOString()
    };
  }

  function saveComposerDraft(showTip) {
    showTip = showTip || false;
    if (state.editingNoteId) return;
    var draft = collectComposerDraft();
    if (!hasComposerDraft(draft)) {
      localStorage.removeItem(composerDraftKey());
      renderComposerDraftState();
      return;
    }
    localStorage.setItem(composerDraftKey(), JSON.stringify(draft));
    renderComposerDraftState(showTip ? "草稿已保存，发布失败也不会丢。" : "");
  }

  function restoreComposerDraft() {
    if (state.editingNoteId) return false;
    var draft = readComposerDraft();
    if (!hasComposerDraft(draft)) {
      renderComposerDraftState();
      return false;
    }
    var form = els.composerForm.elements;
    if (draft.contentType && form.contentType) {
      var typeInput = [...els.contentTypeInputs].find(function(input) { return input.value === draft.contentType; });
      if (typeInput) typeInput.checked = true;
    }
    ["title", "images", "videoUrl", "shopId", "productIds", "tags", "topics", "content"].forEach(function(name) {
      if (form[name] && draft[name] != null) {
        form[name].value = draft[name];
      }
    });
    applyComposerType();
    syncComposerTopicPresets();
    renderComposerDraftState("已恢复上次未发布的草稿。");
    return true;
  }

  function clearComposerDraft(resetForm) {
    resetForm = resetForm || false;
    localStorage.removeItem(composerDraftKey());
    if (resetForm) {
      els.composerForm.reset();
      els.uploadPreview.innerHTML = "";
      els.videoPreview.innerHTML = "";
      applyComposerType();
      syncComposerTopicPresets();
    }
    renderComposerDraftState();
  }

  function renderComposerDraftState(message) {
    message = message || "";
    if (!els.composerDraftBar) return;
    var draft = readComposerDraft();
    var hasDraft = hasComposerDraft(draft);
    els.composerDraftBar.hidden = !hasDraft;
    if (hasDraft && els.composerDraftText) {
      var time = draft.updatedAt ? new Date(draft.updatedAt).toLocaleString("zh-CN", { hour12: false }) : "";
      els.composerDraftText.textContent = message || (time ? "草稿已保存 " + time : "草稿已自动保存");
    }
  }

  function scheduleComposerAssistant() {
    window.clearTimeout(state.aiComposerTimer);
    var form = els.composerForm.elements;
    var title = String(form.title?.value || "").trim();
    var content = String(form.content?.value || "").trim();
    if (title.length + content.length < 12) return;
    state.aiComposerTimer = window.setTimeout(function() { loadComposerSuggestions(); }, 1200);
  }

  function ensureComposerAiPanel() {
    var panel = document.querySelector("#composerAiSuggestions");
    if (!panel) {
      els.composerForm.querySelector("[name='content']").closest("label").insertAdjacentHTML("afterend", `
        <section class="ai-inline-card composer-ai" id="composerAiSuggestions">
          <strong>创作建议</strong>
          <p>写一点内容后，会自动给你标题、标签和正文优化建议。</p>
        </section>
      `);
      panel = document.querySelector("#composerAiSuggestions");
    }
    return panel;
  }

  async function loadComposerSuggestions() {
    var form = els.composerForm.elements;
    var title = String(form.title?.value || "").trim();
    var content = String(form.content?.value || "").trim();
    var panel = ensureComposerAiPanel();
    panel.innerHTML = "<strong>创作建议</strong><p>正在根据当前草稿生成标题、标签和正文建议...</p>";
    try {
      var data = await aiFlow("/ai/flow/compose", {
        scenario: getComposerContentType(),
        title: title,
        content: content
      });
      var answer = data.answer || "";
      panel.innerHTML = `
        <strong>创作建议</strong>
        <p>${escapeHtml(answer)}</p>
        <button type="button" id="useComposerAiText">把建议补进正文</button>
      `;
      document.querySelector("#useComposerAiText").addEventListener("click", function() {
        form.content.value = (form.content.value.trim() + "\n\n" + answer).trim();
        saveComposerDraft(true);
      });
    } catch {
      panel.innerHTML = "<strong>创作建议</strong><p>创作建议暂时不可用，草稿仍会自动保存。</p>";
    }
  }

  function appendComposerImageUrls(urls) {
    if (!urls.length) return;
    var input = els.composerForm.elements.images;
    var existed = String(input.value || "").split(",").map(function(item) { return item.trim(); }).filter(Boolean);
    input.value = [...new Set([...existed, ...urls])].join(",");
  }

  function setComposerVideoUrl(url) {
    if (!url) return;
    var input = els.composerForm.elements.videoUrl;
    input.value = url;
  }

  function fillComposerFromNote(note) {
    var form = els.composerForm.elements;
    var typeInput = [...els.contentTypeInputs].find(function(input) { return input.value === note.contentType; });
    if (typeInput) typeInput.checked = true;
    form.title.value = note.title || "";
    form.images.value = Array.isArray(note.images) ? note.images.join(",") : (note.images || note.image || "");
    form.videoUrl.value = note.videoUrl || "";
    form.shopId.value = note.shopId || "";
    form.productIds.value = Array.isArray(note.products) ? note.products.map(function(product) { return product.id; }).filter(Boolean).join(",") : "";
    form.tags.value = note.tags || "探店";
    form.topics.value = "";
    form.content.value = note.content || "";
    els.uploadPreview.innerHTML = "";
    els.videoPreview.innerHTML = note.videoUrl ? "<video src=\"" + normalizeMedia(note.videoUrl) + "\" controls muted playsinline></video>" : "";
    applyComposerType();
    syncComposerTopicPresets();
  }

  function resetComposerMode() {
    state.editingNoteId = null;
    if (els.composerTitle) els.composerTitle.dataset.mode = "create";
    setComposerSubmitText("发布作品");
  }

  function applyComposerType() {
    var contentType = getComposerContentType();
    var isVideoLike = contentType === "VIDEO";
    var isProductNote = contentType === "PRODUCT_NOTE";
    var titles = {
      IMAGE: "发布图文",
      VIDEO: "发布视频",
      PRODUCT_NOTE: "发布商品种草"
    };
    if (els.composerTitle) {
      els.composerTitle.textContent = state.editingNoteId ? "编辑内容" : (titles[contentType] || "发布内容");
    }
    els.contentFields.forEach(function(field) {
      var scope = field.dataset.contentField;
      field.hidden = (scope === "video" && !isVideoLike) || (scope === "shop" && !isProductNote);
    });
    if (els.videoUploadTip) {
      els.videoUploadTip.textContent = "支持 MP4/WebM/MOV，发布前会自动上传";
    }
    var shopInput = els.composerForm.elements.shopId;
    if (shopInput) {
      shopInput.required = isProductNote;
      shopInput.placeholder = isProductNote ? "商品种草需要关联店铺" : "关联店铺，选填";
    }
    var productInput = els.composerForm.elements.productIds;
    if (productInput) {
      productInput.required = isProductNote;
      productInput.placeholder = isProductNote ? "商品种草至少挂载一个商品 ID" : "多个商品 ID 用英文逗号分隔";
    }
  }

  function parseProductIds(value) {
    return String(value || "")
      .split(",")
      .map(function(item) { return Number(item.trim()); })
      .filter(function(id) { return Number.isInteger(id) && id > 0; })
      .filter(function(id, index, list) { return list.indexOf(id) === index; })
      .slice(0, 6);
  }

  async function uploadSelectedImages() {
    var files = [...els.imageFiles.files];
    var uploaded = [];
    for (var i = 0; i < files.length; i++) {
      var formData = new FormData();
      formData.append("file", files[i]);
      var result = await request("/upload/note", { method: "POST", body: formData });
      uploaded.push(uploadResultUrl(result));
    }
    return uploaded;
  }

  async function uploadSelectedVideo() {
    var file = els.videoFile?.files?.[0];
    if (!file) return "";
    var formData = new FormData();
    formData.append("file", file);
    var result = await request("/upload/video", { method: "POST", body: formData });
    return uploadResultUrl(result);
  }

  function uploadResultUrl(result) {
    if (typeof result === "string") return "/imgs" + result;
    return result?.url || (result?.objectName ? "/imgs" + result.objectName : "");
  }

  async function submitComposer(event) {
    if (event.submitter && event.submitter.value === "cancel") return;
    event.preventDefault();
    if (!requireLogin()) return;
    saveComposerDraft();
    var submitButton = els.composerForm.querySelector("[data-composer-submit]") || els.composerForm.querySelector(".publish-button");
    submitButton.disabled = true;
    submitButton.textContent = "发布中";
    try {
      var contentType = getComposerContentType();
      var isVideoLike = contentType === "VIDEO";
      var isProductNote = contentType === "PRODUCT_NOTE";
      submitButton.textContent = "上传素材中";
      var uploaded = await uploadSelectedImages();
      var uploadedVideo = isVideoLike ? await uploadSelectedVideo() : "";
      appendComposerImageUrls(uploaded);
      setComposerVideoUrl(uploadedVideo);
      submitButton.textContent = "发布中";
      saveComposerDraft();
      var form = new FormData(els.composerForm);
      var manualImages = String(els.composerForm.elements.images?.value || "").trim();
      var videoUrl = isVideoLike ? String(els.composerForm.elements.videoUrl?.value || "").trim() : "";
      var shopId = isProductNote && form.get("shopId") ? Number(form.get("shopId")) : null;
      var productIds = parseProductIds(form.get("productIds"));
      var content = String(form.get("content") || "").trim();
      var normalizedTags = normalizeComposerTags(form.get("tags"));
      if (!(await checkAiRisk((form.get("title") || "") + "\n" + content, "publish"))) {
        saveComposerDraft(true);
        return;
      }
      if (isVideoLike && !videoUrl) {
        showStatus("视频内容需要上传视频或填写视频地址。");
        saveComposerDraft(true);
        return;
      }
      if (!uploaded.length && !manualImages && !videoUrl) {
        showStatus("发布笔记至少需要一张图片。");
        saveComposerDraft(true);
        return;
      }
      if (isProductNote && !shopId) {
        showStatus("商品种草需要关联店铺 ID。");
        saveComposerDraft(true);
        return;
      }
      if (isProductNote && !productIds.length) {
        showStatus("商品种草至少需要挂载一个商品。");
        saveComposerDraft(true);
        return;
      }
      if (content.length < 12) {
        showStatus("正文再多写一点，会更像一篇有价值的探店笔记。");
        saveComposerDraft(true);
        return;
      }
      var payload = {
        title: form.get("title"),
        images: manualImages,
        videoUrl: videoUrl,
        contentType: contentType,
        shopId: shopId,
        productIds: productIds,
        tags: normalizedTags,
        content: mergeTopics(content, form.get("topics"))
      };
      var editingId = state.editingNoteId;
      await request(editingId ? "/notes/" + editingId : "/notes", {
        method: editingId ? "PUT" : "POST",
        body: JSON.stringify(payload)
      });
      if (!editingId) clearComposerDraft();
      resetComposerMode();
      els.composer.close();
      showStatus(editingId ? "保存成功，作品已更新。" : "发布成功，作品已展示到内容流。", "success");
      els.composerForm.reset();
      els.uploadPreview.innerHTML = "";
      els.videoPreview.innerHTML = "";
      applyComposerType();
      syncComposerTopicPresets();
      if (state.currentNote) closeDrawer();
      resetAndLoad();
    } catch (error) {
      saveComposerDraft(true);
      showStatus(error.message || "发布失败，内容已保存到草稿箱。请确认已登录，且图片、店铺信息有效。");
    } finally {
      submitButton.disabled = false;
      submitButton.textContent = state.editingNoteId ? "保存修改" : "发布作品";
    }
  }

  function mergeTopics(content, topics) {
    var body = String(content || "").trim();
    var topicText = String(topics || "").trim();
    return topicText ? body + "\n\n" + topicText : body;
  }

  function closeComposer() {
    if (!els.composer?.open) return;
    saveComposerDraft();
    els.composer.close();
  }

  // ------------------------------
  // Composer open / edit / delete
  // ------------------------------
  function openComposer(preferredType) {
    if (!requireLoginThen("compose")) return;
    resetComposerMode();
    if (!restoreComposerDraft()) {
      els.composerForm.reset();
      if (preferredType) {
        var typeInput = [...els.contentTypeInputs].find(function(input) { return input.value === preferredType; });
        if (typeInput) typeInput.checked = true;
      }
      els.uploadPreview.innerHTML = "";
      els.videoPreview.innerHTML = "";
      applyComposerType();
      syncComposerTopicPresets();
    }
    els.composer.showModal();
  }

  function openComposerForEdit(note) {
    if (!note?.isOwner || !requireLogin()) return;
    state.editingNoteId = note.id;
    fillComposerFromNote(note);
    setComposerSubmitText("保存修改");
    renderComposerDraftState();
    els.composer.showModal();
  }

  async function deleteCurrentNote(note) {
    if (!note?.isOwner || !requireLogin()) return;
    var confirmed = window.confirm("确定删除这篇笔记吗？删除后不可恢复。");
    if (!confirmed) return;
    try {
      await request("/notes/" + note.id, { method: "DELETE" });
      state.notes = state.notes.filter(function(item) { return String(item.id) !== String(note.id); });
      state.videoNotes = state.videoNotes.filter(function(item) { return String(item.id) !== String(note.id); });
      document.querySelectorAll(".note-card").forEach(function(card) {
        var title = card.querySelector(".note-title")?.textContent || "";
        if (title === note.title) card.remove();
      });
      closeDrawer();
      showStatus("笔记已删除。");
      resetAndLoad(false);
    } catch (error) {
      showStatus(error.message || "删除失败，请稍后再试。");
    }
  }

  // Export cross-module functions
  window.COMPOSER_DRAFT_KEY = COMPOSER_DRAFT_KEY;
  window.composerDraftKey = composerDraftKey;
  window.getComposerContentType = getComposerContentType;
  window.splitComposerTags = splitComposerTags;
  window.normalizeComposerTags = normalizeComposerTags;
  window.syncComposerTopicPresets = syncComposerTopicPresets;
  window.addComposerTopicPreset = addComposerTopicPreset;
  window.saveComposerDraftManually = saveComposerDraftManually;
  window.readComposerDraft = readComposerDraft;
  window.hasComposerDraft = hasComposerDraft;
  window.collectComposerDraft = collectComposerDraft;
  window.saveComposerDraft = saveComposerDraft;
  window.restoreComposerDraft = restoreComposerDraft;
  window.clearComposerDraft = clearComposerDraft;
  window.renderComposerDraftState = renderComposerDraftState;
  window.scheduleComposerAssistant = scheduleComposerAssistant;
  window.ensureComposerAiPanel = ensureComposerAiPanel;
  window.loadComposerSuggestions = loadComposerSuggestions;
  window.appendComposerImageUrls = appendComposerImageUrls;
  window.setComposerVideoUrl = setComposerVideoUrl;
  window.fillComposerFromNote = fillComposerFromNote;
  window.resetComposerMode = resetComposerMode;
  window.applyComposerType = applyComposerType;
  window.parseProductIds = parseProductIds;
  window.uploadSelectedImages = uploadSelectedImages;
  window.uploadSelectedVideo = uploadSelectedVideo;
  window.uploadResultUrl = uploadResultUrl;
  window.submitComposer = submitComposer;
  window.mergeTopics = mergeTopics;
  window.closeComposer = closeComposer;
  window.openComposer = openComposer;
  window.openComposerForEdit = openComposerForEdit;
  window.deleteCurrentNote = deleteCurrentNote;
})();
