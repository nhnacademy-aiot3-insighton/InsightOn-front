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
        }}
    ];
    const links = [
        {sourceClientNodeKey: 'trigger', targetClientNodeKey: 'filter', sourcePort: 'out', targetPort: 'in'},
        {sourceClientNodeKey: 'filter', targetClientNodeKey: 'gate', sourcePort: 'true', targetPort: 'in'},
        {sourceClientNodeKey: 'gate', targetClientNodeKey: 'power', sourcePort: 'true', targetPort: 'in'},
        {sourceClientNodeKey: 'gate', targetClientNodeKey: 'temperature', sourcePort: 'true', targetPort: 'in'}
    ];

    const parsed = core.parseFlow(nodes, links, rules);
    assert(parsed !== null, '지원하는 EventGate와 액추에이터 fan-out을 해석해야 한다.');
    assert(parsed.gate.clientNodeKey === 'gate', 'EventGate를 별도 단계로 유지해야 한다.');
    assert(parsed.action.supplementalTemperatureValue === '24', '보조 온도 동작을 편집 값으로 병합해야 한다.');

    const hiddenBranch = links.concat({
        sourceClientNodeKey: 'filter',
        targetClientNodeKey: 'power',
        sourcePort: 'false',
        targetPort: 'in'
    });
    assert(core.parseFlow(nodes, hiddenBranch, rules) === null,
        '화면에서 표현하지 못하는 추가 분기가 있으면 편집을 차단해야 한다.');

    assert(core.minutesToSeconds(5) === 300, '분을 초로 변환해야 한다.');
    assert(core.minutesToSeconds(0.001) === 0, '1초 미만 값은 호출부가 거절할 수 있도록 0을 반환해야 한다.');
    assert(core.minutesToSeconds(Number.MAX_VALUE) === null, '백엔드 정수 범위를 넘는 값은 거절해야 한다.');

    return 'flow-editor-core tests passed';
})(typeof globalThis !== 'undefined' ? globalThis : this);
