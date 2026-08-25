package com.nhnacademy.insightonfront.domain.mypage;

import com.nhnacademy.insightonfront.adapter.auth.mypage.MypageClient;
import com.nhnacademy.insightonfront.adapter.auth.mypage.dto.MyInfoUpdateRequest;
import com.nhnacademy.insightonfront.adapter.auth.mypage.dto.OauthLoginRequest;
import com.nhnacademy.insightonfront.adapter.auth.mypage.dto.PasswordChangeRequest;
import com.nhnacademy.insightonfront.domain.mypage.dto.MyInfoResponse;
import com.nhnacademy.insightonfront.domain.mypage.dto.OauthResponse;
import com.nhnacademy.insightonfront.domain.mypage.dto.RoleResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MypageService {

    private final MypageClient mypageClient;

    public MyInfoResponse findMyInfo() {
        return mypageClient.findMyInfo().getBody();
    }

    public void updateMyInfo(String name, String phoneNumber) {
        mypageClient.updateMyInfo(new MyInfoUpdateRequest(name, phoneNumber));
    }

    public void withdraw() {
        mypageClient.withdraw();
    }

    public void changePassword(String currentPassword, String newPassword) {
        mypageClient.changePassword(new PasswordChangeRequest(currentPassword, newPassword));
    }

    public List<RoleResponse> findMyRoles() {
        return mypageClient.findMyRoles().getBody();
    }

    public List<OauthResponse> findMyOauths() {
        return mypageClient.findMyOauths().getBody();
    }

    public void linkOauth(String provider, String code) {
        mypageClient.linkOauth(provider, new OauthLoginRequest(code));
    }

    public void unlinkOauth(Long oauthId) {
        mypageClient.unlinkOauth(oauthId);
    }
}