package winsome.server;

import java.util.*;
import java.util.Map.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.gson.stream.JsonReader;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.net.*;

import java.rmi.*;
import java.rmi.registry.*;
import java.rmi.server.RemoteObject;
import java.rmi.server.UnicastRemoteObject;

import winsome.api.*;
import winsome.api.exceptions.*;
import winsome.server.datastructs.*;
import winsome.server.exceptions.*;
import winsome.utils.exceptions.*;

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
            ConcurrentHashMap<String, Transaction> transactions;

            try {
                users = parseUsers(usersFile);
                followers = parseFollowers(followersFile);
            } catch(IOException | InvalidJSONFileException ex) { ex.printStackTrace(); setEmptyData(); return; }

            WinsomeServer.this.users = users;
            WinsomeServer.this.followerMap = followers;
        }

        private void setEmptyData(){
            if(!isDataInit.compareAndSet(false, true))
                throw new IllegalStateException("data has already been initialized");

            users = new ConcurrentHashMap<>();
            posts = new ConcurrentHashMap<>();
            followerMap = new ConcurrentHashMap<>();
            followingMap = new ConcurrentHashMap<>();
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
    }

    private static AtomicBoolean isDataInit = new AtomicBoolean(false);

    private ServerConfig config;
    private ServerPersistence persistenceWorker;

    private Selector selector;
    private ServerSocketChannel socketChannel;

    private DatagramSocket multicastSocket;

    private ConcurrentMap<String, User> users;
    private ConcurrentMap<Integer, Post> posts;
    private ConcurrentMap<String, Set<String>> followerMap;
    private ConcurrentMap<String, Set<String>> followingMap;
    private ConcurrentMap<String, List<Transaction>> transactions;
    
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

        System.out.println("Followers: " + followerMap);
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
                        echo(key);
                    }
                    iter.remove();
                } catch(IOException ex){
                    // TODO: disconnect client
                    key.cancel();
                    System.err.println("Connection closed as a result of an IO Exception.");
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

    public void signUp(String username, String password, List<String> tags) throws RemoteException, UserAlreadyExistsException {
        if(username == null || password == null || tags == null) throw new NullPointerException("null parameters in signUp method");
        for(String tag : tags) 
            if(tag == null) throw new NullPointerException("null parameters in signUp method");

        synchronized(this){ // TODO: is this the best way to synchronize things?
            if(users.computeIfAbsent(username, k -> new User(username, password, tags)) != null)
                throw new UserAlreadyExistsException("\"" + username + "\" is not available as a new username");
            followerMap.put(username, ConcurrentHashMap.newKeySet());
            followingMap.put(username, ConcurrentHashMap.newKeySet());
            transactions.put(username, new ArrayList<>());
        }
    }

    public void registerForUpdates(String username, RemoteClient client) throws RemoteException, NoSuchUserException {
        if(username == null) throw new NullPointerException("null parameters while registering user in callback system");
        if(!users.containsKey(username)) throw new NoSuchUserException();

        registeredToCallbacks.putIfAbsent(username, client);
    }

    public void unregisterForUpdates(String username) throws RemoteException, NoSuchUserException {
        if(username == null) throw new NullPointerException("null parameters while unregistering user from callback system");
        if(!users.containsKey(username)) throw new NoSuchUserException();

        registeredToCallbacks.remove(username);
    }

    /* ************** Login/logout ************** */

    public void login(String username, String password, SelectionKey client) 
            throws NullPointerException, NoSuchUserException, WrongPasswordException, InvalidSelectionKeyException, UserAlreadyLoggedException {
        if(username == null || password == null || client == null) throw new NullPointerException("null parameters in login");
        if(!users.containsKey(username)) throw new NoSuchUserException();

        User user = users.get(username);
        if(user.getPassword() != password) throw new WrongPasswordException();

        if(!client.isValid()) throw new InvalidSelectionKeyException();

        synchronized(userSessions){
            if(userSessions.containsKey(username)) throw new UserAlreadyLoggedException();
            userSessions.put(username, client);
        }
    }

    public void logout(String username, SelectionKey client) throws NullPointerException {
        if(username == null || client == null) throw new NullPointerException("null parameters in logout");

        userSessions.remove(username, client);
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
}
