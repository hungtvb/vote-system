package com.hungtvb.votesystem.user;

import com.hungtvb.votesystem.user.dto.UserProfileResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
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
}
