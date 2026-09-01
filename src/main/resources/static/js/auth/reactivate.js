// InsightOn 계정 복구(재활성화) - reactivate.js
// 복구 확인은 reactivate.html 의 서버 폼(th:action="@{/reactivate/confirm}")으로 처리한다.
// 이 파일은 보조 기능만 담당한다:
//   1) 인증 코드 발송  POST /reactivate/send-code { email }  -> 204 / 429(쿨다운) / 423(재전송 초과)
//   2) 발송 버튼 60초 쿨다운 카운트다운
//   3) 제출 직전 코드 형식(숫자 6자리) 확인

(function () {
    "use strict";

    const form = document.getElementById("reactivateForm");
    if (!form) return;

    const emailInput = document.getElementById("email");
    const codeInput = document.getElementById("code");
    const btnSendCode = document.getElementById("btnSendCode");
    const codeStatus = document.getElementById("codeStatus");

    const COOLDOWN_SECONDS = 60;
    let cooldownTimer = null;

    function showStatus(message, type) {
        codeStatus.textContent = message || "";
        codeStatus.style.display = message ? "block" : "none";
        if (type === "success") {
            codeStatus.style.color = "var(--success, #2f7d5c)";
        } else if (type === "error") {
            codeStatus.style.color = "var(--danger, #c0392b)";
        } else {
            codeStatus.style.color = "var(--text-muted, #6b7280)";
        }
    }

    function startCooldown() {
        let remaining = COOLDOWN_SECONDS;
        btnSendCode.disabled = true;
        btnSendCode.textContent = `다시 보내기 (${remaining}s)`;
        cooldownTimer = setInterval(() => {
            remaining -= 1;
            if (remaining <= 0) {
                clearInterval(cooldownTimer);
                cooldownTimer = null;
                btnSendCode.disabled = false;
                btnSendCode.textContent = "인증 코드 다시 보내기";
            } else {
                btnSendCode.textContent = `다시 보내기 (${remaining}s)`;
            }
        }, 1000);
    }

    btnSendCode.addEventListener("click", async () => {
        const email = emailInput.value.trim();
        if (!email) {
            showStatus("이메일을 입력해주세요.", "error");
            emailInput.focus();
            return;
        }

        btnSendCode.disabled = true;
        showStatus("인증 코드를 보내는 중…", "muted");
        try {
            const res = await fetch("/reactivate/send-code", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ email }),
            });

            if (res.ok) {
                showStatus("인증 코드를 이메일로 보냈어요. 메일함을 확인해주세요. (5분 이내 유효)", "success");
                codeInput.focus();
                startCooldown();
            } else if (res.status === 429) {
                showStatus("방금 보냈어요. 잠시 후 다시 시도해주세요.", "error");
                btnSendCode.disabled = false;
            } else if (res.status === 423) {
                showStatus("재전송 횟수를 초과해 잠시 잠겼어요. 15분 후 다시 시도해주세요.", "error");
                btnSendCode.disabled = false;
            } else if (res.status === 400) {
                showStatus("이메일 형식을 다시 확인해주세요.", "error");
                btnSendCode.disabled = false;
            } else {
                showStatus("발송에 실패했어요. 잠시 후 다시 시도해주세요.", "error");
                btnSendCode.disabled = false;
            }
        } catch (err) {
            showStatus("서버에 연결할 수 없어요. 잠시 후 다시 시도해주세요.", "error");
            btnSendCode.disabled = false;
        }
    });

    // 제출 직전 코드 형식 확인
    form.addEventListener("submit", (e) => {
        const code = codeInput.value.trim();
        if (!/^[0-9]{6}$/.test(code)) {
            e.preventDefault();
            showStatus("인증 코드는 숫자 6자리예요.", "error");
            codeInput.focus();
        }
    });
})();
