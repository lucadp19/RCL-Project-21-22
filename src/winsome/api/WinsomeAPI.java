package winsome.api;

import java.io.*;
import java.net.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.rmi.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.*;

import java.util.*;

import winsome.api.exceptions.NotImplementedException;

public class WinsomeAPI extends RemoteObject implements RemoteClient {
    private final String serverAddr;
    private final int serverPort; 
    private final String registryAddr;
    private final int registryPort;

    private Socket socket = null;
    // private SocketChannel socketChannel = null;
    private RemoteServer remoteServer = null;

    private RemoteClient remoteClient;
    
    private String loggedUser = null;
    private Map<Integer, PostInfo> posts = null;
    private Map<String, List<String>> followers = null;
    private Map<String, List<String>> following = null;

    public WinsomeAPI(
        String serverAddr, 
        int serverPort,
        String registryAddr,
        int registryPort
    ){
        super();

        this.serverAddr = serverAddr;
        this.serverPort = serverPort;
        this.registryAddr = registryAddr;
        this.registryPort = registryPort;
    }

    /* *************** Connection methods *************** */

    public void connect() throws IOException, RemoteException, NotBoundException {
        connectTCP(); connectRegistry();
    }

    private void connectTCP() throws IOException {
        if(socket != null) throw new IllegalStateException("already connected to server");

        socket = new Socket(serverAddr, serverPort);
        // opening channel in blocking mode, so there's no need to wait for the connection to be fully established
        // socketChannel = SocketChannel.open(new InetSocketAddress(serverAddr, serverPort));
        // System.out.println("Connected to socket!");
    }

    private void connectRegistry() throws RemoteException, NotBoundException {
        if(remoteServer != null) throw new IllegalStateException("already connected to server");

        Registry reg = LocateRegistry.getRegistry(registryPort);
        Remote remoteObj = reg.lookup(registryAddr);
        remoteServer = (RemoteServer) remoteObj;

        remoteClient = (RemoteClient) UnicastRemoteObject.exportObject(this, 0);
        // System.out.println("Connected to registry!");
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

    /* *************** Test command: echo *************** */
    public String echoMsg(String msg) throws IOException {
        send(msg);
        return receive();
    }

    /* *************** Stubs for TCP Methods *************** */

    public void register(String user, String passw, Set<String> tags) throws NotImplementedException {
        throw new NotImplementedException("method not yet implemented");
    }

    public void login(String user, String passw) throws NotImplementedException {
        throw new NotImplementedException("method not yet implemented");
    }

    public void logout(String user) throws NotImplementedException {
        throw new NotImplementedException("method not yet implemented");
    }

    public void listUsers() throws NotImplementedException {
        throw new NotImplementedException("method not yet implemented");
    }

    public void listFollowers() throws NotImplementedException {
        throw new NotImplementedException("method not yet implemented");
    }

    public void listFollowing() throws NotImplementedException {
        throw new NotImplementedException("method not yet implemented");
    }

    public void followUser(String user) throws NotImplementedException {
        throw new NotImplementedException("method not yet implemented");
    }

    public void unfollowUser(String user) throws NotImplementedException {
        throw new NotImplementedException("method not yet implemented");
    }

    public void viewBlog() throws NotImplementedException {
        throw new NotImplementedException("method not yet implemented");
    }

    public void createPost(String title, String content) throws NotImplementedException {
        throw new NotImplementedException("method not yet implemented");
    }

    public void showFeed() throws NotImplementedException {
        throw new NotImplementedException("method not yet implemented");
    }

    public void showPost(int idPost) throws NotImplementedException {
        throw new NotImplementedException("method not yet implemented");
    }

    public void deletePost(int idPost) throws NotImplementedException {
        throw new NotImplementedException("method not yet implemented");
    }

    public void rewinPost(int idPost) throws NotImplementedException {
        throw new NotImplementedException("method not yet implemented");
    }

    public void ratePost(int idPost, int vote) throws NotImplementedException {
        throw new NotImplementedException("method not yet implemented");
    }

    public void addComment(int idPost, String comment) throws NotImplementedException {
        throw new NotImplementedException("method not yet implemented");
    }

    public void getWallet() throws NotImplementedException {
        throw new NotImplementedException("method not yet implemented");
    }

    public void getWalletInBitcoin() throws NotImplementedException {
        throw new NotImplementedException("method not yet implemented");
    }

    /* *************** Send/receive data *************** */
    private void send(String msg) throws NullPointerException, IOException {
        if(msg == null) throw new NullPointerException("attempting to send an empty message");

        DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));

        byte[] tmp = msg.getBytes(StandardCharsets.UTF_8);
        out.writeInt(tmp.length);
        out.write(tmp);
        out.flush();
    }

    private String receive() throws IOException {
        DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));

        int len = in.readInt();
        if(len <= 0) throw new IOException("received message length less or equal to 0");

        byte[] buf = new byte[len];
        in.readFully(buf);

        return new String(buf, StandardCharsets.UTF_8);
    }  
}
