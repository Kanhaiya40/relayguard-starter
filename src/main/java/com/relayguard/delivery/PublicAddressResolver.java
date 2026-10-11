package com.relayguard.delivery;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import org.springframework.stereotype.Component;

@Component
public class PublicAddressResolver {
    public InetAddress[] resolve(String host) throws UnknownHostException {
        InetAddress[] addresses = InetAddress.getAllByName(host);
        if (addresses.length == 0 || Arrays.stream(addresses).anyMatch(address -> !DestinationUrlValidator.isPublicAddress(address))) {
            throw new UnknownHostException("Host did not resolve exclusively to public addresses");
        }
        return addresses;
    }
}
