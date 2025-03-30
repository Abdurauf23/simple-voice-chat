package voice.chat.app.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.util.CharsetUtil;

import java.util.function.Consumer;

public class WebSocketClientHandler extends SimpleChannelInboundHandler<Object> {

    private final WebSocketClientHandshaker handshaker;
    private ChannelPromise handshakeFuture;
    private static final ObjectMapper mapper = new ObjectMapper();

    private Runnable onJoinConfirmed;
    private Consumer<byte[]> onAudioReceived;

    public WebSocketClientHandler(WebSocketClientHandshaker handshaker) {
        this.handshaker = handshaker;
    }

    public ChannelFuture handshakeFuture() {
        return handshakeFuture;
    }

    public void setOnJoinConfirmed(Runnable r) {
        this.onJoinConfirmed = r;
    }

    public void setOnAudioReceived(Consumer<byte[]> c) {
        this.onAudioReceived = c;
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
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        Channel ch = ctx.channel();

        if (!handshaker.isHandshakeComplete()) {
            handshaker.finishHandshake(ch, (FullHttpResponse) msg);
            System.out.println("[Client] WebSocket handshake complete");
            handshakeFuture.setSuccess();
            return;
        }

        if (msg instanceof TextWebSocketFrame text) {
            String content = text.text();
            System.out.println("[Server] " + content);
        } else if (msg instanceof BinaryWebSocketFrame binary) {
            ByteBuf buf = binary.content();
            byte[] audio = new byte[buf.readableBytes()];
            buf.readBytes(audio);
            if (onAudioReceived != null) {
                onAudioReceived.accept(audio);
            }
        } else if (msg instanceof CloseWebSocketFrame close) {
            ch.close();
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
