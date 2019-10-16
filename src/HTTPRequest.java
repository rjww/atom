/*
    :: src/HTTPRequest.java

    Author   Robert Woods <hi@robertwoods.me>
    Source   https://github.com/rjww/atom

    A plain-old-data object representing a parsed HTTP request.
*/

package rjww.atom;

import java.util.UUID;

public class HTTPRequest {
    public String method;
    public String resource;
    public UUID uuid;
    public int lamportTime;
    public String body;
}
