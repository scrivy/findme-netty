package findme.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.netty.channel.ChannelHandlerContext;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LocationsHandler {
    private static final Map<String, Location> sockets = new ConcurrentHashMap<>();
    private static final ObjectMapper mapper = new ObjectMapper();

    public static void addLocation(ChannelHandlerContext ctx) {
        sockets.put(ctx.channel().id().asShortText(), new Location(ctx));
    }

    public static void handleJsonEvent(String originator, String frameText) {
        System.out.println("websocket message from " + originator + " data: " + frameText);

        JsonNode event = null;
        try {
            event = mapper.readTree(frameText);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        String action = event.get("action").toString();

        if (action.equals("\"updateLocation\"")) {
            JsonNode data = event.get("data");
            JsonNode latLng = data.get("latlng");

            double lat = latLng.get(0).asDouble();
            double lng = latLng.get(1).asDouble();
            int accuracy = data.get("accuracy").asInt();

            broadcastUpdatedLocation(originator, dataToJson(originator, lat, lng, accuracy));
            sockets.get(originator).update(lat, lng, accuracy);
        }
    }

    private static void broadcastUpdatedLocation(String originator, ObjectNode json) {
        for (String key : sockets.keySet()) {
            if (!key.equals(originator)) {
                sockets.get(key).write(json);
            }
        }
    }

    private static ObjectNode dataToJson(String originator, double lat, double lng, int accuracy) {
        ObjectNode data = mapper.createObjectNode();
        data.put("id", originator);
        ArrayNode latlng = data.putArray("latlng");
        latlng.add(lat);
        latlng.add(lng);
        data.put("accuracy", accuracy);

        return data;
    }
}