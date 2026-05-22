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
    runSearchKeyword(els.search.value);
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
  document.querySelector("#closeComposerDialog").addEventListener("click", closeComposer);
  document.querySelector("#cancelComposerDialog").addEventListener("click", closeComposer);
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

  var briefVideo = document.querySelector("[data-brief-action='video']");
  if (briefVideo) briefVideo.addEventListener("click", switchVideo);
  var briefMall = document.querySelector("[data-brief-action='mall']");
  if (briefMall) briefMall.addEventListener("click", switchMall);
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
      else if (actionBtn.dataset.action === "dm") startDmFromNotification(item);
    } else {
      navigateFromNotification(item);
    }
  });
  document.querySelectorAll("[data-message-mode]").forEach(function(button) {
    button.addEventListener("click", function() {
      switchMessageMode(button.dataset.messageMode);
      if (button.dataset.messageMode === "notifications") loadNotifications();
    });
  });
  els.dmConversationList?.addEventListener("click", function(event) {
    var button = event.target.closest("[data-dm-id]");
    if (button) selectDmConversation(button.dataset.dmId);
  });
  els.dmSearchResults?.addEventListener("click", function(event) {
    var button = event.target.closest("[data-dm-user-id]");
    if (button) openDmWithUser(button.dataset.dmUserId);
  });
  els.startDmButton?.addEventListener("click", startDmFromInput);
  els.dmSearchInput?.addEventListener("input", function() {
    clearTimeout(state.dmSearchTimer);
    state.dmSearchTimer = setTimeout(searchDmUsers, 240);
  });
  els.dmSearchInput?.addEventListener("keydown", function(event) {
    if (event.key === "Enter") {
      event.preventDefault();
      startDmFromInput();
    } else if (event.key === "Escape" && els.dmSearchResults) {
      els.dmSearchResults.hidden = true;
    }
  });
  els.dmComposeForm?.addEventListener("submit", function(event) {
    event.preventDefault();
    sendDmMessage(els.dmInput.value);
  });
  els.clearDmConversation?.addEventListener("click", clearActiveDmConversation);
  els.dmBackButton?.addEventListener("click", closeDmMobileChat);
  els.dmOpenProfile?.addEventListener("click", function() {
    var userId = state.activeDmConversation?.peerUserId;
    if (userId) openUserProfile(userId);
  });
  document.querySelectorAll(".notification-tab").forEach(function(tab) {
    tab.addEventListener("click", function() {
      document.querySelectorAll(".notification-tab").forEach(function(t) { t.classList.remove("is-active"); });
      tab.classList.add("is-active");
      state.notificationFilter = tab.dataset.filter;
      loadNotifications();
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
  els.profileMessageButton?.addEventListener("click", function() {
    var userId = state.currentProfile?.userId;
    if (userId) openDmWithUser(userId);
  });
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
        els.commentInput.placeholder = "说点什么…";
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

  if ("IntersectionObserver" in window && els.loading) {
    var feedObserver = new IntersectionObserver(function(entries) {
      if (!entries.some(function(entry) { return entry.isIntersecting; })) return;
      if (state.mode === "mall" || state.mode === "video" || state.mode === "search" || state.mode === "profile") return;
      loadNotes();
    }, { rootMargin: "640px 0px" });
    feedObserver.observe(els.loading);
  }

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
  setFeedTabsVisible(true);
  setMessageEntryActive(false);
  initUser();
  refreshNotificationBadge();
  setInterval(refreshNotificationBadge, 60000);
  loadNotes();
  loadTrends();
  loadHotSearches();
  loadSearchHistory();
  openSharedTargetFromUrl();

  function openSharedTargetFromUrl() {
    var params = new URLSearchParams(location.search);
    var noteId = params.get("noteId");
    var productId = params.get("productId");
    var orderId = params.get("orderId");
    if (noteId) {
      openDrawer({ id: Number(noteId) });
      return;
    }
    if (productId) {
      switchMall();
      setTimeout(function() { openProduct(Number(productId)); }, 80);
      return;
    }
    if (orderId) {
      openOrderDetail(Number(orderId));
    }
  }
})();
