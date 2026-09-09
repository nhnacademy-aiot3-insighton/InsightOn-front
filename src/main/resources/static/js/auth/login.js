// InsightOn 로그인 - login.js
// 로그인은 login.html의 서버 폼(th:action="@{/login}")으로 처리한다 — 여기서는 다루지 않는다.
// 이 파일은 로그인 페이지의 보조 기능만 담당한다:
//   1) 비밀번호 표시/숨김 토글
//   2) 이메일(아이디) 찾기 모달   POST /find-email            {userName, phoneNumber} -> {maskedEmail}
//   3) 비밀번호 재설정 요청 모달   POST /password/reset-request {email}                 -> 204 No Content
//      (재설정 링크는 이메일로 발송되고, 실제 재설정은 별도 페이지에서 진행한다)

(function () {
    "use strict";

    // JSON POST 공용 헬퍼
    async function postJson(path, payload) {
        return fetch(path, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(payload),
        });
    }

    // 결과 영역에 메시지 표시 (type: "success" | "error" | "muted")
    function showResult(el, message, type) {
        if (!el) return;
        el.textContent = message;
        el.className = "form-result"; // 기존 클래스 초기화
        if (type === "success") {
            el.style.color = "var(--success, #2f7d5c)";
        } else if (type === "error") {
            el.style.color = "var(--danger, #c0392b)";
        } else {
            el.style.color = "var(--text-muted, #6b7280)";
        }
    }

    // 버튼 로딩 상태 토글
    function setLoading(btn, loading, originalText) {
        if (!btn) return;
        if (loading) {
            btn.dataset.originalText = btn.textContent;
            btn.disabled = true;
            btn.textContent = "처리 중…";
        } else {
            btn.disabled = false;
            btn.textContent = originalText || btn.dataset.originalText || btn.textContent;
        }
    }

    // ================================================================
    // 1) 비밀번호 표시/숨김 토글
    // ================================================================
    const toggle = document.querySelector(".password-toggle");
    const passwordInput = document.getElementById("password");
    if (toggle && passwordInput) {
        toggle.addEventListener("click", () => {
            const isPw = passwordInput.type === "password";
            passwordInput.type = isPw ? "text" : "password";
            const icon = toggle.querySelector("i");
            if (icon) {
                icon.classList.toggle("ti-eye", !isPw);
                icon.classList.toggle("ti-eye-off", isPw);
            }
        });
    }

    // ================================================================
    // 2) 이메일(아이디) 찾기 모달
    //    POST /find-email  { userName, phoneNumber }  ->  { maskedEmail }
    // ================================================================
    const btnFindId = document.getElementById("btnFindId");
    if (btnFindId) {
        btnFindId.addEventListener("click", async () => {
            const userName = document.getElementById("findIdName").value.trim();
            const phoneNumber = document.getElementById("findIdPhone").value.trim().replace(/[^0-9]/g, "");
            const result = document.getElementById("findIdResult");

            if (!userName || !phoneNumber) {
                showResult(result, "이름과 전화번호를 모두 입력해주세요.", "error");
                return;
            }

            setLoading(btnFindId, true);
            try {
                const res = await postJson("/find-email", { userName, phoneNumber });

                if (res.ok) {
                    const data = await res.json();
                    if (data && data.maskedEmail) {
                        showResult(result, `가입하신 이메일: ${data.maskedEmail}`, "success");
                    } else {
                        showResult(result, "일치하는 계정을 찾지 못했어요.", "error");
                    }
                } else if (res.status === 404) {
                    showResult(result, "입력하신 정보와 일치하는 계정이 없어요.", "error");
                } else if (res.status === 400) {
                    showResult(result, "입력 형식을 다시 확인해주세요.", "error");
                } else {
                    showResult(result, "조회에 실패했어요. 잠시 후 다시 시도해주세요.", "error");
                }
            } catch (err) {
                showResult(result, "서버에 연결할 수 없어요. 잠시 후 다시 시도해주세요.", "error");
            } finally {
                setLoading(btnFindId, false, "이메일 찾기");
            }
        });
    }

    // ================================================================
    // 2-1) 탈퇴 복구 가능 계정 안내 팝업 — 렌더되어 있으면 자동으로 띄운다
    // ================================================================
    const reactivateModalEl = document.getElementById("reactivateModal");
    if (reactivateModalEl && window.bootstrap) {
        bootstrap.Modal.getOrCreateInstance(reactivateModalEl).show();
    }

    // ================================================================
    // 3) 비밀번호 재설정 요청 모달
    //    POST /reset-password/request  { email }  ->  204 No Content
    //    (성공/실패와 무관하게 "메일을 보냈다"고 안내 — 계정 존재 여부 노출 방지)
    // ================================================================
    const btnResetPassword = document.getElementById("btnResetPassword");
    if (btnResetPassword) {
        btnResetPassword.addEventListener("click", async () => {
            const email = document.getElementById("resetEmail").value.trim();
            const result = document.getElementById("resetPasswordResult");

            if (!email) {
                showResult(result, "이메일을 입력해주세요.", "error");
                return;
            }

            setLoading(btnResetPassword, true);
            try {
                const res = await postJson("/password/reset-request", { email });

                if (res.ok) {
                    // 204 등 성공: 재설정 링크 발송 안내
                    showResult(
                        result,
                        "재설정 링크를 이메일로 보냈어요. 메일함을 확인해주세요.",
                        "success"
                    );
                } else if (res.status === 400) {
                    showResult(result, "이메일 형식을 다시 확인해주세요.", "error");
                } else {
                    showResult(result, "요청에 실패했어요. 잠시 후 다시 시도해주세요.", "error");
                }
            } catch (err) {
                showResult(result, "서버에 연결할 수 없어요. 잠시 후 다시 시도해주세요.", "error");
            } finally {
                setLoading(btnResetPassword, false, "재설정 링크 보내기");
            }
        });
    }
})();