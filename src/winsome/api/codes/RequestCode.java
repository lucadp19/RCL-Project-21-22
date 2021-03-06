package winsome.api.codes;

import java.util.Objects;

import com.google.gson.JsonObject;

import winsome.api.exceptions.MalformedJSONException;

/** A Request code in the communication Client-Server */
public enum RequestCode {
    /** Get multicast address */
    MULTICAST,
    /** Login request */
    LOGIN,
    /** Logout request */
    LOGOUT,
    /** Get Winsome Users */
    GET_USERS,
    /** Get currently followed users */
    GET_FOLLOWING,
    /** Follow request */
    FOLLOW,
    /** Unfollow request */
    UNFOLLOW,
    /** List current user's posts */
    BLOG,
    /** New post request */
    POST,
    /** Get feed request */
    FEED,
    /** Show a post */
    SHOW_POST,
    /** Delete a post */
    DELETE_POST,
    /** Rewin a post */
    REWIN_POST,
    /** Rate a post */
    RATE_POST,
    /** Add a comment to a post */
    COMMENT,
    /** Get wallet */
    WALLET,
    /** Get wallet in bitcoins */
    WALLET_BTC;

    /** Name of the request field in Json representation */
    private static final String requestFieldName = "request-code";

    /**
     * Adds a request code to a Json object.
     * @param json the given json
     * @param code the request code
     */
    public static void addRequestToJson(JsonObject json, RequestCode code){
        Objects.requireNonNull(json, "the given json object must not be null");
        Objects.requireNonNull(code, "the request code to add must not be null");

        json.addProperty(requestFieldName, code.toString());
    }

    /**
     * Adds this request code to a Json object.
     * @param json the given json
     */
    public void addRequestToJson(JsonObject json){
        Objects.requireNonNull(json, "the given json object must not be null");

        RequestCode.addRequestToJson(json, this);
    }

    /**
     * Gets a request code from a Json message.
     * @param json the given json
     * @return the request code contained in the given json
     * @throws MalformedJSONException if the given Json does not contain a valid request code 
     */
    public static RequestCode getRequestFromJson(JsonObject json) throws MalformedJSONException {
        Objects.requireNonNull(json, "the given json object must not be null");

        try { return RequestCode.valueOf(json.get(requestFieldName).getAsString()); }
        catch(ClassCastException | IllegalStateException | NullPointerException | IllegalArgumentException ex){ 
            throw new MalformedJSONException("the given json did not contain a valid request code field"); 
        }
    }
 
}
