(function () {
    const card = document.getElementById('gatewayCard');
    if (!card) return;

    const BASE_URL = '/manage/gateway';
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

        const config = gateway.connectionConfig || {};
        document.getElementById('gatewayBrokerUrl').value = (config.brokerUrls && config.brokerUrls[0]) || '';
        document.getElementById('gatewayTopic').value = (config.topics && config.topics[0]) || '';
        document.getElementById('gatewayUsername').value = config.username || '';
        document.getElementById('gatewayPassword').value = config.password || '';
    }

    function collectConnectionConfig() {
        const brokerUrl = document.getElementById('gatewayBrokerUrl').value.trim();
        const topic = document.getElementById('gatewayTopic').value.trim();
        const username = document.getElementById('gatewayUsername').value.trim();
        const password = document.getElementById('gatewayPassword').value.trim();

        const config = {brokerUrls: brokerUrl ? [brokerUrl] : []};
        // topics를 비우면 키 자체를 안 보내야 Core가 ChirpStack 기본 규격으로 처리한다
        // (빈 배열을 보내면 "토픽 없음"으로 해석돼 기본값이 안 먹는다)
        if (topic) config.topics = [topic];
        if (username) config.username = username;
        if (password) config.password = password;
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
        if (!document.getElementById('gatewayBrokerUrl').value.trim()) {
            showError('브로커 주소를 입력하세요.');
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
