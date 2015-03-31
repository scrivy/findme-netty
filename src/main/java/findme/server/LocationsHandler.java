package findme.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;

import java.io.IOException;
import java.net.InetAddress;
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
    public static Location addLocation(ChannelHandlerContext ctx, HttpHeaders headers) {
        String id = ctx.channel().id().asShortText();

        // build the allLocations json response
        ObjectNode response = mapper.createObjectNode();
        response.put("action", "allLocations");
        ObjectNode data = response.putObject("data");
        data.put("id", id);
        ObjectNode locations = data.putObject("locations");

        Location location = null;
        if (headers != null && headers.contains("Cookie")) {
            Matcher m = cookieIdPattern.matcher(headers.get("Cookie"));
            if (m.find()) {
                String oldId = m.group(1);
                if (sockets.containsKey(oldId)) {
                    System.out.println("still has old location");
                    location = sockets.get(oldId);
                    sockets.remove(oldId);
                    location.setCtx(ctx);
                    sockets.put(id, location);

                    try {
                        ObjectNode update = mapper.createObjectNode();
                        update.put("action", "updateLocationId");
                        ObjectNode data2 = update.putObject("data");
                        data2.put("oldId", oldId);
                        data2.put("newId", id);
                        String jsonString = mapper.writeValueAsString(update);
                        broadcastMessage(jsonString, location);
                    } catch (JsonProcessingException e) {
                        e.printStackTrace();
                    }
                    
                    ObjectNode yourLocation = data.putObject("yourLocation");
                    yourLocation.setAll(location.getLatLng());
                }
            }
        }

        for (Map.Entry<String, Location> entry : sockets.entrySet()) {
            Location entryLocation = entry.getValue();
            if (entryLocation != location) {
                locations.set(entry.getKey(), entryLocation.getLatLng());
            }
        }

        com.maxmind.geoip2.record.Location latLng = null;
        if (location == null) {
            IpToLatLng ipToLatLng = IpToLatLng.getInstance();
            try {
                InetAddress ip = InetAddress.getByName(headers.get("X-Real-IP"));
                latLng = ipToLatLng.getLocationFromIP(ip);
            } catch (IOException | GeoIp2Exception e) {
                e.printStackTrace();
            }

            location = new Location(ctx, latLng);
        }

        System.out.println((sockets.size() + 1) + " people connected");

        if (latLng != null) {
            ObjectNode yourLocation = data.putObject("yourLocation");
            ArrayNode latlng = yourLocation.putArray("latlng");
            latlng.add(latLng.getLatitude());
            latlng.add(latLng.getLongitude());
            yourLocation.put("accuracy", 7000); //latLng.getAccuracyRadius());
        }

        // send all locations to client
        try {
            String jsonString = mapper.writeValueAsString(response);
            ctx.channel().write(new TextWebSocketFrame(jsonString));
            System.out.println(jsonString);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }

        sockets.put(id, location);
        return location;
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

        // TODO separate some concerns
        Location location = sockets.get(originator);
        if (location == null) { // TODO: fixxxxx
            location = addLocation(ctx, null);
        }

        if (action.equals("\"updateLocation\"")) {
            JsonNode data = event.get("data");
            JsonNode latLng = data.get("latlng");

            double lat = latLng.get(0).asDouble();
            double lng = latLng.get(1).asDouble();
            int accuracy = data.get("accuracy").asInt();

            location.update(lat, lng, accuracy);

            broadcastUpdatedLocation(location, dataToJson(originator, lat, lng, accuracy));
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

        sockets.values().forEach(location -> {
            if (location != originator) {
                location.write(frameText);
            }
        });
    }

    private static void broadcastMessage(String message, Location originator) {
        if (originator != null) {
            sockets.values().forEach(location -> {
                if (location != originator) {
                    location.write(message);
                }
            });
        } else {
            sockets.values().forEach(location -> {
                location.write(message);
            });
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
                Instant now = Instant.now();
                for (Map.Entry<String, Location> entry : sockets.entrySet()) {
                    Location location = entry.getValue();
                    Instant since = location.getFixedLocationSince();
                    if (since != null) {
                        if (now.isAfter(since)) {
                            System.out.println("removed fixed location");
                            removeLocation(entry.getKey());
                        }
                    } else {
                        if (!location.getAckPing()) {
                            removeLocation(entry.getKey());
                            System.out.println("removed stagnant location");
                        } else {
                            location.setAckPing(false);
                            location.sendPing();
                        }
                    }
                }
            }
        };

        scheduler.scheduleAtFixedRate(pinger, 600, 600, TimeUnit.SECONDS);
    }
}