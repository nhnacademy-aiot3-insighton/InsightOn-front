package com.nhnacademy.insightonfront.domain.groupregistration.service;

import com.nhnacademy.insightonfront.adapter.core.groupregistration.GroupRegistrationClient;
import com.nhnacademy.insightonfront.adapter.core.groupregistration.dto.GroupRegistrationResponse;
import com.nhnacademy.insightonfront.common.dto.PageResponse;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * "이 사용자가 그룹을 이미 갖고 있는지"를 직접 조회하는 API는 아직 없어서, 가장 최근 그룹
 * 신청의 상태로 대신 판단한다. {@link com.nhnacademy.insightonfront.controller.HomeController}와
 * {@link com.nhnacademy.insightonfront.controller.core.GroupRegistrationController}가 같은 판단
 * 로직을 쓰도록 여기 하나로 모아둔다.
 */
@Service
@RequiredArgsConstructor
public class GroupRegistrationStatusService {

    private final GroupRegistrationClient groupRegistrationClient;

    /** 사용자의 최신 그룹 신청 내역을 반환. 신청 이력이 없으면(404) null. */
    public GroupRegistrationResponse findLatest(Long userId) {
        try {
            PageResponse<GroupRegistrationResponse> page =
                    groupRegistrationClient.getMyGroupRegistrations(userId, 0, 1);

            if (page == null || page.content() == null || page.content().isEmpty()) {
                return null;
            }
            return page.content().get(0);

        } catch (FeignException.NotFound e) {
            return null;   // ★ 신청 이력 없음 → null (에러 아님)
        }
    }
}
