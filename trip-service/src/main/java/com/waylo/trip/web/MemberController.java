package com.waylo.trip.web;

import com.waylo.trip.dto.ChangeRoleRequest;
import com.waylo.trip.dto.InviteMemberRequest;
import com.waylo.trip.dto.MemberResponse;
import com.waylo.trip.service.MemberService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Учасники подорожі. Змінювати склад може лише власник. */
@RestController
@RequestMapping("/api/trips/{id}/members")
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @GetMapping
    public List<MemberResponse> list(@RequestHeader("X-User-Id") UUID userId,
                                     @RequestHeader(value = "X-User-Email", required = false) String userEmail,
                                     @PathVariable UUID id) {
        return memberService.list(userId, userEmail, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public List<MemberResponse> invite(@RequestHeader("X-User-Id") UUID userId,
                                       @PathVariable UUID id,
                                       @Valid @RequestBody InviteMemberRequest req) {
        return memberService.invite(userId, id, req);
    }

    @PutMapping("/{memberId}")
    public List<MemberResponse> changeRole(@RequestHeader("X-User-Id") UUID userId,
                                           @PathVariable UUID id,
                                           @PathVariable UUID memberId,
                                           @Valid @RequestBody ChangeRoleRequest req) {
        return memberService.changeRole(userId, id, memberId, req);
    }

    /** Власник прибирає учасника, або учасник виходить сам. */
    @DeleteMapping("/{memberId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@RequestHeader("X-User-Id") UUID userId,
                       @PathVariable UUID id,
                       @PathVariable UUID memberId) {
        memberService.remove(userId, id, memberId);
    }
}
