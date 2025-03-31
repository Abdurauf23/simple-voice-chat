package voice.chat.app.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class VoiceRoomHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {

    private static final Map<String, List<Channel>> rooms = new ConcurrentHashMap<>();
    private String currentRoom = null;

    @Override
    public void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame msg) throws JsonProcessingException {
        String json = msg.text();
        // parse message: join/create/send_audio
        // assume JSON has: type, room, password, data
        ObjectNode node = (ObjectNode) new ObjectMapper().readTree(json);
        String type = node.get("type").asText();


        switch (type) {
            case "join":
                handleJoin(ctx, node);
                break;
            case "audio":
                broadcastAudio(ctx, node);
                break;
        }
    }

    private void handleJoin(ChannelHandlerContext ctx, ObjectNode node) {
        String room = node.get("room").asText();
        String password = node.get("password").asText();

        RedisRoomManager redis = new RedisRoomManager();
        if (!redis.roomExists(room)) {
            redis.createRoom(room, password);
        } else if (!redis.validateRoom(room, password)) {
            ctx.channel().writeAndFlush(new TextWebSocketFrame("Invalid password"));
            ctx.close();
            return;
        }

        rooms.computeIfAbsent(room, k -> new ArrayList<>()).add(ctx.channel());
        this.currentRoom = room;
        ctx.channel().writeAndFlush(new TextWebSocketFrame("Joined room: " + room));
    }

    private void broadcastAudio(ChannelHandlerContext ctx, ObjectNode node) {
        System.out.println("Broadcasting audio");
        byte[] audio = Base64.getDecoder().decode(node.get("data").asText());
        for (Channel ch : rooms.getOrDefault(currentRoom, Collections.emptyList())) {
            if (ch != ctx.channel()) {
                ch.writeAndFlush(new BinaryWebSocketFrame(Unpooled.wrappedBuffer(audio)));
            }
        }
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        if (currentRoom != null) {
            rooms.getOrDefault(currentRoom, Collections.emptyList()).remove(ctx.channel());
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        System.out.println(cause.getCause().getMessage());
        ctx.close();
    }
}
