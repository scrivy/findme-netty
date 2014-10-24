package findme.server;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;

import static findme.server.LocationsHandler.dataToJson;

public class Location {
    private final ChannelHandlerContext ctx;
    private double lat;
    private double lng;
    private int accuracy;

    Location(ChannelHandlerContext ctx) {
        this.ctx = ctx;
    }

    public void update(double lat, double lng, int accuracy) {
        this.lat = lat;
        this.lng = lng;
        this.accuracy = accuracy;
    }

    public ObjectNode getLatLng() {
        return dataToJson(ctx.channel().id().asShortText(), lat, lng, accuracy);
    }

    public void write(String frameText) {
        ctx.channel().writeAndFlush(new TextWebSocketFrame(frameText));
    }
}