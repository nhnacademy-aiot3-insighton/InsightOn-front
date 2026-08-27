(function () {
    // 카드 목록이 타입별로 여러 .actuator-grid로 나뉘어 있어서, 그걸 다 감싸는 wrapper를 기준으로 찾음
    const grid = document.getElementById('actuatorCardsSection');
    if (!grid) return;

    const BASE_URL = `/locations/${ACTUATOR_INIT.locationId}/actuators`;

    function actuatorIdOf(el) {
        return el.closest('.actuator-card').dataset.actuatorId;
    }

    function sendState(actuatorId, state) {
        return fetch(`${BASE_URL}/${actuatorId}/state`, {
            method: 'PUT',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(state)
        }).then((r) => {
            if (!r.ok) throw new Error('command failed');
        });
    }

    // core는 넘어온 상태를 병합하지 않고 통째로 교체하므로, 바뀐 값 하나만 보내면
    // 나머지(전원/모드/온도)가 사라짐. 매번 카드에 표시된 전체 상태를 모아서 함께 보냄
    function buildFullState(card) {
        const state = {};
        // 전원
        const toggle = card.querySelector('.cmd-toggle');
        if (toggle) state[toggle.dataset.command] = toggle.checked ? 'ON' : 'OFF';

        // 모드 - 활성 칩이 있을 때만
        const chipGroup = card.querySelector('.chip-select');
        if (chipGroup) {
            const active = chipGroup.querySelector('.cmd-chip.active');
            if (active) state[chipGroup.dataset.command] = active.dataset.value;
        }

        // 온도 에어컨만 있는 위젯
        const stepper = card.querySelector('.temp-stepper');
        // 액추에이터 생성 시 서버가 항상 온도 기본값을 채워주므로 currentState에 항상 값이 있음
        if (stepper) state[stepper.dataset.command] = Number(stepper.querySelector('.cmd-range-value').textContent);
        return state;
    }

    // ---------- 전원 토글 ----------
    grid.querySelectorAll('.cmd-toggle').forEach((toggle) => {
        toggle.addEventListener('change', () => {
            const card = toggle.closest('.actuator-card');
            const actuatorId = actuatorIdOf(toggle);
            const state = buildFullState(card); // 카드에 있는 모든값을 다보냄
            state[toggle.dataset.command] = toggle.checked ? 'ON' : 'OFF';
            sendState(actuatorId, state).catch(() => {
                toggle.checked = !toggle.checked;
                // 전원 토글 실패시
                alert('전원 조작에 실패했어요. 잠시 후 다시 시도해주세요.');
            });
        });
    });

    // ---------- 모드 선택(chip) ----------
    grid.querySelectorAll('.chip-select').forEach((chipGroup) => {
        chipGroup.querySelectorAll('.cmd-chip').forEach((chip) => {
            chip.addEventListener('click', () => {
                const card = chip.closest('.actuator-card');
                const actuatorId = actuatorIdOf(chip);
                const previousActive = chipGroup.querySelector('.cmd-chip.active');
                const toggle = card.querySelector('.cmd-toggle');
                const wasChecked = toggle ? toggle.checked : null;

                chipGroup.querySelectorAll('.cmd-chip').forEach((c) => c.classList.remove('active'));
                chip.classList.add('active');
                if (toggle) toggle.checked = true; // 모드를 선택하면 전원도 자동으로 켜짐

                const state = buildFullState(card); // 반영된 모든카드를 다보냄
                sendState(actuatorId, state).catch(() => {
                    chip.classList.remove('active');
                    if (previousActive) previousActive.classList.add('active');
                    if (toggle) toggle.checked = wasChecked;
                    // 모드 조작시 자동 전원 ON 실패시
                    alert('모드 조작에 실패했어요. 잠시 후 다시 시도해주세요.');
                });
            });
        });
    });

    // ---------- 숫자 범위(온도 등) ----------
    grid.querySelectorAll('.temp-stepper').forEach((stepper) => {
        const card = stepper.closest('.actuator-card');
        const valueEl = stepper.querySelector('.cmd-range-value');
        const min = Number(stepper.dataset.min);
        const max = Number(stepper.dataset.max);

        function step(delta) {
            const actuatorId = actuatorIdOf(stepper);
            const current = Number(valueEl.textContent);
            const next = Math.min(max, Math.max(min, current + delta));
            if (next === current) return;

            const toggle = card.querySelector('.cmd-toggle');
            const wasChecked = toggle ? toggle.checked : null;

            valueEl.textContent = next;
            if (toggle) toggle.checked = true; // 온도를 조작하면 전원 ON — 모드는 그대로 둠

            const state = buildFullState(card);
            sendState(actuatorId, state).catch(() => {
                valueEl.textContent = current;
                if (toggle) toggle.checked = wasChecked;
                alert('온도 조작에 실패했어요. 잠시 후 다시 시도해주세요.');
            });
        }

        stepper.querySelector('.cmd-step-down').addEventListener('click', () => step(-1));
        stepper.querySelector('.cmd-step-up').addEventListener('click', () => step(1));
    });

    // ---------- 일괄 전원 on/off  ----------
    function SetAllPower(cards, power) {
        cards.forEach((card) => {
            const toggle = card.querySelector('.cmd-toggle');
            if (!toggle) return;
            const actuatorId = actuatorIdOf(toggle);
            const wasChecked = toggle.checked;
            toggle.checked = power === 'ON';
            const state = buildFullState(card);
            sendState(actuatorId, state).catch(() => {
                toggle.checked = wasChecked;
                alert('일부 액추에이터 조작에 실패했어요. 잠시 후 다시 시도해주세요.');
            });
        });
    }

    const allCards = Array.from(grid.querySelectorAll('.actuator-card'));
    const btnAllPowerOn = document.getElementById('btnAllPowerOn');
    const btnAllPowerOff = document.getElementById('btnAllPowerOff');

    // 전체 켜기/끄기는 모든 카드뿐 아니라 타입별 일괄 조작 박스의 전원 토글 표시도 같이 맞춤
    function syncTypeBulkPowerToggles(power) {
        document.querySelectorAll('.type-bulk-power').forEach((toggle) => {
            toggle.checked = power === 'ON';
        });
    }

    if (btnAllPowerOn) btnAllPowerOn.addEventListener('click', () => {
        SetAllPower(allCards, 'ON');
        syncTypeBulkPowerToggles('ON');
    });
    if (btnAllPowerOff) btnAllPowerOff.addEventListener('click', () => {
        SetAllPower(allCards, 'OFF');
        syncTypeBulkPowerToggles('OFF');
    });

    // ---------- 타입별 일괄 모드/온도 조작 ----------
    document.querySelectorAll('[data-type-bulk-box]').forEach((box) => {
        const type = box.dataset.typeBulkBox;
        const cardsOfType = () => allCards.filter((card) => card.dataset.actuatorType === type);

        const typeBulkPower = box.querySelector('.type-bulk-power');
        if (typeBulkPower) {
            typeBulkPower.addEventListener('change', () => {
                SetAllPower(cardsOfType(), typeBulkPower.checked ? 'ON' : 'OFF');
            });
        }

        box.querySelectorAll('.type-bulk-mode').forEach((btn) => {
            btn.addEventListener('click', () => {
                // 모드를 고르면 전원도 자동으로 켜짐 — 박스 자체의 전원 토글 표시도 같이 맞춤
                if (typeBulkPower) typeBulkPower.checked = true;
                cardsOfType().forEach((card) => {
                    const chipGroup = card.querySelector('.chip-select');
                    const toggle = card.querySelector('.cmd-toggle');
                    if (chipGroup) {
                        chipGroup.querySelectorAll('.cmd-chip').forEach((c) => c.classList.remove('active'));
                        const match = Array.from(chipGroup.querySelectorAll('.cmd-chip'))
                            .find((c) => c.dataset.value === btn.dataset.value);
                        if (match) match.classList.add('active');
                    }
                    if (toggle) toggle.checked = true;
                    sendState(card.dataset.actuatorId, buildFullState(card))
                        .catch(() => alert('일부 액추에이터 모드 변경에 실패했어요.'));
                });
            });
        });

        const tempStepper = box.querySelector('.temp-stepper');
        if (tempStepper) {
            const valueEl = tempStepper.querySelector('.type-bulk-temp-value');
            const min = Number(tempStepper.dataset.min);
            const max = Number(tempStepper.dataset.max);

            tempStepper.querySelector('.cmd-step-down').addEventListener('click', () => {
                valueEl.textContent = Math.max(min, Number(valueEl.textContent) - 1);
            });
            tempStepper.querySelector('.cmd-step-up').addEventListener('click', () => {
                valueEl.textContent = Math.min(max, Number(valueEl.textContent) + 1);
            });
            box.querySelector('.type-bulk-temp-apply').addEventListener('click', () => {
                const target = Number(valueEl.textContent);
                // 온도를 적용하면 전원도 자동으로 켜짐 — 박스 자체의 전원 토글 표시도 같이 맞춤
                if (typeBulkPower) typeBulkPower.checked = true;
                cardsOfType().forEach((card) => {
                    const cardStepperValue = card.querySelector('.temp-stepper .cmd-range-value');
                    const toggle = card.querySelector('.cmd-toggle');
                    if (!cardStepperValue) return; // 이 타입엔 온도가 없음
                    cardStepperValue.textContent = target;
                    if (toggle) toggle.checked = true; // 온도를 조작하면 전원 ON — 모드는 그대로 둠
                    sendState(card.dataset.actuatorId, buildFullState(card))
                        .catch(() => alert('일부 액추에이터 온도 변경에 실패했어요.'));
                });
            });
        }
    });

    const bulkCheckboxes = Array.from(grid.querySelectorAll('.actuator-select'));
    if (bulkCheckboxes.length > 0) {
        const bulkCount = document.getElementById('actuatorBulkCount');
        const btnBulkDelete = document.getElementById('btnBulkDelete');

        function selectedCards() {
            return bulkCheckboxes.filter((cb) => cb.checked).map((cb) => cb.closest('.actuator-card'));
        }

        // 바 자체는 항상 떠 있게 두고 버튼만 켜고 끔
        function refreshBulkBar() {
            const n = selectedCards().length;
            bulkCount.textContent = n + '개 선택됨';
            btnBulkDelete.disabled = n === 0;
        }

        bulkCheckboxes.forEach((cb) => cb.addEventListener('change', refreshBulkBar));

        btnBulkDelete.addEventListener('click', () => {
            const cards = selectedCards();
            if (cards.length === 0) return;
            if (!confirm(`선택한 액추에이터 ${cards.length}개를 삭제할까요?`)) return;
            // 일부만 실패해도 성공한 건 이미 지워졌으니, 결과와 상관없이 새로고침해서 실제 상태를 보여줌
            Promise.allSettled(cards.map((card) =>
                fetch(`${BASE_URL}/${card.dataset.actuatorId}`, {method: 'DELETE'})
            )).then((results) => {
                const failed = results.some((r) => r.status === 'rejected' || !r.value.ok);
                if (failed) alert('일부 삭제하지 못했어요.');
                location.reload();
            });
        });
    }

    // ---------- 실행 이력 ----------
    const logsModalEl = document.getElementById('actuatorLogsModal');
    if (logsModalEl) {
        const logsModal = new bootstrap.Modal(logsModalEl);
        const logsTitle = document.getElementById('actuatorLogsTitle');
        const logsBody = document.getElementById('actuatorLogsBody');
        const logsEmpty = document.getElementById('actuatorLogsEmpty');
        const logsPageInfo = document.getElementById('actuatorLogsPageInfo');
        const prevBtn = document.getElementById('actuatorLogsPrev');
        const nextBtn = document.getElementById('actuatorLogsNext');
        const EXECUTED_BY_LABEL = {USER: '사용자', AI_SYSTEM: 'AI', RULE_ENGINE: '규칙 엔진'};

        let logsActuatorId = null;
        let logsPage = 0;

        function loadLogs(page) {
            logsPage = page;
            fetch(`${BASE_URL}/${logsActuatorId}/logs?page=${page}&size=20`)
                .then((r) => {
                    if (!r.ok) throw new Error('load failed');
                    return r.json();
                })
                .then((data) => {
                    logsBody.innerHTML = '';
                    logsEmpty.style.display = data.content.length === 0 ? 'block' : 'none';
                    data.content.forEach((log) => {
                        const tr = document.createElement('tr');
                        const executedAt = log.executedAt ? new Date(log.executedAt).toLocaleString() : '-';
                        tr.innerHTML = `<td>${executedAt}</td><td>${log.commandType}</td><td>${log.commandValue}</td><td>${EXECUTED_BY_LABEL[log.executedByType] || log.executedByType}</td>`;
                        logsBody.appendChild(tr);
                    });
                    logsPageInfo.textContent = `${data.number + 1} / ${Math.max(data.totalPages, 1)}`;
                    prevBtn.disabled = data.first;
                    nextBtn.disabled = data.last;
                })
                .catch(() => {
                    logsBody.innerHTML = '';
                    logsEmpty.textContent = '이력을 불러오지 못했어요.';
                    logsEmpty.style.display = 'block';
                });
        }

        grid.querySelectorAll('.btn-view-logs').forEach((btn) => {
            btn.addEventListener('click', (e) => {
                e.preventDefault();
                const card = btn.closest('.actuator-card');
                logsActuatorId = actuatorIdOf(btn);
                logsTitle.textContent = card.querySelector('.actuator-name').textContent + ' 실행 이력';
                loadLogs(0);
                logsModal.show();
            });
        });

        prevBtn.addEventListener('click', () => { if (logsPage > 0) loadLogs(logsPage - 1); });
        nextBtn.addEventListener('click', () => loadLogs(logsPage + 1));
    }

    // ---------- 수정(이름 변경) ----------
    const editModalEl = document.getElementById('editActuatorModal');
    if (editModalEl) {
        const editModal = new bootstrap.Modal(editModalEl);
        const editNameInput = document.getElementById('editActuatorName');
        const editStatusEl = document.getElementById('editActuatorStatus');
        let editActuatorId = null;

        grid.querySelectorAll('.btn-edit-actuator').forEach((btn) => {
            btn.addEventListener('click', (e) => {
                e.preventDefault();
                const card = btn.closest('.actuator-card');
                editActuatorId = actuatorIdOf(btn);
                editNameInput.value = card.querySelector('.actuator-name').textContent;
                editStatusEl.style.display = 'none';
                editModal.show();
            });
        });

        document.getElementById('btnSaveActuatorName').addEventListener('click', function () {
            const name = editNameInput.value.trim();
            if (!name) {
                editStatusEl.textContent = '이름을 입력하세요.';
                editStatusEl.style.display = 'block';
                return;
            }
            this.disabled = true;
            fetch(`${BASE_URL}/${editActuatorId}/name`, {
                method: 'PUT',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({sensorName: name})
            }).then((r) => {
                if (!r.ok) throw new Error('rename failed');
                location.reload();
            }).catch(() => {
                editStatusEl.textContent = '수정하지 못했어요. 잠시 후 다시 시도해주세요.';
                editStatusEl.style.display = 'block';
                this.disabled = false;
            });
        });
    }

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
        // core에는 이름 중복 체크가 없어서, 지금 이 위치에 이미 있는 이름과 겹치는지 화면에서 먼저 걸러냄
        const isDuplicate = Array.from(grid.querySelectorAll('.actuator-name'))
            .some((el) => el.textContent.trim().toLowerCase() === name.toLowerCase());
        if (isDuplicate) {
            addStatusEl.textContent = '이미 같은 이름의 액추에이터가 있어요.';
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
