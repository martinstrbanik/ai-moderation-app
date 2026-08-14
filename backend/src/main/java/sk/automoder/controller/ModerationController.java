package sk.automoder.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sk.automoder.dto.ModerationRequest;
import sk.automoder.dto.ModerationResponse;
import sk.automoder.service.ModerationService;

@RestController
@RequestMapping("/api/moderation")
public class ModerationController {

    private final ModerationService moderationService;

    public ModerationController(ModerationService moderationService) {
        this.moderationService = moderationService;
    }

    @PostMapping
    public ModerationResponse moderate(@Valid @RequestBody ModerationRequest request) {
        return moderationService.moderate(request.policyId(), request.text());
    }
}