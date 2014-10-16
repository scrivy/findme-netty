package findme.server;

import io.netty.channel.ChannelHandler;

public class Location {
    private final String uuid;
    private final ChannelHandler ctx;
    private double lat;
    private double lng;
    private int accuracy;

    Location(String uuid, WebSocket.Out<JsonNode> out) {
        this.uuid = uuid;
        this.out = out;
    }

    public void update(double lat, double lng, int accuracy) {
        this.lat = lat;
        this.lng = lng;
        this.accuracy = accuracy;
    }

    public ObjectNode getLatLng() {
        return dataToJson(this.uuid, this.lat, this.lng, this.accuracy);
    }

    public void write(JsonNode json) {
        this.out.write(json);
    }
}