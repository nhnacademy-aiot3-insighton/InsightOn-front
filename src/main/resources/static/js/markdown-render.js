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
        renderMermaidIn(el);
    });
}

/**
 * 리포트에 AI가 ```mermaid 코드블록(막대그래프 등)을 넣으면 marked가
 * <pre><code class="language-mermaid">로 만들어주는데, mermaid.js는 <div class="mermaid">
 * 안의 텍스트만 그려주므로 변환해서 렌더링한다. 문법이 깨진 차트는 mermaid가 알아서 에러
 * 박스로 표시하고 나머지 리포트 렌더링은 막지 않는다.
 */
function renderMermaidIn(el) {
    if (!window.mermaid) return;
    const nodes = [];
    el.querySelectorAll('code.language-mermaid').forEach((code) => {
        const div = document.createElement('div');
        div.className = 'mermaid';
        div.textContent = code.textContent;
        code.closest('pre').replaceWith(div);
        nodes.push(div);
    });
    if (nodes.length > 0) {
        mermaid.run({nodes}).catch((e) => console.warn('mermaid 렌더링 실패', e));
    }
}

document.addEventListener('DOMContentLoaded', () => renderMarkdownIn(document));
