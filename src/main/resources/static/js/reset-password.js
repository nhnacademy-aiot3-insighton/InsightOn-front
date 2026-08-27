// InsightOn 비밀번호 재설정 확인 - reset-password.js
// 폼은 서버로 직접 제출된다: POST /password/reset-confirm  (form 방식, token + password)
// JS는 화면 보조만 담당한다:
//   1) 비밀번호 표시/숨김 토글
//   2) 두 비밀번호 일치 확인 (불일치 시 제출 막기)

(function () {
    "use strict";

    // 비밀번호 표시/숨김 토글 (data-target로 대상 지정)
    document.querySelectorAll(".password-toggle").forEach((toggle) => {
        toggle.addEventListener("click", () => {
            const targetId = toggle.dataset.target;
            const input = targetId ? document.getElementById(targetId) : null;
            if (!input) return;
            const isPw = input.type === "password";
            input.type = isPw ? "text" : "password";
            const icon = toggle.querySelector("i");
            if (icon) {
                icon.classList.toggle("ti-eye", !isPw);
                icon.classList.toggle("ti-eye-off", isPw);
            }
        });
    });

    // 두 비밀번호 일치 확인
    const form = document.getElementById("resetConfirmForm");
    if (!form) return;

    const pw = document.getElementById("password");
    const pwConfirm = document.getElementById("passwordConfirm");
    const mismatch = document.getElementById("pwMismatch");

    function checkMatch() {
        if (!pwConfirm.value) {
            mismatch.style.display = "none";
            pwConfirm.setCustomValidity("");
            return;
        }
        if (pw.value !== pwConfirm.value) {
            mismatch.style.display = "block";
            pwConfirm.setCustomValidity("비밀번호가 일치하지 않습니다.");
        } else {
            mismatch.style.display = "none";
            pwConfirm.setCustomValidity("");
        }
    }

    pw.addEventListener("input", checkMatch);
    pwConfirm.addEventListener("input", checkMatch);

    // 제출 직전 최종 확인 (불일치면 막기)
    form.addEventListener("submit", (e) => {
        if (pw.value !== pwConfirm.value) {
            e.preventDefault();
            mismatch.style.display = "block";
            pwConfirm.focus();
        }
    });
})();