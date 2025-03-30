package voice.chat.app.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.handler.stream.ChunkedWriteHandler;

import javax.sound.sampled.*;
import java.net.URI;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class VoiceNettyClient {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final AudioFormat AUDIO_FORMAT = new AudioFormat(44100.0f, 16, 1, true, false);
    private static final AtomicBoolean readyToSend = new AtomicBoolean(false);

    private static SourceDataLine speakers;

    public static void main(String[] args) throws Exception {
        URI uri = new URI("ws://localhost:8080/ws");
        String host = uri.getHost();
        int port = uri.getPort();

        NioEventLoopGroup group = new NioEventLoopGroup();

        try {
            WebSocketClientHandler handler = new WebSocketClientHandler(
                    WebSocketClientHandshakerFactory.newHandshaker(
                            uri,
                            WebSocketVersion.V13,
                            null,
                            true,
                            new DefaultHttpHeaders()
                    )
            );

            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group)
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

            Channel channel = bootstrap.connect(host, port).sync().channel();
            handler.handshakeFuture().sync();

            // Send JOIN message
            ObjectNode joinMsg = mapper.createObjectNode();
            joinMsg.put("type", "join");
            joinMsg.put("room", "testroom");
            joinMsg.put("password", "1234");

            channel.writeAndFlush(new TextWebSocketFrame(joinMsg.toString()));

            // Wait for "joined" confirmation from server
            handler.setOnJoinConfirmed(() -> {
                System.out.println("[Client] Joined room, starting audio stream.");
                readyToSend.set(true);
                Executors.newSingleThreadExecutor().submit(() -> captureAndSendAudio(channel));
            });

            // Set audio playback handler
            handler.setOnAudioReceived(VoiceNettyClient::playAudio);

        } catch (Exception e) {
            e.printStackTrace();
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
