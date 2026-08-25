package com.nhnacademy.insightonfront.adapter.core.actuator;

import com.nhnacademy.insightonfront.adapter.core.actuator.dto.ActuatorType;
import com.nhnacademy.insightonfront.adapter.core.actuator.dto.CommandType;
import com.nhnacademy.insightonfront.adapter.core.actuator.dto.CommandValueRule;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;

/**
 * core의 ActuatorCommandPreset을 그대로 미러링한다 — 실제 검증은 core가 최종적으로 하고,
 * 여기서는 조작 화면이 액추에이터 타입별로 어떤 명령·값 위젯을 그릴지 결정하는 데만 쓴다.
 * core는 {@code Set.of}/{@code Map.of}를 쓰지만 여기서는 화면에 매번 같은 순서로 그리기 위해
 * 순서가 보장되는 컬렉션으로 옮겨 담는다.
 */
public final class ActuatorCommandPreset {

    private ActuatorCommandPreset() {}

    public static final Map<ActuatorType, Map<CommandType, CommandValueRule>> RULES = orderedRules();

    private static Map<ActuatorType, Map<CommandType, CommandValueRule>> orderedRules() {
        Map<ActuatorType, Map<CommandType, CommandValueRule>> rules = new LinkedHashMap<>();

        Map<CommandType, CommandValueRule> aircon = new LinkedHashMap<>();
        aircon.put(CommandType.POWER_STATUS, new CommandValueRule.AllowedValues(new LinkedHashSet<>(java.util.List.of("ON", "OFF"))));
        aircon.put(CommandType.OPERATION_MODE, new CommandValueRule.AllowedValues(new LinkedHashSet<>(java.util.List.of("COOL", "DRY", "FAN", "AUTO"))));
        aircon.put(CommandType.SET_TEMPERATURE, new CommandValueRule.NumericRange(18, 30));
        rules.put(ActuatorType.AIRCON, aircon);

        Map<CommandType, CommandValueRule> airPurifier = new LinkedHashMap<>();
        airPurifier.put(CommandType.POWER_STATUS, new CommandValueRule.AllowedValues(new LinkedHashSet<>(java.util.List.of("ON", "OFF"))));
        airPurifier.put(CommandType.OPERATION_MODE, new CommandValueRule.AllowedValues(new LinkedHashSet<>(java.util.List.of("AUTO", "SLEEP", "TURBO"))));
        rules.put(ActuatorType.AIR_PURIFIER, airPurifier);

        Map<CommandType, CommandValueRule> ventilationFan = new LinkedHashMap<>();
        ventilationFan.put(CommandType.POWER_STATUS, new CommandValueRule.AllowedValues(new LinkedHashSet<>(java.util.List.of("ON", "OFF"))));
        ventilationFan.put(CommandType.OPERATION_MODE, new CommandValueRule.AllowedValues(new LinkedHashSet<>(java.util.List.of("LOW", "MID", "HIGH"))));
        rules.put(ActuatorType.VENTILATION_FAN, ventilationFan);

        return rules;
    }

    /** Thymeleaf/Model에 얹기 쉬운 평범한 Map 모양으로 펼친다 (sealed record는 그대로 직렬화하면 형태가 지저분해짐). */
    public static Map<String, Object> forTemplate(ActuatorType type) {
        Map<String, Object> commands = new LinkedHashMap<>();
        RULES.get(type).forEach((commandType, rule) -> {
            Map<String, Object> widget = new LinkedHashMap<>();
            widget.put("stateKey", commandType.getStateKey()); // 화면(panel.html)이 실제 통신 키를 알 수 있도록 노출
            if (rule instanceof CommandValueRule.AllowedValues allowed) {
                widget.put("kind", "SELECT");
                widget.put("values", allowed.values());
            } else if (rule instanceof CommandValueRule.NumericRange range) {
                widget.put("kind", "RANGE");
                widget.put("min", range.min());
                widget.put("max", range.max());
            }
            commands.put(commandType.name(), widget);
        });
        return commands;
    }
}
