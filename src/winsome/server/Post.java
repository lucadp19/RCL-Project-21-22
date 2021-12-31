package winsome.server;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

/**
 * A Post in the Winsome Social Network.
 */
public class Post {
    public static enum Vote {
        UP, DOWN;
    }
    /** This post's unique identifier */
    public final int id;
    /** This post's author */
    public final String author;
    /** This post's title */
    public final String title;
    /** This post's contents */
    public final String contents;

    /** This post's rewinner, if any */
    public final Optional<String> rewinner;
    /** The rewinned post's ID, if any */
    public final Optional<Integer> rewinID;

    /** Users who have upvoted this post */
    private final Set<String> upvotes = ConcurrentHashMap.newKeySet();
    /** Users who have downvoted this post */
    private final Set<String> downvotes = ConcurrentHashMap.newKeySet();
    /** Users who have rated this post */
    private final ConcurrentMap<String, Vote> votes = new ConcurrentHashMap<>();
    /** The comments under this post */
    private final Collection<Comment> comments = new ConcurrentLinkedQueue<>();
    
    /** Number of upvotes at the last iteration of the Rewards Algorithm */
    private int oldUpvoteNumber = 0;
    /** Keeps track of how many times the Rewards Algorithm has been run on this post */
    private final AtomicInteger rewardsCounter;

    /** The generator for new IDs */
    private static AtomicInteger idGenerator = null;


    /**
     * Creates a new post.
     * @param author author of this post
     * @param title title of this post
     * @param contents contents of this post
     * @throws IllegalStateException if the ID Generator has not been initialized
     * @throws NullPointerException if either author, title, contents are null
     */
    public Post(String author, String title, String contents) throws IllegalStateException, NullPointerException {
        this(author, title, contents, Optional.empty(), Optional.empty());
    }

    /**
     * Creates a new post as a result of a rewin.
     * @param post post to rewin
     * @param rewinner user that performed the rewin
     * @throws IllegalStateException if ID Generator has not been initialized
     * @throws NullPointerException if either post or rewinner are null
     */
    public static Post rewinPost(Post post, String rewinner) throws IllegalStateException, NullPointerException {
        // if the given post is a rewin, the ID of the original post is in post.getRewinID()
        // otherwise it is post.id
        int origID = (post.isRewin()) ? post.getRewinID() : post.id;

        return new Post(post.author, post.title, post.contents, Optional.of(rewinner), Optional.of(origID));
    }

    /**
     * Creates a new post (but with lots of parameters).
     * @param author author of this post
     * @param title title of this post
     * @param contents contents of this post
     * @param rewinner user that created this rewin, if any
     * @param rewinID ID of the rewinned post, if any
     * @throws IllegalStateException if ID Generator has not been initialized
     * @throws NullPointerException if any of the parameters are null
     */
    private Post(String author, String title, String contents, Optional<String> rewinner, Optional<Integer> rewinID) 
            throws IllegalStateException, NullPointerException {
        if(!isIDGenInitialized()) throw new IllegalStateException("no starting value for IDs has been set");
        if(author == null || title == null || contents == null || rewinner == null || rewinID == null) 
            throw new NullPointerException("null parameters in Post creation");
        this.id = idGenerator.getAndIncrement();
        this.author = author;
        this.title = title;
        this.contents = contents;
        this.rewinner = rewinner;
        this.rewinID = rewinID;
        this.rewardsCounter = new AtomicInteger(1);
    }

    /**
     *  Creates a post, taking the ID, up/downvotes as arguments.
     * w in a
     * Useful to deserialize posts.
     * @param id id of this post
     * @param author author of this post
     * @param title title of this post
     * @param contents contents of this post
     * @param rewinner user that created this rewin, if any
     * @param rewinID ID of the rewinned post, if any
     * @param votes map with  users who have voted this post as keys, and votes as values
     * @param oldUpvoteNumber number of upvotes at the latest iteration of the Reward Algorithm
     * @param comments list of comments of the post
     * @param noIterations number of iterations at the latest iteration of the Reward Algorithm
     * @throws NullPointerException if any argument is null
     */
    private Post(
        int id, String author, String title, String contents, 
        Optional<String> rewinner, Optional<Integer> rewinID,
        Map<String, Vote> votes,
        int oldUpvoteNumber, List<Comment> comments,
        int noIterations
    ) throws NullPointerException {
        if(author == null || title == null || contents == null || rewinner == null
                || upvotes == null || downvotes == null || comments == null) 
            throw new NullPointerException("null parameters in Post creation");

        for(Entry<String, Vote> vote : votes.entrySet()){
            if(vote == null || vote.getKey() == null || vote.getValue() == null) 
                throw new NullPointerException("null parameters in Post creation");
            this.votes.put(vote.getKey(), vote.getValue());
        }
        for(Comment comment : comments){
            if(comment == null) throw new NullPointerException("null parameters in Post creation");
            this.comments.add(comment);
        }

        this.id = id;
        this.author = author;
        this.title = title;
        this.contents = contents;
        this.rewinner = rewinner;
        this.rewinID = rewinID;
        this.oldUpvoteNumber = oldUpvoteNumber;
        this.rewardsCounter = new AtomicInteger(noIterations);
    }

    /**
     * Checks whether the ID Generator has been initialized (through initIDGenerator).
     * @return true if and only if the ID Generator has been initialized
     */
    public static boolean isIDGenInitialized(){ return idGenerator != null; }


    /**
     * Initializes the ID Generator.
     * @param init the initial value of the ID Generator
     * @throws IllegalStateException if the ID Generator had already been initialized
     */
    public static void initIDGenerator(int init) throws IllegalStateException {
        if(isIDGenInitialized()) throw new IllegalStateException("starting value has already been set");
        idGenerator = new AtomicInteger(init);
    }

    /**
     * Checks whether or not this post is a rewin. 
     */
    public boolean isRewin(){ return rewinner.isPresent(); }

    /**
     * If this post is a rewin, returns the username of the rewinner.
     * 
     * It is recommended to check if this post is a rewin through {@link #isRewin()}. 
     * @return the username of the rewinner, if any
     * @throws NoSuchElementException if this post is not a rewin
     */
    public String getRewinner() throws NoSuchElementException { return rewinner.get(); }
    /**
     * If this post is a rewin, returns the ID of the rewinned post.
     * 
     * It is recommended to check if this post is a rewin through {@link #isRewin()}. 
     * @return the ID of the rewinned post, if any
     * @throws NoSuchElementException if this post is not a rewin
     */
    public int getRewinID() throws NoSuchElementException { return rewinID.get(); }


    /**
     * Returns the users who have upvoted this post.
     * @return a list of the users who have upvoted this post
     */
    public List<String> getUpvotes(){ 
        List<String> upvoters = new ArrayList<>();
        for(Entry<String, Vote> vote : votes.entrySet())
            if(vote.getValue() == Vote.UP) upvoters.add(vote.getKey());
        
        return upvoters; 
    }
    /**
     * Returns the users who have downvoted this post.
     * @return a list of the users who have downvoted this post
     */
    public List<String> getDownvotes(){ 
        List<String> downvoters = new ArrayList<>();
        for(Entry<String, Vote> vote : votes.entrySet())
            if(vote.getValue() == Vote.DOWN) downvoters.add(vote.getKey());
        
        return downvoters;
    }
    /**
     * Returns the comments under this post.
     * @return a list of the comments under this post
     */
    public List<Comment> getComments(){ return new ArrayList<>(comments); }
    
    /**
     * Returns the number of iterations of the Reward Algorithm (at the next iteration).
     * @return the number of the next iteration of the Reward Algorithm
     */
    public int getRewardsCounter(){ return rewardsCounter.get(); }

    /**
     * Adds an upvote this post.
     * @param voter the user who wants to upvote this post
     * @throws NullPointerException if voter is null
     * @throws IllegalArgumentException if voter had already rated this post
     */
    public void upvote(String voter) throws NullPointerException, IllegalArgumentException {
        if(voter == null) throw new NullPointerException("null parameter in upvoting post");

        if(votes.putIfAbsent(voter, Vote.UP) != null)
            throw new IllegalArgumentException("post had already been voted");
    }
    
    /**
     * Adds a downvote this post.
     * @param voter the user who wants to downvote this post
     * @throws NullPointerException if voter is null
     * @throws IllegalArgumentException if voter had already rated this post
     */
    public void downvote(String voter) throws NullPointerException, IllegalArgumentException {
        if(voter == null) throw new NullPointerException("null parameter in downvoting post");

        if(votes.putIfAbsent(voter, Vote.DOWN) != null)
            throw new IllegalArgumentException("post had already been voted");
    }

    /**
     * Adds a new comment to this post.
     * @param author author of the comment
     * @param contents contents of the comment
     * @throws NullPointerException if author or contents are null
     * @throws IllegalArgumentException if author is the author of this post
     */
    public void addComment(String author, String contents) throws NullPointerException, IllegalArgumentException {
        if(author == null || contents == null) throw new NullPointerException("null parameter in comment creation");
        if(author == this.author) throw new IllegalArgumentException("author cannot add a comment to their own post");
        comments.add(new Comment(author, contents));
    }

    // TODO: change the logic of all this last section

    /**
     * Returns the number of likes not yet counted by the Reward Algorithm.
     * @return the number of likes not yet counted by the Reward Algorithm
     */
    public int getNewLikes(){ return upvotes.size() - oldUpvoteNumber; }

    /** 
     * Increments the Reward Iteration Counter.
     */
    public void incrementRewardsCounter(){ rewardsCounter.incrementAndGet(); }

    /**
     * Returns a map that has
     * <ul>
     * <li> as keys, authors of comments under this post, </li>
     * <li> as values, the number of comments the user has written. </li>
     * </ul>
     * @return a map with comments frequencies
     */
    public Map<String, Integer> getCommentsPerAuthor(){
        Map<String, Integer> map = new HashMap<>();

        for(Comment comment : comments){
            String auth = comment.author;
            Integer val;

            if((val = map.get(auth)) == null){ 
                map.put(auth, 1);
            } else { map.put(auth, val+1); }
        }

        return map;
    }

    /** Sets upvotes and comments as read. */
    public synchronized void updateState(){
        oldUpvoteNumber = upvotes.size();
        for(Comment comment : comments) comment.setRead();
    }
}
