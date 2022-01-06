package winsome.server;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

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
import winsome.utils.configs.exceptions.InvalidConfigFileException;

/** A Server instance for the Winsome Social Network. */
public class WinsomeServer extends RemoteObject implements RemoteServer {
    /** A class for loading the Server status from data and persist the current state. */
    private class ServerPersistence implements Callable<Void> {
        /** Name of the file containing the persisted users */
        private final static String USERS_FILE = "users.json";
        /** Name of the file containing the persisted original posts */
        private final static String ORIG_POSTS_FILE = "originals.json";
        /** Name of the file containing the persisted rewins */
        private final static String REWIN_FILE = "rewins.json";
        /** Name of the file containing the persisted "following" relations */
        private final static String FOLLOWS_FILE = "follows.json";
        /** Name of the file containing the persisted transactions */
        private final static String TRANSACTIONS_FILE = "transactions.json";

        /** Directory containing the persisted data */
        private final File dir;
        /** File containing the persisted users */
        private final File usersFile;
        /** File containing the persisted original posts */
        private final File origsFile;
        /** File containing the persisted rewins */
        private final File rewinsFile;
        /** File containing the persisted "follows" relations */
        private final File followsFile;
        /** File containing the persisted transactions */
        private final File transFile;

        /** Is true if and only if this object is currently writing the persisted data to disk */
        private AtomicBoolean running = new AtomicBoolean(false);
        /** Object used for synchronization */
        public Object runningSync = new Object();

        /**
         * Creates a new ServerPersistence object.
         * @param dirpath the path to the directory that contains/will contain the persisted data
         * @throws InvalidDirectoryException if the given path does not point to an existing directory
         */
        public ServerPersistence(String dirpath) throws InvalidDirectoryException { 
            if(dirpath == null) throw new NullPointerException("directory path is null");

            // initializing directory
            dir = new File(dirpath);
            if(!dir.exists() || !dir.isDirectory()) throw new InvalidDirectoryException("the given directory does not exist");

            // initializing files
            usersFile   = new File(dir, USERS_FILE);
            origsFile   = new File(dir, ORIG_POSTS_FILE);
            rewinsFile  = new File(dir, REWIN_FILE);
            followsFile = new File(dir, FOLLOWS_FILE);
            transFile   = new File(dir, TRANSACTIONS_FILE);
        }

        /**
         * Checks whether this is currently persisting data.
         * @return true if and only if this is currently persisting data
         */
        public boolean isRunning(){ return running.get(); }

        /** 
         * Reads the persisted data from the given directory.
         * <p> 
         * On any failure, initializes the server with empty data.  
         */
        public void getPersistedData() throws FileNotFoundException, InvalidJSONFileException, IOException {
            if(isDataInit.get()) throw new IllegalStateException("data has already been initialized");
            
            ConcurrentHashMap<String, User> users;
            ConcurrentHashMap<Integer, Post> posts;
            ConcurrentHashMap<String, Set<String>> follows = new ConcurrentHashMap<>();
            ConcurrentHashMap<String, Collection<Transaction>> transactions = new ConcurrentHashMap<>();

            try {
                users = parseUsers(usersFile); // parsing users

                // initializing follows and transactions structures
                for(String username : users.keySet()){
                    follows.put(username, ConcurrentHashMap.newKeySet());
                    transactions.put(username, new ConcurrentLinkedQueue<>());
                }

                posts = parsePosts(origsFile, rewinsFile);
                follows = parseFollowers(followsFile, follows);
                transactions = parseTransactions(transFile, transactions);
            }
            catch (FileNotFoundException ex){ setEmptyData(); throw new FileNotFoundException(ex.getMessage()); } 
            catch (InvalidJSONFileException ex){ setEmptyData(); throw new InvalidJSONFileException(ex.getMessage(), ex); }
            catch(IOException ex) { setEmptyData(); throw new IOException(ex.getMessage(), ex); }

            // initializing the WinsomeServer structures
            WinsomeServer.this.users = users;
            WinsomeServer.this.posts = posts;
            WinsomeServer.this.following = follows;
            WinsomeServer.this.transactions = transactions;
        }

        /** Initializes all the structures with empty collections. */
        private void setEmptyData(){
            if(!isDataInit.compareAndSet(false, true))
                throw new IllegalStateException("data has already been initialized");

            users = new ConcurrentHashMap<>();
            posts = new ConcurrentHashMap<>();
            following = new ConcurrentHashMap<>();
            transactions = new ConcurrentHashMap<>();
        }

        /**
         * Tries to parse the file containing serialized users and returns a populated map.
         * @param usersFile the file containing the serialized users
         * @return the populated users map
         * @throws InvalidJSONFileException if the given file is not a valid JSON file
         * @throws IOException if there is an IO error while reading the file
         */
        private ConcurrentHashMap<String, User> parseUsers(File usersFile) throws InvalidJSONFileException, IOException {
            ConcurrentHashMap<String, User> users = new ConcurrentHashMap<>();
            
            try (
                JsonReader reader = new JsonReader(new BufferedReader(new FileReader(usersFile)));
            ){
                reader.beginArray();
                while(reader.hasNext()){
                    User user = User.fromJson(reader); // reading a user
                    users.put(user.getUsername(), user);
                }
                reader.endArray();
            }
            return users;
        }

        /**
         * Tries to parse the files containing original posts and rewins and returns a populated map.
         * @param originalPostsFile the file containing the serialized original posts
         * @param rewinFile the file containing the serialized rewins
         * @return the populated posts map
         * @throws InvalidJSONFileException if the given files are not valid JSON files
         * @throws IOException if some IO error occurs while reading the files
         */
        private ConcurrentHashMap<Integer, Post> parsePosts(File originalPostsFile, File rewinFile) 
                throws InvalidJSONFileException, IOException {
            ConcurrentHashMap<Integer, Post> posts = new ConcurrentHashMap<>();

            // reading posts
            try (
                JsonReader reader = new JsonReader(new BufferedReader(new FileReader(originalPostsFile)));
            ) {
                reader.beginArray();
                while(reader.hasNext()){
                    Post post = OriginalPost.fromJson(reader);
                    posts.put(post.getID(), post);
                }
                reader.endArray();
            }

            // reading rewins
            try (
                JsonReader reader = new JsonReader(new BufferedReader(new FileReader(rewinFile)))
            ) {
                reader.beginArray();
                while(reader.hasNext()){
                    JsonObject rewinJson = Rewin.getDataFromJsonReader(reader);

                    int idOriginal = Rewin.getOriginalIDFromJson(rewinJson);

                    Post orig;
                    if((orig = posts.get(idOriginal)) == null) continue; // ignoring rewins of non-existing posts

                    Post rewin = Rewin.getRewinFromJson(orig, rewinJson);
                    posts.put(rewin.getID(), rewin);
                }
                reader.endArray();
            }

            return posts;
        }
    
        /**
         * Tries to parse the file containing serialized "follows" relations and populates an already initialized map.
         * <p> The input map must be initialized with an entry for every possible existing user.
         * @param followsFile the file containing the serialized "follows"
         * @param follows the already initialized map
         * @return the populated map
         * @throws InvalidJSONFileException if the given file is not a valid JSON file
         * @throws IOException if there is an IO error while reading the file
         */
        private ConcurrentHashMap<String, Set<String>> parseFollowers(
                File followsFile, ConcurrentHashMap<String, Set<String>> follows
            ) throws InvalidJSONFileException, IOException 
        {            
            try (
                JsonReader reader = new JsonReader(new BufferedReader(new FileReader(followsFile)));
            ){
                reader.beginArray();
                while(reader.hasNext()){
                    reader.beginObject();

                    String username = reader.nextName();
                    Set<String> userFollows = null; 

                    // check that user actually exists
                    boolean skip = false;
                    if((userFollows = follows.get(username)) == null)
                        skip = true;

                    reader.beginArray();
                    while(reader.hasNext()){
                        String followed = reader.nextString();
                        if(!skip) userFollows.add(followed);
                    }
                    reader.endArray();
                    reader.endObject();

                    if(!skip) follows.put(username, userFollows);
                }
                reader.endArray();
            }
            return follows;
        }

        /**
         * Tries to parse the file containing serialized transactions and populates an already initialized map.
         * <p> The input map must be initialized with an entry for every possible existing user.
         * @param transactionsFile the file containing the serialized transactions
         * @param transactions the already initialized map
         * @return the populated transactions map
         * @throws InvalidJSONFileException if the given file is not a valid JSON file
         * @throws IOException if there is an IO error while reading the file
         */
        private ConcurrentHashMap<String, Collection<Transaction>> parseTransactions(
                File transactionsFile, ConcurrentHashMap<String, Collection<Transaction>> transactions
            ) throws InvalidJSONFileException, IOException 
        {
            try (
                JsonReader reader = new JsonReader(new BufferedReader(new FileReader(transactionsFile)));
            ){
                reader.beginArray();
                while(reader.hasNext()){
                    reader.beginObject();

                    String username = reader.nextName();
                    Collection<Transaction> userTrans = null;

                    // checking that user actually exists
                    boolean skip = false;
                    if((userTrans = transactions.get(username)) == null)
                        skip = true;

                    reader.beginArray();
                    while(reader.hasNext()){
                        Transaction transaction = Transaction.fromJson(reader);
                        if(!skip) userTrans.add(transaction);
                    }
                    reader.endArray();
                    reader.endObject();

                    if(!skip) transactions.put(username, userTrans);
                }
                reader.endArray();
            }
            return transactions;
        }

        /**
         * Runs the {@link #persistData()} method periodically.
         * @return null
         * @throws IOException if some IO error occurs while writing data
         */
        @Override
        public Void call() throws IOException {
            while(true){
                try { 
                    synchronized(runningSync) { // to synchronize with server.shutdown()
                        if(Thread.interrupted()) return null;

                        running.set(true); 
                        persistData(); 
                        running.set(false);

                        runningSync.notify();
                    }
                }
                catch(IOException ex){
                    selector.wakeup();
                    throw new IOException("IO exception while persisting data", ex);
                }

                try { TimeUnit.SECONDS.sleep(60); }
                catch (InterruptedException ex) { break; }
            }

            return null;
        }

        /**
         * Writes all the current server data into the persistence files.
         * @throws IOException if some IO error occurs while writing
         */
        private void persistData() throws IOException {
            persistTransactions(transFile);
            persistPosts(origsFile, rewinsFile);
            persistFollows(followsFile);
            persistUsers(usersFile);
        }

        /**
         * Serializes all the trasactions and writes them into a file.
         * @param transFile the destination file
         * @throws IOException if some IO error occurs while writing
         */
        private void persistTransactions(File transFile) throws IOException {
            try (
                JsonWriter writer = new JsonWriter(new BufferedWriter(new FileWriter(transFile)));
            ) {
                writer.setIndent("    ");

                writer.beginArray();
                for(Entry<String, Collection<Transaction>> entry : WinsomeServer.this.transactions.entrySet()){
                    writer.beginObject()
                        .name(entry.getKey())
                        .beginArray();

                    for(Transaction trans : entry.getValue())
                        trans.toJson(writer);

                    writer.endArray().endObject();
                }
                writer.endArray();
            }
        }

        /**
         * Serializes all the "follows" relations and writes them into a file.
         * @param followsFile the destination file
         * @throws IOException if some IO error occurs while writing
         */
        private void persistFollows(File followsFile) throws IOException {
            try (
                JsonWriter writer = new JsonWriter(new BufferedWriter(new FileWriter(followsFile)));
            ) {
                writer.setIndent("    ");

                writer.beginArray();
                for(Entry<String, Set<String>> entry : WinsomeServer.this.following.entrySet()){
                    writer.beginObject()
                          .name(entry.getKey())
                          .beginArray();

                    for(String followed : entry.getValue())
                        writer.value(followed);

                    writer.endArray()
                          .endObject();
                }
                writer.endArray();
            }
        }

        /**
         * Serializes all the posts and writes them into a file.
         * @param origsFile the destination file for original posts
         * @param rewinsFile the destination file for rewins
         * @throws IOException if some IO error occurs while writing
         */
        private void persistPosts(File origsFile, File rewinsFile) throws IOException {
            try (
                JsonWriter origsWriter = new JsonWriter(new BufferedWriter(new FileWriter(origsFile)));
                JsonWriter rewinsWriter = new JsonWriter(new BufferedWriter(new FileWriter(rewinsFile)));
            ) {
                origsWriter.setIndent("    ");
                rewinsWriter.setIndent("    ");

                origsWriter.beginArray(); rewinsWriter.beginArray();
                for(Post post : WinsomeServer.this.posts.values()){
                    if(post.isRewin()) post.toJson(rewinsWriter);
                    else post.toJson(origsWriter);
                }
                origsWriter.endArray(); rewinsWriter.endArray();
            }
        }

        /**
         * Serializes all the users and writes them into a file.
         * @param usersFile the destination file
         * @throws IOException if some IO error occurs while writing
         */
        private void persistUsers(File userFile) throws IOException {
            try (
                JsonWriter writer = new JsonWriter(new BufferedWriter(new FileWriter(usersFile)));
            ) {
                writer.setIndent("    ");

                writer.beginArray();
                for(User user : users.values())
                    user.toJson(writer);

                writer.endArray();
            }
        }
    }

    /** A Worker in the Winsome Server.
     * <p>
     * It executes a given request from a client
     * and sends back the result.
     */
    private class Worker implements Runnable {
        /** The request in JSON format */
        JsonObject request;
        /** The SelectionKey of the client who sent the request */
        SelectionKey key;

        /** Creates a new Worker object. */
        public Worker(JsonObject request, SelectionKey key){
            this.request = Objects.requireNonNull(request, "null request in worker thread"); 
            this.key = Objects.requireNonNull(key, "null client key in worker thread"); 
        }

        /**
         * Executes the request and sends the result back to the client.
         */
        @Override
        public void run(){
            JsonObject response = new JsonObject();
            try {
                try {
                    // read request code
                    RequestCode code = RequestCode.getRequestFromJson(request);

                    // switch between request types
                    response = switch (code) {
                        case MULTICAST -> multicastRequest();
                        case LOGIN -> loginRequest();
                        case LOGOUT -> logoutRequest();
                        case GET_USERS -> getUsersRequest();
                        case GET_FOLLOWING -> getFollowingRequest();
                        case FOLLOW -> followRequest();
                        case UNFOLLOW -> unfollowRequest();
                        case BLOG -> blogRequest();
                        case POST -> postRequest();
                        case FEED -> feedRequest();
                        case SHOW_POST -> showPostRequest();
                        case DELETE_POST -> deleteRequest();
                        case REWIN_POST -> rewinRequest();
                        case RATE_POST -> rateRequest();
                        case COMMENT -> commentRequest();
                        case WALLET -> walletRequest();
                        case WALLET_BTC -> walletBTCRequest();
                        default -> throw new MalformedJSONException("unknown key");
                    };
                }
                catch (MalformedJSONException ex){ // failure in parsing json
                    response = new JsonObject();
                    ResponseCode.MALFORMED_JSON_REQUEST.addResponseToJson(response);
                } 
                catch (NoSuchUserException ex){ // if no user with the given username is registered
                    ResponseCode.USER_NOT_REGISTERED.addResponseToJson(response);
                }
                catch (NoLoggedUserException ex){ // if this client is not logged in
                    ResponseCode.NO_LOGGED_USER.addResponseToJson(response);
                }
                catch (WrongUserException ex){ // if this client is not logged in with the given user
                    ResponseCode.WRONG_USER.addResponseToJson(response);
                }
                catch (UserNotVisibleException ex){ // if the given user cannot see the other user
                    ResponseCode.USER_NOT_VISIBLE.addResponseToJson(response);
                }
                
                send(response.toString(), key);
            } catch(IOException ex){ 
                endUserSession(key); // removing the user
            }
        }     

        /**
         * Fulfills a client's request for the Server Multicast Socket address and port.
         * @return the response, formatted as a JsonObject
         */
        private JsonObject multicastRequest(){
            JsonObject response = new JsonObject();
            response.addProperty("multicast-addr", config.getMulticastAddr());
            response.addProperty("multicast-port", config.getMulticastPort());
            return response;
        }
        
        /**
         * Fulfills a client's login request.
         * @return the response, formatted as a JsonObject
         * @throws MalformedJSONException if the client request was not in a valid format
         * @throws NoSuchUserException if the user to login into does not exist
         */
        private JsonObject loginRequest() throws MalformedJSONException, NoSuchUserException {
            JsonObject response = new JsonObject();

            String username, password; 
            
            // reading username and password from the request
            try {
                username = request.get("username").getAsString();
                password = request.get("password").getAsString();
            } catch (NullPointerException | ClassCastException | IllegalStateException ex ){ // no username/password => malformed Json
                throw new MalformedJSONException("request had missing fields", ex);
            }

            List<User> following, followers;
            try { 
                WinsomeServer.this.login(username, password, key);
                following = WinsomeServer.this.getFollowing(username);
                followers = WinsomeServer.this.getFollowers(username);
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
            // sending current followed/followers list to user
            response.add("following", userTagsToJson(following));
            response.add("followers", userTagsToJson(followers));
            
            return response;
        }

        /**
         * Fulfills a client's logout request.
         * @return the response, formatted as a JsonObject
         * @throws MalformedJSONException if the client request was not in a valid format
         * @throws NoSuchUserException if the requesting user does not exist
         * @throws NoLoggedUserException if the client is not currently logged in
         * @throws WrongUserException if the client is logged on a different user
         */
        private JsonObject logoutRequest() throws MalformedJSONException, NoSuchUserException, NoLoggedUserException, WrongUserException {
            JsonObject response = new JsonObject();

            String username;
            
            // reading username and password from the request
            try {
                username = request.get("username").getAsString();
            } catch (NullPointerException | ClassCastException | IllegalStateException ex ){ // no username/password => malformed Json
                throw new MalformedJSONException("request had missing fields", ex);
            }

            WinsomeServer.this.logout(username, key); 
            
            ResponseCode.SUCCESS.addResponseToJson(response);
            return response;
        }

        /**
         * Fulfills a client's "GET_USERS" request.
         * @return the response, formatted as a JsonObject
         * @throws MalformedJSONException if the client request was not in a valid format
         * @throws NoSuchUserException if the requesting user does not exist
         * @throws NoLoggedUserException if the client is not currently logged in
         * @throws WrongUserException if the client is logged on a different user
         */
        private JsonObject getUsersRequest() throws MalformedJSONException, NoSuchUserException, NoLoggedUserException, WrongUserException {
            JsonObject response = new JsonObject();

            String username;
            
            // reading username and password from the request
            try {
                username = request.get("username").getAsString();
            } catch (NullPointerException | ClassCastException | IllegalStateException ex ){ // no username/password => malformed Json
                throw new MalformedJSONException("missing fields in json request", ex);
            }

            WinsomeServer.this.checkIfLogged(username, key); // assert that the user is logged in
            List<User> visibleUsers = WinsomeServer.this.getVisibleUsers(username); 

            // adding users to JSON
            JsonArray usersJson = userTagsToJson(visibleUsers);
            
            // success!
            ResponseCode.SUCCESS.addResponseToJson(response);
            response.add("users", usersJson);
            return response;
        }

        /**
         * Fulfills a client's GET_FOLLOWING request.
         * @return the response, formatted as a JsonObject
         * @throws MalformedJSONException if the client request was not in a valid format
         * @throws NoSuchUserException if the requesting user does not exist
         * @throws NoLoggedUserException if the client is not currently logged in
         * @throws WrongUserException if the client is logged on a different user
         */
        private JsonObject getFollowingRequest() throws MalformedJSONException, NoSuchUserException, NoLoggedUserException, WrongUserException {
            JsonObject response = new JsonObject();

            String username = null; 
            
            // reading username and password from the request
            try {
                username = request.get("username").getAsString();
            } catch (NullPointerException | ClassCastException | IllegalStateException ex ){ // no username/password => malformed Json
                throw new MalformedJSONException("missing fields in json request", ex);
            }

            WinsomeServer.this.checkIfLogged(username, key);
            List<User> following = WinsomeServer.this.getFollowing(username);
            
            // success!
            ResponseCode.SUCCESS.addResponseToJson(response);
            // sending current followed/followers list to user
            response.add("following", userTagsToJson(following));

            return response;
        }

        /**
         * Fulfills a client's follow request.
         * @return the response, formatted as a JsonObject
         * @throws MalformedJSONException if the client request was not in a valid format
         * @throws NoSuchUserException if the requesting user does not exist
         * @throws NoLoggedUserException if the client is not currently logged in
         * @throws WrongUserException if the client is logged on a different user
         * @throws UserNotVisible if the client cannot see the user to follow
         */
        private JsonObject followRequest() throws MalformedJSONException, NoSuchUserException, 
                NoLoggedUserException, WrongUserException, UserNotVisibleException {
            JsonObject response = new JsonObject();

            String username, toFollow;

            // reading username and password from the request
             try {
                username = request.get("username").getAsString();
                toFollow = request.get("to-follow").getAsString();
            } catch (NullPointerException | ClassCastException | IllegalStateException ex ){ // no username/password => malformed Json
                throw new MalformedJSONException("missing fields in json request", ex);
            }

            try { 
                WinsomeServer.this.checkIfLogged(username, key);

                WinsomeServer.this.addFollower(username, toFollow); 
            }
            catch (RemoteException ex) { } // client does not care about RemoteException on followed
            catch (AlreadyFollowingException ex){ // if 'username' already follows 'toFollow'
                ResponseCode.ALREADY_FOLLOWED.addResponseToJson(response);
                return response;
            }

            // success!
            ResponseCode.SUCCESS.addResponseToJson(response);
            return response;
        }
        
        /**
         * Fulfills a client's unfollow request.
         * @return the response, formatted as a JsonObject
         * @throws MalformedJSONException if the client request was not in a valid format
         * @throws NoSuchUserException if the requesting user does not exist
         * @throws NoLoggedUserException if the client is not currently logged in
         * @throws WrongUserException if the client is logged on a different user
         * @throws UserNotVisible if the client cannot see the user to unfollow
         */
        private JsonObject unfollowRequest() throws MalformedJSONException, NoSuchUserException, 
                NoLoggedUserException, WrongUserException, UserNotVisibleException {
            JsonObject response = new JsonObject();

            String username, toUnfollow;

            // reading username and password from the request
             try {
                username = request.get("username").getAsString();
                toUnfollow = request.get("to-unfollow").getAsString();
            } catch (NullPointerException | ClassCastException | IllegalStateException ex ){ // no username/password => malformed Json
                throw new MalformedJSONException("missing fields in json request", ex);
            }

            try { 
                WinsomeServer.this.checkIfLogged(username, key);

                if(!isVisible(username, toUnfollow)) 
                    throw new UserNotVisibleException("the user to unfollow has no common tags with the requester");
                WinsomeServer.this.removeFollower(username, toUnfollow); 
            }
            catch (RemoteException ex) { } // client does not care about RemoteException on followed
            catch (NotFollowingException ex){ // if 'username' does not follow 'toUnfollow'
                ResponseCode.ALREADY_FOLLOWED.addResponseToJson(response);
                return response;
            }

            // success!
            ResponseCode.SUCCESS.addResponseToJson(response);
            return response;
        }

        /**
         * Fulfills a client's blog request.
         * @return the response, formatted as a JsonObject
         * @throws MalformedJSONException if the client request was not in a valid format
         * @throws NoSuchUserException if the requesting user does not exist
         * @throws NoLoggedUserException if the client is not currently logged in
         * @throws WrongUserException if the client is logged on a different user
         * @throws UserNotVisible if the client cannot see the user to show
         */
        private JsonObject blogRequest() 
                throws MalformedJSONException, NoSuchUserException, NoLoggedUserException, 
                    UserNotVisibleException, WrongUserException {
            JsonObject response = new JsonObject();

            String username, toView;

            // reading username from the request
             try {
                username = request.get("username").getAsString();
                toView = request.get("to-view").getAsString();
            } catch (NullPointerException | ClassCastException | IllegalStateException ex ){ // no username => malformed Json
                throw new MalformedJSONException("missing fields in json request", ex);
            }
            
            WinsomeServer.this.checkIfLogged(username, key);

            // check that the user can see the other user
            if(!isVisible(username, toView)) 
                throw new UserNotVisibleException("the user to show has no common tags with the requester");
            List<Post> posts = getPostByAuthor(toView);

            // success!
            ResponseCode.SUCCESS.addResponseToJson(response);

            JsonArray postArray = new JsonArray();
            for(Post post : posts){
                postArray.add(postToJson(post, false));
            }
            response.add("posts", postArray);

            return response;
        }

        /**
         * Fulfills a client's post request.
         * @return the response, formatted as a JsonObject
         * @throws MalformedJSONException if the client request was not in a valid format
         * @throws NoSuchUserException if the requesting user does not exist
         * @throws NoLoggedUserException if the client is not currently logged in
         * @throws WrongUserException if the client is logged on a different user
         */
        private JsonObject postRequest() throws MalformedJSONException, NoSuchUserException, NoLoggedUserException, WrongUserException {
            JsonObject response = new JsonObject();

            String username, title, content;

            // reading username and password from the request
            try {
                username = request.get("username").getAsString();
                title = request.get("title").getAsString();
                content = request.get("content").getAsString();
            } catch (NullPointerException | ClassCastException | IllegalStateException ex ){ // no username/password => malformed Json
                throw new MalformedJSONException("missing fields in json request", ex);
            }

            WinsomeServer.this.checkIfLogged(username, key);

            Post post = new OriginalPost(username, title, content);
            posts.put(post.getID(), post);
            response.addProperty("id", post.getID());

            // success!
            ResponseCode.SUCCESS.addResponseToJson(response);
            return response;
        }

        /**
         * Fulfills a client's "SHOW_FEED" request.
         * @return the response, formatted as a JsonObject
         * @throws MalformedJSONException if the client request was not in a valid format
         * @throws NoSuchUserException if the requesting user does not exist
         * @throws NoLoggedUserException if the client is not currently logged in
         * @throws WrongUserException if the client is logged on a different user
         */
        private JsonObject feedRequest() throws MalformedJSONException, NoSuchUserException, NoLoggedUserException, WrongUserException {
            JsonObject response = new JsonObject();

            String username;

            // reading username from the request
            try {
                username = request.get("username").getAsString();
            } catch (NullPointerException | ClassCastException | IllegalStateException ex ){ // no username => malformed Json
                throw new MalformedJSONException("missing fields in json request", ex);
            }
            
            WinsomeServer.this.checkIfLogged(username, key);
            List<Post> posts = getFeed(username);
            
            // success!
            ResponseCode.SUCCESS.addResponseToJson(response);

            JsonArray postArray = new JsonArray();
            for(Post post : posts){
                postArray.add(postToJson(post, false));
            }
            response.add("posts", postArray);

            return response;
        }

        /**
         * Fulfills a client's "SHOW_POST" request.
         * @return the response, formatted as a JsonObject
         * @throws MalformedJSONException if the client request was not in a valid format
         * @throws NoSuchUserException if the requesting user does not exist
         * @throws NoLoggedUserException if the client is not currently logged in
         * @throws WrongUserException if the client is logged on a different user
         */
        private JsonObject showPostRequest() throws MalformedJSONException, NoSuchUserException, NoLoggedUserException, WrongUserException {
            JsonObject response = new JsonObject();

            String username; int id;

            // reading username from the request
             try {
                username = request.get("username").getAsString();
                id = request.get("id").getAsInt();
            } catch (NullPointerException | ClassCastException | IllegalStateException ex ){ // no username => malformed Json
                throw new MalformedJSONException("missing fields in json request", ex);
            }
            
            Post post;
            try { 
                WinsomeServer.this.checkIfLogged(username, key);

                if((post = posts.get(id)) == null || !isPostVisible(username, post))
                    throw new NoSuchPostException();
            }
            catch (NoSuchPostException ex){ // the given post does not exist or it isn't visible
                ResponseCode.NO_POST.addResponseToJson(response);
                return response;
            }

            // success!
            ResponseCode.SUCCESS.addResponseToJson(response);
            response.add("post", postToJson(post, true));

            return response;
        }

        /**
         * Fulfills a client's delete request.
         * @return the response, formatted as a JsonObject
         * @throws MalformedJSONException if the client request was not in a valid format
         * @throws NoSuchUserException if the requesting user does not exist
         * @throws NoLoggedUserException if the client is not currently logged in
         * @throws WrongUserException if the client is logged on a different user
         */
        private JsonObject deleteRequest() throws MalformedJSONException, NoSuchUserException, NoLoggedUserException, WrongUserException {
            JsonObject response = new JsonObject();

            String username; int id;

            // reading username and password from the request
            try {
                username = request.get("username").getAsString();
                id = request.get("id").getAsInt();
            } catch (NullPointerException | ClassCastException | IllegalStateException ex ){ // no username/password => malformed Json
                throw new MalformedJSONException("missing fields in json request", ex);
            }

            try { 
                WinsomeServer.this.checkIfLogged(username, key);
                WinsomeServer.this.deletePost(username, id);
            }
            catch (NoSuchPostException ex){ // if no post with the given id exists
                ResponseCode.NO_POST.addResponseToJson(response);
                return response;
            }
            catch (NotPostOwnerException ex){ // if the user is now the creator of the post
                ResponseCode.NOT_POST_OWNER.addResponseToJson(response);
                return response;
            }

            // success!
            ResponseCode.SUCCESS.addResponseToJson(response);
            return response;
        }

        /**
         * Fulfills a client's rewin request.
         * @return the response, formatted as a JsonObject
         * @throws MalformedJSONException if the client request was not in a valid format
         * @throws NoSuchUserException if the requesting user does not exist
         * @throws NoLoggedUserException if the client is not currently logged in
         * @throws WrongUserException if the client is logged on a different user
         */
        private JsonObject rewinRequest() throws MalformedJSONException, NoSuchUserException, NoLoggedUserException, WrongUserException {
            JsonObject response = new JsonObject();

            String username; int id;

            // reading username and password from the request
             try {
                username = request.get("username").getAsString();
                id = request.get("id").getAsInt();
            } catch (NullPointerException | ClassCastException | IllegalStateException ex ){ // no username/password => malformed Json
                throw new MalformedJSONException("missing fields in json request", ex);
            }

            try { 
                WinsomeServer.this.checkIfLogged(username, key);
                WinsomeServer.this.rewinPost(username, id);
            }
            catch (NoSuchPostException ex){ // if no post with the given id exists
                ResponseCode.NO_POST.addResponseToJson(response);
                return response;
            }
            catch (NotFollowingException ex){
                ResponseCode.NOT_FOLLOWING.addResponseToJson(response);
                return response;
            }
            catch (RewinException ex){ // if user cannot rewin the given post
                ResponseCode.REWIN_ERR.addResponseToJson(response);
                return response;
            }

            // success!
            ResponseCode.SUCCESS.addResponseToJson(response);
            return response;
        }

        /**
         * Fulfills a client's rate request.
         * @return the response, formatted as a JsonObject
         * @throws MalformedJSONException if the client request was not in a valid format
         * @throws NoSuchUserException if the requesting user does not exist
         * @throws NoLoggedUserException if the client is not currently logged in
         * @throws WrongUserException if the client is logged on a different user
         */
        private JsonObject rateRequest() throws MalformedJSONException, NoSuchUserException, NoLoggedUserException, WrongUserException {
            JsonObject response = new JsonObject();

            String username; int id, vote;

            // reading username from the request
            try {
                username = request.get("username").getAsString();
                id = request.get("id").getAsInt();
                vote = request.get("vote").getAsInt();
            } catch (NullPointerException | ClassCastException | IllegalStateException ex ){ // no username => malformed Json
                throw new MalformedJSONException("missing fields in json request", ex);
            }
            
            try { 
                WinsomeServer.this.checkIfLogged(username, key);
                
                Post post;
                if((post = posts.get(id)) == null)
                    throw new NoSuchPostException();

                if(!canInteractWith(username, post))
                    throw new NotFollowingException("user does not follow the author of the post");
                
                if(vote == 1) post.upvote(username);
                else if(vote == -1) post.downvote(username);
                else throw new WrongVoteFormatException("vote must be +1/-1"); 
            }
            catch (NoSuchPostException ex){ // the given post does not exist or it isn't visible
                ResponseCode.NO_POST.addResponseToJson(response);
                return response;
            }
            catch (NotFollowingException ex){
                ResponseCode.NOT_FOLLOWING.addResponseToJson(response);
                return response;
            }
            catch (AlreadyVotedException ex){ // if no user with the given username is registered
                ResponseCode.ALREADY_VOTED.addResponseToJson(response);
                return response;
            }
            catch (WrongVoteFormatException ex){ // if no user with the given username is registered
                ResponseCode.WRONG_VOTE_FORMAT.addResponseToJson(response);
                return response;
            }

            // success!
            ResponseCode.SUCCESS.addResponseToJson(response);
            return response;
        }

        /**
         * Fulfills a client's comment request.
         * @return the response, formatted as a JsonObject
         * @throws MalformedJSONException if the client request was not in a valid format
         * @throws NoSuchUserException if the requesting user does not exist
         * @throws NoLoggedUserException if the client is not currently logged in
         * @throws WrongUserException if the client is logged on a different user
         */
        private JsonObject commentRequest() throws MalformedJSONException, NoSuchUserException, NoLoggedUserException, WrongUserException {
            JsonObject response = new JsonObject();

            String username, contents; int id;

            // reading username from the request
             try {
                username = request.get("username").getAsString();
                id = request.get("id").getAsInt();
                contents = request.get("comment").getAsString();
            } catch (NullPointerException | ClassCastException | IllegalStateException ex ){ // no username => malformed Json
                throw new MalformedJSONException("missing fields in json request", ex);
            }
            
            try { 
                WinsomeServer.this.checkIfLogged(username, key);
                
                Post post;
                if((post = posts.get(id)) == null)
                    throw new NoSuchPostException();

                if(!canInteractWith(username, post))
                    throw new NotFollowingException("user does not follow the author of the post");
                
                post.addComment(username, contents);
            }
            catch (NoSuchPostException ex){ // the given post does not exist or it isn't visible
                ResponseCode.NO_POST.addResponseToJson(response);
                return response;
            }
            catch (NotFollowingException ex){
                ResponseCode.NOT_FOLLOWING.addResponseToJson(response);
                return response;
            }
            catch (PostOwnerException ex){ // the given user is the owner of the post
                ResponseCode.POST_OWNER.addResponseToJson(response);
                return response;
            }

            // success!
            ResponseCode.SUCCESS.addResponseToJson(response);
            return response;
        }

        /**
         * Fulfills a client's wallet request.
         * @return the response, formatted as a JsonObject
         * @throws MalformedJSONException if the client request was not in a valid format
         * @throws NoSuchUserException if the requesting user does not exist
         * @throws NoLoggedUserException if the client is not currently logged in
         * @throws WrongUserException if the client is logged on a different user
         */
        private JsonObject walletRequest() throws MalformedJSONException, NoSuchUserException, NoLoggedUserException, WrongUserException {
            JsonObject response = new JsonObject();

            String username;

            // reading username from the request
            try {
                username = request.get("username").getAsString();
            } catch (NullPointerException | ClassCastException | IllegalStateException ex ){ // no username => malformed Json
                throw new MalformedJSONException("missing fields in json request", ex);
            }
            
            WinsomeServer.this.checkIfLogged(username, key);
            Collection<Transaction> trans = transactions.get(username); // not null because user exists and is logged

            JsonArray array = new JsonArray();
            double total = 0;
            for(Transaction transaction : trans){
                JsonObject transJson = new JsonObject();
                transJson.addProperty("increment", transaction.increment);
                transJson.addProperty("timestamp", transaction.timestamp.toString());
                array.add(transJson);

                total += transaction.increment;
            }
            response.addProperty("total", total);
            response.add("transactions", array);                       

            // success!
            ResponseCode.SUCCESS.addResponseToJson(response);
            return response;
        }

        /**
         * Fulfills a client's wallet-in-bitcoins request.
         * @return the response, formatted as a JsonObject
         * @throws MalformedJSONException if the client request was not in a valid format
         * @throws NoSuchUserException if the requesting user does not exist
         * @throws NoLoggedUserException if the client is not currently logged in
         * @throws WrongUserException if the client is logged on a different user
         */
        private JsonObject walletBTCRequest() throws MalformedJSONException, NoSuchUserException, NoLoggedUserException, WrongUserException {
            JsonObject response = new JsonObject();

            String username;

            // reading username from the request
            try {
                username = request.get("username").getAsString();
            } catch (NullPointerException | ClassCastException | IllegalStateException ex ){ // no username => malformed Json
                throw new MalformedJSONException("missing fields in json request", ex);
            }
            
            WinsomeServer.this.checkIfLogged(username, key);
            Collection<Transaction>  trans = transactions.get(username); // not null because user exists and is logged

            double total = 0; double exchange;
            for(Transaction transaction : trans)
                total += transaction.increment;

            try { exchange = getBTCExchangeRate(); }
            catch (IOException | NumberFormatException ex) { // error while getting exchange rate
                ResponseCode.EXCHANGE_RATE_ERROR.addResponseToJson(response);
                return response;
            }
            
            response.addProperty("btc-total", total * exchange);

            // success!
            ResponseCode.SUCCESS.addResponseToJson(response);
            return response;
        }

        /**
         * Transforms a collection of users into a JsonArray.
         * <p>
         * The resulting JsonArray contains objects with username
         * and tags of the users in the given collection.
         * <p>
         * This method does not serialize the user's password!
         * @param users a collection of users
         * @return the resulting json serialization
         */
        private JsonArray userTagsToJson(Collection<User> users){
            // adding users to JSON
            JsonArray array = new JsonArray();
            for(User user : users){
                JsonObject toAdd = new JsonObject();
                toAdd.addProperty("username", user.getUsername());

                // creating list of tags
                JsonArray tags = new JsonArray();
                for(String tag : user.getTags())
                    tags.add(tag);
                toAdd.add("tags", tags);

                array.add(toAdd);
            }

            return array;
        }

        /**
         * Converts a given Post to a JsonObject, adding only the information useful to a client.
         * @param post the given post
         * @param includeInfo whether or not to include the contents, votes and comments
         * @return the serialized post
         */
        private JsonObject postToJson(Post post, boolean includeInfo){
            JsonObject json = new JsonObject();
            json.addProperty("id", post.getID());
            json.addProperty("author", post.getAuthor());
            json.addProperty("title", post.getTitle());
            
            if(post.isRewin()) {
                json.addProperty("rewinner", post.getRewinner());
                json.addProperty("original-id", post.getOriginalID());
            }
            
            if(includeInfo){
                json.addProperty("contents", post.getContents());
                json.addProperty("upvotes", post.getUpvoters().size());
                json.addProperty("downvotes", post.getDownvoters().size());

                JsonArray commentsArray = new JsonArray();
                for(Comment comment : post.getComments()){
                    JsonObject obj = new JsonObject();

                    obj.addProperty("author", comment.author);
                    obj.addProperty("contents", comment.contents);

                    commentsArray.add(obj);
                }
                json.add("comments", commentsArray);
            }

            return json;
        }
    }

    private class RewardsAlgorithm implements Callable<Void> {
        private final RewardsPercentage percentage;
        private final long waitTime;

        public RewardsAlgorithm(RewardsPercentage percentage, long waitTime){
            if(percentage == null) throw new NullPointerException("null parameter");

            this.percentage = percentage;
            this.waitTime = waitTime;
        }

        public Void call() throws IOException {
            while(true) {
                if(Thread.interrupted()) return null;

                try { TimeUnit.SECONDS.sleep(waitTime); }
                catch (InterruptedException ex) {
                    return null;
                }

                for(Post post : posts.values()){
                    if(post.isRewin()) continue;

                    PostRewards rewards = ((OriginalPost) post).reapRewards();
                    if(rewards.reward == 0) continue;
                    
                    String author = post.getAuthor();
                    transactions.get(author).add(
                        new Transaction(author, rewards.authorReward(percentage))
                    );

                    for(String curator : rewards.getCurators()){
                        transactions.get(curator).add(
                            new Transaction(curator, rewards.curatorReward(percentage))
                        );
                    }
                }

                byte[] data = "Updated rewards!".getBytes();
                try {
                    InetAddress addr = InetAddress.getByName(config.getMulticastAddr());
                    int port = config.getMulticastPort();

                    DatagramPacket packet = new DatagramPacket(data, data.length, addr, port);
                    multicastSocket.send(packet);
                } catch (IOException ex){
                    selector.wakeup();
                    throw new IOException("IO error while sending 'Updated rewards!' message through multicast", ex);
                }
            }
        }
    }

    /** Boolean flag representing whether the Server data has been loaded yet or not. */
    private AtomicBoolean isDataInit = new AtomicBoolean(false);
    private AtomicBoolean isRunning  = new AtomicBoolean(false);

    /** The Server config. */
    private ServerConfig config;
    /** A Runnable object that loads and writes the Server state from/to disk. */
    private ServerPersistence persistenceWorker;

    private ExecutorService persistenceThread = Executors.newSingleThreadExecutor();
    private ExecutorService rewardsThread = Executors.newSingleThreadExecutor();
    private Future<Void> persistenceResult;
    private Future<Void> rewardsResult;

    /** The thread pool for the Worker Threads. */
    private ExecutorService pool;

    /** The channel selector */
    private Selector selector;
    /** The server socket channel */
    private ServerSocketChannel socketChannel;

    private DatagramSocket multicastSocket;

    /** Users of the Social Network, represented as a map from usernames to User objects. */
    private ConcurrentMap<String, User> users;
    /** Posts of the Social Network, represented as a map from post IDs to Post objects. */
    private ConcurrentMap<Integer, Post> posts;
    /** The 'followers' structure of the Social Network, 
     * represented as a map from usernames to a set of followed users.
     */
    private ConcurrentMap<String, Set<String>> following;
    /** The Social Network's transactions, 
     * represented as a map from usernames to collections of transactions 
     */
    private ConcurrentMap<String, Collection<Transaction>> transactions;
    
    /** The currently logged in users, represented as a map 
     * from usernames to the SelectionKey linked to the given user 
     */
    private final ConcurrentMap<String, SelectionKey> userSessions = new ConcurrentHashMap<>();
    /** The currently registered to callbacks users, represented as a map
     * from usernames to the RemoteClient that exposes the relevant callback methods.
     */
    private final ConcurrentMap<String, RemoteClient> registeredToCallbacks = new ConcurrentHashMap<>();

    /**
     * Creates the Server instance reading the parameters from the given configuration file.
     * @param configPath The path to the configuration file.
     * @throws FileNotFoundException if the configuration file does not exist
     * @throws InvalidConfigFileException if the configuration file is not valid
     * @throws IOException if some IO error occurs while reading the configuration file
     */
    public WinsomeServer(String configPath) throws FileNotFoundException, InvalidConfigFileException, IOException {
        super();

        config = new ServerConfig(Objects.requireNonNull(configPath, "config path must not be null"));
    }

    /**
     * Initializes a new instance of WinsomeServer by loading the persisted data.
     * @throws InvalidDirectoryException if the directory containing the persisted data does not exist
     * @throws FileNotFoundException if any of the persisted files do not exist
     * @throws InvalidJSONFileException if any of the persisted files are invalid
     * @throws IOException if there are errors in reading config files/persisted data
     */
    public void init() throws InvalidDirectoryException, FileNotFoundException, InvalidJSONFileException, IOException {
        persistenceWorker = new ServerPersistence(config.getPersistenceDir());
        try { persistenceWorker.getPersistedData(); }
        finally {
            int maxPostID = -1;
            for(Post post : posts.values()) {
                if(post.getID() > maxPostID) 
                    maxPostID = post.getID();
            }
            
            Post.initIDGenerator(maxPostID + 1);
        }
    }

    /**
     * Starts this server.
     * <p>
     * This method initializes and opens the TCP Socket, the Multicast Socket and the RMI stub.
     * Finally, it starts the thread pool, the Persistence Thread and the Rewards Algorithm Thread.
     * @throws IOException
     */
    public void start() throws IOException {
        // initializing socket and selector
        InetSocketAddress sockAddress = new InetSocketAddress(config.getTCPPort());
        selector = Selector.open();
        socketChannel = ServerSocketChannel.open();
        
        socketChannel.bind(sockAddress);
        socketChannel.configureBlocking(false);
        socketChannel.register(selector, SelectionKey.OP_ACCEPT);

        // initializing multicast socket
        multicastSocket = new DatagramSocket(config.getUDPPort());
        
        // RMI startup
        RemoteServer stub = (RemoteServer) UnicastRemoteObject.exportObject(this, 0);
        LocateRegistry.createRegistry(config.getRegPort());
        Registry reg = LocateRegistry.getRegistry(config.getRegPort());
        reg.rebind(config.getRegHost(), stub);

        // starting threads
        persistenceResult = persistenceThread.submit(persistenceWorker);
        rewardsResult = rewardsThread.submit(
            new RewardsAlgorithm(config.getRewardPerc(), config.getRewardInterval())
        );

        pool = new ThreadPoolExecutor(
            config.getMinThreads(), config.getMaxThreads(), 
            60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>()
        );
    }

    /* **************** Main Loop **************** */
    
    /**
     * Executes the main server loop.
     * <p>
     * The loop is as follows:
     * <ul>
     * <li> the server waits on the select call </li>
     * <li> as soon as a client request comes, the server wakes up </li>
     * <li> if the request is a connection request, the server accepts it </li>
     * <li> if it is a request from an already connected client, 
     *      the server tries to satisfy it and then returns the result to the client. </li>
     * </ul>
     * Each request is served by one of the threads in the thread pool.
     * @throws IOException
     */
    public void run() throws IOException {
        isRunning.set(true);
        while(true){
            // wait for client to wake up the server
            try { selector.select(); }
            catch(IOException ex){ throw new IOException("IO Error in select", ex); }

            if(isInterrupted()){
                shutdown(); return;
            } 

            // get the selected keys
            Set<SelectionKey> keys = selector.selectedKeys();
            Iterator<SelectionKey> iter = keys.iterator();
            while(iter.hasNext()){
                SelectionKey key = iter.next();
                iter.remove();

                try {
                    if(key.isAcceptable()){ // new connection
                        SocketChannel client = socketChannel.accept();
                        client.configureBlocking(false);
                        client.register(selector, SelectionKey.OP_READ, new KeyAttachment()); 

                        // System.out.println("accepted client!");
                    } 
                    if(key.isReadable() && key.isValid()){ // request from already connected client
                        // System.out.println("Key is readable!");
                        
                        // reading new request from client and parsing as a Json Object
                        JsonObject request = null;
                        try { request = getJsonRequest(key); }
                        catch (MalformedJSONException ex){ // parsing failed
                            JsonObject response = new JsonObject();
                            response.addProperty("code", ResponseCode.MALFORMED_JSON_REQUEST.toString());
                            send(response.toString(), key);
                            continue;
                        }
                        catch (EOFException ex){ // user closed its endpoint
                            endUserSession(key);
                            continue;
                        }
                        
                        // execute request
                        pool.execute(new Worker(request, key));
                    }
                } catch(IOException ex){ // fatal IO Exception
                    // System.err.println("Connection closed as a result of an IO Exception.");
                    endUserSession(key);
                }
            }
        }
    }

    public void close(){
        isRunning.set(false);
        selector.wakeup();
    }

    private boolean isInterrupted(){
        try {
            rewardsResult.get(1, TimeUnit.MILLISECONDS);
            persistenceResult.get(1, TimeUnit.MILLISECONDS);
        } catch (InterruptedException | ExecutionException ex) { return true; }
        catch (TimeoutException ex) { }

        if(!isRunning.get()) return true;

        return false;
    }

    private void shutdown() throws IOException {
        selector.wakeup();

        try { UnicastRemoteObject.unexportObject(this, true); }
        catch(NoSuchObjectException ex){ } // won't happen: server was exported in startServer

        // shutting down thread pool
        pool.shutdown();
        try {
            if(!pool.awaitTermination(100, TimeUnit.MILLISECONDS)) // TODO: config
                pool.shutdownNow();
        } catch (InterruptedException ex) { pool.shutdownNow(); }

        // removing all clients
        Iterator<SelectionKey> iter = selector.keys().iterator();
        while(iter.hasNext()){ iter.next().cancel(); }

        // closing sockets
        selector.close();
        multicastSocket.close();
        
        // shutting down Rewards Algorithm and Persistence
        rewardsThread.shutdownNow();
        try {
            synchronized(persistenceWorker.runningSync){
                while(persistenceWorker.isRunning())
                    persistenceWorker.wait();
                
                persistenceWorker.persistData(); 
                persistenceThread.shutdownNow();
            }
        } catch (InterruptedException ex){ persistenceThread.shutdownNow(); }
    }

    /** Test function to echo a message */ // TODO: delete it
    public void echo(SelectionKey key) throws IOException {
        String msg = receive(key);
        System.out.println("String is: " + msg);

        String ans = msg + " echoed by server";
        System.out.println("Answer is: " + ans);
        
        send(ans, key);
    }

    /* **************** Remote Methods **************** */

    @Override
    public void signUp(String username, String password, Collection<String> tags) throws RemoteException, UserAlreadyExistsException {
        if(username == null || password == null || tags == null) throw new NullPointerException("null parameters in signUp method");
        for(String tag : tags) 
            if(tag == null) throw new NullPointerException("null parameters in signUp method");
        
        User newUser = new User(username, password, tags);

        synchronized(this){ // TODO: is this the best way to synchronize things?
            following.computeIfAbsent(username, key -> ConcurrentHashMap.newKeySet());
            transactions.computeIfAbsent(username, key -> new ConcurrentLinkedQueue<>());

            if(users.putIfAbsent(username, newUser) != null)
                throw new UserAlreadyExistsException("\"" + username + "\" is not available as a new username");
        }

        // System.out.println("New user: \n\tUsername: " + username + "\n\tPassword: " + password + "\n\tTags: " + tags);
    }

    @Override
    public void registerForUpdates(String username, RemoteClient client) throws RemoteException, NoSuchUserException {
        if(username == null) throw new NullPointerException("null parameters while registering user in callback system");
        if(!users.containsKey(username)) throw new NoSuchUserException();

        registeredToCallbacks.putIfAbsent(username, client);
    }

    @Override
    public boolean unregisterForUpdates(String username) throws RemoteException {
        if(username == null) throw new NullPointerException("null parameters while unregistering user from callback system");
        if(!users.containsKey(username)) return false;

        registeredToCallbacks.remove(username);
        return true;
    }

    /* ************** Login/logout ************** */

    /**
     * Adds a user to the list of logged users.
     * @param username username of the user
     * @param password password of the user
     * @param client selection key relative to the client's connection
     * @throws NullPointerException if any of username, password or client are null
     * @throws NoSuchUserException if no user with the given username exists
     * @throws WrongPasswordException if the password does not match the saved password
     * @throws UserAlreadyLoggedException if the client is already logged in, or the user is logged on another client
     */
    private void login(String username, String password, SelectionKey client) 
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

    /**
     * Removes a user from the list of logged users.
     * @param username username of the given user
     * @param client selection key relative to the client's connection
     * @throws NullPointerException if any of username, client are null
     * @throws NoSuchUserException if no user with the given username exists
     * @throws NoLoggedUserException if the given user is not logged in
     * @throws WrongUserException if the client is logged in with another user
     */
    public void logout(String username, SelectionKey client) throws NullPointerException, NoSuchUserException, NoLoggedUserException, WrongUserException {
        checkIfLogged(username, client); // asserting that the client is actually logged in

        KeyAttachment attachment = (KeyAttachment) client.attachment();
        
        userSessions.remove(username, client);
        attachment.logout();
    }

    /**
     * Asserts that a given client is logged in on a given user; otherwise it throws some exception.
     * @param username username of the given user
     * @param client selection key relative to the client's connection
     * @throws NullPointerException if any of username, client are null
     * @throws NoSuchUserException if no user with the given username exists
     * @throws NoLoggedUserException if the given user is not logged in
     * @throws WrongUserException if the client is logged in with another user
     */
    private void checkIfLogged(String username, SelectionKey client) throws NullPointerException, NoSuchUserException, NoLoggedUserException, WrongUserException {
        // checking for nulls
        if(username == null || client == null) throw new NullPointerException("null parameters in logout");

        // checking that the user exists
        if(!users.containsKey(username)) throw new NoSuchUserException("user is not registered");


        KeyAttachment attachment = (KeyAttachment) client.attachment();
        if(!attachment.isLoggedIn()) // the client is not logged in
            throw new NoLoggedUserException("no user is currently logged in the given client");
        if(!attachment.loggedUser().equals(username)) // the client is not logged in with the given user
            throw new WrongUserException("user to logout does not correspond to the given client");
    }

    /**
     * Closes a given client's session.
     * <p>
     * If the client is logged in on some user, it is automatically logged out.
     * @param key the client's selection key
     */
    private void endUserSession(SelectionKey key) {
        KeyAttachment attachment = (KeyAttachment) key.attachment();

        if(attachment.isLoggedIn()){ // logging out the user
            String username = attachment.loggedUser();

            userSessions.remove(username);
            registeredToCallbacks.remove(username);
            attachment.logout();
        } 

        key.cancel();
    }

    /* ************** Get users/posts ************** */

    /**
     * Returns the users visible by a given user, 
     * i.e. the users with at least a common tag with the given user.
     * @param username the username of the given user
     * @return a list containing all the users who have a common tag with the given user
     * @throws NoSuchUserException if the given user does not exist
     */
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

    /**
     * Returns the users followed by the given user
     * @param username the username of the given user
     * @return a list of users followed by the given user
     * @throws NoSuchUserException if the given user does not exist
     */
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
    
    /**
     * Returns the users who follow a given user.
     * @param username the username of the given user
     * @return the followers of the given user
     * @throws NoSuchUserException if the given user does not exist
     */
    private List<User> getFollowers(String username) throws NoSuchUserException {
        if(username == null) throw new NullPointerException("null argument");

        if(!users.containsKey(username)) throw new NoSuchUserException();

        List<User> ans = new ArrayList<>();
        for(Entry<String, Set<String>> entry : following.entrySet()){
            if(entry.getValue().contains(username)) 
            ans.add(users.get(entry.getKey()));
        }

        return ans;
    }

    /**
     * Adds a new user to the followed list of a given user.
     * @param username user who filed the request
     * @param toFollow user to follow
     * @throws NullPointerException if any of username or toFollow are null
     * @throws NoSuchUserException if any of the two users do not exist
     * @throws UserNotVisibleException if the user to follow cannot be seen by the first user
     * @throws AlreadyFollowingException if 'username' already follows 'toFollow'
     * @throws RemoteException if some RemoteException happens while sending the callback to 'toFollow'
     */
    private void addFollower(String username, String toFollow) 
            throws NullPointerException, NoSuchUserException, 
                UserNotVisibleException, AlreadyFollowingException, RemoteException {
        if(username == null || toFollow == null) throw new NullPointerException("null arguments");

        Set<String> followedSet;
        User user, userToFollow;
        if((followedSet = following.get(username)) == null  // gets users followed by 'username'
                || (user = users.get(username)) == null)    // gets user with 'username' as name
            throw new NoSuchUserException("requesting user does not exist");
                
        if((userToFollow = users.get(toFollow)) == null)    // checks that 'toFollow exists'
            throw new NoSuchUserException("user to follow does not exist");

        if(!isVisible(user, userToFollow))                  // checks 'username' can see 'toFollow'
            throw new UserNotVisibleException("user to follow has no common tags with requesting user");

        if(!followedSet.add(toFollow))
            throw new AlreadyFollowingException("user already followed");
        
        RemoteClient followedClient;
        if((followedClient = registeredToCallbacks.get(toFollow)) != null)
            followedClient.addFollower(username, user.getTags());
    }
    
    /**
     * Removes an user from the followed list of a given user.
     * @param username user who filed the request
     * @param toFollow user to unfollow
     * @throws NullPointerException if any of 'username' or 'toUnfollow' are null
     * @throws NoSuchUserException if any of the two users do not exist
     * @throws UserNotVisibleException if the user to follow cannot be seen by the first user
     * @throws NotFollowingException if 'username' does not follow 'toUnfollow'
     * @throws RemoteException if some RemoteException happens while sending the callback to 'toUnfollow'
     */
    private void removeFollower(String username, String toUnfollow) 
            throws NullPointerException, NoSuchUserException, 
                UserNotVisibleException, NotFollowingException, RemoteException {
        if(username == null || toUnfollow == null) throw new NullPointerException("null arguments");

        Set<String> followedSet;
        User user, userToUnfollow;
        if((followedSet = following.get(username)) == null  // gets users followed by 'username'
                || (user = users.get(username)) == null)    // gets user with 'username' as name
            throw new NoSuchUserException("requesting user does not exist");
                
        if((userToUnfollow = users.get(toUnfollow)) == null)    // checks that 'toFollow exists'
            throw new NoSuchUserException("user to follow does not exist");

        if(!isVisible(user, userToUnfollow))                  // checks 'username' can see 'toFollow'
            throw new UserNotVisibleException("user to follow has no common tags with requesting user");
        
        if(!followedSet.remove(toUnfollow))
            throw new NotFollowingException("user already unfollowed");
        
        RemoteClient unfollowedClient;
        if((unfollowedClient = registeredToCallbacks.get(toUnfollow)) != null)
            unfollowedClient.removeFollower(username);
    }

    // -------------- Post methods --------------- //

    private List<Post> getPostByAuthor(String username) throws NoSuchUserException {
        if(username == null) throw new NullPointerException("null arguments");
        if(!users.containsKey(username)) throw new NoSuchUserException("user does not exist");

        List<Post> ans = new ArrayList<>();
        for(Post post : posts.values()){
            if((!post.isRewin() && post.getAuthor().equals(username)) || (post.isRewin() && post.getRewinner().equals(username))) 
                ans.add(post);
            
        }
        return ans;
    }

    private List<Post> getVisiblePosts(String username) throws NoSuchUserException {
        if(username == null) throw new NullPointerException("null arguments");

        User user;
        if((user = users.get(username)) == null) throw new NoSuchUserException("user does not exist");

        List<Post> ans = new ArrayList<>();
        for(Post post : posts.values()){
            if(isPostVisible(user, post))
                ans.add(post); 
        }
        return ans;
    }
    
    private List<Post> getFeed(String username) throws NoSuchUserException {
        if(username == null) throw new NullPointerException("null arguments");

        Set<String> followed;
        if((followed = following.get(username)) == null) throw new NoSuchUserException("user does not exist");

        List<Post> ans = new ArrayList<>();
        for(Post post : posts.values()){
            if(
                (post.isRewin() && followed.contains(post.getRewinner())) || // post is a visible rewin
                (!post.isRewin() && followed.contains(post.getAuthor()))     // post is a visible original post
            ) { ans.add(post); }
        }
        return ans;
    }

    private void deletePost(String username, int id) throws NoSuchUserException, NoSuchPostException, NotPostOwnerException {
        if(username == null) throw new NullPointerException();

        User user; Post post;

        if((user = users.get(username)) == null) throw new NoSuchUserException(); 
        if((post = posts.get(id)) == null) throw new NoSuchPostException();

        if(post.isRewin()){
            if(!post.getRewinner().equals(username)) throw new NotPostOwnerException();

            // synchronized with rewins
            synchronized(posts) { posts.remove(id); }
        } else {
            if(!post.getAuthor().equals(username)) throw new NotPostOwnerException();

            // synchronized with rewins
            synchronized(posts) { posts.remove(id); }
            for(Entry<Integer, Post> entry : posts.entrySet()){
                if(entry.getValue().getOriginalID() == id)
                    posts.remove(entry.getKey());
            }
        }
    }

    private void rewinPost(String username, int idPost) 
            throws NoSuchUserException, NoSuchPostException, RewinException, NotFollowingException {
        if(username == null) throw new NullPointerException();

        User user; Post post;
        if((user = users.get(username)) == null) throw new NoSuchUserException();
        if((post = posts.get(idPost)) == null) throw new NoSuchPostException();

        if(!canInteractWith(username, post))
            throw new NotFollowingException("user does not follow the author of the post");

        // synchronizing access with other rewins and with 'delete' operations
        synchronized(posts){
            if(post.getAuthor().equals(username) || post.hasRewinned(username))
                throw new RewinException("user cannot rewin post");
            Post rewin = new Rewin(post, username);

            if(posts.containsKey(idPost)) posts.put(rewin.getID(), rewin);
            else throw new NoSuchPostException();
        }
    }

    // --------------- VISIBILITY METHODS --------------- //

    /**
     * Checks whether a user is visible from another user, i.e. if they have common tags.
     * @param povName the username of the user who wants to interact with other
     * @param otherName the other user's username
     * @return true if and only if pov can see other
     * @throws NoSuchUserException if either povName or otherName is not the username of a registered user
     */
    private boolean isVisible(String povName, String otherName) throws NoSuchUserException{
        if(povName == null || otherName == null) throw new NullPointerException("null arguments");

        User povUser, otherUser;
        if((povUser = users.get(povName)) == null || (otherUser = users.get(otherName)) == null)
            throw new NoSuchUserException();

        if(povName.equals(otherName)) return true;
     
        return isVisible(povUser, otherUser);
    }
    
    /**
     * Checks whether a user is visible from another user, i.e. if they have common tags.
     * @param pov the user who wants to interact with other
     * @param otherName the other user's username
     * @return true if and only if pov can see other
     * @throws NoSuchUserException if otherName is not the username of a registered user
     */
    private boolean isVisible(User pov, String otherName) throws NoSuchUserException{
        if(pov == null || otherName == null) throw new NullPointerException("null arguments");

        User otherUser;
        if((otherUser = users.get(otherName)) == null)
            throw new NoSuchUserException();

        if(pov.getUsername().equals(otherName)) return true;
     
        return isVisible(pov, otherUser);
    }
    
    /**
     * Checks whether a user is visible from another user, i.e. if they have common tags.
     * @param povName the username of the user who wants to interact with other
     * @param other the other user
     * @return true if and only if pov can see other
     * @throws NoSuchUserException if povName is not the username of a registered user
     */
    private boolean isVisible(String povName, User other) throws NoSuchUserException {
        if(povName == null || other == null) throw new NullPointerException("null arguments");

        User povUser;
        if((povUser = users.get(povName)) == null)
            throw new NoSuchUserException();

        if(povName.equals(other.getUsername())) return true;
     
        return isVisible(povUser, other);
    }

    /**
     * Checks whether a user is visible from another user, i.e. if they have common tags.
     * @param pov the user who wants to interact with other
     * @param other the other user
     * @return true if and only if pov can see other
     */
    private boolean isVisible(User pov, User other){
        if(pov == null || other == null) throw new NullPointerException("null arguments");

        if(pov.getUsername().equals(other.getUsername())) return true;

        return pov.hasCommonTags(other);
    }
    
    /**
     * Checks if the given user can see the given post.
     * @param povName the username of the given user
     * @param idPost the ID of the post
     * @return true if and only if pov can see the post
     * @throws NoSuchUserException if povName is not the username of a registered user
     * @throws NoSuchPostException if idPost is not the ID of any post
     */
    private boolean isPostVisible(String povName, int idPost) throws NoSuchUserException, NoSuchPostException {
        if(povName == null) throw new NullPointerException("null arguments");

        User pov;
        if((pov = users.get(povName)) == null)
            throw new NoSuchUserException();
        
        return isPostVisible(pov, idPost);
    }
    
    /**
     * Checks if the given user can see the given post.
     * @param povName the username of the given user
     * @param post the post
     * @return true if and only if pov can see the post
     * @throws NoSuchUserException if povName is not the username of a registered user
     */
    private boolean isPostVisible(String povName, Post post) throws NoSuchUserException {
        if(povName == null || post == null) throw new NullPointerException("null arguments");

        User pov;
        if((pov = users.get(povName)) == null)
            throw new NoSuchUserException();
        
        return isPostVisible(pov, post);
    }

    /**
     * Checks if the given user can see the given post.
     * @param pov the given user
     * @param idPost the ID of the post
     * @return true if and only if pov can see the post
     * @throws NoSuchPostException if idPost is not the ID of any post
     */
    private boolean isPostVisible(User pov, int idPost) throws NoSuchPostException {
        if(pov == null) throw new NullPointerException("null arguments");

        Post post;
        if((post = posts.get(idPost)) == null)
            throw new NoSuchPostException();

        return isPostVisible(pov, post);
    }

    /**
     * Checks if the given user can see the given post.
     * @param pov the given user
     * @param post the given post
     * @return true if and only if pov can see the post
     */
    private boolean isPostVisible(User pov, Post post){
        if(pov == null || post == null) throw new NullPointerException("null arguments");

        try {
            // if the post is a rewin, check that pov can see the rewinner
            if(post.isRewin()) return isVisible(pov, post.getRewinner());
            // otherwise check that pov can see the original author
            else return isVisible(pov, post.getAuthor());
        } catch (NoSuchUserException ex) { throw new IllegalStateException("post author does not exist"); }
    }

    private boolean canInteractWith(String username, int idPost) throws NoSuchUserException, NoSuchPostException {
        if(username == null) throw new NullPointerException("null arguments");

        Post post;
        if((post = posts.get(idPost)) == null) throw new NoSuchPostException();        

        return canInteractWith(username, post);
    }

    private boolean canInteractWith(String username, Post post) throws NoSuchUserException {
        if(username == null || post == null) throw new NullPointerException("null arguments");
        
        Set<String> follows;
        if((follows = following.get(username)) == null) throw new NoSuchUserException();

        String author = (post.isRewin()) ? post.getRewinner() : post.getAuthor();
        return follows.contains(author);
    }

    private double getBTCExchangeRate() throws IOException, NumberFormatException {
        final String randomGenURL = "https://www.random.org/decimal-fractions/?num=1&dec=10&col=1&format=plain&rnd=new";

        try ( 
            BufferedReader in = new BufferedReader(new InputStreamReader(new URL(randomGenURL).openStream()));
        ) {
            return Double.parseDouble(in.readLine());
        }
    }

    /* ************** Send/receive methods ************** */
    /**
     * Receives a string from a given client.
     * @param key the given client
     * @return the string message sent by the client, or null if the client closed its endpoint
     * @throws IOException if there is an IO error while communicating
     */
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

    /**
     * Sends a string to a given client.
     * @param key the given client
     * @throws IOException if there is an IO error while communicating
     */
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

    /**
     * Reads a string from a client and parses it as a JsonObject.
     * @param key the client
     * @return the parsed json
     * @throws IOException if there is an error while communicating
     * @throws MalformedJSONException if the received string does not parse to a JsonObject
     */
    private JsonObject getJsonRequest(SelectionKey key) throws IOException, MalformedJSONException {
        String requestStr = receive(key);

        if(requestStr == null) // EOF
            throw new EOFException("EOF reached");
        
        try { return JsonParser.parseString(requestStr).getAsJsonObject(); }
        catch (JsonParseException | IllegalStateException ex) {
            throw new MalformedJSONException("could not parse the given message to a JsonObject");
        }
    }
}