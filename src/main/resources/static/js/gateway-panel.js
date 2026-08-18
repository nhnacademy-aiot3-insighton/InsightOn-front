(function () {
    const card = document.getElementById('gatewayCard');
    if (!card) return;

    const BASE_URL = '/manage/gateway';
    const configListEl = document.getElementById('connectionConfigList');
    const rowTemplate = document.getElementById('configRowTemplate');
    const saveStatusEl = document.getElementById('saveStatus');
    const btnSave = document.getElementById('btnSaveGateway');
    const btnDelete = document.getElementById('btnDeleteGateway');

    const gateway = GATEWAY_INIT.gateway;
    const isEdit = !!gateway;

    document.getElementById('gatewayEmptyNotice').style.display = isEdit ? 'none' : 'block';
    btnSave.textContent = isEdit ? '저장' : '게이트웨이 등록';
    btnDelete.style.display = isEdit ? 'inline-block' : 'none';

    if (isEdit) {
        document.getElementById('gatewayStatusPanel').style.display = 'flex';
        document.getElementById('gatewayStatusName').textContent = gateway.name;
        document.getElementById('gatewayStatusBadge').textContent = gateway.status;
        document.getElementById('gatewayStatusBadge').className = 'status-badge ' + (gateway.status === 'ACTIVE' ? 'success' : 'danger');
        document.getElementById('gatewayStatusProtocol').textContent = gateway.protocolType;
        document.getElementById('gatewayHeartbeat').textContent = gateway.lastHeartbeatAt
            ? new Date(gateway.lastHeartbeatAt).toLocaleString('ko-KR')
            : '통신 기록 없음';
        document.getElementById('gatewayCreatedAt').textContent = new Date(gateway.createdAt).toLocaleDateString('ko-KR');
        document.getElementById('gatewayName').value = gateway.name || '';
        document.getElementById('gatewayProtocol').value = gateway.protocolType || 'MQTT';
    }

    function addConfigRow(key, value) {
        const clone = rowTemplate.content.cloneNode(true);
        const row = clone.querySelector('.condition-row');
        row.querySelector('.config-key').value = key || '';
        row.querySelector('.config-value').value = value != null ? value : '';
        row.querySelector('.btn-remove-condition').addEventListener('click', () => row.remove());
        configListEl.appendChild(row);
    }

    const initialConfig = (gateway && gateway.connectionConfig) || {};
    const configKeys = Object.keys(initialConfig);
    if (configKeys.length) {
        configKeys.forEach((key) => addConfigRow(key, initialConfig[key]));
    } else {
        addConfigRow('', '');
    }

    document.getElementById('btnAddConfigRow').addEventListener('click', () => addConfigRow('', ''));

    function collectConnectionConfig() {
        const config = {};
        configListEl.querySelectorAll('.condition-row').forEach((row) => {
            const key = row.querySelector('.config-key').value.trim();
            const value = row.querySelector('.config-value').value.trim();
            if (key) config[key] = value;
        });
        return config;
    }

    function showError(message) {
        saveStatusEl.textContent = message;
        saveStatusEl.style.display = 'block';
    }

    btnSave.addEventListener('click', () => {
        const name = document.getElementById('gatewayName').value.trim();
        if (!name) {
            showError('게이트웨이 이름을 입력하세요.');
            return;
        }
        saveStatusEl.style.display = 'none';
        btnSave.disabled = true;

        const body = {
            name,
            protocolType: document.getElementById('gatewayProtocol').value,
            connectionConfig: collectConnectionConfig()
        };
        const url = isEdit ? `${BASE_URL}/${gateway.id}` : BASE_URL;
        const method = isEdit ? 'PUT' : 'POST';

        fetch(url, {method, headers: {'Content-Type': 'application/json'}, body: JSON.stringify(body)})
            .then((r) => {
                if (!r.ok) throw new Error('save failed');
                location.reload();
            })
            .catch(() => {
                showError('저장하지 못했어요. 잠시 후 다시 시도해주세요.');
                btnSave.disabled = false;
            });
    });

    if (isEdit) {
        btnDelete.addEventListener('click', () => {
            if (!confirm('게이트웨이를 삭제할까요? 연결된 센서·액추에이터 통신이 끊겨요.')) return;
            fetch(`${BASE_URL}/${gateway.id}`, {method: 'DELETE'})
                .then((r) => {
                    if (!r.ok) throw new Error('delete failed');
                    location.reload();
                })
                .catch(() => showError('삭제하지 못했어요. 잠시 후 다시 시도해주세요.'));
        });
    }
})();
