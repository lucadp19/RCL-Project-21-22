package winsome.server.datastructs;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import winsome.server.exceptions.InvalidJSONFileException;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

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
     * Writes this comment onto a JSON stream.
     * @param writer the given JSON stream
     * @throws IOException if some IO error occurs while writing
     */
    public void toJson(JsonWriter writer) throws IOException {
        Objects.requireNonNull(writer, "json writer must not be null")
            .beginObject()
            .name("author").value(author)
            .name("contents").value(contents)
            .name("visited").value(visited.get())
            .endObject();
    }

    /**
     * Parses a new Comment from a JSON stream.
     * @param reader the given JsonReader
     * @return a new Comment, obtained from the given json
     * @throws InvalidJSONFileException if reader does not read a valid Comment
     * @throws IOException if an IO error occurs
     */
    public static Comment fromJson(JsonReader reader) throws InvalidJSONFileException, IOException {
        Objects.requireNonNull(reader, "json reader must not be null");
        
        try {
            String author   = null;
            String contents = null;
            Boolean visited    = null;

            reader.beginObject();
            while(reader.hasNext()){
                String property = reader.nextName();
                
                switch (property) {
                    case "author"   -> author = reader.nextString();
                    case "contents" -> contents = reader.nextString();
                    case "visited"  -> visited = reader.nextBoolean();
                    default -> throw new InvalidJSONFileException("parse error in json file");
                }
            }
            reader.endObject();

            return new Comment(author, contents, visited);
        } catch(NullPointerException | ClassCastException | IllegalStateException ex){
            throw new IllegalArgumentException("parameter does not represent a valid Comment");
        }
    }
}


