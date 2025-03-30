package voice.chat.app.client;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.util.CharsetUtil;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;


public class WebSocketClientHandler extends SimpleChannelInboundHandler<Object> {

    private final WebSocketClientHandshaker handshaker;
    private ChannelPromise handshakeFuture;
    private static final AudioFormat AUDIO_FORMAT = new AudioFormat(44100.0f, 16, 1, true, false);
    private SourceDataLine speakers;

    public WebSocketClientHandler(WebSocketClientHandshaker handshaker) {
        this.handshaker = handshaker;
    }

    public ChannelFuture handshakeFuture() {
        return handshakeFuture;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        handshakeFuture = ctx.newPromise();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        handshaker.handshake(ctx.channel());
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        Channel ch = ctx.channel();
        if (!handshaker.isHandshakeComplete()) {
            handshaker.finishHandshake(ch, (FullHttpResponse) msg);
            System.out.println("[Client] WebSocket handshake complete");
            handshakeFuture.setSuccess();
            return;
        }

        if (msg instanceof FullHttpResponse response) {
            throw new IllegalStateException("Unexpected FullHttpResponse: " + response.content().toString(CharsetUtil.UTF_8));
        }

        WebSocketFrame frame = (WebSocketFrame) msg;

        if (frame instanceof TextWebSocketFrame text) {
            System.out.println("[Server]: " + text.text());
        } else if (frame instanceof BinaryWebSocketFrame binary) {
            playAudio(binary.content());
        } else if (frame instanceof CloseWebSocketFrame close) {
            ch.close();
        }
    }

    private void playAudio(ByteBuf content) {
        try {
            byte[] audio = new byte[content.readableBytes()];
            content.readBytes(audio);
            if (speakers == null) {
                speakers = AudioSystem.getSourceDataLine(AUDIO_FORMAT);
                speakers.open(AUDIO_FORMAT);
                speakers.start();
            }
            speakers.write(audio, 0, audio.length);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        if (!handshakeFuture.isDone()) {
            handshakeFuture.setFailure(cause);
        }
        ctx.close();
    }
}

