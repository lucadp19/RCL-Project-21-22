package winsome.server;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.naming.NoInitialContextException;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.net.*;

import java.rmi.*;
import java.rmi.registry.*;
import java.rmi.server.RemoteObject;
import java.rmi.server.UnicastRemoteObject;

import winsome.api.*;
import winsome.api.codes.*;
import winsome.api.exceptions.*;
import winsome.server.datastructs.*;
import winsome.server.exceptions.*;

public class WinsomeServer extends RemoteObject implements RemoteServer {
    private class ServerPersistence {
        private String dirpath;

        public ServerPersistence(String dirpath){ 
            if(dirpath == null) throw new NullPointerException("directory path is null");
            this.dirpath = dirpath; 
        }

        public void getPersistedData(){
            if(isDataInit.get()) throw new IllegalStateException("data has already been initialized");

            File dir = new File(dirpath);
            if(!dir.exists() || !dir.isDirectory()) { setEmptyData(); return; }

            // TODO: read the files and get the persisted data
            File usersFile = new File(dir, "users.json");
            File postsFile = new File(dir, "posts.json");
            File followersFile = new File(dir, "followers.json");
            File transactionFile = new File(dir, "transactions.json");

            // if(!postsFile.exists() || !postsFile.isFile()
            //     || !followersFile.exists() || !followersFile.isFile()
            //     || !transactionFile.exists() || !transactionFile.isFile()
            // ){ setEmptyData(); return; }
            
            ConcurrentHashMap<String, User> users;
            ConcurrentHashMap<Integer, Post> posts;
            ConcurrentHashMap<String, Set<String>> followers;
            ConcurrentHashMap<String, Collection<Transaction>> transactions;

            try {
                users = parseUsers(usersFile);
                posts = new ConcurrentHashMap<>(); // TODO: parse post files
                followers = parseFollowers(followersFile);
                transactions = parseTransactions(transactionFile);
            } catch(IOException | InvalidJSONFileException ex) { ex.printStackTrace(); setEmptyData(); return; }

            WinsomeServer.this.users = users;
            WinsomeServer.this.posts = posts;
            WinsomeServer.this.following = followers;
            WinsomeServer.this.transactions = transactions;
        }

        private void setEmptyData(){
            if(!isDataInit.compareAndSet(false, true))
                throw new IllegalStateException("data has already been initialized");

            users = new ConcurrentHashMap<>();
            posts = new ConcurrentHashMap<>();
            following = new ConcurrentHashMap<>();
            transactions = new ConcurrentHashMap<>();
        }

        private ConcurrentHashMap<String, User> parseUsers(File usersFile) throws InvalidJSONFileException, IOException {
            ConcurrentHashMap<String, User> users = new ConcurrentHashMap<>();
            
            try (
                JsonReader reader = new JsonReader(new BufferedReader(new FileReader(usersFile)));
            ){
                reader.beginArray();
                while(reader.hasNext()){
                    User nextUser = User.fromJson(reader);
                    String nextUsername = nextUser.getUsername();

                    users.put(nextUsername, nextUser);
                }
                reader.endArray();
            }
            return users;
        }

        private ConcurrentHashMap<String, Set<String>> parseFollowers(File followersFile) throws InvalidJSONFileException, IOException {
            ConcurrentHashMap<String, Set<String>> followers = new ConcurrentHashMap<>();
            
            try (
                JsonReader reader = new JsonReader(new BufferedReader(new FileReader(followersFile)));
            ){
                reader.beginArray();
                while(reader.hasNext()){
                    reader.beginObject();

                    String username = reader.nextName();
                    Set<String> userFollowers = ConcurrentHashMap.newKeySet();

                    reader.beginArray();
                    while(reader.hasNext()){
                        userFollowers.add(reader.nextString());
                    }
                    reader.endArray();
                    reader.endObject();

                    followers.put(username, userFollowers);
                }
                reader.endArray();
            }
            return followers;
        }

        private ConcurrentHashMap<String, Collection<Transaction>> parseTransactions(File transactionsFile) throws InvalidJSONFileException, IOException {
            ConcurrentHashMap<String, Collection<Transaction>> transactionMap = new ConcurrentHashMap<>();
            
            try (
                JsonReader reader = new JsonReader(new BufferedReader(new FileReader(transactionsFile)));
            ){
                reader.beginArray();
                while(reader.hasNext()){
                    reader.beginObject();

                    String username = reader.nextName();
                    ConcurrentLinkedQueue<Transaction> userTrans = new ConcurrentLinkedQueue<>();

                    reader.beginArray();
                    while(reader.hasNext()){
                        userTrans.add(Transaction.fromJson(reader));
                    }
                    reader.endArray();
                    reader.endObject();

                    transactionMap.put(username, userTrans);
                }
                reader.endArray();
            }
            return transactionMap;
        }
    }

    private class Worker implements Runnable {
        JsonObject request;
        SelectionKey key;

        public Worker(JsonObject request, SelectionKey key){
            this.request = request; this.key = key; 
        }

        public void run(){
            try {
                RequestCode code;
                JsonObject response;

                try { code = RequestCode.getRequestFromJson(request); }
                catch (MalformedJSONException ex){ 
                    response = new JsonObject();
                    ResponseCode.MALFORMED_JSON_REQUEST.addResponseToJson(response);
                    send(response.toString(), key);

                    return;
                }

                switch (code) {
                    case LOGIN:
                        response = loginRequest();
                        break;
                    case LOGOUT:
                        response = logoutRequest();
                        break;
                    case GET_USERS:
                        response = getUsersRequest();
                        break;
                    default:
                        // TODO: implement things
                        response = new JsonObject();
                        ResponseCode.MALFORMED_JSON_REQUEST.addResponseToJson(response);
                }

                send(response.toString(), key);
            }
            catch(IOException ex){
                // TODO: remove user
            }
        }     
        
        /**
         * Fulfills a client's login request.
         * @return the response, formatted as a JsonObject
         */
        private JsonObject loginRequest(){
            JsonObject response = new JsonObject();

            String username = null; 
            String password = null; 
            
            // reading username and password from the request
            try {
                username = request.get("username").getAsString();
                password = request.get("password").getAsString();
            } catch (NullPointerException | ClassCastException | IllegalStateException ex ){ // no username/password => malformed Json
                ResponseCode.MALFORMED_JSON_REQUEST.addResponseToJson(response);
                return response;
            }

            List<User> following;
            List<User> followers;
            try { 
                WinsomeServer.this.login(username, password, key);
                following = WinsomeServer.this.getFollowing(username);
                followers = WinsomeServer.this.getFollowers(username);
            }
            catch (NoSuchUserException ex){ // if no user with the given username is registered
                ResponseCode.USER_NOT_REGISTERED.addResponseToJson(response);
                return response;
            }
            catch (WrongPasswordException ex){ // if the password does not match
                ResponseCode.WRONG_PASSW.addResponseToJson(response);
                return response;
            }
            catch (UserAlreadyLoggedException ex){ // if the user or the key is already logged in
                ResponseCode.ALREADY_LOGGED.addResponseToJson(response);
                return response;
            }
            
            // success!
            ResponseCode.SUCCESS.addResponseToJson(response);
            response.add("following", userTagsToJson(following));
            response.add("followers", userTagsToJson(followers));
            
            return response;
        }

        /**
         * Fulfills a client's logout request.
         * @return the response, formatted as a JsonObject
         */
        private JsonObject logoutRequest(){
            JsonObject response = new JsonObject();

            String username = null;
            
            // reading username and password from the request
            try {
                username = request.get("username").getAsString();
            } catch (NullPointerException | ClassCastException | IllegalStateException ex ){ // no username/password => malformed Json
                ResponseCode.MALFORMED_JSON_REQUEST.addResponseToJson(response);
                return response;
            }

            try { WinsomeServer.this.logout(username, key); }
            catch (NoSuchUserException ex){// if no user with the given username is registered
                ResponseCode.USER_NOT_REGISTERED.addResponseToJson(response);
                return response;
            }
            catch (NoLoggedUserException ex){ // if this client is not logged in
                ResponseCode.NO_LOGGED_USER.addResponseToJson(response);
                return response;
            }
            catch (WrongUserException ex){ // if this client is not logged in with the given user
                ResponseCode.WRONG_USER.addResponseToJson(response);
                return response;
            }
            
            // success!
            ResponseCode.SUCCESS.addResponseToJson(response);
            return response;
        }

        /**
         * Fulfills a client's "GET_USERS" request.
         * @return the response, formatted as a JsonObject
         */
        private JsonObject getUsersRequest(){
            JsonObject response = new JsonObject();

            String username = null;
            
            // reading username and password from the request
            try {
                username = request.get("username").getAsString();
            } catch (NullPointerException | ClassCastException | IllegalStateException ex ){ // no username/password => malformed Json
                ResponseCode.MALFORMED_JSON_REQUEST.addResponseToJson(response);
                return response;
            }

            List<User> visibleUsers;
            try { 
                WinsomeServer.this.checkIfLogged(username, key);
                visibleUsers = WinsomeServer.this.getVisibleUsers(username); 
            }
            catch (NoSuchUserException ex){// if no user with the given username is registered
                ResponseCode.USER_NOT_REGISTERED.addResponseToJson(response);
                return response;
            }
            catch (NoLoggedUserException ex){ // if this client is not logged in
                ResponseCode.NO_LOGGED_USER.addResponseToJson(response);
                return response;
            }
            catch (WrongUserException ex){ // if this client is not logged in with the given user
                ResponseCode.WRONG_USER.addResponseToJson(response);
                return response;
            }

            // adding users to JSON
            JsonArray usersJson = userTagsToJson(visibleUsers);
            
            // success!
            ResponseCode.SUCCESS.addResponseToJson(response);
            response.add("users", usersJson);
            return response;
        }

        private JsonArray userTagsToJson(Collection<User> users){
            // adding users to JSON
            JsonArray array = new JsonArray();
            for(User user : users){
                JsonObject toAdd = new JsonObject();
                toAdd.addProperty("username", user.getUsername());

                JsonArray tags = new JsonArray();
                for(String tag : user.getTags())
                    tags.add(tag);
                toAdd.add("tags", tags);

                array.add(toAdd);
            }

            return array;
        }
    }

    private static AtomicBoolean isDataInit = new AtomicBoolean(false);

    private ServerConfig config;
    private ServerPersistence persistenceWorker;

    private Executor pool;

    private Selector selector;
    private ServerSocketChannel socketChannel;

    private DatagramSocket multicastSocket;

    private ConcurrentMap<String, User> users;
    private ConcurrentMap<Integer, Post> posts;
    private ConcurrentMap<String, Set<String>> following;
    private ConcurrentMap<String, Collection<Transaction>> transactions;
    
    private final ConcurrentMap<String, SelectionKey> userSessions = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, RemoteClient> registeredToCallbacks = new ConcurrentHashMap<>();

    public WinsomeServer(){
        super();
    }

    public void initServer(String configPath) 
        throws NullPointerException, FileNotFoundException, 
                NumberFormatException, IOException {
        config = new ServerConfig(configPath);

        // TODO: read persisted data
        persistenceWorker = new ServerPersistence(config.getPersistenceDir());
        persistenceWorker.getPersistedData();

        pool = new ThreadPoolExecutor(
            config.getMinThreads(), config.getMaxThreads(), 
            60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>()
        );
    }

    /* **************** Connection methods **************** */

    public void startServer() throws IOException {
        // initializing socket and selector
        InetSocketAddress sockAddress = new InetSocketAddress(config.getTCPPort());
        selector = Selector.open();
        socketChannel = ServerSocketChannel.open();

        // initializing multicast socket
        multicastSocket = new DatagramSocket(config.getUDPPort());

        socketChannel.bind(sockAddress);
        socketChannel.configureBlocking(false);
        socketChannel.register(selector, SelectionKey.OP_ACCEPT);

        // RMI startup
        RemoteServer stub = (RemoteServer) UnicastRemoteObject.exportObject(this, 0);
        LocateRegistry.createRegistry(config.getRegPort());
        Registry reg = LocateRegistry.getRegistry(config.getRegPort());
        reg.rebind(config.getRegHost(), stub);
    }

    /* **************** Main Loop **************** */
    
    public void runServer() throws IOException {
        while(true){
            try { selector.select(); }
            catch(IOException ex){ throw new IOException("IO Error in select", ex); }

            Set<SelectionKey> keys = selector.selectedKeys();
            Iterator<SelectionKey> iter = keys.iterator();
            while(iter.hasNext()){
                SelectionKey key = iter.next();
                iter.remove();

                try {
                    if(key.isAcceptable()){ 
                        SocketChannel client = socketChannel.accept();
                        client.configureBlocking(false);
                        client.register(selector, SelectionKey.OP_READ, new KeyAttachment()); 

                        System.out.println("accepted client!");
                    } 
                    if(key.isReadable() && key.isValid()){
                        // TODO
                        System.out.println("Key is readable!");
                        
                        JsonObject request = null;
                        try { request = getJsonRequest(key); }
                        catch (JsonParseException | IllegalStateException ex){
                            JsonObject response = new JsonObject();
                            response.addProperty("code", ResponseCode.MALFORMED_JSON_REQUEST.toString());
                            send(response.toString(), key);
                        }
                        catch (EOFException ex){
                            endUserSession(key);
                            continue;
                        }
                        
                        pool.execute(new Worker(request, key));
                        // TODO: create a worker and insert it into a thread pool
                    }
                    
                } catch(IOException ex){
                    System.err.println("Connection closed as a result of an IO Exception.");
                    endUserSession(key);
                }
            }
        }
    }

    public void echo(SelectionKey key) throws IOException {
        String msg = receive(key);
        System.out.println("String is: " + msg);

        String ans = msg + " echoed by server";
        System.out.println("Answer is: " + ans);
        
        send(ans, key);
    }

    /* **************** Remote Methods **************** */

    public void signUp(String username, String password, Collection<String> tags) throws RemoteException, UserAlreadyExistsException {
        if(username == null || password == null || tags == null) throw new NullPointerException("null parameters in signUp method");
        for(String tag : tags) 
            if(tag == null) throw new NullPointerException("null parameters in signUp method");
        
        System.out.println("New user: \n\tUsername: " + username + "\n\tPassword: " + password + "\n\tTags: " + tags);

        User newUser = new User(username, password, tags);

        synchronized(this){ // TODO: is this the best way to synchronize things?
            if(users.putIfAbsent(username, newUser) != null)
                throw new UserAlreadyExistsException("\"" + username + "\" is not available as a new username");
            following.put(username, ConcurrentHashMap.newKeySet());
            transactions.put(username, new ConcurrentLinkedQueue<>());
        }

        System.out.println("New user: \n\tUsername: " + username + "\n\tPassword: " + password + "\n\tTags: " + tags);
    }

    public void registerForUpdates(String username, RemoteClient client) throws RemoteException, NoSuchUserException {
        if(username == null) throw new NullPointerException("null parameters while registering user in callback system");
        if(!users.containsKey(username)) throw new NoSuchUserException();

        registeredToCallbacks.putIfAbsent(username, client);
    }

    public boolean unregisterForUpdates(String username) throws RemoteException {
        if(username == null) throw new NullPointerException("null parameters while unregistering user from callback system");
        if(!users.containsKey(username)) return false;

        registeredToCallbacks.remove(username);
        return true;
    }

    /* ************** Login/logout ************** */

    public void login(String username, String password, SelectionKey client) 
            throws NullPointerException, NoSuchUserException, WrongPasswordException, UserAlreadyLoggedException {
        if(username == null || password == null || client == null) throw new NullPointerException("null parameters in login");

        KeyAttachment attachment = (KeyAttachment) client.attachment();
        User user;

        // if no user with the given username is registered
        if((user = users.get(username)) == null) { throw new NoSuchUserException("user is not signed up"); }
        // if the password does not match
        if(!user.getPassword().equals(password)){ throw new WrongPasswordException("password does not match"); }
        // if the user or the key is already logged in
        if(attachment.isLoggedIn() || WinsomeServer.this.userSessions.putIfAbsent(username, client) != null){
            throw new UserAlreadyLoggedException("user or client is already logged in");
        } 

        attachment.login(username);
    }

    public void logout(String username, SelectionKey client) throws NullPointerException, NoSuchUserException, NoLoggedUserException, WrongUserException {
        checkIfLogged(username, client);

        KeyAttachment attachment = (KeyAttachment) client.attachment();
        
        userSessions.remove(username, client);
        attachment.logout();
    }

    private void checkIfLogged(String username, SelectionKey client) throws NullPointerException, NoSuchUserException, NoLoggedUserException, WrongUserException {
        if(username == null || client == null) throw new NullPointerException("null parameters in logout");

        if(!users.containsKey(username)) throw new NoSuchUserException("user is not registered");

        KeyAttachment attachment = (KeyAttachment) client.attachment();

        if(!attachment.isLoggedIn())
            throw new NoLoggedUserException("no user is currently logged in the given client");
        if(!attachment.loggedUser().equals(username))
            throw new WrongUserException("user to logout does not correspond to the given client");
    }

    private void endUserSession(SelectionKey key) {
        KeyAttachment attachment = (KeyAttachment) key.attachment();

        if(attachment.isLoggedIn()){
            String username = attachment.loggedUser();

            userSessions.remove(username);
            registeredToCallbacks.remove(username);
            attachment.logout();
        } 

        key.cancel();
    }

    /* ************** Get users/posts ************** */

    private List<User> getVisibleUsers(String username) throws NoSuchUserException {
        if(username == null) throw new NullPointerException();
        
        User user;
        if((user = users.get(username)) == null) throw new NoSuchUserException();

        List<User> visibleUsers = new ArrayList<>();
        for(User otherUser : users.values()){
            // same user => skip
            if(otherUser.getUsername().equals(username)) continue;

            if(user.hasCommonTags(otherUser)) visibleUsers.add(otherUser);
        }

        return visibleUsers;
    }

    private List<User> getFollowing(String username) throws NoSuchUserException {
        if(username == null) throw new NullPointerException("null argument");

        Collection<String> tmp;
        List<User> followedUsers = new ArrayList<>();

        if((tmp = following.get(username)) == null)
            throw new NoSuchUserException();
        
        for(String followedUser : tmp) 
            followedUsers.add(users.get(followedUser));

        return followedUsers;
    }
    
    private List<User> getFollowers(String username) throws NoSuchUserException {
        if(username == null) throw new NullPointerException("null argument");

        List<User> ans = new ArrayList<>();
        for(Entry<String, Set<String>> entry : following.entrySet()){
            if(entry.getValue().contains(username)) 
            ans.add(users.get(entry.getKey()));
        }

        return ans;
    }

    /* ************** Send/receive methods ************** */
    private String receive(SelectionKey key) throws IOException {
        SocketChannel client = (SocketChannel) key.channel();
        ByteBuffer buf = (key.attachment() != null) ? 
            ((KeyAttachment) key.attachment()).getBuffer() :
            ByteBuffer.allocate(2048);

        // EOF
        if(client.read(buf) == -1) return null;

        // reading length of message
        while(buf.hasRemaining()) {
            int read = client.read(buf);
            if(read == 0) break;            // no more data
            if(read == -1) return null;     // EOF
        }
        buf.flip();
        int len = buf.getInt();             // length of message 
        buf.compact(); buf.flip();

        // reading message
        byte[] tmp = new byte[len];
        int pos = 0;    // next byte to read
        while(true){
            int remainingLen = len - pos;   // remaining no. of bytes to read
            if(remainingLen <= 0) break;

            // reads some data and puts it in the byte buffer
            buf.get(tmp, pos, Math.min(remainingLen, buf.remaining()));

            // advancing position of next byte to read
            pos += buf.position();
            buf.compact();  // to go in writer mode

            while(buf.hasRemaining()) {
                int read = client.read(buf);
                if(read == 0) break;
                if(read == -1) return null;
            }
            buf.flip(); // to go in reader mode again
        }

        buf.clear();
        return new String(tmp, StandardCharsets.UTF_8);
    }

    private void send(String msg, SelectionKey key) throws IOException {
        SocketChannel client = (SocketChannel) key.channel();
        ByteBuffer buf = (key.attachment() != null) ? 
            ((KeyAttachment) key.attachment()).getBuffer() :
            ByteBuffer.allocate(2048);

        // converting the message into a byte array
        byte[] tmp = msg.getBytes(StandardCharsets.UTF_8);
        int len = tmp.length;
        
        // writing the message length
        buf.putInt(len);
        // flushing (otherwise pos calculation won't work)
        buf.flip();
        while(buf.hasRemaining()) client.write(buf);
        buf.compact();

        int pos = 0;    // next byte of the message to write

        while(true){
            int remainingLen = len - pos;   // remaining no. of bytes to write
            if(remainingLen <= 0) break;

            // writing some data into the buffer
            buf.put(tmp, pos, Math.min(remainingLen, buf.remaining()));
            // updating the next byte to write
            pos += buf.position();

            // flushing
            buf.flip();
            while(buf.hasRemaining()) client.write(buf);
            buf.compact();
        }
        // final flush, just to be sure
        buf.flip();
        while(buf.hasRemaining()) client.write(buf);
        buf.clear();
    }

    private JsonObject getJsonRequest(SelectionKey key) throws IOException, JsonParseException, IllegalStateException {
        String requestStr = receive(key);

        if(requestStr == null) // EOF
            throw new EOFException("EOF reached");
        
        return JsonParser.parseString(requestStr).getAsJsonObject();
    }
}
