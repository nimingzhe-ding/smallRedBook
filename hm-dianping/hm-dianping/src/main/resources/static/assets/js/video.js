(function() {

function pauseFeedVideos() {
  document.querySelectorAll(".note-video-cover").forEach(video => {
    video.pause();
  });
}

function renderVideoFeed() {
  const videos = state.videoNotes;
  if (!videos.length) {
    els.videoFeed.innerHTML = `<p class="empty-text video-empty">还没有视频笔记，发布时上传视频就会出现在这里。</p>`;
    return;
  }
  els.videoFeed.innerHTML = videos.map(note => `
    <article class="video-slide" data-video-id="${note.id}">
      <video class="immersive-video" src="${normalizeMedia(note.videoUrl)}" poster="${normalizeImage(note.image)}" loop playsinline preload="metadata" ${state.videoMuted ? "muted" : ""}></video>
      <div class="danmaku-layer" data-danmaku-layer="${note.id}"></div>
      <div class="video-gradient"></div>
      <div class="video-info">
        <div class="video-author">
          <img src="${normalizeImage(note.icon)}" alt="">
          <span>${escapeHtml(note.name)}</span>
        </div>
        <h2>${escapeHtml(note.title)}</h2>
        <p>${escapeHtml(note.content || "")}</p>
      </div>
      <div class="video-actions">
        <button type="button" data-video-like="${note.id}"><span>♥</span><small>${note.liked || 0}</small></button>
        <button type="button" data-video-comment="${note.id}"><span>评</span><small>${note.comments || 0}</small></button>
        <button type="button" data-video-buy="${note.id}"><span>购</span><small>同款</small></button>
        <button type="button" data-video-report-note="${note.id}"><span>!</span><small>举报</small></button>
        <button type="button" data-video-open="${note.id}"><span>···</span><small>详情</small></button>
      </div>
      <form class="danmaku-form" data-danmaku-form="${note.id}">
        <input name="danmaku" maxlength="40" autocomplete="off" placeholder="发条弹幕...">
        <button type="submit">发送</button>
      </form>
      <div class="danmaku-controls">
        <button type="button" data-video-autoplay="${note.id}">${state.videoAutoplay ? "自动播开" : "自动播关"}</button>
        <button type="button" data-video-mute="${note.id}">${state.videoMuted ? "静音开" : "静音关"}</button>
        <button type="button" data-danmaku-toggle="${note.id}">${state.danmakuEnabled ? "弹幕开" : "弹幕关"}</button>
        <button type="button" data-danmaku-hot="${note.id}">${state.danmakuHotOnly ? "热门开" : "热门关"}</button>
        <label>速度
          <input type="range" min="5" max="13" step="1" value="${state.danmakuSpeed}" data-danmaku-speed="${note.id}">
        </label>
        <label>透明
          <input type="range" min="0.25" max="1" step="0.05" value="${state.danmakuOpacity}" data-danmaku-opacity="${note.id}">
        </label>
        <label class="danmaku-block-label">屏蔽
          <input type="text" maxlength="80" value="${escapeHtml(state.danmakuBlockWords)}" placeholder="词1,词2" data-danmaku-block="${note.id}">
        </label>
      </div>
    </article>
  `).join("");
  bindVideoFeedEvents(videos);
  videos.forEach(note => loadDanmaku(note.id));
}

function bindVideoFeedEvents(videos) {
  if (state.videoObserver) state.videoObserver.disconnect();
  state.videoObserver = new IntersectionObserver(entries => {
    entries.forEach(entry => {
      const video = entry.target.querySelector("video");
      if (!video) return;
      if (entry.isIntersecting && entry.intersectionRatio > 0.62) {
        pauseImmersiveVideos(video);
        subscribeDanmaku(entry.target.dataset.videoId);
        const savedState = loadVideoState(entry.target.dataset.videoId);
        if (state.videoAutoplay && !savedState.paused) {
          video.play().catch(() => {});
        }
        startDanmakuTicker(entry.target);
      } else {
        video.pause();
        stopDanmakuTicker(entry.target);
      }
    });
  }, { threshold: [0, 0.62, 1] });
  els.videoFeed.querySelectorAll(".video-slide").forEach(slide => state.videoObserver.observe(slide));
  els.videoFeed.querySelectorAll(".immersive-video").forEach(video => {
    bindVideoPlaybackState(video);
    video.addEventListener("click", () => {
      if (video.paused) video.play().catch(() => {});
      else video.pause();
    });
  });
  els.videoFeed.querySelectorAll("[data-video-open], [data-video-comment]").forEach(button => {
    button.addEventListener("click", () => {
      const note = videos.find(item => String(item.id) === String(button.dataset.videoOpen || button.dataset.videoComment));
      if (note) openDrawer(note);
    });
  });
  els.videoFeed.querySelectorAll("[data-video-like]").forEach(button => {
    button.addEventListener("click", () => {
      const note = videos.find(item => String(item.id) === String(button.dataset.videoLike));
      if (note) likeNote(note);
    });
  });
  els.videoFeed.querySelectorAll("[data-video-buy]").forEach(button => {
    button.addEventListener("click", () => {
      const note = videos.find(item => String(item.id) === String(button.dataset.videoBuy));
      if (note?.products?.length) {
        openProduct(note.products[0].id);
        return;
      }
      if (note?.shop) openDrawer(note);
      else switchMall();
    });
  });
  els.videoFeed.querySelectorAll("[data-video-report-note]").forEach(button => {
    button.addEventListener("click", () => {
      const note = videos.find(item => String(item.id) === String(button.dataset.videoReportNote));
      if (note) reportNote(note);
    });
  });
  els.videoFeed.querySelectorAll("[data-danmaku-form]").forEach(form => {
    form.addEventListener("submit", submitDanmaku);
  });
  els.videoFeed.querySelectorAll("[data-danmaku-toggle]").forEach(button => {
    button.addEventListener("click", () => {
      state.danmakuEnabled = !state.danmakuEnabled;
      localStorage.setItem("hmdp_danmaku_enabled", JSON.stringify(state.danmakuEnabled));
      document.querySelectorAll("[data-danmaku-toggle]").forEach(item => {
        item.textContent = state.danmakuEnabled ? "弹幕开" : "弹幕关";
      });
      document.querySelectorAll(".danmaku-layer").forEach(layer => {
        layer.hidden = !state.danmakuEnabled;
      });
    });
  });
  els.videoFeed.querySelectorAll("[data-video-autoplay]").forEach(button => {
    button.addEventListener("click", () => {
      state.videoAutoplay = !state.videoAutoplay;
      localStorage.setItem("hmdp_video_autoplay", JSON.stringify(state.videoAutoplay));
      document.querySelectorAll("[data-video-autoplay]").forEach(item => {
        item.textContent = state.videoAutoplay ? "自动播开" : "自动播关";
      });
    });
  });
  els.videoFeed.querySelectorAll("[data-video-mute]").forEach(button => {
    button.addEventListener("click", () => {
      state.videoMuted = !state.videoMuted;
      localStorage.setItem("hmdp_video_muted", JSON.stringify(state.videoMuted));
      document.querySelectorAll(".immersive-video").forEach(video => {
        video.muted = state.videoMuted;
        saveVideoState(video);
      });
      document.querySelectorAll("[data-video-mute]").forEach(item => {
        item.textContent = state.videoMuted ? "静音开" : "静音关";
      });
    });
  });
  els.videoFeed.querySelectorAll("[data-danmaku-speed]").forEach(input => {
    input.addEventListener("input", () => {
      state.danmakuSpeed = Number(input.value);
      localStorage.setItem("hmdp_danmaku_speed", String(state.danmakuSpeed));
    });
  });
  els.videoFeed.querySelectorAll("[data-danmaku-opacity]").forEach(input => {
    input.addEventListener("input", () => {
      state.danmakuOpacity = Number(input.value);
      localStorage.setItem("hmdp_danmaku_opacity", String(state.danmakuOpacity));
    });
  });
  els.videoFeed.querySelectorAll("[data-danmaku-block]").forEach(input => {
    input.addEventListener("change", () => {
      state.danmakuBlockWords = input.value.trim();
      localStorage.setItem("hmdp_danmaku_block_words", state.danmakuBlockWords);
      document.querySelectorAll("[data-danmaku-block]").forEach(item => {
        if (item !== input) item.value = state.danmakuBlockWords;
      });
    });
  });
  els.videoFeed.querySelectorAll("[data-danmaku-hot]").forEach(button => {
    button.addEventListener("click", () => {
      state.danmakuHotOnly = !state.danmakuHotOnly;
      localStorage.setItem("hmdp_danmaku_hot_only", JSON.stringify(state.danmakuHotOnly));
      document.querySelectorAll("[data-danmaku-hot]").forEach(item => {
        item.textContent = state.danmakuHotOnly ? "热门开" : "热门关";
      });
    });
  });
}

function defaultDanmaku(noteId) {
  return [
    "这个镜头好有感觉",
    "求路线",
    "收藏了，下次去",
    "这个同款想买",
    "氛围感拉满"
  ].map((content, index) => ({ content, videoSecond: index * 3, lane: index % 4 }));
}

async function loadDanmaku(noteId) {
  try {
    const data = await request(`/video-danmaku/public/${noteId}`);
    state.danmakuStore[String(noteId)] = dedupeDanmaku(Array.isArray(data) ? data : []);
  } catch {
    state.danmakuStore[String(noteId)] = defaultDanmaku(noteId);
  }
  const layer = els.videoFeed.querySelector(`[data-danmaku-layer="${noteId}"]`);
  if (layer) layer.hidden = !state.danmakuEnabled;
}

function subscribeDanmaku(noteId) {
  if (!noteId || state.danmakuSourceNoteId === String(noteId)) return;
  closeDanmakuSource();
  if (typeof EventSource === "undefined") return;
  const source = new EventSource(apiUrl(`/video-danmaku/stream/${noteId}`));
  state.danmakuSource = source;
  state.danmakuSourceNoteId = String(noteId);
  source.addEventListener("danmaku", event => {
    try {
      receiveRealtimeDanmaku(noteId, JSON.parse(event.data));
    } catch {
      // Ignore malformed events; HTTP history remains the source of truth.
    }
  });
  source.onerror = () => {
    closeDanmakuSource();
  };
}

function closeDanmakuSource() {
  if (state.danmakuSource) {
    state.danmakuSource.close();
  }
  state.danmakuSource = null;
  state.danmakuSourceNoteId = null;
}

function receiveRealtimeDanmaku(noteId, item) {
  if (!item || item.id == null) return;
  const key = String(noteId);
  const list = state.danmakuStore[key] || [];
  if (list.some(existing => String(existing.id) === String(item.id))) {
    return;
  }
  list.push(item);
  state.danmakuStore[key] = dedupeDanmaku(list);
  const slide = els.videoFeed.querySelector(`.video-slide[data-video-id="${noteId}"]`);
  const video = slide?.querySelector("video");
  const currentSecond = Math.floor(video?.currentTime || 0);
  if (Math.abs(Number(item.videoSecond || 0) - currentSecond) <= 1) {
    const layer = els.videoFeed.querySelector(`[data-danmaku-layer="${noteId}"]`);
    if (layer) shootDanmaku(layer, item.content || "", item.lane, item.id);
    item.__shownAtSecond = Number(item.videoSecond || 0);
  }
}

function dedupeDanmaku(list) {
  const seen = new Set();
  return list.filter(item => {
    const id = item && item.id != null ? String(item.id) : `${item.content}:${item.videoSecond}:${item.lane}`;
    if (seen.has(id)) return false;
    seen.add(id);
    return true;
  });
}

function renderDanmaku(noteId, currentSecond) {
  const layer = els.videoFeed.querySelector(`[data-danmaku-layer="${noteId}"]`);
  if (!layer || !state.danmakuEnabled) return;
  const list = filterDanmakuList(noteId, state.danmakuStore[String(noteId)] || []);
  list
    .filter(item => Number(item.videoSecond || 0) === currentSecond && !item.__shownAtSecond)
    .slice(0, 8)
    .forEach((item, index) => {
      item.__shownAtSecond = currentSecond;
      shootDanmaku(layer, item.content || item.text || item, item.lane ?? index % 5, item.id);
    });
}

function shootDanmaku(layer, text, lane, danmakuId) {
  const item = document.createElement("span");
  item.className = "danmaku-item";
  item.textContent = text;
  if (danmakuId != null) {
    item.dataset.danmakuId = String(danmakuId);
    item.title = "双击举报弹幕";
    item.addEventListener("dblclick", () => reportDanmaku(danmakuId), { once: true });
  }
  const laneNumber = Number(lane);
  const track = Number.isInteger(laneNumber) ? ((laneNumber % 5) + 5) % 5 : Math.floor(Math.random() * 5);
  item.style.setProperty("--lane", String(track));
  item.style.setProperty("--duration", `${state.danmakuSpeed}s`);
  item.style.opacity = String(state.danmakuOpacity);
  layer.appendChild(item);
  item.addEventListener("animationend", () => item.remove(), { once: true });
}

function filterDanmakuList(noteId, list) {
  const words = state.danmakuBlockWords
    .split(/[,，\s]+/)
    .map(word => word.trim())
    .filter(Boolean);
  let result = list.filter(item => {
    const text = String(item.content || item.text || item || "");
    return !words.some(word => text.includes(word));
  });
  if (!state.danmakuHotOnly) return result;
  const countMap = result.reduce((map, item) => {
    const text = String(item.content || item.text || item || "");
    map.set(text, (map.get(text) || 0) + 1);
    return map;
  }, new Map());
  const hot = result.filter(item => {
    const text = String(item.content || item.text || item || "");
    return Number(item.liked || 0) > 0 || countMap.get(text) > 1;
  });
  return hot.length ? hot : result.slice(0, Math.max(1, Math.ceil(result.length / 2)));
}

function videoStateKey(noteId) {
  return `hmdp_video_state_${noteId}`;
}

function loadVideoState(noteId) {
  try {
    return JSON.parse(localStorage.getItem(videoStateKey(noteId)) || "{}");
  } catch {
    return {};
  }
}

function saveVideoState(video) {
  const slide = video.closest(".video-slide");
  const noteId = slide?.dataset.videoId;
  if (!noteId) return;
  localStorage.setItem(videoStateKey(noteId), JSON.stringify({
    currentTime: Math.floor(video.currentTime || 0),
    paused: video.paused,
    muted: video.muted,
    updatedAt: Date.now()
  }));
}

function bindVideoPlaybackState(video) {
  const slide = video.closest(".video-slide");
  const noteId = slide?.dataset.videoId;
  if (!noteId) return;
  const saved = loadVideoState(noteId);
  video.muted = saved.muted ?? state.videoMuted;
  video.addEventListener("loadedmetadata", () => {
    const savedTime = Number(saved.currentTime || 0);
    if (savedTime > 0 && Number.isFinite(video.duration) && savedTime < video.duration - 1) {
      video.currentTime = savedTime;
    }
  }, { once: true });
  video.addEventListener("timeupdate", () => {
    const now = Date.now();
    if (now - Number(video.dataset.lastSavedAt || 0) < 1200) return;
    video.dataset.lastSavedAt = String(now);
    saveVideoState(video);
  });
  video.addEventListener("play", () => saveVideoState(video));
  video.addEventListener("pause", () => {
    saveVideoState(video);
    reportVideoMetric(video);
  });
  video.addEventListener("volumechange", () => {
    state.videoMuted = video.muted;
    localStorage.setItem("hmdp_video_muted", JSON.stringify(state.videoMuted));
    saveVideoState(video);
  });
  video.addEventListener("ended", () => {
    saveVideoState(video);
    reportVideoMetric(video, true);
  });
}

function reportVideoMetric(video, completed = false) {
  if (!token()) return;
  const slide = video.closest(".video-slide");
  const noteId = slide?.dataset.videoId;
  if (!noteId) return;
  const duration = Math.floor(video.duration || 0);
  const watched = Math.floor(video.currentTime || 0);
  if (duration <= 0 || watched <= 1) return;
  const now = Date.now();
  if (!completed && now - Number(video.dataset.lastMetricAt || 0) < 8000) return;
  video.dataset.lastMetricAt = String(now);
  request("/video/metrics/play", {
    method: "POST",
    body: JSON.stringify({
      blogId: Number(noteId),
      durationSecond: duration,
      watchedSecond: watched,
      maxProgress: Math.min(100, Math.floor((watched / duration) * 100)),
      completed: completed || watched / duration >= 0.95
    })
  }).catch(() => {});
  trackEvent("play", { noteId: Number(noteId), scene: "video" });
}

async function submitDanmaku(event) {
  event.preventDefault();
  if (!requireLogin()) return;
  const form = event.currentTarget;
  const noteId = String(form.dataset.danmakuForm);
  const input = form.elements.danmaku;
  const text = input.value.trim();
  if (!text) return;
  const slide = form.closest(".video-slide");
  const video = slide?.querySelector("video");
  const currentSecond = Math.max(0, Math.floor(video?.currentTime || 0));
  const payload = {
    blogId: Number(noteId),
    content: text,
    videoSecond: currentSecond,
    lane: Math.floor(Math.random() * 5),
  };
  try {
    const saved = await request("/video-danmaku", {
      method: "POST",
      body: JSON.stringify(payload)
    });
    const list = state.danmakuStore[noteId] || [];
    list.push(saved || payload);
    state.danmakuStore[noteId] = list;
    input.value = "";
    const layer = els.videoFeed.querySelector(`[data-danmaku-layer="${noteId}"]`);
    if (layer) shootDanmaku(layer, text, payload.lane, saved?.id);
  } catch (error) {
    showStatus(error.message || "弹幕发送失败。");
  }
}

async function reportDanmaku(danmakuId) {
  if (!danmakuId || !requireLogin()) return;
  try {
    await request(`/video-danmaku/${danmakuId}/report`, { method: "PUT" });
    Object.keys(state.danmakuStore).forEach(key => {
      state.danmakuStore[key] = (state.danmakuStore[key] || [])
        .filter(item => String(item.id) !== String(danmakuId));
    });
    document.querySelectorAll(`[data-danmaku-id="${danmakuId}"]`).forEach(item => item.remove());
    showStatus("已提交弹幕举报。");
  } catch (error) {
    showStatus(error.message || "弹幕举报失败。");
  }
}

function startDanmakuTicker(slide) {
  if (slide.dataset.danmakuTimer) return;
  const timer = window.setInterval(() => {
    const video = slide.querySelector("video");
    if (!video || video.paused) return;
    renderDanmaku(slide.dataset.videoId, Math.floor(video.currentTime || 0));
  }, 500);
  slide.dataset.danmakuTimer = String(timer);
}

function stopDanmakuTicker(slide) {
  if (!slide.dataset.danmakuTimer) return;
  window.clearInterval(Number(slide.dataset.danmakuTimer));
  delete slide.dataset.danmakuTimer;
}

function playCurrentImmersiveVideo() {
  if (!state.videoAutoplay) return;
  const first = els.videoFeed.querySelector(".immersive-video");
  const savedState = loadVideoState(first?.closest(".video-slide")?.dataset.videoId);
  if (first && !savedState.paused) first.play().catch(() => {});
}

function pauseImmersiveVideos(except) {
  document.querySelectorAll(".immersive-video").forEach(video => {
    if (video !== except) video.pause();
  });
  document.querySelectorAll(".video-slide").forEach(slide => {
    if (slide.querySelector("video") !== except) stopDanmakuTicker(slide);
  });
}

function closeDrawer() {
  els.drawer.classList.remove("is-open");
  els.drawer.setAttribute("aria-hidden", "true");
  document.body.style.overflow = "";
  state.replyTarget = null;
  els.commentInput.placeholder = "说点什么...";
}

// Export cross-module functions
window.pauseFeedVideos = pauseFeedVideos;
window.renderVideoFeed = renderVideoFeed;
window.bindVideoFeedEvents = bindVideoFeedEvents;
window.defaultDanmaku = defaultDanmaku;
window.loadDanmaku = loadDanmaku;
window.subscribeDanmaku = subscribeDanmaku;
window.closeDanmakuSource = closeDanmakuSource;
window.receiveRealtimeDanmaku = receiveRealtimeDanmaku;
window.dedupeDanmaku = dedupeDanmaku;
window.renderDanmaku = renderDanmaku;
window.shootDanmaku = shootDanmaku;
window.reportDanmaku = reportDanmaku;
window.filterDanmakuList = filterDanmakuList;
window.videoStateKey = videoStateKey;
window.loadVideoState = loadVideoState;
window.saveVideoState = saveVideoState;
window.bindVideoPlaybackState = bindVideoPlaybackState;
window.reportVideoMetric = reportVideoMetric;
window.submitDanmaku = submitDanmaku;
window.startDanmakuTicker = startDanmakuTicker;
window.stopDanmakuTicker = stopDanmakuTicker;
window.playCurrentImmersiveVideo = playCurrentImmersiveVideo;
window.pauseImmersiveVideos = pauseImmersiveVideos;
window.closeDrawer = closeDrawer;

})();
