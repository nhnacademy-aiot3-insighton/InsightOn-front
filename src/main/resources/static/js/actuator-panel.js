(function () {
    const grid = document.querySelector('.actuator-grid');
    if (!grid) return;

    const BASE_URL = `/locations/${ACTUATOR_INIT.locationId}/actuators`;

    function actuatorIdOf(el) {
        return el.closest('.actuator-card').dataset.actuatorId;
    }

    function sendState(actuatorId, command, value) {
        return fetch(`${BASE_URL}/${actuatorId}/state`, {
            method: 'PUT',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({[command]: value})
        }).then((r) => {
            if (!r.ok) throw new Error('command failed');
        });
    }

    // ---------- 전원 토글 ----------
    grid.querySelectorAll('.cmd-toggle').forEach((toggle) => {
        toggle.addEventListener('change', () => {
            const actuatorId = actuatorIdOf(toggle);
            const value = toggle.checked ? 'ON' : 'OFF';
            sendState(actuatorId, toggle.dataset.command, value).catch(() => {
                toggle.checked = !toggle.checked;
                alert('조작에 실패했어요. 잠시 후 다시 시도해주세요.');
            });
        });
    });

    // ---------- 모드 선택(chip) ----------
    grid.querySelectorAll('.chip-select').forEach((chipGroup) => {
        chipGroup.querySelectorAll('.cmd-chip').forEach((chip) => {
            chip.addEventListener('click', () => {
                const actuatorId = actuatorIdOf(chip);
                const previousActive = chipGroup.querySelector('.cmd-chip.active');
                chipGroup.querySelectorAll('.cmd-chip').forEach((c) => c.classList.remove('active'));
                chip.classList.add('active');
                sendState(actuatorId, chipGroup.dataset.command, chip.dataset.value).catch(() => {
                    chip.classList.remove('active');
                    if (previousActive) previousActive.classList.add('active');
                    alert('조작에 실패했어요. 잠시 후 다시 시도해주세요.');
                });
            });
        });
    });

    // ---------- 숫자 범위(온도 등) ----------
    grid.querySelectorAll('.temp-stepper').forEach((stepper) => {
        const valueEl = stepper.querySelector('.cmd-range-value');
        const min = Number(stepper.dataset.min);
        const max = Number(stepper.dataset.max);

        function step(delta) {
            const actuatorId = actuatorIdOf(stepper);
            const current = Number(valueEl.textContent);
            const next = Math.min(max, Math.max(min, current + delta));
            if (next === current) return;
            valueEl.textContent = next;
            sendState(actuatorId, stepper.dataset.command, next).catch(() => {
                valueEl.textContent = current;
                alert('조작에 실패했어요. 잠시 후 다시 시도해주세요.');
            });
        }

        stepper.querySelector('.cmd-step-down').addEventListener('click', () => step(-1));
        stepper.querySelector('.cmd-step-up').addEventListener('click', () => step(1));
    });

    // ---------- 삭제 ----------
    grid.querySelectorAll('.btn-delete-actuator').forEach((btn) => {
        btn.addEventListener('click', (e) => {
            e.preventDefault();
            if (!confirm('이 액추에이터를 삭제할까요?')) return;
            const actuatorId = actuatorIdOf(btn);
            fetch(`${BASE_URL}/${actuatorId}`, {method: 'DELETE'})
                .then((r) => {
                    if (!r.ok) throw new Error('delete failed');
                    location.reload();
                })
                .catch(() => alert('삭제하지 못했어요. 잠시 후 다시 시도해주세요.'));
        });
    });

    // ---------- 추가 ----------
    const addStatusEl = document.getElementById('addActuatorStatus');
    document.getElementById('btnCreateActuator').addEventListener('click', function () {
        const name = document.getElementById('newActuatorName').value.trim();
        if (!name) {
            addStatusEl.textContent = '이름을 입력하세요.';
            addStatusEl.style.display = 'block';
            return;
        }
        addStatusEl.style.display = 'none';
        const actuatorType = document.querySelector('input[name="newActuatorType"]:checked').value;
        this.disabled = true;

        fetch(BASE_URL, {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({name, actuatorType})
        }).then((r) => {
            if (!r.ok) throw new Error('create failed');
            location.reload();
        }).catch(() => {
            addStatusEl.textContent = '추가하지 못했어요. 잠시 후 다시 시도해주세요.';
            addStatusEl.style.display = 'block';
            this.disabled = false;
        });
    });
})();
