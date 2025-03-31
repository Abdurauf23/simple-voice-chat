package voice.chat.app.client.conifg;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.stream.ChunkedWriteHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import voice.chat.app.client.conifg.data.VoiceChatClientConfigData;
import voice.chat.app.client.handler.WebSocketClientHandler;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;
import java.net.URI;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class VoiceChatClientConfig {
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final AudioFormat AUDIO_FORMAT = new AudioFormat(44100.0f, 16, 1, true, false);
    private static final AtomicBoolean readyToSend = new AtomicBoolean(false);

    private static SourceDataLine speakers;

    @Bean
    public NioEventLoopGroup voiceChatClientNioEventLoopGroup() {
        return new NioEventLoopGroup(1);
    }

    @Bean
    public WebSocketClientHandler voiceChatClientWebSocketClientHandler(
            VoiceChatClientConfigData configData
    ) {
        return new WebSocketClientHandler(
                WebSocketClientHandshakerFactory.newHandshaker(
                        configData.serverUri(),
                        WebSocketVersion.V13,
                        null,
                        true,
                        new DefaultHttpHeaders()
                )
        );
    }

    @Bean
    public Bootstrap voiceChatClientBootstrap(
            @Qualifier("voiceChatClientNioEventLoopGroup") NioEventLoopGroup group,
            @Qualifier("voiceChatClientWebSocketClientHandler") WebSocketClientHandler handler
    ) {
        return new Bootstrap().group(group)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<>() {
                    @Override
                    protected void initChannel(Channel ch) {
                        ChannelPipeline p = ch.pipeline();
                        p.addLast(new HttpClientCodec());
                        p.addLast(new HttpObjectAggregator(8192));
                        p.addLast(new ChunkedWriteHandler());
                        p.addLast(handler);
                    }
                });
    }

    @Bean
    public Channel voiceChatClientHandshake(
            @Qualifier("voiceChatClientBootstrap") Bootstrap bootstrap,
            @Qualifier("voiceChatClientWebSocketClientHandler") WebSocketClientHandler handler,
            VoiceChatClientConfigData configData
    ) {
        try {
            URI uri = configData.serverUri();
            Channel channel = bootstrap.connect(uri.getHost(), uri.getPort()).sync().channel();
            handler.handshakeFuture().sync();
            return channel;
        } catch (InterruptedException e) {
            log.error("Error in VoiceChatClientConfig.voiceChatClientHandshake: ", e);
            throw new RuntimeException(e);
        }
    }

    private static void captureAndSendAudio(Channel channel) {
        try {
            TargetDataLine mic = AudioSystem.getTargetDataLine(AUDIO_FORMAT);
            mic.open(AUDIO_FORMAT);
            mic.start();

            byte[] buffer = new byte[2048];
            while (true) {
                if (!readyToSend.get()) continue;

                int bytesRead = mic.read(buffer, 0, buffer.length);
                if (bytesRead > 0 && channel.isOpen()) {
                    ByteBuf buf = Unpooled.wrappedBuffer(buffer, 0, bytesRead);
                    channel.writeAndFlush(new BinaryWebSocketFrame(buf));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void playAudio(byte[] data) {
        try {
            if (speakers == null) {
                speakers = AudioSystem.getSourceDataLine(AUDIO_FORMAT);
                speakers.open(AUDIO_FORMAT);
                speakers.start();
            }
            speakers.write(data, 0, data.length);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
