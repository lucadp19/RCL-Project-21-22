package winsome.server.datastructs;

import java.io.IOException;
import java.util.*;
import java.util.Map.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import winsome.api.exceptions.AlreadyVotedException;
import winsome.api.exceptions.PostOwnerException;
import winsome.api.exceptions.TextLengthException;
import winsome.server.PostRewards;
import winsome.server.exceptions.InvalidJSONFileException;

/**
 * An Original Post in the Winsome Social Network.
 */
public class OriginalPost extends Post {
    /** A Vote on a Post */
    public static enum Vote {
        /** Upvote */
        UP, 
        /** Downvote */
        DOWN;

        /** Whether or not this vote has been counted by the Rewards Algorithm */
        private AtomicBoolean visited;

        private Vote(){ visited = new AtomicBoolean(false); }
        private Vote(boolean visited){ this.visited = new AtomicBoolean(visited); }

        /**
         * Used by the Rewards Algorithm to mark this vote, if it hadn't already been marked.
         * @return true if and only if this vote hadn't already been marked
         */
        public boolean visit(){ return visited.compareAndSet(false, true); }

        /**
         * Deserializes a Vote from a JSON stream.
         * @param reader the given JSON stream
         * @return the parsed Vote
         * @throws IOException if some IO error occurs
         * @throws InvalidJSONFileException if the stream did not contain a valid Vote
         */
        public static Vote fromJson(JsonReader reader) throws IOException, InvalidJSONFileException {
            Objects.requireNonNull(reader, "json reader must not be null");
            try { 
                String vote = null; 
                Boolean visited = false;

                reader.beginObject();
                while(reader.hasNext()){
                    String property = reader.nextName();

                    switch (property) {
                        case "vote"     -> vote = reader.nextString();
                        case "visited"  -> visited = reader.nextBoolean();
                        default -> throw new InvalidJSONFileException("error while parsing vote in json file");
                    }
                }
                reader.endObject();

                Vote ans = Vote.valueOf(vote);
                if(visited) ans.visit(); // setting the vote as visited

                return ans;
            }
            catch(IllegalArgumentException | NullPointerException ex){ throw new InvalidJSONFileException("invalid vote"); }
        }

        /**
         * Serializes this vote through a JSON stream.
         * @param writer the given JSON stream
         * @throws IOException if some IO error occurs
         */
        public void toJson(JsonWriter writer) throws IOException {
            Objects.requireNonNull(writer, "json writer must not be null")
                .beginObject()
                .name("vote").value(this.toString())
                .name("visited").value(this.visited.get())
                .endObject();
        }
    }
    
    /** This post's unique identifier */
    public final int id;
    /** This post's author */
    public final String author;
    /** This post's title */
    public final String title;
    /** This post's contents */
    public final String contents;

    /** Users who have rated this post */
    private final ConcurrentMap<String, Vote> votes;
    /** The comments under this post */
    private final Collection<Comment> comments;
    /** Users who have rewinned this post */
    private final Set<String> rewinnerSet;
    
    /** Keeps track of how many times the Rewards Algorithm has been run on this post */
    private final AtomicInteger iterations;

    /**
     * Creates a new post.
     * @param author author of this post
     * @param title title of this post (maximum 50 characters)
     * @param contents contents of this post (maximum 500 characters)
     * @throws TextLengthException if title or contents exceed limits
     */
    public OriginalPost(String author, String title, String contents) throws TextLengthException {
        if(author == null || title == null || contents == null) 
            throw new NullPointerException("null parameters in Post creation");
        if(title.length() > 50 || contents.length() > 500) throw new TextLengthException("title or contents of post exceed limits");

        this.id = getNextID();
        this.author = author;
        this.title = title;
        this.contents = contents;
        this.votes = new ConcurrentHashMap<>();
        this.comments = new ConcurrentLinkedQueue<>();
        this.rewinnerSet = ConcurrentHashMap.newKeySet();
        this.iterations = new AtomicInteger(0);
    }

    /**
     *  Creates an original post, taking the ID, votes and comments as arguments.
     * <p>
     * This constructor expects votes and comments to be newly allocated 
     * objects, and as such it won't copy the structures.
     * It is useful to deserialize posts.
     * @param id id of this post
     * @param author author of this post
     * @param title title of this post
     * @param contents contents of this post
     * @param votes map with users who have voted this post as keys, and votes as values
     * @param comments queue of comments of the post
     * @param iterations number of iterations at the latest iteration of the Reward Algorithm
     */
    private OriginalPost(
        int id, String author, String title, String contents, 
        ConcurrentHashMap<String, Vote> votes,
        ConcurrentLinkedQueue<Comment> comments, int iterations
    ) throws NullPointerException {
        if(author == null || title == null || contents == null
                || votes == null || comments == null) 
            throw new NullPointerException("null parameters in Post creation");

        this.id = id;
        this.author = author;
        this.title = title;
        this.contents = contents;
        this.votes = votes;
        this.comments = comments;
        this.rewinnerSet = ConcurrentHashMap.newKeySet();
        this.iterations = new AtomicInteger(iterations);
    }

    @Override
    public int getID() { return id; }

    @Override
    public String getAuthor() { return author; }

    @Override
    public String getTitle() { return title; }

    @Override
    public String getContents() { return contents; }

    /**
     * Returns whether or not this post is a rewin. 
     * <p>
     * If the post is an OriginalPost, this method always returns false.
     * @return false
     */
    @Override
    public boolean isRewin(){ return false; }

    /**
     * Returns the username of the rewinner if this post is a rewin.
     * <p>
     * Since this post is an OriginalPost, this method always throws NoSuchElementException. 
     * @return the username of the rewinner, if any
     * @throws NoSuchElementException if this post is not a rewin
     */
    @Override
    public String getRewinner() throws NoSuchElementException { throw new NoSuchElementException("this post is not a rewin"); }

    /**
     * Returns the ID of the original post this post is based on.
     * <p>
     * Since this post is an OriginalPost, this method always returns this post's ID.
     * @return the ID of this post
     */
    @Override
    public int getOriginalID() { return id; }
    
    /**
     * Returns the original post this post is based on.
     * <p>
     * Since this post is an OriginalPost, this method always returns this.
     * @return this post
     */
    @Override
    public Post getOriginalPost() { return this; }    

    @Override
    public boolean addRewinner(String username){
        if(username == null) throw new NullPointerException("null username");
        return rewinnerSet.add(username);
    }

    @Override
    public boolean hasRewinned(String username){
        if(username == null) throw new NullPointerException("null username");
        return rewinnerSet.contains(username);
    }

    @Override
    public List<String> getUpvoters(){ 
        List<String> upvoters = new ArrayList<>();
        for(Entry<String, Vote> vote : votes.entrySet())
            if(vote.getValue() == Vote.UP) upvoters.add(vote.getKey());
        
        return upvoters; 
    }
    
    @Override
    public List<String> getDownvoters(){ 
        List<String> downvoters = new ArrayList<>();
        for(Entry<String, Vote> vote : votes.entrySet())
            if(vote.getValue() == Vote.DOWN) downvoters.add(vote.getKey());
        
        return downvoters;
    }

    @Override
    public List<Comment> getComments(){ return new ArrayList<>(comments); }
    
   
    @Override
    public void upvote(String voter) throws NullPointerException, AlreadyVotedException {
        if(voter == null) throw new NullPointerException("null parameter in upvoting post");

        if(votes.putIfAbsent(voter, Vote.UP) != null)
            throw new AlreadyVotedException("post had already been voted");
    }
    
    @Override
    public void downvote(String voter) throws NullPointerException, AlreadyVotedException {
        if(voter == null) throw new NullPointerException("null parameter in downvoting post");

        if(votes.putIfAbsent(voter, Vote.DOWN) != null)
            throw new AlreadyVotedException("post had already been voted");
    }

    @Override
    public void addComment(String author, String contents) throws NullPointerException, PostOwnerException {
        if(author == null || contents == null) throw new NullPointerException("null parameter in comment creation");
        if(author.equals(this.author)) throw new PostOwnerException("author cannot add a comment to their own post");
        comments.add(new Comment(author, contents));
    }

    // ------------------- To/From JSON ------------------- //

    @Override
    public void toJson(JsonWriter writer) throws IOException {
        Objects.requireNonNull(writer, "json writer must not be null")
            .beginObject()
            .name("id").value(this.id)
            .name("author").value(this.author)
            .name("title").value(this.title)
            .name("contents").value(this.contents);


        // serializing votes
        writer.name("votes")
            .beginArray();
        for(Entry<String, Vote> vote : votes.entrySet()){
            writer.beginObject();

            writer.name("voter").value(vote.getKey())
                .name("vote");
            vote.getValue()
                .toJson(writer);

            writer.endObject();
        }
        writer.endArray();

        // serializing comments
        writer.name("comments")
            .beginArray();
        for(Comment comment : comments) comment.toJson(writer);
        writer.endArray();

        // serializing iterations
        writer.name("iterations")
            .value(iterations.get());

        writer.endObject();
    }

    /**
     * Deserializes an OriginalPost from a JSON stream.
     * @param reader the given JSON stream
     * @return the deserialized OriginalPost
     * @throws InvalidJSONFileException if the given stream did not contain a valid OriginalPost
     * @throws IOException if some IO error occurs
     */
    public static OriginalPost fromJson(JsonReader reader) throws InvalidJSONFileException, IOException {
        Objects.requireNonNull(reader, "json reader must not be null");

        try {
            Integer id      = null;
            String author   = null;
            String title    = null;
            String contents = null;
            
            ConcurrentHashMap<String, Vote> votes   = new ConcurrentHashMap<>();
            ConcurrentLinkedQueue<Comment> comments = new ConcurrentLinkedQueue<>();
            
            Integer iterations = null;

            reader.beginObject();
            while(reader.hasNext()){
                String property = reader.nextName();

                switch (property) {
                    case "id"       -> id = reader.nextInt();
                    case "author"   -> author = reader.nextString();
                    case "title"    -> title = reader.nextString();
                    case "contents" -> contents = reader.nextString();
                    case "votes" -> {
                        // opening array of votes
                        reader.beginArray();
                        while(reader.hasNext()){
                            String voter = null;
                            Vote vote = null;
                            
                            // opening single vote
                            reader.beginObject();
                            while(reader.hasNext()){
                                String innerProperty = reader.nextName();

                                switch (innerProperty) {
                                    case "voter" -> voter = reader.nextString();
                                    case "vote" -> vote = Vote.fromJson(reader);
                                    default -> throw new InvalidJSONFileException("parameter does not represent a valid OriginalPost");
                                }
                            }
                            reader.endObject();
                            // closing single vote

                            votes.put(voter, vote);
                        }
                        reader.endArray();
                        // closing array of votes
                    }
                    case "comments" -> {
                        reader.beginArray();
                        while(reader.hasNext())
                            comments.add(Comment.fromJson(reader)); // reading comment
                        reader.endArray();
                    }
                    case "iterations" -> iterations = reader.nextInt();
                    default -> throw new InvalidJSONFileException("json reader does not represent a valid Post");
                }
            }
            reader.endObject();
            
            return new OriginalPost(id, author, title, contents, votes, comments, iterations);
        } catch (ClassCastException | IllegalStateException | NullPointerException | NumberFormatException ex){
            throw new InvalidJSONFileException("json reader does not represent a valid Post", ex);
        }
    }

    /**
     * Computes the rewards generated by this post and updates its status.
     * @return the rewards generated by this post
     */
    public PostRewards reapRewards(){
        Set<String> curators = new HashSet<>();

        // getting upvoters
        int newVotes = 0;
        for(Entry<String, Vote> entry : votes.entrySet()){
            Vote vote = entry.getValue();
            if(vote.visit()){
                newVotes += (vote == Vote.UP) ? 1 : -1;
                if(vote == Vote.UP) curators.add(entry.getKey());
            }
        }

        // getting commenters
        Map<String, Integer> commentFreq = new HashMap<>();
        for(Comment comment : comments){
            if(comment.visit()){
                commentFreq.merge(
                    comment.author, 
                    1, 
                    (oldFreq, incr) -> oldFreq + incr
                );
                curators.add(comment.author);
            }
        }

        // computing reward
        double reward = Math.log(Math.max(newVotes, 0) + 1);
        double commentReward = 0;
        for(int freq : commentFreq.values())
            commentReward += 2/(1 + Math.exp(-(freq + 1)));
        reward += Math.log(commentReward + 1);
        reward /= iterations.incrementAndGet();

        return new PostRewards(reward, curators);
    }
}
