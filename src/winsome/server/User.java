package winsome.server;

import java.util.*;

public class User {
    private final String username;
    private final String password;
    private final List<String> tags;

    public User(String username, String password, List<String> tags){
        if(username == null || password == null || tags == null)
            throw new NullPointerException("null arguments in User constructor");
        if(tags.size() < 1 || tags.size() > 5)
            throw new IllegalArgumentException("a User must have at least one tag and at most 5");
        for(String tag : tags)
            if(tag == null)
                throw new NullPointerException("null arguments in User constructor");
        
        this.username = username;
        this.password = password;
        this.tags = new ArrayList<>(tags);
    }

    public String getUsername(){ return username; }
    public String getPassword(){ return password; }
    public List<String> getTags(){ return new ArrayList<>(tags); }
}
