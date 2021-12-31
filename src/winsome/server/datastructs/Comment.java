package winsome.server.datastructs;

import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
    
/** A Comment on a Post in the Winsome Social Network */
public class Comment {
    /** Author of this comment */
    public final String author;
    /** Contents of this comment */
    public final String contents;
    /** Whether this comment has already been accounted for in the Rewards Algorithm  */
    private boolean read = false;

    /**
     * Creates a new comment.
     * @param author this comment's author
     * @param contents this comment's contents
     * @throws NullPointerException if author or contents are null
     */
    public Comment(String author, String contents) throws NullPointerException {
        this(author, contents, false);
    }

    /**
     * Creates a new comment.
     * @param author this comment's author
     * @param contents this comment's contents
     * @param read whether this comment has been accounted for by the Rewards Algorithm
     * @throws NullPointerException if author or contents are null
     */
    private Comment(String author, String contents, boolean read) throws NullPointerException {
        if(author == null || contents == null) throw new NullPointerException("null parameter in comment creation");
        this.author = author;
        this.contents = contents;
        this.read = read;
    }

    /**
     * Returns whether or not this comment has been accounted for by the Rewards Algorithm.
     * @return true if and only if this comment has been counted
     */
    public boolean isRead(){ return read; }

    /**
     * Sets this comments "read" status to true.
     */
    public void setRead(){ read = true; }

    /**
     * Returns this comment formatted as a JsonObject.
     * @return this comment formatted as a JsonObject
     */
    public JsonObject toJson(){
        JsonObject json = new JsonObject();

        json.addProperty("author", author);
        json.addProperty("contents", contents);
        json.addProperty("read", read);

        return json;
    }

    /**
     * Parses a new Comment from a JsonObject.
     * @param json the given JsonObject
     * @return a new Comment, obtained from the given json
     * @throws IllegalArgumentException if json does not represent a valid Comment
     */
    public static Comment fromJson(JsonObject json){
        try {
            String author   = json.get("author").getAsString();
            String contents = json.get("contents").getAsString();
            boolean read    = json.get("read").getAsBoolean();

            return new Comment(author, contents, read);
        } catch(NullPointerException | ClassCastException | IllegalStateException ex){
            throw new IllegalArgumentException("parameter does not represent a valid Comment");
        }
    }
}


