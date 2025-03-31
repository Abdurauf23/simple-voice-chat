package voice.chat.app.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.stream.ChunkedWriteHandler;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;
import java.net.URI;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class VoiceNettyClient {
    public static void main(String[] args) throws Exception {
        try {
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


}
