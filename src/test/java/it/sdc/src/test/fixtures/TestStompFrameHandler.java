package it.sdc.src.test.fixtures;

import it.sdc.src.dto.MessageDto;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;

import java.lang.reflect.Type;
import java.util.concurrent.BlockingQueue;

@RequiredArgsConstructor
public class TestStompFrameHandler implements StompFrameHandler {
    private final BlockingQueue<MessageDto> messagesQueue;

    @Override
    public @NonNull Type getPayloadType(@NonNull StompHeaders headers) {
        return MessageDto.class;
    }

    @Override
    public void handleFrame(@NonNull StompHeaders headers, @Nullable Object payload) {
        if (payload != null) messagesQueue.add((MessageDto) payload);
    }
}
