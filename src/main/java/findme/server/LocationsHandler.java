package findme.server;

import com.fasterxml.jackson.databind.JsonNode;
import io.netty.channel.ChannelHandlerContext;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LocationsHandler {
    private static final Map<String, Location> sockets = new ConcurrentHashMap<>();

    public static void addLocation(ChannelHandlerContext ctx) {
        sockets.put(ctx.channel().id().asShortText(), new Location(ctx));
    }

    public static void handleJsonEvent(ChannelHandlerContext ctx, JsonNode event) {
        String action = event.get("action").toString();

        if (action.equals("\"updateLocation\"")) {
            JsonNode data = event.get("data");
            JsonNode latLng = data.get("latlng");

            double lat = latLng.get(0).asDouble();
            double lng = latLng.get(1).asDouble();
            int accuracy = data.get("accuracy").asInt();

            updateLocation(ctx.channel().id().asShortText(), dataToJson(uuid, lat, lng, accuracy));
            sockets.get(uuid).update(lat, lng, accuracy);
        }
    }

    private static void updateLocation() {

    }
}