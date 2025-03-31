package voice.chat.app.common.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BaseMessage<T> {
    private VoiceChatMethod method;
    private T data;
}
