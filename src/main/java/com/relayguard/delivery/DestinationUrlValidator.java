package com.relayguard.delivery;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class DestinationUrlValidator {
    private final PublicAddressResolver addressResolver;

    public DestinationUrlValidator(PublicAddressResolver addressResolver) {
        this.addressResolver = addressResolver;
    }

    public URI validate(String value) {
        if (value == null || value.length() > 2048) throw invalid();
        try {
            URI uri = new URI(value).normalize();
            String scheme = uri.getScheme();
            if (!uri.isAbsolute() || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    || uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null
                    || uri.getPort() == 0 || uri.getPort() > 65535) {
                throw invalid();
            }
            String host = uri.getHost();
            String resolvedHost = host.startsWith("[") && host.endsWith("]")
                    ? host.substring(1, host.length() - 1) : host;
            addressResolver.resolve(resolvedHost);
            return uri;
        } catch (URISyntaxException | java.net.UnknownHostException exception) {
            throw invalid();
        }
    }

    static boolean isPublicAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) return false;
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) return isPublicIpv4(bytes);
        if (isIpv4MappedAddress(bytes)) return isPublicIpv4(java.util.Arrays.copyOfRange(bytes, 12, 16));
        // Accept global-unicast 2000::/3 only; exclude transition and documentation ranges.
        if ((bytes[0] & 0xE0) != 0x20) return false;
        if (startsWith(bytes, new byte[] {0x20, 0x01, 0x0d, (byte) 0xb8})) return false; // 2001:db8::/32
        if (bytes[0] == 0x20 && bytes[1] == 0x02) return false; // 6to4 can tunnel to private IPv4
        return true;
    }

    private static boolean isPublicIpv4(byte[] bytes) {
        int address = ((bytes[0] & 0xff) << 24) | ((bytes[1] & 0xff) << 16)
                | ((bytes[2] & 0xff) << 8) | (bytes[3] & 0xff);
        return !inCidr(address, 0x00000000, 8)   // this network
                && !inCidr(address, 0x0a000000, 8)
                && !inCidr(address, 0x64400000, 10) // carrier-grade NAT
                && !inCidr(address, 0x7f000000, 8)
                && !inCidr(address, 0xa9fe0000, 16)
                && !inCidr(address, 0xac100000, 12)
                && !inCidr(address, 0xc0000000, 24)
                && !inCidr(address, 0xc0000200, 24)
                && !inCidr(address, 0xc0586300, 24)
                && !inCidr(address, 0xc0a80000, 16)
                && !inCidr(address, 0xc6120000, 15)
                && !inCidr(address, 0xc6336400, 24)
                && !inCidr(address, 0xcb007100, 24)
                && !inCidr(address, 0xe0000000, 3);
    }

    private static boolean inCidr(int address, int network, int prefix) {
        int mask = prefix == 0 ? 0 : -1 << (32 - prefix);
        return (address & mask) == (network & mask);
    }

    private static boolean isIpv4MappedAddress(byte[] bytes) {
        if (bytes.length != 16) return false;
        for (int i = 0; i < 10; i++) if (bytes[i] != 0) return false;
        return bytes[10] == (byte) 0xff && bytes[11] == (byte) 0xff;
    }

    private static boolean startsWith(byte[] value, byte[] prefix) {
        for (int i = 0; i < prefix.length; i++) if (value[i] != prefix[i]) return false;
        return true;
    }

    private ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Destination URL must use HTTP or HTTPS and resolve only to public IP addresses");
    }
}
