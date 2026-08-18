const chatForm = document.getElementById('chatForm');
const chatInput = document.getElementById('chatInput');
const chatMessages = document.getElementById('chatMessages');

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

🎛️ 액추에이터 조작
  "사무실1 에어컨 켜줘" / "회의실 온도 23도로 맞춰줘" / "공기청정기 꺼줘"

/help — 이 도움말 다시 보기`;

function appendChatBubble(text, who) {
    const bubble = document.createElement('div');
    bubble.className = 'chat-bubble ' + who;
    bubble.textContent = text;
    chatMessages.appendChild(bubble);
    chatMessages.scrollTop = chatMessages.scrollHeight;
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

        setTimeout(() => appendChatBubble('아직 목업 단계라 실제 응답은 준비 중이에요.', 'bot'), 400);
    });
}