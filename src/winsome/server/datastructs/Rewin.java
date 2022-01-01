package winsome.server.datastructs;

import java.util.List;

import com.google.gson.JsonObject;

public class Rewin extends Post {
    public final int id;
    private Post rewinnedPost;
    private String rewinner;

    public Rewin(Post post, String rewinner) throws IllegalStateException { 
        id = getNextID();
        rewinnedPost = post.getOriginalPost();
        this.rewinner = rewinner;
    }

    private Rewin(int id, Post post, String rewinner) {
        this.id = id;
        rewinnedPost = post.getOriginalPost();
        this.rewinner = rewinner;
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
    public List<String> getUpvoters() { return rewinnedPost.getUpvoters(); }
    
    @Override
    public List<String> getDownvoters() { return rewinnedPost.getDownvoters(); }
    
    @Override
    public List<Comment> getComments() { return rewinnedPost.getComments(); }
    
    @Override
    public void upvote(String voter) throws IllegalArgumentException { rewinnedPost.upvote(voter); }
   
    @Override
    public void downvote(String voter) throws IllegalArgumentException { rewinnedPost.downvote(voter); }

    @Override
    public void addComment(String author, String contents) throws IllegalArgumentException {
        rewinnedPost.addComment(author, contents);
    }

    // -------------- Serialization -------------- //

    /**
     * Returns a JsonObject containing a serialized version of this post.
     * @return a JsonObject containing a serialized version of this post
     */
    public JsonObject toJson(){
        JsonObject json = new JsonObject();

        json.addProperty("id", id);
        json.addProperty("originalID", this.getOriginalID());
        json.addProperty("rewinner", this.rewinner);

        return json;
    }
    
    /**
     * Returns whether the given JsonObject is a valid serialization of a Rewin.
     * @param json the given JsonObject
     * @return true if and only if json is a valid Rewin
     */
    public static boolean isRewinJson(JsonObject json){
        if(json == null) throw new NullPointerException("null parameter");
        return (json.get("id") != null && json.get("originalID") != null && json.get("rewinner") != null);
    }

    /**
     * Returns the original ID of this JsonObject representing a Rewin.
     * @param json the given JsonObject
     * @return the original ID of the serialized Rewin
     * @throws IllegalArgumentException if the given json is not a valid serialization of a Rewin 
     */
    public static int getOriginalIDFromJson(JsonObject json){
        if(!isRewinJson(json)) throw new IllegalArgumentException("the given json is not a valid Rewin");
        return json.get("originalID").getAsInt();
    }

    /**
     * Returns the username of the Rewinner of this JsonObject representing a Rewin.
     * @param json the given JsonObject
     * @return the rewinner of the serialized Rewin
     * @throws IllegalArgumentException if the given json is not a valid serialization of a Rewin 
     */
    private static String getRewinnerFromJson(JsonObject json){
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
        if(post.getOriginalID() != getOriginalIDFromJson(json))
            throw new IllegalArgumentException("the given Post and Json have different Post IDs");
        return new Rewin(getIDFromJson(json), post.getOriginalPost(), getRewinnerFromJson(json));
    }
}