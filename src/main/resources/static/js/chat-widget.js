const chatForm = document.getElementById('chatForm');
const chatInput = document.getElementById('chatInput');
const chatMessages = document.getElementById('chatMessages');
const chatPanel = document.getElementById('aiChatPanel');

const HELP_TEXT = `이렇게 물어볼 수 있어요:

📄 리포트
  "이번 주 리포트 보여줘" / "3층 회의실 8월 월간 리포트 보여줘"

📊 센서 통계
  "사무실1 최근 일주일 온습도 어때?"

🚨 엔진 알람
  "최근 알람 뭐 있었어?" / "심각 알람만 보여줘"

💡 AI 제안
  "AI 제안 이력 보여줘"

🔔 안 읽은 알림
  "안 읽은 알림 있어?"

📍 위치 목록
  "우리 그룹 위치 목록 보여줘"

🌤️ 날씨/미세먼지
  "오늘 날씨 어때?" / "미세먼지 심해?"

🎛️ 액추에이터 조작
  "사무실1 에어컨 켜줘" / "회의실 온도 23도로 맞춰줘" / "공기청정기 꺼줘"

⏰ 방문 전 예약 준비
  "오늘 오후 3시에 회의 있어" / "내일 9시까지 사무실 쾌적하게 해줘" (7일 이내만 가능)

/help — 이 도움말 다시 보기`;

function appendChatBubble(text, who) {
    const bubble = document.createElement('div');
    bubble.className = 'chat-bubble ' + who;
    bubble.textContent = text;
    chatMessages.appendChild(bubble);
    chatMessages.scrollTop = chatMessages.scrollHeight;
    return bubble;
}

/** AI 응답 대기 중: 버블에 물결치는 3점 인디케이터를 넣는다. */
function showTypingIndicator(bubble) {
    bubble.classList.add('is-typing');
    bubble.innerHTML = '<span class="chat-typing-dot"></span><span class="chat-typing-dot"></span><span class="chat-typing-dot"></span>';
    chatMessages.scrollTop = chatMessages.scrollHeight;
}

/** 첫 응답 토큰이 오거나 에러가 나면 인디케이터를 걷어낸다. */
function clearTypingIndicator(bubble) {
    if (bubble.classList.contains('is-typing')) {
        bubble.classList.remove('is-typing');
        bubble.innerHTML = '';
    }
}

/** "data:" 뒤 공백 한 칸만 떼고 나머지는 그대로 둔다 - trim()을 쓰면 토큰 자체가 줄바꿈/공백일 때 사라진다. */
function stripDataPrefix(line) {
    const value = line.slice(5);
    return value.startsWith(' ') ? value.slice(1) : value;
}

/** SSE 이벤트 블록("data: ...\n\n" 단위)에서 data: 뒤 텍스트만 뽑는다(여러 줄이면 이어붙임). */
function parseSseEvent(block) {
    return block.split('\n')
        .filter((line) => line.startsWith('data:'))
        .map(stripDataPrefix)
        .join('\n');
}

/** 마크다운(굵게/제목/목록 등)을 렌더링하고 XSS 방지를 위해 살균한다. 라이브러리 로드 실패 시 텍스트로만 표시. */
function renderMarkdown(bubble, rawText) {
    if (window.marked && window.DOMPurify) {
        bubble.innerHTML = DOMPurify.sanitize(marked.parse(rawText, {breaks: true}));
    } else {
        bubble.textContent = rawText;
    }
}

/** 페이지 로드 시 이전 대화 이력을 불러와 채팅창에 미리 채워둔다. 실패해도 빈 채팅창으로 그냥 시작. */
async function loadChatHistory() {
    let response;
    try {
        response = await fetch('/my-group/chat');
    } catch (e) {
        return;
    }
    if (!response.ok) return;

    const history = await response.json();
    history.forEach((entry) => {
        if (entry.role === 'USER') {
            appendChatBubble(entry.content, 'user');
        } else {
            renderMarkdown(appendChatBubble('', 'bot'), entry.content);
        }
    });
}

async function streamChatReply(message) {
    const locationId = chatPanel ? chatPanel.dataset.locationId : '';
    const bubble = appendChatBubble('', 'bot');
    showTypingIndicator(bubble);
    let rawText = '';

    let response;
    try {
        response = await fetch('/my-group/chat' + (locationId ? `?locationId=${locationId}` : ''), {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({message}),
        });
    } catch (e) {
        clearTypingIndicator(bubble);
        bubble.textContent = '응답을 받아오지 못했어요. 잠시 후 다시 시도해주세요.';
        return;
    }

    if (!response.ok || !response.body) {
        clearTypingIndicator(bubble);
        bubble.textContent = '응답을 받아오지 못했어요. 잠시 후 다시 시도해주세요.';
        return;
    }

    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';
    let received = false;

    while (true) {
        const {value, done} = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, {stream: true});

        let boundary;
        while ((boundary = buffer.indexOf('\n\n')) !== -1) {
            const token = parseSseEvent(buffer.slice(0, boundary));
            buffer = buffer.slice(boundary + 2);
            if (token) {
                rawText += token;
                received = true;
                clearTypingIndicator(bubble);
                renderMarkdown(bubble, rawText);
                chatMessages.scrollTop = chatMessages.scrollHeight;
            }
        }
    }

    if (!received) {
        clearTypingIndicator(bubble);
        bubble.textContent = '응답이 없어요. 잠시 후 다시 시도해주세요.';
    }
}

if (chatMessages) {
    loadChatHistory();
}

if (chatForm) {
    chatForm.addEventListener('submit', (e) => {
        e.preventDefault();
        const text = chatInput.value.trim();
        if (!text) return;
        appendChatBubble(text, 'user');
        chatInput.value = '';

        if (text.toLowerCase() === '/help') {
            appendChatBubble(HELP_TEXT, 'bot');
            return;
        }

        streamChatReply(text);
    });
}
