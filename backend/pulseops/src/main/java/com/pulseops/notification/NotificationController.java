package com.pulseops.notification;

import com.pulseops.auth.AuthenticatedUser;
import com.pulseops.notification.NotificationService.NotificationList;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public NotificationList list(@AuthenticationPrincipal AuthenticatedUser user,
                                 @RequestParam(defaultValue = "20") int limit) {
        return notificationService.list(user.tenantId(), limit);
    }

    @PostMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        notificationService.markRead(user.tenantId(), id);
    }

    @PostMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllRead(@AuthenticationPrincipal AuthenticatedUser user) {
        notificationService.markAllRead(user.tenantId());
    }
}
