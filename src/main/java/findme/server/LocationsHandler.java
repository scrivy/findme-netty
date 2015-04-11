package findme.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LocationsHandler {
    final static Logger logger = LoggerFactory.getLogger(LocationsHandler.class);

    private static final Map<String, Location> sockets = new ConcurrentHashMap<>();
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    private static final Pattern cookieIdPattern = Pattern.compile("id=(\\w{8})");

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
                    logger.debug("still has old location");
                    location = sockets.remove(oldId);
                    location.setCtx(ctx);
                    sockets.put(id, location);

                    // update other clients about changed id
                    ObjectNode data = mapper.createObjectNode();
                    data.put("oldId", oldId);
                    data.put("newId", id);
                    broadcastMessage("updateLocationId", data, location);
                }
            }
        }

        if (location == null) {
            location = new Location(ctx);
            sockets.put(id, new Location(ctx));
        }

        sendAllLocationsTo(location);

        logger.info("{} people connected", sockets.size());
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

        String frameText;
        try {
            frameText = mapper.writeValueAsString(json);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return;
        }

        theirLoc.write(frameText);
    }

    public static void removeLocation(String originator) {
        sockets.remove(originator);
        logger.info("removed location: {}", originator);
    }

    public static void handleJsonEvent(ChannelHandlerContext ctx, String frameText) {
        String id = ctx.channel().id().asShortText();
        logger.info("websocket message from {} data: {}", id, frameText);

        JsonNode event;
        try {
            event = mapper.readTree(frameText);
        } catch (IOException e) {
            logger.error("Could not parse incoming websocket json: ", e);
            return;
        }
        String action = event.get("action").toString();
        
        Location location = sockets.get(id);
        if (location == null) {
            location = new Location(ctx);
            sockets.put(id, location);
        }

        if (action.equals("\"updateLocation\"")) {
            location.update(event);
            ObjectNode data = mapper.createObjectNode();
            data.setAll(location.getLatLngJson());
            broadcastMessage("updateLocation", data, location);
        } else if (action.equals("\"changeFixedLocationState\"")) {
            boolean state = event.get("data").asBoolean();
            location.fixLocation(state);
        }
    }

    private static void broadcastMessage(String action, ObjectNode data, Location originator) {
        ObjectNode json = mapper.createObjectNode();
        json.put("action", action);
        json.set("data", data);

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
        Location location = sockets.get(originator);
        if (location != null) {
            location.setAckPing(true);
        }
    }

    public static void pingAndCleanUpWebSockets() {
        final Runnable pinger = new Runnable() {
            @Override
            public void run() {
                Instant now = Instant.now();
                sockets.forEach((id, location) -> {
                    Instant since = location.getFixedLocationSince();
                    if (since != null) {
                        if (now.isAfter(since)) {
                            logger.info("removed fixed location");
                            removeLocation(id);
                        }
                    } else {
                        if (!location.getAckPing()) {
                            removeLocation(id);
                            logger.info("removed stagnant location");
                        } else {
                            location.setAckPing(false);
                            location.sendPing();
                        }
                    }
                });
            }
        };

        scheduler.scheduleAtFixedRate(pinger, 600, 600, TimeUnit.SECONDS);
    }
}