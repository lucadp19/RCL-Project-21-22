package winsome.server;

import java.util.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * A User of the Winsome Social Network.
 */
public class User {
    /** Username of this user */
    private final String username;
    /** Password of this user */
    private final String password;
    /** Tags of this user */
    private final Set<String> tags;

    /**
     * Creates a new instance of User.
     * @param username the username of the new user
     * @param password the password of the new user
     * @param tags the tags the new user is interested in
     * @throws NullPointerException if username, password, tags or one of the elements of tags is null
     * @throws IllegalArgumentException if there is fewer than 1 tag or more than 5
     */
    public User(String username, String password, Collection<String> tags){
        // checking for nulls
        if(username == null || password == null || tags == null)
            throw new NullPointerException("null arguments in User constructor");
        if(tags.size() < 1 || tags.size() > 5)
            throw new IllegalArgumentException("a User must have at least one tag and at most 5");
        for(String tag : tags)
            if(tag == null)
                throw new NullPointerException("null arguments in User constructor");
        
        this.username = username;
        this.password = password;
        this.tags = new HashSet<>(tags);
    }

    /**
     * Returns the username of this user.
     * @return the username of this user
     */
    public String getUsername(){ return username; }

    /**
     * Returns the password of this user.
     * @return the password of this user
     */
    public String getPassword(){ return password; }
    /**
     * Returns this user's tags
     * @return the tags set by this user
     */
    public Set<String> getTags(){ return new HashSet<>(tags); }

    /**
     * Checks if this user has tags in common with another set of tags
     * @param otherTags tags used for the comparison
     * @return true if and only if this user has tags in common with otherTags
     */
    public boolean hasCommonTags(Collection<String> otherTags){
        return !Collections.disjoint(this.tags, otherTags);
    }

    /**
     * Returns a Json version of this user.
     * @return this user in json format
     */
    public JsonObject toJson(){
        // creating JsonObject
        JsonObject json = new JsonObject();

        // adding username and password
        json.addProperty("username", username);
        json.addProperty("password", password);

        // creating and then adding tags
        JsonArray jsonTags = new JsonArray();
        tags.forEach(tag -> jsonTags.add(tag));

        json.add("tags", jsonTags);

        return json;
    }

    /**
     * Takes a JsonObject and creates a new User from the given information.
     * @param json the given JsonObject
     * @return the User created from the JsonObject
     * @throws IllegalArgumentException whenever json does not represent a valid User
     */
    public static User fromJson(JsonObject json) throws IllegalArgumentException {
        // null checking
        if(json == null) throw new NullPointerException("null json object");

        // getting the relevant fields
        JsonElement userJson = json.get("username");
        JsonElement passJson = json.get("password");
        JsonElement tagsJson = json.get("tags");

        User user = null;
        
        try {
            String username = userJson.getAsString();
            String password = passJson.getAsString();
            Set<String> tags = new HashSet<>();
            
            // adding tags to the set
            Iterator<JsonElement> iter = tagsJson.getAsJsonArray().iterator();
            while(iter.hasNext()){
                JsonElement tagJson = iter.next();
                tags.add(tagJson.getAsString());
            }

            user = new User(username, password, tags);
        } catch (ClassCastException | IllegalStateException | NullPointerException ex) {
            throw new IllegalArgumentException("parameter did not represent a valid User"); // TODO: create exception
        }

        return user;        
    }
}
