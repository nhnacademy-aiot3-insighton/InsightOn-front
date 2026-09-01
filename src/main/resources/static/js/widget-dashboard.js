(function () {
    const gridEl = document.getElementById('widgetGrid');
    if (!gridEl) return;

    // 차트 색을 현재 테마(app.css 토큰)에서 읽어온다 — theme.js가 <head>에서
    // <html data-theme>를 이미 세팅하므로 페이지 로드 시점의 라이트/다크가 반영됨.
    function cssVar(name, fallback) {
        const v = getComputedStyle(document.documentElement).getPropertyValue(name).trim();
        return v || fallback;
    }

    const emptyStateEl = document.getElementById('widgetEmptyState');
    const saveStatusEl = document.getElementById('saveStatus');
    const btnAddWidget = document.getElementById('btnAddWidget');
    const btnSaveLayout = document.getElementById('btnSaveLayout');

    const modalEl = document.getElementById('widgetConfigModal');
    const modal = new bootstrap.Modal(modalEl);
    const sensorSelect = document.getElementById('widgetSensorSelect');
    const metricListEl = document.getElementById('widgetMetricList');
    const rangeSelect = document.getElementById('widgetRangeSelect');
    const aggSelect = document.getElementById('widgetAggSelect');
    const btnApplyConfig = document.getElementById('btnApplyWidgetConfig');

    const GROUP_ID = DASHBOARD_INIT.groupId;
    const LOCATION_ID = DASHBOARD_INIT.locationId;
    const CAN_MANAGE = DASHBOARD_INIT.canManage === true;
    const BASE_URL = `/my-group/location/${LOCATION_ID}/dashboard`;

    // sensorEui -> display name & sensorId mapping
    const sensorNameByEui = {};
    const sensorIdByEui = {};
    if (sensorSelect) {
        sensorSelect.querySelectorAll('option[data-eui]').forEach((opt) => {
            const eui = opt.dataset.eui;
            sensorNameByEui[eui] = opt.textContent.trim();
            sensorIdByEui[eui] = opt.value; // sensorId
        });
    }

    let uidCounter = 0;
    const state = (DASHBOARD_INIT.widgets || []).map((w) => ({
        uid: 'w' + (w.widgetId ?? ++uidCounter),
        widgetId: w.widgetId,
        xPos: w.xPos,
        yPos: w.yPos,
        width: w.width,
        height: w.height,
        widgetConfig: w.widgetConfig || {
            type: 'GRAPH',
            sensorEui: null,
            range: '-1h',
            aggregateWindow: '15m',
            fields: []
        },
        layoutDirty: false,  // 위치·크기 변경 여부 (드래그, 리사이즈)
        configDirty: false   // 설정 변경 여부 (위젯 설정 모달 "적용")
    }));

    const chartInstances = {};
    const sseConnections = {};
    let editingUid = null;

    // SSE 구독 처리
    function subscribeWidgetSse(w, sensorId) {
        if (!sensorId) return;

        if (sseConnections[w.uid]) {
            sseConnections[w.uid].close();
        }

        console.log(`[SSE Subscribed] 센서 ${sensorId}번 실시간 SSE 연결 개설 완료`);
        const eventSource = new EventSource(`/sse/sensors/${sensorId}`);
        sseConnections[w.uid] = eventSource;

        eventSource.addEventListener('telemetry', (event) => {
            try {
                const data = JSON.parse(event.data);
                console.log(`[SSE Telemetry Received] 센서 ${sensorId}번 데이터 수신:`, data);
                updateWidgetRealtime(w, data);
            } catch (e) {
                console.warn('[SSE Parse Error]', e);
            }
        });

        eventSource.onerror = (err) => {
            console.warn(`[SSE Error] 센서 ${sensorId} SSE 연결 알림`, err);
        };
    }

    function getAggregateWindowMs(aggStr) {
        if (!aggStr) return 15 * 60 * 1000;
        const unit = aggStr.slice(-1);
        const val = parseInt(aggStr.slice(0, -1), 10) || 15;
        if (unit === 'm') return val * 60 * 1000;
        if (unit === 'h') return val * 60 * 60 * 1000;
        if (unit === 's') return val * 1000;
        return 15 * 60 * 1000;
    }

    function formatTelemetryTime(ts) {
        const d = ts ? new Date(ts) : new Date();
        const month = String(d.getMonth() + 1).padStart(2, '0');
        const day = String(d.getDate()).padStart(2, '0');
        const hours = String(d.getHours()).padStart(2, '0');
        const minutes = String(d.getMinutes()).padStart(2, '0');
        return `${month}-${day} ${hours}:${minutes}`;
    }

    // SSE 실시간 데이터 갱신 (widgetConfig.aggregateWindow 버킷 주기에 맞춘 실시간 갱신)
    function updateWidgetRealtime(w, telemetryData) {
        const chart = chartInstances[w.uid];
        const metrics = telemetryData.metrics || {};
        const type = w.widgetConfig.type || 'GRAPH';

        const incomingMs = telemetryData.timestamp ? new Date(telemetryData.timestamp).getTime() : Date.now();
        const windowMs = getAggregateWindowMs(w.widgetConfig.aggregateWindow);
        const bucketMs = Math.floor(incomingMs / windowMs) * windowMs;
        const bucketTimeStr = formatTelemetryTime(bucketMs);

        if ((type === 'GRAPH' || type === 'BAR') && chart) {
            const labels = chart.data.labels;
            const lastLabel = labels.length > 0 ? labels[labels.length - 1] : null;

            if (lastLabel === bucketTimeStr) {
                // 같은 집계 주기(예: 동일한 15분 구간) 내 수신 데이터는 기존 마지막 포인트를 갱신
                chart.data.datasets.forEach((ds) => {
                    const key = ds.fieldKey || ds.label;
                    const val = metrics[key] ?? metrics[ds.label] ?? null;
                    if (val !== null && ds.data.length > 0) {
                        ds.data[ds.data.length - 1] = Number(val);
                    }
                });
            } else {
                // 새로운 집계 주기 구간이 시작되면 신규 라벨 및 데이터 포인트 추가
                labels.push(bucketTimeStr);
                chart.data.datasets.forEach((ds) => {
                    const key = ds.fieldKey || ds.label;
                    const val = metrics[key] ?? metrics[ds.label] ?? null;
                    ds.data.push(val !== null ? Number(val) : null);
                });
            }

            // 데이터 개수가 많아지면 좌우 스크롤 폭 확장
            adjustChartScroll(w, chart.data.labels.length);

            // Y축 수치 범위 계산 및 좌측 Sticky 고정 Y축 동적 갱신
            const allDataPoints = chart.data.datasets.flatMap(d => d.data || []).filter(v => v !== null && v !== undefined);
            const minVal = allDataPoints.length ? Math.min(...allDataPoints) : 0;
            const maxVal = allDataPoints.length ? Math.max(...allDataPoints) : 100;
            syncYAxis(w, minVal, maxVal);

            chart.update('quiet');
        } else if (type === 'GAUGE' || type === 'SINGLE_STAT') {
            const el = contentEl(w.uid);
            const firstField = (w.widgetConfig.fields || [])[0];
            if (!firstField) return;
            const val = metrics[firstField] ?? 0;
            const numericVal = Number(val);

            const valueEl = el ? el.querySelector('.grid-widget-value') : null;
            if (valueEl) {
                valueEl.textContent = numericVal.toLocaleString(undefined, { maximumFractionDigits: 1 });
            }

            if (chart) {
                const maxVal = 100;
                const fillVal = Math.min(Math.max(numericVal, 0), maxVal);
                chart.data.datasets[0].data = [fillVal, maxVal - fillVal];
                chart.update('quiet');
            }
        } else {
            const el = contentEl(w.uid);
            if (!el) return;
            const firstField = (w.widgetConfig.fields || [])[0];
            const val = metrics[firstField];
            const valueEl = el.querySelector('.grid-widget-value');
            if (valueEl && val !== undefined) {
                valueEl.textContent = Number(val).toLocaleString(undefined, {
                    maximumFractionDigits: 1
                });
            }
        }
    }

    // GridStack 초기화 (위젯 카드 이동은 헤더 바를 잡았을 때만 가능하도록 한정)
    const grid = GridStack.init({
        column: 12,
        cellHeight: 80,
        margin: 8,
        float: true,
        handle: '.grid-widget-header',
        resizable: {handles: 'e, se, s, sw, w'},
        disableDrag: !CAN_MANAGE,
        disableResize: !CAN_MANAGE
    }, gridEl);

    function itemEl(uid) {
        return gridEl.querySelector(`.grid-stack-item[gs-id="${uid}"]`);
    }

    function contentEl(uid) {
        const item = itemEl(uid);
        return item ? item.querySelector('.grid-widget') : null;
    }

    function nextFreeRow() {
        return state.reduce((max, w) => Math.max(max, w.yPos + w.height), 0);
    }

    // 위젯에 쓰인 센서들의 메트릭 정의(표시명/단위)를 페이지 로드 시 미리 모아 캐싱한다.
    // metric_definitions는 전역 테이블(센서 무관하게 같은 키는 같은 정의)이라 위젯마다 다시 안 물어봐도 됨
    const metricDefsByKey = {};

    function metricLabel(key) {
        return (metricDefsByKey[key] && metricDefsByKey[key].displayName) || key;
    }

    function metricUnit(key) {
        return (metricDefsByKey[key] && metricDefsByKey[key].unit) || '';
    }

    function metricLabelWithUnit(key) {
        const unit = metricUnit(key);
        return unit ? `${metricLabel(key)} (${unit})` : metricLabel(key);
    }

    function loadMetricDefs(sensorIds) {
        const uniqueIds = [...new Set(sensorIds.filter(Boolean))];
        return Promise.all(uniqueIds.map((id) =>
            fetch(`/my-group/sensors/${id}/attributes`)
                .then((r) => r.ok ? r.json() : [])
                .then((attrs) => attrs.forEach((a) => {
                    metricDefsByKey[a.metricKey] = {displayName: a.displayName, unit: a.unit};
                }))
                .catch(() => {})
        ));
    }

    function widgetTitle(w) {
        const sensorName = sensorNameByEui[w.widgetConfig.sensorEui] || '미설정 센서';
        const fields = (w.widgetConfig.fields || []).map(metricLabel).join(', ') || '메트릭 미선택';
        return {sensorName, fields};
    }

    function renderAll() {
        emptyStateEl.style.display = state.length === 0 ? 'block' : 'none';
    }

    function buildGridStackItem(w) {
        const item = document.createElement('div');
        item.className = 'grid-stack-item';
        item.setAttribute('gs-id', w.uid);
        item.setAttribute('gs-x', w.xPos);
        item.setAttribute('gs-y', w.yPos);
        item.setAttribute('gs-w', w.width);
        item.setAttribute('gs-h', w.height);

        const {sensorName, fields} = widgetTitle(w);

        const cursorStyle = CAN_MANAGE ? 'cursor: move;' : '';
        const dragIcon = CAN_MANAGE ? '<i class="ti ti-drag-drop text-muted fs-3" title="드래그하여 위젯 이동"></i>' : '';
        const controlsHtml = CAN_MANAGE ? `
            <div class="grid-widget-controls d-flex align-items-center gap-1">
                <button type="button" class="settings btn btn-icon btn-ghost-secondary btn-sm" title="위젯 설정" aria-label="위젯 설정"><i class="ti ti-settings"></i></button>
                <button type="button" class="remove btn btn-icon btn-ghost-danger btn-sm" title="위젯 삭제" aria-label="위젯 삭제"><i class="ti ti-trash"></i></button>
            </div>
        ` : '';

        item.innerHTML = `
            <div class="card grid-widget h-100 border shadow-sm rounded-3">
                <div class="card-header grid-widget-header px-3 py-2 border-bottom d-flex align-items-center justify-content-between" style="${cursorStyle}">
                    <div class="grid-widget-label d-flex align-items-center gap-2 text-truncate" style="max-width: ${CAN_MANAGE ? 'calc(100% - 95px)' : '100%'};">
                        ${dragIcon}
                        <span class="fw-bold text-truncate" title="${sensorName} · ${fields}">${sensorName} · ${fields}</span>
                    </div>
                    ${controlsHtml}
                </div>
                <div class="card-body grid-widget-body p-2 d-flex flex-column" style="position: relative; flex: 1; min-height: 0;"></div>
                <div class="grid-widget-dim">${w.width}×${w.height}</div>
            </div>
        `;

        const content = item.querySelector('.grid-widget');
        if (CAN_MANAGE) {
            const settingsBtn = content.querySelector('.settings');
            if (settingsBtn) settingsBtn.addEventListener('click', () => openConfigModal(w));
            const removeBtn = content.querySelector('.remove');
            if (removeBtn) removeBtn.addEventListener('click', () => removeWidget(w.uid));
        }

        return item;
    }

    function addItemToGrid(w) {
        const item = buildGridStackItem(w);
        gridEl.appendChild(item);
        grid.makeWidget(item);
        renderWidgetBody(w, item.querySelector('.grid-widget'));
    }

    function updateLabel(w) {
        const el = contentEl(w.uid);
        if (!el) return;
        const {sensorName, fields} = widgetTitle(w);
        const span = el.querySelector('.grid-widget-label span');
        if (span) {
            span.textContent = `${sensorName} · ${fields}`;
            span.title = `${sensorName} · ${fields}`;
        }
    }

    function updateDim(w) {
        const el = contentEl(w.uid);
        if (el) {
            const dimEl = el.querySelector('.grid-widget-dim');
            if (dimEl) dimEl.textContent = `${w.width}×${w.height}`;
        }
    }

    function renderWidgetBody(w, el) {
        const body = el.querySelector('.grid-widget-body');
        if (!body) return;

        // 센서가 설정된 경우 차트/수치 캔버스 렌더링
        // configDirty(설정 변경)일 때는 btnApplyConfig에서 직접 fetchAndRenderData를 호출하므로 여기서 중복 호출하지 않음
        // layoutDirty(드래그·리사이즈)일 때는 renderWidgetBody 자체가 호출되지 않으므로 고려 불필요
        if (w.widgetConfig && w.widgetConfig.sensorEui) {
            initEmptyChart(w, el);
            if (w.widgetId && !w.configDirty) {
                // 초기 로드(페이지 리로드) 시에만 여기서 InfluxDB 조회
                fetchAndRenderData(w, el);
            }
            return;
        }

        // 미설정 위젯일 때만 안내 문구 출력
        body.className = 'card-body grid-widget-body p-3 d-flex flex-column align-items-center justify-content-center text-center';
        body.innerHTML = `
            <div class="text-secondary">
                <i class="ti ti-settings fs-2 mb-1 text-muted"></i>
                <div class="fw-bold">위젯 설정을 완료해주세요</div>
                <small class="text-muted">⚙️ 버튼을 눌러 센서를 선택하세요</small>
            </div>
        `;
    }

    function adjustChartScroll(w, labelCount, forceScroll = false) {
        const el = contentEl(w.uid);
        if (!el) return;
        const inner = el.querySelector('.chart-inner-canvas');
        const wrapper = el.querySelector('.chart-scroll-wrapper');
        if (inner && wrapper) {
            const displayMode = (w.widgetConfig && w.widgetConfig.displayMode) || 'SCROLL';
            if (displayMode === 'FIT') {
                inner.style.width = '100%';
                wrapper.style.overflowX = 'hidden';
                return;
            }

            wrapper.style.overflowX = 'auto';
            const wrapperWidth = wrapper.getBoundingClientRect().width || wrapper.clientWidth || 300;
            const contentWidth = labelCount > 0 ? labelCount * 50 : wrapperWidth;
            const minWidth = Math.max(wrapperWidth, contentWidth);
            inner.style.width = minWidth + 'px';

            const isNearRight = (wrapper.scrollWidth - wrapper.clientWidth - wrapper.scrollLeft) < 80;
            if (forceScroll || isNearRight) {
                wrapper.scrollLeft = wrapper.scrollWidth;
            }

            if (!wrapper.dataset.dragBound) {
                wrapper.dataset.dragBound = 'true';
                let isDown = false;
                let startX, scrollLeftPos;

                wrapper.addEventListener('mousedown', (e) => {
                    isDown = true;
                    wrapper.style.cursor = 'grabbing';
                    startX = e.pageX - wrapper.offsetLeft;
                    scrollLeftPos = wrapper.scrollLeft;
                });
                wrapper.addEventListener('mouseleave', () => {
                    isDown = false;
                    wrapper.style.cursor = 'grab';
                });
                wrapper.addEventListener('mouseup', () => {
                    isDown = false;
                    wrapper.style.cursor = 'grab';
                });
                wrapper.addEventListener('mousemove', (e) => {
                    if (!isDown) return;
                    e.preventDefault();
                    const x = e.pageX - wrapper.offsetLeft;
                    const walk = (x - startX) * 1.5;
                    wrapper.scrollLeft = scrollLeftPos - walk;
                });
            }
        }
    }

    function getFieldColor(field, index) {
        const name = String(field || '').toLowerCase();
        if (name.includes('temp') || name.includes('온도')) {
            return '#ff6b6b'; // 모던 코랄 소프트 레드 (온도)
        }
        if (name.includes('humid') || name.includes('습도')) {
            return '#4ecdc4'; // 세련된 틸 민트 블루 (습도)
        }
        if (name.includes('co2') || name.includes('co')) {
            return '#00b894'; // 에메랄드 그린 (CO2)
        }
        if (name.includes('press') || name.includes('기압')) {
            return '#fdcb6e'; // 앰버 옐로우 (기압)
        }
        if (name.includes('light') || name.includes('illumi') || name.includes('조도')) {
            return '#e17055'; // 테라코타 오렌지 (조도)
        }
        if (name.includes('battery') || name.includes('배터리')) {
            return '#a29bfe'; // 소프트 라벤더 바이올렛 (배터리)
        }

        const fallbackColors = ['#ff6b6b', '#4ecdc4', '#00b894', '#fdcb6e', '#a29bfe', '#e17055', '#0984e3'];
        return fallbackColors[index % fallbackColors.length];
    }

    function createSmartChartScales(fields) {
        const leftField = fields && fields.length ? fields[0] : '';
        const rightField = fields && fields.length > 1 ? fields[1] : null;

        const scales = {
            x: {
                ticks: {
                    font: {size: 10, weight: '500'},
                    color: cssVar('--ink-soft', '#64748b'),
                    maxRotation: 0,
                    autoSkip: true,
                    callback: function (val, index, ticks) {
                        const rawLabel = this.getLabelForValue(val);
                        if (!rawLabel || typeof rawLabel !== 'string') return rawLabel;
                        const parts = rawLabel.trim().split(' ');
                        if (parts.length === 2) {
                            const dateStr = parts[0]; // e.g. "08-31"
                            const timeStr = parts[1]; // e.g. "14:00"
                            const prevLabel = (index > 0 && ticks[index - 1]) ? this.getLabelForValue(ticks[index - 1].value) : null;
                            const prevDate = (prevLabel && typeof prevLabel === 'string') ? prevLabel.trim().split(' ')[0] : null;
                            if (index === 0 || dateStr !== prevDate) {
                                return dateStr.replace('-', '/') + ' ' + timeStr;
                            }
                            return timeStr;
                        }
                        return rawLabel;
                    }
                },
                grid: {display: false}
            },
            y: {
                type: 'linear',
                display: true,
                position: 'left',
                grid: {color: cssVar('--line', '#e2e8f0')},
                ticks: {
                    font: {size: 10, weight: '600'},
                    color: getFieldColor(leftField, 0),
                    callback: function (val) {
                        if (Math.abs(val) >= 1000000) return (val / 1000000).toFixed(1) + 'M';
                        if (Math.abs(val) >= 10000) return (val / 1000).toFixed(0) + 'k';
                        return Number(val).toLocaleString();
                    }
                }
            }
        };

        if (rightField) {
            scales.y1 = {
                type: 'linear',
                display: true,
                position: 'right',
                grid: {drawOnChartArea: false},
                ticks: {
                    font: {size: 10, weight: '600'},
                    color: getFieldColor(rightField, 1),
                    callback: function (val) {
                        if (Math.abs(val) >= 1000000) return (val / 1000000).toFixed(1) + 'M';
                        if (Math.abs(val) >= 10000) return (val / 1000).toFixed(0) + 'k';
                        return Number(val).toLocaleString();
                    }
                }
            };
        }

        return scales;
    }

    function initEmptyChart(w, el) {
        const type = w.widgetConfig.type || 'GRAPH';
        const body = el.querySelector('.grid-widget-body');
        if (!body) return;

        if (type === 'GRAPH' || type === 'BAR') {
            const fields = (w.widgetConfig.fields && w.widgetConfig.fields.length)
                ? w.widgetConfig.fields
                : [];

            const legendHtml = fields.map((field, idx) => {
                const color = getFieldColor(field, idx);
                return `<div class="d-flex align-items-center gap-1 small fw-bold" style="color: ${cssVar('--ink', '#334155')};">
                    <span style="display: inline-block; width: 10px; height: 10px; border-radius: 2px; background-color: ${color};"></span>
                    <span>${metricLabelWithUnit(field)}</span>
                </div>`;
            }).join('');

            body.className = 'card-body grid-widget-chart grid-widget-body p-2 d-flex flex-column';
            body.innerHTML = `
                <div class="chart-legend-header px-2 pb-1 d-flex flex-wrap gap-3 align-items-center border-bottom mb-1" style="flex-shrink: 0; background: ${cssVar('--surface', '#ffffff')};">
                    ${legendHtml}
                </div>
                <div class="chart-scroll-wrapper" style="width: 100%; height: 100%; overflow-x: auto; overflow-y: hidden; cursor: grab; scrollbar-width: none; -ms-overflow-style: none; flex: 1; position: relative;">
                    <div class="chart-inner-canvas" style="min-width: 100%; height: 100%; position: relative;">
                        <canvas class="main-canvas" style="width: 100%; height: 100%;"></canvas>
                    </div>
                </div>
            `;
            const canvas = body.querySelector('.main-canvas');
            destroyChart(w.uid);

            const chartType = (type === 'BAR') ? 'bar' : 'line';

            chartInstances[w.uid] = new Chart(canvas, {
                type: chartType,
                data: {
                    labels: [],
                    datasets: fields.map((field, idx) => {
                        const color = getFieldColor(field, idx);
                        const yAxisID = (fields.length > 1 && idx > 0) ? 'y1' : 'y';
                        return {
                            fieldKey: field,
                            label: metricLabelWithUnit(field),
                            data: [],
                            yAxisID: yAxisID,
                            borderColor: color,
                            backgroundColor: (type === 'BAR') ? color + 'b0' : color + '20',
                            borderWidth: (type === 'BAR') ? 1 : 1.5,
                            fill: idx === 0,
                            tension: 0.3,
                            pointRadius: 3,
                            pointHoverRadius: 5
                        };
                    })
                },
                options: {
                    responsive: true,
                    maintainAspectRatio: false,
                    animation: false,
                    plugins: {
                        legend: {display: false},
                        tooltip: {
                            mode: 'index',
                            intersect: false,
                            callbacks: {
                                title: function (items) {
                                    if (!items || !items.length) return '';
                                    return '🕒 ' + items[0].label;
                                }
                            }
                        }
                    },
                    scales: createSmartChartScales(fields)
                }
            });
        } else if (type === 'GAUGE' || type === 'SINGLE_STAT') {
            const gaugeUnit = metricUnit((w.widgetConfig.fields || [])[0]);
            body.className = 'card-body grid-widget-body p-2 d-flex flex-column align-items-center justify-content-center position-relative';
            body.innerHTML = `
                <div style="width: 100%; height: 75%; position: relative;">
                    <canvas style="width: 100%; height: 100%;"></canvas>
                </div>
                <div style="position: absolute; bottom: 15px; text-align: center;">
                    <span class="grid-widget-value fs-2 fw-bold text-primary">0</span>
                    <span class="grid-widget-unit text-muted">${gaugeUnit}</span>
                </div>
            `;
            const canvas = body.querySelector('canvas');
            destroyChart(w.uid);

            chartInstances[w.uid] = new Chart(canvas, {
                type: 'doughnut',
                data: {
                    labels: ['현재값', '잔여'],
                    datasets: [{
                        data: [0, 100],
                        backgroundColor: [cssVar('--primary', '#206bc4'), cssVar('--surface-alt', '#eef1f6')],
                        borderWidth: 0,
                        cutout: '75%'
                    }]
                },
                options: {
                    rotation: 270,
                    circumference: 180,
                    responsive: true,
                    maintainAspectRatio: false,
                    animation: false,
                    plugins: {legend: {display: false}}
                }
            });
        } else {
            const defaultUnit = metricUnit((w.widgetConfig.fields || [])[0]);
            body.className = 'card-body grid-widget-body p-2 d-flex flex-column align-items-center justify-content-center';
            body.innerHTML = `<span class="grid-widget-value fs-1 fw-bold">—</span><span class="grid-widget-unit text-muted">${defaultUnit}</span>`;
        }
    }

    function fetchAndRenderData(w, el) {
        const type = w.widgetConfig.type || 'GRAPH';
        const body = el.querySelector('.grid-widget-body');
        if (!body) return;

        fetch(`${BASE_URL}/widgets/${w.widgetId}/chart-data`)
            .then((r) => r.json())
            .then((res) => {
                const labels = res.timeLabels || [];
                const datasets = res.datasets || [];
                w.configDirty = false;

                if (type === 'GRAPH' || type === 'BAR') {
                    const legendHtml = datasets.map((ds, i) => {
                        const color = getFieldColor(ds.label, i);
                        return `<div class="d-flex align-items-center gap-1 small fw-bold" style="color: ${cssVar('--ink', '#334155')};">
                            <span style="display: inline-block; width: 10px; height: 10px; border-radius: 2px; background-color: ${color};"></span>
                            <span>${metricLabelWithUnit(ds.label)}</span>
                        </div>`;
                    }).join('');

                    body.className = 'card-body grid-widget-chart grid-widget-body p-2 d-flex flex-column';
                    body.innerHTML = `
                        <div class="chart-legend-header px-2 pb-1 d-flex flex-wrap gap-3 align-items-center border-bottom mb-1" style="flex-shrink: 0; background: ${cssVar('--surface', '#ffffff')};">
                            ${legendHtml}
                        </div>
                        <div class="chart-scroll-wrapper" style="width: 100%; height: 100%; overflow-x: auto; overflow-y: hidden; cursor: grab; scrollbar-width: none; -ms-overflow-style: none; flex: 1; position: relative;">
                            <div class="chart-inner-canvas" style="min-width: 100%; height: 100%; position: relative;">
                                <canvas class="main-canvas" style="width: 100%; height: 100%;"></canvas>
                            </div>
                        </div>
                    `;
                    const canvas = body.querySelector('.main-canvas');
                    if (!canvas) return;
                    destroyChart(w.uid);
                    const chartType = (type === 'BAR') ? 'bar' : 'line';
                    const fields = datasets.map(ds => ds.label);

                    chartInstances[w.uid] = new Chart(canvas, {
                        type: chartType,
                        data: {
                            labels,
                            datasets: datasets.map((ds, i) => {
                                const rawKey = ds.label;
                                const color = getFieldColor(rawKey, i);
                                const yAxisID = (datasets.length > 1 && i > 0) ? 'y1' : 'y';
                                return {
                                    fieldKey: rawKey,
                                    label: metricLabelWithUnit(rawKey),
                                    data: ds.data,
                                    yAxisID: yAxisID,
                                    borderColor: color,
                                    backgroundColor: (type === 'BAR') ? color + 'b0' : color + '20',
                                    borderWidth: 1.5,
                                    tension: 0.3,
                                    fill: i === 0,
                                    pointRadius: 3,
                                    pointHoverRadius: 5
                                };
                            })
                        },
                        options: {
                            responsive: true,
                            maintainAspectRatio: false,
                            plugins: {
                                legend: {display: false},
                                tooltip: {
                                    mode: 'index',
                                    intersect: false,
                                    callbacks: {
                                        title: function (items) {
                                            if (!items || !items.length) return '';
                                            return '🕒 ' + items[0].label;
                                        }
                                    }
                                }
                            },
                            scales: createSmartChartScales(fields)
                        }
                    });

                    w.lastLabelCount = labels.length;
                    adjustChartScroll(w, labels.length, true);
                    return;
                }

                const firstSeries = datasets[0]?.data || [];
                const latest = firstSeries.length ? firstSeries[firstSeries.length - 1] : null;
                const valueEl = body.querySelector('.grid-widget-value');
                if (valueEl) {
                    valueEl.textContent = latest === null || latest === undefined ? '0' : Number(latest).toLocaleString(undefined, {maximumFractionDigits: 1});
                }

                if ((type === 'GAUGE' || type === 'SINGLE_STAT') && chartInstances[w.uid] && latest !== null && latest !== undefined) {
                    const numericVal = Number(latest);
                    const maxVal = 100;
                    const fillVal = Math.min(Math.max(numericVal, 0), maxVal);
                    chartInstances[w.uid].data.datasets[0].data = [fillVal, maxVal - fillVal];
                    chartInstances[w.uid].update('quiet');
                }
            })
            .catch((err) => {
                console.warn('[InfluxDB Data Fetch Failed]', err);
            });
    }

    function destroyChart(uid) {
        if (chartInstances[uid]) {
            chartInstances[uid].destroy();
            delete chartInstances[uid];
        }
    }

    // ---------- GridStack layout events ----------

    grid.on('change', (event, items) => {
        (items || []).forEach((item) => {
            const w = state.find((x) => x.uid === String(item.id));
            if (!w) return;
            w.xPos = item.x;
            w.yPos = item.y;
            w.width = item.w;
            w.height = item.h;
            w.layoutDirty = true;  // 위치·크기 변경 — 데이터 재조회 불필요
            console.log(`[Widget Layout Changed] 위젯 (${w.uid}) 위치/크기 변경: x=${w.xPos}, y=${w.yPos}, w=${w.width}, h=${w.height}`);
            updateDim(w);
            adjustChartScroll(w, w.lastLabelCount || 0);
            if (chartInstances[w.uid]) {
                chartInstances[w.uid].resize();
            }
        });
    });

    grid.on('resizestop', (event, el) => {
        const uid = el.getAttribute('gs-id');
        console.log(`[Widget Resize Stopped] 위젯 (${uid}) 리사이즈 완료`);
        const w = state.find((x) => x.uid === String(uid));
        if (w) {
            adjustChartScroll(w, w.lastLabelCount || 0);
        }
        if (uid && chartInstances[uid]) {
            chartInstances[uid].resize();
        }
    });

    // ---------- config modal ----------

    function loadMetrics(sensorId, selectedFields) {
        metricListEl.innerHTML = `<p class="metric-check-empty">불러오는 중...</p>`;
        fetch(`/my-group/sensors/${sensorId}/attributes`)
            .then((r) => {
                if (!r.ok) throw new Error('attributes fetch failed');
                return r.json();
            })
            .then((attrs) => {
                // 새로 고른 센서의 정의도 캐시에 넣어둔다 - 위젯 적용 직후 카드/차트가 raw 키로
                // 잠깐이라도 안 보이고 바로 표시명/단위로 나오게
                (attrs || []).forEach((a) => {
                    metricDefsByKey[a.metricKey] = {displayName: a.displayName, unit: a.unit};
                });
                if (!attrs || !attrs.length) {
                    metricListEl.innerHTML = `<p class="metric-check-empty">이 센서에 등록된 메트릭이 없습니다.</p>`;
                    return;
                }
                metricListEl.innerHTML = attrs.map((a) => `
                    <div class="form-check">
                        <input class="form-check-input" type="checkbox" value="${a.metricKey}" id="metric-${a.metricKey}"
                               ${selectedFields.includes(a.metricKey) ? 'checked' : ''}>
                        <label class="form-check-label" for="metric-${a.metricKey}">${a.displayName || a.metricKey} ${a.unit ? `<span class="text-muted">(${a.unit})</span>` : ''}</label>
                    </div>
                `).join('');
            })
            .catch((err) => {
                console.warn('[loadMetrics] 센서 메트릭 조회 실패:', err);
                metricListEl.innerHTML = `<p class="metric-check-empty text-danger">메트릭 정보를 불러올 수 없습니다.</p>`;
            });
    }

    function openConfigModal(w) {
        editingUid = w.uid;
        const displayMode = (w.widgetConfig && w.widgetConfig.displayMode) || 'SCROLL';
        modalEl.querySelectorAll('input[name="widgetDisplayMode"]').forEach((r) => {
            r.checked = r.value === displayMode;
        });
        modalEl.querySelectorAll('input[name="widgetType"]').forEach((r) => {
            r.checked = r.value === w.widgetConfig.type;
        });
        sensorSelect.value = '';
        Array.from(sensorSelect.options).forEach((opt) => {
            if (opt.dataset.eui === w.widgetConfig.sensorEui) sensorSelect.value = opt.value;
        });
        rangeSelect.value = w.widgetConfig.range || '-1h';
        aggSelect.value = w.widgetConfig.aggregateWindow || '15m';

        if (sensorSelect.value) {
            loadMetrics(sensorSelect.value, w.widgetConfig.fields || []);
        } else {
            metricListEl.innerHTML = `<p class="metric-check-empty">먼저 센서를 선택하세요.</p>`;
        }
        modal.show();
    }

    sensorSelect.addEventListener('change', () => {
        if (sensorSelect.value) {
            loadMetrics(sensorSelect.value, []);
        } else {
            metricListEl.innerHTML = `<p class="metric-check-empty">먼저 센서를 선택하세요.</p>`;
        }
    });

    btnApplyConfig.addEventListener('click', () => {
        const w = state.find((x) => x.uid === editingUid);
        if (!w) return;

        const displayMode = modalEl.querySelector('input[name="widgetDisplayMode"]:checked')?.value || 'SCROLL';
        const type = modalEl.querySelector('input[name="widgetType"]:checked')?.value || 'GRAPH';
        const sensorOpt = sensorSelect.options[sensorSelect.selectedIndex];
        const sensorEui = sensorOpt ? sensorOpt.dataset.eui : null;

        const checkboxEls = metricListEl.querySelectorAll('input[type="checkbox"]');
        const fields = checkboxEls.length
            ? Array.from(checkboxEls).filter((c) => c.checked).map((c) => c.value)
            : (w.widgetConfig.fields || []);

        if (!sensorEui) {
            alert('센서를 선택해주세요.');
            return;
        }
        if (!fields || fields.length === 0) {
            alert('최소 1개 이상의 메트릭을 선택해 주세요.');
            return;
        }

        w.widgetConfig = {
            type,
            sensorEui: sensorEui || null,
            range: rangeSelect.value,
            aggregateWindow: aggSelect.value,
            fields,
            displayMode
        };
        w.configDirty = true;  // 설정 변경 — 저장 필요 + InfluxDB 재조회 필요

        modal.hide();
        updateLabel(w);
        const el = contentEl(w.uid);

        // renderWidgetBody는 configDirty=true일 때 fetchAndRenderData를 건너뛰므로
        // 여기서 명시적으로 호출해 InfluxDB에서 최신 데이터를 가져옴
        if (el) renderWidgetBody(w, el);
        if (w.widgetId && el) {
            fetchAndRenderData(w, el);
        }

        // 설정 적용 시 해당 센서 SSE 새로 구독
        const sensorId = (sensorOpt && sensorOpt.value) ? sensorOpt.value : (sensorIdByEui[sensorEui] || '1');
        if (sensorId) {
            subscribeWidgetSse(w, sensorId);
        }
    });

    // ---------- add / remove ----------

    if (btnAddWidget) {
        btnAddWidget.addEventListener('click', () => {
            const w = {
                uid: 'new' + (++uidCounter),
                widgetId: null,
                xPos: 0,
                yPos: nextFreeRow(),
                width: 4,
                height: 4,
                widgetConfig: {type: 'GRAPH', sensorEui: null, range: '-1h', aggregateWindow: '15m', fields: [], displayMode: 'SCROLL'}
            };
            state.push(w);
            addItemToGrid(w);
            renderAll();
        });
    }

    function removeWidget(uid) {
        if (!confirm('이 위젯을 삭제할까요?')) return;
        destroyChart(uid);

        if (sseConnections[uid]) {
            sseConnections[uid].close();
            delete sseConnections[uid];
        }

        const el = itemEl(uid);
        if (el) grid.removeWidget(el);
        const idx = state.findIndex((w) => w.uid === uid);
        if (idx >= 0) state.splice(idx, 1);
        renderAll();
    }

    // ---------- save ----------

    if (btnSaveLayout) {
        btnSaveLayout.addEventListener('click', () => {
        const payload = state.map((w) => ({
            widgetId: w.widgetId,
            xPos: w.xPos,
            yPos: w.yPos,
            width: w.width,
            height: w.height,
            widgetConfig: {...w.widgetConfig, groupId: GROUP_ID, locationId: LOCATION_ID}
        }));

        saveStatusEl.style.display = 'flex';
        saveStatusEl.textContent = '저장 중...';
        btnSaveLayout.disabled = true;

        fetch(`${BASE_URL}/save`, {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(payload)
        })
            .then((r) => {
                if (!r.ok) throw new Error(`save failed with status ${r.status}`);
                return r.json();
            })
            .then((savedWidgetIds) => {
                // 저장 성공: 서버에서 반환된 widgetId를 state에 반영 (신규 위젯 ID 확정)
                if (Array.isArray(savedWidgetIds)) {
                    state.forEach((w, idx) => {
                        if (savedWidgetIds[idx] != null) {
                            w.widgetId = savedWidgetIds[idx];
                            // uid도 widgetId 기반으로 갱신 (gridstack gs-id는 유지)
                        }
                    });
                }

                // dirty 플래그 초기화
                state.forEach((w) => {
                    const wasDirty = w.layoutDirty || w.configDirty;
                    w.layoutDirty = false;
                    w.configDirty = false;

                    // 설정이 변경됐던 위젯은 InfluxDB 데이터 즉시 재조회
                    if (wasDirty && w.widgetId) {
                        const el = contentEl(w.uid);
                        if (el) {
                            console.log(`[Save] 위젯 (${w.uid}) 저장 완료, InfluxDB 데이터 재조회`);
                            fetchAndRenderData(w, el);
                        }
                    }
                });

                saveStatusEl.textContent = '저장 완료 ✓';
                btnSaveLayout.disabled = false;
                setTimeout(() => { saveStatusEl.style.display = 'none'; }, 2000);
                console.log('[Dashboard Save] 저장 완료, widgetIds:', savedWidgetIds);
            })
            .catch((err) => {
                console.error('[Dashboard Save] 저장 실패 원인:', err);
                saveStatusEl.textContent = '저장에 실패했어요. 잠시 후 다시 시도해주세요.';
                btnSaveLayout.disabled = false;
            });
        });
    }

    // ---------- init ----------

    // 위젯 카드/차트를 그리기 전에 메트릭 표시명·단위부터 채워둬야, 첫 렌더부터 raw 키(temperature)가
    // 아니라 "온도 (°C)"로 바로 보인다
    const widgetSensorIds = state.map((w) => sensorIdByEui[w.widgetConfig.sensorEui]);
    loadMetricDefs(widgetSensorIds).then(() => {
        state.forEach((w) => addItemToGrid(w));
        renderAll();
    });
})();
