package winsome.server;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.EOFException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.rmi.NoSuchObjectException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RemoteObject;
import java.rmi.server.UnicastRemoteObject;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import winsome.api.codes.RequestCode;
import winsome.api.codes.ResponseCode;
import winsome.api.exceptions.AlreadyFollowingException;
import winsome.api.exceptions.AlreadyRewinnedException;
import winsome.api.exceptions.AlreadyVotedException;
import winsome.api.exceptions.EmptyPasswordException;
import winsome.api.exceptions.EmptyUsernameException;
import winsome.api.exceptions.IllegalTagException;
import winsome.api.exceptions.MalformedJSONException;
import winsome.api.exceptions.NoLoggedUserException;
import winsome.api.exceptions.NoSuchPostException;
import winsome.api.exceptions.NoSuchUserException;
import winsome.api.exceptions.NotFollowingException;
import winsome.api.exceptions.NotPostOwnerException;
import winsome.api.exceptions.PostOwnerException;
import winsome.api.exceptions.TextLengthException;
import winsome.api.exceptions.UserAlreadyExistsException;
import winsome.api.exceptions.UserAlreadyLoggedException;
import winsome.api.exceptions.UserNotVisibleException;
import winsome.api.exceptions.WrongPasswordException;
import winsome.api.exceptions.WrongUserException;
import winsome.api.exceptions.WrongVoteFormatException;
import winsome.api.remote.RemoteClient;
import winsome.api.remote.RemoteServer;
import winsome.server.datastructs.Comment;
import winsome.server.datastructs.OriginalPost;
import winsome.server.datastructs.Post;
import winsome.server.datastructs.Rewin;
import winsome.server.datastructs.Transaction;
import winsome.server.datastructs.User;
import winsome.server.exceptions.InvalidDirectoryException;
import winsome.server.exceptions.InvalidJSONFileException;
import winsome.utils.configs.exceptions.InvalidConfigFileException;
import winsome.utils.cryptography.Hash;

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

        /** Time to wait between two successive iterations of the Persistence Algorithm */
        private final long waitTime;

        /**
         * Creates a new ServerPersistence object.
         * @param dirpath the path to the directory that contains/will contain the persisted data
         * @throws InvalidDirectoryException if the given path does not point to an existing directory
         */
        public ServerPersistence(String dirpath, long waitTime) throws InvalidDirectoryException { 
            if(dirpath == null) throw new NullPointerException("directory path is null");
            this.waitTime = waitTime;

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
         * If the serialized files are not found, the Server is initialized with empty data.
         * @throws FileNotFoundException if the serialized files are not found (Server initialized with empty data)
         * @throws InvalidJSONFileException if the serialized files are not valid
         * @throws IOException if some other IO error occurs
         */
        public void getPersistedData() throws FileNotFoundException, InvalidJSONFileException, IOException {
            if(isDataInit.get()) throw new IllegalStateException("data has already been initialized");
            
            ConcurrentHashMap<String, User> users;
            ConcurrentHashMap<Integer, Post> posts;
            ConcurrentHashMap<String, Set<String>> follows = new ConcurrentHashMap<>();
            ConcurrentHashMap<String, Collection<Transaction>> transactions = new ConcurrentHashMap<>();

            logger.log(Level.INFO, "Parsing JSON files containing the persisted data.");
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
            catch (FileNotFoundException ex){ 
                logger.warning("Serialized JSON Files not found: initializing the Server with empty data.");
                setEmptyData(); throw new FileNotFoundException(ex.getMessage()); 
            }

            logger.log(Level.INFO, "Serialized persisted JSON files correctly parsed.");

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
            
            logger.log(Level.INFO, "Parsing JSON file containing serialized users.");
            try (
                JsonReader reader = new JsonReader(new BufferedReader(new FileReader(usersFile)));
            ){
                reader.beginArray();
                while(reader.hasNext()){
                    logger.log(Level.FINER, "Parsing new user.");
                    User user = User.fromJson(reader); // reading a user
                    users.put(user.getUsername(), user);
                }
                reader.endArray();
            }
            logger.info("Users correctly parsed.");

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

            logger.info("Parsing JSON files containing serialized posts.");
            // reading posts
            try (
                JsonReader reader = new JsonReader(new BufferedReader(new FileReader(originalPostsFile)));
            ) {
                reader.beginArray();
                while(reader.hasNext()){
                    logger.finer("Parsing another original post.");
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
                    logger.finer("Parsing another rewin.");
                    JsonObject rewinJson = Rewin.getDataFromJsonReader(reader);

                    int idOriginal = Rewin.getOriginalIDFromJson(rewinJson);

                    Post orig;
                    if((orig = posts.get(idOriginal)) == null) { // ignoring rewins of non-existing posts
                        logger.warning("Parsed a Rewin whose Original Post does not exist: ignoring the rewin.");
                        continue;
                    }

                    Post rewin = Rewin.getRewinFromJson(orig, rewinJson);
                    posts.put(rewin.getID(), rewin);
                }
                reader.endArray();
            }

            logger.info("Correctly parsed JSON files containing posts.");
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
            logger.info("Parsing JSON file containing serialized 'follows'.");
            try (
                JsonReader reader = new JsonReader(new BufferedReader(new FileReader(followsFile)));
            ){
                reader.beginArray();
                while(reader.hasNext()){
                    reader.beginObject();

                    String username = reader.nextName();

                    logger.finer("Reading users followed by " + username + ".");
                    Set<String> userFollows = null; 

                    // check that user actually exists
                    boolean skip = false;
                    if((userFollows = follows.get(username)) == null){
                        logger.warning(
                            "Found non existent username '" + username + "' while parsing 'follows' file: ignoring this user."
                        );
                        skip = true;
                    }

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

            logger.info("Correctly parsed JSON file containing serialized 'follows'.");
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
            logger.info("Parsing JSON file containing serialized transactions.");
            try (
                JsonReader reader = new JsonReader(new BufferedReader(new FileReader(transactionsFile)));
            ){
                reader.beginArray();
                while(reader.hasNext()){
                    reader.beginObject();

                    String username = reader.nextName();

                    logger.finer("Parsing transactions pertaining to " + username + ".");
                    Collection<Transaction> userTrans = null;

                    // checking that user actually exists
                    boolean skip = false;
                    if((userTrans = transactions.get(username)) == null){
                        logger.warning(
                            "Found non existent username '" + username + "' while parsing 'transactions' file: ignoring this user."
                        );
                        skip = true;
                    }

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
            Thread.currentThread().setName("persistence-algorithm");
            logger.info("Starting persistence algorithm thread.");

            while(true){
                logger.fine("Persistence Algorithm waiting.");
                try { TimeUnit.SECONDS.sleep(waitTime); }
                catch (InterruptedException ex) { break; }

                try { 
                    logger.info("Starting persistence algorithm.");
                    synchronized(runningSync) { // to synchronize with server.shutdown()
                        if(Thread.interrupted()) return null;

                        running.set(true); 
                        persistData(); 
                        running.set(false);

                        runningSync.notify();
                    }
                    logger.info("Persistence algorithm run!");
                }
                catch(IOException ex){
                    selector.wakeup();
                    logger.log(Level.SEVERE, "IO Exception while persisting data: " + ex.getMessage(), ex);
                    throw new IOException("IO exception while persisting data", ex);
                }
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
            logger.info("Persisting transactions.");
            try (
                JsonWriter writer = new JsonWriter(new BufferedWriter(new FileWriter(transFile)));
            ) {
                writer.setIndent("    ");

                writer.beginArray();
                for(Entry<String, Collection<Transaction>> entry : WinsomeServer.this.transactions.entrySet()){
                    logger.fine("Persisting transactions pertaining to user: " + entry.getKey() + ".");
                    writer.beginObject()
                        .name(entry.getKey())
                        .beginArray();

                    for(Transaction trans : entry.getValue())
                        trans.toJson(writer);

                    writer.endArray().endObject();
                }
                writer.endArray();
            }
            logger.info("Transactions persisted.");
        }

        /**
         * Serializes all the "follows" relations and writes them into a file.
         * @param followsFile the destination file
         * @throws IOException if some IO error occurs while writing
         */
        private void persistFollows(File followsFile) throws IOException {
            logger.info("Persisting follows.");
            try (
                JsonWriter writer = new JsonWriter(new BufferedWriter(new FileWriter(followsFile)));
            ) {
                writer.setIndent("    ");

                writer.beginArray();
                for(Entry<String, Set<String>> entry : WinsomeServer.this.following.entrySet()){
                    logger.fine("Persisting follows of user: " + entry.getKey() + ".");
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
            logger.info("Follows persisted.");
        }

        /**
         * Serializes all the posts and writes them into a file.
         * @param origsFile the destination file for original posts
         * @param rewinsFile the destination file for rewins
         * @throws IOException if some IO error occurs while writing
         */
        private void persistPosts(File origsFile, File rewinsFile) throws IOException {
            logger.info("Persisting posts.");
            try (
                JsonWriter origsWriter = new JsonWriter(new BufferedWriter(new FileWriter(origsFile)));
                JsonWriter rewinsWriter = new JsonWriter(new BufferedWriter(new FileWriter(rewinsFile)));
            ) {
                origsWriter.setIndent("    ");
                rewinsWriter.setIndent("    ");

                origsWriter.beginArray(); rewinsWriter.beginArray();
                for(Post post : WinsomeServer.this.posts.values()){
                    if(post.isRewin()) { 
                        logger.finer("Persisting rewin with id " + post.getID() + "."); 
                        post.toJson(rewinsWriter); 
                    }
                    else { 
                        logger.finer("Persisting original post with id " + post.getID() + "."); 
                        post.toJson(origsWriter); 
                    }
                }
                origsWriter.endArray(); rewinsWriter.endArray();
            }
            logger.info("Posts persisted.");
        }

        /**
         * Serializes all the users and writes them into a file.
         * @param usersFile the destination file
         * @throws IOException if some IO error occurs while writing
         */
        private void persistUsers(File userFile) throws IOException {
            logger.info("Persisting users.");
            try (
                JsonWriter writer = new JsonWriter(new BufferedWriter(new FileWriter(usersFile)));
            ) {
                writer.setIndent("    ");

                writer.beginArray();
                for(User user : users.values()){
                    logger.finer("Persisting user " + user.getUsername() + ".");
                    user.toJson(writer);
                }

                writer.endArray();
            }
            logger.info("Users persisted.");
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
            Thread.currentThread().setName(
                "WinsomeWorker-" + Thread.currentThread().getId()
            );

            JsonObject response = new JsonObject();
            try {
                logger.info("Fulfilling a client's request.");
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
                    logger.info(
                        "Client request fail. Error code: " + ResponseCode.MALFORMED_JSON_REQUEST.toString() + " (" +
                        ResponseCode.MALFORMED_JSON_REQUEST.getMessage() +
                        ")."
                    );
                } 
                catch (NoSuchUserException ex){ // if no user with the given username is registered
                    response = new JsonObject();
                    ResponseCode.USER_NOT_REGISTERED.addResponseToJson(response);
                    logger.info(
                        "Client request fail. Error code: " + ResponseCode.USER_NOT_REGISTERED.toString() + " (" +
                        ResponseCode.USER_NOT_REGISTERED.getMessage() +
                        ")."
                    );
                }
                catch (NoLoggedUserException ex){ // if this client is not logged in
                    response = new JsonObject();
                    ResponseCode.NO_LOGGED_USER.addResponseToJson(response);
                    logger.info(
                        "Client request fail. Error code: " + ResponseCode.NO_LOGGED_USER.toString() + " (" +
                        ResponseCode.NO_LOGGED_USER.getMessage() +
                        ")."
                    );
                }
                catch (WrongUserException ex){ // if this client is not logged in with the given user
                    response = new JsonObject();
                    ResponseCode.WRONG_USER.addResponseToJson(response);
                    logger.info(
                        "Client request fail. Error code: " + ResponseCode.WRONG_USER.toString() + " (" +
                        ResponseCode.WRONG_USER.getMessage() +
                        ")."
                    );
                }
                catch (UserNotVisibleException ex){ // if the given user cannot see the other user
                    response = new JsonObject();
                    ResponseCode.USER_NOT_VISIBLE.addResponseToJson(response);
                    logger.info(
                        "Client request fail. Error code: " + ResponseCode.USER_NOT_VISIBLE.toString() + " (" +
                        ResponseCode.USER_NOT_VISIBLE.getMessage() +
                        ")."
                    );
                }
                
                logger.info("Sending response to client.");
                send(response.toString(), key);
            } catch(IOException ex){ 
                logger.log(Level.WARNING, "IO exception while fulfilling a client's request: " + ex.getMessage(), ex);
                logger.warning("Removing client.");
                endUserSession(key); // removing the user
            }
        }     

        /**
         * Fulfills a client's request for the Server Multicast Socket address and port.
         * @return the response, formatted as a JsonObject
         */
        private JsonObject multicastRequest(){
            logger.info("Fulfilling client's MULTICAST request.");
        
            JsonObject response = new JsonObject();
            response.addProperty("multicast-addr", config.multicastAddr);
            response.addProperty("multicast-port", config.multicastPort);

            logger.info("Client request fulfilled.");
            return response;
        }
        
        /**
         * Fulfills a client's login request.
         * @return the response, formatted as a JsonObject
         * @throws MalformedJSONException if the client request was not in a valid format
         * @throws NoSuchUserException if the user to login into does not exist
         */
        private JsonObject loginRequest() throws MalformedJSONException, NoSuchUserException {
            logger.info("Fulfilling client's LOGIN request.");
            JsonObject response = new JsonObject();

            String username, password; 
            
            // reading username and password from the request
            try {
                username = request.get("username").getAsString();
                password = request.get("password").getAsString();
            } catch (NullPointerException | ClassCastException | IllegalStateException ex){
                throw new MalformedJSONException("request had missing fields", ex);
            }

            // logging in
            try { WinsomeServer.this.login(username, password, key); }
            catch (WrongPasswordException ex){ // if the password does not match
                ResponseCode.WRONG_PASSW.addResponseToJson(response);
                logger.info("Client request failed with error code " + ResponseCode.WRONG_PASSW +
                    " (" + ResponseCode.WRONG_PASSW.getMessage() + ")."
                );
                return response;
            }
            catch (UserAlreadyLoggedException ex){ // if the user or the key is already logged in
                ResponseCode.ALREADY_LOGGED.addResponseToJson(response);
                logger.info("Client request failed with error code " + ResponseCode.ALREADY_LOGGED +
                    " (" + ResponseCode.ALREADY_LOGGED.getMessage() + ")."
                );
                return response;
            }

            // getting followers
            List<User> followers = WinsomeServer.this.getFollowers(username);
            
            // success!
            ResponseCode.SUCCESS.addResponseToJson(response);
            // sending current follower list to user
            response.add("followers", userTagsToJson(followers));
            
            logger.info("Client request fulfilled.");
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
            logger.info("Fulfilling a client's LOGOUT request.");
            JsonObject response = new JsonObject();

            String username;
            
            // reading username and password from the request
            try {
                username = request.get("username").getAsString();
            } catch (NullPointerException | ClassCastException | IllegalStateException ex ){
                throw new MalformedJSONException("request had missing fields", ex);
            }

            WinsomeServer.this.logout(username, key); 
            
            ResponseCode.SUCCESS.addResponseToJson(response);
            logger.info("Client request fulfilled.");
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
            logger.info("Fulfilling a client's GET_USERS request.");
            JsonObject response = new JsonObject();

            String username;
            
            // reading username and password from the request
            try {
                username = request.get("username").getAsString();
            } catch (NullPointerException | ClassCastException | IllegalStateException ex ){
                throw new MalformedJSONException("missing fields in json request", ex);
            }

            WinsomeServer.this.checkIfLogged(username, key); // assert that the user is logged in
            List<User> visibleUsers = WinsomeServer.this.getVisibleUsers(username); 

            // adding users to JSON
            JsonArray usersJson = userTagsToJson(visibleUsers);
            
            // success!
            ResponseCode.SUCCESS.addResponseToJson(response);
            response.add("users", usersJson);
            logger.info("Client request fulfilled.");
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
            logger.info("Fulfilling a client's GET_FOLLOWING request.");
            JsonObject response = new JsonObject();

            String username = null; 
            
            // reading username and password from the request
            try {
                username = request.get("username").getAsString();
            } catch (NullPointerException | ClassCastException | IllegalStateException ex ){
                throw new MalformedJSONException("missing fields in json request", ex);
            }

            WinsomeServer.this.checkIfLogged(username, key);

            // getting following list
            List<User> following = WinsomeServer.this.getFollowing(username);
            
            // success!
            ResponseCode.SUCCESS.addResponseToJson(response);
            // sending current followed/followers list to user
            response.add("following", userTagsToJson(following));

            logger.info("Client request fulfilled.");
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
            logger.info("Fulfilling a client's FOLLOW request.");
            JsonObject response = new JsonObject();

            String username, toFollow;

            // reading username and password from the request
             try {
                username = request.get("username").getAsString();
                toFollow = request.get("to-follow").getAsString();
            } catch (NullPointerException | ClassCastException | IllegalStateException ex ){
                throw new MalformedJSONException("missing fields in json request", ex);
            }

            WinsomeServer.this.checkIfLogged(username, key);

            if(username.equals(toFollow)) {
                logger.info("Client request failed with error code " + ResponseCode.SELF_FOLLOW +
                    " (users cannot follow themselves)."
                );
                ResponseCode.SELF_FOLLOW.addResponseToJson(response); return response;
            }

            // adding follower
            try { WinsomeServer.this.addFollower(username, toFollow); }
            catch (AlreadyFollowingException ex){ // if 'username' already follows 'toFollow'
                logger.info("Client request failed with error code " + ResponseCode.ALREADY_FOLLOWED +
                    " (user already follows the other user)."
                );
                ResponseCode.ALREADY_FOLLOWED.addResponseToJson(response);
                return response;
            }

            // success!
            ResponseCode.SUCCESS.addResponseToJson(response);
            logger.info("Client request fulfilled.");
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
            logger.info("Fulfilling a client's UNFOLLOW request.");
            JsonObject response = new JsonObject();

            String username, toUnfollow;

            // reading username and password from the request
             try {
                username = request.get("username").getAsString();
                toUnfollow = request.get("to-unfollow").getAsString();
            } catch (NullPointerException | ClassCastException | IllegalStateException ex ){
                throw new MalformedJSONException("missing fields in json request", ex);
            }

            WinsomeServer.this.checkIfLogged(username, key);

            if(username.equals(toUnfollow)) {
                logger.info("Client request failed with error code " + ResponseCode.SELF_FOLLOW +
                    " (users cannot unfollow themselves)."
                );
                ResponseCode.SELF_FOLLOW.addResponseToJson(response); return response;
            }

            // removing follower
            try { WinsomeServer.this.removeFollower(username, toUnfollow); }
            catch (NotFollowingException ex){ // if 'username' does not follow 'toUnfollow'
                logger.info("Client request failed with error code " + ResponseCode.NOT_FOLLOWING +
                    " (user does not follow the user to unfollow)."
                );
                ResponseCode.NOT_FOLLOWING.addResponseToJson(response);
                return response;
            }

            // success!
            ResponseCode.SUCCESS.addResponseToJson(response);
            logger.info("Client request fulfilled.");
            return response;
        }

        /**
         * Fulfills a client's blog request.
         * @return the response, formatted as a JsonObject
         * @throws MalformedJSONException if the client request was not in a valid format
         * @throws NoSuchUserException if the requesting user does not exist
         * @throws NoLoggedUserException if the client is not currently logged in
         * @throws WrongUserException if the client is logged on a different user
         */
        private JsonObject blogRequest() 
                throws MalformedJSONException, NoSuchUserException, NoLoggedUserException, WrongUserException {
            logger.info("Fulfilling a client's BLOG request.");
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
            if(!isVisible(username, toView)) {
                logger.info("Client request failed with error code " + ResponseCode.USER_NOT_VISIBLE +
                    " (user cannot see the blog of the other user)."
                );
                ResponseCode.USER_NOT_VISIBLE.addResponseToJson(response); return response;
            }
            // getting posts
            List<Post> posts = getPostByAuthor(toView);

            // success!
            ResponseCode.SUCCESS.addResponseToJson(response);

            JsonArray postArray = new JsonArray();
            for(Post post : posts)
                postArray.add(postToJson(post, false));
            response.add("posts", postArray);

            logger.info("Client request fulfilled.");
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
            logger.info("Fulfilling a client's POST request.");
            JsonObject response = new JsonObject();

            String username, title, content;

            // reading username and password from the request
            try {
                username = request.get("username").getAsString();
                title = request.get("title").getAsString();
                content = request.get("content").getAsString();
            } catch (NullPointerException | ClassCastException | IllegalStateException ex ){
                throw new MalformedJSONException("missing fields in json request", ex);
            }

            WinsomeServer.this.checkIfLogged(username, key);

            // creating and adding new post
            try {
                Post post = new OriginalPost(username, title, content);
                posts.put(post.getID(), post);
                response.addProperty("id", post.getID());
            } catch (TextLengthException ex) {
                logger.info("Client request failed with error code " + ResponseCode.TEXT_LENGTH +
                    " (text length exceeded maximum limits)."
                );
                ResponseCode.TEXT_LENGTH.addResponseToJson(response); return response;
            }

            // success!
            ResponseCode.SUCCESS.addResponseToJson(response);
            logger.info("Client request fulfilled.");
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
            logger.info("Fulfilling a client's GET_FEED request");
            JsonObject response = new JsonObject();

            String username;

            // reading username from the request
            try { username = request.get("username").getAsString(); }
            catch (NullPointerException | ClassCastException | IllegalStateException ex ){
                throw new MalformedJSONException("missing fields in json request", ex);
            }
            
            WinsomeServer.this.checkIfLogged(username, key);

            // getting feed
            List<Post> posts = getFeed(username);
            
            // success!
            ResponseCode.SUCCESS.addResponseToJson(response);

            JsonArray postArray = new JsonArray();
            for(Post post : posts){
                postArray.add(postToJson(post, false));
            }
            response.add("posts", postArray);

            logger.info("Client request fulfilled.");
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
            logger.info("Fulfilling a client's SHOW_POST request.");
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
            WinsomeServer.this.checkIfLogged(username, key);
            if((post = posts.get(id)) == null || !isPostVisible(username, post)) {
                logger.info("Client request failed with error code " + ResponseCode.NO_POST +
                    " (post does not exist or user cannot see post)."
                );
                ResponseCode.NO_POST.addResponseToJson(response); return response;
            }

            // success!
            ResponseCode.SUCCESS.addResponseToJson(response);
            response.add("post", postToJson(post, true));

            logger.info("Client request fulfilled.");
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
            logger.info("Fulfilling a client's DELETE request.");
            JsonObject response = new JsonObject();

            String username; int id;

            // reading username and password from the request
            try {
                username = request.get("username").getAsString();
                id = request.get("id").getAsInt();
            } catch (NullPointerException | ClassCastException | IllegalStateException ex ){
                throw new MalformedJSONException("missing fields in json request", ex);
            }

            WinsomeServer.this.checkIfLogged(username, key);

            // deleting post
            try { WinsomeServer.this.deletePost(username, id); }
            catch (NoSuchPostException ex){ // if no post with the given id exists
                logger.info("Client request failed with error code " + ResponseCode.NO_POST +
                    " (post does not exist or user cannot see it)."
                );
                ResponseCode.NO_POST.addResponseToJson(response);
                return response;
            }
            catch (NotPostOwnerException ex){ // if the user is not the creator of the post
                logger.info("Client request failed with error code " + ResponseCode.POST_OWNER +
                    " (user cannot delete other user's posts)."
                );
                ResponseCode.NOT_POST_OWNER.addResponseToJson(response);
                return response;
            }

            // success!
            ResponseCode.SUCCESS.addResponseToJson(response);
            logger.info("Client request fulfilled.");
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
            logger.info("Fulfilling a client's REWIN request.");
            JsonObject response = new JsonObject();

            String username; int id;

            // reading username and password from the request
             try {
                username = request.get("username").getAsString();
                id = request.get("id").getAsInt();
            } catch (NullPointerException | ClassCastException | IllegalStateException ex ){
                throw new MalformedJSONException("missing fields in json request", ex);
            }

            WinsomeServer.this.checkIfLogged(username, key);

            // rewinning post
            try { WinsomeServer.this.rewinPost(username, id); }
            catch (NoSuchPostException ex){ // if no post with the given id exists
                logger.info("Client request failed with error code " + ResponseCode.NO_POST +
                    " (post does not exist or user cannot see it)."
                );
                ResponseCode.NO_POST.addResponseToJson(response);
                return response;
            }
            catch (NotFollowingException ex){ // user does not follow the owner of the post to rewin
                logger.info("Client request failed with error code " + ResponseCode.NOT_FOLLOWING +
                    " (user cannot interact with not followed users)."
                );
                ResponseCode.NOT_FOLLOWING.addResponseToJson(response);
                return response;
            }
            catch (PostOwnerException ex){ // if user is the author of the given post
                logger.info("Client request failed with error code " + ResponseCode.POST_OWNER +
                    " (user cannot rewin their own posts)."
                );
                ResponseCode.POST_OWNER.addResponseToJson(response);
                return response;
            }
            catch (AlreadyRewinnedException ex){ // if user has already rewinned the given post
                logger.info("Client request failed with error code " + ResponseCode.REWIN_ERR +
                    " (user has already rewinned this post)."
                );
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
            logger.info("Fulfilling a client's RATE request.");
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
            
            WinsomeServer.this.checkIfLogged(username, key);
            
            Post post;
            if((post = posts.get(id)) == null){
                logger.info("Client request failed with error code " + ResponseCode.NO_POST +
                    " (post does not exist or user cannot see it)."
                );
                ResponseCode.NO_POST.addResponseToJson(response); return response;
            }

            if(post.getAuthor().equals(username)){
                logger.info("Client request failed with error code " + ResponseCode.POST_OWNER +
                    " (user cannot rate their own post)."
                );
                ResponseCode.POST_OWNER.addResponseToJson(response); return response;
            }
            
            // checking that user follows the author of the post
            if(!canInteractWith(username, post)) {
                logger.info("Client request failed with error code " + ResponseCode.NOT_FOLLOWING +
                    " (user cannot interact with not followed users)."
                );
                ResponseCode.NOT_FOLLOWING.addResponseToJson(response); return response;
            }
            
            // upvoting post
            try { 
                if(vote == 1) post.upvote(username);
                else if(vote == -1) post.downvote(username);
                else throw new WrongVoteFormatException("vote must be +1/-1"); 
            }
            catch (AlreadyVotedException ex){
                logger.info("Client request failed with error code " + ResponseCode.ALREADY_VOTED +
                    " (user had already voted the given post)."
                );
                ResponseCode.ALREADY_VOTED.addResponseToJson(response);
                return response;
            }
            catch (WrongVoteFormatException ex){
                logger.info("Client request failed with error code " + ResponseCode.WRONG_VOTE_FORMAT +
                    " (vote was not in the correct format)."
                );
                ResponseCode.WRONG_VOTE_FORMAT.addResponseToJson(response);
                return response;
            }

            // success!
            ResponseCode.SUCCESS.addResponseToJson(response);
            logger.info("Client request fulfilled.");
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
            logger.info("Fulfilling a client's COMMENT request.");
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
            
            WinsomeServer.this.checkIfLogged(username, key);
            
            Post post;
            if((post = posts.get(id)) == null){
                logger.info("Client request failed with error code " + ResponseCode.NO_POST +
                    " (post does not exist or user cannot see it)."
                );
                ResponseCode.NO_POST.addResponseToJson(response); return response;
            }

            // checking that user follows the author of the post
            if(!canInteractWith(username, post)) {
                logger.info("Client request failed with error code " + ResponseCode.NOT_FOLLOWING +
                    " (user cannot interact with not followed users)."
                );
                ResponseCode.NOT_FOLLOWING.addResponseToJson(response); return response;
            }
            
            // adding comment
            try { post.addComment(username, contents); }
            catch (PostOwnerException ex){ // the given user is the owner of the post
                logger.info("Client request failed with error code " + ResponseCode.POST_OWNER +
                    " (user cannot rate their own post)."
                );
                ResponseCode.POST_OWNER.addResponseToJson(response);
                return response;
            }

            // success!
            ResponseCode.SUCCESS.addResponseToJson(response);
            logger.info("Client request fulfilled.");
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
            logger.info("Fulfilling a client's WALLET request.");
            JsonObject response = new JsonObject();

            String username;

            // reading username from the request
            try {
                username = request.get("username").getAsString();
            } catch (NullPointerException | ClassCastException | IllegalStateException ex ){ // no username => malformed Json
                throw new MalformedJSONException("missing fields in json request", ex);
            }
            
            WinsomeServer.this.checkIfLogged(username, key);

            // getting transactions
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
            
            logger.info("Client request fulfilled.");
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
            logger.info("Fulfilling a client's WALLET_BTC request.");
            JsonObject response = new JsonObject();

            String username;

            // reading username from the request
            try {
                username = request.get("username").getAsString();
            } catch (NullPointerException | ClassCastException | IllegalStateException ex ){ // no username => malformed Json
                throw new MalformedJSONException("missing fields in json request", ex);
            }
            
            WinsomeServer.this.checkIfLogged(username, key);

            // getting transactions
            Collection<Transaction> trans = transactions.get(username); // not null because user exists and is logged

            double total = 0; double exchange;
            for(Transaction transaction : trans)
                total += transaction.increment;

            try { exchange = getBTCExchangeRate(); }
            catch (IOException | NumberFormatException ex) { // error while getting exchange rate
                logger.info("Client request failed with error code " + ResponseCode.EXCHANGE_RATE_ERROR +
                    " (" + ResponseCode.EXCHANGE_RATE_ERROR.getMessage() + ")."
                );
                ResponseCode.EXCHANGE_RATE_ERROR.addResponseToJson(response);
                return response;
            }
            
            response.addProperty("btc-total", total * exchange);

            // success!
            ResponseCode.SUCCESS.addResponseToJson(response);
            logger.info("Client request fulfilled.");
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

    /** Calculates asynchronously the rewards for each post. */
    private class RewardsAlgorithm implements Callable<Void> {
        /** The percentage of the reward going to the author/curator */
        private final RewardsPercentage percentage;
        /** The waiting time betweet two iterations */
        private final long waitTime;

        /** Initializes the algorithm */
        public RewardsAlgorithm(RewardsPercentage percentage, long waitTime){
            if(percentage == null) throw new NullPointerException("null parameter");

            this.percentage = percentage;
            this.waitTime = waitTime;
        }

        /**
         * Computes periodically the new rewards and notifies clients.
         * @return null
         * @throws IOException if some IO error occurs while notifying clients
         */
        @Override
        public Void call() throws IOException {
            Thread.currentThread().setName("rewards-algorithm");
            logger.info("Starting Rewards Algorithm thread.");

            while(true) {
                if(Thread.interrupted()) return null;

                logger.fine("Rewards Algorithm waiting");
                try { TimeUnit.SECONDS.sleep(waitTime); }
                catch (InterruptedException ex) {
                    return null;
                }

                // computing rewards
                logger.info("Rewards Algorithm running.");
                for(Post post : posts.values()){
                    if(post.isRewin()) continue;

                    PostRewards rewards = ((OriginalPost) post).reapRewards();
                    logger.fine("Rewards of post with ID " + post.getID() + " is " + rewards.reward + ".");
                    if(rewards.reward == 0) continue;
                    
                    // adding transaction for author
                    String author = post.getAuthor();
                    logger.fine("Adding transaction for author of post with ID " + post.getID() + ".");
                    transactions.get(author).add(
                        new Transaction(author, rewards.authorReward(percentage))
                    );

                    // adding transactions for curators
                    for(String curator : rewards.getCurators()){
                        logger.fine("Adding transaction for curator " + curator + " of post with ID " + post.getID() + ".");
                        transactions.get(curator).add(
                            new Transaction(curator, rewards.curatorReward(percentage))
                        );
                    }
                }
                logger.info("Computed rewards for every post. Sending notification through Multicast.");

                // getting current date/time
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

                // notifying clients
                byte[] data = ("Updated rewards! (" + formatter.format(LocalDateTime.now()) + ")").getBytes();
                try {
                    InetAddress addr = InetAddress.getByName(config.multicastAddr);
                    int port = config.multicastPort;

                    DatagramPacket packet = new DatagramPacket(data, data.length, addr, port);
                    multicastSocket.send(packet);
                    logger.info("Notification sent.");
                } catch (IOException ex){
                    selector.wakeup();
                    logger.log(
                        Level.SEVERE,
                        "IO error while sending notification through multicast: " + ex.getMessage(),
                        ex
                    );
                    throw new IOException("IO error while sending 'Updated rewards!' message through multicast", ex);
                }
            }
        }
    }

    /** The server logger */
    Logger logger = Logger.getLogger("Winsome-Server");

    /** Boolean flag representing whether the Server data has been loaded yet or not. */
    private AtomicBoolean isDataInit = new AtomicBoolean(false);
    /** Boolean flag representin whether the server is currently running */
    private AtomicBoolean isRunning  = new AtomicBoolean(false);

    /** The Server config. */
    private ServerConfig config;
    /** A Runnable object that loads and writes the Server state from/to disk. */
    private ServerPersistence persistenceWorker;

    /** Thread executing the persistence worker */
    private ExecutorService persistenceThread = Executors.newSingleThreadExecutor();
    /** Result of the persistence worker (to check that no exceptions have been thrown) */
    private Future<Void> persistenceResult;
    /** Thread executing the Rewards Algorithm */
    private ExecutorService rewardsThread = Executors.newSingleThreadExecutor();
    /** Result of the Rewards Algorithm (to check that no exceptions have been thrown) */
    private Future<Void> rewardsResult;

    /** The thread pool for the Worker Threads. */
    private ExecutorService pool;

    /** The channel selector */
    private Selector selector;
    /** The server socket channel */
    private ServerSocketChannel socketChannel;

    /** The multicast socket to notify clients of new rewards */
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

        config = ServerConfig.fromConfigFile(Objects.requireNonNull(configPath, "config path must not be null"));
    }

    /**
     * Initializes a new instance of WinsomeServer by loading the persisted data.
     * @throws InvalidDirectoryException if the directory containing the persisted data does not exist
     * @throws FileNotFoundException if any of the persisted files do not exist
     * @throws InvalidJSONFileException if any of the persisted files are invalid
     * @throws IOException if there are errors in reading config files/persisted data
     */
    public void init() throws InvalidDirectoryException, FileNotFoundException, InvalidJSONFileException, IOException {
        logger.info("Initializing data.");
        
        persistenceWorker = new ServerPersistence(config.persistenceDir, config.persistenceInterval);
        try { persistenceWorker.getPersistedData(); }
        finally {
            logger.info("Getting max post ID.");
            // getting max ID
            int maxPostID = 
                posts.values().stream()
                    .mapToInt(post -> post.getID())
                    .max().orElse(-1);
            
            // initializing the ID generator for posts
            logger.info("Initializing post ID Generator at " + (maxPostID + 1) + ".");
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
        logger.info("Opening TCP socket on port " + config.portTCP);
        InetSocketAddress sockAddress = new InetSocketAddress(config.portTCP);
        selector = Selector.open();
        socketChannel = ServerSocketChannel.open();
        
        socketChannel.bind(sockAddress);
        socketChannel.configureBlocking(false);
        socketChannel.register(selector, SelectionKey.OP_ACCEPT);

        // initializing multicast socket
        logger.info("Opening multicast socket on port " + config.portUDP);
        multicastSocket = new DatagramSocket(config.portUDP);
        
        // RMI startup
        logger.info("Starting up RMI Registry.");
        RemoteServer stub = (RemoteServer) UnicastRemoteObject.exportObject(this, 0);
        LocateRegistry.createRegistry(config.regPort);
        Registry reg = LocateRegistry.getRegistry(config.regPort);
        reg.rebind(config.regName, stub);

        // starting threads
        logger.info("Starting Persistence/Rewards Algorithms.");
        persistenceResult = persistenceThread.submit(persistenceWorker);
        rewardsResult = rewardsThread.submit(
            new RewardsAlgorithm(config.percentage, config.rewardInterval)
        );

        logger.info("Starting Worker pool.");
        pool = new ThreadPoolExecutor(
            config.minThreads, config.maxThreads, 
            config.keepAlive, TimeUnit.SECONDS, new LinkedBlockingQueue<>()
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
        logger.info("Running main server loop.");
        isRunning.set(true);
        while(true){
            // wait for client to wake up the server
            logger.info("Waiting on select.");
            try { selector.select(); }
            catch(IOException ex){ throw new IOException("IO Error in select", ex); }

            if(isInterrupted()){
                shutdown(); return;
            } 

            logger.info("Server woken up by a new request.");
            // get the selected keys
            Set<SelectionKey> keys = selector.selectedKeys();
            Iterator<SelectionKey> iter = keys.iterator();
            // iterate on keys
            while(iter.hasNext()){
                SelectionKey key = iter.next();
                iter.remove();

                try {
                    if(key.isAcceptable()){ // new connection
                        SocketChannel client = socketChannel.accept();
                        client.configureBlocking(false);
                        client.register(selector, SelectionKey.OP_READ, new KeyAttachment()); 
                        logger.info("Accepted new client.");
                    } 
                    if(key.isReadable() && key.isValid()){ // request from already connected client
                        logger.info("Got new request from client.");

                        // reading new request from client and parsing as a Json Object
                        JsonObject request;

                        try { request = getJsonRequest(key); }
                        catch (MalformedJSONException ex){ // parsing failed
                            logger.info("Could not parse client request.");

                            JsonObject response = new JsonObject();
                            response.addProperty("code", ResponseCode.MALFORMED_JSON_REQUEST.toString());
                            send(response.toString(), key);
                            continue;
                        }
                        catch (EOFException ex){ // user closed its endpoint
                            logger.info("User closed their endpoint: removing them.");
                            endUserSession(key);
                            continue;
                        }
                        
                        // execute request
                        pool.execute(new Worker(request, key));
                    }
                } catch(IOException ex){ // fatal IO Exception
                    logger.log(Level.WARNING, "IO exception while communicating with client: " + ex.getMessage(), ex);
                    logger.warning("Closing connection with client.");
                    endUserSession(key);
                }
            }
        }
    }

    /** Closes the server, starting the shutdown procedure. */
    public void close(){
        isRunning.set(false);
        selector.wakeup();
    }

    /**
     * Checks whether this server has been interrupted.
     * <p> Interruption causes are either an external call to {@link #close()}
     * or an exception thrown by one of the non-worker threads. 
     * @return true if and only if this thread should shutdown
     */
    private boolean isInterrupted(){
        try {
            rewardsResult.get(1, TimeUnit.MILLISECONDS);
            persistenceResult.get(1, TimeUnit.MILLISECONDS);
        } catch (InterruptedException | ExecutionException ex) { 
            logger.log(Level.SEVERE, "Exception in Rewards/Persistence Algorithm: " + ex.getMessage(), ex);
            return true; 
        }
        catch (TimeoutException ex) { }

        if(!isRunning.get()) return true;

        return false;
    }

    /** 
     * Shuts down the server, closing all connections and terminating threads.
     * @throws IOException if some IOException occurs while shutting the server down
     */
    private void shutdown() throws IOException {
        logger.info("Beginning server shutdown.");
        selector.wakeup();

        logger.fine("Unexporting Remote Object.");
        try { UnicastRemoteObject.unexportObject(this, true); }
        catch(NoSuchObjectException ex){  // won't happen: server was exported in startServer
            logger.log(Level.SEVERE, "Unexpected exception: " + ex.getMessage(), ex);
        }

        // shutting down thread pool
        logger.fine("Shutting down server pool.");
        pool.shutdown();
        try {
            if(!pool.awaitTermination(config.poolTimeout, TimeUnit.MILLISECONDS))
                pool.shutdownNow();
        } catch (InterruptedException ex) { pool.shutdownNow(); }

        // removing all clients
        logger.fine("Removing clients.");
        Iterator<SelectionKey> iter = selector.keys().iterator();
        while(iter.hasNext()){ iter.next().cancel(); }

        // closing sockets
        logger.fine("Closing sockets.");
        selector.close();
        multicastSocket.close();
        
        // shutting down Rewards Algorithm and Persistence
        logger.fine("Shutting down Rewards/Persistence Threads.");
        rewardsThread.shutdownNow();
        try {
            synchronized(persistenceWorker.runningSync){
                while(persistenceWorker.isRunning())
                    persistenceWorker.wait();
                
                persistenceThread.shutdownNow();
                
                logger.info("Running Persistence Algorithm to save all data.");
                persistenceWorker.persistData(); 
            }
        } catch (InterruptedException ex){ persistenceThread.shutdownNow(); }

        logger.info("Server shutdown complete.");
    }

    /* **************** Remote Methods **************** */

    @Override
    public void signUp(String username, Hash password, Collection<String> tags) 
            throws RemoteException, UserAlreadyExistsException, EmptyUsernameException, EmptyPasswordException, IllegalTagException {
        if(username == null || password == null || tags == null) throw new NullPointerException("null parameters in signUp method");
        for(String tag : tags) {
            if(tag == null) throw new NullPointerException("null parameters in signUp method");
            if(!tag.matches("[a-z]+")) throw new IllegalTagException("tags must not be empty and they must contain only lowercase letters");
        }

        logger.fine("Signing up new user (username: "+ username + ").");
        
        if(username.isEmpty()) throw new EmptyUsernameException("username cannot be empty");
        if(password.isEmpty()) throw new EmptyPasswordException("password cannot be empty");
        
        User newUser = new User(username, password, tags);

        synchronized(this){ 
            following.computeIfAbsent(username, key -> ConcurrentHashMap.newKeySet());
            transactions.computeIfAbsent(username, key -> new ConcurrentLinkedQueue<>());

            if(users.putIfAbsent(username, newUser) != null)
                throw new UserAlreadyExistsException("\"" + username + "\" is not available as a new username");
        }
        logger.fine("User with username " + username + " signed up.");
    }

    @Override
    public void registerForUpdates(String username, RemoteClient client) throws RemoteException, NoSuchUserException {
        if(username == null) throw new NullPointerException("null parameters while registering user in callback system");
        if(!users.containsKey(username)) throw new NoSuchUserException();

        registeredToCallbacks.putIfAbsent(username, client);
        logger.fine("Registered client to callback service.");
    }

    @Override
    public boolean unregisterForUpdates(String username) throws RemoteException {
        if(username == null) throw new NullPointerException("null parameters while unregistering user from callback system");
        if(!users.containsKey(username)) return false;

        registeredToCallbacks.remove(username);
        logger.fine("Unregistered client from callback service.");
        return true;
    }

    /* ************** Login/logout ************** */

    /**
     * Adds a user to the list of logged users.
     * @param username username of the user
     * @param password password of the user
     * @param client selection key relative to the client's connection
     * @throws NoSuchUserException if no user with the given username exists
     * @throws WrongPasswordException if the password does not match the saved password
     * @throws UserAlreadyLoggedException if the client is already logged in, or the user is logged on another client
     */
    private void login(String username, String password, SelectionKey client) 
            throws NullPointerException, NoSuchUserException, WrongPasswordException, UserAlreadyLoggedException {
        if(username == null || password == null || client == null) throw new NullPointerException("null parameters in login");

        logger.fine("Logging in user with username " + username + ".");

        KeyAttachment attachment = (KeyAttachment) client.attachment();
        User user;

        // if no user with the given username is registered
        if((user = users.get(username)) == null) { throw new NoSuchUserException("user is not signed up"); }
        // if the password does not match
        if(!user.getPassword().digest.equals(password)){ throw new WrongPasswordException("password does not match"); }
        // if the user or the key is already logged in
        if(attachment.isLoggedIn() || WinsomeServer.this.userSessions.putIfAbsent(username, client) != null){
            throw new UserAlreadyLoggedException("user or client is already logged in");
        } 

        attachment.login(username);
        logger.fine("User with username " + username + " succesfully logged in.");
    }

    /**
     * Removes a user from the list of logged users.
     * @param username username of the given user
     * @param client selection key relative to the client's connection
     * @throws NoSuchUserException if no user with the given username exists
     * @throws NoLoggedUserException if the given user is not logged in
     * @throws WrongUserException if the client is logged in with another user
     */
    public void logout(String username, SelectionKey client) throws NullPointerException, NoSuchUserException, NoLoggedUserException, WrongUserException {
        logger.fine("Logging out client with username: " + username + ".");
        checkIfLogged(username, client); // asserting that the client is actually logged in

        KeyAttachment attachment = (KeyAttachment) client.attachment();
        
        userSessions.remove(username, client);
        attachment.logout();
        logger.fine("Client with username " + username + " succesfully logged out.");
    }

    /**
     * Asserts that a given client is logged in on a given user; otherwise it throws some exception.
     * @param username username of the given user
     * @param client selection key relative to the client's connection
     * @throws NoSuchUserException if no user with the given username exists
     * @throws NoLoggedUserException if the given user is not logged in
     * @throws WrongUserException if the client is logged in with another user
     */
    private void checkIfLogged(String username, SelectionKey client) throws NullPointerException, NoSuchUserException, NoLoggedUserException, WrongUserException {
        // checking for nulls
        if(username == null || client == null) throw new NullPointerException("null parameters in logout");

        logger.finer("Asserting that a client is logged in.");

        // checking that the user exists
        if(!users.containsKey(username)) throw new NoSuchUserException("user is not registered");

        KeyAttachment attachment = (KeyAttachment) client.attachment();
        if(!attachment.isLoggedIn()) // the client is not logged in
            throw new NoLoggedUserException("no user is currently logged in the given client");
        if(!attachment.loggedUser().equals(username)) // the client is not logged in with the given user
            throw new WrongUserException("user to logout does not correspond to the given client");
        
        logger.finer("Assertion successful.");
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
        logger.info("User session successfully ended.");
    }

    /**
     * Closes a given client's session.
     * @param username the username of the client
     */
    private void endUserSession(String username){
        if(username == null) throw new NullPointerException("null arguments");

        SelectionKey key;
        if((key = userSessions.get(username)) != null)
            endUserSession(key);
        
        userSessions.remove(username);
        registeredToCallbacks.remove(username);
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
            if(entry.getValue().contains(username)) // if this user follows 'username'
                ans.add(users.get(entry.getKey()));
        }

        return ans;
    }

    /**
     * Adds a new user to the followed list of a given user.
     * @param username user who filed the request
     * @param toFollow user to follow
     * @throws NoSuchUserException if any of the two users do not exist
     * @throws UserNotVisibleException if the user to follow cannot be seen by the first user
     * @throws AlreadyFollowingException if 'username' already follows 'toFollow'
     */
    private void addFollower(String username, String toFollow) 
            throws NullPointerException, NoSuchUserException, UserNotVisibleException, AlreadyFollowingException {
        if(username == null || toFollow == null) throw new NullPointerException("null arguments");

        logger.fine("Adding '" + toFollow + "' to the list of users followed by '" + username + "'.");

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
        
        // updating followed through RMI
        RemoteClient followedClient;
        if((followedClient = registeredToCallbacks.get(toFollow)) != null){
            try { followedClient.addFollower(username, user.getTags()); }
            catch (RemoteException ex) { // remote error -> removing client
                logger.log(
                    Level.WARNING, 
                    "RemoteException thrown while adding follower remotely to user '" + toFollow + "':" + ex.getMessage(),
                    ex
                );
                logger.warning("Ending session of user '" + toFollow + "'.");
                endUserSession(toFollow);
            }
        }
    }
    
    /**
     * Removes an user from the followed list of a given user.
     * @param username user who filed the request
     * @param toFollow user to unfollow
     * @throws NullPointerException if any of 'username' or 'toUnfollow' are null
     * @throws NoSuchUserException if any of the two users do not exist
     * @throws UserNotVisibleException if the user to follow cannot be seen by the first user
     * @throws NotFollowingException if 'username' does not follow 'toUnfollow'
     */
    private void removeFollower(String username, String toUnfollow) 
            throws NullPointerException, NoSuchUserException, UserNotVisibleException, NotFollowingException {
        if(username == null || toUnfollow == null) throw new NullPointerException("null arguments");

        logger.fine("Removing '" + toUnfollow + "' to the list of users followed by '" + username + "'.");

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
        
        // updating unfollowed through RMI
        RemoteClient unfollowedClient;
        if((unfollowedClient = registeredToCallbacks.get(toUnfollow)) != null){
            try { unfollowedClient.removeFollower(username); }
            catch (RemoteException ex){ // remote error -> removing client
                logger.log(
                    Level.WARNING, 
                    "RemoteException thrown while adding follower remotely to user '" + toUnfollow + "':" + ex.getMessage(),
                    ex
                );
                logger.warning("Ending session of user '" + toUnfollow + "'.");
                endUserSession(toUnfollow);
            }
        }
    }

    // -------------- Post methods --------------- //

    /**
     * Returns all the posts written by a given user.
     * @param username the username of the author
     * @return a list with all the post written by the given user
     * @throws NoSuchUserException if no user with the given username exist
     */
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
    
    /**
     * Returns a client's feed, i.e. all the posts published by the users followed by the given client.
     * @param username the username of the given client
     * @return the client's feed
     * @throws NoSuchUserException if no user with the given username exists
     */
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

    /**
     * Deletes a post.<p>
     * If the post is a rewin, it only deletes the post; if it's original, all rewins are deleted with it.
     * @param username the username of the deleter
     * @param id the id of the post to delete
     * @throws NoSuchUserException if no user with the given username exists
     * @throws NoSuchPostException if no post with the given id exists
     * @throws NotPostOwnerException if the client is not the author/rewinner of the given post
     */
    private void deletePost(String username, int id) throws NoSuchUserException, NoSuchPostException, NotPostOwnerException {
        if(username == null) throw new NullPointerException();

        Post post;

        if(!users.containsKey(username)) throw new NoSuchUserException("no user with the given username exists"); 
        if((post = posts.get(id)) == null) throw new NoSuchPostException("no post with the given id exists");

        if(post.isRewin()){
            if(!post.getRewinner().equals(username)) throw new NotPostOwnerException("user is not the rewinner of this post");

            // synchronized with rewins
            synchronized(posts) { posts.remove(id); }
        } else {
            if(!post.getAuthor().equals(username)) throw new NotPostOwnerException("user is not the author of this post");

            // synchronized with rewins
            synchronized(posts) { posts.remove(id); }
            for(Entry<Integer, Post> entry : posts.entrySet()){
                if(entry.getValue().getOriginalID() == id)
                    posts.remove(entry.getKey());
            }
        }
    }

    /**
     * Rewins a post.
     * @param username the username of the rewinner
     * @param idPost the id of the post to rewin
     * @throws NoSuchUserException no user with the given username exists
     * @throws NoSuchPostException no post with the given id exists
     * @throws PostOwnerException user is the author of the post
     * @throws AlreadyRewinnedException user has already rewinned the post
     * @throws NotFollowingException user is not following the owner of the post
     */
    private void rewinPost(String username, int idPost) 
            throws NoSuchUserException, NoSuchPostException, PostOwnerException, AlreadyRewinnedException, NotFollowingException {
        if(username == null) throw new NullPointerException();

        Post post;
        if(!users.containsKey(username)) throw new NoSuchUserException("user does not exist");
        if((post = posts.get(idPost)) == null) throw new NoSuchPostException("no post with the given ID exists");

        if(post.getAuthor().equals(username))
            throw new PostOwnerException("user is the author of the post");

        if(!canInteractWith(username, post))
            throw new NotFollowingException("user does not follow the author of the post");
    
        // synchronizing access with other rewins and with 'delete' operations
        synchronized(posts){
            if(post.hasRewinned(username))
                throw new AlreadyRewinnedException("user cannot rewin post");
            Post rewin = new Rewin(post, username);

            if(posts.containsKey(idPost)) posts.put(rewin.getID(), rewin);
            else throw new NoSuchPostException("no post with the given ID exists");
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

    /**
     * Checks whether a user can interact with a post, i.e. if they follow the post's owner.
     * @param username the username of the given user
     * @param idPost the id of the post to interact with
     * @return true if and only if the user can interact with the given post
     * @throws NoSuchUserException if no user with the given username exists
     * @throws NoSuchPostException if no post with the given id exists
     */
    private boolean canInteractWith(String username, int idPost) throws NoSuchUserException, NoSuchPostException {
        if(username == null) throw new NullPointerException("null arguments");

        Post post;
        if((post = posts.get(idPost)) == null) throw new NoSuchPostException();        

        return canInteractWith(username, post);
    }

    /**
     * Checks whether a user can interact with a post, i.e. if they follow the post's owner.
     * @param username the username of the given user
     * @param idPost the post to interact with
     * @return true if and only if the user can interact with the given post
     * @throws NoSuchUserException if no user with the given username exists
     */
    private boolean canInteractWith(String username, Post post) throws NoSuchUserException {
        if(username == null || post == null) throw new NullPointerException("null arguments");
        
        Set<String> follows;
        if((follows = following.get(username)) == null) throw new NoSuchUserException();

        String author = (post.isRewin()) ? post.getRewinner() : post.getAuthor();
        return follows.contains(author);
    }

    /**
     * Calculates the current exchange rate from Wincoins to Bitcoins.
     * @return the exchange rate to BTC
     * @throws IOException if some IO error occurs
     */
    private double getBTCExchangeRate() throws IOException {
        final String randomGenURL = "https://www.random.org/decimal-fractions/?num=1&dec=10&col=1&format=plain&rnd=new";

        try ( 
            BufferedReader in = new BufferedReader(new InputStreamReader(new URL(randomGenURL).openStream()));
        ) {
            return Double.parseDouble(in.readLine());
        } catch (NumberFormatException ex) { throw new IOException("result was not a valid exchange rate"); }
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