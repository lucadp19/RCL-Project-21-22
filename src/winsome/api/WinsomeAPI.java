package winsome.api;

import java.io.IOException;
import java.net.*;

import java.nio.channels.*;

import java.rmi.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.*;

import java.util.*;

public class WinsomeAPI extends RemoteObject implements RemoteClient {
    private final String serverAddr;
    private final int serverPort; 
    private final String remoteObjName = "WINSOME-SERVER";
    private final int registryPort;

    private SocketChannel socketChannel = null;
    private RemoteServer remoteServer = null;

    private RemoteClient remoteClient;
    
    private String loggedUser = null;
    private Map<Integer, Post> posts = null;
    private Map<String, List<String>> followers = null;
    private Map<String, List<String>> following = null;

    public WinsomeAPI(
        String serverAddr, 
        int serverPort,
        int registryPort
    ){
        super();

        this.serverAddr = serverAddr;
        this.serverPort = serverPort;
        this.registryPort = registryPort;
    }

    /* *************** Connection methods *************** */

    public void connect() throws IOException, RemoteException, NotBoundException {
        connectTCP(); connectRegistry();
    }

    private void connectTCP() throws IOException {
        if(socketChannel != null) throw new IllegalStateException("already connected to server");

        // opening channel in blocking mode, so there's no need to wait for the connection to be fully established
        socketChannel = SocketChannel.open(new InetSocketAddress(serverAddr, serverPort));
    }

    private void connectRegistry() throws RemoteException, NotBoundException {
        if(remoteServer != null) throw new IllegalStateException("already connected to server");

        Registry reg = LocateRegistry.getRegistry(registryPort);
        Remote remoteObj = reg.lookup(remoteObjName);
        remoteServer = (RemoteServer) remoteObj;

        remoteClient = (RemoteClient) UnicastRemoteObject.exportObject(this, 0);
    }

    /* *************** Callback methods *************** */

    public void addFollower(String user, List<String> tags) throws RemoteException {
        if(loggedUser == null) throw new IllegalStateException(); // TODO: make new exception
        if(user == null || tags == null) throw new NullPointerException("null parameters while adding new follower");
        for(String tag : tags) 
            if(tag == null) throw new NullPointerException("null parameters while adding new follower");
        
        if(followers.containsKey(user)) throw new IllegalArgumentException(); // TODO: make new exception

        followers.put(user, tags);
    }

    public void removeFollower(String user) throws RemoteException {
        if(loggedUser == null) throw new IllegalStateException(); // TODO: make new exception
        if(user == null) throw new NullPointerException("null parameter while removing follower");
        if(!followers.containsKey(user)) throw new IllegalArgumentException(); // TODO: make new exception

        followers.remove(user);
    }

    /* *************** Stubs for TCP Methods *************** */

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
