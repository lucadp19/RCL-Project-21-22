package winsome.api;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.rmi.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.*;
import java.time.Instant;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.google.gson.*;

import winsome.api.PostInfo.Comment;
import winsome.api.codes.*;
import winsome.api.exceptions.*;
import winsome.utils.cryptography.Hash;

/** The API interface to communicate with the Winsome Social Network Server. */
public class WinsomeAPI extends RemoteObject implements RemoteClient {
    private class WalletUpdatesWorker implements Callable<String> {
        private MulticastSocket mcastSocket;

        WalletUpdatesWorker(InetAddress addr, MulticastSocket sock) {
            mcastAddress = addr; mcastSocket = sock;
        }

        public String call() throws IOException {
            byte[] buf = new byte[2048];
            DatagramPacket packet = new DatagramPacket(buf, buf.length);
            mcastSocket.receive(packet);
            return new String(packet.getData(), StandardCharsets.UTF_8);
        }
    }

    /** The address of the Winsome Server */
    private final String serverAddr;
    /** The port of the Winsome Server */
    private final int serverPort; 
    /** The address (name) of the Registry containing the Remote Server */
    private final String registryAddr;
    /** The port of the Registry */
    private final int registryPort;

    /** The socket used to communicate with the server */
    private Socket socket = null;
    /** The socket used to receive wallet updates */
    private MulticastSocket mcastSocket = null;
    /** The address of the Multicast Group */
    private InetAddress mcastAddress = null;
    private ExecutorService thread = Executors.newSingleThreadExecutor();
    private Future<String> serverMsg = null;
    private WalletUpdatesWorker worker = null;
    /** The Remote Server instance */
    private RemoteServer remoteServer = null;

    /** The username of the currently logged user */
    private String loggedUser = null;
    /** The followers of the currently logged user */
    private Map<String, List<String>> followers = null;

    /**
     * Creates a new instance of a Winsome API.
     * @param serverAddr the server address
     * @param serverPort the server port
     * @param registryAddr the registry nome
     * @param registryPort the registry port
     */
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
    /**
     * Establishes a connection with the Server.
     * @throws IOException if there is an IO error
     * @throws NotBoundException if the registry does not contain the given registry address
     */    
    public void connect() throws IOException, NotBoundException {
        connectTCP(); connectRegistry();
    }

    /**
     * Connects the TCP Socket and the Multicast Socket.
     * @throws IOException if an IO error occurs
     */
    private void connectTCP() throws IOException {
        if(socket != null) throw new IllegalStateException("already connected to server");

        socket = new Socket(serverAddr, serverPort);
        getMulticastSocket();
    }

    /**
     * Connects the Registry and exports this as a RemoteClient.
     * @throws RemoteException if there is a remote error
     * @throws NotBoundException if the registry does not contain the given registry address
     */
    private void connectRegistry() throws RemoteException, NotBoundException {
        if(remoteServer != null) throw new IllegalStateException("already connected to server");

        Registry reg = LocateRegistry.getRegistry(registryPort);
        Remote remoteObj = reg.lookup(registryAddr);
        remoteServer = (RemoteServer) remoteObj;

        RemoteClient remoteClient = (RemoteClient) UnicastRemoteObject.exportObject(this, 0);
        // System.out.println("Connected to registry!");
    }

    private void getMulticastSocket() throws IOException {
        JsonObject request = new JsonObject();
        RequestCode.MULTICAST.addRequestToJson(request);

        send(request.toString());
        
        String addr; int port;
        try {
            JsonObject response = getJsonResponse();
            addr = response.get("multicast-addr").getAsString();
            port = response.get("multicast-port").getAsInt();
        } catch (MalformedJSONException | NullPointerException | ClassCastException | IllegalStateException ex) {
            throw new IOException();
        }

        mcastAddress = InetAddress.getByName(addr);
        mcastSocket = new MulticastSocket(port);
        worker = new WalletUpdatesWorker(mcastAddress, mcastSocket);
    }

    /**
     * Terminates this instance of the Winsome API.
     * @throws IOException if some IO error occurs while closing connections
     */
    public void close() throws IOException {
        if(socket != null) socket.close();
        UnicastRemoteObject.unexportObject(this, true);
        if(mcastSocket != null) { mcastSocket.leaveGroup(mcastAddress); mcastSocket.close(); }
        if(serverMsg != null) serverMsg.cancel(true);
        
        return;
    }

    /* *************** Callback methods *************** */

    @Override
    public void addFollower(String user, Collection<String> tags) throws RemoteException {
        if(loggedUser == null) return;
        if(user == null || tags == null) throw new NullPointerException("null parameters while adding new follower");
        for(String tag : tags) 
            if(tag == null) throw new NullPointerException("null parameters while adding new follower");

        followers.putIfAbsent(user, new ArrayList<>(tags));
    }

    @Override
    public void removeFollower(String user) throws RemoteException {
        if(loggedUser == null) throw new IllegalStateException(); // TODO: make new exception
        if(user == null) throw new NullPointerException("null parameter while removing follower");

        followers.remove(user);
    }

    /* *************** Test command: echo *************** */
    public String echoMsg(String msg) throws IOException {
        send(msg);
        return receive();
    }

    public Optional<String> getServerMsg() throws IOException  {
        if(!isLogged() || serverMsg == null) return Optional.empty();

        Optional<String> msg;
        try { msg = Optional.of(serverMsg.get(200, TimeUnit.MILLISECONDS)); }
        catch (TimeoutException | InterruptedException ex){ return Optional.empty(); }
        catch (ExecutionException ex){ throw new IOException(); }

        serverMsg = thread.submit(worker);
        return msg;
    }

    /* *************** Stubs for TCP Methods *************** */

    /**
     * Tries to register a new user into the Social Network.
     * @param username the username of the new user
     * @param password the password of the new user
     * @param tags the tags the new user's interested in
     * @throws UserAlreadyLoggedException if this client is already logged as some user
     * @throws UserAlreadyExistsException if the given username is not available
     * @throws RemoteException
     */
    public void register(String username, String password, Set<String> tags) 
            throws UserAlreadyExistsException, UserAlreadyLoggedException, RemoteException,
                EmptyUsernameException, EmptyPasswordException, IllegalTagException {
        if(username == null || password == null || tags == null) throw new NullPointerException("null arguments to register");
        for(String tag : tags)
            if(tag == null) throw new NullPointerException("null tag in register");
        
        if(isLogged()) throw new UserAlreadyLoggedException("a user is already logged; please log out before trying to sign up");

        remoteServer.signUp(username, Hash.fromPlainText(password), tags);
    }

    /**
     * Tries to login as a given user.
     * @param user the given username
     * @param passw the password of the given user
     * @throws IOException if some IO error occurs
     * @throws MalformedJSONException if the response is a malformed JSON
     * @throws UserAlreadyLoggedException if this client or the user is already logged in
     * @throws NoSuchUserException if no user with the given username exists
     * @throws WrongPasswordException if the password does not match the actual password
     * @throws UnexpectedServerResponse if the server sent an unexpected response
     */
    public void login(String user, String passw) 
            throws IOException, MalformedJSONException, UserAlreadyLoggedException, 
                NoSuchUserException, WrongPasswordException, UnexpectedServerResponseException {
        if(isLogged()) throw new UserAlreadyLoggedException(
            "already logged as " + loggedUser + ". Please logout before trying to login with another user.");

        // creating the request object
        JsonObject request = new JsonObject();

        // hashing the password
        Hash hash = Hash.fromPlainText(passw);

        RequestCode.LOGIN.addRequestToJson(request);
        request.addProperty("username", user);
        request.addProperty("password", hash.digest);

        // sending the request
        send(request.toString());

        // reading response
        JsonObject response = getJsonResponse();
        ResponseCode responseCode = ResponseCode.getResponseFromJson(response);
        switch (responseCode) {
            case SUCCESS: // successful login
                loggedUser = user;
                followers = new ConcurrentHashMap<>();
                remoteServer.registerForUpdates(user, this);

                // initialize followers and following maps
                try {
                    addUsersAndTags(response.get("followers").getAsJsonArray(), followers);
                } catch (NullPointerException | IllegalStateException ex) { } // leave everything empty

                mcastSocket.joinGroup(mcastAddress);
                serverMsg = thread.submit(worker);

                return;
            case USER_NOT_REGISTERED: throw new NoSuchUserException("\"" + user + "\" does not exist");
            case WRONG_PASSW: throw new WrongPasswordException("password does not match");
            case ALREADY_LOGGED: throw new UserAlreadyLoggedException("user is already logged in");
            default: throw new UnexpectedServerResponseException(responseCode.getMessage());
        }
    }

    /**
     * Logout of the current user.
     * @throws IOException if some IO error occurs
     * @throws IllegalStateException if an unexpected error is thrown
     * @throws MalformedJSONException if the response is a malformed JSON
     * @throws NoLoggedUserException if this client is not currently logged in as any user
     * @throws UnexpectedServerResponse if the server sent an unexpected response
     */
    public void logout() 
            throws IOException, IllegalStateException, MalformedJSONException, 
                NoLoggedUserException, UnexpectedServerResponseException {
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
                mcastSocket.leaveGroup(mcastAddress);
                serverMsg.cancel(true);
                serverMsg = null;
                break;
            default: throw new UnexpectedServerResponseException(responseCode.getMessage());
        }
    }

    public Map<String, List<String>> listUsers() 
            throws IOException, NoLoggedUserException, MalformedJSONException, UnexpectedServerResponseException {
        if(!isLogged()) throw new NoLoggedUserException("no user is currently logged; please log in first.");

        JsonObject request = new JsonObject();
        RequestCode.GET_USERS.addRequestToJson(request);
        request.addProperty("username", loggedUser);
        
        send(request.toString());

        JsonObject response = getJsonResponse();
        ResponseCode responseCode = ResponseCode.getResponseFromJson(response);
        switch (responseCode) {
            case SUCCESS: return getUsersAndTags(response, "users");
            // there should not be any errors on list_users
            default: throw new UnexpectedServerResponseException(responseCode.getMessage());
        }
    }

    public Map<String, List<String>> listFollowers() throws NoLoggedUserException {
        if(!isLogged()) throw new NoLoggedUserException("no user is currently logged; please log in first.");

        Map<String, List<String>> ans = new HashMap<>();
        for(Entry<String, List<String>> entry : followers.entrySet())
            ans.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        
        return ans;
    }

    public Map<String, List<String>> listFollowing() 
            throws IOException, NoLoggedUserException, MalformedJSONException, UnexpectedServerResponseException {
        if(!isLogged()) throw new NoLoggedUserException("no user is currently logged; please log in first.");

        JsonObject request = new JsonObject();
        RequestCode.GET_FOLLOWING.addRequestToJson(request);
        request.addProperty("username", loggedUser);
        
        send(request.toString());

        JsonObject response = getJsonResponse();
        ResponseCode responseCode = ResponseCode.getResponseFromJson(response);
        switch (responseCode) {
            case SUCCESS: return getUsersAndTags(response, "following");
            default: throw new UnexpectedServerResponseException(responseCode.getMessage());
        }
    }

    public void followUser(String toFollow) 
            throws IOException, MalformedJSONException, NoLoggedUserException, SelfFollowException,
                    UserNotVisibleException, AlreadyFollowingException, UnexpectedServerResponseException {
        if(!isLogged()) throw new NoLoggedUserException("no user is currently logged; please log in first.");

        JsonObject request = new JsonObject();
        RequestCode.FOLLOW.addRequestToJson(request);
        request.addProperty("username", loggedUser);
        request.addProperty("to-follow", toFollow);
        
        send(request.toString());

        JsonObject response = getJsonResponse();
        ResponseCode responseCode = ResponseCode.getResponseFromJson(response);
        switch (responseCode) {
            case SUCCESS: return;
            case USER_NOT_VISIBLE: throw new UserNotVisibleException("user to follow has no common tags");
            case SELF_FOLLOW: throw new SelfFollowException("user cannot follow themselves");
            case ALREADY_FOLLOWED: throw new AlreadyFollowingException("user already followed");
            default: throw new UnexpectedServerResponseException(responseCode.getMessage());
        }
    }

    public void unfollowUser(String toUnfollow) 
            throws IOException, MalformedJSONException, NoLoggedUserException, SelfFollowException,
                UserNotVisibleException, NotFollowingException, UnexpectedServerResponseException {
        if(!isLogged()) throw new NoLoggedUserException("no user is currently logged; please log in first.");

        JsonObject request = new JsonObject();
        RequestCode.UNFOLLOW.addRequestToJson(request);
        request.addProperty("username", loggedUser);
        request.addProperty("to-unfollow", toUnfollow);
        
        send(request.toString());

        JsonObject response = getJsonResponse();
        ResponseCode responseCode = ResponseCode.getResponseFromJson(response);
        switch (responseCode) {
            case SUCCESS: return;
            case USER_NOT_VISIBLE: throw new UserNotVisibleException("user to follow has no common tags");
            case SELF_FOLLOW: throw new SelfFollowException("user cannot un follow themselves");
            case NOT_FOLLOWING: throw new NotFollowingException("user not followed");
            default: throw new UnexpectedServerResponseException(responseCode.getMessage());
        }
    }

    public List<PostInfo> viewBlog() 
        throws IOException, NoLoggedUserException, MalformedJSONException, 
            UserNotVisibleException, UnexpectedServerResponseException  {
        if(!isLogged()) throw new NoLoggedUserException("no user is currently logged; please log in first.");

        return viewBlog(loggedUser);
    }

    public List<PostInfo> viewBlog(String otherUser) 
            throws IOException, NoLoggedUserException, MalformedJSONException, 
                UserNotVisibleException, UnexpectedServerResponseException  {
        if(!isLogged()) throw new NoLoggedUserException("no user is currently logged; please log in first.");

        JsonObject request = new JsonObject();

        RequestCode.BLOG.addRequestToJson(request);
        request.addProperty("username", loggedUser);
        request.addProperty("to-view", otherUser);
        
        send(request.toString());

        JsonObject response = getJsonResponse();
        ResponseCode responseCode = ResponseCode.getResponseFromJson(response);
        switch (responseCode) {
            case SUCCESS:
                try { 
                    Iterator<JsonElement> iter = response.get("posts").getAsJsonArray().iterator();
                    List<PostInfo> posts = new ArrayList<>();
                    while(iter.hasNext()){
                        posts.add(
                            getPostFromJson(iter.next().getAsJsonObject(), false)
                        );
                    }
                    return posts;
                }
                catch (NullPointerException | ClassCastException | IllegalStateException ex) {
                    throw new MalformedJSONException("server sent malformed json");
                }
            case USER_NOT_VISIBLE: throw new UserNotVisibleException("user to show is not visible to the current user");
            default: throw new UnexpectedServerResponseException(responseCode.getMessage());
        }
    }

    public int createPost(String title, String content) 
            throws IOException, MalformedJSONException, NoLoggedUserException, 
                TextLengthException, UnexpectedServerResponseException {
        if(!isLogged()) throw new NoLoggedUserException("no user is currently logged; please log in first.");
        if(title.isEmpty() || content.isEmpty()) throw new TextLengthException("title and content must not be empty");
        if(title.length() > 50 || content.length() > 500)
            throw new TextLengthException("title or content exceed maximum length");

        JsonObject request = new JsonObject();
        RequestCode.POST.addRequestToJson(request);
        request.addProperty("username", loggedUser);
        request.addProperty("title", title);
        request.addProperty("content", content);
        
        send(request.toString());

        JsonObject response = getJsonResponse();
        ResponseCode responseCode = ResponseCode.getResponseFromJson(response);
        switch (responseCode) {
            case SUCCESS:
                try { return response.get("id").getAsInt(); }
                catch (NullPointerException | ClassCastException | IllegalStateException ex) {
                    throw new MalformedJSONException("server sent malformed json");
                }
            default: throw new UnexpectedServerResponseException(responseCode.getMessage());
        }
    }
    
    public List<PostInfo> showFeed() throws IOException, NoLoggedUserException, MalformedJSONException, UnexpectedServerResponseException  {
        if(!isLogged()) throw new NoLoggedUserException("no user is currently logged; please log in first.");

        JsonObject request = new JsonObject();
        RequestCode.FEED.addRequestToJson(request);
        request.addProperty("username", loggedUser);
        
        send(request.toString());

        JsonObject response = getJsonResponse();
        ResponseCode responseCode = ResponseCode.getResponseFromJson(response);
        switch (responseCode) {
            case SUCCESS:
                try { 
                    Iterator<JsonElement> iter = response.get("posts").getAsJsonArray().iterator();
                    List<PostInfo> posts = new ArrayList<>();
                    while(iter.hasNext()){
                        posts.add(
                            getPostFromJson(iter.next().getAsJsonObject(), false)
                        );
                    }
                    return posts;
                }
                catch (NullPointerException | ClassCastException | IllegalStateException ex) {
                    throw new MalformedJSONException("server sent malformed json");
                }
            default: throw new UnexpectedServerResponseException(responseCode.getMessage());
        }
    }

    public PostInfo showPost(int idPost) 
            throws IOException, MalformedJSONException, NoLoggedUserException, 
                NoSuchPostException, UnexpectedServerResponseException {
        if(!isLogged()) throw new NoLoggedUserException("no user is currently logged; please log in first.");

        JsonObject request = new JsonObject();
        RequestCode.SHOW_POST.addRequestToJson(request);
        request.addProperty("username", loggedUser);
        request.addProperty("id", idPost);
        
        send(request.toString());

        JsonObject response = getJsonResponse();
        ResponseCode responseCode = ResponseCode.getResponseFromJson(response);
        switch (responseCode) {
            case SUCCESS:
                try { return getPostFromJson(response.get("post").getAsJsonObject(), true); }
                catch (NullPointerException | ClassCastException | IllegalStateException ex) {
                    throw new MalformedJSONException("server sent malformed json");
                }
            case NO_POST: throw new NoSuchPostException("the given post does not exist");
            default: throw new UnexpectedServerResponseException(responseCode.getMessage());
        }
    }

    public void deletePost(int idPost) 
            throws IOException, NoLoggedUserException, MalformedJSONException, 
                NoSuchPostException, NotPostOwnerException, UnexpectedServerResponseException {
        if(!isLogged()) throw new NoLoggedUserException("no user is currently logged; please log in first.");

        JsonObject request = new JsonObject();
        RequestCode.DELETE_POST.addRequestToJson(request);
        request.addProperty("username", loggedUser);
        request.addProperty("id", idPost);
        
        send(request.toString());

        JsonObject response = getJsonResponse();
        ResponseCode responseCode = ResponseCode.getResponseFromJson(response);
        switch (responseCode) {
            case SUCCESS: return;
            case NO_POST: throw new NoSuchPostException("there is no post with the given id");
            case NOT_POST_OWNER: throw new NotPostOwnerException("this user is not the owner of the given post");
            default: throw new UnexpectedServerResponseException(responseCode.getMessage());
        }
    }

    public void rewinPost(int idPost) 
            throws IOException, NoLoggedUserException, MalformedJSONException, NoSuchPostException, 
                AlreadyRewinnedException, NotFollowingException, PostOwnerException, UnexpectedServerResponseException {
        if(!isLogged()) throw new NoLoggedUserException("no user is currently logged; please log in first.");

        JsonObject request = new JsonObject();
        RequestCode.REWIN_POST.addRequestToJson(request);
        request.addProperty("username", loggedUser);
        request.addProperty("id", idPost);
        
        send(request.toString());

        JsonObject response = getJsonResponse();
        ResponseCode responseCode = ResponseCode.getResponseFromJson(response);
        switch (responseCode) {
            case SUCCESS: return;
            case NO_POST: throw new NoSuchPostException("there is no post with the given id");
            case NOT_FOLLOWING: throw new NotFollowingException("the current user is not following the owner of the post to interact with");
            case POST_OWNER: throw new PostOwnerException("this user is the owner of the given post");
            case REWIN_ERR: throw new AlreadyRewinnedException("this user has already rewinned this post");
            default: throw new UnexpectedServerResponseException(responseCode.getMessage());
        }
    }

    public void ratePost(int idPost, int vote) 
            throws IOException, NoLoggedUserException, MalformedJSONException, 
                NoSuchPostException, AlreadyVotedException, WrongVoteFormatException, 
                NotFollowingException, PostOwnerException, UnexpectedServerResponseException {
        if(!isLogged()) throw new NoLoggedUserException("no user is currently logged; please log in first.");
        if(vote != +1 && vote != -1) throw new WrongVoteFormatException("vote should be either +1 or -1");

        JsonObject request = new JsonObject();
        RequestCode.RATE_POST.addRequestToJson(request);
        request.addProperty("username", loggedUser);
        request.addProperty("id", idPost);
        request.addProperty("vote", vote);
        
        send(request.toString());

        JsonObject response = getJsonResponse();
        ResponseCode responseCode = ResponseCode.getResponseFromJson(response);
        switch (responseCode) {
            case SUCCESS: return;
            case NO_POST: throw new NoSuchPostException("there is no post with the given id");
            case NOT_FOLLOWING: throw new NotFollowingException("the current user is not following the owner of the post to interact with");
            case POST_OWNER: throw new PostOwnerException("cannot vote your own posts");
            case ALREADY_VOTED: throw new AlreadyVotedException("this user has already voted the given post");
            default: throw new UnexpectedServerResponseException(responseCode.getMessage());
        }
    }

    public void addComment(int idPost, String comment) 
            throws IOException, NoLoggedUserException, MalformedJSONException, 
                NoSuchPostException, PostOwnerException, NotFollowingException, UnexpectedServerResponseException {
        if(!isLogged()) throw new NoLoggedUserException("no user is currently logged; please log in first.");

        JsonObject request = new JsonObject();
        RequestCode.COMMENT.addRequestToJson(request);
        request.addProperty("username", loggedUser);
        request.addProperty("id", idPost);
        request.addProperty("comment", comment);
        
        send(request.toString());

        JsonObject response = getJsonResponse();
        ResponseCode responseCode = ResponseCode.getResponseFromJson(response);
        switch (responseCode) {
            case SUCCESS: return;
            case NO_POST: throw new NoSuchPostException("there is no post with the given id");
            case NOT_FOLLOWING: throw new NotFollowingException("the current user is not following the owner of the post to interact with");
            case POST_OWNER: throw new PostOwnerException("you are the owner of the post");
            default: throw new UnexpectedServerResponseException(responseCode.getMessage());
        }
    }

    public Wallet getWallet() throws IOException, NoLoggedUserException, MalformedJSONException, UnexpectedServerResponseException {
        if(!isLogged()) throw new NoLoggedUserException("no user is currently logged; please log in first.");

        JsonObject request = new JsonObject();
        RequestCode.WALLET.addRequestToJson(request);
        request.addProperty("username", loggedUser);
        
        send(request.toString());

        JsonObject response = getJsonResponse();
        ResponseCode responseCode = ResponseCode.getResponseFromJson(response);
        switch (responseCode) {
            case SUCCESS:
                try { return getWalletFromJson(response); }
                catch (NullPointerException | ClassCastException | IllegalStateException ex) {
                    throw new MalformedJSONException("server sent malformed json");
                }
            default: throw new UnexpectedServerResponseException(responseCode.getMessage());
        }
    }
    
    public double getWalletInBitcoin() 
            throws IOException, NoLoggedUserException, MalformedJSONException, 
                ExchangeRateException, UnexpectedServerResponseException {
        if(!isLogged()) throw new NoLoggedUserException("no user is currently logged; please log in first.");

        JsonObject request = new JsonObject();
        RequestCode.WALLET_BTC.addRequestToJson(request);
        request.addProperty("username", loggedUser);
        
        send(request.toString());

        JsonObject response = getJsonResponse();
        ResponseCode responseCode = ResponseCode.getResponseFromJson(response);
        switch (responseCode) {
            case SUCCESS:
                try { return response.get("btc-total").getAsDouble(); }
                catch (NullPointerException | ClassCastException | IllegalStateException ex) {
                    throw new MalformedJSONException("server sent malformed json");
                }
            case EXCHANGE_RATE_ERROR: throw new ExchangeRateException("server could not compute the current exchange rate");
            default: throw new UnexpectedServerResponseException(responseCode.getMessage());
        }
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

    private Map<String, List<String>> getUsersAndTags(JsonObject request, String fieldName) throws MalformedJSONException {
        JsonArray usersJson;
        Map<String, List<String>> users = new HashMap<>();
        try { 
            usersJson = request.get(fieldName).getAsJsonArray();

            addUsersAndTags(usersJson, users);
        } catch (NullPointerException | IllegalStateException ex) {
            throw new MalformedJSONException("json response does not contain the requested information");
        }

        return users;
    }

    private void addUsersAndTags(JsonArray array, Map<String, List<String>> users) throws MalformedJSONException {
        if(users == null) throw new NullPointerException();

        try { 
            Iterator<JsonElement> iter = array.iterator();
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
    }

    private PostInfo getPostFromJson(JsonObject json, boolean includeInfo) throws MalformedJSONException {
        if(json == null) throw new NullPointerException("null json");
        PostInfo post;

        try {
            int id = json.get("id").getAsInt();
            String author = json.get("author").getAsString();
            String title = json.get("title").getAsString();
            String contents = "";
            
            Optional<String> rewinner;
            Optional<Integer> rewinID;
            
            try { 
                rewinner = Optional.of(json.get("rewinner").getAsString());
                rewinID = Optional.of(json.get("original-id").getAsInt());
            } catch (NullPointerException ex){ rewinner = Optional.empty(); rewinID = Optional.empty(); }

            int upvotes = 0;
            int downvotes = 0;
            List<Comment> comments = new ArrayList<>();

            if(includeInfo) {
                contents = json.get("contents").getAsString();
                upvotes = json.get("upvotes").getAsInt();
                downvotes = json.get("downvotes").getAsInt();
                Iterator<JsonElement> iter = json.get("comments").getAsJsonArray().iterator();
                while(iter.hasNext()){
                    JsonObject obj = iter.next().getAsJsonObject();
                    comments.add(
                        new Comment(
                            obj.get("author").getAsString(), 
                            obj.get("contents").getAsString()
                    ));
                }
            }

            return new PostInfo(id, author, title, contents, rewinner, rewinID, upvotes, downvotes, comments);
        } catch (NullPointerException | ClassCastException | IllegalStateException ex) {
            throw new MalformedJSONException("given json does not represent a valid post");
        }
    }

    private Wallet getWalletFromJson(JsonObject json) throws MalformedJSONException {
        if(json == null) throw new NullPointerException("null object");

        try {
            Double total = json.get("total").getAsDouble();
            List<TransactionInfo> transactions = new ArrayList<>();

            Iterator<JsonElement> iter = json.get("transactions").getAsJsonArray().iterator();
            while(iter.hasNext()){
                JsonObject obj = iter.next().getAsJsonObject();
                transactions.add(
                    new TransactionInfo(
                        obj.get("increment").getAsDouble(), 
                        Instant.parse(obj.get("timestamp").getAsString())
                    )
                );
            }

            return new Wallet(total, transactions);
        } catch (NullPointerException | ClassCastException | IllegalStateException ex){
            throw new MalformedJSONException("given json does not represent a valid wallet");
        }
    }
}
