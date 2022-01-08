package winsome.api.codes;

import java.util.Objects;

import com.google.gson.JsonObject;

import winsome.api.exceptions.MalformedJSONException;

/** A Response code for the communication Server-Client */
public enum ResponseCode {
    /** Successful operation */
    SUCCESS                 ("successful operation"),
    /** Request was malformed */
    MALFORMED_JSON_REQUEST  ("malformed request"),
    /** The given user is not signed up */
    USER_NOT_REGISTERED     ("user does not exist"),
    /** Wrong password in login */
    WRONG_PASSW             ("wrong password"),
    /** User or client is already logged */
    ALREADY_LOGGED          ("user or client is already logged"),
    /** Client is not logged in */
    NO_LOGGED_USER          ("client is not logged in"),
    /** Client is logged on another user */
    WRONG_USER              ("client is logged on another user"),
    /** Client cannot see another user */
    USER_NOT_VISIBLE        ("client has no common tags with other user"),
    /** User cannot follow/unfollow themselves */
    SELF_FOLLOW             ("cannot follow/unfollow oneself"),
    /** User already followed the other user */
    ALREADY_FOLLOWED        ("this user already follows the other user"),
    /** User did not follow the other user */
    NOT_FOLLOWING           ("this user does not currently follow the other user"),
    /** Title or contents of post exceed limits */
    TEXT_LENGTH             ("title or contents of post exceed maximum length"),
    /** Post does not exist */
    NO_POST                 ("no post with the given ID"),
    /** User is not the owner of the post */
    NOT_POST_OWNER          ("this user is not the author/rewinner of the given post"),
    /** User is the owner of the post */
    POST_OWNER              ("this user is the author/rewinner of the given post"),
    /** Rewin error: user is author of the post or has already rewinned it */
    REWIN_ERR               ("this user has already rewinned the given post"),
    /** The user had already voted the post */
    ALREADY_VOTED           ("this user has already voted the given post"),
    /** The vote was in a wrong format */
    WRONG_VOTE_FORMAT       ("vote was not +1 or -1"),
    /** Could not compute the exchange rate to BTC */
    EXCHANGE_RATE_ERROR     ("server could not comupte the exchange rate to BTC"),
    /** Fatal communication error */
    FATAL_ERR               ("fatal communication error");

    /** Name of the response field in Json representation */
    private static final String responseFieldName = "response-code";

    private final String msg;
    ResponseCode(String msg){ this.msg = Objects.requireNonNull(msg, "null ResponseCode message"); }

    public String getMessage(){ return this.msg; }

    /**
     * Adds a response code to a Json object.
     * @param json the given json
     * @param code the response code
     * @throws NullPointerException if either json or code are null
     */
    public static void addResponseToJson(JsonObject json, ResponseCode code){
        Objects.requireNonNull(json, "the given json object must not be null");
        Objects.requireNonNull(code, "the request code to add must not be null");

        json.addProperty(responseFieldName, code.toString());
    }

    /**
     * Adds this response code to a Json object.
     * @param json the given json
     * @throws NullPointerException if either json or code are null
     */
    public void addResponseToJson(JsonObject json){
        Objects.requireNonNull(json, "the given json object must not be null");
        ResponseCode.addResponseToJson(json, this);
    }

    /**
     * Gets a response code from a Json message.
     * @param json the given json
     * @return the response code contained in the given json
     * @throws MalformedJSONException if the given Json does not contain a valid response code 
     */
    public static ResponseCode getResponseFromJson(JsonObject json) throws MalformedJSONException {
        Objects.requireNonNull(json, "the given json object must not be null");

        try { return ResponseCode.valueOf(json.get(responseFieldName).getAsString()); }
        catch(ClassCastException | IllegalStateException | NullPointerException | IllegalArgumentException ex){ 
            throw new MalformedJSONException("the given json did not contain a valid response code field", ex); 
        }
    }
}
