package com.nhnacademy.insightonfront.domain.groupregistration.service;

import com.nhnacademy.insightonfront.adapter.core.groupregistration.GroupRegistrationClient;
import com.nhnacademy.insightonfront.adapter.core.groupregistration.dto.GroupRegistrationResponse;
import com.nhnacademy.insightonfront.common.dto.PageResponse;
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

    public GroupRegistrationResponse findLatest(Long userId) {
        PageResponse<GroupRegistrationResponse> page = groupRegistrationClient.getMyGroupRegistrations(userId, 0, 1);
        return page.content().isEmpty() ? null : page.content().getFirst();
    }
}
