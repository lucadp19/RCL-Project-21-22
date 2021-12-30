package winsome.server;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

public class Post {
    private int id;
    private String author;
    private String title;
    private String contents;

    private Optional<String> rewinner;
    private Optional<Integer> rewinID;

    private int oldUpvoteNumber = 0;
    private final Set<String> upvotes = new HashSet<>();
    private final Set<String> downvotes = new HashSet<>();

    private final List<Comment> comments = new ArrayList<>();

    private final AtomicInteger rewardsCounter = new AtomicInteger(1);

    private static boolean isGeneratorSet = false;
    private static AtomicInteger idGenerator;

    public Post(String author, String title, String contents) throws IllegalStateException, NullPointerException {
        this(author, title, contents, Optional.empty(), Optional.empty());
    }

    public Post(Post post, String rewinner) throws IllegalStateException, NullPointerException {
        this(post.getAuthor(), post.getTitle(), post.getContents(), Optional.of(rewinner), Optional.of(post.getID()));
    }

    public Post(String author, String title, String contents, Optional<String> rewinner, Optional<Integer> rewinID) 
            throws IllegalStateException, NullPointerException {
        if(!isGeneratorSet) throw new IllegalStateException("no starting value for IDs has been set");
        if(author == null || title == null || contents == null) 
            throw new NullPointerException("null parameters in Post creation");
        this.id = idGenerator.getAndIncrement();
        this.author = author;
        this.title = title;
        this.contents = contents;
        this.rewinner = rewinner;
        this.rewinID = rewinID;
    }

    public static void initIDGenerator(int init) throws IllegalStateException {
        if(isGeneratorSet) throw new IllegalStateException("starting value has already been set");
        isGeneratorSet = true;
        idGenerator = new AtomicInteger(init);
    }

    public int getID(){ return id; }
    public String getAuthor(){ return author; }
    public String getTitle(){ return title; }
    public String getContents(){ return contents; }
    public Optional<String> getRewinner(){ return rewinner; }
    public Optional<Integer> getRewinID(){ return rewinID; }

    public boolean isRewin(){ return rewinner.isPresent(); }

    public synchronized List<String> getUpvotes(){ return new ArrayList<>(upvotes); }
    public synchronized List<String> getDownvotes(){ return new ArrayList<>(downvotes); }
    public synchronized List<Comment> getComments(){ return new ArrayList<>(comments); }
    public synchronized int getRewardsCounter(){ return rewardsCounter.get(); }

    public synchronized void upvote(String voter) throws NullPointerException, IllegalArgumentException {
        if(voter == null) throw new NullPointerException("null parameter in upvoting post");
        if(!upvotes.add(voter)) 
            throw new IllegalArgumentException("post has already been upvoted");
    }

   public synchronized void downvote(String voter) throws NullPointerException, IllegalArgumentException {
        if(voter == null) throw new NullPointerException("null parameter in downvoting post");
        if(!downvotes.add(voter)) 
            throw new IllegalArgumentException("post has already been downvoted");
    }

    public synchronized void addComment(String author, String contents) throws NullPointerException, IllegalArgumentException {
        if(author == null || contents == null) throw new NullPointerException("null parameter in comment creation");
        if(author == this.author) throw new IllegalArgumentException("author cannot add a comment to their own post");
        comments.add(new Comment(author, contents));
    }

    public synchronized int getNewLikes(){ return upvotes.size() - oldUpvoteNumber; }

    public synchronized void updateRewardsCounter(){ rewardsCounter.incrementAndGet(); }

    public synchronized Map<String, AtomicInteger> getUnreadComments(){
        Map<String, AtomicInteger> map = new HashMap<>();

        for(Comment comment : comments){
            if(comment.isRead()) continue;

            String auth = comment.author;
            AtomicInteger val;

            if((val = map.get(auth)) == null){ 
                map.put(auth, new AtomicInteger(1));
            } else { val.incrementAndGet(); }
        }

        return map;
    }

    public synchronized void updateState(){
        oldUpvoteNumber = upvotes.size();
        for(Comment comment : comments) comment.setRead();
    }
}
