package com.nhnacademy.insightonfront.adapter.ruleengine.flow;

import com.nhnacademy.insightonfront.adapter.ruleengine.flow.dto.FlowCreateRequest;
import com.nhnacademy.insightonfront.adapter.ruleengine.flow.dto.FlowDefinitionResponse;
import com.nhnacademy.insightonfront.adapter.ruleengine.flow.dto.FlowResponse;
import com.nhnacademy.insightonfront.adapter.ruleengine.flow.dto.FlowStatus;
import com.nhnacademy.insightonfront.adapter.ruleengine.flow.dto.FlowStatusChangeRequest;
import com.nhnacademy.insightonfront.adapter.ruleengine.flow.dto.FlowUpdateRequest;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Rule Engine의 Flow API를 Gateway 경유로 호출한다.
 * userId는 안 넘긴다 — 게이트웨이가 Authorization을 검증해서 X-User-Id로 바꿔 Rule Engine에 넘겨준다.
 */
@FeignClient(name = "insighton-gateway", contextId = "flowClient", url = "${service-url.gateway}")
public interface FlowClient {

    @GetMapping("/api/v1/flows")
    List<FlowResponse> list(@RequestParam("groupId") Long groupId,
                             @RequestParam(value = "status", required = false) FlowStatus status);

    @GetMapping("/api/v1/flows/{flowId}")
    FlowDefinitionResponse getFlow(@PathVariable("flowId") Long flowId,
                                   @RequestParam("groupId") Long groupId);

    @PutMapping("/api/v1/flows/{flowId}/status")
    FlowResponse changeStatus(@PathVariable("flowId") Long flowId,
                              @RequestParam("groupId") Long groupId,
                              @RequestBody FlowStatusChangeRequest request);

    @PostMapping("/api/v1/flows/{flowId}/archive")
    FlowResponse archive(@PathVariable("flowId") Long flowId,
                         @RequestParam("groupId") Long groupId);

    @PostMapping("/api/v1/flows/{archivedFlowId}/restore")
    FlowResponse restore(@PathVariable("archivedFlowId") Long archivedFlowId,
                         @RequestParam("groupId") Long groupId);

    @DeleteMapping("/api/v1/flows/{flowId}")
    void delete(@PathVariable("flowId") Long flowId,
               @RequestParam("groupId") Long groupId);

    @PostMapping("/api/v1/flows")
    FlowResponse create(@RequestParam("groupId") Long groupId,
                        @RequestBody FlowCreateRequest request);

    @PutMapping("/api/v1/flows/{flowId}")
    FlowResponse update(@PathVariable("flowId") Long flowId,
                        @RequestParam("groupId") Long groupId,
                        @RequestBody FlowUpdateRequest request);
}
