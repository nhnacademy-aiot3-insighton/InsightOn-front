/**
 * AI가 만든 텍스트(리포트 본문, 엔진알람 메시지 등)는 마크다운으로 오는데 지금까지 th:text로
 * 그냥 글자 그대로 찍었다. data-markdown 속성이 붙은 요소를 찾아 렌더링+살균한다.
 * marked/DOMPurify는 chatWidget 프래그먼트가 이미 로드해두므로 여기서 또 불러오지 않는다.
 *
 * 전역 함수로 노출하는 이유: 이 페이지가 그대로 열릴 때(DOMContentLoaded)뿐 아니라,
 * split-detail.js가 목록 화면 오른쪽 패널에 상세 HTML을 innerHTML로 통째로 옮겨 붙일 때도
 * 다시 호출해야 한다 - innerHTML로 주입된 <script>는 브라우저가 실행해주지 않기 때문.
 */
function renderMarkdownIn(root) {
    if (!window.marked || !window.DOMPurify) return;
    root.querySelectorAll('[data-markdown]').forEach((el) => {
        const raw = el.textContent;
        el.innerHTML = DOMPurify.sanitize(marked.parse(raw, {breaks: true}));
    });
}

document.addEventListener('DOMContentLoaded', () => renderMarkdownIn(document));
