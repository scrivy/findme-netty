package findme.server;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static findme.server.LocationsHandler.dataToJson;

public class Location {
    private ChannelHandlerContext ctx;
    private boolean ackPing = true;
    private double lat;
    private double lng;
    private int accuracy;
    private Instant fixedLocationSince;

    Location(ChannelHandlerContext ctx, com.maxmind.geoip2.record.Location latLng) {
        setCtx(ctx);

        if (latLng != null) {
            this.lat = latLng.getLatitude();
            this.lng = latLng.getLongitude();
        //    this.accuracy = latLng.getAccuracyRadius();
            this.accuracy = 3000;
        }
    }

    public void update(double lat, double lng, int accuracy) {
        this.lat = lat;
        this.lng = lng;
        this.accuracy = accuracy;
    }

    public void setCtx(ChannelHandlerContext ctx) {
        this.ctx = ctx;
    }

    public ObjectNode getLatLng() {
        return dataToJson(ctx.channel().id().asShortText(), lat, lng, accuracy);
    }

    public void write(String frameText) {
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
            fixedLocationSince = Instant.now().plus(1, ChronoUnit.HOURS);
        } else {
            fixedLocationSince = null;
        }
    }
}