(function() {
  // ------------------------------
  // Event binding and app initialization
  // References: state, els, searchTimer, enterUnifiedSearch,
  //   renderSuggestions, resetAndLoad, switchFeed, switchMall,
  //   switchVideo, closeDrawer, openComposer, openMerchantCenter,
  //   openCartDialog, openOrdersDialog, openNotificationDialog,
  //   markNotificationsRead, markSingleNotificationRead, deleteNotification,
  //   navigateFromNotification, openProfileEdit, submitProfileEdit,
  //   loadProfileTab, sendCode, submitComposer, submitLogin,
  //   submitComment, loadCommentSuggestions, loadComments,
  //   saveComposerDraft, applyComposerType, renderComposerDraftState,
  //   clearComposerDraft, resetComposerMode, scheduleComposerAssistant,
  //   addCurrentProductToCart, buyCurrentProductNow, loadProducts,
  //   initUser, refreshNotificationBadge, loadNotes,
  //   loadTrends, loadCollectState
  // ------------------------------

  els.searchForm.addEventListener("submit", function(event) {
    event.preventDefault();
    enterUnifiedSearch(els.search.value);
  });

  var searchTimer;
  els.search.addEventListener("focus", function() { renderSuggestions(els.search.value); });
  els.search.addEventListener("input", function() {
    renderSuggestions(els.search.value);
    clearTimeout(searchTimer);
  });
  els.search.addEventListener("keydown", function(event) {
    if (event.key === "Escape") {
      els.search.value = "";
      els.suggestPopover.classList.remove("is-open");
      els.trendList.hidden = true;
    }
  });
  document.addEventListener("click", function(event) {
    if (!event.target.closest(".search-box")) {
      els.suggestPopover.classList.remove("is-open");
      els.trendList.hidden = true;
    }
    if (!event.target.closest("#accountMenu")) {
      hideAccountPopover?.();
    }
  });

  els.imageFiles.addEventListener("change", function() {
    els.uploadPreview.innerHTML = [...els.imageFiles.files].map(function(file) {
      var url = URL.createObjectURL(file);
      return '<img src="' + url + '" alt="">';
    }).join("");
    saveComposerDraft();
  });

  els.videoFile.addEventListener("change", function() {
    var file = els.videoFile.files[0];
    if (!file) {
      els.videoPreview.innerHTML = "";
      saveComposerDraft();
      return;
    }
    var url = URL.createObjectURL(file);
    els.videoPreview.innerHTML = '<video src="' + url + '" controls muted playsinline></video>';
    saveComposerDraft();
  });

  els.contentTypeInputs.forEach(function(input) {
    input.addEventListener("change", function() {
      applyComposerType();
      saveComposerDraft();
    });
  });
  applyComposerType();
  renderComposerDraftState();

  els.composerForm.addEventListener("input", function() {
    saveComposerDraft();
    scheduleComposerAssistant();
  });
  els.composerForm.addEventListener("change", function() { saveComposerDraft(); });
  els.clearComposerDraft?.addEventListener("click", function() {
    clearComposerDraft(true);
    showStatus("草稿已清空。");
  });
  els.composer.addEventListener("close", function() {
    resetComposerMode();
  });

  document.querySelectorAll("[data-feed]").forEach(function(button) {
    button.addEventListener("click", function() { switchFeed(button.dataset.feed); });
  });

  document.querySelectorAll("[data-smart-query]").forEach(function(button) {
    button.addEventListener("click", function() {
      state.query = button.dataset.smartQuery;
      els.search.value = state.query;
      enterUnifiedSearch(state.query);
    });
  });

  document.querySelectorAll("[data-close-drawer]").forEach(function(item) { item.addEventListener("click", closeDrawer); });

  document.querySelector("#openComposer").addEventListener("click", openComposer);
  document.querySelector("#mobilePublish").addEventListener("click", openComposer);
  document.querySelector("#closeShopDialog").addEventListener("click", function() { els.shopDialog.close(); });
  document.querySelector("#mobileMall").addEventListener("click", switchMall);
  var mobileProfile = document.querySelector("#mobileProfile");
  if (mobileProfile) mobileProfile.addEventListener("click", function() { openMyProfile(); });
  document.querySelector("#openCart").addEventListener("click", openCartDialog);
  document.querySelector("#openOrders").addEventListener("click", openOrdersDialog);
  document.querySelector("#openMerchantCenter").addEventListener("click", openMerchantCenter);
  document.querySelector("#openAdminCenter")?.addEventListener("click", openAdminCenter);
  document.querySelector("#closeProductDialog").addEventListener("click", function() { els.productDialog.close(); });
  document.querySelector("#closeCheckoutDialog").addEventListener("click", function() { els.checkoutDialog.close(); });
  document.querySelector("#closeCartDialog").addEventListener("click", function() { els.cartDialog.close(); });
  document.querySelectorAll("#openNotifications, #mobileNotifications").forEach(function(button) {
    button.addEventListener("click", function() {
      setMobileTabActive("messages");
      openNotificationDialog();
    });
  });
  document.querySelector("#closeNotificationDialog").addEventListener("click", function() { els.notificationDialog.close(); });
  document.querySelector("#markNotificationsRead").addEventListener("click", markNotificationsRead);
  document.querySelector("#toggleNotificationSettings")?.addEventListener("click", function() {
    if (!els.notificationSettings) return;
    els.notificationSettings.hidden = !els.notificationSettings.hidden;
    if (!els.notificationSettings.hidden) loadNotificationSettings();
  });
  els.notificationSettings?.addEventListener("change", function(event) {
    var input = event.target.closest("[data-notification-setting]");
    if (!input) return;
    updateNotificationSetting(input.dataset.notificationSetting, input.checked);
  });
  document.querySelector("#openCustomerService").addEventListener("click", function() { openCustomerServiceDialog(); });
  document.querySelector("#closeCustomerService").addEventListener("click", function() { els.customerServiceDialog.close(); });
  els.customerServiceForm.addEventListener("submit", submitCustomerService);
  bindCustomerQuickButtons();
  els.notificationList.addEventListener("click", function(e) {
    var actionBtn = e.target.closest("[data-action]");
    var item = e.target.closest(".notification-item");
    if (!item) return;
    var id = Number(item.dataset.id);
    if (actionBtn) {
      e.stopPropagation();
      if (actionBtn.dataset.action === "read") markSingleNotificationRead(id);
      else if (actionBtn.dataset.action === "delete") deleteNotification(id);
    } else {
      navigateFromNotification(item);
    }
  });
  document.querySelectorAll(".notification-tab").forEach(function(tab) {
    tab.addEventListener("click", function() {
      document.querySelectorAll(".notification-tab").forEach(function(t) { t.classList.remove("is-active"); });
      tab.classList.add("is-active");
      state.notificationFilter = tab.dataset.filter;
      openNotificationDialog();
    });
  });
  document.querySelector("#closeMerchantDialog").addEventListener("click", function() { els.merchantDialog.close(); });
  document.querySelector("#addProductCart").addEventListener("click", addCurrentProductToCart);
  document.querySelector("#buyProductNow").addEventListener("click", buyCurrentProductNow);
  document.querySelectorAll("[data-mall-category]").forEach(function(button) {
    button.addEventListener("click", function() {
      state.mallCategory = button.dataset.mallCategory;
      document.querySelectorAll("[data-mall-category]").forEach(function(item) {
        item.classList.toggle("is-active", item === button);
      });
      loadProducts();
    });
  });
  document.querySelector("#loginButton").addEventListener("click", openLoginDialog);
  els.accountPopover?.addEventListener("click", function(event) {
    var button = event.target.closest("[data-account-action]");
    if (!button) return;
    handleAccountAction(button.dataset.accountAction);
  });
  els.loginDialog.addEventListener("click", function(e) { if (e.target === els.loginDialog) els.loginDialog.close(); });
  document.querySelector("#editProfileButton").addEventListener("click", openProfileEdit);
  els.profileEditForm.addEventListener("submit", submitProfileEdit);
  document.querySelectorAll(".profile-home-stats [data-profile-tab]").forEach(function(button) {
    button.addEventListener("click", function() { loadProfileTab(button.dataset.profileTab); });
  });
  document.querySelector("#sendCodeButton").addEventListener("click", sendCode);
  els.composerForm.addEventListener("submit", submitComposer);
  els.loginForm.addEventListener("submit", submitLogin);
  els.commentForm.addEventListener("submit", submitComment);
  els.commentInput.addEventListener("focus", loadCommentSuggestions);
  document.querySelectorAll("[data-comment-sort]").forEach(function(button) {
    button.addEventListener("click", function() {
      state.commentSort = button.dataset.commentSort || "hot";
      document.querySelectorAll("[data-comment-sort]").forEach(function(item) {
        item.classList.toggle("is-active", item === button);
      });
      if (state.currentNote) loadComments(state.currentNote.id);
    });
  });

  window.addEventListener("keydown", function(event) {
    if (event.key === "Escape") {
      if (state.replyTarget) {
        state.replyTarget = null;
        els.commentInput.placeholder = "说点什么...";
        return;
      }
      closeDrawer();
    }
  });

  window.addEventListener("scroll", function() {
    if (state.mode === "mall" || state.mode === "video" || state.mode === "search" || state.mode === "profile") return;
    var nearBottom = window.innerHeight + window.scrollY > document.body.offsetHeight - 620;
    if (nearBottom) loadNotes();
  }, { passive: true });

  // ------------------------------
  // App initialization
  // ------------------------------
  state.mode = "feed";
  state.query = "";
  els.search.value = "";
  els.suggestPopover.classList.remove("is-open");
  els.trendList.hidden = true;
  els.unifiedSearch.hidden = true;
  els.profileHome.hidden = true;
  els.feed.hidden = false;
  els.loading.hidden = false;
  initUser();
  refreshNotificationBadge();
  setInterval(refreshNotificationBadge, 60000);
  loadNotes();
  loadTrends();
  openSharedNoteFromUrl();

  function openSharedNoteFromUrl() {
    var noteId = new URLSearchParams(location.search).get("noteId");
    if (!noteId) return;
    openDrawer({ id: Number(noteId) });
  }
})();
