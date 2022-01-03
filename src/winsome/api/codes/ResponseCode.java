package winsome.api.codes;

import com.google.gson.JsonObject;

import winsome.api.exceptions.MalformedJSONException;

/** A Response code for the communication Server-Client */
public enum ResponseCode {
    /** Successful operation */
    SUCCESS,
    /** Request was malformed */
    MALFORMED_JSON_REQUEST,
    /** The given user is not signed up */
    USER_NOT_REGISTERED,
    /** Wrong password in login */
    WRONG_PASSW,
    /** User or client is already logged */
    ALREADY_LOGGED,
    /** Client is not logged in */
    NO_LOGGED_USER,
    /** Client is logged on another user */
    WRONG_USER,
    /** User already followed the other user */
    ALREADY_FOLLOWED,
    /** User did not follow the other user */
    NOT_FOLLOWING,
    /** Fatal communication error */
    FATAL_ERR;

    /** Name of the response field in Json representation */
    private static final String responseFieldName = "response-code";

    /**
     * Adds a response code to a Json object.
     * @param json the given json
     * @param code the response code
     * @throws NullPointerException if either json or code are null
     */
    public static void addResponseToJson(JsonObject json, ResponseCode code){
        if(json == null || code == null) throw new NullPointerException();

        json.addProperty(responseFieldName, code.toString());
    }

    /**
     * Adds this response code to a Json object.
     * @param json the given json
     * @throws NullPointerException if either json or code are null
     */
    public void addResponseToJson(JsonObject json){
        ResponseCode.addResponseToJson(json, this);
    }

    /**
     * Gets a response code from a Json message.
     * @param json the given json
     * @return the response code contained in the given json
     * @throws MalformedJSONException if the given Json does not contain a valid response code 
     */
    public static ResponseCode getResponseFromJson(JsonObject json) throws MalformedJSONException {
        if(json == null) throw new NullPointerException();

        try { return ResponseCode.valueOf(json.get(responseFieldName).getAsString()); }
        catch(ClassCastException | IllegalStateException | NullPointerException | IllegalArgumentException ex){ 
            throw new MalformedJSONException("the given json did not contain a valid response code field", ex); 
        }
    }
}
