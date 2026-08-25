package com.nhnacademy.insightonfront.controller.ruleengine;

import com.nhnacademy.insightonfront.adapter.core.sensor.SensorClient;
import com.nhnacademy.insightonfront.adapter.core.sensor.dto.SensorResponse;
import com.nhnacademy.insightonfront.adapter.core.sensorattribute.SensorAttributeClient;
import com.nhnacademy.insightonfront.adapter.core.sensorattribute.dto.SensorAttributeResponse;
import com.nhnacademy.insightonfront.adapter.ruleengine.flow.FlowClient;
import com.nhnacademy.insightonfront.adapter.ruleengine.flow.dto.FlowCreateRequest;
import com.nhnacademy.insightonfront.adapter.ruleengine.flow.dto.FlowResponse;
import com.nhnacademy.insightonfront.adapter.ruleengine.flow.dto.FlowStatus;
import com.nhnacademy.insightonfront.adapter.ruleengine.flow.dto.FlowStatusChangeRequest;
import com.nhnacademy.insightonfront.adapter.ruleengine.flow.dto.FlowUpdateRequest;
import com.nhnacademy.insightonfront.domain.flow.dto.FlowDetailViewModel;
import com.nhnacademy.insightonfront.domain.flow.dto.FlowEditViewModel;
import com.nhnacademy.insightonfront.domain.flow.dto.FlowErrorResponse;
import com.nhnacademy.insightonfront.domain.flow.dto.FlowTrashEmptyResult;
import com.nhnacademy.insightonfront.domain.flow.dto.FlowViewModel;
import com.nhnacademy.insightonfront.domain.flow.service.FlowPermissionService;
import com.nhnacademy.insightonfront.domain.flow.service.FlowViewService;
import jakarta.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.List;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * groupId는 쿠키에서 읽는다(한 유저 = 한 그룹). userId는 게이트웨이가 Authorization에서 뽑아 쓴다.
 * status를 지정하지 않으면 Rule Engine이 ARCHIVED만 제외하고 돌려준다(ACTIVE/INACTIVE/ERROR).
 */
@Controller
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/my-group/flows")
public class FlowController {

    private final FlowViewService flowViewService;
    private final FlowPermissionService flowPermissionService;
    private final FlowClient flowClient;
    private final SensorClient sensorClient;
    private final SensorAttributeClient sensorAttributeClient;
    private final ObjectMapper objectMapper;

    @GetMapping
    public String list(@CookieValue(value = "userId", required = false) Long userId,
                        @CookieValue(value = "groupId", required = false) Long groupId,
                        @RequestParam(required = false) FlowStatus status,
                        Model model, HttpServletResponse response) {
        if (userId == null || groupId == null) {
            return "redirect:/login";
        }
        try {
            List<FlowViewModel> flows = flowViewService.getFlows(groupId, status);
            model.addAttribute("flows", flows);
            model.addAttribute("selectedStatus", status);
            model.addAttribute("canManage", flowPermissionService.isManagerOrAbove(groupId, userId));
            return "flow/list";
        } catch (ResponseStatusException exception) {
            return renderFlowError(response, model, toErrorResponse(exception));
        } catch (FeignException exception) {
            return renderFlowError(response, model, toErrorResponse(exception));
        }
    }

    @GetMapping("/trash")
    public String trash(@CookieValue(value = "userId", required = false) Long userId,
                        @CookieValue(value = "groupId", required = false) Long groupId,
                        Model model, HttpServletResponse response) {
        if (userId == null || groupId == null) {
            return "redirect:/login";
        }
        try {
            model.addAttribute("flows", flowViewService.getFlows(groupId, FlowStatus.ARCHIVED));
            model.addAttribute("canManage", flowPermissionService.isManagerOrAbove(groupId, userId));
            return "flow/trash";
        } catch (ResponseStatusException exception) {
            return renderFlowError(response, model, toErrorResponse(exception));
        } catch (FeignException exception) {
            return renderFlowError(response, model, toErrorResponse(exception));
        }
    }

    @GetMapping("/new")
    public String newFlow(@CookieValue(value = "userId", required = false) Long userId,
                           @CookieValue(value = "groupId", required = false) Long groupId,
                           Model model, HttpServletResponse response) {
        if (userId == null || groupId == null) {
            return "redirect:/login";
        }
        try {
            flowPermissionService.requireManagerOrAbove(groupId, userId);
            List<SensorResponse> sensors = sensorClient.search(groupId, null, null, null, null);
            model.addAttribute("mode", "create");
            model.addAttribute("flow", null);
            model.addAttribute("sensors", sensors);
            return "flow/editor";
        } catch (ResponseStatusException exception) {
            return renderFlowError(response, model, toErrorResponse(exception));
        } catch (FeignException exception) {
            return renderFlowError(response, model, toErrorResponse(exception));
        }
    }

    @GetMapping("/{flowId}/edit")
    public String editFlow(@CookieValue(value = "userId", required = false) Long userId,
                            @CookieValue(value = "groupId", required = false) Long groupId,
                            @PathVariable Long flowId,
                            Model model, HttpServletResponse response) {
        if (userId == null || groupId == null) {
            return "redirect:/login";
        }
        try {
            flowPermissionService.requireManagerOrAbove(groupId, userId);
            FlowEditViewModel flow = flowViewService.getFlowForEdit(flowId, groupId);
            List<SensorResponse> sensors = sensorClient.search(groupId, null, null, null, null);
            model.addAttribute("mode", "edit");
            model.addAttribute("flow", flow);
            model.addAttribute("sensors", sensors);
            return "flow/editor";
        } catch (ResponseStatusException exception) {
            return renderFlowError(response, model, toErrorResponse(exception));
        } catch (FeignException exception) {
            return renderFlowError(response, model, toErrorResponse(exception));
        }
    }

    @GetMapping("/{flowId}")
    public String detail(@CookieValue(value = "userId", required = false) Long userId,
                          @CookieValue(value = "groupId", required = false) Long groupId,
                          @PathVariable Long flowId,
                          Model model, HttpServletResponse response) {
        if (userId == null || groupId == null) {
            return "redirect:/login";
        }
        try {
            FlowDetailViewModel flow = flowViewService.getFlow(flowId, groupId);
            model.addAttribute("flow", flow);
            model.addAttribute("canManage", flowPermissionService.isManagerOrAbove(groupId, userId));
            return "flow/detail";
        } catch (ResponseStatusException exception) {
            return renderFlowError(response, model, toErrorResponse(exception));
        } catch (FeignException exception) {
            return renderFlowError(response, model, toErrorResponse(exception));
        }
    }

    @GetMapping("/sensors/{sensorId}/attributes")
    @ResponseBody
    public List<SensorAttributeResponse> sensorAttributes(
            @CookieValue(value = "groupId", required = false) Long groupId,
            @PathVariable Long sensorId) {
        if (groupId == null) {
            return List.of();
        }
        return sensorAttributeClient.getSensorAttribute(sensorId);
    }

    @PostMapping
    @ResponseBody
    public FlowResponse create(@CookieValue("groupId") Long groupId,
                                @CookieValue(value = "userId", required = false) Long userId,
                                @RequestBody FlowCreateRequest request) {
        flowPermissionService.requireManagerOrAbove(groupId, userId);
        return flowClient.create(groupId, request);
    }

    @PutMapping("/{flowId}")
    @ResponseBody
    public FlowResponse update(@CookieValue("groupId") Long groupId,
                                @CookieValue(value = "userId", required = false) Long userId,
                                @PathVariable Long flowId,
                                @RequestBody FlowUpdateRequest request) {
        flowPermissionService.requireManagerOrAbove(groupId, userId);
        return flowClient.update(flowId, groupId, request);
    }

    @PutMapping("/{flowId}/status")
    @ResponseBody
    public void changeStatus(@CookieValue("groupId") Long groupId,
                              @CookieValue(value = "userId", required = false) Long userId,
                              @PathVariable Long flowId,
                              @RequestBody FlowStatusChangeRequest request) {
        flowPermissionService.requireManagerOrAbove(groupId, userId);
        flowClient.changeStatus(flowId, groupId, request);
    }

    @PostMapping("/{flowId}/archive")
    @ResponseBody
    public void archive(@CookieValue("groupId") Long groupId,
                        @CookieValue(value = "userId", required = false) Long userId,
                        @PathVariable Long flowId) {
        flowPermissionService.requireManagerOrAbove(groupId, userId);
        flowClient.archive(flowId, groupId);
    }

    @PostMapping("/{flowId}/restore")
    @ResponseBody
    public void restore(@CookieValue("groupId") Long groupId,
                        @CookieValue(value = "userId", required = false) Long userId,
                        @PathVariable Long flowId) {
        flowPermissionService.requireManagerOrAbove(groupId, userId);
        flowClient.restore(flowId, groupId);
    }

    @DeleteMapping("/{flowId}")
    @ResponseBody
    public void delete(@CookieValue("groupId") Long groupId,
                       @CookieValue(value = "userId", required = false) Long userId,
                       @PathVariable Long flowId) {
        flowPermissionService.requireManagerOrAbove(groupId, userId);
        flowClient.delete(flowId, groupId);
    }

    @DeleteMapping("/trash")
    @ResponseBody
    public FlowTrashEmptyResult emptyTrash(@CookieValue("groupId") Long groupId,
                                           @CookieValue(value = "userId", required = false) Long userId) {
        flowPermissionService.requireManagerOrAbove(groupId, userId);
        List<FlowViewModel> archivedFlows = flowViewService.getFlows(groupId, FlowStatus.ARCHIVED);
        List<Long> deletedFlowIds = new ArrayList<>();
        List<Long> failedFlowIds = new ArrayList<>();
        for (FlowViewModel flow : archivedFlows) {
            try {
                flowClient.delete(flow.flowId(), groupId);
                deletedFlowIds.add(flow.flowId());
            } catch (RuntimeException exception) {
                failedFlowIds.add(flow.flowId());
                log.warn("Flow 휴지통 비우기 중 영구 삭제 실패: flowId={}", flow.flowId(), exception);
            }
        }
        return new FlowTrashEmptyResult(List.copyOf(deletedFlowIds), List.copyOf(failedFlowIds));
    }

    /** Gateway/Rule Engine의 상태 코드와 메시지를 Browser까지 보존한다(AJAX 호출용 JSON). */
    @ExceptionHandler(FeignException.class)
    @ResponseBody
    public ResponseEntity<FlowErrorResponse> handleFeignException(FeignException exception) {
        FlowErrorResponse error = toErrorResponse(exception);
        return ResponseEntity.status(HttpStatusCode.valueOf(error.status())).body(error);
    }

    @ExceptionHandler(ResponseStatusException.class)
    @ResponseBody
    public ResponseEntity<FlowErrorResponse> handleResponseStatusException(ResponseStatusException exception) {
        FlowErrorResponse error = toErrorResponse(exception);
        return ResponseEntity.status(error.status()).body(error);
    }

    private FlowErrorResponse toErrorResponse(FeignException exception) {
        int status = exception.status() >= 400 && exception.status() <= 599
                ? exception.status()
                : HttpStatus.BAD_GATEWAY.value();
        // Rule Engine 원본 메시지에는 groupId/flowId/locationId 같은 내부 값이 그대로 들어있어
        // 사용자에게 보여주지 않고, 자주 나오는 상황(409 이름 중복, 404 없는 Flow)은 안내 문구로 바꾼다.
        String message = switch (status) {
            case 409 -> "이미 사용 중인 이름이에요. 다른 이름으로 저장해주세요.";
            case 404 -> "요청하신 자동화를 찾을 수 없어요. 삭제되었거나 잘못된 주소예요.";
            default -> extractDownstreamMessage(exception);
        };
        return new FlowErrorResponse(status, message);
    }

    private FlowErrorResponse toErrorResponse(ResponseStatusException exception) {
        int status = exception.getStatusCode().value();
        String message = exception.getReason() == null ? "요청을 처리하지 못했습니다." : exception.getReason();
        return new FlowErrorResponse(status, message);
    }

    /** 페이지 이동(GET) 중 발생한 오류는 JSON이 아니라 화면(전역 error.html)으로 보여준다. */
    private String renderFlowError(HttpServletResponse response, Model model, FlowErrorResponse error) {
        response.setStatus(error.status());
        model.addAttribute("status", error.status());
        model.addAttribute("errorMessage", error.message());
        return "error";
    }

    private String extractDownstreamMessage(FeignException exception) {
        try {
            JsonNode body = objectMapper.readTree(exception.contentUTF8());
            String message = body.path("message").asText(null);
            if (message != null && !message.isBlank()) {
                return message;
            }
        } catch (RuntimeException ignored) {
            // 응답 본문이 JSON이 아니면 사용자에게 내부 내용을 그대로 노출하지 않는다.
        }
        return exception.status() < 0
                ? "연결된 서비스를 호출하지 못했습니다."
                : "Flow 요청을 처리하지 못했습니다.";
    }
}
