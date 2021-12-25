package winsome.api;

import java.io.Console;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import winsome.utils.ConsoleColors;

public class Post {
    public class Comment {
        private String author;
        private String contents;
        private boolean read = false;

        public Comment(String author, String contents) throws NullPointerException {
            if(author == null || contents == null) throw new NullPointerException("null parameter in comment creation");
            this.author = author;
            this.contents = contents;
        }

        public String getAuthor(){ return author; }
        public String getContents(){ return contents; }
        public boolean isRead(){ return read; }
        public void setRead(){ read = true; }

        public String prettify(){ return this.author + ": \"" + this.contents + "\""; }
    }

    private int id;
    private String author;
    private String title;
    private String contents;

    private Optional<String> rewinner;

    private int oldUpvoteNumber;
    private Set<String> upvotes;
    private Set<String> downvotes;

    private List<Comment> comments;

    private static boolean isGeneratorSet = false;
    private static AtomicInteger idGenerator;

    public Post(String author, String title, String contents) throws IllegalStateException {
        initPost(author, title, contents, Optional.empty());
    }

    public Post(Post post, String rewinner){
        if(post == null || rewinner == null) throw new NullPointerException("null parameters in post rewin");
        initPost(post.getAuthor(), post.getTitle(), post.getContents(), Optional.of(rewinner));
    }

    public static void initIDGenerator(int init) throws IllegalStateException {
        if(isGeneratorSet) throw new IllegalStateException("starting value has already been set");
        isGeneratorSet = true;
        idGenerator = new AtomicInteger(init);
    }

    private void initPost(String author, String title, String contents, Optional<String> rewinner) 
            throws IllegalStateException, NullPointerException {
        if(!isGeneratorSet) throw new IllegalStateException("no starting value for IDs has been set");
        if(author == null || title == null || contents == null) 
            throw new NullPointerException("null parameters in Post creation");
        this.id = idGenerator.getAndIncrement();
        this.author = author;
        this.title = title;
        this.contents = contents;
        this.rewinner = rewinner;

        this.oldUpvoteNumber = 0;
        this.upvotes = new HashSet<>();
        this.downvotes = new HashSet<>();
    }

    public String getAuthor(){ return author; }
    public String getTitle(){ return title; }
    public String getContents(){ return contents; }
    public Optional<String> getRewinner(){ return rewinner; }
    public List<String> getUpvotes(){ return new ArrayList<>(upvotes); }
    public List<String> getDownvotes(){ return new ArrayList<>(downvotes); }
    public List<Comment> getComments(){ return new ArrayList<>(comments); }

    public void upvote(String voter) throws NullPointerException, IllegalArgumentException {
        if(voter == null) throw new NullPointerException("null parameter in upvoting post");
        if(!upvotes.add(voter)) 
            throw new IllegalArgumentException("post has already been upvoted");
    }

   public void downvote(String voter) throws NullPointerException, IllegalArgumentException {
        if(voter == null) throw new NullPointerException("null parameter in downvoting post");
        if(!downvotes.add(voter)) 
            throw new IllegalArgumentException("post has already been downvoted");
    }

    public void addComment(String author, String contents) throws NullPointerException, IllegalArgumentException {
        if(author == null || contents == null) throw new NullPointerException("null parameter in comment creation");
        if(author == this.author) throw new IllegalArgumentException("author cannot add a comment to their own post");
        comments.add(new Comment(author, contents));
    }

    public int getNewLikes(){ return upvotes.size() - oldUpvoteNumber; }

    public Map<String, AtomicInteger> getUnreadComments(){
        Map<String, AtomicInteger> map = new HashMap<>();

        for(Comment comment : comments){
            if(comment.isRead()) continue;

            String auth = comment.getAuthor();
            AtomicInteger val;

            if((val = map.get(auth)) == null){ 
                map.put(auth, new AtomicInteger(1));
            } else { val.incrementAndGet(); }
        }

        return map;
    }

    public void updateState(){
        oldUpvoteNumber = upvotes.size();
        for(Comment comment : comments) comment.setRead();
    }

    public String prettify(){
        String str = 
            ConsoleColors.GREEN_BOLD + "Title: " + ConsoleColors.RESET + this.title + "\n"
            + ConsoleColors.GREEN_BOLD + "Contents: " + ConsoleColors.RESET + this.contents + "\n"
            + ConsoleColors.GREEN_BOLD + "Votes: " + ConsoleColors.RESET + 
                this.upvotes.size() + " upvotes, " + this.downvotes.size() + " downvotes\n" 
            + ConsoleColors.GREEN_BOLD + "Comments: ";
        if(comments.isEmpty()) str += "0\n";
        else { 
            for(Comment comment : comments) str += "    " + comment.prettify() + "\n";
        }

        return str;
    }
}
