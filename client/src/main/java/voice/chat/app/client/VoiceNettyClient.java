package voice.chat.app.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.stream.ChunkedWriteHandler;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.TargetDataLine;
import java.net.URI;
import java.util.concurrent.Executors;

public class VoiceNettyClient {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final AudioFormat AUDIO_FORMAT = new AudioFormat(44100.0f, 16, 1, true, false);
    private static WebSocketClientHandler handler;

    public static void main(String[] args) throws Exception {
        URI uri = new URI("ws://localhost:8080/ws");
        String host = uri.getHost();
        int port = uri.getPort();

        EventLoopGroup group = new NioEventLoopGroup();
        try {
            handler = new WebSocketClientHandler(
                    WebSocketClientHandshakerFactory.newHandshaker(
                            uri, WebSocketVersion.V13, null, true, new DefaultHttpHeaders()
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
            try {
                handler.handshakeFuture().sync();
            } catch (Exception e) {
                System.err.println("[Client] WebSocket handshake failed:");
                e.printStackTrace();
                return;
            }

            // Send join message
            ObjectNode joinMsg = mapper.createObjectNode();
            joinMsg.put("type", "join");
            joinMsg.put("room", "testroom");
            joinMsg.put("password", "1234");
            String joinJson = mapper.writeValueAsString(joinMsg);
            channel.writeAndFlush(new TextWebSocketFrame(joinJson));

            Thread.sleep(5000);

            // Start audio capture
            Executors.newSingleThreadExecutor().submit(() -> captureAndSendAudio(channel));

        } finally {
             group.shutdownGracefully(); // optional: let it run
        }
    }

    private static void captureAndSendAudio(Channel channel) {
        try {
            TargetDataLine mic = AudioSystem.getTargetDataLine(AUDIO_FORMAT);
            mic.open(AUDIO_FORMAT);
            mic.start();
            byte[] buffer = new byte[2048];
            while (true) {
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
}
