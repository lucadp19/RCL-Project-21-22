package winsome.server.datastructs;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import winsome.api.exceptions.AlreadyRewinnedException;
import winsome.api.exceptions.AlreadyVotedException;
import winsome.api.exceptions.PostOwnerException;
import winsome.server.exceptions.InvalidJSONFileException;

/** A Rewin in the Winsome Social Network */
public class Rewin extends Post {
    /** Identifier of this post */
    public final int id;
    /** Rewinned post */
    private Post rewinnedPost;
    /** User who created this rewin */
    private String rewinner;

    /**
     * Creates a new rewin from a given post.
     * @param post the post to rewin
     * @param rewinner the username of the rewinner
     * @throws AlreadyRewinnedException if the rewinner had already rewinned the given post
     */
    public Rewin(Post post, String rewinner) throws AlreadyRewinnedException { 
        id = getNextID();

        rewinnedPost = Objects
            .requireNonNull(post, "the post to rewin must not be null")
            .getOriginalPost();
        if(!rewinnedPost.addRewinner(rewinner))
            throw new AlreadyRewinnedException("user had already rewinned this post");

        this.rewinner = Objects.requireNonNull(rewinner, "the rewinner username must not be null");
    }

    private Rewin(int id, Post post, String rewinner) {
        this.id = id;
        rewinnedPost = Objects
            .requireNonNull(post, "the post to rewin must not be null")
            .getOriginalPost();
        this.rewinner = Objects.requireNonNull(rewinner, "the rewinner username must not be null");
    }

    @Override
    public int getID(){ return id; }

    @Override
    public String getAuthor(){ return rewinnedPost.getAuthor(); }

    @Override
    public String getTitle(){ return rewinnedPost.getTitle(); }
    
    @Override
    public String getContents(){ return rewinnedPost.getContents(); }

    /**
     * Checks if this post is a rewin, i.e., always returns true.
     * @return true
     */
    @Override
    public boolean isRewin(){ return true; }
    /**
     * Returns the username of the user who created this rewin.
     * @return the rewinner of this post
     */
    public String getRewinner() { return rewinner; }
    /**
     * Returns the ID of the original, rewinned post.
     * @return the ID of the original post
     */ 
    public int getOriginalID() { return rewinnedPost.getOriginalID(); }
    /**
     * Returns the original, rewinned post.
     * @return the original post
     */ 
    public Post getOriginalPost() { return rewinnedPost.getOriginalPost(); }

    @Override
    public boolean addRewinner(String username) { return rewinnedPost.addRewinner(username); }

    @Override
    public boolean hasRewinned(String username) { return rewinnedPost.hasRewinned(username); }

    @Override
    public List<String> getUpvoters() { return rewinnedPost.getUpvoters(); }
    
    @Override
    public List<String> getDownvoters() { return rewinnedPost.getDownvoters(); }
    
    @Override
    public List<Comment> getComments() { return rewinnedPost.getComments(); }
    
    @Override
    public void upvote(String voter) throws AlreadyVotedException { rewinnedPost.upvote(voter); }
   
    @Override
    public void downvote(String voter) throws AlreadyVotedException { rewinnedPost.downvote(voter); }

    @Override
    public void addComment(String author, String contents) throws PostOwnerException {
        rewinnedPost.addComment(author, contents);
    }

    // -------------- Serialization -------------- //

    /**
     * Serializes this post through a JSON stream.
     * @param writer the given JSON stream
     * @throws IOException if some IO error occurs
     */
    public void toJson(JsonWriter writer) throws IOException {
        Objects.requireNonNull(writer, "json writer must not be null")
            .beginObject()
            .name("id").value(this.id)
            .name("original-id").value(this.getOriginalID())
            .name("rewinner").value(this.rewinner)
            .endObject();
    }
    
    /**
     * Returns whether the given JsonObject is a valid serialization of a Rewin.
     * @param json the given JsonObject
     * @return true if and only if json is a valid Rewin
     */
    public static boolean isRewinJson(JsonObject json){
        Objects.requireNonNull(json, "the serialized json must not be null");

        return (json.get("id") != null && json.get("original-id") != null && json.get("rewinner") != null);
    }

    /**
     * Returns the original ID of this JsonObject representing a Rewin.
     * @param json the given JsonObject
     * @return the original ID of the serialized Rewin
     * @throws IllegalArgumentException if the given json is not a valid serialization of a Rewin 
     */
    public static int getOriginalIDFromJson(JsonObject json){
        Objects.requireNonNull(json, "the serialized json must not be null");

        if(!isRewinJson(json)) throw new IllegalArgumentException("the given json is not a valid Rewin");
        return json.get("original-id").getAsInt();
    }

    /**
     * Returns the username of the Rewinner of this JsonObject representing a Rewin.
     * @param json the given JsonObject
     * @return the rewinner of the serialized Rewin
     * @throws IllegalArgumentException if the given json is not a valid serialization of a Rewin 
     */
    private static String getRewinnerFromJson(JsonObject json){
        Objects.requireNonNull(json, "the serialized json must not be null");

        if(!isRewinJson(json)) throw new IllegalArgumentException("the given json is not a valid Rewin");
        return json.get("rewinner").getAsString();        
    }

    /**
     * Returns the ID of this JsonObject representing a Rewin.
     * @param json the given JsonObject
     * @return the ID of the serialized Rewin
     * @throws IllegalArgumentException if the given json is not a valid serialization of a Rewin 
     */
    private static int getIDFromJson(JsonObject json){
        Objects.requireNonNull(json, "the serialized json must not be null");

        if(!isRewinJson(json)) throw new IllegalArgumentException("the given json is not a valid Rewin");
        return json.get("id").getAsInt();        
    }

    /**
     * Deserializes a JsonObject (representing a Rewin) into a Rewin.
     * @param post the post to rewin
     * @param json the given JsonObject
     * @return the Rewin
     * @throws IllegalArgumentException if the given json is not a valid serialization of a Rewin, 
     *      or if the given json and the given Post's original IDs do not correspond 
     */
    public static Rewin getRewinFromJson(Post post, JsonObject json){
        Objects.requireNonNull(post, "the post to rewin must not be null");
        Objects.requireNonNull(json, "the serialized json must not be null");

        if(post.getOriginalID() != getOriginalIDFromJson(json))
            throw new IllegalArgumentException("the given Post and Json have different Post IDs");
        return new Rewin(getIDFromJson(json), post.getOriginalPost(), getRewinnerFromJson(json));
    }

    /**
     * Reads the data of a Rewin from a JSON stream.
     * @param reader the given JSON stream
     * @return a JSON Object containing the rewin's data
     * @throws IOException if some IO error occurs
     * @throws InvalidJSONFileException if the given JSON stream does not contain a valid Rewin
     */
    public static JsonObject getDataFromJsonReader(JsonReader reader) throws IOException, InvalidJSONFileException {
        Objects.requireNonNull(reader, "json reader must not be null");

        JsonObject json = new JsonObject();
        try {
            reader.beginObject();

            while(reader.hasNext()){
                String property = reader.nextName();

                switch (property) {
                    case "id"           -> json.addProperty(property, reader.nextInt());
                    case "original-id"  -> json.addProperty(property, reader.nextInt());
                    case "rewinner"     -> json.addProperty(property, reader.nextString());
                    default -> throw new InvalidJSONFileException();
                }
            }
            reader.endObject();
            
            return json;
        } catch (ClassCastException | IllegalStateException | NullPointerException | NumberFormatException ex){
            throw new InvalidJSONFileException("json reader does not read a valid Rewin");
        }
    }
}
