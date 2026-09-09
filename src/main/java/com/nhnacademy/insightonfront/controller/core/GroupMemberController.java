package com.nhnacademy.insightonfront.controller.core;

import com.nhnacademy.insightonfront.adapter.core.groupmember.GroupMemberClient;
import com.nhnacademy.insightonfront.adapter.core.groupmember.dto.GroupMemberListResponse;
import com.nhnacademy.insightonfront.adapter.core.groupmember.dto.GroupMemberResponse;
import com.nhnacademy.insightonfront.common.service.GroupPermissionService;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * groupId는 쿠키에서 읽는다(한 유저 = 한 그룹). groupMemberId는 그룹 안에서 특정 멤버를
 * 가리키는 값이라 세션이 아니라 경로 변수로 받는다.
 */
@Controller
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/my-group/members")
public class GroupMemberController {
    private final GroupMemberClient groupMemberClient;
    private final GroupPermissionService groupPermissionService;

    @PostMapping("/invite")
    public String inviteMemberByEmail(@CookieValue(value = "groupId", required = false) Long groupId,
                                      @RequestParam("email") String email,
                                      RedirectAttributes redirectAttributes) {
        if (groupId == null) {
            return "redirect:/group-registration";
        }

        try {
            groupMemberClient.inviteMemberByEmail(groupId, email);
            log.info("멤버 초대가 완료 되었습니다. Group ID: {}, Email: {}", groupId, email);
            redirectAttributes.addFlashAttribute("inviteSuccess", "멤버 초대가 완료되었습니다!");
        } catch (FeignException e) {
            log.warn("멤버 초대 실패 - groupId:{}, email:{}", groupId, email, e);
            redirectAttributes.addFlashAttribute("inviteError", "초대에 실패했습니다. 가입되지 않은 이메일이거나 이미 그룹에 소속된 유저입니다.");
        }

        return "redirect:/my-group/members";
    }

    @GetMapping
    public String getGroupMemberList(@CookieValue(value = "groupId", required = false) Long groupId, Model model) {
        if (groupId == null) {
            return "redirect:/group-registration";
        }
        try {
            List<GroupMemberListResponse> groupMemberList = groupMemberClient.getGroupMemberList(groupId);
            model.addAttribute("groupMemberList", groupMemberList);
        } catch (FeignException e) {
            log.warn("그룹 멤버 목록 조회 실패 - groupId:{}", groupId, e);
            if (e.status() == 403) {
                model.addAttribute("permissionError", "멤버 목록을 볼 수 있는 권한이 없습니다.");
            } else {
                model.addAttribute("groupMemberList", null);
            }
        }

        return "groupmember/list";
    }

    @GetMapping("/{group-member-id}")
    public String getGroupMember(@CookieValue(value = "userId", required = false) Long userId,
                                 @CookieValue(value = "groupId", required = false) Long groupId,
                                 @PathVariable("group-member-id") Long groupMemberId, Model model) {
        if (groupId == null) {
            return "redirect:/group-registration";
        }
        try {
            GroupMemberResponse groupMember = groupMemberClient.getGroupMember(groupId, groupMemberId);
            model.addAttribute("groupMember", groupMember);

            boolean isSuperManager = false;
            if (userId != null) {
                isSuperManager = groupPermissionService.isSuperManager(groupId, userId);
            }
            model.addAttribute("isSuperManager", isSuperManager);

        } catch (FeignException e) {
            log.warn("그룹 멤버 상세 조회 실패 - groupId:{}, groupMemberId:{}", groupId, groupMemberId, e);
            model.addAttribute("groupMember", null);
        }

        return "groupmember/detail";
    }

    @PutMapping("/{group-member-id}/toggle-manager")
    public String toggleManagerRole(@CookieValue("groupId") Long groupId,
                                    @PathVariable("group-member-id") Long groupMemberId,
                                    RedirectAttributes redirectAttributes) {
        try {
            groupMemberClient.toggleManagerRole(groupId, groupMemberId);
            log.info("멤버의 권한이 변경 되었습니다. Group ID : {}, groupMember ID : {}", groupId, groupMemberId);
        } catch (FeignException.Forbidden e) {
            log.warn("매니저 권한 변경 실패(403) - SuperManager만 변경 가능. groupId:{}, targetMemberId:{}", groupId, groupMemberId);
            redirectAttributes.addFlashAttribute("memberError", "매니저 권한 변경은 Super Manager만 가능합니다.");
        } catch (FeignException.BadRequest e) {
            log.warn("매니저 권한 변경 실패(400) - 잘못된 대상이거나 변경 조건 미충족. groupId:{}, targetMemberId:{}", groupId, groupMemberId);
            redirectAttributes.addFlashAttribute("memberError", "권한을 변경할 수 없는 대상입니다.");
        } catch (FeignException.NotFound e) {
            log.warn("매니저 권한 변경 실패(404) - 존재하지 않는 멤버. groupId:{}, targetMemberId:{}", groupId, groupMemberId);
            redirectAttributes.addFlashAttribute("memberError", "존재하지 않는 멤버입니다.");
        } catch (Exception e) {
            log.warn("매니저 권한 변경 중 예외 발생 - groupId:{}, targetMemberId:{}", groupId, groupMemberId, e);
            redirectAttributes.addFlashAttribute("memberError", "권한 변경 중 오류가 발생했습니다.");
        }

        return "redirect:/my-group/members/" + groupMemberId;
    }

    @PutMapping("/{group-member-id}/toggle-super-manager")
    public String toggleSuperManagerRole(@CookieValue("groupId") Long groupId,
                                         @PathVariable("group-member-id") Long groupMemberId,
                                         RedirectAttributes redirectAttributes) {
        try {
            groupMemberClient.toggleSuperManagerRole(groupId, groupMemberId);
            log.info("super manager의 권한이 양도 되었습니다. Group ID : {}, 새로운 super manager groupMember ID: {}", groupId, groupMemberId);
        } catch (FeignException.Forbidden e) {
            log.warn("SuperManager 권한 양도 실패(403) - 권한 없음. groupId:{}, targetMemberId:{}", groupId, groupMemberId);
            redirectAttributes.addFlashAttribute("memberError", "Super Manager 권한 양도는 현재 Super Manager만 가능합니다.");
        } catch (FeignException.BadRequest e) {
            log.warn("SuperManager 권한 양도 실패(400) - 대상이 Manager가 아니거나 양도 불가. groupId:{}, targetMemberId:{}", groupId, groupMemberId);
            redirectAttributes.addFlashAttribute("memberError", "Manager 역할 이상의 멤버에게만 Super Manager 권한을 양도할 수 있습니다.");
        } catch (FeignException.NotFound e) {
            log.warn("SuperManager 권한 양도 실패(404) - 존재하지 않는 멤버. groupId:{}, targetMemberId:{}", groupId, groupMemberId);
            redirectAttributes.addFlashAttribute("memberError", "존재하지 않는 멤버입니다.");
        } catch (Exception e) {
            log.warn("SuperManager 권한 양도 중 예외 발생 - groupId:{}, targetMemberId:{}", groupId, groupMemberId, e);
            redirectAttributes.addFlashAttribute("memberError", "권한 양도 중 오류가 발생했습니다.");
        }

        return "redirect:/my-group/members/" + groupMemberId;
    }

    @DeleteMapping("/{group-member-id}/kick")
    public String kickGroupMember(@CookieValue("groupId") Long groupId,
                                  @PathVariable("group-member-id") Long groupMemberId,
                                  RedirectAttributes redirectAttributes) {
        try {
            groupMemberClient.kickGroupMember(groupId, groupMemberId);
            log.info("멤버가 추방 되었습니다. Group ID : {}, 추방된 멤버 ID : {}", groupId, groupMemberId);
            return "redirect:/my-group/members";
        } catch (FeignException.Forbidden e) {
            log.warn("멤버 추방 실패(403) - 권한 없음. groupId:{}, targetMemberId:{}", groupId, groupMemberId);
            redirectAttributes.addFlashAttribute("memberError", "멤버 추방 권한이 없습니다.");
            return "redirect:/my-group/members/" + groupMemberId;
        } catch (FeignException.BadRequest e) {
            log.warn("멤버 추방 실패(400) - 본인 추방 불가 또는 SuperManager 추방 불가. groupId:{}, targetMemberId:{}", groupId, groupMemberId);
            redirectAttributes.addFlashAttribute("memberError", "자기 자신 또는 Super Manager는 추방할 수 없습니다.");
            return "redirect:/my-group/members/" + groupMemberId;
        } catch (FeignException.NotFound e) {
            log.warn("멤버 추방 실패(404) - 존재하지 않는 멤버. groupId:{}, targetMemberId:{}", groupId, groupMemberId);
            redirectAttributes.addFlashAttribute("memberError", "존재하지 않는 멤버입니다.");
            return "redirect:/my-group/members";
        } catch (Exception e) {
            log.warn("멤버 추방 중 예외 발생 - groupId:{}, targetMemberId:{}", groupId, groupMemberId, e);
            redirectAttributes.addFlashAttribute("memberError", "멤버 추방 중 오류가 발생했습니다.");
            return "redirect:/my-group/members/" + groupMemberId;
        }
    }

    @PostMapping("/leave")
    public String leaveGroupPost(@CookieValue(value = "groupId", required = false) Long groupId,
                                 jakarta.servlet.http.HttpServletResponse response,
                                 RedirectAttributes redirectAttributes) {
        return leaveGroup(groupId, response, redirectAttributes);
    }

    @DeleteMapping("/leave")
    public String leaveGroup(@CookieValue(value = "groupId", required = false) Long groupId,
                             jakarta.servlet.http.HttpServletResponse response,
                             RedirectAttributes redirectAttributes) {
        if (groupId != null) {
            try {
                groupMemberClient.leaveGroup(groupId);
                log.info("group({})을 떠나셨습니다.", groupId);
            } catch (FeignException.BadRequest e) {
                log.warn("그룹 탈퇴 실패(400) - SuperManager는 양도 전 탈퇴 불가. groupId:{}", groupId);
                redirectAttributes.addFlashAttribute("leaveError", "Super Manager는 권한을 양도한 후에만 그룹을 탈퇴할 수 있습니다.");
                return "redirect:/my-group/members";
            } catch (FeignException.Forbidden e) {
                log.warn("그룹 탈퇴 권한 없음(403) - groupId:{}", groupId);
                redirectAttributes.addFlashAttribute("leaveError", "그룹 탈퇴 권한이 없습니다.");
                return "redirect:/my-group/members";
            } catch (FeignException e) {
                log.warn("그룹 탈퇴 실패 - groupId:{}", groupId, e);
                redirectAttributes.addFlashAttribute("leaveError", "그룹 탈퇴 중 오류가 발생했습니다.");
                return "redirect:/my-group/members";
            }
        }
        jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie("groupId", null);
        cookie.setMaxAge(0);
        cookie.setPath("/");
        response.addCookie(cookie);

        return "redirect:/";
    }
}
