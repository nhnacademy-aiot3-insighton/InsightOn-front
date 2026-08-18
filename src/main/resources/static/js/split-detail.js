/**
 * 목록(리포트/제안/엔진알람) 왼쪽 행을 클릭하면 페이지 이동 없이 오른쪽 패널에 상세를 띄운다.
 * 상세 페이지는 그대로 두고(직접 URL 접속·북마크는 계속 전체 페이지로 동작), 여기서는 그 응답
 * HTML에서 상세 컨텐츠 부분만 잘라내 오른쪽 패널에 옮겨 붙인다 — 상세 렌더링 로직을 JS에 다시 안 써도 됨.
 */
function initSplitDetail(options) {
    const rows = document.querySelectorAll(options.rowSelector);
    const panel = document.getElementById(options.panelId);
    if (!rows.length || !panel) return;

    rows.forEach((row) => {
        row.addEventListener('click', (e) => {
            e.preventDefault();
            rows.forEach((r) => r.classList.remove('active'));
            row.classList.add('active');
            panel.innerHTML = '<p class="split-detail-empty">불러오는 중...</p>';

            fetch(row.href)
                .then((r) => {
                    if (!r.ok) throw new Error('load failed');
                    return r.text();
                })
                .then((html) => {
                    const doc = new DOMParser().parseFromString(html, 'text/html');
                    const content = doc.getElementById(options.contentId);
                    panel.innerHTML = content
                        ? content.outerHTML
                        : '<p class="split-detail-empty">불러오지 못했어요.</p>';
                })
                .catch(() => {
                    panel.innerHTML = '<p class="split-detail-empty">불러오지 못했어요. 잠시 후 다시 시도해주세요.</p>';
                });
        });
    });
}
