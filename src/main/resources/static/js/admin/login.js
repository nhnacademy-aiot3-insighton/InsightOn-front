// InsightOn 관리자 로그인 - admin/login.js
// 로그인은 서버 폼(th:action="@{/admin/login}")으로 처리한다.
// JS는 비밀번호 표시/숨김 토글만 담당한다.

(function () {
    "use strict";

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
})();