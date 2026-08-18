//package com.nhnacademy.insightonfront.adapter.ruleengine.flow;
//
//import com.nhnacademy.insightonfront.adapter.ruleengine.flow.dto.FlowCreateRequest;
//import com.nhnacademy.insightonfront.adapter.ruleengine.flow.dto.FlowResponse;
//import com.nhnacademy.insightonfront.adapter.ruleengine.flow.dto.FlowStatus;
//import com.nhnacademy.insightonfront.adapter.ruleengine.flow.dto.FlowStatusChangeRequest;
//import com.nhnacademy.insightonfront.adapter.ruleengine.flow.dto.FlowUpdateRequest;
//import java.util.List;
//import org.springframework.cloud.openfeign.FeignClient;
//import org.springframework.web.bind.annotation.DeleteMapping;
//import org.springframework.web.bind.annotation.GetMapping;
//import org.springframework.web.bind.annotation.PathVariable;
//import org.springframework.web.bind.annotation.PostMapping;
//import org.springframework.web.bind.annotation.PutMapping;
//import org.springframework.web.bind.annotation.RequestBody;
//import org.springframework.web.bind.annotation.RequestParam;
//
///**
// * insighton-ruleengine의 Flow API — "Rule Engine Flow API 통합 명세 v1"(2026-07-27) 기준.
// * 다른 어댑터들과 달리 insighton-gateway를 거치지 않고 서비스에 바로 붙는다.
// * <p>이 버전 기준 알려진 제약:
// * <ul>
// *   <li>인증은 groupId 쿼리파라미터로만 스코프 확인 — X-User-Id 헤더나 별도 권한 검증은 명세에 없음
// *       (권한·Core ID 실존 확인은 이후 별도 연동 범위로 명시돼 있음)</li>
// *   <li>Node/Link(조건-액션 그래프)가 아직 없다 — Flow는 이름/설명/상태만 있는 껍데기 상태.
// *       "Node·Link 구현 후 완성된 Graph 검증과 저장 계약 추가 예정"</li>
// *   <li>보관(ARCHIVED)은 별도 엔드포인트가 없고 PUT /{flowId}(수정)의 부수효과로만 일어난다 —
// *       기존 flowId가 ARCHIVED로 바뀌고 새 flowId가 INACTIVE로 생성됨</li>
// *   <li>상태변경 엔드포인트로 ARCHIVED 전환·동일 상태로의 변경을 시도하면 409</li>
// *   <li>삭제는 ARCHIVED 상태의 Flow만 가능(ACTIVE/INACTIVE 삭제 시도는 409)</li>
// * </ul>
// */
//@FeignClient(name = "insighton-ruleengine", contextId = "ruleEngineFlowClient", url = "${service-url.ruleengine}")
//public interface RuleEngineFlowClient {
//
//    @PostMapping("/api/v1/flows")
//    FlowResponse createFlow(@RequestParam("groupId") Long groupId,
//                            @RequestBody FlowCreateRequest request);
//
//    /** locationId만 단독으로 넘기면 400. status 없으면 ACTIVE+INACTIVE, status=ARCHIVED면 휴지통 목록. */
//    @GetMapping("/api/v1/flows")
//    List<FlowResponse> getFlows(@RequestParam("groupId") Long groupId,
//                                @RequestParam(value = "locationId", required = false) Long locationId,
//                                @RequestParam(value = "status", required = false) FlowStatus status);
//
//    /** ACTIVE/INACTIVE/ARCHIVED 전부 조회 가능. Flow가 없거나 groupId가 다르면 404. */
//    @GetMapping("/api/v1/flows/{flowId}")
//    FlowResponse getFlow(@PathVariable("flowId") Long flowId,
//                         @RequestParam("groupId") Long groupId);
//
//    /** INACTIVE<->ACTIVE만 허용. 동일 상태·ARCHIVED 요청은 409. */
//    @PutMapping("/api/v1/flows/{flowId}/status")
//    FlowResponse changeStatus(@PathVariable("flowId") Long flowId,
//                              @RequestParam("groupId") Long groupId,
//                              @RequestBody FlowStatusChangeRequest request);
//
//    /** 기존 flowId는 ARCHIVED로, 새 flowId가 INACTIVE로 생성된다 — 응답의 새 flowId를 이후 요청에 써야 함. */
//    @PutMapping("/api/v1/flows/{flowId}")
//    FlowResponse update(@PathVariable("flowId") Long flowId,
//                        @RequestParam("groupId") Long groupId,
//                        @RequestBody FlowUpdateRequest request);
//
//    /** Body 없음. 새 Flow를 만들지 않고 같은 flowId를 INACTIVE로 되돌린다(재활성화는 별도 요청 필요). */
//    @PostMapping("/api/v1/flows/{archivedFlowId}/restore")
//    FlowResponse restore(@PathVariable("archivedFlowId") Long archivedFlowId,
//                         @RequestParam("groupId") Long groupId);
//
//    /** ARCHIVED만 삭제 가능(그 외 상태는 409). */
//    @DeleteMapping("/api/v1/flows/{flowId}")
//    void delete(@PathVariable("flowId") Long flowId,
//               @RequestParam("groupId") Long groupId);
//}
