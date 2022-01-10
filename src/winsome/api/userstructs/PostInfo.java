package winsome.api.userstructs;

import java.util.*;

/** A class containing the information relative to a post */
public class PostInfo {
    /** A comment under a post */
    public static class Comment {
        /** Author of the comment */
        public final String author;
        /** Contents of the comment */
        public final String contents;

        public Comment(String author, String contents) {
            this.author = Objects.requireNonNull(author, "author of the comment must not be null");
            this.contents = Objects.requireNonNull(contents, "contents of the comment must not be null");
        }
    }

    /** Identifier of the post */
    public final int id;
    /** Author of the post */
    public final String author;
    /** Title of the post */
    public final String title;
    /** Contents of the post */
    public final String contents;

    /** Rewinner of this post, if any */
    public final Optional<String> rewinner;
    /** ID of the original post */
    public final Optional<Integer> originalID;

    /** Number of upvotes */
    public final int upvotes;
    /** Number of downvotes */
    public final int downvotes;

    /** List of comments of this post */
    private final List<Comment> comments;

    public PostInfo(int id, String author, String title, String contents, int upvotes, int downvotes, List<Comment> comments) 
            throws NullPointerException {
        this(id, author, title, contents, Optional.empty(), Optional.empty(), upvotes, downvotes, comments);
    }

    public PostInfo(
        int id, String author, String title, String contents, 
        Optional<String> rewinner, Optional<Integer> originalID,
        int upvotes, int downvotes, List<Comment> comments
    ) {
        this.id = id;
        this.author = Objects.requireNonNull(author, "author must not be null");
        this.title = Objects.requireNonNull(title, "title must not be null");
        this.contents = Objects.requireNonNull(contents, "contents must not be null");
        this.rewinner = Objects.requireNonNull(rewinner, "rewinner must not be null");
        this.originalID = Objects.requireNonNull(originalID, "originalID must not be null");
        this.upvotes = upvotes;
        this.downvotes = downvotes;
        
        Objects.requireNonNull(comments, "list of comments must not be null");
        for(Comment comment : comments)
            Objects.requireNonNull(comment, "each comment under a post must not be null");
        this.comments = new ArrayList<>(comments);
    }

    /**
     * Checks whether this post is a rewin.
     * @return true if and only if this post is a rewin
     */
    public boolean isRewin(){ return rewinner.isPresent(); }
    /**
     * Returns the comments under this post.
     * @return the list of comments
     */
    public List<Comment> getComments(){ return new ArrayList<>(comments); }
}
