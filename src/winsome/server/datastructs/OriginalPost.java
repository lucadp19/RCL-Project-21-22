package winsome.server.datastructs;

import java.io.IOException;
import java.util.*;
import java.util.Map.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import winsome.api.exceptions.AlreadyVotedException;
import winsome.api.exceptions.PostOwnerException;
import winsome.server.PostRewards;
import winsome.server.exceptions.InvalidJSONFileException;

/**
 * An Original Post in the Winsome Social Network.
 */
public class OriginalPost extends Post {
    public static enum Vote {
        UP, DOWN;

        private AtomicBoolean visited;
        private Vote(){ visited = new AtomicBoolean(false); }
        private Vote(boolean visited){ this.visited = new AtomicBoolean(visited); }

        public boolean visit(){ return visited.compareAndSet(false, true); }

        public static Vote fromJson(JsonReader reader) throws IOException, InvalidJSONFileException {
            if(reader == null) throw new NullPointerException("null argument");
            try { 
                String vote = null; 
                Boolean visited = false;

                reader.beginObject();
                while(reader.hasNext()){
                    String property = reader.nextName();

                    switch (property) {
                        case "vote":
                            vote = reader.nextString();
                            break;
                        case "visited":
                            visited = reader.nextBoolean();
                            break;
                        default:
                            throw new InvalidJSONFileException("error while parsing vote in json file");
                    }
                }
                reader.endObject();

                Vote ans = Vote.valueOf(vote);
                if(visited) ans.visit();

                return ans;
            }
            catch(IllegalArgumentException | NullPointerException ex){ throw new InvalidJSONFileException("invalid vote"); }
        }

        public void toJson(JsonWriter writer) throws IOException {
            if(writer == null) throw new NullPointerException("null writer");

            writer.beginObject()
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
    
    /** Number of upvotes at the last iteration of the Rewards Algorithm */
    private int oldUpvoteNumber = 0;
    /** Keeps track of how many times the Rewards Algorithm has been run on this post */
    private final AtomicInteger rewardsCounter;

    /**
     * Creates a new post.
     * @param author author of this post
     * @param title title of this post
     * @param contents contents of this post
     * @throws IllegalStateException if the ID Generator has not been initialized
     * @throws NullPointerException if either author, title, contents are null
     */
    public OriginalPost(String author, String title, String contents) throws IllegalStateException, NullPointerException {
        if(author == null || title == null || contents == null) 
            throw new NullPointerException("null parameters in Post creation");
        this.id = getNextID();
        this.author = author;
        this.title = title;
        this.contents = contents;
        this.votes = new ConcurrentHashMap<>();
        this.comments = new ConcurrentLinkedQueue<>();
        this.rewinnerSet = ConcurrentHashMap.newKeySet();
        this.rewardsCounter = new AtomicInteger(1);
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
     * @param oldUpvoteNumber number of upvotes at the latest iteration of the Reward Algorithm
     * @param comments queue of comments of the post
     * @param noIterations number of iterations at the latest iteration of the Reward Algorithm
     * @throws NullPointerException if any argument is null
     */
    private OriginalPost(
        int id, String author, String title, String contents, 
        ConcurrentHashMap<String, Vote> votes,
        int oldUpvoteNumber, ConcurrentLinkedQueue<Comment> comments,
        int noIterations
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
        this.oldUpvoteNumber = oldUpvoteNumber;
        this.rewinnerSet = ConcurrentHashMap.newKeySet();
        this.rewardsCounter = new AtomicInteger(noIterations);
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

    // @Override
    // public JsonObject toJson(){
    //     JsonObject json = new JsonObject();
    //     // adding standard properties
    //     json.addProperty("id", id);
    //     json.addProperty("author", author);
    //     json.addProperty("title", title);
    //     json.addProperty("contents", contents);

    //     // adding votes
    //     JsonArray jsonVotes = new JsonArray();
    //     for(Entry<String, Vote> vote : votes.entrySet()){
    //         JsonObject jsonVote = new JsonObject();

    //         jsonVote.addProperty("voter", vote.getKey());
    //         jsonVote.addProperty("vote", vote.getValue().toString());

    //         jsonVotes.add(jsonVote);
    //     }
    //     json.add("votes", jsonVotes);

    //     // adding comments
    //     JsonArray jsonComments = new JsonArray();
    //     for(Comment comment : comments){ jsonComments.add(comment.toJson()); }
    //     json.add("comments", jsonComments);

    //     // adding counters
    //     json.addProperty("oldUpvoteNumber", oldUpvoteNumber);
    //     json.addProperty("rewardsCounter", rewardsCounter);

    //     return json;
    // }

    @Override
    public void toJson(JsonWriter writer) throws IOException {
        if(writer == null) throw new NullPointerException();

        writer.beginObject();

        writer.name("id").value(this.id);
        writer.name("author").value(this.author);
        writer.name("title").value(this.title);
        writer.name("contents").value(this.contents);

        writer.name("votes").beginArray();
        for(Entry<String, Vote> vote : votes.entrySet()){
            writer.beginObject()
                  .name("voter").value(vote.getKey())
                  .name("vote");
            vote.getValue().toJson(writer);
            writer.endObject();
        }
        writer.endArray();

        writer.name("comments").beginArray();
        for(Comment comment : comments)
            comment.toJson(writer);
        writer.endArray();

        writer.name("oldUpvoteNumber").value(oldUpvoteNumber);
        writer.name("rewardsCounter").value(rewardsCounter);

        writer.endObject();
    }

    // public static OriginalPost fromJson(JsonObject json) throws IllegalArgumentException {
    //     if(json == null) throw new NullPointerException("null parameters");

    //     try {
    //         int id = json.get("id").getAsInt();
    //         String author = json.get("author").getAsString();
    //         String title = json.get("title").getAsString();
    //         String contents = json.get("contents").getAsString();

    //         JsonArray jsonVotes = json.get("votes").getAsJsonArray();
    //         Iterator<JsonElement> iterVotes = jsonVotes.iterator();
    //         ConcurrentHashMap<String, Vote> votes = new ConcurrentHashMap<>();
    //         while(iterVotes.hasNext()){
    //             JsonObject jsonVote = iterVotes.next().getAsJsonObject();
    //             votes.put(
    //                 jsonVote.get("voter").getAsString(), 
    //                 Vote.valueOf(jsonVote.get("vote").getAsString())
    //             );
    //         }

    //         JsonArray jsonComments = json.get("comments").getAsJsonArray();
    //         Iterator<JsonElement> iterComments = jsonComments.iterator();
    //         ConcurrentLinkedQueue<Comment> comments = new ConcurrentLinkedQueue<>();
    //         while(iterComments.hasNext()){
    //             JsonObject jsonComment = iterComments.next().getAsJsonObject();
    //             comments.add(Comment.fromJson(jsonComment));
    //         }

    //         int oldUpvoteNumber = json.get("oldUpvoteNumber").getAsInt();
    //         int noIterations = json.get("rewardsCounter").getAsInt();

    //         return new OriginalPost(id, author, title, contents, votes, oldUpvoteNumber, comments, noIterations);
    //     } catch (ClassCastException | IllegalStateException | NullPointerException | IllegalArgumentException ex){
    //         throw new IllegalArgumentException("parameter does not represent a valid OriginalPost", ex);
    //     }
    // }

    public static OriginalPost fromJson(JsonReader reader) throws InvalidJSONFileException, IOException {
        if(reader == null) throw new NullPointerException("null parameters");

        try {
            Integer id      = null;
            String author   = null;
            String title    = null;
            String contents = null;
            
            ConcurrentHashMap<String, Vote> votes   = new ConcurrentHashMap<>();
            ConcurrentLinkedQueue<Comment> comments = new ConcurrentLinkedQueue<>();
            
            Integer oldUpvoteNumber = null;
            Integer noIterations    = null;

            reader.beginObject();
            while(reader.hasNext()){
                String property = reader.nextName();

                switch (property) {
                    case "id":
                        id = reader.nextInt();
                        break;
                    case "author":
                        author = reader.nextString();
                        break;
                    case "title":
                        title = reader.nextString();
                        break;
                    case "contents":
                        contents = reader.nextString();
                        break;
                    case "votes": {
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
                                    case "voter":
                                        voter = reader.nextString();
                                        break;
                                    case "vote":
                                        vote = Vote.fromJson(reader);
                                        break;
                                    default:
                                        throw new InvalidJSONFileException("parameter does not represent a valid OriginalPost");
                                }
                            }
                            reader.endObject();
                            // closing single vote

                            votes.put(voter, vote);
                        }

                        reader.endArray();
                        // closing array of votes
                        break;
                    }
                    case "comments": {
                        reader.beginArray();
                        while(reader.hasNext()){
                            // reading comment
                            comments.add(Comment.fromJson(reader));
                        }
                        reader.endArray();
                        break;
                    }
                    case "oldUpvoteNumber":
                        oldUpvoteNumber = reader.nextInt();
                        break;
                    case "rewardsCounter":
                        noIterations = reader.nextInt();
                        break;
                    default:
                        throw new InvalidJSONFileException("json reader does not represent a valid Post");
                }
            }
            reader.endObject();
            
            return new OriginalPost(id, author, title, contents, votes, oldUpvoteNumber, comments, noIterations);
        } catch (ClassCastException | IllegalStateException | NullPointerException | NumberFormatException ex){
            throw new InvalidJSONFileException("json reader does not represent a valid Post", ex);
        }
    }


    // TODO: change the logic of all this last section

    public PostRewards reapRewards(){
        Set<String> curators = new HashSet<>();

        int newVotes = 0;
        for(Entry<String, Vote> entry : votes.entrySet()){
            Vote vote = entry.getValue();
            if(vote.visit()){
                newVotes += (vote == Vote.UP) ? 1 : -1;
                if(vote == Vote.UP) curators.add(entry.getKey());
            }
        }

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

        double reward = Math.log(Math.max(newVotes, 0) + 1);
        double commentReward = 0;
        for(int freq : commentFreq.values())
            commentReward += 2/(1 + Math.exp(-(freq + 1)));
        reward += Math.log(commentReward + 1);

        return new PostRewards(reward, curators);
    }
}
