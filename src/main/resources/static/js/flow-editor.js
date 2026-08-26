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
    const defaultRequiredCount = 3;
    const defaultCountTimeoutMinutes = 5;
    const defaultCooldownMinutes = 30;
    const secondsPerMinute = 60;

    const locationSelect = document.getElementById('flowLocationId');
    const pathList = document.getElementById('flowPathList');
    const pathTemplate = document.getElementById('flowPathTemplate');
    const errorEl = document.getElementById('editorError');
    const saveBtn = document.getElementById('btnSaveFlow');
    const attributeCache = new Map();
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

        const attributesRequest = triggerType === 'LOCATION'
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

    function addAction(path, configuration) {
        const config = configuration || {};
        const action = document.createElement('div');
        action.className = 'flow-action-item';
        action.innerHTML = `
            <div class="flow-action-item-heading">
                <span class="flow-action-number"></span>
                <span class="status-badge neutral">필수</span>
            </div>
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
            <p class="flow-action-safety-hint"><i class="ti ti-shield-check"></i> 권장 시작값은 5분 안에 3회 확인하고, 알림 후 30분 동안 다시 보내지 않도록 설정됩니다.</p>`;

        action.querySelector('.action-title').value = config.title || '';
        action.querySelector('.flow-action-number').textContent = '보낼 알림';
        action.querySelector('.action-severity').value = config.severity || 'WARNING';
        action.querySelector('.action-message').value = config.message || '';
        action.querySelector('.action-required-count').value = config.requiredCount == null
            ? defaultRequiredCount
            : config.requiredCount;
        action.querySelector('.action-count-timeout').value = secondsToMinutes(
            config.countTimeoutSeconds,
            defaultCountTimeoutMinutes
        );
        action.querySelector('.action-cooldown').value = secondsToMinutes(
            config.cooldownSeconds,
            defaultCooldownMinutes
        );
        path.querySelector('.path-action-list').appendChild(action);
        updateCountTimeout(action);
    }

    function updateTriggerVisibility(path) {
        const type = path.querySelector('.path-trigger-type').value;
        path.querySelector('.path-trigger-sensor-field').style.display = type === 'SENSOR' ? '' : 'none';
        refreshPathConditionMetrics(path);
    }

    function addPath(data) {
        const path = pathTemplate.content.firstElementChild.cloneNode(true);
        pathList.appendChild(path);

        const trigger = data && data.trigger;
        path.querySelector('.path-trigger-type').value = trigger ? trigger.nodeType : 'SENSOR';
        refreshSensorSelect(
            path.querySelector('.path-trigger-sensor'),
            trigger && trigger.configuration ? trigger.configuration.sensorId : null
        );
        updateTriggerVisibility(path);

        (data && data.filters ? data.filters : []).forEach((filter) => {
            addCondition(path, filter.configuration ? filter.configuration.expression : '');
        });
        refreshConditionList(path.querySelector('.path-condition-list'));

        const action = data && data.action ? data.action : {configuration: {}};
        addAction(path, action.configuration);
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

        const triggers = nodes.filter((node) => node.nodeType === 'SENSOR' || node.nodeType === 'LOCATION');
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
            if (target.nodeType === 'ALERT') action = target;
            break;
        }

        return action && usedKeys.size === nodes.length ? {trigger, filters, action} : null;
    }

    locationSelect.addEventListener('change', refreshAllSensorOptions);

    pathList.addEventListener('change', (event) => {
        const path = event.target.closest('.flow-path');
        if (!path) return;
        if (event.target.matches('.path-trigger-type')) updateTriggerVisibility(path);
        if (event.target.matches('.path-trigger-sensor')) refreshPathConditionMetrics(path);
        if (event.target.matches('.action-required-count')) updateCountTimeout(event.target.closest('.flow-action-item'));
    });

    pathList.addEventListener('input', (event) => {
        if (event.target.matches('.action-required-count')) updateCountTimeout(event.target.closest('.flow-action-item'));
    });

    pathList.addEventListener('click', (event) => {
        const path = event.target.closest('.flow-path');
        if (!path) return;

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

            const triggerKey = `${prefix}-trigger`;
            const triggerConfiguration = triggerType === 'SENSOR'
                ? {sensorId: Number(sensorSelect.value)}
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
            if (actions.length !== 1) return showError('조건을 만족했을 때 보낼 알림이 하나 필요합니다.');
            for (let actionIndex = 0; actionIndex < actions.length; actionIndex++) {
                const action = actions[actionIndex];
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

                const actionKey = `${prefix}-action-${actionIndex + 1}`;
                nodes.push({
                    clientNodeKey: actionKey,
                    nodeType: 'ALERT',
                    configuration: {
                        title,
                        severity: action.querySelector('.action-severity').value,
                        message,
                        requiredCount,
                        countTimeoutSeconds,
                        cooldownSeconds
                    }
                });
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
