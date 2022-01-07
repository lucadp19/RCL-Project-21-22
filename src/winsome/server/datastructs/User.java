package winsome.server.datastructs;

import java.io.IOException;
import java.util.*;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import winsome.server.exceptions.InvalidJSONFileException;
import winsome.utils.cryptography.Hash;

/**
 * A User of the Winsome Social Network.
 */
public class User {
    /** Username of this user */
    private final String username;
    /** Password of this user */
    private final Hash password;
    /** Tags of this user */
    private final Set<String> tags;

    /**
     * Creates a new instance of User.
     * @param username the username of the new user
     * @param password the hashed password of the new user
     * @param tags the tags the new user is interested in
     * @throws NullPointerException if username, password, tags or one of the elements of tags is null
     * @throws IllegalArgumentException if there is fewer than 1 tag or more than 5
     */
    public User(String username, Hash password, Collection<String> tags){
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
    public Hash getPassword(){ return password; }
    /**
     * Returns this user's tags
     * @return the tags set by this user
     */
    public Set<String> getTags(){ return new HashSet<>(tags); }

    /**
     * Checks if this user has tags in common with another user
     * @param other user we want to compare this to
     * @return true if and only if this user has tags in common with other
     */
    public boolean hasCommonTags(User other){
        return !Collections.disjoint(this.tags, other.getTags());
    }
    
    /**
     * Checks if this user has tags in common with another set of tags
     * @param otherTags tags used for the comparison
     * @return true if and only if this user has tags in common with otherTags
     */
    public boolean hasCommonTags(Collection<String> otherTags){
        return !Collections.disjoint(this.tags, otherTags);
    }

    /**
     * Serializes this User through a JSON stream.
     * @param writer the given JSON stream
     * @throws IOException if some IO error occurs
     */
    public void toJson(JsonWriter writer) throws IOException {
        Objects.requireNonNull(writer, "json writer must not be null")
            .beginObject()
            .name("username").value(this.username)
            .name("password"); this.password.toJson(writer);

        writer.name("tags");
        writer.beginArray();
        for(String tag : tags) writer.value(tag);
        writer.endArray();

        writer.endObject();
    }
 
    /**
     * Deserializes a User from a given JSON stream.
     * @param reader the given JSON stream
     * @return the deserialized user
     * @throws InvalidJSONFileException if the reader does not read a valid User
     * @throws IOException if some IO error occurs
     */
    public static User fromJson(JsonReader reader) throws InvalidJSONFileException, IOException {
        Objects.requireNonNull(reader, "json reader must not be null");

        try {
            String username = null;
            Hash password = null;
            Set<String> tags = new HashSet<>();
            
            reader.beginObject();
            while(reader.hasNext()){
                String property = reader.nextName();

                switch (property) {
                    case "username" -> username = reader.nextString();
                    case "password" -> password = Hash.fromJson(reader);
                    case "tags" -> {
                        reader.beginArray();
                        while(reader.hasNext()){
                            tags.add(reader.nextString());
                        }
                        reader.endArray();
                    }
                    default -> throw new InvalidJSONFileException(property + ": parse error in json file");
                }
            }
            reader.endObject();

            return new User(username, password, tags);
        } catch (ClassCastException | IllegalStateException | NullPointerException ex) {
            throw new InvalidJSONFileException("parameter did not represent a valid User", ex);
        }       
    }
}
