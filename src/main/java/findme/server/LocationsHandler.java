package findme.server;

import com.fasterxml.jackson.databind.JsonNode;
import io.netty.channel.ChannelHandlerContext;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LocationsHandler {
    private static final Map<String, Location> sockets = new ConcurrentHashMap<>();

    public static void handleJsonEvent(ChannelHandlerContext ctx, JsonNode event) {

    }

}