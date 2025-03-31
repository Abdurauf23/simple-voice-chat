package voice.chat.app.client.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;
import voice.chat.app.client.service.VoiceChatClientService;

@RequiredArgsConstructor
@RestController("/client")
public class VoiceChatClientController {
    private final VoiceChatClientService service;
}
