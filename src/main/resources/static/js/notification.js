(function () {
    const notifLists = document.querySelectorAll('.notif-list');
    if (!notifLists.length) return;

    const notifCounts = document.querySelectorAll('.notif-count');
    const notifHeaderCounts = document.querySelectorAll('.notif-dropdown-header span');

    const DETAIL_URL_BY_TYPE = {
        ENGINE_ALERT: (id) => `/my-group/engine-alerts/${id}`,
        SUGGESTION: (id) => `/my-group/suggestions/${id}`,
        REPORT: (id) => `/my-group/reports/${id}`,
        GATEWAY: () => `/manage/gateway`
    };

    const SEVERITY_BY_TYPE = {
        ENGINE_ALERT: 'severity-danger',
        GATEWAY: 'severity-warning',
        SUGGESTION: 'severity-success',
        REPORT: ''
    };

    function timeAgo(createdAt) {
        const min = Math.floor((Date.now() - new Date(createdAt).getTime()) / 60000);
        if (min < 1) return '방금 전';
        if (min < 60) return `${min}분 전`;
        const hour = Math.floor(min / 60);
        if (hour < 24) return `${hour}시간 전`;
        return `${Math.floor(hour / 24)}일 전`;
    }

    function render(notifications) {
        const unreadCount = notifications.filter((n) => !n.isRead).length;
        notifCounts.forEach((el) => {
            el.textContent = unreadCount;
            el.style.display = unreadCount > 0 ? '' : 'none';
        });
        notifHeaderCounts.forEach((el) => {
            el.textContent = `${unreadCount}건 안 읽음`;
        });

        const itemsHtml = notifications.length
            ? notifications.map((n) => `
                <a href="#" class="notif-item ${SEVERITY_BY_TYPE[n.notificationType] || ''} ${n.isRead ? 'is-read' : ''}"
                   data-id="${n.dashboardNotificationId}" data-type="${n.notificationType}" data-source-id="${n.sourceId}">
                    <div class="notif-item-title">${n.locationName ? n.locationName + ' · ' : ''}${n.title}</div>
                    <div class="notif-item-meta">${timeAgo(n.createdAt)}</div>
                </a>
            `).join('')
            : `<div style="padding:12px;color:var(--ink-faint);">새 알림이 없습니다.</div>`;

        notifLists.forEach((list) => { list.innerHTML = itemsHtml; });
    }

    function loadNotifications() {
        fetch('/groups/notifications')
            .then((r) => {
                if (!r.ok) throw new Error('notifications fetch failed');
                return r.json();
            })
            .then(render)
            .catch((err) => console.warn('[notifications] 조회 실패', err));
    }

    document.addEventListener('click', (event) => {
        const item = event.target.closest('.notif-item[data-id]');
        if (!item) return;
        event.preventDefault();

        const id = item.dataset.id;
        const buildUrl = DETAIL_URL_BY_TYPE[item.dataset.type];

        fetch(`/groups/notifications/${id}/read`, { method: 'POST' })
            .catch((err) => console.warn('[notifications] 읽음 처리 실패', err))
            .finally(() => {
                if (buildUrl) window.location.href = buildUrl(item.dataset.sourceId);
            });
    });

    loadNotifications();

    const stream = new EventSource('/groups/notifications/stream');
    stream.onmessage = () => loadNotifications();
    stream.onerror = () => console.warn('[notifications] SSE 연결 끊김 (브라우저가 자동 재연결 시도)');
})();