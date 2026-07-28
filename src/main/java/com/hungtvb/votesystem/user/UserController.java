package com.hungtvb.votesystem.user;

import com.hungtvb.votesystem.user.dto.PublicUserProfileResponse;
import com.hungtvb.votesystem.user.dto.UpdateUserProfileRequest;
import com.hungtvb.votesystem.user.dto.UserProfileResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    UserProfileResponse me(@AuthenticationPrincipal Jwt jwt) {
        return userService.getCurrentUser(UUID.fromString(jwt.getSubject()));
    }

    @PatchMapping("/me")
    UserProfileResponse updateMe(@AuthenticationPrincipal Jwt jwt,
                                 @Valid @RequestBody UpdateUserProfileRequest request) {
        return userService.updateCurrentUser(UUID.fromString(jwt.getSubject()), request);
    }

    @GetMapping("/{userId}")
    PublicUserProfileResponse publicProfile(@PathVariable UUID userId) {
        return userService.getPublicUser(userId);
    }
}
