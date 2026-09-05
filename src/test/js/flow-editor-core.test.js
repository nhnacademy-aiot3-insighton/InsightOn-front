(function (root) {
    'use strict';

    const core = typeof require === 'function'
        ? require('../../main/resources/static/js/flow-editor-core.js')
        : root.FlowEditorCore;

    function assert(condition, message) {
        if (!condition) throw new Error(message);
    }

    const rules = {
        AIRCON: {
            POWER_STATUS: {stateKey: 'power', kind: 'SELECT', values: ['ON', 'OFF']},
            SET_TEMPERATURE: {stateKey: 'temperature', kind: 'RANGE', min: 16, max: 30}
        },
        AIR_PURIFIER: {
            POWER_STATUS: {stateKey: 'power', kind: 'SELECT', values: ['ON', 'OFF']}
        }
    };
    const nodes = [
        {clientNodeKey: 'trigger', nodeType: 'LOCATION', configuration: {}},
        {clientNodeKey: 'filter', nodeType: 'THRESHOLD', configuration: {expression: "#metrics['temperature'] > 30"}},
        {clientNodeKey: 'gate', nodeType: 'EVENT_GATE', configuration: {
            requiredCount: 3,
            countWindowSeconds: 300,
            cooldownSeconds: 1800
        }},
        {clientNodeKey: 'power', nodeType: 'ACTUATOR_CONTROL', configuration: {
            actuatorType: 'AIRCON', command: 'power', commandValue: 'ON'
        }},
        {clientNodeKey: 'temperature', nodeType: 'ACTUATOR_CONTROL', configuration: {
            actuatorType: 'AIRCON', command: 'temperature', commandValue: '24'
        }},
        {clientNodeKey: 'purifier', nodeType: 'ACTUATOR_CONTROL', configuration: {
            actuatorType: 'AIR_PURIFIER', command: 'power', commandValue: 'ON'
        }}
    ];
    const links = [
        {sourceClientNodeKey: 'trigger', targetClientNodeKey: 'filter', sourcePort: 'out', targetPort: 'in'},
        {sourceClientNodeKey: 'filter', targetClientNodeKey: 'gate', sourcePort: 'true', targetPort: 'in'},
        {sourceClientNodeKey: 'gate', targetClientNodeKey: 'power', sourcePort: 'true', targetPort: 'in'},
        {sourceClientNodeKey: 'gate', targetClientNodeKey: 'temperature', sourcePort: 'true', targetPort: 'in'},
        {sourceClientNodeKey: 'gate', targetClientNodeKey: 'purifier', sourcePort: 'true', targetPort: 'in'}
    ];

    const parsed = core.parseFlow(nodes, links, rules);
    assert(parsed !== null, '지원하는 EventGate와 액추에이터 fan-out을 해석해야 한다.');
    assert(parsed.gate.clientNodeKey === 'gate', 'EventGate를 별도 단계로 유지해야 한다.');
    assert(parsed.actions.length === 2, '서로 다른 기기의 다중 동작을 편집 카드 배열로 유지해야 한다.');
    assert(parsed.actions[0].supplementalTemperatureValue === '24', '보조 온도 동작을 편집 값으로 병합해야 한다.');
    assert(parsed.actions[1].configuration.actuatorType === 'AIR_PURIFIER', '추가 기기 동작의 순서를 유지해야 한다.');

    const timeWindow = {
        clientNodeKey: 'time-window',
        nodeType: 'TIME_WINDOW',
        configuration: {startTime: '09:00:00', endTime: '18:00:00'}
    };
    const nodesWithTimeWindow = [nodes[0], timeWindow].concat(nodes.slice(1));
    const linksWithTimeWindow = [
        {sourceClientNodeKey: 'trigger', targetClientNodeKey: 'time-window', sourcePort: 'out', targetPort: 'in'},
        {sourceClientNodeKey: 'time-window', targetClientNodeKey: 'filter', sourcePort: 'true', targetPort: 'in'}
    ].concat(links.slice(1));
    const parsedWithTimeWindow = core.parseFlow(nodesWithTimeWindow, linksWithTimeWindow, rules);
    assert(parsedWithTimeWindow !== null, '운영 시간부터 시작하는 정규 조건 경로를 해석해야 한다.');
    assert(parsedWithTimeWindow.timeWindow.clientNodeKey === 'time-window',
        'TIME_WINDOW를 측정값 조건과 구분해 편집 값으로 유지해야 한다.');
    assert(parsedWithTimeWindow.filters.length === 1 && parsedWithTimeWindow.gate.clientNodeKey === 'gate',
        'TIME_WINDOW 뒤의 측정값 조건과 안전장치를 모두 유지해야 한다.');

    const normalWindow = core.parseTimeWindowConfiguration({startTime: '09:00:00', endTime: '18:00'});
    assert(normalWindow !== null && normalWindow.startTime === '09:00' && normalWindow.endTime === '18:00',
        '분 단위와 0초 표기를 같은 LocalTime 값으로 정규화해야 한다.');
    assert(normalWindow.overnight === false, '시작이 빠른 운영 시간은 당일 범위여야 한다.');

    const overnightWindow = core.parseTimeWindowConfiguration({startTime: '22:00', endTime: '06:00:30'});
    assert(overnightWindow !== null && overnightWindow.overnight === true,
        '종료가 더 이른 운영 시간은 자정을 지나는 정상 범위여야 한다.');
    assert(overnightWindow.endTime === '06:00:30', '0이 아닌 기존 초 단위 시각을 보존해야 한다.');
    assert(core.parseTimeWindowConfiguration({startTime: '09:00', endTime: '09:00:00'}) === null,
        '표기만 다른 동일 시각은 24시간 범위로 해석하지 않고 거절해야 한다.');
    assert(core.parseTimeWindowConfiguration({startTime: '24:00', endTime: '06:00'}) === null,
        'LocalTime 범위를 벗어나는 시각을 거절해야 한다.');
    assert(core.parseTimeWindowConfiguration({startTime: '', endTime: '06:00'}) === null,
        '시작 또는 종료 시각이 비어 있으면 거절해야 한다.');

    const hiddenBranch = links.concat({
        sourceClientNodeKey: 'filter',
        targetClientNodeKey: 'power',
        sourcePort: 'false',
        targetPort: 'in'
    });
    assert(core.parseFlow(nodes, hiddenBranch, rules) === null,
        '화면에서 표현하지 못하는 추가 분기가 있으면 편집을 차단해야 한다.');

    const duplicateTimeWindow = {
        clientNodeKey: 'time-window-2',
        nodeType: 'TIME_WINDOW',
        configuration: {startTime: '10:00', endTime: '17:00'}
    };
    assert(core.parseFlow(
        [nodes[0], timeWindow, duplicateTimeWindow, nodes[3]],
        [
            {sourceClientNodeKey: 'trigger', targetClientNodeKey: 'time-window', sourcePort: 'out', targetPort: 'in'},
            {sourceClientNodeKey: 'time-window', targetClientNodeKey: 'time-window-2', sourcePort: 'true', targetPort: 'in'},
            {sourceClientNodeKey: 'time-window-2', targetClientNodeKey: 'power', sourcePort: 'true', targetPort: 'in'}
        ],
        rules
    ) === null, '폼이 표현하지 못하는 TIME_WINDOW 중복은 편집을 차단해야 한다.');

    assert(core.parseFlow(
        [nodes[0], nodes[1], timeWindow, nodes[3]],
        [
            {sourceClientNodeKey: 'trigger', targetClientNodeKey: 'filter', sourcePort: 'out', targetPort: 'in'},
            {sourceClientNodeKey: 'filter', targetClientNodeKey: 'time-window', sourcePort: 'true', targetPort: 'in'},
            {sourceClientNodeKey: 'time-window', targetClientNodeKey: 'power', sourcePort: 'true', targetPort: 'in'}
        ],
        rules
    ) === null, '저장 시 순서가 바뀌는 THRESHOLD 뒤 TIME_WINDOW는 편집을 차단해야 한다.');

    assert(core.parseFlow(
        [nodes[0], timeWindow, nodes[3]],
        [
            {sourceClientNodeKey: 'trigger', targetClientNodeKey: 'time-window', sourcePort: 'out', targetPort: 'in'},
            {sourceClientNodeKey: 'time-window', targetClientNodeKey: 'power', sourcePort: 'true', targetPort: 'in'},
            {sourceClientNodeKey: 'time-window', targetClientNodeKey: 'power', sourcePort: 'false', targetPort: 'in'}
        ],
        rules
    ) === null, '폼이 표시하지 않는 TIME_WINDOW false 분기를 삭제하지 않도록 편집을 차단해야 한다.');

    function parseActionFanOut(actionNodes) {
        const fanOutNodes = [{clientNodeKey: 'trigger', nodeType: 'LOCATION', configuration: {}}]
            .concat(actionNodes);
        const fanOutLinks = actionNodes.map((node) => ({
            sourceClientNodeKey: 'trigger',
            targetClientNodeKey: node.clientNodeKey,
            sourcePort: 'out',
            targetPort: 'in'
        }));
        return core.parseFlow(fanOutNodes, fanOutLinks, rules);
    }

    assert(parseActionFanOut([nodes[4], nodes[3]]) === null,
        '온도 동작이 전원 동작보다 먼저인 순서를 임의로 바꾸어 병합하지 않아야 한다.');
    assert(parseActionFanOut([nodes[3], nodes[5], nodes[4]]) === null,
        '사이에 다른 동작이 있는 전원·온도 노드를 합쳐 실행 순서를 바꾸지 않아야 한다.');

    function parseScheduleFlow(middleNodes, actionNodes) {
        const schedule = {
            clientNodeKey: 'schedule',
            nodeType: 'SCHEDULE',
            configuration: {cron: '0 0 9 * * *'}
        };
        const allNodes = [schedule].concat(middleNodes, actionNodes);
        const chain = [schedule].concat(middleNodes);
        const last = chain[chain.length - 1];
        const scheduleLinks = [];
        for (let i = 0; i < chain.length - 1; i++) {
            scheduleLinks.push({
                sourceClientNodeKey: chain[i].clientNodeKey,
                targetClientNodeKey: chain[i + 1].clientNodeKey,
                sourcePort: i === 0 ? 'out' : 'true',
                targetPort: 'in'
            });
        }
        actionNodes.forEach((action) => scheduleLinks.push({
            sourceClientNodeKey: last.clientNodeKey,
            targetClientNodeKey: action.clientNodeKey,
            sourcePort: middleNodes.length ? 'true' : 'out',
            targetPort: 'in'
        }));
        return core.parseFlow(allNodes, scheduleLinks, rules);
    }

    assert(parseScheduleFlow([nodes[1]], [nodes[5]]) === null,
        '예약 폼이 표현할 수 없는 조건 노드가 있으면 편집을 차단해야 한다.');
    assert(parseScheduleFlow([nodes[2]], [nodes[5]]) === null,
        '예약 폼이 표현할 수 없는 안전장치 노드가 있으면 편집을 차단해야 한다.');
    assert(parseScheduleFlow([timeWindow], [nodes[5]]) === null,
        '엔진에서 허용하지 않는 예약 시작과 TIME_WINDOW 조합을 편집하지 않아야 한다.');
    assert(parseScheduleFlow([], [{
        clientNodeKey: 'alert',
        nodeType: 'ALERT',
        configuration: {title: '예약', severity: 'INFO', message: '알림'}
    }]) === null, '예약 폼이 기기 제어가 아닌 동작을 임의로 변경하지 않아야 한다.');

    assert(core.minutesToSeconds(5) === 300, '분을 초로 변환해야 한다.');
    assert(core.minutesToSeconds(0.001) === 0, '1초 미만 값은 호출부가 거절할 수 있도록 0을 반환해야 한다.');
    assert(core.minutesToSeconds(Number.MAX_VALUE) === null, '백엔드 정수 범위를 넘는 값은 거절해야 한다.');

    const weekdaySchedule = core.parseCron('0 30 8 * * MON-FRI');
    assert(weekdaySchedule !== null && weekdaySchedule.repeatType === 'WEEKLY',
        '요일 이름과 범위로 만든 예약을 폼 값으로 해석해야 한다.');
    assert(weekdaySchedule.weekdays.join(',') === '1,2,3,4,5',
        '평일 범위를 월요일부터 금요일까지 펼쳐야 한다.');
    assert(core.buildCron({repeatType: 'MONTHLY', hour: 9, minute: 5, day: 10, weekdays: []})
        === '0 5 9 10 * *', '폼의 매월 예약을 Spring cron으로 변환해야 한다.');
    assert(core.parseCron('0 */15 * * * *') === null,
        '폼으로 손실 없이 표현할 수 없는 예약은 해석 실패로 처리해 저장을 차단해야 한다.');
    assert(core.parseCron('0 0 9 * * 0-7').weekdays.join(',') === '0,1,2,3,4,5,6',
        'Spring이 주 7일로 해석하는 0-7 요일 범위를 일요일로 축소하지 않아야 한다.');
    assert(core.parseCron('0 0 9 * * 7-7').weekdays.join(',') === '0,1,2,3,4,5,6',
        'Spring의 숫자 7-7 범위도 일요일 하나가 아닌 주 7일로 유지해야 한다.');
    assert(core.parseCron('0 0 9 * * 6-1') === null,
        'Spring이 거절하는 역방향 요일 범위를 임의로 순환 범위로 바꾸지 않아야 한다.');

    return 'flow-editor-core tests passed';
})(typeof globalThis !== 'undefined' ? globalThis : this);
