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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import winsome.api.codes.RequestCode;
import winsome.api.codes.ResponseCode;
import winsome.api.exceptions.MalformedJSONException;
import winsome.api.exceptions.NoLoggedUserException;
import winsome.api.exceptions.NoSuchUserException;
import winsome.api.exceptions.NotImplementedException;
import winsome.api.exceptions.UserAlreadyExistsException;
import winsome.api.exceptions.UserAlreadyLoggedException;
import winsome.api.exceptions.WrongPasswordException;
import winsome.api.exceptions.WrongUserException;
import winsome.server.datastructs.User;

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

    public void register(String username, String password, Set<String> tags) 
            throws NullPointerException, IllegalStateException, UserAlreadyExistsException, RemoteException {
        if(username == null || password == null || tags == null) throw new NullPointerException("null arguments to register");
        for(String tag : tags)
            if(tag == null) throw new NullPointerException("null tag in register");
        
        if(isLogged()) throw new IllegalStateException("a user is already logged; please log out before trying to sign up");

        remoteServer.signUp(username, password, tags);
    }

    public void login(String user, String passw) 
            throws IOException, MalformedJSONException, UserAlreadyLoggedException, 
                NoSuchUserException, WrongPasswordException {
        if(isLogged()) throw new UserAlreadyLoggedException(
            "already logged as " + loggedUser + ". Please logout before trying to login with another user.");

        JsonObject request = new JsonObject();

        RequestCode.LOGIN.addRequestToJson(request);
        request.addProperty("username", user);
        request.addProperty("password", passw);

        send(request.toString());

        JsonObject response = getJsonResponse();
        ResponseCode responseCode = ResponseCode.getResponseFromJson(response);
        switch (responseCode) {
            case SUCCESS:
                loggedUser = user;
                remoteServer.registerForUpdates(user, this);
                return;
            case USER_NOT_REGISTERED:
                throw new NoSuchUserException("\"" + user + "\" is not signed up");
            case WRONG_PASSW:
                throw new WrongPasswordException("password does not match");
            case ALREADY_LOGGED:
                throw new UserAlreadyLoggedException("user is already logged in");
            default:
                throw new IllegalStateException(responseCode.toString());
        }
    }

    public void logout() 
            throws IOException, IllegalStateException, MalformedJSONException, NoLoggedUserException {
        if(!isLogged()) throw new NoLoggedUserException("no user is currently logged; please log in first.");

        JsonObject request = new JsonObject();
        RequestCode.LOGOUT.addRequestToJson(request);
        request.addProperty("username", loggedUser);
        
        send(request.toString());

        JsonObject response = getJsonResponse();
        ResponseCode responseCode = ResponseCode.getResponseFromJson(response);
        switch (responseCode) {
            case SUCCESS:
                remoteServer.unregisterForUpdates(loggedUser);
                loggedUser = null;
                break;
            // there should not be any errors on logout
            default: {
                String msg;
                switch (responseCode) {
                    case USER_NOT_REGISTERED:
                        msg = ("the user \"" + loggedUser + "\" is not signed up in the Social Network");
                    case NO_LOGGED_USER:
                        msg = ("no user is currently logged; please log in first");
                    case WRONG_USER:
                        msg = ("the user currently logged does not correspond to the user to log out");
                    default:
                        msg = responseCode.toString();
                }
                throw new IllegalStateException(msg);
            }
        }
    }

    public Map<String, List<String>> listUsers() throws IOException, NoLoggedUserException, MalformedJSONException {
        if(!isLogged()) throw new NoLoggedUserException("no user is currently logged; please log in first.");

        JsonObject request = new JsonObject();
        RequestCode.GET_USERS.addRequestToJson(request);
        request.addProperty("username", loggedUser);
        
        send(request.toString());

        JsonObject response = getJsonResponse();
        ResponseCode responseCode = ResponseCode.getResponseFromJson(response);
        switch (responseCode) {
            case SUCCESS: {
                return getUsersAndTags(response);
            }
            // there should not be any errors on list_users
            default: {
                String msg;
                switch (responseCode) {
                    case USER_NOT_REGISTERED:
                        msg = ("the user \"" + loggedUser + "\" is not signed up in the Social Network");
                    case NO_LOGGED_USER:
                        msg = ("no user is currently logged; please log in first");
                    case WRONG_USER:
                        msg = ("the user currently logged does not correspond to the username in the request");
                    default:
                        msg = responseCode.toString();
                }
                throw new IllegalStateException(msg);
            }
        }
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

    // ------------ Utility functions ------------ //

    public boolean isLogged(){ return loggedUser != null; }

    private JsonObject getJsonResponse() throws IOException, MalformedJSONException {
        try { return JsonParser.parseString(receive()).getAsJsonObject(); }
        catch (JsonParseException | IllegalStateException ex){ throw new MalformedJSONException("received malformed JSON"); }
    }

    private Map<String, List<String>> getUsersAndTags(JsonObject request) throws MalformedJSONException {
        JsonArray usersJson;
        Map<String, List<String>> users = new HashMap<>();
        try { 
            usersJson = request.get("users").getAsJsonArray();

            Iterator<JsonElement> iter = usersJson.iterator();
            while(iter.hasNext()){
                JsonObject user = iter.next().getAsJsonObject();
                String username = user.get("username").getAsString();
                List<String> tags = new ArrayList<>();

                Iterator<JsonElement> innerIter = user.get("tags").getAsJsonArray().iterator();
                while(innerIter.hasNext())
                    tags.add(innerIter.next().getAsString());
                
                users.put(username, tags);
            } 
        } catch (NullPointerException | IllegalStateException ex) {
            throw new MalformedJSONException("json response does not contain the requested information");
        }

        return users;
    }
}
