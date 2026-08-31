/**
 * Flow 생성/수정 화면.
 * Rule Engine 검증 계약에 맞춰 Trigger 1개 → THRESHOLD 0..N개 → ALERT 1개를
 * 직렬 연결한다. 저장 전 화면 상태를 Node/Link 목록과 clientNodeKey로 변환한다.
 */
(function () {
    const init = window.FLOW_EDITOR_INIT || {};
    const mode = init.mode;
    const flow = init.flow;
    const sensors = init.sensors || [];
    const actuatorCommandRules = init.actuatorCommandRules || {};
    const defaultRequiredCount = 3;
    const defaultCountTimeoutMinutes = 5;
    const defaultCooldownMinutes = 30;
    const secondsPerMinute = 60;
    // 대시보드 액추에이터 조작 화면(actuator-panel.js)·제안 로그(SuggestionLogViewService)와 같은 한글 표기를 쓴다.
    const actuatorTypeLabels = {AIRCON: '에어컨', AIR_PURIFIER: '공기청정기', VENTILATION_FAN: '환풍기'};
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

    function showError(message) {
        errorEl.textContent = message;
        errorEl.style.display = 'block';
        errorEl.scrollIntoView({behavior: 'smooth', block: 'center'});
    }

    function clearError() {
        errorEl.style.display = 'none';
    }

    function createOption(value, label) {
        const option = document.createElement('option');
        option.value = String(value);
        option.textContent = label;
        return option;
    }

    // 화면에서 고른 반복 규칙을 Rule Engine이 이해하는 Spring 6필드(초 분 시 일 월 요일) cron 문자열로 바꾼다.
    // 백엔드 CronExpressionValidator가 초 필드는 반드시 "0"만 허용한다(분 단위 미만 스케줄은 지원하지 않음).
    function buildCron(schedule) {
        const minute = String(schedule.minute);
        const hour = String(schedule.hour);
        if (schedule.repeatType === 'WEEKLY') {
            const days = schedule.weekdays.length ? schedule.weekdays.slice().sort().join(',') : '*';
            return `0 ${minute} ${hour} * * ${days}`;
        }
        if (schedule.repeatType === 'MONTHLY') {
            return `0 ${minute} ${hour} ${schedule.day} * *`;
        }
        return `0 ${minute} ${hour} * * *`;
    }

    // buildCron의 역변환. 화면에서 만들 수 있는 3가지 형태(매일/매주/매월)만 인식하고,
    // 그 외(다른 사람이 손으로 만든 복잡한 cron 등)는 null을 돌려줘 호출부가 기본값으로 대체하게 한다.
    function parseCron(cron) {
        const parts = String(cron || '').trim().split(/\s+/);
        if (parts.length !== 6) return null;
        const [second, minute, hour, day, month, weekday] = parts;
        if (second !== '0' || !/^\d+$/.test(minute) || !/^\d+$/.test(hour) || month !== '*') return null;
        const m = Number(minute);
        const h = Number(hour);
        if (m < 0 || m > 59 || h < 0 || h > 23) return null;
        if (day !== '*' && weekday === '*') {
            if (!/^\d+$/.test(day)) return null;
            const d = Number(day);
            if (d < 1 || d > 31) return null;
            return {repeatType: 'MONTHLY', hour: h, minute: m, day: d, weekdays: []};
        }
        if (day === '*' && weekday !== '*') {
            const weekdays = weekday.split(',').map(Number);
            if (weekdays.some((value) => !Number.isInteger(value) || value < 0 || value > 6)) return null;
            return {repeatType: 'WEEKLY', hour: h, minute: m, day: null, weekdays};
        }
        if (day === '*' && weekday === '*') {
            return {repeatType: 'DAILY', hour: h, minute: m, day: null, weekdays: []};
        }
        return null;
    }

    function cronToSummary(cron) {
        const parsed = parseCron(cron);
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
        const [hour, minute] = path.querySelector('.path-schedule-time').value.split(':').map(Number);
        const weekdays = Array.from(path.querySelectorAll('.path-schedule-weekday:checked')).map((box) => Number(box.value));
        const day = Number(path.querySelector('.path-schedule-day').value);
        return {repeatType, hour, minute, weekdays, day};
    }

    function updateScheduleSummary(path) {
        const schedule = readSchedule(path);
        path.querySelector('.path-schedule-weekday-field').style.display = schedule.repeatType === 'WEEKLY' ? '' : 'none';
        path.querySelector('.path-schedule-day-field').style.display = schedule.repeatType === 'MONTHLY' ? '' : 'none';
        const summary = path.querySelector('.path-schedule-summary');
        if (Number.isNaN(schedule.hour) || Number.isNaN(schedule.minute)) {
            summary.textContent = '반복 시간을 선택해주세요.';
            return;
        }
        if (schedule.repeatType === 'WEEKLY' && !schedule.weekdays.length) {
            summary.textContent = '반복할 요일을 선택해주세요.';
            return;
        }
        summary.textContent = cronToSummary(buildCron(schedule));
    }

    function applyScheduleFromCron(path, cron) {
        const daySelect = path.querySelector('.path-schedule-day');
        if (!daySelect.options.length) populateScheduleDaySelect(daySelect);

        const parsed = cron ? parseCron(cron) : null;
        const schedule = parsed || {repeatType: 'DAILY', hour: 9, minute: 0, day: 1, weekdays: []};
        path.querySelector('.path-schedule-repeat').value = schedule.repeatType;
        path.querySelector('.path-schedule-time').value =
            `${String(schedule.hour).padStart(2, '0')}:${String(schedule.minute).padStart(2, '0')}`;
        path.querySelectorAll('.path-schedule-weekday').forEach((box) => {
            box.checked = schedule.weekdays.includes(Number(box.value));
        });
        daySelect.value = String(schedule.day || 1);
        updateScheduleSummary(path);
        return cron && !parsed;
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
        if (selectedValue != null) select.value = String(selectedValue);
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

    function populateMetricSelect(select, attributes, selectedMetricKey) {
        select.replaceChildren();
        attributes.forEach((attribute) => {
            const label = attribute.unit
                ? `${attribute.displayName} (${attribute.unit})`
                : attribute.displayName;
            select.appendChild(createOption(attribute.metricKey, label));
        });
        if (selectedMetricKey && !attributes.some((attribute) => attribute.metricKey === selectedMetricKey)) {
            select.appendChild(createOption(selectedMetricKey, `현재 설정: ${selectedMetricKey}`));
        }
        if (selectedMetricKey) select.value = selectedMetricKey;
        select.disabled = false;
    }

    function refreshPathConditionMetrics(path) {
        const triggerType = path.querySelector('.path-trigger-type').value;
        const sensorId = triggerType === 'SENSOR' ? path.querySelector('.path-trigger-sensor').value : null;
        const requestId = String(++metricRequestSequence);
        path.dataset.metricRequestId = requestId;
        path.dataset.metricsReady = 'false';
        const status = path.querySelector('.flow-metric-status');
        status.textContent = triggerType === 'SENSOR' ? '센서 측정 항목을 불러오는 중...' : '위치 전체의 공통 측정 항목을 사용합니다.';

        path.querySelectorAll('.condition-metric').forEach((select) => {
            if (!select.dataset.selectedMetricKey) select.dataset.selectedMetricKey = select.value;
            select.replaceChildren(createOption('', '불러오는 중...'));
            select.disabled = true;
        });

        const attributesRequest = (triggerType === 'LOCATION' || triggerType === 'SCHEDULE')
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
            status.textContent = triggerType === 'SENSOR'
                ? `${attributes.length}개 측정 항목을 불러왔습니다.`
                : '온도·습도·CO₂·조도 공통 항목을 사용합니다.';
        }).catch((error) => {
            if (path.dataset.metricRequestId !== requestId) return;
            path.querySelectorAll('.condition-metric').forEach((select) => {
                select.replaceChildren(createOption('', '측정 항목을 불러오지 못함'));
                select.disabled = true;
            });
            status.textContent = `${error.message} 센서를 다시 선택하면 재시도합니다.`;
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

    function updateCountTimeout(action) {
        const requiredCount = Number(action.querySelector('.action-required-count').value || defaultRequiredCount);
        action.querySelector('.action-count-timeout-field').style.display = requiredCount >= 2 ? '' : 'none';
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
    }

    function refreshActuatorFields(action, selectedCommand, selectedValue) {
        const actuatorType = action.querySelector('.action-actuator-type').value;
        populateActuatorCommandSelect(action.querySelector('.action-actuator-command'), actuatorType, selectedCommand);
        renderActuatorValueControl(action, selectedValue);
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
                <span class="flow-action-number"></span>
                <span class="status-badge neutral">필수</span>
            </div>
            <div class="settings-field">
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
                <div class="action-subfields">
                    <div class="settings-field">
                        <label>확인 횟수</label>
                        <input type="number" class="form-control action-required-count" min="1" aria-label="알림 전 확인 횟수" value="3">
                    </div>
                    <div class="settings-field action-count-timeout-field" style="display:none;">
                        <label>확인 시간(분)</label>
                        <input type="number" class="form-control action-count-timeout" min="0.01" step="any" aria-label="확인 시간(분)" value="5">
                    </div>
                    <div class="settings-field">
                        <label>재알림 대기(분)</label>
                        <input type="number" class="form-control action-cooldown" min="0" step="any" aria-label="재알림 대기(분)" value="30">
                    </div>
                </div>
                <p class="flow-action-safety-hint"><i class="ti ti-shield-check"></i> 권장 시작값은 5분 안에 3회 확인하고, 알림 후 30분 동안 다시 보내지 않도록 설정됩니다.</p>
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
                    </div>
                    <div class="settings-field">
                        <label>값</label>
                        <div class="action-actuator-value-control"></div>
                    </div>
                </div>
                <p class="field-hint action-actuator-summary" aria-live="polite"></p>
            </div>`;

        item.querySelector('.action-type').value = actionType;
        item.querySelector('.action-title').value = config.title || '';
        item.querySelector('.action-severity').value = config.severity || 'WARNING';
        item.querySelector('.action-message').value = config.message || '';
        item.querySelector('.action-required-count').value = config.requiredCount == null
            ? defaultRequiredCount
            : config.requiredCount;
        item.querySelector('.action-count-timeout').value = secondsToMinutes(
            config.countTimeoutSeconds,
            defaultCountTimeoutMinutes
        );
        item.querySelector('.action-cooldown').value = secondsToMinutes(
            config.cooldownSeconds,
            defaultCooldownMinutes
        );
        item.querySelector('.action-actuator-type').value = config.actuatorType || 'AIRCON';
        refreshActuatorFields(item, config.command, config.commandValue);
        updateActionTypeVisibility(item);
        path.querySelector('.path-action-list').appendChild(item);
        updateCountTimeout(item);
    }

    function updateTriggerVisibility(path) {
        const type = path.querySelector('.path-trigger-type').value;
        path.querySelector('.path-trigger-sensor-field').style.display = type === 'SENSOR' ? '' : 'none';
        path.querySelector('.path-trigger-schedule-field').style.display = type === 'SCHEDULE' ? '' : 'none';
        if (type === 'SCHEDULE') updateScheduleSummary(path);
        refreshPathConditionMetrics(path);
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
        const cronNotRecognized = applyScheduleFromCron(
            path,
            trigger && trigger.configuration ? trigger.configuration.cron : null
        );
        if (cronNotRecognized) {
            showError('저장된 예약 주기를 새 화면 형식으로 옮기지 못해 기본값(매일 09:00)으로 표시했어요. 필요하면 다시 설정해주세요.');
        }
        updateTriggerVisibility(path);
        updateLocationTriggerLabel();

        (data && data.filters ? data.filters : []).forEach((filter) => {
            addCondition(path, filter.configuration ? filter.configuration.expression : '');
        });
        refreshConditionList(path.querySelector('.path-condition-list'));

        addAction(path, data && data.action ? data.action : {nodeType: 'ALERT', configuration: {}});
        return path;
    }

    function parseFlow(nodes, links) {
        const nodeByKey = new Map(nodes.map((node) => [node.clientNodeKey, node]));
        const outgoing = new Map();
        links.forEach((link) => {
            const key = `${link.sourceClientNodeKey}:${link.sourcePort}`;
            if (!outgoing.has(key)) outgoing.set(key, []);
            outgoing.get(key).push(link);
        });

        const triggers = nodes.filter((node) =>
            node.nodeType === 'SENSOR' || node.nodeType === 'LOCATION' || node.nodeType === 'SCHEDULE');
        if (triggers.length !== 1) return null;
        const usedKeys = new Set();
        const trigger = triggers[0];
        const filters = [];
        let action = null;
        usedKeys.add(trigger.clientNodeKey);
        let sourceKey = trigger.clientNodeKey;
        let sourcePort = 'out';

        for (let guard = 0; guard <= nodes.length; guard++) {
            const nextLinks = outgoing.get(`${sourceKey}:${sourcePort}`) || [];
            if (nextLinks.length !== 1) return null;
            const target = nodeByKey.get(nextLinks[0].targetClientNodeKey);
            if (!target || usedKeys.has(target.clientNodeKey)) return null;
            usedKeys.add(target.clientNodeKey);
            if (target.nodeType === 'THRESHOLD') {
                filters.push(target);
                sourceKey = target.clientNodeKey;
                sourcePort = 'true';
                continue;
            }
            if (target.nodeType === 'ALERT' || target.nodeType === 'ACTUATOR_CONTROL') action = target;
            break;
        }

        return action && usedKeys.size === nodes.length ? {trigger, filters, action} : null;
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
        if (event.target.matches('.action-required-count')) updateCountTimeout(event.target.closest('.flow-action-item'));
        if (event.target.matches('.path-schedule-repeat, .path-schedule-time, .path-schedule-day, .path-schedule-weekday')) {
            updateScheduleSummary(path);
        }
        if (event.target.matches('.action-type')) updateActionTypeVisibility(event.target.closest('.flow-action-item'));
        if (event.target.matches('.action-actuator-type')) {
            refreshActuatorFields(event.target.closest('.flow-action-item'), null, null);
        }
        if (event.target.matches('.action-actuator-command')) {
            renderActuatorValueControl(event.target.closest('.flow-action-item'), null);
            updateActuatorSummary(event.target.closest('.flow-action-item'));
        }
        if (event.target.matches('.action-actuator-value')) {
            updateActuatorSummary(event.target.closest('.flow-action-item'));
        }
    });

    pathList.addEventListener('input', (event) => {
        if (event.target.matches('.action-required-count')) updateCountTimeout(event.target.closest('.flow-action-item'));
        if (event.target.matches('.action-actuator-value')) updateActuatorSummary(event.target.closest('.flow-action-item'));
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
    });

    if (mode === 'edit' && flow) {
        document.getElementById('flowName').value = flow.name || '';
        document.getElementById('flowDescription').value = flow.description || '';
        locationSelect.value = String(flow.locationId);
        locationSelect.disabled = true;

        const parsedFlow = parseFlow(flow.nodes || [], flow.links || []);
        if (!parsedFlow) {
            showError('이 자동화에는 현재 화면에서 수정할 수 없는 복잡한 연결이 있습니다. 기존 내용을 보호하기 위해 저장하지 않았어요. 상세 화면에서 작동 순서를 확인해주세요.');
            addPath();
            saveBtn.disabled = true;
        } else {
            addPath(parsedFlow);
        }
    } else {
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
                schedule = readSchedule(path);
                if (Number.isNaN(schedule.hour) || Number.isNaN(schedule.minute)) {
                    return showError('예약 시간을 선택해주세요.');
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
                    ? {cron: buildCron(schedule)}
                    : {};
            nodes.push({clientNodeKey: triggerKey, nodeType: triggerType, configuration: triggerConfiguration});

            let sourceKey = triggerKey;
            let sourcePort = 'out';
            const conditionRows = Array.from(path.querySelectorAll('.condition-row'));
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

            const actions = Array.from(path.querySelectorAll('.flow-action-item'));
            if (actions.length !== 1) return showError('조건을 만족했을 때 실행할 동작이 하나 필요합니다.');
            for (let actionIndex = 0; actionIndex < actions.length; actionIndex++) {
                const action = actions[actionIndex];
                const actionType = action.querySelector('.action-type').value;
                const actionKey = `${prefix}-action-${actionIndex + 1}`;
                let configuration;

                if (actionType === 'ACTUATOR_CONTROL') {
                    const actuatorType = action.querySelector('.action-actuator-type').value;
                    const command = action.querySelector('.action-actuator-command').value;
                    const valueEl = action.querySelector('.action-actuator-value');
                    const commandValue = valueEl ? valueEl.value.trim() : '';
                    if (!command || !commandValue) {
                        return showError('제어할 기기의 명령과 값을 선택해주세요.');
                    }
                    configuration = {actuatorType, command, commandValue};
                } else {
                    const title = action.querySelector('.action-title').value.trim();
                    const message = action.querySelector('.action-message').value.trim();
                    const requiredCount = Number(
                        action.querySelector('.action-required-count').value || defaultRequiredCount
                    );
                    if (!Number.isInteger(requiredCount) || requiredCount < 1) {
                        return showError('알림을 보내기 전 확인할 횟수는 1 이상의 정수로 입력해주세요.');
                    }
                    const countTimeoutMinutes = requiredCount >= 2
                        ? Number(action.querySelector('.action-count-timeout').value || 0)
                        : null;
                    if (!title || !message) {
                        return showError('받는 사람이 이해할 수 있도록 알림 제목과 내용을 입력해주세요.');
                    }
                    if (requiredCount >= 2 && (!countTimeoutMinutes || countTimeoutMinutes <= 0)) {
                        return showError('여러 번 확인할 시간을 분 단위로 입력해주세요.');
                    }
                    const cooldownMinutes = Number(
                        action.querySelector('.action-cooldown').value || defaultCooldownMinutes
                    );
                    if (!Number.isFinite(cooldownMinutes) || cooldownMinutes < 0) {
                        return showError('재알림 대기 시간은 0분 이상으로 입력해주세요.');
                    }
                    const countTimeoutSeconds = requiredCount >= 2
                        ? Math.round(countTimeoutMinutes * secondsPerMinute)
                        : null;
                    const cooldownSeconds = Math.round(cooldownMinutes * secondsPerMinute);
                    configuration = {
                        title,
                        severity: action.querySelector('.action-severity').value,
                        message,
                        requiredCount,
                        countTimeoutSeconds,
                        cooldownSeconds
                    };
                }

                nodes.push({clientNodeKey: actionKey, nodeType: actionType, configuration});
                links.push({sourceClientNodeKey: sourceKey, targetClientNodeKey: actionKey, sourcePort, targetPort: 'in'});
            }
        }

        const description = document.getElementById('flowDescription').value.trim() || null;
        const request = mode === 'edit'
            ? {name, description, nodes, links}
            : {locationId: Number(locationSelect.value), name, description, nodes, links};
        const url = mode === 'edit' ? `/my-group/flows/${flow.flowId}` : '/my-group/flows';
        const method = mode === 'edit' ? 'PUT' : 'POST';

        saveBtn.disabled = true;
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
            .then((saved) => {
                location.href = `/my-group/flows/${saved.flowId}`;
            })
            .catch((error) => {
                showError(error.message);
                saveBtn.disabled = false;
            });
    });
})();
