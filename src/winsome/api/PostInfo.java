package winsome.api;

import java.util.*;

import winsome.utils.ConsoleColors;

public class PostInfo {
    public class Comment {
        public final String author;
        public final String contents;

        public Comment(String author, String contents) throws NullPointerException {
            if(author == null || contents == null) throw new NullPointerException("null parameter in comment creation");
            this.author = author;
            this.contents = contents;
        }

        public String prettify(){ return this.author + ": \"" + this.contents + "\""; }
    }

    public final int id;
    public final String author;
    public final String title;
    public final String contents;

    public final Optional<String> rewinner;
    public final Optional<Integer> rewinID;

    public final int upvotes;
    public final int downvotes;

    private final List<Comment> comments;

    public PostInfo(int id, String author, String title, String contents, int upvotes, int downvotes, List<Comment> comments) 
            throws NullPointerException {
        this(id, author, title, contents, Optional.empty(), Optional.empty(), upvotes, downvotes, comments);
    }

    public PostInfo(
        int id,
        String author, String title, String contents, 
        Optional<String> rewinner, Optional<Integer> rewinID,
        int upvotes, int downvotes, List<Comment> comments
    ) throws NullPointerException {
        if(author == null || title == null || contents == null || rewinner == null || rewinID == null || comments == null) 
            throw new NullPointerException("null parameters in PostInfo creation");
        for(Comment comment : comments)
            if(comment == null) throw new NullPointerException("null parameters in PostInfo creation");

        this.id = id;
        this.author = author;
        this.title = title;
        this.contents = contents;
        this.rewinner = rewinner;
        this.rewinID = rewinID;
        this.upvotes = upvotes;
        this.downvotes = downvotes;
        this.comments = new ArrayList<>(comments);
    }

    public boolean isRewin(){ return rewinner.isPresent(); }

    public synchronized String prettify(){
        String str = 
            ConsoleColors.green("Title: ") + this.title + "\n"
            + ConsoleColors.green("Contents: ") + this.contents + "\n"
            + ConsoleColors.green("Votes: ") + 
                this.upvotes + " upvotes, " + this.downvotes + " downvotes\n" 
            + ConsoleColors.green("Comments: ");
        if(comments.isEmpty()) str += "0\n";
        else { 
            for(Comment comment : comments) str += "    " + comment.prettify() + "\n";
        }

        return str;
    }
}
