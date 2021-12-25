package winsome.api;

import java.util.*;

public class WinsomeAPI {
    private final String serverAddr;
    private final int serverPort; 
    
    private String currentUser;

    public WinsomeAPI(
        String serverAddr, 
        int serverPort
    ){
        this.serverAddr = serverAddr;
        this.serverPort = serverPort;
    }

    /*
        Stubs for methods
    */

    public void register(String user, String passw, Set<String> tags){}

    public void login(String user, String passw){}

    public void logout(String user){}

    public void listUsers(){}

    public void listFollowers(){}

    public void listFollowing(){}

    public void followUser(String user){}

    public void unfollowUser(String user){}

    public void viewBlog(){}

    public void createPost(String title, String content){}

    public void showFeed(){}

    public void showPost(int idPost){}

    public void deletePost(int idPost){}

    public void rewinPost(int idPost){}

    public void ratePost(int idPost, int vote){}

    public void addComment(int idPost, String comment){}

    public void getWallet(){}

    public void getWalletInBitcoin(){}
}
