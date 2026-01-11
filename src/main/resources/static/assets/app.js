(() => {
  const defaultDocQuery = {
    q: "",
    authorId: "",
    tagId: "",
    timeRange: "",
    folderId: null,
    sortBy: "updatedAt",
    order: "desc"
  };

  const state = {
    baseUrl: localStorage.getItem("docflow_baseUrl") || window.location.origin,
    token: localStorage.getItem("docflow_token") || "",
    user: null,
    docs: [],
    folders: [],
    tags: [],
    adminUsers: [],
    templates: [],
    notifications: [],
    tasks: [],
    meetings: [],
    activeDoc: null,
    activeRole: null,
    activeMeeting: null,
    meetingJoined: false,
    activeDocTags: [],
    docQuery: { ...defaultDocQuery },
    taskFilter: {
      status: ""
    },
    adminUserFilter: {
      q: "",
      role: "",
      status: ""
    },
    ws: null,
    wsConnected: false,
    wsUrl: "",
    wsPingTimer: null,
    wsReconnectTimer: null,
    wsBackoffMs: 2000,
    wsShouldReconnect: true,
    presenceDocId: null,
    unreadCount: 0,
    jitsiApi: null,
    jitsiDomain: "",
    screenSharing: false
  };

  const demo = {
    docs: [
      { id: 101, title: "项目概要", folderId: 2, format: "RICH_TEXT", updatedAt: "2026-01-05T09:30:00" },
      { id: 102, title: "迭代计划", folderId: 3, format: "MARKDOWN", updatedAt: "2026-01-04T14:22:00" },
      { id: 103, title: "发布说明", folderId: null, format: "PLAIN", updatedAt: "2026-01-03T18:05:00" }
    ],
    templates: [
      { id: 201, title: "周报模板", description: "每周复盘模板", format: "RICH_TEXT", updatedAt: "2026-01-02T09:00:00" },
      { id: 202, title: "会议纪要", description: "结构化会议记录", format: "MARKDOWN", updatedAt: "2026-01-01T11:10:00" }
    ],
    notifications: [
      { id: 301, type: "TASK_ASSIGNED", payload: "你被分配了任务 #8", read: false, createdAt: "2026-01-05T08:00:00" },
      { id: 302, type: "COMMENT", payload: "《项目概要》有新评论", read: true, createdAt: "2026-01-04T16:30:00" }
    ],
    tasks: [
      { id: 401, title: "更新引言部分", status: "IN_PROGRESS", dueAt: "2026-01-08T12:00:00" },
      { id: 402, title: "补充截图", status: "TODO", dueAt: "2026-01-10T09:00:00" }
    ],
    members: [
      { userId: 1, nickname: "用户A", role: "OWNER" },
      { userId: 2, nickname: "用户B", role: "EDITOR" }
    ],
    comments: [
      { id: 501, authorId: 1, content: "请补充目标说明。", createdAt: "2026-01-04T10:30:00" }
    ],
    chat: [
      { id: 601, senderId: 1, content: "准备好了提醒我。", createdAt: "2026-01-04T10:40:00" }
    ],
    files: [
      { id: 701, originalName: "需求说明.pdf", downloadUrl: "/files/701", sizeBytes: 120000 }
    ],
    meetings: [
      { id: 801, title: "启动会", status: "ACTIVE", joinUrl: "https://meet.example.com/abc" }
    ],
    audit: [
      { id: 901, action: "文档保存", createdAt: "2026-01-04T12:20:00" }
    ],
    adminUsers: [
      { id: 1, nickname: "管理员", systemRole: "ADMIN", status: "ACTIVE" },
      { id: 2, nickname: "用户B", systemRole: "USER", status: "ACTIVE" }
    ],
    surveyStats: { averageRating: 4.4, counts: [{ rating: 5, count: 12 }, { rating: 4, count: 6 }, { rating: 3, count: 2 }] },
    surveys: [
      { id: 1, userId: 1, rating: 5, comment: "体验很棒", createdAt: "2026-01-03T10:00:00" }
    ]
  };

  const formatMap = {
    RICH_TEXT: "富文本",
    MARKDOWN: "Markdown",
    PLAIN: "纯文本"
  };
  const roleMap = {
    OWNER: "拥有者",
    EDITOR: "编辑者",
    VIEWER: "查看者"
  };
  const taskStatusMap = {
    TODO: "待办",
    IN_PROGRESS: "进行中",
    DONE: "已完成",
    CANCELED: "已取消"
  };
  const commentStatusMap = {
    OPEN: "开放",
    RESOLVED: "已解决",
    DELETED: "已删除"
  };
  const meetingStatusMap = {
    ACTIVE: "进行中",
    ENDED: "已结束",
    CANCELED: "已取消"
  };
  const systemRoleMap = {
    ADMIN: "管理员",
    USER: "普通用户"
  };
  const userStatusMap = {
    ACTIVE: "启用",
    FROZEN: "冻结",
    BANNED: "封禁"
  };
  const notificationMap = {
    SHARE: "共享提醒",
    COMMENT_REPLY: "回复通知",
    MENTION: "@提醒",
    TASK_ASSIGNED: "任务分配",
    TASK_COMPLETED: "任务完成",
    DOC_EDIT: "文档更新",
    COMMENT: "评论提醒",
    COMMENT_STATUS: "批注状态"
  };
  const actionMap = {
    doc_save: "文档保存",
    login: "登录",
    register: "注册",
    reset_password: "重置密码"
  };

  function formatLabel(value) {
    return formatMap[value] || value || "-";
  }

  function roleLabel(value) {
    return roleMap[value] || value || "-";
  }

  function taskStatusLabel(value) {
    return taskStatusMap[value] || value || "-";
  }

  function commentStatusLabel(value) {
    return commentStatusMap[value] || value || "-";
  }

  function meetingStatusLabel(value) {
    return meetingStatusMap[value] || value || "-";
  }

  function systemRoleLabel(value) {
    return systemRoleMap[value] || value || "-";
  }

  function userStatusLabel(value) {
    return userStatusMap[value] || value || "-";
  }

  function notificationLabel(value) {
    return notificationMap[value] || value || "-";
  }

  function actionLabel(value) {
    return actionMap[value] || value || "-";
  }

  const viewMeta = {
    docs: { title: "文档", sub: "工作区概览与动态" },
    editor: { title: "编辑器", sub: "编辑、协作与同步" },
    templates: { title: "模板库", sub: "快速复用常用模板" },
    notifications: { title: "通知中心", sub: "查看编辑、评论与任务提醒" },
    settings: { title: "通知设置", sub: "管理通知偏好" },
    tasks: { title: "我的任务", sub: "需要处理的工作清单" },
    profile: { title: "个人资料", sub: "管理账号与头像" },
    audit: { title: "我的日志", sub: "最近操作记录" },
    survey: { title: "满意度", sub: "提交反馈" },
    "admin-users": { title: "用户管理", sub: "账号、角色与状态" },
    "admin-behavior": { title: "用户行为", sub: "活跃度与统计" },
    "admin-audit": { title: "审计日志", sub: "用户操作轨迹" },
    "admin-surveys": { title: "满意度统计", sub: "反馈与评分概览" },
    login: { title: "登录", sub: "进入你的工作区" },
    register: { title: "注册", sub: "创建新账号" },
    forgot: { title: "找回密码", sub: "获取重置令牌" },
    reset: { title: "重置密码", sub: "设置新密码" }
  };

  const el = {
    status: document.getElementById("status-banner"),
    pageTitle: document.getElementById("page-title"),
    pageSub: document.getElementById("page-sub"),
    unreadBadge: document.getElementById("unread-badge"),
    primaryCta: document.getElementById("primary-cta"),
    authStatus: document.getElementById("auth-status"),
    logout: document.getElementById("logout-btn"),
    baseUrl: document.getElementById("base-url"),
    tokenPreview: document.getElementById("token-preview"),
    toastStack: document.getElementById("toast-stack"),
    docList: document.getElementById("doc-list"),
    folderList: document.getElementById("folder-list"),
    tagList: document.getElementById("tag-list"),
    searchAuthor: document.getElementById("search-author"),
    searchTag: document.getElementById("search-tag"),
    searchTime: document.getElementById("search-time"),
    filterIndicator: document.getElementById("filter-indicator"),
    createFolder: document.getElementById("create-folder"),
    createTags: document.getElementById("create-tags"),
    templateList: document.getElementById("template-list"),
    notificationList: document.getElementById("notification-list"),
    notificationType: document.getElementById("notification-type"),
    assignedList: document.getElementById("assigned-list"),
    memberList: document.getElementById("member-list"),
    presenceList: document.getElementById("presence-list"),
    commentList: document.getElementById("comment-list"),
    taskList: document.getElementById("task-list"),
    cursorList: document.getElementById("cursor-list"),
    chatList: document.getElementById("chat-list"),
    fileList: document.getElementById("file-list"),
    meetingList: document.getElementById("meeting-list"),
    meetingEmbed: document.getElementById("meeting-embed"),
    meetingEmbedTip: document.getElementById("meeting-embed-tip"),
    meetingJoin: document.getElementById("meeting-join"),
    meetingOpenWindow: document.getElementById("meeting-open-window"),
    meetingLeave: document.getElementById("meeting-leave"),
    meetingEmbedCard: document.getElementById("meeting-embed-card"),
    screenShareCard: document.getElementById("screen-share-card"),
    activeMeetingInfo: document.getElementById("active-meeting-info"),
    screenStatus: document.getElementById("screen-status"),
    auditList: document.getElementById("audit-list"),
    docAuditList: document.getElementById("doc-audit-list"),
    adminUserList: document.getElementById("admin-user-list"),
    behaviorResult: document.getElementById("behavior-result"),
    adminAuditList: document.getElementById("admin-audit-list"),
    surveyStats: document.getElementById("survey-stats"),
    surveyList: document.getElementById("survey-list"),
    versionList: document.getElementById("version-list"),
    versionDetailMeta: document.getElementById("version-detail-meta"),
    versionDetailContent: document.getElementById("version-detail-content"),
    versionFilter: document.getElementById("version-filter"),
    docTagList: document.getElementById("doc-tag-list"),
    docTagSelect: document.getElementById("doc-tag-select"),
    typeSettings: document.getElementById("type-settings"),
    muteAll: document.getElementById("mute-all"),
    editorTitle: document.getElementById("editor-title"),
    editorMeta: document.getElementById("editor-meta"),
    docContent: document.getElementById("doc-content"),
    richToolbar: document.getElementById("rich-toolbar"),
    richEditor: document.getElementById("doc-rich-editor"),
    docFormat: document.getElementById("doc-format"),
    docVersion: document.getElementById("doc-version"),
    docRename: document.getElementById("doc-rename"),
    forgotResult: document.getElementById("forgot-result"),
    avatarPreview: document.getElementById("avatar-preview"),
    templateSelect: document.getElementById("create-template"),
    statDocs: document.getElementById("stat-docs"),
    statTemplates: document.getElementById("stat-templates"),
    statTasks: document.getElementById("stat-tasks"),
    statNotifications: document.getElementById("stat-notifications")
  };

  function setStatus(message, type = "ok") {
    if (!message) {
      el.status.className = "status";
      el.status.textContent = "";
      return;
    }
    el.status.textContent = message;
    el.status.className = `status show ${type}`.trim();
  }

  function toast(message, type = "info") {
    const node = document.createElement("div");
    node.className = `toast ${type}`.trim();
    node.textContent = message;
    el.toastStack.appendChild(node);
    setTimeout(() => node.remove(), 3200);
  }

  function escapeHtml(input) {
    return String(input || "")
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;");
  }

  function resolveAssetUrl(path) {
    if (!path) {
      return "";
    }
    if (/^https?:\/\//i.test(path)) {
      return path;
    }
    const base = state.baseUrl.replace(/\/$/, "");
    const normalized = path.startsWith("/") ? path : `/${path}`;
    return `${base}${normalized}`;
  }

  function syncProfileForm(user) {
    if (!user) {
      return;
    }
    const form = document.getElementById("profile-form");
    if (form) {
      form.nickname.value = user.nickname || "";
      form.email.value = user.email || "";
      form.phone.value = user.phone || "";
    }
    if (el.avatarPreview) {
      const avatarUrl = resolveAssetUrl(user.avatarUrl);
      el.avatarPreview.style.backgroundImage = avatarUrl ? `url(${avatarUrl})` : "none";
    }
  }

  function formatUserLabel(userId, nickname) {
    const fallbackName = state.user && state.user.id === userId ? state.user.nickname : "";
    const label = nickname || fallbackName || `用户 #${userId}`;
    return escapeHtml(label);
  }

  function parseIdList(value) {
    return String(value || "")
      .split(",")
      .map(item => item.trim())
      .filter(Boolean)
      .map(Number)
      .filter(Number.isFinite);
  }

  function formatLocalDateTime(date) {
    const pad = value => String(value).padStart(2, "0");
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
  }

  function resolveTimeRange(range) {
    if (!range) {
      return { from: "", to: "" };
    }
    const now = new Date();
    let fromDate = null;
    if (range === "today") {
      fromDate = new Date(now.getFullYear(), now.getMonth(), now.getDate());
    } else if (range === "7d") {
      fromDate = new Date(now);
      fromDate.setDate(now.getDate() - 7);
    } else if (range === "30d") {
      fromDate = new Date(now);
      fromDate.setDate(now.getDate() - 30);
    } else if (range === "90d") {
      fromDate = new Date(now);
      fromDate.setDate(now.getDate() - 90);
    }
    if (!fromDate) {
      return { from: "", to: "" };
    }
    return { from: formatLocalDateTime(fromDate), to: formatLocalDateTime(now) };
  }

  let autosaveTimer = null;
  let autosaveInFlight = false;
  let editorDirty = false;
  let lastFormat = "RICH_TEXT";
  let docEditTimer = null;
  let cursorTimer = null;
  let docSearchTimer = null;
  let liveEditWarnAt = 0;

  function getEditorContent() {
    if (el.docFormat && el.docFormat.value === "RICH_TEXT" && el.richEditor) {
      return el.richEditor.innerHTML.trim();
    }
    return el.docContent ? el.docContent.value : "";
  }

  function setEditorContent(value) {
    if (el.docContent) {
      el.docContent.value = value || "";
    }
    if (el.richEditor) {
      el.richEditor.innerHTML = value || "";
    }
  }

  function setEditorMode(format) {
    if (!el.docContent || !el.richEditor || !el.richToolbar) {
      return;
    }
    const rich = format === "RICH_TEXT";
    el.richEditor.style.display = rich ? "block" : "none";
    el.richToolbar.style.display = rich ? "flex" : "none";
    el.docContent.style.display = rich ? "none" : "block";
  }

  function markEditorDirty() {
    editorDirty = true;
  }

  function handleEditorInput() {
    markEditorDirty();
    scheduleDocEditBroadcast();
  }

  function scheduleDocEditBroadcast() {
    if (!state.activeDoc || state.activeRole === "VIEWER") {
      return;
    }
    if (!state.wsConnected) {
      return;
    }
    if (docEditTimer) {
      return;
    }
    docEditTimer = setTimeout(() => {
      docEditTimer = null;
      sendWs("doc.edit", {
        docId: state.activeDoc.id,
        content: getEditorContent(),
        format: el.docFormat ? el.docFormat.value : "RICH_TEXT",
        baseVersion: Number(el.docVersion.value || state.activeDoc.version || 1)
      });
    }, 800);
  }

  function applyLiveEdit(payload) {
    if (!payload || payload.content === undefined || payload.content === null) {
      return;
    }
    const nextFormat = payload.format || state.activeDoc?.format || "RICH_TEXT";
    if (el.docFormat && el.docFormat.value !== nextFormat) {
      el.docFormat.value = nextFormat;
      lastFormat = nextFormat;
      setEditorMode(nextFormat);
    }
    if (nextFormat === "RICH_TEXT") {
      if (el.richEditor) {
        el.richEditor.innerHTML = String(payload.content);
      }
    } else if (el.docContent) {
      el.docContent.value = String(payload.content);
    }
    if (state.activeDoc) {
      state.activeDoc = {
        ...state.activeDoc,
        content: String(payload.content),
        format: nextFormat
      };
    }
  }

  function scheduleCursorBroadcast() {
    if (cursorTimer) {
      return;
    }
    cursorTimer = setTimeout(() => {
      cursorTimer = null;
      broadcastCursorPosition();
    }, 300);
  }

  function computeLineColumn(text, offset) {
    const safeText = String(text || "");
    const safeOffset = Math.max(0, Math.min(offset || 0, safeText.length));
    const before = safeText.slice(0, safeOffset);
    const lines = before.split("\n");
    const line = lines.length;
    const column = lines[lines.length - 1].length + 1;
    return { line, column };
  }

  function getPlainCursorPosition() {
    if (!el.docContent) {
      return null;
    }
    const pos = el.docContent.selectionStart ?? 0;
    return computeLineColumn(el.docContent.value, pos);
  }

  function getRichCursorPosition() {
    if (!el.richEditor) {
      return null;
    }
    const selection = window.getSelection();
    if (!selection || selection.rangeCount === 0) {
      return null;
    }
    const range = selection.getRangeAt(0);
    const preRange = range.cloneRange();
    preRange.selectNodeContents(el.richEditor);
    preRange.setEnd(range.endContainer, range.endOffset);
    const text = preRange.toString();
    return computeLineColumn(text, text.length);
  }

  function broadcastCursorPosition() {
    if (!state.activeDoc || !state.wsConnected || state.activeRole === "VIEWER") {
      return;
    }
    const format = el.docFormat ? el.docFormat.value : "RICH_TEXT";
    const pos = format === "RICH_TEXT" ? getRichCursorPosition() : getPlainCursorPosition();
    if (!pos) {
      return;
    }
    sendWs("cursor.update", {
      docId: state.activeDoc.id,
      line: pos.line,
      column: pos.column
    });
  }

  function startAutosave() {
    if (autosaveTimer) {
      clearInterval(autosaveTimer);
    }
    autosaveTimer = setInterval(async () => {
      if (!state.activeDoc || !editorDirty || autosaveInFlight) {
        return;
      }
      if (state.activeRole === "VIEWER") {
        return;
      }
      autosaveInFlight = true;
      await saveDoc(true);
      autosaveInFlight = false;
    }, 15);
  }

  function setView(view) {
    if (!state.token && !["login", "register", "forgot", "reset"].includes(view)) {
      view = "login";
    }
    const previousView = document.body.dataset.view;
    if (previousView === "editor" && view !== "editor") {
      leaveDocPresence();
      closeMeetingEmbed();
    }
    document.querySelectorAll(".view").forEach(section => {
      section.classList.toggle("active", section.dataset.view === view);
    });
    document.querySelectorAll(".nav-item").forEach(button => {
      button.classList.toggle("active", button.dataset.viewTarget === view);
    });
    document.body.dataset.view = view;
    const meta = viewMeta[view] || viewMeta.docs;
    el.pageTitle.textContent = meta.title;
    el.pageSub.textContent = meta.sub;
    if (view === "editor" && state.activeDoc && state.activeDoc.id) {
      joinDocPresence(state.activeDoc.id);
    }
  }

  function updateAuthUI() {
    document.body.classList.toggle("unauth", !state.token);
    if (state.token) {
      el.authStatus.textContent = "已登录";
      el.tokenPreview.textContent = state.token.slice(0, 12) + "...";
      el.logout.disabled = false;
    } else {
      el.authStatus.textContent = "未登录";
      el.tokenPreview.textContent = "-";
      el.logout.disabled = true;
    }
  }

  function updateAdminVisibility() {
    const isAdmin = state.user && state.user.systemRole === "ADMIN";
    document.querySelectorAll("[data-admin-only]").forEach(elm => {
      elm.style.display = isAdmin ? "" : "none";
    });
  }

  function updateStats() {
    el.statDocs.textContent = state.docs.length;
    el.statTemplates.textContent = state.templates.length;
    el.statTasks.textContent = state.tasks.length;
    el.statNotifications.textContent = state.notifications.length;
  }

  async function api(path, options = {}) {
    const url = state.baseUrl.replace(/\/$/, "") + path;
    const headers = options.headers ? { ...options.headers } : {};
    if (state.token) {
      headers.Authorization = `Bearer ${state.token}`;
    }
    const isForm = options.body instanceof FormData;
    if (options.body && !isForm) {
      headers["Content-Type"] = headers["Content-Type"] || "application/json";
    }
    const response = await fetch(url, {
      ...options,
      headers
    });
    const text = await response.text();
    const data = text ? safeJson(text) : null;
    if (!response.ok) {
      const error = { status: response.status, data };
      throw error;
    }
    return data;
  }

  function safeJson(text) {
    try {
      return JSON.parse(text);
    } catch (err) {
      return text;
    }
  }

  function handleError(err) {
    if (!err || !err.status) {
      toast("出现未知错误", "error");
      return;
    }
    if (err.status === 401) {
      toast("请先登录", "warn");
      setView("login");
      return;
    }
    if (err.status === 403) {
      toast("没有访问权限", "warn");
      return;
    }
    if (err.status === 409) {
      toast("版本冲突，请刷新后重试", "warn");
      return;
    }
    toast(`请求失败（${err.status}）`, "error");
  }

  function buildWsUrl() {
    const base = state.baseUrl.replace(/\/$/, "");
    const wsBase = base.replace(/^https:/, "wss:").replace(/^http:/, "ws:");
    return `${wsBase}/ws?token=${encodeURIComponent(state.token)}`;
  }

  function sendWs(type, data = null) {
    if (!state.ws || state.ws.readyState !== WebSocket.OPEN) {
      return;
    }
    state.ws.send(JSON.stringify({ type, data }));
  }

  function startWsPing() {
    if (state.wsPingTimer) {
      clearInterval(state.wsPingTimer);
    }
    state.wsPingTimer = setInterval(() => {
      sendWs("presence.ping");
    }, 10000);
  }

  function stopWsPing() {
    if (state.wsPingTimer) {
      clearInterval(state.wsPingTimer);
      state.wsPingTimer = null;
    }
  }

  function joinDocPresence(docId) {
    if (!docId || !state.wsConnected) {
      return;
    }
    if (state.presenceDocId && state.presenceDocId !== docId) {
      sendWs("presence.leave");
    }
    sendWs("presence.join", { docId });
    state.presenceDocId = docId;
  }

  function leaveDocPresence() {
    if (!state.wsConnected || !state.presenceDocId) {
      return;
    }
    sendWs("presence.leave");
    state.presenceDocId = null;
    if (el.presenceList) {
      el.presenceList.innerHTML = "<div class=\"meta\">暂无在线成员</div>";
    }
    if (el.cursorList) {
      el.cursorList.innerHTML = "<div class=\"meta\">暂无光标</div>";
    }
  }

  function scheduleWsReconnect() {
    if (!state.wsShouldReconnect || !state.token) {
      return;
    }
    if (state.wsReconnectTimer) {
      return;
    }
    state.wsReconnectTimer = setTimeout(() => {
      state.wsReconnectTimer = null;
      connectWs();
    }, state.wsBackoffMs);
    state.wsBackoffMs = Math.min(state.wsBackoffMs * 2, 30000);
  }

  function closeWs(manual = false) {
    state.wsShouldReconnect = !manual;
    stopWsPing();
    if (state.wsReconnectTimer) {
      clearTimeout(state.wsReconnectTimer);
      state.wsReconnectTimer = null;
    }
    if (state.ws) {
      state.ws.onopen = null;
      state.ws.onmessage = null;
      state.ws.onclose = null;
      state.ws.onerror = null;
      if (state.ws.readyState === WebSocket.OPEN || state.ws.readyState === WebSocket.CONNECTING) {
        state.ws.close();
      }
    }
    state.ws = null;
    state.wsConnected = false;
    state.wsUrl = "";
    state.presenceDocId = null;
  }

  function connectWs() {
    if (!state.token) {
      return;
    }
    const url = buildWsUrl();
    if (state.ws && state.wsUrl === url &&
        (state.ws.readyState === WebSocket.OPEN || state.ws.readyState === WebSocket.CONNECTING)) {
      return;
    }
    closeWs(true);
    state.wsShouldReconnect = true;
    try {
      state.ws = new WebSocket(url);
    } catch (err) {
      scheduleWsReconnect();
      return;
    }
    state.wsUrl = url;
    state.ws.onopen = () => {
      state.wsConnected = true;
      state.wsBackoffMs = 2000;
      startWsPing();
      if (state.activeDoc && state.activeDoc.id) {
        joinDocPresence(state.activeDoc.id);
      }
    };
    state.ws.onmessage = event => {
      handleWsMessage(event.data);
    };
    state.ws.onclose = () => {
      state.wsConnected = false;
      stopWsPing();
      scheduleWsReconnect();
    };
    state.ws.onerror = () => {
      state.wsConnected = false;
    };
  }

  function handleNotificationPush(data) {
    if (!data) {
      return;
    }
    const id = data.id || Date.now();
    if (state.notifications.some(note => note.id === id)) {
      return;
    }
    const note = {
      id,
      type: data.type,
      docId: data.docId ?? null,
      commentId: data.commentId ?? null,
      taskId: data.taskId ?? null,
      payload: data.payload ?? null,
      read: false,
      createdAt: new Date().toISOString()
    };
    state.notifications = [note, ...state.notifications].slice(0, 50);
    state.unreadCount = Math.max(0, (state.unreadCount || 0) + 1);
    el.unreadBadge.textContent = state.unreadCount;
    renderNotifications();
    toast("收到新通知", "info");
  }

  function shouldHandleDocEvent(docId) {
    return state.activeDoc && Number(docId) === Number(state.activeDoc.id);
  }

  function handleWsMessage(raw) {
    let message = null;
    try {
      message = typeof raw === "string" ? JSON.parse(raw) : raw;
    } catch (err) {
      return;
    }
    if (!message || !message.type) {
      return;
    }
    const data = message.data || {};
    switch (message.type) {
      case "notification.push":
        handleNotificationPush(data);
        break;
      case "presence.join":
      case "presence.leave":
        if (shouldHandleDocEvent(data.docId)) {
          renderPresenceList(data.members || []);
        }
        break;
      case "cursor.update":
        if (shouldHandleDocEvent(data.docId)) {
          renderCursorList(data.cursors || []);
        }
        break;
      case "doc.edit":
        if (shouldHandleDocEvent(data.docId)) {
          if (state.user && data.actorId && Number(data.actorId) === Number(state.user.id)) {
            break;
          }
          if (editorDirty) {
            const now = Date.now();
            if (now - liveEditWarnAt > 3000) {
              liveEditWarnAt = now;
              toast("检测到本地编辑，暂停实时同步", "warn");
            }
            break;
          }
          applyLiveEdit(data);
        }
        break;
      case "doc.sync":
        if (shouldHandleDocEvent(data.docId)) {
          const autosave = Boolean(data.autosave);
          if (state.activeDoc && data.version && data.version <= (state.activeDoc.version || 0)) {
            break;
          }
          if (editorDirty) {
            // if (!autosave) {
            //   toast("文档已被他人更新，请刷新后查看", "warn");
            // }
            break;
          }
          const updated = {
            ...state.activeDoc,
            content: data.content,
            format: data.format || state.activeDoc?.format,
            version: data.version || state.activeDoc?.version,
            updatedAt: data.updatedAt || state.activeDoc?.updatedAt,
            role: state.activeRole || state.activeDoc?.role
          };
          state.activeDoc = updated;
          applyEditor(updated);
          if (!autosave) {
            toast("文档已同步更新", "info");
          }
        }
        break;
      case "chat.message":
        if (shouldHandleDocEvent(data.docId)) {
          loadDocChat();
        }
        break;
      case "file.shared":
        if (shouldHandleDocEvent(data.docId)) {
          loadDocFiles();
        }
        break;
      case "meeting.start":
      case "meeting.end":
        if (shouldHandleDocEvent(data.docId)) {
          loadDocMeetings();
          if (message.type === "meeting.end" && state.activeMeeting && data.id === state.activeMeeting.id) {
            closeMeetingEmbed();
          }
        }
        break;
      case "screen.share.start":
        if (shouldHandleDocEvent(data.docId)) {
          if (el.screenStatus) {
            el.screenStatus.textContent = data.shareUrl ? `进行中：${data.shareUrl}` : "进行中";
          }
          state.screenSharing = true;
        }
        break;
      case "screen.share.stop":
        if (shouldHandleDocEvent(data.docId)) {
          if (el.screenStatus) {
            el.screenStatus.textContent = "未开启";
          }
          state.screenSharing = false;
        }
        break;
      default:
        break;
    }
  }

  function parseJoinUrl(joinUrl) {
    if (!joinUrl) {
      return null;
    }
    let value = String(joinUrl).trim();
    if (!/^https?:\/\//i.test(value)) {
      value = `https://${value}`;
    }
    try {
      const url = new URL(value);
      const room = url.pathname.replace(/^\/+/, "").split("/")[0];
      if (!room) {
        return null;
      }
      return { url: value, domain: url.host, room, protocol: url.protocol };
    } catch (err) {
      return null;
    }
  }

  function loadJitsiScript(info) {
    if (!info || !info.domain) {
      return Promise.reject(new Error("invalid domain"));
    }
    if (window.JitsiMeetExternalAPI && state.jitsiDomain === info.domain) {
      return Promise.resolve();
    }
    return new Promise((resolve, reject) => {
      const script = document.createElement("script");
      script.src = `${info.protocol}//${info.domain}/external_api.js`;
      script.async = true;
      script.onload = () => {
        state.jitsiDomain = info.domain;
        resolve();
      };
      script.onerror = () => reject(new Error("load failed"));
      document.head.appendChild(script);
    });
  }

  function updateMeetingEmbedTip(message) {
    if (!el.meetingEmbedTip) {
      return;
    }
    el.meetingEmbedTip.textContent = message;
  }

  function setMeetingPanelsVisible(visible) {
    if (el.meetingEmbedCard) {
      el.meetingEmbedCard.hidden = !visible;
    }
    if (el.screenShareCard) {
      el.screenShareCard.hidden = !visible;
    }
  }

  function closeMeetingEmbed() {
    if (state.jitsiApi && typeof state.jitsiApi.dispose === "function") {
      state.jitsiApi.dispose();
    }
    state.jitsiApi = null;
    state.meetingJoined = false;
    if (el.meetingEmbed) {
      el.meetingEmbed.innerHTML = "";
    }
    updateMeetingEmbedTip("未加入会议");
    setMeetingPanelsVisible(false);
  }

  function syncScreenShareStatus(active) {
    if (!state.activeDoc) {
      return;
    }
    if (state.screenSharing === active) {
      return;
    }
    state.screenSharing = active;
    if (active) {
      const shareUrl = state.activeMeeting?.joinUrl || "";
      requestScreenShareStart(shareUrl);
    } else {
      requestScreenShareStop();
    }
  }

  function bindJitsiEvents(api) {
    if (!api || typeof api.addListener !== "function") {
      return;
    }
    api.addListener("videoConferenceJoined", () => {
      state.meetingJoined = true;
      setMeetingPanelsVisible(true);
      updateMeetingEmbedTip("会议已连接");
    });
    api.addListener("videoConferenceLeft", () => {
      closeMeetingEmbed();
    });
    api.addListener("readyToClose", () => {
      closeMeetingEmbed();
    });
    api.addListener("screenSharingStatusChanged", event => {
      syncScreenShareStatus(Boolean(event && event.on));
    });
  }

  async function openMeetingEmbed(meeting) {
    if (!meeting || !meeting.joinUrl) {
      toast("会议暂无加入链接", "warn");
      return;
    }
    const info = parseJoinUrl(meeting.joinUrl);
    if (!info) {
      window.open(meeting.joinUrl, "_blank");
      return;
    }
    try {
      await loadJitsiScript(info);
    } catch (err) {
      window.open(meeting.joinUrl, "_blank");
      return;
    }
    if (!window.JitsiMeetExternalAPI || !el.meetingEmbed) {
      window.open(meeting.joinUrl, "_blank");
      return;
    }
    closeMeetingEmbed();
    setMeetingPanelsVisible(true);
    el.meetingEmbed.innerHTML = "";
    const options = {
      roomName: info.room,
      parentNode: el.meetingEmbed,
      width: "100%",
      height: "100%",
      userInfo: {
        displayName: state.user?.nickname || ""
      }
    };
    state.jitsiApi = new window.JitsiMeetExternalAPI(info.domain, options);
    state.activeMeeting = meeting;
    bindJitsiEvents(state.jitsiApi);
    updateMeetingEmbedTip(`已加入会议：${meeting.title || "会议"}`);
  }

  function openMeetingWindow(meeting) {
    if (!meeting || !meeting.joinUrl) {
      toast("会议暂无加入链接", "warn");
      return;
    }
    window.open(meeting.joinUrl, "_blank", "noopener");
  }

  function renderList(container, items, formatter) {
    container.innerHTML = "";
    if (!items || items.length === 0) {
      container.innerHTML = "<div class=\"meta\">暂无数据</div>";
      return;
    }
    items.forEach(item => {
      const node = document.createElement("div");
      node.className = "item";
      node.innerHTML = formatter(item);
      container.appendChild(node);
    });
  }

  function renderFolderOptions() {
    if (!el.createFolder) {
      return;
    }
    const options = ["<option value=\"\">不设置文件夹</option>"];
    state.folders.forEach(folder => {
      options.push(`<option value="${folder.id}">${escapeHtml(folder.name)}</option>`);
    });
    el.createFolder.innerHTML = options.join("");
  }

  function renderTagOptions() {
    const targets = [el.createTags, el.docTagSelect].filter(Boolean);
    targets.forEach(select => {
      if (!select) {
        return;
      }
      if (!state.tags.length) {
        select.innerHTML = "<option value=\"\" disabled>暂无标签</option>";
        return;
      }
      select.innerHTML = state.tags.map(tag =>
        `<option value="${tag.id}">${escapeHtml(tag.name)}</option>`
      ).join("");
    });
    if (el.searchTag) {
      const options = ["<option value=\"\">全部标签</option>"];
      if (!state.tags.length) {
        options.push("<option value=\"\" disabled>暂无标签</option>");
      } else {
        state.tags.forEach(tag => {
          options.push(`<option value="${tag.id}">${escapeHtml(tag.name)}</option>`);
        });
      }
      el.searchTag.innerHTML = options.join("");
      if (state.docQuery && state.docQuery.tagId) {
        el.searchTag.value = String(state.docQuery.tagId);
      }
    }
    syncDocTagSelection();
  }

  function renderAuthorOptions(users = []) {
    if (!el.searchAuthor) {
      return;
    }
    const options = ["<option value=\"\">全部作者</option>"];
    const seen = new Set();
    if (state.user && state.user.id) {
      const label = state.user.nickname ? `仅本人（${escapeHtml(state.user.nickname)}）` : "仅本人";
      options.push(`<option value="${state.user.id}">${label}</option>`);
      seen.add(String(state.user.id));
    }
    users.forEach(user => {
      if (!user || !user.id) {
        return;
      }
      const id = String(user.id);
      if (seen.has(id)) {
        return;
      }
      seen.add(id);
      options.push(`<option value="${user.id}">${escapeHtml(user.nickname || "用户")} (#${user.id})</option>`);
    });
    el.searchAuthor.innerHTML = options.join("");
    if (state.docQuery && state.docQuery.authorId) {
      el.searchAuthor.value = String(state.docQuery.authorId);
    }
  }

  async function loadAuthorOptions() {
    if (!el.searchAuthor) {
      return;
    }
    let users = [];
    if (state.user && state.user.systemRole === "ADMIN") {
      try {
        const data = await api("/admin/users?page=0&size=50");
        users = data.items || [];
        state.adminUsers = users;
      } catch (err) {
        users = demo.adminUsers;
        state.adminUsers = users;
      }
    } else {
      users = state.adminUsers || [];
    }
    renderAuthorOptions(users);
  }

  function renderNotificationTypeOptions() {
    if (!el.notificationType) {
      return;
    }
    const order = ["DOC_EDIT", "COMMENT", "COMMENT_REPLY", "COMMENT_STATUS", "MENTION", "TASK_ASSIGNED", "TASK_COMPLETED", "SHARE"];
    const options = ["<option value=\"\">全部类型</option>"];
    order.forEach(key => {
      if (notificationMap[key]) {
        options.push(`<option value="${key}">${notificationMap[key]}</option>`);
      }
    });
    el.notificationType.innerHTML = options.join("");
  }

  function parseNotificationPayload(payload) {
    if (!payload) {
      return {};
    }
    if (typeof payload === "object") {
      return payload;
    }
    const text = String(payload);
    try {
      return JSON.parse(text);
    } catch (err) {
      return { message: text };
    }
  }

  function buildNotificationSummary(note) {
    const payload = parseNotificationPayload(note.payload);
    const message = payload.message || "";
    const title = payload.title ? `《${payload.title}》` : "";
    const summary = message
      ? `${message}${title ? ` · ${title}` : ""}`
      : (title || "通知更新");
    const details = [];
    if (payload.taskTitle) {
      details.push(`任务：${payload.taskTitle}`);
    }
    if (payload.status) {
      details.push(`状态：${taskStatusLabel(payload.status)}`);
    }
    if (payload.commentStatus) {
      details.push(`批注：${commentStatusLabel(payload.commentStatus)}`);
    }
    if (payload.docId) {
      details.push(`文档 #${payload.docId}`);
    }
    if (payload.commentId) {
      details.push(`评论 #${payload.commentId}`);
    }
    if (payload.taskId) {
      details.push(`任务 #${payload.taskId}`);
    }
    return { summary, detail: details.join(" | ") };
  }

  function syncDocTagSelection() {
    if (!el.docTagSelect) {
      return;
    }
    const selected = new Set((state.activeDocTags || []).map(tag => tag.id));
    Array.from(el.docTagSelect.options).forEach(option => {
      const id = Number(option.value);
      option.selected = selected.has(id);
    });
  }

  async function loadDocs() {
    try {
      const params = new URLSearchParams({ page: "0", size: "20" });
      const query = state.docQuery || defaultDocQuery;
      const timeRange = resolveTimeRange(query.timeRange);
      const useSearch = Boolean(query.q || query.authorId || timeRange.from || timeRange.to);
      if (query.sortBy) {
        params.append("sortBy", query.sortBy);
      }
      if (query.order) {
        params.append("order", query.order);
      }
      if (useSearch) {
        if (query.q) {
          params.append("q", query.q);
        }
        if (query.authorId) {
          params.append("authorId", query.authorId);
        }
        if (timeRange.from) {
          params.append("from", timeRange.from);
        }
        if (timeRange.to) {
          params.append("to", timeRange.to);
        }
        if (query.tagId) {
          params.append("tagId", query.tagId);
        }
      } else {
        if (query.folderId) {
          params.append("folderId", query.folderId);
        }
        if (query.tagId) {
          params.append("tagId", query.tagId);
        }
      }
      const endpoint = useSearch ? "/search" : "/docs";
      const data = await api(`${endpoint}?${params.toString()}`);
      let items = data.items || [];
      if (useSearch && query.folderId) {
        const folderId = Number(query.folderId);
        items = items.filter(doc => Number(doc.folderId) === folderId);
      }
      state.docs = items;
      renderDocs();
      setStatus("已连接后端服务", "ok");
    } catch (err) {
      state.docs = [];
      renderDocs();
      if (err && err.status) {
        handleError(err);
        return;
      }
      setStatus("后端不可达，无法加载文档列表。", "warn");
    }
  }

  function renderDocs(list = state.docs) {
    renderList(el.docList, list, doc => {
      const folderName = doc.folderId ? (state.folders.find(folder => folder.id === doc.folderId)?.name || `#${doc.folderId}`) : "未归档";
      return `
        <div>
          <div><strong>${escapeHtml(doc.title)}</strong></div>
          <div class="meta">#${doc.id} | ${formatLabel(doc.format)} | ${folderName} | ${doc.updatedAt || "-"}</div>
        </div>
        <div class="inline">
          <button class="ghost" data-open-doc="${doc.id}">打开</button>
        </div>
      `;
    });
    el.docList.querySelectorAll("[data-open-doc]").forEach(btn => {
      btn.addEventListener("click", () => openDoc(btn.dataset.openDoc));
    });
    updateStats();
    updateFilterIndicator();
  }

  async function loadWorkspace() {
    await loadDocs();
    await loadFolders();
    await loadTags();
    await loadTemplates();
    await loadNotifications();
    await loadAssignedTasks();
  }

  function updateFilterIndicator() {
    if (!el.filterIndicator) {
      return;
    }
    const parts = [];
    const query = state.docQuery || defaultDocQuery;
    if (query.q) {
      parts.push(`标题：${query.q}`);
    }
    if (query.authorId) {
      const authorId = Number(query.authorId);
      let label = String(query.authorId);
      if (state.user && state.user.id === authorId) {
        label = "本人";
      } else {
        const author = (state.adminUsers || []).find(user => user.id === authorId);
        if (author) {
          label = `${author.nickname || "用户"} (#${authorId})`;
        }
      }
      parts.push(`作者：${label}`);
    }
    if (query.folderId) {
      const folderId = Number(query.folderId);
      const folderName = state.folders.find(folder => folder.id === folderId)?.name;
      parts.push(`文件夹：${folderName || query.folderId}`);
    }
    if (query.tagId) {
      const tagId = Number(query.tagId);
      const tagName = state.tags.find(tag => tag.id === tagId)?.name;
      parts.push(`标签：${tagName || query.tagId}`);
    }
    if (query.timeRange) {
      const labels = {
        today: "今天",
        "7d": "近7天",
        "30d": "近30天",
        "90d": "近90天"
      };
      parts.push(`时间：${labels[query.timeRange] || query.timeRange}`);
    }
    el.filterIndicator.textContent = parts.length ? `当前筛选：${parts.join(" / ")}` : "当前筛选：全部";
  }

  function syncDocQueryForm() {
    const query = state.docQuery || defaultDocQuery;
    const searchForm = document.getElementById("search-form");
    if (searchForm) {
      if (searchForm.q) {
        searchForm.q.value = query.q || "";
      }
      if (searchForm.authorId) {
        searchForm.authorId.value = query.authorId || "";
      }
      if (searchForm.tagId) {
        searchForm.tagId.value = query.tagId || "";
      }
      if (searchForm.timeRange) {
        searchForm.timeRange.value = query.timeRange || "";
      }
      if (searchForm.sortBy) {
        searchForm.sortBy.value = query.sortBy || defaultDocQuery.sortBy;
      }
      if (searchForm.order) {
        searchForm.order.value = query.order || defaultDocQuery.order;
      }
    }
    const docSortForm = document.getElementById("doc-sort-form");
    if (docSortForm) {
      docSortForm.sortBy.value = query.sortBy || defaultDocQuery.sortBy;
      docSortForm.order.value = query.order || defaultDocQuery.order;
    }
    const globalSearch = document.getElementById("global-search");
    if (globalSearch && globalSearch.value !== (query.q || "")) {
      globalSearch.value = query.q || "";
    }
    if (el.searchTime && el.searchTime.value !== (query.timeRange || "")) {
      el.searchTime.value = query.timeRange || "";
    }
  }

  function applyDocFilter(next) {
    state.docQuery = { ...state.docQuery, ...next };
    syncDocQueryForm();
    loadDocs();
  }

  function clearDocFilters() {
    state.docQuery = { ...defaultDocQuery };
    if (docSearchTimer) {
      clearTimeout(docSearchTimer);
      docSearchTimer = null;
    }
    syncDocQueryForm();
    loadDocs();
  }

  async function loadFolders() {
    if (!el.folderList) {
      return;
    }
    try {
      const data = await api("/folders");
      state.folders = data || [];
    } catch (err) {
      state.folders = [];
      handleError(err);
    }
    renderFolderOptions();
    renderList(el.folderList, state.folders, folder => `
      <div>
        <div><strong>${escapeHtml(folder.name)}</strong></div>
        <div class="meta">#${folder.id}</div>
      </div>
      <button class="ghost" data-filter-folder="${folder.id}">筛选</button>
    `);
    if (el.folderList) {
      el.folderList.querySelectorAll("[data-filter-folder]").forEach(btn => {
        btn.addEventListener("click", () => {
          applyDocFilter({ folderId: Number(btn.dataset.filterFolder) });
        });
      });
    }
    if (state.docs.length) {
      renderDocs();
    }
    updateFilterIndicator();
  }

  async function loadTags() {
    if (!el.tagList) {
      return;
    }
    try {
      const data = await api("/tags");
      state.tags = data || [];
    } catch (err) {
      state.tags = [];
      handleError(err);
    }
    renderTagOptions();
    renderList(el.tagList, state.tags, tag => `
      <div>
        <div><strong>${escapeHtml(tag.name)}</strong></div>
        <div class="meta">#${tag.id}</div>
      </div>
      <button class="ghost" data-filter-tag="${tag.id}">筛选</button>
    `);
    if (el.tagList) {
      el.tagList.querySelectorAll("[data-filter-tag]").forEach(btn => {
        btn.addEventListener("click", () => {
          applyDocFilter({ tagId: Number(btn.dataset.filterTag) });
        });
      });
    }
    if (state.docs.length) {
      renderDocs();
    }
    updateFilterIndicator();
  }

  async function openDoc(id) {
    const docId = Number(id);
    if (!docId) {
      toast("请填写文档ID", "warn");
      return;
    }
    try {
      closeMeetingEmbed();
      const doc = await api(`/docs/${docId}`);
      state.activeDoc = doc;
      state.activeRole = doc.role || "VIEWER";
      applyEditor(doc);
      setView("editor");
      connectWs();
      await loadDocVersions();
      await loadDocTags();
    } catch (err) {
      handleError(err);
    }
  }

  function applyEditor(doc) {
    if (!doc) {
      return;
    }
    el.editorTitle.textContent = doc.title || "未命名";
    el.editorMeta.textContent = `角色：${roleLabel(doc.role)} | 版本：${doc.version ?? "-"}`;
    el.docFormat.value = doc.format || "RICH_TEXT";
    lastFormat = el.docFormat.value;
    setEditorMode(el.docFormat.value);
    setEditorContent(doc.content || "");
    el.docVersion.value = doc.version ?? "";
    el.docRename.value = "";
    editorDirty = false;
    state.activeDocTags = [];
    if (el.docTagList) {
      el.docTagList.innerHTML = "<div class=\"meta\">暂无数据</div>";
    }
    syncDocTagSelection();
    if (el.presenceList) {
      el.presenceList.innerHTML = "<div class=\"meta\">暂无在线成员</div>";
    }
    if (el.cursorList) {
      el.cursorList.innerHTML = "<div class=\"meta\">暂无光标</div>";
    }
    startAutosave();
  }

  async function saveDoc(auto = false) {
    if (!state.activeDoc) {
      toast("请先打开文档", "warn");
      return;
    }
    const payload = {
      content: getEditorContent(),
      baseVersion: Number(el.docVersion.value || state.activeDoc.version || 1),
      format: el.docFormat.value || "RICH_TEXT"
    };
    try {
      const url = auto ? `/docs/${state.activeDoc.id}/autosave` : `/docs/${state.activeDoc.id}`;
      const updated = await api(url, {
        method: "PUT",
        body: JSON.stringify(payload)
      });
      state.activeDoc = updated;
      applyEditor(updated);
      await loadDocVersions();
      if (!auto) {
        toast("保存完成", "info");
      }
    } catch (err) {
      handleError(err);
    }
  }

  async function renameDoc() {
    if (!state.activeDoc) {
      toast("请先打开文档", "warn");
      return;
    }
    const title = el.docRename.value.trim();
    if (!title) {
      toast("请输入新标题", "warn");
      return;
    }
    try {
      const updated = await api(`/docs/${state.activeDoc.id}/title`, {
        method: "PUT",
        body: JSON.stringify({ title })
      });
      state.activeDoc = { ...state.activeDoc, title: updated.title };
      applyEditor(updated);
      toast("标题已更新", "info");
      await loadDocs();
    } catch (err) {
      handleError(err);
    }
  }

  async function deleteDoc() {
    if (!state.activeDoc) {
      return;
    }
    try {
      await api(`/docs/${state.activeDoc.id}`, { method: "DELETE" });
      toast("文档已删除", "warn");
      state.activeDoc = null;
      await loadDocs();
      setView("docs");
    } catch (err) {
      handleError(err);
    }
  }

  async function loadTemplates() {
    try {
      const data = await api("/templates?page=0&size=20");
      state.templates = data.items || [];
    } catch (err) {
      state.templates = demo.templates;
      handleError(err);
    }
    renderList(el.templateList, state.templates, tpl => `
      <div>
        <div><strong>${escapeHtml(tpl.title)}</strong></div>
        <div class="meta">${escapeHtml(tpl.description || "")}</div>
        <div class="meta">#${tpl.id} | ${formatLabel(tpl.format)}</div>
      </div>
      <div class="inline">
        <button class="ghost" data-template-detail="${tpl.id}">查看</button>
        <button class="ghost" data-template-delete="${tpl.id}">删除</button>
      </div>
    `);
    if (el.templateList) {
      el.templateList.querySelectorAll("[data-template-detail]").forEach(btn => {
        btn.addEventListener("click", () => loadTemplateDetail(btn.dataset.templateDetail));
      });
      el.templateList.querySelectorAll("[data-template-delete]").forEach(btn => {
        btn.addEventListener("click", () => deleteTemplate(btn.dataset.templateDelete));
      });
    }
    updateTemplateOptions();
    updateStats();
  }

  async function loadTemplateDetail(templateId) {
    const id = Number(templateId);
    if (!id) {
      toast("请输入模板ID", "warn");
      return;
    }
    try {
      const tpl = await api(`/templates/${id}`);
      const form = document.getElementById("template-update-form");
      if (form) {
        form.templateId.value = tpl.id || "";
        form.title.value = tpl.title || "";
        form.description.value = tpl.description || "";
        form.content.value = tpl.content || "";
        form.format.value = tpl.format || "";
        form.visibility.value = String(tpl.isPublic);
      }
      toast("模板详情已加载", "info");
    } catch (err) {
      handleError(err);
    }
  }

  async function deleteTemplate(templateId) {
    const id = Number(templateId);
    if (!id) {
      toast("请输入模板ID", "warn");
      return;
    }
    if (!confirm("确定删除模板吗？")) {
      return;
    }
    try {
      await api(`/templates/${id}`, { method: "DELETE" });
      toast("模板已删除", "warn");
      await loadTemplates();
    } catch (err) {
      handleError(err);
    }
  }

  function updateTemplateOptions() {
    if (!el.templateSelect) {
      return;
    }
    el.templateSelect.innerHTML = "<option value=\"\">不使用模板</option>";
    state.templates.forEach(template => {
      const option = document.createElement("option");
      option.value = template.id;
      option.textContent = template.title;
      el.templateSelect.appendChild(option);
    });
  }

  function renderNotifications() {
    if (!el.notificationList) {
      return;
    }
    renderList(el.notificationList, state.notifications, note => {
      const info = buildNotificationSummary(note);
      return `
        <div>
          <div><strong>${notificationLabel(note.type)}</strong></div>
          <div class="meta">${escapeHtml(info.summary)}</div>
          ${info.detail ? `<div class="meta note-detail">${escapeHtml(info.detail)}</div>` : ""}
        </div>
        <div class="stack">
          <div class="meta">#${note.id} | ${note.read ? "已读" : "未读"}</div>
          <div class="meta">${note.createdAt || "-"}</div>
          ${note.read ? "" : `<button class="ghost" data-mark-read="${note.id}">标记已读</button>`}
        </div>
      `;
    });
    el.notificationList.querySelectorAll("[data-mark-read]").forEach(btn => {
      btn.addEventListener("click", async () => {
        const ids = parseIdList(btn.dataset.markRead);
        await markNotificationsRead(ids);
      });
    });
    updateStats();
  }

  async function loadNotifications(query = "") {
    try {
      const data = await api(`/notifications?page=0&size=20${query}`);
      state.notifications = (data.page && data.page.items) ? data.page.items : [];
      state.unreadCount = data.unreadCount ?? state.notifications.filter(n => !n.read).length;
      el.unreadBadge.textContent = state.unreadCount;
    } catch (err) {
      state.notifications = [];
      state.unreadCount = 0;
      el.unreadBadge.textContent = 0;
      handleError(err);
    }
    renderNotifications();
  }

  async function markNotificationsRead(ids) {
    if (!ids || !ids.length) {
      toast("请输入通知ID", "warn");
      return;
    }
    try {
      await api("/notifications/read", {
        method: "POST",
        body: JSON.stringify({ ids, all: false })
      });
      toast("已标记为已读", "info");
      await loadNotifications();
    } catch (err) {
      handleError(err);
    }
  }

  async function loadAssignedTasks() {
    try {
      const data = await api("/tasks/assigned?page=0&size=20");
      state.tasks = data.items || [];
    } catch (err) {
      state.tasks = demo.tasks;
      handleError(err);
    }
    renderList(el.assignedList, state.tasks, task => `
      <div>
        <div><strong>${escapeHtml(task.title)}</strong></div>
        <div class="meta">${taskStatusLabel(task.status)} | 截止 ${task.dueAt || "-"}</div>
      </div>
      <div class="meta">#${task.id || "-"}</div>
    `);
    updateStats();
  }

  async function loadProfile() {
    if (!state.token) {
      return;
    }
    try {
      const data = await api("/users/me");
      state.user = data;
      updateAdminVisibility();
      syncProfileForm(state.user);
      setStatus("个人资料已加载", "ok");
      await loadAuthorOptions();
    } catch (err) {
      handleError(err);
    }
  }

  async function loadAudit() {
    try {
      const data = await api("/users/me/audit?page=0&size=20");
      const items = data.items || [];
      renderList(el.auditList, items, log => `
        <div>
          <div><strong>${actionLabel(log.action)}</strong></div>
          <div class="meta">${log.createdAt || "-"}</div>
        </div>
        <div class="meta">${log.targetType || ""}</div>
      `);
    } catch (err) {
      renderList(el.auditList, demo.audit, log => `
        <div>
          <div><strong>${actionLabel(log.action)}</strong></div>
          <div class="meta">${log.createdAt}</div>
        </div>
      `);
      handleError(err);
    }
  }

  async function loadDocAudit() {
    if (!state.activeDoc) {
      toast("请先打开文档", "warn");
      return;
    }
    if (!el.docAuditList) {
      return;
    }
    try {
      const data = await api(`/docs/${state.activeDoc.id}/audit?page=0&size=20`);
      const items = data.items || [];
      renderList(el.docAuditList, items, log => `
        <div>
          <div><strong>${actionLabel(log.action)}</strong></div>
          <div class="meta">${log.createdAt || "-"}</div>
        </div>
        <div class="meta">${log.targetType || ""} #${log.targetId || ""}</div>
      `);
    } catch (err) {
      renderList(el.docAuditList, [], () => "");
      handleError(err);
    }
  }

  async function loadDocMembers() {
    if (!state.activeDoc) {
      return;
    }
    try {
      const members = await api(`/docs/${state.activeDoc.id}/members`);
      renderList(el.memberList, members, member => `
        <div>
          <div><strong>${escapeHtml(member.nickname || "用户")}</strong></div>
          <div class="meta">${roleLabel(member.role)}</div>
        </div>
        <div class="meta">#${member.userId}</div>
      `);
    } catch (err) {
      renderList(el.memberList, demo.members, member => `
        <div>
          <div><strong>${member.nickname}</strong></div>
          <div class="meta">${roleLabel(member.role)}</div>
        </div>
      `);
      handleError(err);
    }
  }

  async function loadDocTags() {
    if (!state.activeDoc || !el.docTagList) {
      return;
    }
    try {
      const tags = await api(`/docs/${state.activeDoc.id}/tags`);
      state.activeDocTags = tags || [];
      renderList(el.docTagList, state.activeDocTags, tag => `
        <div><strong>${escapeHtml(tag.name)}</strong></div>
        <div class="meta">#${tag.id}</div>
      `);
      syncDocTagSelection();
    } catch (err) {
      state.activeDocTags = [];
      renderList(el.docTagList, [], () => "");
      handleError(err);
    }
  }

  async function loadDocComments() {
    if (!state.activeDoc) {
      return;
    }
      try {
        const comments = await api(`/docs/${state.activeDoc.id}/comments?page=0&size=50`);
        renderList(el.commentList, comments, comment => `
          <div>
            <div><strong>评论 #${comment.id}</strong></div>
            <div class="meta">${escapeHtml(comment.content)}</div>
            <div class="meta">状态：${commentStatusLabel(comment.status)}</div>
          </div>
          <div class="meta">${comment.createdAt || "-"}</div>
        `);
      } catch (err) {
        renderList(el.commentList, demo.comments, comment => `
          <div>
            <div><strong>评论 #${comment.id}</strong></div>
            <div class="meta">${escapeHtml(comment.content)}</div>
          </div>
        `);
        handleError(err);
      }
  }

  async function loadDocTasks() {
    if (!state.activeDoc) {
      return;
    }
    try {
      const params = new URLSearchParams({ page: "0", size: "20" });
      if (state.taskFilter.status) {
        params.append("status", state.taskFilter.status);
      }
      const data = await api(`/docs/${state.activeDoc.id}/tasks?${params.toString()}`);
      renderList(el.taskList, data.items || [], task => `
        <div>
          <div><strong>${escapeHtml(task.title)}</strong></div>
          <div class="meta">${taskStatusLabel(task.status)}</div>
        </div>
        <div class="meta">#${task.id}</div>
      `);
    } catch (err) {
      renderList(el.taskList, demo.tasks, task => `
        <div>
          <div><strong>${escapeHtml(task.title)}</strong></div>
          <div class="meta">${taskStatusLabel(task.status)}</div>
        </div>
      `);
      handleError(err);
    }
  }

  async function loadDocChat() {
    if (!state.activeDoc) {
      return;
    }
    try {
      const data = await api(`/docs/${state.activeDoc.id}/chat/messages?page=0&size=20`);
      renderList(el.chatList, data.items || [], msg => `
        <div>
          <div><strong>${formatUserLabel(msg.senderId, msg.senderNickname)}</strong></div>
          <div class="meta">${escapeHtml(msg.content)}</div>
        </div>
        <div class="meta">${msg.createdAt || "-"}</div>
      `);
    } catch (err) {
      renderList(el.chatList, demo.chat, msg => `
        <div>
          <div><strong>${formatUserLabel(msg.senderId, msg.senderNickname)}</strong></div>
          <div class="meta">${escapeHtml(msg.content)}</div>
        </div>
      `);
      handleError(err);
    }
  }

  async function loadDocFiles() {
    if (!state.activeDoc) {
      return;
    }
    try {
      const data = await api(`/docs/${state.activeDoc.id}/files?page=0&size=20`);
      renderList(el.fileList, data.items || [], file => `
        <div>
          <div><strong>${escapeHtml(file.originalName)}</strong></div>
          <div class="meta">${file.sizeBytes || "-"} 字节</div>
        </div>
        <a class="meta" href="${file.downloadUrl}" target="_blank">下载</a>
      `);
    } catch (err) {
      renderList(el.fileList, demo.files, file => `
        <div>
          <div><strong>${escapeHtml(file.originalName)}</strong></div>
          <div class="meta">${file.sizeBytes} 字节</div>
        </div>
        <a class="meta" href="${file.downloadUrl}">下载</a>
      `);
      handleError(err);
    }
  }

  async function loadDocMeetings() {
    if (!state.activeDoc) {
      return;
    }
    try {
      const data = await api(`/docs/${state.activeDoc.id}/meetings?page=0&size=20`);
      const items = data.items || [];
      state.meetings = items;
      state.activeMeeting = items.find(meeting => meeting.status === "ACTIVE") || items[0] || null;
      renderList(el.meetingList, items, meeting => `
        <div>
          <div><strong>${escapeHtml(meeting.title || "会议")}</strong></div>
          <div class="meta">${meetingStatusLabel(meeting.status)}</div>
        </div>
        <div class="inline">
          <a class="meta" href="${meeting.joinUrl || "#"}" target="_blank">链接</a>
          <button class="ghost" data-meeting-open="${meeting.id}">进入</button>
          <button class="ghost" data-meeting-window="${meeting.id}">新窗口</button>
          ${meeting.status === "ACTIVE" ? `<button class="ghost" data-meeting-end="${meeting.id}">结束</button>` : ""}
        </div>
      `);
      el.meetingList.querySelectorAll("[data-meeting-open]").forEach(button => {
        const meetingId = Number(button.dataset.meetingOpen);
        const meeting = items.find(item => item.id === meetingId);
        button.addEventListener("click", () => openMeetingEmbed(meeting));
      });
      el.meetingList.querySelectorAll("[data-meeting-window]").forEach(button => {
        const meetingId = Number(button.dataset.meetingWindow);
        const meeting = items.find(item => item.id === meetingId);
        button.addEventListener("click", () => openMeetingWindow(meeting));
      });
      el.meetingList.querySelectorAll("[data-meeting-end]").forEach(button => {
        button.addEventListener("click", () => endMeeting(button.dataset.meetingEnd));
      });
      if (state.activeMeeting) {
        updateMeetingEmbedTip(`当前会议：${state.activeMeeting.title || "会议"}`);
        if (el.activeMeetingInfo) {
          el.activeMeetingInfo.textContent = `当前会议：${state.activeMeeting.title || "会议"} (${meetingStatusLabel(state.activeMeeting.status)})`;
        }
      } else {
        updateMeetingEmbedTip("暂无会议");
        if (el.activeMeetingInfo) {
          el.activeMeetingInfo.textContent = "当前会议：-";
        }
      }
    } catch (err) {
      state.meetings = demo.meetings;
      state.activeMeeting = demo.meetings[0] || null;
      renderList(el.meetingList, demo.meetings, meeting => `
        <div>
          <div><strong>${escapeHtml(meeting.title)}</strong></div>
          <div class="meta">${meetingStatusLabel(meeting.status)}</div>
        </div>
        <div class="inline">
          <a class="meta" href="${meeting.joinUrl}">链接</a>
          <button class="ghost" data-meeting-open="${meeting.id}">进入</button>
          <button class="ghost" data-meeting-window="${meeting.id}">新窗口</button>
          ${meeting.status === "ACTIVE" ? `<button class="ghost" data-meeting-end="${meeting.id}">结束</button>` : ""}
        </div>
      `);
      el.meetingList.querySelectorAll("[data-meeting-open]").forEach(button => {
        const meetingId = Number(button.dataset.meetingOpen);
        const meeting = demo.meetings.find(item => item.id === meetingId);
        button.addEventListener("click", () => openMeetingEmbed(meeting));
      });
      el.meetingList.querySelectorAll("[data-meeting-window]").forEach(button => {
        const meetingId = Number(button.dataset.meetingWindow);
        const meeting = demo.meetings.find(item => item.id === meetingId);
        button.addEventListener("click", () => openMeetingWindow(meeting));
      });
      el.meetingList.querySelectorAll("[data-meeting-end]").forEach(button => {
        button.addEventListener("click", () => endMeeting(button.dataset.meetingEnd));
      });
      if (state.activeMeeting) {
        updateMeetingEmbedTip(`当前会议：${state.activeMeeting.title || "会议"}`);
        if (el.activeMeetingInfo) {
          el.activeMeetingInfo.textContent = `当前会议：${state.activeMeeting.title || "会议"} (${meetingStatusLabel(state.activeMeeting.status)})`;
        }
      } else {
        updateMeetingEmbedTip("暂无会议");
        if (el.activeMeetingInfo) {
          el.activeMeetingInfo.textContent = "当前会议：-";
        }
      }
      handleError(err);
    }
  }

  function renderPresenceList(members = []) {
    if (!el.presenceList) {
      return;
    }
    renderList(el.presenceList, members, member => `
      <div>
        <div><strong>${escapeHtml(member.nickname || "用户")}</strong></div>
        <div class="meta">#${member.userId}</div>
      </div>
      <div class="meta">在线</div>
    `);
  }

  function renderCursorList(cursors = []) {
    if (!el.cursorList) {
      return;
    }
    renderList(el.cursorList, cursors, cursor => `
      <div>
        <div><strong>${escapeHtml(cursor.nickname || "用户")}</strong></div>
        <div class="meta">#${cursor.userId}</div>
      </div>
      <div class="meta">L${cursor.line} C${cursor.column}</div>
    `);
  }

  async function loadActiveMeeting() {
    if (!state.activeDoc) {
      toast("请先打开文档", "warn");
      return;
    }
    try {
      const meeting = await api(`/docs/${state.activeDoc.id}/meetings/active`);
      state.activeMeeting = meeting;
      if (el.activeMeetingInfo) {
        el.activeMeetingInfo.textContent = `当前会议：${meeting.title || "会议"} (${meetingStatusLabel(meeting.status)})`;
      }
      updateMeetingEmbedTip(`当前会议：${meeting.title || "会议"}`);
      toast("已加载当前会议", "info");
    } catch (err) {
      if (el.activeMeetingInfo) {
        el.activeMeetingInfo.textContent = "当前会议：-";
      }
      handleError(err);
    }
  }

  async function endMeeting(meetingId) {
    if (!state.activeDoc) {
      toast("请先打开文档", "warn");
      return;
    }
    const id = Number(meetingId);
    if (!id) {
      toast("会议ID无效", "warn");
      return;
    }
    try {
      await api(`/docs/${state.activeDoc.id}/meetings/${id}/end`, { method: "POST" });
      toast("会议已结束", "info");
      await loadDocMeetings();
    } catch (err) {
      handleError(err);
    }
  }

  async function requestScreenShareStart(shareUrl) {
    if (!state.activeDoc) {
      return;
    }
    try {
      await api(`/docs/${state.activeDoc.id}/screen-share/start`, {
        method: "POST",
        body: JSON.stringify({ shareUrl: shareUrl || "" })
      });
      toast("屏幕共享已开启", "info");
      await loadScreenStatus();
    } catch (err) {
      handleError(err);
    }
  }

  async function requestScreenShareStop() {
    if (!state.activeDoc) {
      return;
    }
    try {
      await api(`/docs/${state.activeDoc.id}/screen-share/stop`, { method: "POST" });
      toast("屏幕共享已停止", "info");
      await loadScreenStatus();
    } catch (err) {
      handleError(err);
    }
  }

  async function loadScreenStatus() {
    if (!state.activeDoc) {
      return;
    }
    try {
      const data = await api(`/docs/${state.activeDoc.id}/screen-share`);
      el.screenStatus.textContent = data.active ? `进行中：${data.shareUrl || ""}` : "未开启";
      state.screenSharing = Boolean(data.active);
    } catch (err) {
      el.screenStatus.textContent = "未开启";
      state.screenSharing = false;
      handleError(err);
    }
  }

  async function loadDocVersions() {
    if (!state.activeDoc) {
      return;
    }
    if (!el.versionList) {
      return;
    }
    const type = el.versionFilter ? el.versionFilter.value : "all";
    try {
      const data = await api(`/docs/${state.activeDoc.id}/versions?page=0&size=20&type=${type}`);
      const items = data.items || [];
      renderList(el.versionList, items, version => `
        <div>
          <div><strong>版本 ${version.versionNumber}</strong></div>
          <div class="meta">${version.autosave ? "自动保存" : "手动保存"} | ${version.createdAt || "-"}</div>
        </div>
        <div class="inline">
          <button class="ghost" data-restore-version="${version.id}">恢复</button>
          <button class="ghost" data-version-detail="${version.id}">详情</button>
        </div>
      `);
      if (el.versionList) {
        el.versionList.querySelectorAll("[data-restore-version]").forEach(btn => {
          btn.addEventListener("click", () => restoreVersion(btn.dataset.restoreVersion));
        });
        el.versionList.querySelectorAll("[data-version-detail]").forEach(btn => {
          btn.addEventListener("click", () => loadVersionDetail(btn.dataset.versionDetail));
        });
      }
      if (el.versionDetailMeta) {
        el.versionDetailMeta.textContent = "选择版本查看详情";
      }
      if (el.versionDetailContent) {
        el.versionDetailContent.value = "";
      }
    } catch (err) {
      handleError(err);
    }
  }

  async function loadVersionDetail(versionId) {
    if (!state.activeDoc) {
      toast("请先打开文档", "warn");
      return;
    }
    const id = Number(versionId);
    if (!id) {
      toast("版本ID无效", "warn");
      return;
    }
    try {
      const detail = await api(`/docs/${state.activeDoc.id}/versions/${id}`);
      if (el.versionDetailMeta) {
        const autosaveLabel = detail.autosave ? "自动保存" : "手动保存";
        el.versionDetailMeta.textContent = `版本 ${detail.versionNumber} | ${formatLabel(detail.format)} | ${autosaveLabel}`;
      }
      if (el.versionDetailContent) {
        el.versionDetailContent.value = detail.content || "";
      }
    } catch (err) {
      handleError(err);
    }
  }

  async function restoreVersion(versionId) {
    if (!state.activeDoc) {
      toast("请先打开文档", "warn");
      return;
    }
    const baseVersion = Number(el.docVersion.value || state.activeDoc.version || 1);
    try {
      const updated = await api(`/docs/${state.activeDoc.id}/versions/${versionId}/restore`, {
        method: "POST",
        body: JSON.stringify({ baseVersion })
      });
      state.activeDoc = updated;
      applyEditor(updated);
      toast("已恢复到指定版本", "info");
      await loadDocVersions();
    } catch (err) {
      handleError(err);
    }
  }

  async function loadSettings() {
    try {
      const data = await api("/notifications/settings");
      el.muteAll.checked = data.muteAll;
      renderList(el.typeSettings, data.types || [], setting => `
        <div>
          <div><strong>${notificationLabel(setting.type)}</strong></div>
          <div class="meta">${setting.enabled ? "已开启" : "已静默"}</div>
        </div>
        <button class="ghost" data-type-toggle="${setting.type}" data-enabled="${setting.enabled}">
          ${setting.enabled ? "关闭" : "开启"}
        </button>
      `);
      el.typeSettings.querySelectorAll("[data-type-toggle]").forEach(btn => {
        btn.addEventListener("click", async () => {
          const type = btn.dataset.typeToggle;
          const enabled = btn.dataset.enabled === "true" ? false : true;
          try {
            await api(`/notifications/settings/${type}`, {
              method: "PUT",
              body: JSON.stringify({ enabled })
            });
            toast("通知设置已更新", "info");
            await loadSettings();
          } catch (err) {
            handleError(err);
          }
        });
      });
    } catch (err) {
      el.typeSettings.innerHTML = "<div class=\"meta\">暂无法加载通知设置</div>";
      handleError(err);
    }
  }

  async function loadAdminUsers() {
    try {
      const params = new URLSearchParams({ page: "0", size: "20" });
      if (state.adminUserFilter.q) {
        params.append("q", state.adminUserFilter.q);
      }
      if (state.adminUserFilter.role) {
        params.append("role", state.adminUserFilter.role);
      }
      if (state.adminUserFilter.status) {
        params.append("status", state.adminUserFilter.status);
      }
      const data = await api(`/admin/users?${params.toString()}`);
      const users = data.items || [];
      state.adminUsers = users;
      renderList(el.adminUserList, users, user => `
        <div>
          <div><strong>${escapeHtml(user.nickname || "用户")}</strong></div>
          <div class="meta">${systemRoleLabel(user.systemRole)} | ${userStatusLabel(user.status)}</div>
        </div>
        <div class="meta">#${user.id}</div>
      `);
      renderAuthorOptions(users);
    } catch (err) {
      renderList(el.adminUserList, demo.adminUsers, user => `
        <div>
          <div><strong>${escapeHtml(user.nickname)}</strong></div>
          <div class="meta">${systemRoleLabel(user.systemRole)} | ${userStatusLabel(user.status)}</div>
        </div>
      `);
      state.adminUsers = demo.adminUsers;
      renderAuthorOptions(state.adminUsers);
      handleError(err);
    }
  }

  async function loadSurveyStats() {
    try {
      const data = await api("/admin/surveys/stats");
      renderList(el.surveyStats, data.counts || [], item => `
        <div>
          <div><strong>${item.rating} 星</strong></div>
          <div class="meta">${item.count} 条反馈</div>
        </div>
        <div class="meta">平均 ${data.averageRating.toFixed(2)}</div>
      `);
    } catch (err) {
      const stats = demo.surveyStats;
      renderList(el.surveyStats, stats.counts, item => `
        <div>
          <div><strong>${item.rating} 星</strong></div>
          <div class="meta">${item.count} 条反馈</div>
        </div>
        <div class="meta">平均 ${stats.averageRating.toFixed(2)}</div>
      `);
      handleError(err);
    }
  }

  async function loadSurveyList() {
    try {
      const data = await api("/admin/surveys?page=0&size=20");
      const items = data.items || [];
      renderList(el.surveyList, items, item => `
        <div>
          <div><strong>用户 #${item.userId}</strong></div>
          <div class="meta">${escapeHtml(item.comment || "")}</div>
        </div>
        <div class="meta">${item.rating} 星</div>
      `);
    } catch (err) {
      renderList(el.surveyList, demo.surveys, item => `
        <div>
          <div><strong>用户 #${item.userId}</strong></div>
          <div class="meta">${escapeHtml(item.comment || "")}</div>
        </div>
        <div class="meta">${item.rating} 星</div>
      `);
      handleError(err);
    }
  }

  function bindRichToolbar() {
    if (!el.richToolbar || !el.richEditor) {
      return;
    }
    el.richToolbar.addEventListener("click", event => {
      const button = event.target.closest("[data-command]");
      if (!button) {
        return;
      }
      const command = button.dataset.command;
      if (command === "createLink") {
        const url = prompt("请输入链接地址");
        if (url) {
          document.execCommand("createLink", false, url);
          markEditorDirty();
        }
        return;
      }
      if (command === "removeFormat") {
        document.execCommand("removeFormat");
        markEditorDirty();
        return;
      }
      document.execCommand(command, false, button.dataset.value || undefined);
      markEditorDirty();
    });
  }

  function bindNav() {
    document.querySelectorAll("[data-view-target]").forEach(btn => {
      btn.addEventListener("click", () => {
        setView(btn.dataset.viewTarget);
      });
    });
  }

  function bindForms() {
    document.getElementById("create-doc-form").addEventListener("submit", async event => {
      event.preventDefault();
      const form = event.target;
      const payload = {
        title: form.title.value || null,
        content: form.content.value ? form.content.value : null,
        format: form.format.value || null,
        templateId: form.templateId.value ? Number(form.templateId.value) : null
      };
      const folderId = form.folderId && form.folderId.value ? Number(form.folderId.value) : null;
      if (folderId) {
        payload.folderId = folderId;
      }
      const selectedTags = el.createTags
        ? Array.from(el.createTags.selectedOptions)
          .map(option => Number(option.value))
          .filter(value => Number.isFinite(value))
        : [];
      if (selectedTags.length) {
        payload.tagIds = selectedTags;
      }
      try {
        const doc = await api("/docs", { method: "POST", body: JSON.stringify(payload) });
        toast("文档创建成功", "info");
        await loadDocs();
        await openDoc(doc.id);
      } catch (err) {
        handleError(err);
      }
    });

    if (el.templateSelect) {
      el.templateSelect.addEventListener("change", event => {
        const templateId = Number(event.target.value);
        if (!templateId) {
          return;
        }
        const template = state.templates.find(item => item.id === templateId);
        if (!template) {
          return;
        }
        const form = document.getElementById("create-doc-form");
        if (form) {
          if (!form.title.value) {
            form.title.value = template.title || "";
          }
          if (!form.content.value) {
            form.content.value = template.content || "";
          }
          if (form.format && template.format) {
            form.format.value = template.format;
          }
        }
      });
    }

    document.getElementById("search-form").addEventListener("submit", event => {
      event.preventDefault();
      const form = event.target;
      state.docQuery = {
        ...state.docQuery,
        q: form.q.value.trim(),
        authorId: form.authorId.value.trim(),
        tagId: form.tagId.value.trim(),
        timeRange: form.timeRange ? form.timeRange.value.trim() : ""
      };
      if (form.sortBy) {
        state.docQuery.sortBy = form.sortBy.value || defaultDocQuery.sortBy;
      }
      if (form.order) {
        state.docQuery.order = form.order.value || defaultDocQuery.order;
      }
      syncDocQueryForm();
      loadDocs();
    });

    const folderForm = document.getElementById("folder-form");
    if (folderForm) {
      folderForm.addEventListener("submit", async event => {
        event.preventDefault();
        try {
          await api("/folders", {
            method: "POST",
            body: JSON.stringify({
              name: folderForm.name.value,
              parentId: folderForm.parentId.value ? Number(folderForm.parentId.value) : null
            })
          });
          folderForm.reset();
          toast("文件夹已创建", "info");
          await loadFolders();
        } catch (err) {
          handleError(err);
        }
      });
    }

    const folderRenameForm = document.getElementById("folder-rename-form");
    if (folderRenameForm) {
      folderRenameForm.addEventListener("submit", async event => {
        event.preventDefault();
        const form = event.target;
        const folderId = form.folderId.value ? Number(form.folderId.value) : null;
        if (!folderId) {
          toast("请输入文件夹ID", "warn");
          return;
        }
        if (!form.newName.value.trim()) {
          toast("请输入新的名称", "warn");
          return;
        }
        try {
          await api(`/folders/${folderId}`, {
            method: "PUT",
            body: JSON.stringify({ name: form.newName.value })
          });
          toast("文件夹已重命名", "info");
          form.reset();
          await loadFolders();
        } catch (err) {
          handleError(err);
        }
      });
    }

    const folderDeleteForm = document.getElementById("folder-delete-form");
    if (folderDeleteForm) {
      folderDeleteForm.addEventListener("submit", async event => {
        event.preventDefault();
        const form = event.target;
        const folderId = form.folderId.value ? Number(form.folderId.value) : null;
        if (!folderId) {
          toast("请输入文件夹ID", "warn");
          return;
        }
        if (!confirm("确定删除文件夹吗？")) {
          return;
        }
        try {
          await api(`/folders/${folderId}`, { method: "DELETE" });
          toast("文件夹已删除", "warn");
          form.reset();
          await loadFolders();
          await loadDocs();
        } catch (err) {
          handleError(err);
        }
      });
    }

    const tagForm = document.getElementById("tag-form");
    if (tagForm) {
      tagForm.addEventListener("submit", async event => {
        event.preventDefault();
        try {
          await api("/tags", {
            method: "POST",
            body: JSON.stringify({ name: tagForm.name.value })
          });
          tagForm.reset();
          toast("标签已创建", "info");
          await loadTags();
        } catch (err) {
          handleError(err);
        }
      });
    }

    const tagRenameForm = document.getElementById("tag-rename-form");
    if (tagRenameForm) {
      tagRenameForm.addEventListener("submit", async event => {
        event.preventDefault();
        const form = event.target;
        const tagId = form.tagId.value ? Number(form.tagId.value) : null;
        if (!tagId) {
          toast("请输入标签ID", "warn");
          return;
        }
        if (!form.newName.value.trim()) {
          toast("请输入新的名称", "warn");
          return;
        }
        try {
          await api(`/tags/${tagId}`, {
            method: "PUT",
            body: JSON.stringify({ name: form.newName.value })
          });
          toast("标签已重命名", "info");
          form.reset();
          await loadTags();
        } catch (err) {
          handleError(err);
        }
      });
    }

    const tagDeleteForm = document.getElementById("tag-delete-form");
    if (tagDeleteForm) {
      tagDeleteForm.addEventListener("submit", async event => {
        event.preventDefault();
        const form = event.target;
        const tagId = form.tagId.value ? Number(form.tagId.value) : null;
        if (!tagId) {
          toast("请输入标签ID", "warn");
          return;
        }
        if (!confirm("确定删除标签吗？")) {
          return;
        }
        try {
          await api(`/tags/${tagId}`, { method: "DELETE" });
          toast("标签已删除", "warn");
          form.reset();
          await loadTags();
          await loadDocs();
        } catch (err) {
          handleError(err);
        }
      });
    }

    const openDocForm = document.getElementById("open-doc-form");
    if (openDocForm) {
      openDocForm.addEventListener("submit", event => {
        event.preventDefault();
        const input = document.getElementById("open-doc-id");
        const idValue = input ? input.value.trim() : "";
        openDoc(idValue);
        if (input) {
          input.value = "";
        }
      });
    }

    const docSortForm = document.getElementById("doc-sort-form");
    if (docSortForm) {
      docSortForm.addEventListener("submit", event => {
        event.preventDefault();
        const form = event.target;
        state.docQuery = {
          ...state.docQuery,
          sortBy: form.sortBy.value || defaultDocQuery.sortBy,
          order: form.order.value || defaultDocQuery.order
        };
        syncDocQueryForm();
        loadDocs();
      });
    }

    document.getElementById("login-form").addEventListener("submit", async event => {
      event.preventDefault();
      const form = event.target;
      try {
        const data = await api("/auth/login", {
          method: "POST",
          body: JSON.stringify({ account: form.account.value, password: form.password.value })
        });
        state.token = data.token;
        localStorage.setItem("docflow_token", state.token);
        updateAuthUI();
        connectWs();
        await loadProfile();
        await loadWorkspace();
        toast("\u767b\u5f55\u6210\u529f", "info");
        setView("docs");
      } catch (err) {
        handleError(err);
      }
    });

    document.getElementById("register-form").addEventListener("submit", async event => {
      event.preventDefault();
      const form = event.target;
      try {
        const data = await api("/auth/register", {
          method: "POST",
          body: JSON.stringify({
            email: form.email.value || null,
            phone: form.phone.value || null,
            password: form.password.value,
            nickname: form.nickname.value
          })
        });
        state.token = data.token;
        localStorage.setItem("docflow_token", state.token);
        updateAuthUI();
        connectWs();
        await loadProfile();
        await loadWorkspace();
        toast("注册成功", "info");
        setView("docs");
      } catch (err) {
        handleError(err);
      }
    });

    document.getElementById("forgot-form").addEventListener("submit", async event => {
      event.preventDefault();
      const form = event.target;
      try {
        const data = await api("/auth/forgot", {
          method: "POST",
          body: JSON.stringify({ account: form.account.value })
        });
        el.forgotResult.textContent = `重置令牌：${data.resetToken}`;
        toast("已生成重置令牌", "info");
      } catch (err) {
        handleError(err);
      }
    });

    document.getElementById("reset-form").addEventListener("submit", async event => {
      event.preventDefault();
      const form = event.target;
      try {
        await api("/auth/reset", {
          method: "POST",
          body: JSON.stringify({ resetToken: form.resetToken.value, newPassword: form.newPassword.value })
        });
        toast("密码已重置", "info");
        setView("login");
      } catch (err) {
        handleError(err);
      }
    });

    document.getElementById("profile-form").addEventListener("submit", async event => {
      event.preventDefault();
      const form = event.target;
      try {
        await api("/users/me", {
          method: "PUT",
          body: JSON.stringify({
            nickname: form.nickname.value || null,
            email: form.email.value || null,
            phone: form.phone.value || null
          })
        });
        toast("资料已更新", "info");
        await loadProfile();
      } catch (err) {
        handleError(err);
      }
    });

    document.getElementById("avatar-form").addEventListener("submit", async event => {
      event.preventDefault();
      const form = event.target;
      const file = form.file.files[0];
      if (!file) {
        toast("请选择文件", "warn");
        return;
      }
      const formData = new FormData();
      formData.append("file", file);
      try {
        const data = await api("/users/me/avatar", {
          method: "POST",
          body: formData
        });
        state.user = data;
        syncProfileForm(state.user);
        toast("头像已更新", "info");
      } catch (err) {
        handleError(err);
      }
    });

    document.getElementById("survey-form").addEventListener("submit", async event => {
      event.preventDefault();
      const form = event.target;
      try {
        await api("/surveys", {
          method: "POST",
          body: JSON.stringify({ rating: Number(form.rating.value), comment: form.comment.value })
        });
        toast("反馈已提交", "info");
      } catch (err) {
        handleError(err);
      }
    });

    document.getElementById("member-form").addEventListener("submit", async event => {
      event.preventDefault();
      if (!state.activeDoc) {
        return;
      }
      const form = event.target;
      try {
        await api(`/docs/${state.activeDoc.id}/members`, {
          method: "PUT",
          body: JSON.stringify({
            userId: Number(form.userId.value),
            role: form.role.value,
            remove: form.remove.checked
          })
        });
        toast("成员权限已更新", "info");
        await loadDocMembers();
      } catch (err) {
        handleError(err);
      }
    });

    const docTagForm = document.getElementById("doc-tag-form");
    if (docTagForm) {
      docTagForm.addEventListener("submit", async event => {
        event.preventDefault();
        if (!state.activeDoc) {
          return;
        }
        const tagIds = el.docTagSelect
          ? Array.from(el.docTagSelect.selectedOptions)
            .map(option => Number(option.value))
            .filter(value => Number.isFinite(value))
          : [];
        try {
          await api(`/docs/${state.activeDoc.id}/tags`, {
            method: "PUT",
            body: JSON.stringify({ tagIds })
          });
          toast("标签已更新", "info");
          await loadDocTags();
          await loadDocs();
        } catch (err) {
          handleError(err);
        }
      });
    }

    document.getElementById("comment-form").addEventListener("submit", async event => {
      event.preventDefault();
      if (!state.activeDoc) {
        return;
      }
      const form = event.target;
      try {
        await api(`/docs/${state.activeDoc.id}/comments`, {
          method: "POST",
          body: JSON.stringify({
            blockId: form.blockId.value || null,
            paragraphIndex: form.paragraphIndex.value ? Number(form.paragraphIndex.value) : null,
            content: form.content.value,
            mentions: parseIdList(form.mentions.value)
          })
        });
        toast("评论已提交", "info");
        await loadDocComments();
      } catch (err) {
        handleError(err);
      }
    });

    const commentStatusForm = document.getElementById("comment-status-form");
    if (commentStatusForm) {
      commentStatusForm.addEventListener("submit", async event => {
        event.preventDefault();
        const form = event.target;
        const commentId = form.commentId.value ? Number(form.commentId.value) : null;
        if (!commentId) {
          toast("请输入评论ID", "warn");
          return;
        }
        try {
          await api(`/comments/${commentId}/status`, {
            method: "PUT",
            body: JSON.stringify({ status: form.status.value })
          });
          toast("批注状态已更新", "info");
          await loadDocComments();
        } catch (err) {
          handleError(err);
        }
      });
    }

    const commentReplyForm = document.getElementById("comment-reply-form");
    if (commentReplyForm) {
      commentReplyForm.addEventListener("submit", async event => {
        event.preventDefault();
        const form = event.target;
        const commentId = form.commentId.value ? Number(form.commentId.value) : null;
        if (!commentId) {
          toast("请输入评论ID", "warn");
          return;
        }
        if (!form.content.value.trim()) {
          toast("请输入回复内容", "warn");
          return;
        }
        try {
          await api(`/comments/${commentId}/replies`, {
            method: "POST",
            body: JSON.stringify({
              content: form.content.value,
              mentions: parseIdList(form.mentions.value)
            })
          });
          toast("回复已提交", "info");
          form.reset();
          await loadDocComments();
        } catch (err) {
          handleError(err);
        }
      });
    }

    document.getElementById("task-form").addEventListener("submit", async event => {
      event.preventDefault();
      if (!state.activeDoc) {
        return;
      }
      const form = event.target;
      try {
        await api(`/docs/${state.activeDoc.id}/tasks`, {
          method: "POST",
          body: JSON.stringify({
            title: form.title.value,
            description: null,
            assigneeId: form.assigneeId.value ? Number(form.assigneeId.value) : null,
            dueAt: form.dueAt.value || null
          })
        });
        toast("任务已创建", "info");
        await loadDocTasks();
      } catch (err) {
        handleError(err);
      }
    });

    const taskUpdateForm = document.getElementById("task-update-form");
    if (taskUpdateForm) {
      taskUpdateForm.addEventListener("submit", async event => {
        event.preventDefault();
        const form = event.target;
        const taskId = form.taskId.value ? Number(form.taskId.value) : null;
        if (!taskId) {
          toast("请输入任务ID", "warn");
          return;
        }
        const payload = {};
        if (form.title.value) {
          payload.title = form.title.value;
        }
        if (form.description.value) {
          payload.description = form.description.value;
        }
        if (form.assigneeId.value) {
          payload.assigneeId = Number(form.assigneeId.value);
        }
        if (form.dueAt.value) {
          payload.dueAt = form.dueAt.value;
        }
        if (form.status.value) {
          payload.status = form.status.value;
        }
        if (!Object.keys(payload).length) {
          toast("请至少填写一个字段", "warn");
          return;
        }
        try {
          await api(`/tasks/${taskId}`, {
            method: "PUT",
            body: JSON.stringify(payload)
          });
          toast("任务已更新", "info");
          await loadDocTasks();
          await loadAssignedTasks();
        } catch (err) {
          handleError(err);
        }
      });
    }

    const taskFilterForm = document.getElementById("task-filter-form");
    if (taskFilterForm) {
      taskFilterForm.addEventListener("submit", event => {
        event.preventDefault();
        const form = event.target;
        state.taskFilter.status = form.status.value || "";
        loadDocTasks();
      });
    }
    const clearTaskFilter = document.getElementById("clear-task-filter");
    if (clearTaskFilter) {
      clearTaskFilter.addEventListener("click", () => {
        state.taskFilter.status = "";
        if (taskFilterForm) {
          taskFilterForm.reset();
        }
        loadDocTasks();
      });
    }

    document.getElementById("chat-form").addEventListener("submit", async event => {
      event.preventDefault();
      if (!state.activeDoc) {
        return;
      }
      const form = event.target;
      try {
        await api(`/docs/${state.activeDoc.id}/chat/messages`, {
          method: "POST",
          body: JSON.stringify({ content: form.content.value })
        });
        form.reset();
        await loadDocChat();
      } catch (err) {
        handleError(err);
      }
    });

    document.getElementById("file-form").addEventListener("submit", async event => {
      event.preventDefault();
      if (!state.activeDoc) {
        return;
      }
      const form = event.target;
      const file = form.file.files[0];
      if (!file) {
        toast("请选择文件", "warn");
        return;
      }
      const formData = new FormData();
      formData.append("file", file);
      try {
        await api(`/docs/${state.activeDoc.id}/files`, {
          method: "POST",
          body: formData
        });
        toast("文件已上传", "info");
        await loadDocFiles();
      } catch (err) {
        handleError(err);
      }
    });

    document.getElementById("meeting-form").addEventListener("submit", async event => {
      event.preventDefault();
      if (!state.activeDoc) {
        return;
      }
      const form = event.target;
      try {
        await api(`/docs/${state.activeDoc.id}/meetings`, {
          method: "POST",
          body: JSON.stringify({
            title: form.title.value,
            provider: form.provider.value,
            joinUrl: form.joinUrl.value
          })
        });
        toast("会议已创建", "info");
        await loadDocMeetings();
      } catch (err) {
        handleError(err);
      }
    });

    document.getElementById("screen-start").addEventListener("click", async event => {
      event.preventDefault();
      if (!state.activeDoc) {
        return;
      }
      if (state.jitsiApi && typeof state.jitsiApi.executeCommand === "function") {
        try {
          state.jitsiApi.executeCommand("toggleShareScreen");
          return;
        } catch (err) {
          // 回退到后端状态更新
        }
      }
      const shareUrl = document.getElementById("screen-url").value || state.activeMeeting?.joinUrl || "";
      await requestScreenShareStart(shareUrl);
    });

    document.getElementById("screen-stop").addEventListener("click", async event => {
      event.preventDefault();
      if (!state.activeDoc) {
        return;
      }
      if (state.jitsiApi && typeof state.jitsiApi.executeCommand === "function") {
        try {
          state.jitsiApi.executeCommand("toggleShareScreen");
          return;
        } catch (err) {
          // 回退到后端状态更新
        }
      }
      await requestScreenShareStop();
    });

    document.getElementById("notification-filter").addEventListener("submit", async event => {
      event.preventDefault();
      const form = event.target;
      const params = new URLSearchParams();
      ["type", "read", "from", "to"].forEach(key => {
        if (form[key].value) {
          params.append(key, form[key].value);
        }
      });
      const query = params.toString() ? `&${params.toString()}` : "";
      await loadNotifications(query);
    });

    document.getElementById("mark-all-read").addEventListener("click", async () => {
      try {
        await api("/notifications/read", {
          method: "POST",
          body: JSON.stringify({ ids: [], all: true })
        });
        toast("已全部标记为已读", "info");
        await loadNotifications();
      } catch (err) {
        handleError(err);
      }
    });

    const markReadForm = document.getElementById("mark-read-form");
    if (markReadForm) {
      markReadForm.addEventListener("submit", async event => {
        event.preventDefault();
        const form = event.target;
        const ids = parseIdList(form.ids.value);
        await markNotificationsRead(ids);
        form.reset();
      });
    }

    document.getElementById("template-form").addEventListener("submit", async event => {
      event.preventDefault();
      const form = event.target;
      try {
        await api("/templates", {
          method: "POST",
          body: JSON.stringify({
            title: form.title.value,
            description: form.description.value || null,
            content: form.content.value,
            format: form.format.value,
            isPublic: form.isPublic.checked
          })
        });
        toast("模板已创建", "info");
        await loadTemplates();
      } catch (err) {
        handleError(err);
      }
    });

    const templateUpdateForm = document.getElementById("template-update-form");
    if (templateUpdateForm) {
      templateUpdateForm.addEventListener("submit", async event => {
        event.preventDefault();
        const form = event.target;
        const templateId = form.templateId.value ? Number(form.templateId.value) : null;
        if (!templateId) {
          toast("请输入模板ID", "warn");
          return;
        }
        const payload = {};
        if (form.title.value) {
          payload.title = form.title.value;
        }
        if (form.description.value) {
          payload.description = form.description.value;
        }
        if (form.content.value) {
          payload.content = form.content.value;
        }
        if (form.format.value) {
          payload.format = form.format.value;
        }
        if (form.visibility.value) {
          payload.isPublic = form.visibility.value === "true";
        }
        if (!Object.keys(payload).length) {
          toast("请至少填写一个字段", "warn");
          return;
        }
        try {
          await api(`/templates/${templateId}`, {
            method: "PUT",
            body: JSON.stringify(payload)
          });
          toast("模板已更新", "info");
          await loadTemplates();
        } catch (err) {
          handleError(err);
        }
      });
    }

    const templateDeleteForm = document.getElementById("template-delete-form");
    if (templateDeleteForm) {
      templateDeleteForm.addEventListener("submit", async event => {
        event.preventDefault();
        const form = event.target;
        const templateId = form.templateId.value ? Number(form.templateId.value) : null;
        if (!templateId) {
          toast("请输入模板ID", "warn");
          return;
        }
        if (!confirm("确定删除模板吗？")) {
          return;
        }
        try {
          await api(`/templates/${templateId}`, { method: "DELETE" });
          toast("模板已删除", "warn");
          form.reset();
          await loadTemplates();
        } catch (err) {
          handleError(err);
        }
      });
    }

    document.getElementById("behavior-form").addEventListener("submit", async event => {
      event.preventDefault();
      const form = event.target;
      try {
        const data = await api(`/admin/users/${form.userId.value}/behavior`);
        el.behaviorResult.innerHTML = `
          <div class="item"><div><strong>操作次数</strong></div><div class="meta">${data.totalActions}</div></div>
          <div class="item"><div><strong>最近活跃</strong></div><div class="meta">${data.lastActiveAt || "-"}</div></div>
        `;
      } catch (err) {
        el.behaviorResult.innerHTML = "";
        handleError(err);
      }
    });

    document.getElementById("admin-audit-form").addEventListener("submit", async event => {
      event.preventDefault();
      const form = event.target;
      try {
        const data = await api(`/admin/users/${form.userId.value}/audit?page=0&size=20`);
        renderList(el.adminAuditList, data.items || [], log => `
          <div>
            <div><strong>${actionLabel(log.action)}</strong></div>
            <div class="meta">${log.createdAt || "-"}</div>
          </div>
          <div class="meta">${log.targetType || ""}</div>
        `);
      } catch (err) {
        handleError(err);
      }
    });

    const adminFilterForm = document.getElementById("admin-user-filter-form");
    if (adminFilterForm) {
      adminFilterForm.addEventListener("submit", event => {
        event.preventDefault();
        const form = event.target;
        state.adminUserFilter.q = form.q.value.trim();
        state.adminUserFilter.role = form.role.value;
        state.adminUserFilter.status = form.status.value;
        loadAdminUsers();
      });
    }
    const clearAdminFilter = document.getElementById("clear-admin-filter");
    if (clearAdminFilter) {
      clearAdminFilter.addEventListener("click", () => {
        state.adminUserFilter = { q: "", role: "", status: "" };
        if (adminFilterForm) {
          adminFilterForm.reset();
        }
        loadAdminUsers();
      });
    }

    const adminRoleForm = document.getElementById("admin-role-form");
    if (adminRoleForm) {
      adminRoleForm.addEventListener("submit", async event => {
        event.preventDefault();
        const form = event.target;
        const userId = form.userId.value ? Number(form.userId.value) : null;
        if (!userId) {
          toast("请输入用户ID", "warn");
          return;
        }
        try {
          await api(`/admin/users/${userId}/role`, {
            method: "PUT",
            body: JSON.stringify({ role: form.role.value })
          });
          toast("角色已更新", "info");
          await loadAdminUsers();
        } catch (err) {
          handleError(err);
        }
      });
    }

    const adminStatusForm = document.getElementById("admin-status-form");
    if (adminStatusForm) {
      adminStatusForm.addEventListener("submit", async event => {
        event.preventDefault();
        const form = event.target;
        const userId = form.userId.value ? Number(form.userId.value) : null;
        if (!userId) {
          toast("请输入用户ID", "warn");
          return;
        }
        try {
          await api(`/admin/users/${userId}/status`, {
            method: "PUT",
            body: JSON.stringify({ status: form.status.value })
          });
          toast("状态已更新", "info");
          await loadAdminUsers();
        } catch (err) {
          handleError(err);
        }
      });
    }

    document.getElementById("save-mute").addEventListener("click", async () => {
      try {
        await api("/notifications/settings", {
          method: "PUT",
          body: JSON.stringify({ muteAll: el.muteAll.checked })
        });
        toast("设置已保存", "info");
        await loadSettings();
      } catch (err) {
        handleError(err);
      }
    });
  }

  function bindButtons() {
    document.getElementById("refresh-docs").addEventListener("click", loadDocs);
    const clearFilter = document.getElementById("clear-filter");
    if (clearFilter) {
      clearFilter.addEventListener("click", clearDocFilters);
    }
    const refreshFolders = document.getElementById("refresh-folders");
    if (refreshFolders) {
      refreshFolders.addEventListener("click", loadFolders);
    }
    const refreshTags = document.getElementById("refresh-tags");
    if (refreshTags) {
      refreshTags.addEventListener("click", loadTags);
    }
    document.getElementById("back-to-docs").addEventListener("click", () => setView("docs"));
    document.getElementById("save-doc").addEventListener("click", () => saveDoc(false));
    const autosaveButton = document.getElementById("autosave-doc");
    if (autosaveButton) {
      autosaveButton.addEventListener("click", () => saveDoc(true));
    }
    document.getElementById("rename-doc").addEventListener("click", renameDoc);
    document.getElementById("delete-doc").addEventListener("click", deleteDoc);
    document.getElementById("load-members").addEventListener("click", loadDocMembers);
    const loadDocTagsButton = document.getElementById("load-doc-tags");
    if (loadDocTagsButton) {
      loadDocTagsButton.addEventListener("click", loadDocTags);
    }
    document.getElementById("load-comments").addEventListener("click", loadDocComments);
    document.getElementById("load-tasks").addEventListener("click", loadDocTasks);
    document.getElementById("load-chat").addEventListener("click", loadDocChat);
    document.getElementById("load-files").addEventListener("click", loadDocFiles);
    document.getElementById("load-meetings").addEventListener("click", loadDocMeetings);
    const loadActiveMeetingButton = document.getElementById("load-active-meeting");
    if (loadActiveMeetingButton) {
      loadActiveMeetingButton.addEventListener("click", loadActiveMeeting);
    }
    const loadDocAuditButton = document.getElementById("load-doc-audit");
    if (loadDocAuditButton) {
      loadDocAuditButton.addEventListener("click", loadDocAudit);
    }
    document.getElementById("load-screen").addEventListener("click", loadScreenStatus);
    if (el.meetingJoin) {
      el.meetingJoin.addEventListener("click", () => {
        if (!state.activeMeeting) {
          toast("暂无会议可加入", "warn");
          return;
        }
        openMeetingEmbed(state.activeMeeting);
      });
    }
    if (el.meetingOpenWindow) {
      el.meetingOpenWindow.addEventListener("click", () => {
        if (!state.activeMeeting) {
          toast("暂无会议可加入", "warn");
          return;
        }
        openMeetingWindow(state.activeMeeting);
      });
    }
    if (el.meetingLeave) {
      el.meetingLeave.addEventListener("click", () => {
        closeMeetingEmbed();
      });
    }
    const loadVersions = document.getElementById("load-versions");
    if (loadVersions) {
      loadVersions.addEventListener("click", loadDocVersions);
    }
    document.getElementById("refresh-templates").addEventListener("click", loadTemplates);
    const loadTemplateDetailButton = document.getElementById("load-template-detail");
    if (loadTemplateDetailButton) {
      loadTemplateDetailButton.addEventListener("click", () => {
        const form = document.getElementById("template-update-form");
        if (!form) {
          return;
        }
        loadTemplateDetail(form.templateId.value);
      });
    }
    document.getElementById("refresh-notifications").addEventListener("click", () => loadNotifications());
    document.getElementById("refresh-settings").addEventListener("click", loadSettings);
    document.getElementById("refresh-assigned").addEventListener("click", loadAssignedTasks);
    document.getElementById("refresh-audit").addEventListener("click", loadAudit);
    document.getElementById("refresh-admin-users").addEventListener("click", loadAdminUsers);
    document.getElementById("refresh-survey-stats").addEventListener("click", loadSurveyStats);
    document.getElementById("refresh-survey-list").addEventListener("click", loadSurveyList);
    if (el.docContent) {
      el.docContent.addEventListener("input", handleEditorInput);
      el.docContent.addEventListener("keyup", scheduleCursorBroadcast);
      el.docContent.addEventListener("click", scheduleCursorBroadcast);
    }
    if (el.richEditor) {
      el.richEditor.addEventListener("input", handleEditorInput);
      el.richEditor.addEventListener("keyup", scheduleCursorBroadcast);
      el.richEditor.addEventListener("click", scheduleCursorBroadcast);
    }
    if (el.docFormat) {
      el.docFormat.addEventListener("change", () => {
        const currentContent = lastFormat === "RICH_TEXT"
          ? (el.richEditor ? el.richEditor.innerHTML : "")
          : (el.docContent ? el.docContent.value : "");
        setEditorContent(currentContent);
        lastFormat = el.docFormat.value;
        setEditorMode(el.docFormat.value);
        markEditorDirty();
        scheduleDocEditBroadcast();
      });
    }
    if (el.versionFilter) {
      el.versionFilter.addEventListener("change", loadDocVersions);
    }
    document.getElementById("save-base").addEventListener("click", () => {
      const value = el.baseUrl.value.trim();
      if (!value) {
        return;
      }
      state.baseUrl = value;
      localStorage.setItem("docflow_baseUrl", value);
      toast("服务地址已保存", "info");
      if (state.token) {
        connectWs();
      }
    });
    document.getElementById("copy-token").addEventListener("click", async () => {
      if (!state.token) {
        return;
      }
      await navigator.clipboard.writeText(state.token);
      toast("令牌已复制", "info");
    });
    el.logout.addEventListener("click", () => {
      state.token = "";
      localStorage.removeItem("docflow_token");
      updateAuthUI();
      closeWs(true);
      setView("login");
      toast("已退出登录", "warn");
    });
    const globalSearch = document.getElementById("global-search");
    if (globalSearch) {
      globalSearch.addEventListener("input", () => {
        const keyword = globalSearch.value.trim();
        state.docQuery = { ...state.docQuery, q: keyword };
        if (docSearchTimer) {
          clearTimeout(docSearchTimer);
        }
        docSearchTimer = setTimeout(() => {
          syncDocQueryForm();
          loadDocs();
        }, 300);
      });
    }
    el.primaryCta.addEventListener("click", () => {
      setView("docs");
      document.getElementById("create-doc-form").scrollIntoView({ behavior: "smooth" });
    });
  }

  async function init() {
    el.baseUrl.value = state.baseUrl;
    updateAuthUI();
    const defaultView = state.token ? "docs" : "login";
    const activeView = document.querySelector(".view.active")?.dataset.view || defaultView;
    const initialView = state.token ? activeView : "login";
    setView(initialView);
    bindNav();
    bindRichToolbar();
    bindButtons();
    bindForms();
    renderNotificationTypeOptions();
    if (state.token) {
      connectWs();
      await loadProfile();
      await loadWorkspace();
    }
    updateAdminVisibility();
    updateStats();
    setMeetingPanelsVisible(Boolean(state.jitsiApi && state.meetingJoined));
  }

  init();
})();

