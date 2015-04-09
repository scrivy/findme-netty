package findme.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaders;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LocationsHandler {
    private static final Map<String, Location> sockets = new ConcurrentHashMap<>();
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    private static final Pattern cookieIdPattern = Pattern.compile("\\Aid=(\\w{8})\\z");

    // TODO: seperate concerns
    public static void addCtx(ChannelHandlerContext ctx, HttpHeaders headers) {
        String id = ctx.channel().id().asShortText();

        // build the allLocations json response

        Location location = null;
        if (headers != null && headers.contains("Cookie")) {
            Matcher m = cookieIdPattern.matcher(headers.get("Cookie"));
            if (m.find()) {
                String oldId = m.group(1);
                if (sockets.containsKey(oldId)) {
                    System.out.println("still has old location");
                    location = sockets.remove(oldId);
                    location.setCtx(ctx);
                    sockets.put(id, location);

                    // update other clients about changed id
                    ObjectNode update = mapper.createObjectNode();
                    update.put("action", "updateLocationId");
                    ObjectNode data = update.putObject("data");
                    data.put("oldId", oldId);
                    data.put("newId", id);
                    broadcastMessage(update, location);
                }
            }
        }

        if (location == null) {
            location = new Location(ctx);
            sockets.put(id, new Location(ctx));
        }

        sendAllLocationsTo(location);

        System.out.println(sockets.size() + " people connected");
    }

    private static void sendAllLocationsTo(Location theirLoc) {
        ObjectNode json = mapper.createObjectNode();
        json.put("action", "allLocations");
        ObjectNode data = json.putObject("data");
        data.put("id", theirLoc.getId());
        ArrayNode locations = data.putArray("locations");

        ObjectNode theirLatLng = theirLoc.getLatLngJson();
        if (theirLatLng != null) {
            ObjectNode yourLocation = data.putObject("yourLocation");
            yourLocation.setAll(theirLatLng);
        }

        sockets.values().forEach((location) -> {
            if (location != theirLoc) {
                ObjectNode latLng = location.getLatLngJson();
                if (latLng != null) {
                    locations.add(latLng);
                }
            }
        });

        theirLoc.write(json);
    }

    public static void removeLocation(String originator) {
        sockets.remove(originator);
        System.out.println("removed location: " + originator);
    }

    public static void handleJsonEvent(ChannelHandlerContext ctx, String frameText) {
        String id = ctx.channel().id().asShortText();
        System.out.println("websocket message from " + id + " data: " + frameText);

        JsonNode event;
        try {
            event = mapper.readTree(frameText);
        } catch (IOException e) {
            System.err.println("Could not parse incoming websocket json");
            e.printStackTrace();
            return;
        }
        String action = event.get("action").toString();
        
        Location location = sockets.get(id);
        if (location == null) {
            location = new Location(ctx);
            sockets.put(id, location);
        }

        if (action.equals("\"updateLocation\"")) {
            JsonNode data = event.get("data");
            JsonNode latLng = data.get("latlng");

            double lat = latLng.get(0).asDouble();
            double lng = latLng.get(1).asDouble();
            int accuracy = data.get("accuracy").asInt();

            location.update(lat, lng, accuracy);

            ObjectNode json = mapper.createObjectNode();
            json.put("action", "updateLocation");
            json.set("data", location.getLatLngJson());
            broadcastMessage(json, location);
        } else if (action.equals("\"changeFixedLocationState\"")) {
            boolean state = event.get("data").asBoolean();
            location.fixLocation(state);
        }
    }

    private static void broadcastUpdatedLocation(Location originator, ObjectNode json) {
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

        broadcastMessage(frameText, originator);
    }

    private static void broadcastMessage(ObjectNode json, Location originator) {
        String message;
        try {
            message = mapper.writeValueAsString(json);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return;
        }

        sockets.values().forEach(location -> {
            if (location != originator) {
                location.write(message);
            }
        });
    }

    public static void touchLocation(String originator) {
        sockets.get(originator).setAckPing(true);
    }

    public static void pingAndCleanUpWebSockets() {
        final Runnable pinger = new Runnable() {
            @Override
            public void run() {
                Instant now = Instant.now();
                removeLocIfStagnant(sockets, now);
                removeLocIfStagnant(roSockets, now);
            }
        };

        scheduler.scheduleAtFixedRate(pinger, 600, 600, TimeUnit.SECONDS);
    }

    private static void removeLocIfStagnant(Map<String, Location> sockets, Instant now) {
        sockets.forEach((id, location) -> {
            Instant since = location.getFixedLocationSince();
            if (since != null) {
                if (now.isAfter(since)) {
                    System.out.println("removed fixed location");
                    removeLocation(id);
                }
            } else {
                if (!location.getAckPing()) {
                    removeLocation(id);
                    System.out.println("removed stagnant location");
                } else {
                    location.setAckPing(false);
                    location.sendPing();
                }
            }
        });
    }
}