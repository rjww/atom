/*
    :: src/HTTPResponse.java

    Author   Robert Woods <hi@robertwoods.me>
    Source   https://github.com/rjww/atom

    A plain-old-data object representing a parsed HTTP response.
*/

package rjww.atom;

public class HTTPResponse {
    public int statusCode;
    public int lamportTime;
    public String body;
}
