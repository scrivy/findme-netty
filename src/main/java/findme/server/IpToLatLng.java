package findme.server;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.GeoIp2Exception;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;

/**
 * Created by rocknice on 3/30/15.
 */
public class IpToLatLng {
    private static final IpToLatLng INSTANCE = new IpToLatLng();

    private final File db = new File("GeoLite2-City.mmdb");
    private DatabaseReader reader;

    private IpToLatLng() {
        try {
            reader = new DatabaseReader.Builder(db).build();
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("could not initialize iptolatlng");
        }
    }

    public static IpToLatLng getInstance() {
        return INSTANCE;
    }

    public com.maxmind.geoip2.record.Location getLocationFromIP(InetAddress ip)
            throws IOException, GeoIp2Exception {

        return reader.city(ip).getLocation();
    }
}
