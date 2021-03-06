package winsome.api;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

import java.rmi.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RemoteObject;
import java.rmi.server.UnicastRemoteObject;
import java.time.Instant;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.google.gson.*;

import winsome.api.codes.*;
import winsome.api.exceptions.*;
import winsome.api.remote.*;
import winsome.api.userstructs.*;
import winsome.api.userstructs.PostInfo.Comment;

import winsome.utils.cryptography.Hash;

/** The API interface to communicate with the Winsome Social Network Server. */
public class WinsomeAPI extends RemoteObject implements RemoteClient {
    /** Reads and registers the server updates on the Multicast Socket */
    private class WalletUpdatesWorker implements Callable<Void> {
        /** The multicast socket */
        private MulticastSocket mcastSocket;
        /** The multicast address */
        private InetAddress mcastAddress;
        /** A blocking queue containing the server messages */
        private BlockingQueue<String> messages;

        /**
         * Initializes this worker.
         * @param addr the multicast address
         * @param sock the multicast socket
         */
        public WalletUpdatesWorker(InetAddress addr, MulticastSocket sock) throws IOException {
            mcastAddress = addr;
            mcastSocket = sock;
            mcastSocket.joinGroup(mcastAddress);
            messages = new LinkedBlockingQueue<>();
        }

        /**
         * Empties the message queue.
         */
        public void clear() throws IOException {
            messages.clear();
        }

        /** Receives server messages and saves them. */
        @Override
        public Void call() throws IOException {
            while(true) {
                byte[] buf = new byte[2048];
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                mcastSocket.receive(packet);
                // adds the message to the queue
                if(isLogged())
                    messages.offer(
                        new String(packet.getData(), StandardCharsets.UTF_8)
                    );
            }
        }

        /**
         * Returns all the messages sent by the server until now.
         * @return the server messages
         */
        public Collection<String> getMessages(){
            return getMessages(new ArrayList<>());
        }

        /**
         * Adds the server messages to a collection.
         * @param buffer the given collection
         * @return the filled up buffer
         */
        public Collection<String> getMessages(Collection<String> buffer){
            messages.drainTo(buffer);
            return buffer;
        }
    }

    /** The address of the Winsome Server */
    private final String serverAddr;
    /** The port of the Winsome Server */
    private final int serverPort; 
    /** The name of the Registry containing the Remote Server */
    private final String registryName;
    /** The port of the Registry */
    private final int registryPort;
    /** The socket timeout */
    private final int sockTimeout;

    /** The socket used to communicate with the server */
    private Socket socket = null;
    /** The socket used to receive wallet updates */
    private MulticastSocket mcastSocket = null;
    /** The address of the Multicast Group */
    private InetAddress mcastAddress = null;

    /** The Wallet Updates Worker */
    private WalletUpdatesWorker worker = null;
    /** The thread pool running the Wallet Update Worker */
    private ExecutorService thread = Executors.newSingleThreadExecutor();
    /** Result of the Wallet Updates Worker thread (useful to check it hasn't thrown) */
    private Future<Void> mcastFuture = null;

    /** The Remote Server instance */
    private RemoteServer remoteServer = null;

    /** The username of the currently logged user */
    private Optional<String> loggedUser = Optional.empty();
    /** The followers of the currently logged user */
    private Map<String, List<String>> followers = null;

    /**
     * Creates a new instance of a Winsome API.
     * @param serverAddr the server address
     * @param serverPort the server port
     * @param registryName the registry name
     * @param registryPort the registry port
     * @param sockTimeout the socket timeout
     */
    public WinsomeAPI(
        String serverAddr, 
        int serverPort,
        String registryName,
        int registryPort,
        int sockTimeout
    ){
        super();

        this.serverAddr = serverAddr;
        this.serverPort = serverPort;
        this.registryName = registryName;
        this.registryPort = registryPort;
        this.sockTimeout = sockTimeout;
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
        socket.setSoTimeout(sockTimeout);

        getMulticastSocket();
    }

    /**
     * Connects the Registry and exports this as a RemoteClient.
     * @throws RemoteException if there is a remote error
     * @throws NotBoundException if the registry does not contain the given registry address
     */
    private void connectRegistry() throws RemoteException, NotBoundException {
        if(remoteServer != null) throw new IllegalStateException("already connected to server");

        Registry reg = LocateRegistry.getRegistry(serverAddr, registryPort);
        remoteServer = (RemoteServer) reg.lookup(registryName);

        UnicastRemoteObject.exportObject(this, 0);
    }

    /**
     * Requests the Multicast Socket's coordinates to the server and initializes it.
     * @throws IOException if some IO error occurs
     */
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
            throw new IOException("could not obtain coordinates of multicast socket");
        }

        mcastAddress = InetAddress.getByName(addr);
        mcastSocket = new MulticastSocket(port);
        worker = new WalletUpdatesWorker(mcastAddress, mcastSocket);
        mcastFuture = thread.submit(worker);
    }

    /**
     * Terminates this instance of the Winsome API.
     * @throws IOException if some IO error occurs while closing connections
     */
    public void close() throws IOException {
        if(socket != null) socket.close();
        UnicastRemoteObject.unexportObject(this, true);
        if(mcastSocket != null) { mcastSocket.leaveGroup(mcastAddress); mcastSocket.close(); }
        if(mcastFuture != null) mcastFuture.cancel(true);
    }

    /* *************** Callback methods *************** */

    @Override
    public void addFollower(String user, Collection<String> tags) throws RemoteException {
        if(!isLogged()) return;
        if(user == null || tags == null) throw new NullPointerException("null parameters while adding new follower");
        for(String tag : tags) 
            if(tag == null) throw new NullPointerException("null parameters while adding new follower");

        followers.putIfAbsent(user, new ArrayList<>(tags));
    }

    @Override
    public void removeFollower(String user) throws RemoteException {
        if(!isLogged()) return;
        if(user == null) throw new NullPointerException("null parameter while removing follower");

        followers.remove(user);
    }

    // --------------- Server Messages ----------------- //

    /**
     * Reads and returns any message the server might have sent.
     * @return {@link java.util.Optional#empty()} if no client is actually logged,
     *      a collection of messages wrapped in {@link java.util.Optional#of(Object)} otherwise 
     * @throws IOException if some IO error occurs
     */
    public Optional<Collection<String>> getServerMsg() throws IOException  {
        // check that this client is actually logged
        if(!isLogged() || mcastFuture == null) return Optional.empty();

        try { mcastFuture.get(1, TimeUnit.MICROSECONDS); }
        // request timed out because thread is still working => ok!
        catch (TimeoutException | InterruptedException ex) { }
        catch (ExecutionException ex) {
            throw new IOException("exception while waiting for server messages", ex); 
        }

        // get server messages
        Collection<String> messages = new ArrayList<>();
        return Optional.of(worker.getMessages(messages));
    }

    /* *************** TCP methods *************** */

    /**
     * Tries to register a new user into the Social Network.
     * @param username the username of the new user
     * @param password the password of the new user
     * @param tags the tags the new user's interested in
     * @throws UserAlreadyLoggedException if this client is already logged as some user
     * @throws UserAlreadyExistsException if the given username is not available
     * @throws RemoteException if some IO error occurs while calling this method
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
            case SUCCESS -> { // successful login
                loggedUser = Optional.of(user);
                followers = new ConcurrentHashMap<>();
                remoteServer.registerForUpdates(user, this);

                // initialize followers and following maps
                try {
                    addUsersAndTags(response.get("followers").getAsJsonArray(), followers);
                } catch (NullPointerException | IllegalStateException ex) { } // leave everything empty
            }
            case USER_NOT_REGISTERED -> throw new NoSuchUserException("\"" + user + "\" does not exist");
            case WRONG_PASSW -> throw new WrongPasswordException("password does not match");
            case ALREADY_LOGGED -> throw new UserAlreadyLoggedException("user is already logged in");
            default -> throw new UnexpectedServerResponseException(responseCode.getMessage());
        }
    }

    /**
     * Logout of the current user.
     * @throws IOException if some IO error occurs
     * @throws MalformedJSONException if the response is a malformed JSON
     * @throws NoLoggedUserException if this client is not currently logged in as any user
     * @throws UnexpectedServerResponse if the server sent an unexpected response
     */
    public void logout() throws IOException, MalformedJSONException, 
                NoLoggedUserException, UnexpectedServerResponseException {
        if(!isLogged()) throw new NoLoggedUserException("no user is currently logged; please log in first.");

        JsonObject request = new JsonObject();
        RequestCode.LOGOUT.addRequestToJson(request);
        request.addProperty("username", loggedUser.get());
        
        send(request.toString());

        // getting response
        JsonObject response = getJsonResponse();
        ResponseCode responseCode = ResponseCode.getResponseFromJson(response);
        switch (responseCode) {
            case SUCCESS -> {
                remoteServer.unregisterForUpdates(loggedUser.get());
                loggedUser = Optional.empty();

                worker.clear();
            }
            default -> throw new UnexpectedServerResponseException(responseCode.getMessage());
        }
    }

    /**
     * Lists the users of this Social Network who have common interests with the currently logged user.
     * @return the users of this SN
     * @throws IOException if some IO error occurs
     * @throws NoLoggedUserException if no user is currently logged
     * @throws MalformedJSONException if the server sent a malformed response
     * @throws UnexpectedServerResponseException if server sent an unexpected response
     */
    public Map<String, List<String>> listUsers() 
            throws IOException, NoLoggedUserException, MalformedJSONException, UnexpectedServerResponseException {
        if(!isLogged()) throw new NoLoggedUserException("no user is currently logged; please log in first.");

        JsonObject request = new JsonObject();
        RequestCode.GET_USERS.addRequestToJson(request);
        request.addProperty("username", loggedUser.get());
        
        send(request.toString());

        JsonObject response = getJsonResponse();
        ResponseCode responseCode = ResponseCode.getResponseFromJson(response);

        return switch (responseCode) {
            case SUCCESS -> getUsersAndTags(response, "users");
            default -> throw new UnexpectedServerResponseException(responseCode.getMessage());
        };
    }

    /**
     * Lists the followers of the currently logged user.
     * @return this user's followers
     * @throws NoLoggedUserException if no user is currently logged
     */
    public Map<String, List<String>> listFollowers() throws NoLoggedUserException {
        if(!isLogged()) throw new NoLoggedUserException("no user is currently logged; please log in first.");

        Map<String, List<String>> ans = new HashMap<>();
        for(Entry<String, List<String>> entry : followers.entrySet())
            ans.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        
        return ans;
    }

    /**
     * Lists the users followed by the current user
     * @return the users followed by the current user
     * @throws IOException if some IO error occurs
     * @throws NoLoggedUserException if no user is currently logged
     * @throws MalformedJSONException if the server sent a malformed response
     * @throws UnexpectedServerResponseException if server sent an unexpected response
     */
    public Map<String, List<String>> listFollowing() 
            throws IOException, NoLoggedUserException, MalformedJSONException, UnexpectedServerResponseException {
        if(!isLogged()) throw new NoLoggedUserException("no user is currently logged; please log in first.");

        JsonObject request = new JsonObject();
        RequestCode.GET_FOLLOWING.addRequestToJson(request);
        request.addProperty("username", loggedUser.get());
        
        send(request.toString());

        JsonObject response = getJsonResponse();
        ResponseCode responseCode = ResponseCode.getResponseFromJson(response);
        switch (responseCode) {
            case SUCCESS: return getUsersAndTags(response, "following");
            default: throw new UnexpectedServerResponseException(responseCode.getMessage());
        }
    }

    /**
     * Follows a user.
     * @param toFollow username of the user to follow
     * @throws IOException if some IO error occurs
     * @throws NoLoggedUserException if no user is currently logged
     * @throws MalformedJSONException if the server sent a malformed response
     * @throws SelfFollowException if the current user is trying to follow themselves
     * @throws UserNotVisibleException if the user to follow has no common interests with the logged user
     * @throws AlreadyFollowingException if the logged user already follows toFollow
     * @throws UnexpectedServerResponseException if server sent an unexpected response
     */
    public void followUser(String toFollow) 
            throws IOException, MalformedJSONException, NoLoggedUserException, SelfFollowException,
                    UserNotVisibleException, AlreadyFollowingException, UnexpectedServerResponseException {
        if(!isLogged()) throw new NoLoggedUserException("no user is currently logged; please log in first.");

        JsonObject request = new JsonObject();
        RequestCode.FOLLOW.addRequestToJson(request);
        request.addProperty("username", loggedUser.get());
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


    /**
     * Unfollows a user.
     * @param toUnfollow username of the user to follow
     * @throws IOException if some IO error occurs
     * @throws NoLoggedUserException if no user is currently logged
     * @throws MalformedJSONException if the server sent a malformed response
     * @throws SelfFollowException if the current user is trying to unfollow themselves
     * @throws UserNotVisibleException if the user to unfollow has no common interests with the logged user
     * @throws NotFollowingException if the logged user does not follow toUnfollow
     * @throws UnexpectedServerResponseException if server sent an unexpected response
     */
    public void unfollowUser(String toUnfollow) 
            throws IOException, MalformedJSONException, NoLoggedUserException, SelfFollowException,
                UserNotVisibleException, NotFollowingException, UnexpectedServerResponseException {
        if(!isLogged()) throw new NoLoggedUserException("no user is currently logged; please log in first.");

        JsonObject request = new JsonObject();
        RequestCode.UNFOLLOW.addRequestToJson(request);
        request.addProperty("username", loggedUser.get());
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

    /**
     * Shows the blog of the current user
     * @throws IOException if some IO error occurs
     * @throws NoLoggedUserException if no user is currently logged
     * @throws MalformedJSONException if the server sent a malformed response
     * @throws UnexpectedServerResponseException if server sent an unexpected response
     */
    public List<PostInfo> viewBlog() throws IOException, NoLoggedUserException, MalformedJSONException, UnexpectedServerResponseException  {
        if(!isLogged()) throw new NoLoggedUserException("no user is currently logged; please log in first.");

        try { return viewBlog(loggedUser.get()); }
        catch (UserNotVisibleException ex) { throw new InternalError("impossible error: user has no common tags with themselves"); }
    }

    /**
     * Shows the blog of a given user.
     * @param otherUser username of the user to show
     * @throws IOException if some IO error occurs
     * @throws NoLoggedUserException if no user is currently logged
     * @throws MalformedJSONException if the server sent a malformed response
     * @throws UserNotVisibleException if the current user has no common interest with otherUser
     * @throws UnexpectedServerResponseException if server sent an unexpected response
     */
    public List<PostInfo> viewBlog(String otherUser) 
            throws IOException, NoLoggedUserException, MalformedJSONException, 
                UserNotVisibleException, UnexpectedServerResponseException  {
        if(!isLogged()) throw new NoLoggedUserException("no user is currently logged; please log in first.");

        JsonObject request = new JsonObject();

        RequestCode.BLOG.addRequestToJson(request);
        request.addProperty("username", loggedUser.get());
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

    /**
     * Creates a new post.
     * @param title the post's title
     * @param content the post's contents
     * @return the id of the newly created post
     * @throws IOException if some IO error occurs
     * @throws NoLoggedUserException if no user is currently logged
     * @throws MalformedJSONException if the server sent a malformed response
     * @throws TextLengthException if title or content are either empty or exceed maximum length
     * @throws UnexpectedServerResponseException if server sent an unexpected response
     */
    public int createPost(String title, String content) 
            throws IOException, MalformedJSONException, NoLoggedUserException, 
                TextLengthException, UnexpectedServerResponseException {
        if(!isLogged()) throw new NoLoggedUserException("no user is currently logged; please log in first.");
        if(title.isEmpty() || content.isEmpty()) throw new TextLengthException("title and content must not be empty");
        if(title.length() > 50 || content.length() > 500)
            throw new TextLengthException("title or content exceed maximum length");

        JsonObject request = new JsonObject();
        RequestCode.POST.addRequestToJson(request);
        request.addProperty("username", loggedUser.get());
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

    /**
     * Shows the current user's feed.
     * @throws IOException if some IO error occurs
     * @throws NoLoggedUserException if no user is currently logged
     * @throws MalformedJSONException if the server sent a malformed response
     * @throws UnexpectedServerResponseException if server sent an unexpected response
     */
    public List<PostInfo> showFeed() throws IOException, NoLoggedUserException, MalformedJSONException, UnexpectedServerResponseException  {
        if(!isLogged()) throw new NoLoggedUserException("no user is currently logged; please log in first.");

        JsonObject request = new JsonObject();
        RequestCode.FEED.addRequestToJson(request);
        request.addProperty("username", loggedUser.get());
        
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

    /**
     * Shows the post with the given ID.
     * @param idPost the given ID
     * @return the information of the post with the given ID
     * @throws IOException if some IO error occurs
     * @throws NoLoggedUserException if no user is currently logged
     * @throws MalformedJSONException if the server sent a malformed response
     * @throws NoSuchPostException if no post with the given ID exists
     * @throws UnexpectedServerResponseException if server sent an unexpected response
     */
    public PostInfo showPost(int idPost) 
            throws IOException, MalformedJSONException, NoLoggedUserException, 
                NoSuchPostException, UnexpectedServerResponseException {
        if(!isLogged()) throw new NoLoggedUserException("no user is currently logged; please log in first.");

        JsonObject request = new JsonObject();
        RequestCode.SHOW_POST.addRequestToJson(request);
        request.addProperty("username", loggedUser.get());
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


    /**
     * Deletes the post with the given ID.
     * @param idPost the given ID
     * @throws IOException if some IO error occurs
     * @throws NoLoggedUserException if no user is currently logged
     * @throws MalformedJSONException if the server sent a malformed response
     * @throws NoSuchPostException if no post with the given ID exists
     * @throws NotPostOwnerException if the current user is not the owner of the post
     * @throws UnexpectedServerResponseException if server sent an unexpected response
     */
    public void deletePost(int idPost) 
            throws IOException, NoLoggedUserException, MalformedJSONException, 
                NoSuchPostException, NotPostOwnerException, UnexpectedServerResponseException {
        if(!isLogged()) throw new NoLoggedUserException("no user is currently logged; please log in first.");

        JsonObject request = new JsonObject();
        RequestCode.DELETE_POST.addRequestToJson(request);
        request.addProperty("username", loggedUser.get());
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


    /**
     * Rewins the post with the given ID.
     * @param idPost the given ID
     * @throws IOException if some IO error occurs
     * @throws NoLoggedUserException if no user is currently logged
     * @throws MalformedJSONException if the server sent a malformed response
     * @throws NoSuchPostException if no post with the given ID exists     
     * @throws AlreadyRewinnedException if the current user has already rewinned the given post
     * @throws NotFollowingException if the current user does not follow the author of the given post
     * @throws PostOwnerException if the current user is the owner of the post
     * @throws UnexpectedServerResponseException if server sent an unexpected response
     */
    public void rewinPost(int idPost) 
            throws IOException, NoLoggedUserException, MalformedJSONException, NoSuchPostException, 
                AlreadyRewinnedException, NotFollowingException, PostOwnerException, UnexpectedServerResponseException {
        if(!isLogged()) throw new NoLoggedUserException("no user is currently logged; please log in first.");

        JsonObject request = new JsonObject();
        RequestCode.REWIN_POST.addRequestToJson(request);
        request.addProperty("username", loggedUser.get());
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

    /**
     * Rates the post with the given ID.
     * @param idPost the given ID
     * @param vote the given vote (+1 for upvotes, -1 for downvotes)
     * @throws IOException if some IO error occurs
     * @throws NoLoggedUserException if no user is currently logged
     * @throws MalformedJSONException if the server sent a malformed response
     * @throws NoSuchPostException if no post with the given ID exists     
     * @throws AlreadyVotedException if the current user has already voted the given post
     * @throws NotFollowingException if the current user does not follow the author of the given post
     * @throws WrongVoteFormatException if the vote is not +1/-1
     * @throws PostOwnerException if the current user is the owner of the post
     * @throws UnexpectedServerResponseException if server sent an unexpected response
     */
    public void ratePost(int idPost, int vote) 
            throws IOException, NoLoggedUserException, MalformedJSONException, 
                NoSuchPostException, AlreadyVotedException, WrongVoteFormatException, 
                NotFollowingException, PostOwnerException, UnexpectedServerResponseException {
        if(!isLogged()) throw new NoLoggedUserException("no user is currently logged; please log in first.");
        if(vote != +1 && vote != -1) throw new WrongVoteFormatException("vote should be either +1 or -1");

        JsonObject request = new JsonObject();
        RequestCode.RATE_POST.addRequestToJson(request);
        request.addProperty("username", loggedUser.get());
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

    /**
     * Adds a comment to the post with the given ID.
     * @param idPost the given ID
     * @param comment the cmment
     * @throws IOException if some IO error occurs
     * @throws NoLoggedUserException if no user is currently logged
     * @throws MalformedJSONException if the server sent a malformed response
     * @throws NoSuchPostException if no post with the given ID exists
     * @throws NotFollowingException if the current user does not follow the author of the given post
     * @throws PostOwnerException if the current user is the owner of the post
     * @throws UnexpectedServerResponseException if server sent an unexpected response
     */
    public void addComment(int idPost, String comment) 
            throws IOException, NoLoggedUserException, MalformedJSONException, 
                NoSuchPostException, PostOwnerException, NotFollowingException, UnexpectedServerResponseException {
        if(!isLogged()) throw new NoLoggedUserException("no user is currently logged; please log in first.");

        JsonObject request = new JsonObject();
        RequestCode.COMMENT.addRequestToJson(request);
        request.addProperty("username", loggedUser.get());
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

    /**
     * Returns the wallet of the current user.
     * @return the wallet of the current user
     * @throws IOException if some IO error occurs
     * @throws NoLoggedUserException if no user is currently logged
     * @throws MalformedJSONException if the server sent a malformed response
     * @throws UnexpectedServerResponseException if server sent an unexpected response
     */
    public Wallet getWallet() throws IOException, NoLoggedUserException, MalformedJSONException, UnexpectedServerResponseException {
        if(!isLogged()) throw new NoLoggedUserException("no user is currently logged; please log in first.");

        JsonObject request = new JsonObject();
        RequestCode.WALLET.addRequestToJson(request);
        request.addProperty("username", loggedUser.get());
        
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
    
    /**
     * Returns the total amount of the rewards of the current user, converted in Bitcoin.
     * @return the BTC reward of the current user
     * @throws IOException if some IO error occurs
     * @throws NoLoggedUserException if no user is currently logged
     * @throws MalformedJSONException if the server sent a malformed response
     * @throws ExchangeRateException if the server could not compute the exchange rate
     * @throws UnexpectedServerResponseException if server sent an unexpected response
     */
    public double getWalletInBitcoin() 
            throws IOException, NoLoggedUserException, MalformedJSONException, 
                ExchangeRateException, UnexpectedServerResponseException {
        if(!isLogged()) throw new NoLoggedUserException("no user is currently logged; please log in first.");

        JsonObject request = new JsonObject();
        RequestCode.WALLET_BTC.addRequestToJson(request);
        request.addProperty("username", loggedUser.get());
        
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
    /**
     * Sends a message through the TCP socket.
     * @param msg the given message
     * @throws IOException if some IO error occurs
     */
    private void send(String msg) throws IOException {
        Objects.requireNonNull(msg, "attempting to send an empty message");

        // not wrapped in a try-with-resources otherwise the socket is automatically closed!
        DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));

        byte[] tmp = msg.getBytes(StandardCharsets.UTF_8);
        out.writeInt(tmp.length);
        out.write(tmp);
        out.flush();
    }

    /**
     * Receives a message from the TCP socket.
     * @return the received message
     * @throws IOException if some IO error occurs
     */
    private String receive() throws IOException {
        // not wrapped in a try-with-resources otherwise the socket is automatically closed!
        DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));

        int len = in.readInt();
        if(len <= 0) throw new IOException("received message length less or equal to 0");

        byte[] buf = new byte[len];
        in.readFully(buf);

        return new String(buf, StandardCharsets.UTF_8);
    }  

    /**
     * Reads a message from the TCP socket and parses it as a JsonObject.
     * @return the parsed JSON object
     * @throws IOException if some IO error occurs
     * @throws MalformedJSONException if the server sent a malformed JSON
     */
    private JsonObject getJsonResponse() throws IOException, MalformedJSONException {
        try { return JsonParser.parseString(receive()).getAsJsonObject(); }
        catch (JsonParseException | IllegalStateException ex){ throw new MalformedJSONException("received malformed JSON"); }
    }

    // ------------ Utility functions ------------ //

    /**
     * Checks whether a user is currently logged.
     * @return true if and only if a user is currently logged
     */
    public boolean isLogged(){ return loggedUser.isPresent(); }

    /**
     * Returns the currently logged user.
     * @return the currently logged user
     */
    public Optional<String> getLoggedUser() { return loggedUser; }

    /**
     * Parses a specific field of a JSON object as a map String -> List<String>. 
     * @param json the JSON object
     * @param fieldName the JSON object field
     * @return the parsed map
     * @throws MalformedJSONException if the given JSON object could not be parsed as a map
     */
    private Map<String, List<String>> getUsersAndTags(JsonObject json, String fieldName) throws MalformedJSONException {
        Objects.requireNonNull(json, "the given json object must not be null");
        Objects.requireNonNull(fieldName, "the given field name must not be null");

        try { 
            Map<String, List<String>> users = new HashMap<>();
            JsonArray usersJson = json.get(fieldName).getAsJsonArray();

            addUsersAndTags(usersJson, users);
            return users;
        } catch (NullPointerException | IllegalStateException ex) {
            throw new MalformedJSONException("json response does not contain the requested information");
        }
    }

    /**
     * Parses a JSON Array as a map String -> List<String>.
     * @param array the given JSON array
     * @param users the map to fill
     * @throws MalformedJSONException if the given array could not be parsed as a map
     */
    private void addUsersAndTags(JsonArray array, Map<String, List<String>> users) throws MalformedJSONException {
        Objects.requireNonNull(array, "the given json array must not be null");
        Objects.requireNonNull(users, "the given field name must not be null");

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

    /**
     * Parses the information regarding a post from a JSON object.
     * @param json the given JSON object
     * @param includeInfo whether or not to read additional info, such as contents, number of upvotes/downvotes and comments
     * @return the parsed post
     * @throws MalformedJSONException if the given JSON object could not be parsed as a Post
     */
    private PostInfo getPostFromJson(JsonObject json, boolean includeInfo) throws MalformedJSONException {
        Objects.requireNonNull(json, "the given json object must not be null");

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

    /**
     * Parses the information regarding a user's Wallet from a JSON object.
     * @param json the given JSON object
     * @return the parsed wallet
     * @throws MalformedJSONException if the given JSON object could not be parsed as a Wallet
     */
    private Wallet getWalletFromJson(JsonObject json) throws MalformedJSONException {
        Objects.requireNonNull(json, "the given json object must not be null");

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
