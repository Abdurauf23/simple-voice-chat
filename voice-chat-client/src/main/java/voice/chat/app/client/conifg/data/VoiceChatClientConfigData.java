package voice.chat.app.client.conifg.data;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;

@ConfigurationProperties("voice-chat")
public record VoiceChatClientConfigData(
        URI serverUri
) {}
