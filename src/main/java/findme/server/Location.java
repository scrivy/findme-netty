package findme.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class Location {
    private ChannelHandlerContext ctx;
    private String id;
    private boolean ackPing = true;
    private double lat;
    private double lng;
    private int accuracy;
    private Instant fixedLocationSince;

    private static final ObjectMapper mapper = new ObjectMapper();

    Location(ChannelHandlerContext ctx) {
        setCtx(ctx);
        id = ctx.channel().id().asShortText();
    }

    public String getId() {
        return id;
    }

    public void update(double lat, double lng, int accuracy) {
        this.lat = lat;
        this.lng = lng;
        this.accuracy = accuracy;
    }

    public void setCtx(ChannelHandlerContext ctx) {
        this.ctx = ctx;
    }

    public ObjectNode getLatLngJson() {
        if (lat == 0) return null;

        ObjectNode json = mapper.createObjectNode();
        json.put("id", id);
        ArrayNode latlng = json.putArray("latlng");
        latlng.add(lat);
        latlng.add(lng);
        json.put("accuracy", accuracy);

        return json;
    }

    public void write(ObjectNode json) {
        String frameText;
        try {
            frameText = mapper.writeValueAsString(json);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return;
        }

        ctx.channel().writeAndFlush(new TextWebSocketFrame(frameText));
    }

    public void sendPing() {
        ctx.channel().writeAndFlush(new PingWebSocketFrame());
    }

    public boolean getAckPing() {
        return ackPing;
    }

    public void setAckPing(boolean val) {
        ackPing = val;
    }

    public Instant getFixedLocationSince() {
        return fixedLocationSince;
    }

    public void fixLocation(boolean state) {
        if (state) {
            fixedLocationSince = Instant.now().plus(10, ChronoUnit.MINUTES);
        } else {
            fixedLocationSince = null;
        }
    }
}