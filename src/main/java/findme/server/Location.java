package findme.server;

import io.netty.channel.ChannelHandlerContext;

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
}