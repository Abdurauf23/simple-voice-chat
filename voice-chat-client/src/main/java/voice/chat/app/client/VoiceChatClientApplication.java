package voice.chat.app.client;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties
public class VoiceChatClientApplication {

    public static void main(String[] args) {
        SpringApplication.run(VoiceChatClientApplication.class, args);
    }

}
