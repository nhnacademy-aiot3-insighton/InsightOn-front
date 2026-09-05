/**
 * Flow 생성/수정 화면.
 * Rule Engine 검증 계약에 맞춰 Trigger 1개 → THRESHOLD 0..N개 → EVENT_GATE 0..1개
 * → Action 1개 이상을 팬아웃으로 연결한다. 전원 켜기와 온도를 함께 설정하면 한 카드가 Action 2개가 된다.
 * 저장 전 화면 상태를 Node/Link 목록으로 변환한다.
 */
(function () {
    const init = window.FLOW_EDITOR_INIT || {};
    const mode = init.mode;
    const flow = init.flow;
    const presetLocationId = init.presetLocationId;
    const sensors = init.sensors || [];
    const actuatorCommandRules = init.actuatorCommandRules || {};
    const editorCore = window.FlowEditorCore;
    if (!editorCore) throw new Error('Flow 편집기 핵심 모듈을 불러오지 못했습니다.');
    const defaultRequiredCount = 3;
    const defaultCountWindowMinutes = 5;
    const defaultCooldownMinutes = 30;
    const secondsPerMinute = 60;
    // 대시보드 액추에이터 조작 화면(actuator-panel.js)·제안 로그(SuggestionLogViewService)와 같은 한글 표기를 쓴다.
    const actuatorTypeLabels = {AIRCON: '에어컨', AIR_PURIFIER: '공기청정기', VENTILATION_FAN: '환풍기'};
    const allActuatorTypes = Object.keys(actuatorTypeLabels);
    const commandLabels = {POWER_STATUS: '전원', OPERATION_MODE: '모드', SET_TEMPERATURE: '온도'};
    const commandValueLabels = {
        ON: '켜기', OFF: '끄기',
        COOL: '냉방', DRY: '제습', FAN: '송풍', AUTO: '자동',
        SLEEP: '취침', TURBO: '터보',
        LOW: '약', MID: '중', HIGH: '강'
    };

    const locationSelect = document.getElementById('flowLocationId');
    const pathList = document.getElementById('flowPathList');
    const pathTemplate = document.getElementById('flowPathTemplate');
    const errorEl = document.getElementById('editorError');
    const saveBtn = document.getElementById('btnSaveFlow');
    const attributeCache = new Map();
    const weekdayLabels = ['일', '월', '화', '수', '목', '금', '토'];
    const locationMetrics = [
        {metricKey: 'temperature', displayName: '온도', unit: '°C'},
        {metricKey: 'humidity', displayName: '습도', unit: '%'},
        {metricKey: 'co2', displayName: 'CO₂', unit: 'ppm'},
        {metricKey: 'illuminance', displayName: '조도', unit: 'lx'}
    ];
    let metricRequestSequence = 0;

    function showError(message, kind) {
        errorEl.textContent = message;
        errorEl.dataset.errorKind = kind || '';
        errorEl.style.display = 'block';
        errorEl.scrollIntoView({behavior: 'smooth', block: 'center'});
    }

    function clearError() {
        delete errorEl.dataset.errorKind;
        errorEl.style.display = 'none';
    }

    function showActivationStatusUnknown(flowId) {
        errorEl.replaceChildren();
        errorEl.append('새 버전은 저장됐지만 활성화 완료 여부를 확인하지 못했어요. ');
        const detailLink = document.createElement('a');
        detailLink.href = `/my-group/flows/${flowId}`;
        detailLink.textContent = '저장된 버전의 현재 상태 확인하기';
        errorEl.appendChild(detailLink);
        errorEl.style.display = 'block';
        errorEl.scrollIntoView({behavior: 'smooth', block: 'center'});
        const label = saveBtn.querySelector('span');
        if (label) label.textContent = '새 버전 저장 완료';
    }

    function showActivationFailure(flowId) {
        errorEl.replaceChildren();
        errorEl.append('새 버전은 저장됐지만 활성화하지 못했어요. 현재 비활성 상태입니다. ');
        const detailLink = document.createElement('a');
        detailLink.href = `/my-group/flows/${flowId}`;
        detailLink.textContent = '저장된 버전에서 다시 활성화하기';
        errorEl.appendChild(detailLink);
        errorEl.style.display = 'block';
        errorEl.scrollIntoView({behavior: 'smooth', block: 'center'});
        const label = saveBtn.querySelector('span');
        if (label) label.textContent = '새 버전 저장 완료';
    }

    function createOption(value, label) {
        const option = document.createElement('option');
        option.value = String(value);
        option.textContent = label;
        return option;
    }

    function cronToSummary(cron) {
        const parsed = editorCore.parseCron(cron);
        if (!parsed) return null;
        const time = `${String(parsed.hour).padStart(2, '0')}:${String(parsed.minute).padStart(2, '0')}`;
        if (parsed.repeatType === 'WEEKLY') {
            const days = parsed.weekdays.slice().sort().map((value) => weekdayLabels[value]).join(', ');
            return days ? `매주 ${days}요일 ${time}에 시작합니다.` : '요일을 선택해주세요.';
        }
        if (parsed.repeatType === 'MONTHLY') {
            return `매월 ${parsed.day}일 ${time}에 시작합니다.`;
        }
        return `매일 ${time}에 시작합니다.`;
    }

    function populateScheduleDaySelect(select) {
        for (let day = 1; day <= 31; day++) {
            select.appendChild(createOption(day, `${day}일`));
        }
    }

    function readSchedule(path) {
        const repeatType = path.querySelector('.path-schedule-repeat').value;
        const timeParts = path.querySelector('.path-schedule-time').value.split(':');
        const hour = timeParts.length === 2 && timeParts[0] !== '' ? Number(timeParts[0]) : Number.NaN;
        const minute = timeParts.length === 2 && timeParts[1] !== '' ? Number(timeParts[1]) : Number.NaN;
        const weekdays = Array.from(path.querySelectorAll('.path-schedule-weekday:checked')).map((box) => Number(box.value));
        const day = Number(path.querySelector('.path-schedule-day').value);
        return {repeatType, hour, minute, weekdays, day};
    }

    function updateScheduleSummary(path) {
        const schedule = readSchedule(path);
        path.querySelector('.path-schedule-weekday-field').style.display = schedule.repeatType === 'WEEKLY' ? '' : 'none';
        path.querySelector('.path-schedule-day-field').style.display = schedule.repeatType === 'MONTHLY' ? '' : 'none';
        const summary = path.querySelector('.path-schedule-summary');
        if (!schedule.repeatType || !Number.isInteger(schedule.hour) || !Number.isInteger(schedule.minute)) {
            summary.textContent = '반복 주기와 시간을 선택해주세요.';
            return;
        }
        if (schedule.repeatType === 'WEEKLY' && !schedule.weekdays.length) {
            summary.textContent = '반복할 요일을 선택해주세요.';
            return;
        }
        summary.textContent = cronToSummary(editorCore.buildCron(schedule));
    }

    function applyScheduleFromCron(path, cron, existingSchedule) {
        const daySelect = path.querySelector('.path-schedule-day');
        if (!daySelect.options.length) populateScheduleDaySelect(daySelect);

        const parsed = cron ? editorCore.parseCron(cron) : null;
        const invalidExistingSchedule = existingSchedule && !parsed;
        const schedule = parsed || {repeatType: 'DAILY', hour: 9, minute: 0, day: 1, weekdays: []};
        path.querySelector('.path-schedule-repeat').value = invalidExistingSchedule ? '' : schedule.repeatType;
        path.querySelector('.path-schedule-time').value = invalidExistingSchedule
            ? ''
            : `${String(schedule.hour).padStart(2, '0')}:${String(schedule.minute).padStart(2, '0')}`;
        path.querySelectorAll('.path-schedule-weekday').forEach((box) => {
            box.checked = !invalidExistingSchedule && schedule.weekdays.includes(Number(box.value));
        });
        daySelect.value = String(schedule.day || 1);
        updateScheduleSummary(path);
        const invalidNotice = path.querySelector('.path-schedule-invalid');
        if (invalidExistingSchedule) {
            path.dataset.scheduleInvalid = 'true';
            invalidNotice.hidden = false;
            return true;
        }
        delete path.dataset.scheduleInvalid;
        invalidNotice.hidden = true;
        return false;
    }

    function acceptScheduleFormInput(path) {
        if (path.dataset.scheduleInvalid !== 'true') return;
        delete path.dataset.scheduleInvalid;
        path.querySelector('.path-schedule-invalid').hidden = true;
        if (errorEl.dataset.errorKind === 'schedule') clearError();
    }

    function secondsToMinutes(seconds, fallbackMinutes) {
        if (seconds == null || seconds === '') return fallbackMinutes;
        const value = Number(seconds);
        return Number.isFinite(value)
            ? Math.round((value / secondsPerMinute) * 100) / 100
            : fallbackMinutes;
    }

    function refreshSensorSelect(select, selectedValue) {
        const locationId = Number(locationSelect.value);
        select.replaceChildren();
        sensors
            .filter((sensor) => Number(sensor.locationId) === locationId)
            .forEach((sensor) => select.appendChild(createOption(sensor.sensorId, sensor.sensorName)));
        // selectedValue가 빈 문자열이면(이전 위치에 센서가 없어 선택된 게 없던 경우) 되돌리지 않고
        // 새로 채워진 옵션의 기본 선택(첫 번째 센서)을 그대로 둔다.
        if (selectedValue) select.value = String(selectedValue);
    }

    function refreshAllSensorOptions() {
        pathList.querySelectorAll('.flow-path').forEach((path) => {
            const select = path.querySelector('.path-trigger-sensor');
            const selectedValue = select.value;
            refreshSensorSelect(select, selectedValue);
            refreshPathConditionMetrics(path);
        });
    }

    function updateLocationTriggerLabel() {
        const selectedOption = locationSelect.options[locationSelect.selectedIndex];
        const locationName = selectedOption ? selectedOption.textContent.trim() : '';
        const label = locationName ? `${locationName} 전체` : '위치 전체';
        pathList.querySelectorAll('.path-trigger-type option[value="LOCATION"]').forEach((option) => {
            option.textContent = label;
        });
    }

    function refreshConditionList(list) {
        list.querySelectorAll('.condition-and-label, .flow-condition-empty').forEach((element) => element.remove());
        const rows = Array.from(list.querySelectorAll('.condition-row'));
        if (!rows.length) {
            const empty = document.createElement('p');
            empty.className = 'flow-condition-empty';
            empty.innerHTML = '<i class="ti ti-alert-triangle"></i> 조건이 없으면 측정값이 들어올 때마다 확인해요. 보통은 조건을 하나 이상 추가하는 것이 안전합니다.';
            list.appendChild(empty);
            return;
        }
        rows.forEach((row, index) => {
            row.querySelector('.condition-row-label').textContent = `조건 ${index + 1}`;
            if (index === 0) return;
            const label = document.createElement('div');
            label.className = 'condition-and-label';
            label.textContent = 'AND';
            list.insertBefore(label, row);
        });
    }

    function parseExpression(expression) {
        const match = String(expression || '').match(/^#metrics\[['"]([^'"]+)['"]\]\s*(>=|<=|==|!=|>|<)\s*(-?\d+(?:\.\d+)?)$/);
        return match ? {metricKey: match[1], operator: match[2], value: match[3]} : null;
    }

    function loadSensorAttributes(sensorId) {
        if (!sensorId) return Promise.reject(new Error('센서를 먼저 선택해주세요.'));
        if (attributeCache.has(sensorId)) return attributeCache.get(sensorId);
        const request = fetch(`/my-group/flows/sensors/${sensorId}/attributes`)
            .then((response) => {
                if (!response.ok) throw new Error('측정 항목을 불러오지 못했어요.');
                return response.json();
            })
            .then((attributes) => {
                if (!Array.isArray(attributes) || !attributes.length) {
                    throw new Error('이 센서에 등록된 측정 항목이 없어요.');
                }
                return attributes;
            })
            .catch((error) => {
                attributeCache.delete(sensorId);
                throw error;
            });
        attributeCache.set(sensorId, request);
        return request;
    }

    function loadLocationAttributes(locationId) {
        const sensorIds = sensors
            .filter((sensor) => Number(sensor.locationId) === locationId)
            .map((sensor) => sensor.sensorId);
        if (!sensorIds.length) {
            return Promise.reject(new Error('이 위치에 등록된 센서가 없어요.'));
        }
        // 센서마다 실제로 지원하는 측정 항목만 모아서, 위치에 없는 항목(예: CO₂ 센서가 없는데 CO₂ 조건)을 고를 수 없게 한다.
        return Promise.all(sensorIds.map((id) => loadSensorAttributes(id).catch(() => [])))
            .then((attributeLists) => {
                const merged = new Map();
                attributeLists.flat().forEach((attribute) => {
                    if (!merged.has(attribute.metricKey)) merged.set(attribute.metricKey, attribute);
                });
                const attributes = Array.from(merged.values());
                if (!attributes.length) {
                    throw new Error('이 위치의 센서에 등록된 측정 항목이 없어요.');
                }
                return attributes;
            });
    }

    function populateMetricSelect(select, attributes, selectedMetricKey) {
        select.replaceChildren();
        attributes.forEach((attribute) => {
            const label = attribute.unit
                ? `${attribute.displayName} (${attribute.unit})`
                : attribute.displayName;
            select.appendChild(createOption(attribute.metricKey, label));
        });
        if (selectedMetricKey && !attributes.some((attribute) => attribute.metricKey === selectedMetricKey)) {
            const known = locationMetrics.find((metric) => metric.metricKey === selectedMetricKey);
            const label = known
                ? `현재 설정: ${known.displayName} (이 위치에서 사용 불가)`
                : `현재 설정: ${selectedMetricKey}`;
            select.appendChild(createOption(selectedMetricKey, label));
        }
        if (selectedMetricKey) select.value = selectedMetricKey;
        select.disabled = false;
    }

    function refreshPathConditionMetrics(path) {
        const triggerType = path.querySelector('.path-trigger-type').value;
        const sensorSelect = path.querySelector('.path-trigger-sensor');
        const sensorId = triggerType === 'SENSOR' ? sensorSelect.value : null;
        const requestId = String(++metricRequestSequence);
        path.dataset.metricRequestId = requestId;
        path.dataset.metricsReady = 'false';
        const status = path.querySelector('.flow-metric-status');

        if (triggerType === 'SENSOR' && !sensorSelect.options.length) {
            path.querySelectorAll('.condition-metric').forEach((select) => {
                select.replaceChildren(createOption('', '측정 항목을 불러오지 못함'));
                select.disabled = true;
            });
            status.textContent = '이 위치에는 등록된 센서가 없어요. 다른 위치를 선택하거나 먼저 센서를 등록해주세요.';
            return Promise.resolve();
        }

        status.textContent = triggerType === 'SCHEDULE' ? '위치 전체의 공통 측정 항목을 사용합니다.' : '측정 항목을 불러오는 중...';

        path.querySelectorAll('.condition-metric').forEach((select) => {
            if (!select.dataset.selectedMetricKey) select.dataset.selectedMetricKey = select.value;
            select.replaceChildren(createOption('', '불러오는 중...'));
            select.disabled = true;
        });

        const attributesRequest = triggerType === 'LOCATION'
            ? loadLocationAttributes(Number(locationSelect.value))
            : triggerType === 'SCHEDULE'
                ? Promise.resolve(locationMetrics)
                : loadSensorAttributes(sensorId);
        return attributesRequest.then((attributes) => {
            if (path.dataset.metricRequestId !== requestId) return;
            path.querySelectorAll('.condition-row').forEach((row, index) => {
                const select = row.querySelector('.condition-metric');
                const selected = select.dataset.selectedMetricKey || select.value;
                populateMetricSelect(select, attributes, selected);
                delete select.dataset.selectedMetricKey;
            });
            path.dataset.metricsReady = 'true';
            status.textContent = triggerType === 'SCHEDULE'
                ? '온도·습도·CO₂·조도 공통 항목을 사용합니다.'
                : `${attributes.length}개 측정 항목을 불러왔습니다.`;
        }).catch((error) => {
            if (path.dataset.metricRequestId !== requestId) return;
            path.querySelectorAll('.condition-metric').forEach((select) => {
                select.replaceChildren(createOption('', '측정 항목을 불러오지 못함'));
                select.disabled = true;
            });
            const retryHint = triggerType === 'LOCATION' ? '위치를' : '센서를';
            status.textContent = `${error.message} ${retryHint} 다시 선택하면 재시도합니다.`;
        });
    }

    function addCondition(path, expression) {
        const list = path.querySelector('.path-condition-list');
        const parsed = parseExpression(expression);
        const row = document.createElement('div');
        row.className = 'condition-row';
        row.innerHTML =
            '<span class="condition-row-label"></span>' +
            '<select class="condition-metric form-select" aria-label="측정 항목"></select>' +
            '<select class="condition-operator form-select" aria-label="비교 연산자">' +
            '<option value=">">초과 (&gt;)</option><option value=">=">이상 (≥)</option>' +
            '<option value="<">미만 (&lt;)</option><option value="<=">이하 (≤)</option>' +
            '<option value="==">같음 (=)</option><option value="!=">다름 (≠)</option></select>' +
            '<input type="number" step="any" class="condition-value form-control" aria-label="기준값" placeholder="기준값">' +
            '<button type="button" class="btn-remove-condition" aria-label="조건 삭제"><i class="ti ti-x"></i></button>';
        row.querySelector('.condition-operator').value = parsed ? parsed.operator : '>';
        row.querySelector('.condition-value').value = parsed ? parsed.value : '';
        list.appendChild(row);
        refreshConditionList(list);
        if (parsed) row.querySelector('.condition-metric').dataset.selectedMetricKey = parsed.metricKey;
        refreshPathConditionMetrics(path);
    }

    function updateGateFields(path) {
        const enabled = path.querySelector('.path-gate-enabled').checked;
        const requiredCountValue = path.querySelector('.path-gate-required-count').value.trim();
        const requiredCount = Number(requiredCountValue);
        path.querySelector('.path-gate-fields').style.display = enabled ? '' : 'none';
        path.querySelector('.path-gate-window-field').style.display = enabled && requiredCount >= 2 ? '' : 'none';

        const summary = path.querySelector('.path-gate-summary');
        if (!enabled) {
            summary.textContent = '조건을 만족할 때마다 바로 실행합니다.';
            return;
        }
        if (!requiredCountValue || !Number.isInteger(requiredCount) || requiredCount < 1) {
            summary.textContent = '확인 횟수를 1 이상의 정수로 입력해주세요.';
            return;
        }
        const windowMinutes = path.querySelector('.path-gate-window').value.trim();
        if (requiredCount >= 2 && !windowMinutes) {
            summary.textContent = '여러 번 확인할 시간을 입력해주세요.';
            return;
        }
        const cooldownValue = path.querySelector('.path-gate-cooldown').value.trim();
        if (!cooldownValue) {
            summary.textContent = '최소 실행 간격을 입력해주세요. 제한하지 않으려면 0을 입력해주세요.';
            return;
        }
        const cooldownMinutes = Number(cooldownValue);
        const countSummary = requiredCount >= 2
            ? `${windowMinutes}분 안에 ${requiredCount}번 확인한 뒤 실행합니다.`
            : '한 번 확인하면 실행합니다.';
        const cooldownSummary = cooldownMinutes > 0
            ? ` 안전장치를 통과한 뒤 ${cooldownMinutes}분 동안 다시 실행을 시도하지 않습니다.`
            : '';
        summary.textContent = countSummary + cooldownSummary;
    }

    function configureGate(path, gate, isNewFlow) {
        const gateType = gate ? gate.nodeType : null;
        const gateConfig = gate && gate.configuration ? gate.configuration : {};
        let requiredCount = defaultRequiredCount;
        let countWindowSeconds = defaultCountWindowMinutes * secondsPerMinute;
        let cooldownSeconds = defaultCooldownMinutes * secondsPerMinute;
        let enabled = isNewFlow;

        if (gateType === 'EVENT_GATE') {
            requiredCount = Number(gateConfig.requiredCount == null ? 1 : gateConfig.requiredCount);
            countWindowSeconds = Number(gateConfig.countWindowSeconds || 0);
            cooldownSeconds = Number(gateConfig.cooldownSeconds || 0);
            enabled = true;
        }

        path.querySelector('.path-gate-enabled').checked = enabled;
        const requiredCountInput = path.querySelector('.path-gate-required-count');
        requiredCountInput.max = String(editorCore.BACKEND_INTEGER_MAX);
        requiredCountInput.value = String(Math.max(1, requiredCount));
        path.querySelector('.path-gate-window').value = String(secondsToMinutes(
            countWindowSeconds,
            defaultCountWindowMinutes
        ));
        path.querySelector('.path-gate-cooldown').value = String(secondsToMinutes(cooldownSeconds, 0));
        updateGateFields(path);
    }

    // ActuatorCommandPreset.forTemplate()이 보내는 모양: {AIRCON: {POWER_STATUS: {stateKey:'power', kind:'SELECT', values:[...]}, ...}, ...}
    function findActuatorCommandWidget(actuatorType, stateKey) {
        const rules = actuatorCommandRules[actuatorType] || {};
        return Object.values(rules).find((widget) => widget.stateKey === stateKey) || null;
    }

    function populateActuatorCommandSelect(select, actuatorType, selectedStateKey) {
        const rules = actuatorCommandRules[actuatorType] || {};
        select.replaceChildren();
        Object.entries(rules).forEach(([commandType, widget]) => {
            select.appendChild(createOption(widget.stateKey, commandLabels[commandType] || commandType));
        });
        if (selectedStateKey && Array.from(select.options).some((option) => option.value === selectedStateKey)) {
            select.value = selectedStateKey;
        }
        // 드롭다운을 열어보지 않아도 이 기기에서 고를 수 있는 명령을 한눈에 볼 수 있게 한다.
        const hint = select.closest('.settings-field').querySelector('.action-actuator-command-hint');
        const labels = Object.keys(rules).map((commandType) => commandLabels[commandType] || commandType);
        hint.textContent = labels.length ? `선택 가능: ${labels.join(' · ')}` : '';
    }

    // "값" 입력은 명령마다 모양이 달라(전원=선택, 온도=범위) select/number input을 동적으로 바꿔 끼운다.
    function renderActuatorValueControl(action, selectedValue) {
        const actuatorType = action.querySelector('.action-actuator-type').value;
        const command = action.querySelector('.action-actuator-command').value;
        const widget = findActuatorCommandWidget(actuatorType, command);
        const container = action.querySelector('.action-actuator-value-control');
        container.replaceChildren();
        if (!widget) return;
        if (widget.kind === 'RANGE') {
            const input = document.createElement('input');
            input.type = 'number';
            input.className = 'form-control action-actuator-value';
            input.min = String(widget.min);
            input.max = String(widget.max);
            input.step = '1';
            input.setAttribute('aria-label', '값');
            input.value = selectedValue ? selectedValue : String(widget.min);
            container.appendChild(input);
            return;
        }
        const select = document.createElement('select');
        select.className = 'form-select action-actuator-value';
        select.setAttribute('aria-label', '값');
        (widget.values || []).forEach((value) => select.appendChild(createOption(value, commandValueLabels[value] || value)));
        if (selectedValue && (widget.values || []).includes(selectedValue)) select.value = selectedValue;
        container.appendChild(select);
    }

    function updateActuatorSummary(action) {
        const actuatorType = action.querySelector('.action-actuator-type').value;
        const command = action.querySelector('.action-actuator-command').value;
        const valueEl = action.querySelector('.action-actuator-value');
        const value = valueEl ? valueEl.value : '';
        const summary = action.querySelector('.action-actuator-summary');
        const typeLabel = actuatorTypeLabels[actuatorType] || '기기';
        if (!value) {
            summary.textContent = `${typeLabel}에 보낼 값을 선택해주세요.`;
            return;
        }
        if (command === 'temperature') {
            summary.textContent = `${typeLabel} 설정 온도를 ${value}°C로 바꿉니다.`;
            return;
        }
        summary.textContent = `${typeLabel} ${commandValueLabels[value] || value} 설정으로 바꿉니다.`;
        const tempField = action.querySelector('.action-actuator-temp-field');
        const tempInput = action.querySelector('.action-actuator-temp-value');
        if (tempField.style.display !== 'none' && tempInput.value.trim()) {
            summary.textContent += ` 온도는 ${tempInput.value.trim()}°C로 함께 바꿉니다.`;
        }
    }

    // 이 기기 타입에서 숫자 범위로 값을 받는 명령(현재는 에어컨 온도)을 찾는다.
    function findRangeCommandWidget(actuatorType) {
        const rules = actuatorCommandRules[actuatorType] || {};
        return Object.values(rules).find((widget) => widget.kind === 'RANGE') || null;
    }

    // updateActuatorState는 병합이 아니라 통째 교체라, 온도만 보내면 기존 전원 상태가 그대로 유지된다
    // (InsightOn-core UpdateActuatorStateByGroupUseCase 참고). 그래서 "전원 켜기"를 실제로 보내면서
    // 온도까지 같이 바꾸고 싶을 때는 팬아웃으로 별도 액션 노드를 하나 더 만들어야 하므로,
    // 전원=켜기를 고른 경우에만 보조 온도 입력을 보여준다.
    function updateActuatorTempFieldVisibility(action) {
        const actuatorType = action.querySelector('.action-actuator-type').value;
        const command = action.querySelector('.action-actuator-command').value;
        const valueEl = action.querySelector('.action-actuator-value');
        const tempField = action.querySelector('.action-actuator-temp-field');
        const tempWidget = command === 'power' && valueEl && valueEl.value === 'ON'
            ? findRangeCommandWidget(actuatorType)
            : null;
        tempField.style.display = tempWidget ? '' : 'none';
        if (!tempWidget) {
            tempField.querySelector('.action-actuator-temp-value').value = '';
            return;
        }
        const tempInput = tempField.querySelector('.action-actuator-temp-value');
        tempInput.min = String(tempWidget.min);
        tempInput.max = String(tempWidget.max);
        tempField.querySelector('.field-hint').textContent =
            `비워두면 온도는 바꾸지 않고 전원만 켭니다. (${tempWidget.min}~${tempWidget.max})`;
    }

    function refreshActuatorFields(action, selectedCommand, selectedValue) {
        const actuatorType = action.querySelector('.action-actuator-type').value;
        populateActuatorCommandSelect(action.querySelector('.action-actuator-command'), actuatorType, selectedCommand);
        renderActuatorValueControl(action, selectedValue);
        updateActuatorTempFieldVisibility(action);
        updateActuatorSummary(action);
    }

    function updateActionTypeVisibility(action) {
        const type = action.querySelector('.action-type').value;
        action.querySelector('.action-alert-fields').style.display = type === 'ALERT' ? '' : 'none';
        action.querySelector('.action-actuator-fields').style.display = type === 'ACTUATOR_CONTROL' ? '' : 'none';
        action.querySelector('.flow-action-number').textContent = type === 'ACTUATOR_CONTROL' ? '제어할 기기' : '보낼 알림';
    }

    function addAction(path, action) {
        const actionData = action || {};
        const actionType = actionData.nodeType === 'ACTUATOR_CONTROL' ? 'ACTUATOR_CONTROL' : 'ALERT';
        const config = actionData.configuration || {};
        const item = document.createElement('div');
        item.className = 'flow-action-item';
        item.innerHTML = `
            <div class="flow-action-item-heading">
                <div class="flow-action-item-heading-label">
                    <span class="flow-action-number"></span>
                    <span class="status-badge neutral action-required-badge">필수</span>
                </div>
                <div class="flow-action-item-heading-controls">
                    <button type="button" class="btn-remove-action" aria-label="기기 제어 삭제" style="display:none;"><i class="ti ti-x"></i></button>
                </div>
            </div>
            <div class="settings-field action-type-field">
                <label>동작 종류</label>
                <select class="form-select action-type" aria-label="동작 종류">
                    <option value="ALERT">알림 보내기</option>
                    <option value="ACTUATOR_CONTROL">기기 제어</option>
                </select>
            </div>
            <div class="action-alert-fields">
                <div class="flow-action-grid">
                    <div class="settings-field">
                        <label>알림 제목</label>
                        <input type="text" class="form-control action-title" maxlength="200" aria-label="알림 제목" placeholder="예: 고온 경고">
                    </div>
                    <div class="settings-field">
                        <label>중요도</label>
                        <select class="form-select action-severity" aria-label="알림 중요도">
                            <option value="INFO">정보</option>
                            <option value="WARNING">경고</option>
                            <option value="CRITICAL">위험</option>
                        </select>
                    </div>
                    <div class="settings-field flow-action-message">
                        <label>메시지</label>
                        <textarea class="form-control action-message" rows="2" aria-label="알림 메시지" placeholder="예: 온도가 30도를 초과했습니다."></textarea>
                    </div>
                </div>
            </div>
            <div class="action-actuator-fields">
                <div class="flow-action-grid">
                    <div class="settings-field">
                        <label>기기 종류</label>
                        <select class="form-select action-actuator-type" aria-label="기기 종류">
                            <option value="AIRCON">에어컨</option>
                            <option value="AIR_PURIFIER">공기청정기</option>
                            <option value="VENTILATION_FAN">환풍기</option>
                        </select>
                    </div>
                    <div class="settings-field">
                        <label>명령</label>
                        <select class="form-select action-actuator-command" aria-label="명령"></select>
                        <p class="field-hint action-actuator-command-hint"></p>
                    </div>
                    <div class="settings-field">
                        <label>값</label>
                        <div class="action-actuator-value-control"></div>
                    </div>
                    <div class="settings-field action-actuator-temp-field" style="display:none;">
                        <label>설정 온도 (선택)</label>
                        <input type="number" class="form-control action-actuator-temp-value" aria-label="설정 온도" placeholder="예: 24">
                        <p class="field-hint">비워두면 온도는 바꾸지 않고 전원만 켭니다.</p>
                    </div>
                </div>
                <p class="field-hint action-actuator-summary" aria-live="polite"></p>
            </div>`;

        item.querySelector('.action-type').value = actionType;
        item.querySelector('.action-title').value = config.title || '';
        item.querySelector('.action-severity').value = config.severity || 'WARNING';
        item.querySelector('.action-message').value = config.message || '';
        item.querySelector('.action-actuator-type').value = config.actuatorType || 'AIRCON';
        refreshActuatorFields(item, config.command, config.commandValue);
        if (actionData.supplementalTemperatureValue != null) {
            item.querySelector('.action-actuator-temp-value').value = String(actionData.supplementalTemperatureValue);
            updateActuatorSummary(item);
        }
        updateActionTypeVisibility(item);
        path.querySelector('.path-action-list').appendChild(item);
    }

    // 기기 제어 카드가 2개 이상이면 위→아래 순서가 곧 실행 순서다.
    // 번호/화살표/삭제·드래그 버튼을 다시 그리고, 마지막 카드가 기기 제어일 때만 추가 버튼을 보여준다.
    function refreshActionList(path) {
        const list = path.querySelector('.path-action-list');
        list.querySelectorAll('.action-order-label').forEach((el) => el.remove());
        const items = Array.from(list.querySelectorAll('.flow-action-item'));
        const multiple = items.length > 1;

        items.forEach((item, index) => {
            const type = item.querySelector('.action-type').value;
            item.querySelector('.btn-remove-action').style.display = multiple ? '' : 'none';
            item.querySelector('.action-required-badge').style.display = multiple ? 'none' : '';
            // 카드가 여러 개면 번호("기기 제어 N")로 이미 기기 제어라는 걸 알 수 있으니,
            // 중복되는 "동작 종류" 선택 줄을 접어서 카드 높이를 줄인다.
            item.querySelector('.action-type-field').style.display = multiple ? 'none' : '';
            item.querySelector('.flow-action-number').textContent = multiple
                ? `${type === 'ACTUATOR_CONTROL' ? '기기 제어' : '알림'} ${index + 1}`
                : (type === 'ACTUATOR_CONTROL' ? '제어할 기기' : '보낼 알림');
            if (index > 0) {
                const connector = document.createElement('div');
                connector.className = 'action-order-label';
                connector.innerHTML = '<i class="ti ti-arrow-down"></i> 다음 실행';
                list.insertBefore(connector, item);
            }
        });

        refreshActuatorTypeAvailability(path);

        // 마지막 카드가 기기 제어일 때만, 그리고 아직 안 쓴 기기 종류가 남아 있을 때만 추가 버튼을 보여준다.
        const addBtn = path.querySelector('.btn-add-path-action');
        const lastType = items.length ? items[items.length - 1].querySelector('.action-type').value : null;
        const hasAvailableType = items
            .filter((item) => item.querySelector('.action-type').value === 'ACTUATOR_CONTROL')
            .length < allActuatorTypes.length;
        addBtn.style.display = lastType === 'ACTUATOR_CONTROL' && hasAvailableType ? '' : 'none';
    }

    // 같은 기기 종류를 두 카드에서 동시에 고르지 못하게 막는다("에어컨 두 대 실행" 같은 의도치 않은 중복 방지).
    // 각 카드는 자기 자신이 이미 고른 값은 그대로 두고, 다른 카드가 쓰고 있는 종류만 비활성화한다.
    function refreshActuatorTypeAvailability(path) {
        const actuatorItems = Array.from(path.querySelectorAll('.flow-action-item'))
            .filter((item) => item.querySelector('.action-type').value === 'ACTUATOR_CONTROL');
        const usedTypes = actuatorItems.map((item) => item.querySelector('.action-actuator-type').value);
        actuatorItems.forEach((item) => {
            const select = item.querySelector('.action-actuator-type');
            const ownValue = select.value;
            Array.from(select.options).forEach((option) => {
                option.disabled = option.value !== ownValue && usedTypes.includes(option.value);
            });
        });
    }

    // 새 기기 제어 카드를 추가할 때, 이미 다른 카드가 쓰고 있지 않은 기기 종류를 기본값으로 고른다.
    function nextAvailableActuatorType(path) {
        const usedTypes = new Set(
            Array.from(path.querySelectorAll('.flow-action-item'))
                .filter((item) => item.querySelector('.action-type').value === 'ACTUATOR_CONTROL')
                .map((item) => item.querySelector('.action-actuator-type').value)
        );
        return allActuatorTypes.find((type) => !usedTypes.has(type)) || allActuatorTypes[0];
    }

    function updateTriggerVisibility(path) {
        const type = path.querySelector('.path-trigger-type').value;
        path.querySelector('.path-trigger-sensor-field').style.display = type === 'SENSOR' ? '' : 'none';
        path.querySelector('.path-trigger-schedule-field').style.display = type === 'SCHEDULE' ? '' : 'none';
        if (type === 'SCHEDULE') updateScheduleSummary(path);

        // 예약 시작(SCHEDULE)은 엔진 규칙상 동작 노드에 직접 연결해야 하므로 조건과 안전장치를 숨긴다.
        path.querySelector('.flow-node-filter').style.display = type === 'SCHEDULE' ? 'none' : '';
        path.querySelector('.flow-node-gate').style.display = type === 'SCHEDULE' ? 'none' : '';
        path.querySelectorAll('.flow-connector')[1].style.display = type === 'SCHEDULE' ? 'none' : '';
        path.querySelectorAll('.flow-connector')[2].style.display = type === 'SCHEDULE' ? 'none' : '';

        // 같은 이유로 동작도 기기 제어만 허용하고, 알림 보내기는 선택하지 못하게 막는다.
        setActionTypeRestricted(path, type === 'SCHEDULE');
        refreshActionList(path);

        refreshPathConditionMetrics(path);
    }

    function setActionTypeRestricted(path, restricted) {
        path.querySelectorAll('.flow-action-item').forEach((action) => {
            const select = action.querySelector('.action-type');
            const alertOption = select.querySelector('option[value="ALERT"]');
            if (restricted && alertOption) {
                if (select.value === 'ALERT') select.value = 'ACTUATOR_CONTROL';
                alertOption.remove();
                updateActionTypeVisibility(action);
            }
            if (!restricted && !alertOption) {
                select.insertBefore(createOption('ALERT', '알림 보내기'), select.firstChild);
            }
        });
    }

    function setSensorTriggerAdvanced(path, advanced) {
        const select = path.querySelector('.path-trigger-type');
        const toggle = path.querySelector('.path-trigger-advanced-toggle');
        let sensorOption = select.querySelector('option[value="SENSOR"]');
        if (advanced && !sensorOption) {
            sensorOption = createOption('SENSOR', '센서 값');
            select.insertBefore(sensorOption, select.firstChild);
        }
        if (!advanced && sensorOption) {
            if (select.value === 'SENSOR') select.value = 'LOCATION';
            sensorOption.remove();
        }
        toggle.setAttribute('aria-pressed', String(advanced));
        toggle.textContent = advanced ? '고급 설정 끄기' : '고급 설정: 특정 센서로 시작하기';
    }

    function addPath(data) {
        const path = pathTemplate.content.firstElementChild.cloneNode(true);
        pathList.appendChild(path);

        const trigger = data && data.trigger;
        const triggerType = trigger ? trigger.nodeType : 'LOCATION';
        setSensorTriggerAdvanced(path, triggerType === 'SENSOR');
        path.querySelector('.path-trigger-type').value = triggerType;
        refreshSensorSelect(
            path.querySelector('.path-trigger-sensor'),
            trigger && trigger.configuration ? trigger.configuration.sensorId : null
        );
        applyScheduleFromCron(
            path,
            trigger && trigger.configuration ? trigger.configuration.cron : null,
            Boolean(trigger && triggerType === 'SCHEDULE')
        );
        updateTriggerVisibility(path);
        updateLocationTriggerLabel();

        (data && data.filters ? data.filters : []).forEach((filter) => {
            addCondition(path, filter.configuration ? filter.configuration.expression : '');
        });
        refreshConditionList(path.querySelector('.path-condition-list'));

        const actionsData = data && data.actions && data.actions.length
            ? data.actions
            : (data && data.action
                ? [data.action]
                : [{nodeType: 'ALERT', configuration: {}}]);
        actionsData.forEach((action) => addAction(path, action));
        configureGate(path, data && data.gate ? data.gate : null, !data);
        // 동작 노드는 addAction 이후에야 존재하므로, 예약 시작 제약을 다시 적용한다.
        setActionTypeRestricted(path, triggerType === 'SCHEDULE');
        refreshActionList(path);
        return path;
    }

    locationSelect.addEventListener('change', () => {
        refreshAllSensorOptions();
        updateLocationTriggerLabel();
    });

    pathList.addEventListener('change', (event) => {
        const path = event.target.closest('.flow-path');
        if (!path) return;
        if (event.target.matches('.path-trigger-type')) updateTriggerVisibility(path);
        if (event.target.matches('.path-trigger-sensor')) refreshPathConditionMetrics(path);
        if (event.target.matches('.path-gate-enabled, .path-gate-required-count, .path-gate-window, .path-gate-cooldown')) {
            updateGateFields(path);
        }
        if (event.target.matches('.path-schedule-repeat, .path-schedule-time, .path-schedule-day, .path-schedule-weekday')) {
            acceptScheduleFormInput(path);
            updateScheduleSummary(path);
        }
        if (event.target.matches('.action-type')) {
            updateActionTypeVisibility(event.target.closest('.flow-action-item'));
            refreshActionList(path);
        }
        if (event.target.matches('.action-actuator-type')) {
            refreshActuatorFields(event.target.closest('.flow-action-item'), null, null);
            refreshActionList(path);
        }
        if (event.target.matches('.action-actuator-command')) {
            renderActuatorValueControl(event.target.closest('.flow-action-item'), null);
            updateActuatorTempFieldVisibility(event.target.closest('.flow-action-item'));
            updateActuatorSummary(event.target.closest('.flow-action-item'));
        }
        if (event.target.matches('.action-actuator-value')) {
            updateActuatorTempFieldVisibility(event.target.closest('.flow-action-item'));
            updateActuatorSummary(event.target.closest('.flow-action-item'));
        }
    });

    pathList.addEventListener('input', (event) => {
        const path = event.target.closest('.flow-path');
        if (path && event.target.matches('.path-gate-required-count, .path-gate-window, .path-gate-cooldown')) {
            updateGateFields(path);
        }
        if (event.target.matches('.action-actuator-value, .action-actuator-temp-value')) {
            updateActuatorSummary(event.target.closest('.flow-action-item'));
        }
    });

    pathList.addEventListener('click', (event) => {
        const path = event.target.closest('.flow-path');
        if (!path) return;

        if (event.target.closest('.path-trigger-advanced-toggle')) {
            const nowAdvanced = path.querySelector('.path-trigger-advanced-toggle').getAttribute('aria-pressed') !== 'true';
            setSensorTriggerAdvanced(path, nowAdvanced);
            if (nowAdvanced) path.querySelector('.path-trigger-type').value = 'SENSOR';
            updateTriggerVisibility(path);
            return;
        }
        if (event.target.closest('.btn-add-path-condition')) {
            addCondition(path);
            return;
        }
        const removeCondition = event.target.closest('.btn-remove-condition');
        if (removeCondition) {
            const list = path.querySelector('.path-condition-list');
            removeCondition.closest('.condition-row').remove();
            refreshConditionList(list);
            return;
        }
        if (event.target.closest('.btn-add-path-action')) {
            addAction(path, {
                nodeType: 'ACTUATOR_CONTROL',
                configuration: {actuatorType: nextAvailableActuatorType(path)}
            });
            refreshActionList(path);
            return;
        }
        const removeAction = event.target.closest('.btn-remove-action');
        if (removeAction) {
            removeAction.closest('.flow-action-item').remove();
            refreshActionList(path);
        }
    });

    if (mode === 'edit' && flow) {
        document.getElementById('flowName').value = flow.name || '';
        document.getElementById('flowDescription').value = flow.description || '';
        locationSelect.value = String(flow.locationId);
        locationSelect.disabled = true;

        const parsedFlow = editorCore.parseFlow(
            flow.nodes || [],
            flow.links || [],
            actuatorCommandRules
        );
        if (!parsedFlow) {
            showError('이 자동화에는 현재 화면에서 수정할 수 없는 복잡한 연결이 있습니다. 기존 내용을 보호하기 위해 저장하지 않았어요. 상세 화면에서 작동 순서를 확인해주세요.');
            addPath();
            saveBtn.disabled = true;
        } else {
            addPath(parsedFlow);
        }
    } else {
        if (presetLocationId != null) {
            locationSelect.value = String(presetLocationId);
            locationSelect.disabled = true;
        }
        addPath();
    }

    saveBtn.addEventListener('click', () => {
        clearError();
        const name = document.getElementById('flowName').value.trim();
        if (!name) return showError('이름을 입력해주세요.');
        if (!locationSelect.value) return showError('위치를 선택해주세요.');

        const nodes = [];
        const links = [];
        const paths = Array.from(pathList.querySelectorAll('.flow-path'));
        if (paths.length !== 1) return showError('자동화를 시작할 센서 또는 위치가 하나 필요합니다.');

        for (let pathIndex = 0; pathIndex < paths.length; pathIndex++) {
            const path = paths[pathIndex];
            const prefix = 'flow';
            const triggerType = path.querySelector('.path-trigger-type').value;
            const sensorSelect = path.querySelector('.path-trigger-sensor');
            if (triggerType === 'SENSOR' && !sensorSelect.value) {
                return showError('자동화를 시작할 센서를 선택해주세요.');
            }
            let schedule = null;
            if (triggerType === 'SCHEDULE') {
                if (path.dataset.scheduleInvalid === 'true') {
                    return showError('기존 예약을 그대로 저장할 수 없어요. 반복이나 시간을 다시 지정해주세요.', 'schedule');
                }
                schedule = readSchedule(path);
                if (!schedule.repeatType || !Number.isInteger(schedule.hour) || !Number.isInteger(schedule.minute)
                    || schedule.hour < 0 || schedule.hour > 23 || schedule.minute < 0 || schedule.minute > 59) {
                    return showError('예약 반복 주기와 시간을 선택해주세요.');
                }
                if (schedule.repeatType === 'WEEKLY' && !schedule.weekdays.length) {
                    return showError('예약할 요일을 하나 이상 선택해주세요.');
                }
                if (schedule.repeatType === 'MONTHLY' && !schedule.day) {
                    return showError('예약할 날짜를 선택해주세요.');
                }
            }

            const triggerKey = `${prefix}-trigger`;
            const triggerConfiguration = triggerType === 'SENSOR'
                ? {sensorId: Number(sensorSelect.value)}
                : triggerType === 'SCHEDULE'
                    ? {cron: editorCore.buildCron(schedule)}
                    : {};
            nodes.push({clientNodeKey: triggerKey, nodeType: triggerType, configuration: triggerConfiguration});

            let sourceKey = triggerKey;
            let sourcePort = 'out';
            // SCHEDULE 트리거는 조건 노드를 숨겨서 못 만들게 하므로, 편집 모드에서 넘어온 잔여 행이 있어도 무시한다.
            const conditionRows = triggerType === 'SCHEDULE'
                ? []
                : Array.from(path.querySelectorAll('.condition-row'));
            if (conditionRows.length && path.dataset.metricsReady !== 'true') {
                return showError('측정 항목을 불러온 뒤 저장해주세요. 센서를 다시 선택하면 재시도합니다.');
            }
            const expressions = conditionRows.map((row) => {
                const metricKey = row.querySelector('.condition-metric').value;
                const operator = row.querySelector('.condition-operator').value;
                const value = row.querySelector('.condition-value').value.trim();
                return metricKey && value ? `#metrics['${metricKey}'] ${operator} ${value}` : '';
            });
            const incompleteConditionIndex = expressions.findIndex((expression) => !expression);
            if (incompleteConditionIndex >= 0) {
                return showError(`조건 ${incompleteConditionIndex + 1}의 측정 항목과 기준값을 입력해주세요.`);
            }
            expressions.forEach((expression, filterIndex) => {
                const filterKey = `${prefix}-filter-${filterIndex + 1}`;
                nodes.push({clientNodeKey: filterKey, nodeType: 'THRESHOLD', configuration: {expression}});
                links.push({sourceClientNodeKey: sourceKey, targetClientNodeKey: filterKey, sourcePort, targetPort: 'in'});
                sourceKey = filterKey;
                sourcePort = 'true';
            });

            if (triggerType !== 'SCHEDULE' && path.querySelector('.path-gate-enabled').checked) {
                const requiredCount = Number(path.querySelector('.path-gate-required-count').value);
                if (!Number.isInteger(requiredCount)
                    || requiredCount < 1
                    || requiredCount > editorCore.BACKEND_INTEGER_MAX) {
                    return showError('안전장치의 확인 횟수는 1 이상의 올바른 정수로 입력해주세요.');
                }
                const countWindowMinutes = requiredCount >= 2
                    ? Number(path.querySelector('.path-gate-window').value)
                    : null;
                if (requiredCount >= 2 && (!Number.isFinite(countWindowMinutes) || countWindowMinutes <= 0)) {
                    return showError('여러 번 확인할 시간을 분 단위로 입력해주세요.');
                }
                const countWindowSeconds = requiredCount >= 2
                    ? editorCore.minutesToSeconds(countWindowMinutes)
                    : null;
                if (requiredCount >= 2 && (!countWindowSeconds || countWindowSeconds < 1)) {
                    return showError('여러 번 확인할 시간은 1초 이상이며 저장 가능한 범위여야 합니다.');
                }
                const cooldownValue = path.querySelector('.path-gate-cooldown').value.trim();
                if (!cooldownValue) {
                    return showError('최소 실행 간격을 입력해주세요. 제한하지 않으려면 0을 입력해주세요.');
                }
                const cooldownMinutes = Number(cooldownValue);
                if (!Number.isFinite(cooldownMinutes) || cooldownMinutes < 0) {
                    return showError('최소 실행 간격은 0분 이상으로 입력해주세요.');
                }
                const cooldownSeconds = editorCore.minutesToSeconds(cooldownMinutes);
                if (cooldownSeconds == null || (cooldownMinutes > 0 && cooldownSeconds < 1)) {
                    return showError('최소 실행 간격은 0초 또는 1초 이상이며 저장 가능한 범위여야 합니다.');
                }
                if (requiredCount === 1 && cooldownSeconds === 0) {
                    return showError('확인 횟수가 1회이고 최소 실행 간격이 0분이면 안전장치를 꺼주세요.');
                }

                const gateKey = `${prefix}-event-gate`;
                nodes.push({
                    clientNodeKey: gateKey,
                    nodeType: 'EVENT_GATE',
                    configuration: {
                        requiredCount,
                        countWindowSeconds,
                        cooldownSeconds
                    }
                });
                links.push({
                    sourceClientNodeKey: sourceKey,
                    targetClientNodeKey: gateKey,
                    sourcePort,
                    targetPort: 'in'
                });
                sourceKey = gateKey;
                sourcePort = 'true';
            }

            const actions = Array.from(path.querySelectorAll('.flow-action-item'));
            if (actions.length < 1) return showError('조건을 만족했을 때 실행할 동작이 최소 1개 필요합니다.');

            const actuatorTypeCounts = new Map();
            actions.forEach((action) => {
                if (action.querySelector('.action-type').value !== 'ACTUATOR_CONTROL') return;
                const type = action.querySelector('.action-actuator-type').value;
                actuatorTypeCounts.set(type, (actuatorTypeCounts.get(type) || 0) + 1);
            });
            const duplicateType = Array.from(actuatorTypeCounts.entries()).find(([, count]) => count > 1);
            if (duplicateType) {
                const typeLabel = actuatorTypeLabels[duplicateType[0]] || duplicateType[0];
                return showError(`같은 기기 종류(${typeLabel})를 두 개 이상의 기기 제어 카드에서 선택할 수 없어요. 서로 다른 기기로 바꿔주세요.`);
            }

            for (let actionIndex = 0; actionIndex < actions.length; actionIndex++) {
                const action = actions[actionIndex];
                const actionType = action.querySelector('.action-type').value;
                const actionKey = `${prefix}-action-${actionIndex + 1}`;
                let configuration;
                let pendingTempAction = null;

                if (actionType === 'ACTUATOR_CONTROL') {
                    const actuatorType = action.querySelector('.action-actuator-type').value;
                    const command = action.querySelector('.action-actuator-command').value;
                    const valueEl = action.querySelector('.action-actuator-value');
                    const commandValue = valueEl ? valueEl.value.trim() : '';
                    if (!command || !commandValue) {
                        return showError('제어할 기기의 명령과 값을 선택해주세요.');
                    }
                    configuration = {actuatorType, command, commandValue};

                    // "전원 켜기"를 고르고 온도까지 입력했으면, 팬아웃으로 온도 액션 노드를 하나 더 만든다
                    // (같은 조건에서 나가는 별도 액션 2개 — updateActuatorTempFieldVisibility 주석 참고).
                    const tempField = action.querySelector('.action-actuator-temp-field');
                    const tempInput = action.querySelector('.action-actuator-temp-value');
                    if (tempField.style.display !== 'none' && tempInput.value.trim()) {
                        const tempWidget = findRangeCommandWidget(actuatorType);
                        const numericTemp = Number(tempInput.value.trim());
                        if (!tempWidget || !Number.isFinite(numericTemp)
                            || numericTemp < tempWidget.min || numericTemp > tempWidget.max) {
                            return showError(
                                `설정 온도는 ${tempWidget ? `${tempWidget.min}~${tempWidget.max}` : '허용 범위'} 사이로 입력해주세요.`
                            );
                        }
                        pendingTempAction = {
                            clientNodeKey: `${actionKey}-temp`,
                            nodeType: 'ACTUATOR_CONTROL',
                            configuration: {actuatorType, command: tempWidget.stateKey, commandValue: String(numericTemp)}
                        };
                    }
                } else {
                    const title = action.querySelector('.action-title').value.trim();
                    const message = action.querySelector('.action-message').value.trim();
                    if (!title || !message) {
                        return showError('받는 사람이 이해할 수 있도록 알림 제목과 내용을 입력해주세요.');
                    }
                    configuration = {
                        title,
                        severity: action.querySelector('.action-severity').value,
                        message
                    };
                }

                nodes.push({clientNodeKey: actionKey, nodeType: actionType, configuration});
                links.push({sourceClientNodeKey: sourceKey, targetClientNodeKey: actionKey, sourcePort, targetPort: 'in'});
                if (pendingTempAction) {
                    nodes.push(pendingTempAction);
                    links.push({
                        sourceClientNodeKey: sourceKey,
                        targetClientNodeKey: pendingTempAction.clientNodeKey,
                        sourcePort,
                        targetPort: 'in'
                    });
                }
            }
        }

        const description = document.getElementById('flowDescription').value.trim() || null;
        const request = mode === 'edit'
            ? {name, description, nodes, links}
            : {locationId: Number(locationSelect.value), name, description, nodes, links};
        const url = mode === 'edit' ? `/my-group/flows/${flow.flowId}` : '/my-group/flows';
        const method = mode === 'edit' ? 'PUT' : 'POST';
        const reactivateInput = document.getElementById('reactivateAfterSave');
        const reactivateAfterSave = mode === 'edit' && Boolean(reactivateInput?.checked);

        saveBtn.disabled = true;
        if (reactivateInput) reactivateInput.disabled = true;
        fetch(url, {
            method,
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(request)
        })
            .then(async (response) => {
                if (!response.ok) {
                    const body = await response.json().catch(() => null);
                    throw new Error(body && (body.message || body.detail) ? (body.message || body.detail) : '저장하지 못했어요.');
                }
                return response.json();
            })
            .then(async (saved) => {
                if (!reactivateAfterSave) return saved;
                let activationResponse;
                try {
                    activationResponse = await fetch(`/my-group/flows/${saved.flowId}/status`, {
                        method: 'PUT',
                        headers: {'Content-Type': 'application/json'},
                        body: JSON.stringify({status: 'ACTIVE'})
                    });
                } catch (ignored) {
                    showActivationStatusUnknown(saved.flowId);
                    return null;
                }
                if (!activationResponse.ok) {
                    showActivationFailure(saved.flowId);
                    return null;
                }
                return saved;
            })
            .then((saved) => {
                if (saved) location.href = `/my-group/flows/${saved.flowId}`;
            })
            .catch((error) => {
                showError(error.message);
                saveBtn.disabled = false;
                if (reactivateInput) reactivateInput.disabled = false;
            });
    });
})();
