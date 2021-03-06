package winsome.server.datastructs;

import java.io.IOException;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.gson.JsonObject;
import com.google.gson.stream.JsonWriter;

import winsome.api.exceptions.AlreadyVotedException;
import winsome.api.exceptions.PostOwnerException;

/** A Post in the Winsome Social Network. */
public abstract class Post {
    /** The generator for new IDs */
    private static AtomicInteger idGenerator = null;

    /**
     * Checks whether the ID Generator has been initialized (through {@link #initIDGenerator}).
     * @return true if and only if the ID Generator has been initialized
     */
    public static final boolean isIDGeneratorInit(){ return (idGenerator != null); }

    /**
     * Initializes the ID Generator, with an initial value of 0.
     * @throws IllegalStateException if the ID Generator had already been initialized
     */ 
    public static final void initIDGenerator() throws IllegalStateException { 
        initIDGenerator(0);
    }

    /**
     * Initializes the ID Generator.
     * @param init the initial value of the ID Generator
     * @throws IllegalStateException if the ID Generator had already been initialized
     */
    public static final void initIDGenerator(int init) throws IllegalStateException {
        if(isIDGeneratorInit()) throw new IllegalStateException("ID Generator has already been initialized");
        idGenerator = new AtomicInteger(init);
    }

    /**
     * Gets the next Post ID from the ID Generator.
     * @return the next post ID
     * @throws IllegalStateException if the generator has not been initialized yet
     */
    protected static final int getNextID() throws IllegalStateException {
        if(!isIDGeneratorInit()) throw new IllegalStateException("ID Generator has not been initialized yet");
        return idGenerator.getAndIncrement();
    }
    
    /** 
     * Returns this post's unique identifier. 
     * @return this post's ID
     */
    public abstract int getID();

    /** 
     * Returns this post's author. 
     * @return this post's author 
     */
    public abstract String getAuthor();

    /** 
     * Returns this post's title.
     * @return this post's title
     */
    public abstract String getTitle();
    /** 
     * Returns this post's contents
     * @return this post's contents
     */
    public abstract String getContents();

    /**
     * Checks if this post is a rewin.
     * @return true if and only if this post is a rewin
     */
    public abstract boolean isRewin();
    /**
     * Returns the username of the user who created this rewin, if any.
     * 
     * It is recommended to check if this post is a rewin through {@link #isRewin()}
     * @return the rewinner of this post, if any
     * @throws NoSuchElementException if this post is not a rewin
     */
    public abstract String getRewinner() throws NoSuchElementException;
    /**
     * If this post is a rewin, returns the ID of the rewinned post;
     * otherwise it returns this post's ID.
     * <p>
     * To check whether this post is a rewin or not, use {@link #isRewin()}.
     * @return the ID of this post, if it's original, or the ID of the original rewinned post
     */
    public abstract int getOriginalID() throws NoSuchElementException;
    
    /**
     * Adds a rewinner to the list of rewinners of this post (or of its original version, if it is a rewin).
     * @param username the given user
     * @return true if and only if the operation was successful
     */
    public abstract boolean addRewinner(String username);
    
    /**
     * Checks if some user has rewinned this post (or the original post, if this is a rewin).
     * @param username the given user
     * @return true if and only if the given user has rewinned this post (or its original version)
     */
    public abstract boolean hasRewinned(String username);
    
    /**
     * Returns the original post this post is based on.
     * <p>
     * If this post is already original, it returns this;
     * otherwise it returns the original rewinned post.
     * @return this if this post is original, or the original rewinned post if this is a rewin
     */
    public abstract Post getOriginalPost();

    /**
     * Returns a list with the usernames of the users who have upvoted this post.
     * @return a list with the users who have upvoted this post
     */
    public abstract List<String> getUpvoters();
    
    /**
     * Returns a list with the usernames of the users who have downvoted this post.
     * @return a list with the users who have downvoted this post
     */
    public abstract List<String> getDownvoters();
    
    /**
     * Returns a list with the comments written under this post.
     * @return a list with the comments written under this post
     */
    public abstract List<Comment> getComments();
    
    /**
     * Adds an upvote this post.
     * <p>
     * If this post is a rewin, the user upvotes the original post.
     * @param voter the user who wants to upvote this post
     * @throws NullPointerException if voter is null
     * @throws AlreadyVotedException if voter had already rated this post
     */
    public abstract void upvote(String voter) throws AlreadyVotedException;
   
    /**
     * Adds a downvote this post.
     * <p>
     * If this post is a rewin, the user downvotes the original post.
     * @param voter the user who wants to downvote this post
     * @throws NullPointerException if voter is null
     * @throws AlreadyVotedException if voter had already rated this post
     */
    public abstract void downvote(String voter) throws AlreadyVotedException;

    /**
     * Adds a new comment to this post.
     * <p>
     * If this post is a rewin, the user adds a comment to the original post.
     * @param author author of the comment
     * @param contents contents of the comment
     * @throws NullPointerException if author or contents are null
     * @throws PostOwnerException if author is the author of this post
     */
    public abstract void addComment(String author, String contents) throws PostOwnerException;

    /**
     * Serializes a Post through a JSON stream.
     * @param writer the given JSON stream
     * @throws IOException if some IO exception occurs
     */
    public abstract void toJson(JsonWriter writer) throws IOException;
}
