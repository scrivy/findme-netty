package findme.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LocationsHandler {
    private static final Map<String, Location> sockets = new ConcurrentHashMap<>();
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    private static final Pattern cookieIdPattern = Pattern.compile("\\Aid=(\\w{8})\\z");

    public static void addLocation(ChannelHandlerContext ctx, HttpHeaders headers) {
        String id = ctx.channel().id().asShortText();

        if (headers != null && headers.contains("Cookie")) {
            Matcher m = cookieIdPattern.matcher(headers.get("Cookie"));
            if (m.find()) {
                String oldId = m.group(1);
                if (sockets.containsKey(oldId)) {
                    System.out.println("still has old location");
                    removeLocation(oldId);
                }
            }
        }

        // build the allLocations json response
        ObjectNode response = mapper.createObjectNode();
        response.put("action", "allLocations");
        ObjectNode data = response.putObject("data");
        data.put("id", id);
        ObjectNode locations = data.putObject("locations");
        for (String key : sockets.keySet()) {
            locations.set(key, sockets.get(key).getLatLng());
        }

        // send all locations to client
        try {
            String jsonString = mapper.writeValueAsString(response);
            ctx.channel().write(new TextWebSocketFrame(jsonString));
            System.out.println(jsonString);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }

        sockets.put(id, new Location(ctx));
        System.out.println(sockets.size() + " people connected");
    }

    public static void removeLocation(String originator) {
        sockets.remove(originator);
        System.out.println("removed location: " + originator);
    }

    public static void handleJsonEvent(ChannelHandlerContext ctx, String frameText) {
        String originator = ctx.channel().id().asShortText();
        System.out.println("websocket message from " + originator + " data: " + frameText);

        JsonNode event;
        try {
            event = mapper.readTree(frameText);
        } catch (IOException e) {
            System.err.println("Could not parse incoming websocket json");
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
            Location location = sockets.get(originator);
            if (location != null) {
                location.update(lat, lng, accuracy);
            } else {
                addLocation(ctx, null);
            }
        }
    }

    private static void broadcastUpdatedLocation(String originator, ObjectNode json) {
        // build json to send
        ObjectNode jsonToSend = mapper.createObjectNode();
        jsonToSend.put("action", "updateLocation");
        jsonToSend.set("data", json);
        String frameText;
        try {
            frameText = mapper.writeValueAsString(jsonToSend);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return;
        }

        for (String key : sockets.keySet()) {
            if (!key.equals(originator)) {
                sockets.get(key).write(frameText);
            }
        }
    }

    public static ObjectNode dataToJson(String originator, double lat, double lng, int accuracy) {
        ObjectNode data = mapper.createObjectNode();
        data.put("id", originator);
        ArrayNode latlng = data.putArray("latlng");
        latlng.add(lat);
        latlng.add(lng);
        data.put("accuracy", accuracy);

        return data;
    }

    public static void touchLocation(String originator) {
        sockets.get(originator).setAckPing(true);
    }

    public static void pingAndCleanUpWebSockets() {
        final Runnable pinger = new Runnable() {
            @Override
            public void run() {
                for (String key : sockets.keySet()) {
                    Location location = sockets.get(key);
                    if (!location.getAckPing()) {
                        System.out.println("Stagnant Location");
                        removeLocation(key);
                    } else {
                        location.setAckPing(false);
                        location.sendPing();
                    }
                }
            }
        };

        scheduler.scheduleAtFixedRate(pinger, 600, 600, TimeUnit.SECONDS);
    }
}