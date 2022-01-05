package winsome.server.datastructs;

import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import winsome.server.exceptions.InvalidJSONFileException;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.gson.JsonElement;
    
/** A Comment on a Post in the Winsome Social Network */
public class Comment {
    /** Author of this comment */
    public final String author;
    /** Contents of this comment */
    public final String contents;
    /** Whether this comment has already been accounted for in the Rewards Algorithm  */
    private final AtomicBoolean visited;

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
     * @param visited whether this comment has been accounted for by the Rewards Algorithm
     * @throws NullPointerException if author or contents are null
     */
    private Comment(String author, String contents, boolean visited) throws NullPointerException {
        if(author == null || contents == null) throw new NullPointerException("null parameter in comment creation");
        this.author = author;
        this.contents = contents;
        this.visited = new AtomicBoolean(visited);
    }

    /**
     * Checks whether this comment has been accounted for by the Rewards Algorithm, 
     * and if it hasn't, it sets its status as 'visited'.
     * @return true if and only if this comment hadn't been accounted for
     */
    public boolean visit(){ return visited.compareAndSet(false, true); }

    /**
     * Returns this comment formatted as a JsonObject.
     * @return this comment formatted as a JsonObject
     */
    // public JsonObject toJson(){
    //     JsonObject json = new JsonObject();

    //     json.addProperty("author", author);
    //     json.addProperty("contents", contents);
    //     json.addProperty("read", read);

    //     return json;
    // }

    public void toJson(JsonWriter writer) throws IOException {
        if(writer == null) throw new NullPointerException("null arguments");

        writer.beginObject();
        writer.name("author").value(author);
        writer.name("contents").value(contents);
        writer.name("visited").value(visited.get());
        writer.endObject();
    }

    /**
     * Parses a new Comment from a JsonObject.
     * @param json the given JsonObject
     * @return a new Comment, obtained from the given json
     * @throws IllegalArgumentException if json does not represent a valid Comment
     */
    // public static Comment fromJson(JsonObject json){
    //     try {
    //         String author   = json.get("author").getAsString();
    //         String contents = json.get("contents").getAsString();
    //         boolean visited = json.get("visited").getAsBoolean();

    //         return new Comment(author, contents, visited);
    //     } catch(NullPointerException | ClassCastException | IllegalStateException ex){
    //         throw new IllegalArgumentException("parameter does not represent a valid Comment");
    //     }
    // }

    /**
     * Parses a new Comment from a JsonReader.
     * @param reader the given JsonReader
     * @return a new Comment, obtained from the given json
     * @throws InvalidJSONFileException if reader does not read a valid Comment
     * @throws IOException if an IO error occurs
     */
    public static Comment fromJson(JsonReader reader) throws InvalidJSONFileException, IOException {
        if(reader == null) throw new NullPointerException("null parameter");
        
        try {
            String author   = null;
            String contents = null;
            Boolean visited    = null;

            reader.beginObject();
            while(reader.hasNext()){
                String property = reader.nextName();
                
                switch (property) {
                    case "author":
                        author = reader.nextString();
                        break;
                    case "contents":
                        contents = reader.nextString();
                        break;
                    case "visited":
                        visited = reader.nextBoolean();
                        break;
                    default:
                        throw new InvalidJSONFileException("parse error in json file");
                }
            }
            reader.endObject();

            return new Comment(author, contents, visited);
        } catch(NullPointerException | ClassCastException | IllegalStateException ex){
            throw new IllegalArgumentException("parameter does not represent a valid Comment");
        }
    }
}


