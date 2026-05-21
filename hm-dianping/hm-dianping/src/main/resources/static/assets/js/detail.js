(function() {
  // ------------------------------
  // Note detail drawer, image carousel, shop bridge, vouchers,
  // creator growth, related notes, and like/collect/follow interactions
  // References: state, els, request, normalizeNote, normalizeProduct,
  //   normalizeImage, normalizeMedia, escapeHtml, formatTime, formatMoney,
  //   showStatus, requireLogin, trackEvent, defaultNoteImage, pauseFeedVideos,
  //   loadComments, analyzeCurrentNote, loadRecommendReason,
  //   openComposerForEdit, deleteCurrentNote, openProduct, addToCart,
  //   buyProductNow, token
  // ------------------------------

  async function openDrawer(note) {
    try {
      const freshNote = await request(`/notes/${note.id}`);
      note = normalizeNote(freshNote);
    } catch {
      // 详情聚合接口不可用时继续展示卡片已有数据。
    }
    pauseFeedVideos();
    state.currentNote = note;
    state.replyTarget = null;
    state.commentSort = "hot";
    document.querySelectorAll("[data-comment-sort]").forEach(button => {
      button.classList.toggle("is-active", button.dataset.commentSort === "hot");
    });
    els.noteSmart.hidden = false;
    els.noteSmartText.textContent = "正在生成笔记亮点、避雷点、适合人群和推荐理由...";
    renderDrawerImages(note);
    document.querySelector("#drawerAvatar").src = normalizeImage(note.icon);
    document.querySelector("#drawerAuthor").textContent = note.name;
    document.querySelector("#drawerTime").textContent = formatTime(note.createTime);
    renderCreatorGrowth(els.drawerAuthorGrowth, note.creatorGrowth);
    document.querySelector("#drawerTitle").textContent = note.title;
    document.querySelector("#drawerContent").textContent = note.content || "这个作者还没有填写更多内容。";
    renderShopBridge(note.shop);
    renderNoteProducts(note.products);
    renderRelatedNotes(note.relatedNotes || []);
    document.querySelector("#drawerLike").innerHTML = `♥ 赞 ${note.liked}`;
    if (note.isCollect) state.collected.add(String(note.id));
    if (note.isFollow) state.followed.add(String(note.userId));
    document.querySelector("#drawerCollect").innerHTML = state.collected.has(String(note.id)) ? "★ 已收藏" : "☆ 收藏";
    document.querySelector("#drawerFollow").textContent = state.followed.has(String(note.userId)) ? "已关注" : "关注";
    document.querySelector("#drawerLike").onclick = () => likeNote(note);
    document.querySelector("#drawerCollect").onclick = () => toggleCollect(note);
    document.querySelector("#drawerFollow").onclick = () => toggleFollow(note);
    document.querySelector("#drawerAnalyze").hidden = true;
    document.querySelector("#drawerAnalyze").onclick = () => analyzeCurrentNote(note);
    const editButton = document.querySelector("#drawerEdit");
    const deleteButton = document.querySelector("#drawerDelete");
    editButton.hidden = !note.isOwner;
    deleteButton.hidden = !note.isOwner;
    editButton.onclick = () => openComposerForEdit(note);
    deleteButton.onclick = () => deleteCurrentNote(note);
    els.drawer.classList.add("is-open");
    els.drawer.setAttribute("aria-hidden", "false");
    document.body.style.overflow = "hidden";
    trackEvent("detail", { blogId: note.id, scene: "detail" });
    loadCollectState(note);
    loadComments(note.id);
    analyzeCurrentNote(note);
    loadRecommendReason(note);
  }

  function renderShopBridge(shop) {
    if (!shop) {
      els.shopBridge.hidden = true;
      return;
    }
    const image = String(shop.images || "").split(",").map(item => item.trim()).filter(Boolean)[0] || defaultNoteImage;
    document.querySelector("#shopImage").src = normalizeImage(image);
    document.querySelector("#shopName").textContent = shop.name || "关联店铺";
    const score = shop.score ? `${(shop.score / 10).toFixed(1)}分` : "暂无评分";
    const price = shop.avgPrice ? `人均 ¥${shop.avgPrice}` : "价格待补充";
    document.querySelector("#shopMeta").textContent = `${score} · ${price} · ${shop.area || "本地生活"}`;
    document.querySelector("#shopAddress").textContent = shop.address || shop.openHours || "";
    const hasVoucher = Boolean(shop.voucherId);
    document.querySelector("#shopOffer").hidden = !hasVoucher;
    if (hasVoucher) {
      document.querySelector("#voucherTitle").textContent = shop.voucherTitle || "店铺优惠";
      document.querySelector("#voucherSubTitle").textContent = shop.voucherSubTitle || "到店前先看看优惠";
      document.querySelector("#voucherAction").onclick = () => openShopDialog(shop);
    }
    els.shopBridge.hidden = false;
  }

  function renderNoteProducts(products = []) {
    const list = Array.isArray(products) ? products.map(normalizeProduct) : [];
    if (!list.length) {
      els.noteProductBridge.hidden = true;
      els.noteProductList.innerHTML = "";
      els.noteProductCount.textContent = "0";
      return;
    }
    els.noteProductCount.textContent = String(list.length);
    els.noteProductList.innerHTML = list.map(product => `
      <article class="note-product-card">
        <button class="note-product-main" type="button" data-note-product-open="${product.id}">
          <img src="${normalizeImage(product.image)}" alt="${escapeHtml(product.title)}">
          <span>
            <strong>${escapeHtml(product.title)}</strong>
            <small>${escapeHtml(product.subTitle || "内容同款好物")}</small>
            <b>¥${formatMoney(product.price)}</b>
          </span>
        </button>
        <div class="note-product-actions">
          <button type="button" data-note-product-cart="${product.id}">加购</button>
          <button type="button" data-note-product-buy="${product.id}">购买</button>
        </div>
      </article>
    `).join("");
    els.noteProductList.querySelectorAll("[data-note-product-open]").forEach(button => {
      button.addEventListener("click", () => openProduct(button.dataset.noteProductOpen));
    });
    els.noteProductList.querySelectorAll("[data-note-product-cart]").forEach(button => {
      button.addEventListener("click", () => addToCart(button.dataset.noteProductCart, 1));
    });
    els.noteProductList.querySelectorAll("[data-note-product-buy]").forEach(button => {
      button.addEventListener("click", () => buyProductNow(button.dataset.noteProductBuy));
    });
    els.noteProductBridge.hidden = false;
  }

  function renderCreatorGrowth(target, growth) {
    if (!target) return;
    if (!growth) {
      target.innerHTML = "";
      return;
    }
    const badges = Array.isArray(growth.badges) ? growth.badges : [];
    target.innerHTML = `
      <span class="creator-level">${escapeHtml(growth.levelName || `Lv.${growth.level || 1}`)}</span>
      ${growth.qualityCreator ? `<span class="creator-quality">优质创作者</span>` : ""}
      <span>连续发布 ${growth.continuousPublishDays || 0} 天</span>
      ${badges.map(badge => `<span>${escapeHtml(badge)}</span>`).join("")}
    `;
  }

  function renderRelatedNotes(notes = []) {
    const list = Array.isArray(notes) ? notes.map(normalizeNote).filter(note => note.id !== state.currentNote?.id) : [];
    if (!list.length) {
      els.noteRelated.hidden = true;
      els.noteRelatedList.innerHTML = "";
      return;
    }
    els.noteRelatedList.innerHTML = list.map(note => `
      <button class="related-note" type="button" data-related-note="${note.id}">
        <img src="${normalizeImage(note.image)}" alt="${escapeHtml(note.title)}">
        <span>
          <strong>${escapeHtml(note.title)}</strong>
          <small>${escapeHtml(note.name || "探店用户")} · ${note.liked || 0} 赞</small>
        </span>
      </button>
    `).join("");
    els.noteRelatedList.querySelectorAll("[data-related-note]").forEach(button => {
      const related = list.find(note => String(note.id) === String(button.dataset.relatedNote));
      button.addEventListener("click", () => related && openDrawer(related));
    });
    els.noteRelated.hidden = false;
  }

  async function openShopDialog(shop) {
    if (!shop?.id) return;
    document.querySelector("#shopDialogName").textContent = shop.name || "店铺详情";
    document.querySelector("#shopDialogImage").src = normalizeImage(String(shop.images || "").split(",")[0] || defaultNoteImage);
    const score = shop.score ? `${(shop.score / 10).toFixed(1)}分` : "暂无评分";
    const price = shop.avgPrice ? `人均 ¥${shop.avgPrice}` : "价格待补充";
    document.querySelector("#shopDialogMeta").textContent = `${score} · ${price} · ${shop.area || "本地生活"}`;
    document.querySelector("#shopDialogAddress").textContent = shop.address || "";
    document.querySelector("#shopDialogHours").textContent = shop.openHours ? `营业时间 ${shop.openHours}` : "";
    els.voucherList.innerHTML = `<p class="empty-text">正在加载优惠...</p>`;
    els.shopDialog.showModal();
    try {
      const vouchers = await request(`/voucher/list/${shop.id}`);
      renderVouchers(Array.isArray(vouchers) ? vouchers : []);
    } catch {
      renderVouchers([]);
    }
  }

  function renderVouchers(vouchers) {
    if (!vouchers.length) {
      els.voucherList.innerHTML = `<p class="empty-text">这家店暂时没有可用优惠。</p>`;
      return;
    }
    els.voucherList.innerHTML = vouchers.map(voucher => {
      const pay = voucher.payValue ? `¥${formatMoney(voucher.payValue)}` : "到店优惠";
      const actual = voucher.actualValue ? `抵 ¥${formatMoney(voucher.actualValue)}` : "查看规则";
      const isSeckill = Number(voucher.type) === 1;
      const owned = state.wallet.has(String(voucher.id));
      const actionText = isSeckill ? "抢购" : (owned ? "已领取" : "领取");
      return `
        <article class="voucher-item">
          <div>
            <strong>${escapeHtml(voucher.title || "店铺优惠券")}</strong>
            <span>${escapeHtml(voucher.subTitle || voucher.rules || "到店前先看看优惠")}</span>
          </div>
          <button type="button" data-voucher-id="${voucher.id}" data-voucher-type="${voucher.type || 0}" ${owned && !isSeckill ? "disabled" : ""}>
            ${actionText}<small>${pay} · ${actual}</small>
          </button>
        </article>
      `;
    }).join("");
    els.voucherList.querySelectorAll("button").forEach(button => {
      button.addEventListener("click", () => handleVoucherAction(button));
    });
  }

  async function handleVoucherAction(button) {
    if (!requireLogin()) return;
    const voucherId = button.dataset.voucherId;
    const isSeckill = Number(button.dataset.voucherType) === 1;
    button.disabled = true;
    const previous = button.firstChild.textContent;
    button.firstChild.textContent = isSeckill ? "抢购中" : "领取中";
    try {
      if (isSeckill) {
        const orderId = await request(`/voucher-order/seckill/${voucherId}`, { method: "POST" });
        showStatus(`抢购成功，订单号：${orderId}`);
      } else {
        state.wallet.add(String(voucherId));
        localStorage.setItem("hmdp_wallet", JSON.stringify([...state.wallet]));
        button.firstChild.textContent = "已领取";
        showStatus("优惠券已放入本地卡包，后续可接入正式券包表。");
      }
    } catch (error) {
      button.disabled = false;
      button.firstChild.textContent = previous;
      showStatus(isSeckill ? "抢购失败，请确认库存和登录状态。" : "领取失败，请稍后再试。");
    }
  }

  // formatMoney is already defined in utils.js

  function renderDrawerImages(note) {
    if (note.isVideo && note.videoUrl) {
      els.drawerMedia.innerHTML = `
        <video class="drawer-video" src="${normalizeMedia(note.videoUrl)}" poster="${normalizeImage(note.image)}" controls autoplay playsinline preload="metadata"></video>
      `;
      els.drawerThumbs.innerHTML = "";
      return;
    }
    const images = (note.images.length ? note.images : [note.image]).slice(0, 9);
    let activeIndex = 0;
    const setActive = index => {
      activeIndex = (index + images.length) % images.length;
      els.drawerMedia.innerHTML = `
        <button class="carousel-nav carousel-prev" type="button" aria-label="上一张">‹</button>
        <img src="${normalizeImage(images[activeIndex])}" alt="${escapeHtml(note.title)}">
        <button class="carousel-nav carousel-next" type="button" aria-label="下一张">›</button>
        <span class="carousel-count">${activeIndex + 1}/${images.length}</span>
      `;
      els.drawerThumbs.querySelectorAll("button").forEach((button, buttonIndex) => {
        button.classList.toggle("is-active", buttonIndex === activeIndex);
      });
      els.drawerMedia.querySelector(".carousel-prev").addEventListener("click", () => setActive(activeIndex - 1));
      els.drawerMedia.querySelector(".carousel-next").addEventListener("click", () => setActive(activeIndex + 1));
    };
    els.drawerThumbs.innerHTML = images.map((src, index) => `
      <button type="button" class="${index === 0 ? "is-active" : ""}" data-index="${index}">
        <img src="${normalizeImage(src)}" alt="">
      </button>
    `).join("");
    els.drawerThumbs.querySelectorAll("button").forEach(button => {
      button.addEventListener("click", () => setActive(Number(button.dataset.index)));
    });
    setActive(0);
  }

  // ------------------------------
  // Like, collect, and follow interactions
  // ------------------------------
  async function likeNote(note) {
    if (!requireLogin()) return;
    try {
      await request(`/notes/${note.id}/like`, { method: "PUT" });
      trackEvent("like", { blogId: note.id, scene: "detail" });
      note.liked += note.isLike ? -1 : 1;
      note.isLike = !note.isLike;
      document.querySelector("#drawerLike").textContent = `♥ ${note.liked}`;
    } catch {
      showStatus("点赞失败，请稍后再试。");
    }
  }

  function toggleCollect(note) {
    if (!requireLogin()) return;
    const id = String(note.id);
    const next = !state.collected.has(id);
    request(`/notes/${note.id}/collect/${next}`, { method: "PUT" })
      .then(() => {
        if (next) state.collected.add(id);
        else state.collected.delete(id);
        trackEvent(next ? "collect" : "uncollect", { blogId: note.id, scene: "detail" });
        localStorage.setItem("hmdp_collected", JSON.stringify([...state.collected]));
        document.querySelector("#drawerCollect").textContent = next ? "★ 已收藏" : "☆ 收藏";
        var statCollects = document.querySelector("#statCollects");
        if (statCollects) statCollects.textContent = state.collected.size;
      })
      .catch(() => showStatus("收藏失败，请确认数据库已执行收藏表升级脚本。"));
  }

  async function loadCollectState(note) {
    if (!token()) return;
    try {
      const collected = await request(`/notes/${note.id}/collect`);
      const id = String(note.id);
      if (collected) state.collected.add(id);
      else state.collected.delete(id);
      localStorage.setItem("hmdp_collected", JSON.stringify([...state.collected]));
      document.querySelector("#drawerCollect").textContent = collected ? "★ 已收藏" : "☆ 收藏";
    } catch {
      // 收藏表未升级时保留本地状态，避免影响浏览主流程。
    }
  }

  async function toggleFollow(note) {
    if (!requireLogin()) return;
    const id = String(note.userId || "");
    const next = !state.followed.has(id);
    try {
      if (note.userId) await request(`/follow/${note.userId}/${next}`, { method: "PUT" });
      if (next) state.followed.add(id);
      else state.followed.delete(id);
      localStorage.setItem("hmdp_followed", JSON.stringify([...state.followed]));
      document.querySelector("#drawerFollow").textContent = next ? "已关注" : "关注";
    } catch {
      showStatus("关注失败，请稍后再试。");
    }
  }

  // Export cross-module functions
  window.openDrawer = openDrawer;
  window.renderCreatorGrowth = renderCreatorGrowth;
  window.likeNote = likeNote;
  window.toggleCollect = toggleCollect;
  window.loadCollectState = loadCollectState;
  window.toggleFollow = toggleFollow;
})();
