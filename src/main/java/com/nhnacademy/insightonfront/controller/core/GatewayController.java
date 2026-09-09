package com.nhnacademy.insightonfront.controller.core;

import com.nhnacademy.insightonfront.adapter.core.gateway.GatewayClient;
import com.nhnacademy.insightonfront.adapter.core.gateway.dto.GatewayCreateRequest;
import com.nhnacademy.insightonfront.adapter.core.gateway.dto.GatewayResponse;
import com.nhnacademy.insightonfront.adapter.core.gateway.dto.GatewayUpdateRequest;
import com.nhnacademy.insightonfront.adapter.core.gateway.dto.ProtocolType;
import feign.FeignException;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.CookieValue;

/**
 * 그룹당 게이트웨이는 1대로 제한돼 있어(core의 규칙), 목록 화면 없이 상세 정보를 바로 보여주고
 * 그 자리에서 바로 수정하게 한다 — 아직 게이트웨이가 없으면 같은 화면에서 등록 폼으로 대체된다.
 * <p>groupId는 쿠키에서 읽는다 — 한 유저는 그룹 하나에만 속해서 URL에 실을 필요가 없다.
 */
@Controller
@RequiredArgsConstructor
@RequestMapping("/manage/gateway")
public class GatewayController {

    private final GatewayClient gatewayClient;

    @GetMapping
    public String detail(@CookieValue(value = "userId", required = false) Long userId,
                          @CookieValue(value = "groupId", required = false) Long groupId,
                          Model model) {
        if (userId == null || groupId == null) {
            return "redirect:/login";
        }
        GatewayResponse gateway = findGateway(groupId);
        model.addAttribute("gateway", gateway);
        model.addAttribute("section", "gateway");
        return "group/manage";
    }

    @PostMapping
    @ResponseBody
    public GatewayResponse create(@CookieValue(value = "groupId", required = false) Long groupId,
                                   @RequestBody GatewayForm form) {
        return gatewayClient.create(
                new GatewayCreateRequest(groupId, form.name(), form.protocolType(), form.connectionConfig()));
    }

    @PutMapping("/{gateway-id}")
    @ResponseBody
    public void update(@PathVariable("gateway-id") Long gatewayId,
                        @RequestBody GatewayForm form) {
        gatewayClient.update(gatewayId,
                new GatewayUpdateRequest(form.name(), form.protocolType(), form.connectionConfig()));
    }

    @DeleteMapping("/{gateway-id}")
    @ResponseBody
    public void delete(@PathVariable("gateway-id") Long gatewayId) {
        gatewayClient.delete(gatewayId);
    }

    private GatewayResponse findGateway(Long groupId) {
        try {
            return gatewayClient.getByGroupId(groupId);
        } catch (FeignException.NotFound e) {
            return null;
        }
    }

    public record GatewayForm(String name, ProtocolType protocolType, Map<String, Object> connectionConfig) {}
}
