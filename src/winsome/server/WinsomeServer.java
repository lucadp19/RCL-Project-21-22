package winsome.server;

import java.util.*;
import java.util.Map.*;
import java.util.concurrent.*;

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
import winsome.server.exceptions.*;
import winsome.utils.exceptions.*;

public class WinsomeServer extends RemoteObject implements RemoteServer {
    private ServerConfig config;

    private Selector selector;
    private ServerSocketChannel socketChannel;

    private DatagramSocket multicastSocket;

    private ConcurrentMap<String, SelectionKey> userSessions;

    private ConcurrentMap<String, User> users;
    private ConcurrentMap<Integer, Post> posts;
    private ConcurrentMap<String, List<String>> followerMap;
    private ConcurrentMap<String, List<String>> followingMap;
    private ConcurrentMap<String, List<Transaction>> transactions;

    private ConcurrentMap<String, RemoteClient> registeredToCallbacks; // TODO: multiple clients, same user?

    public WinsomeServer(){
        super();
    }

    public void initServer(String configPath) 
        throws NullPointerException, FileNotFoundException, 
                NumberFormatException, IOException {
        config = new ServerConfig(configPath);

        // TODO: read persisted data

        userSessions = new ConcurrentHashMap<>();

        users = new ConcurrentHashMap<>();
        posts = new ConcurrentHashMap<>();
        followerMap = new ConcurrentHashMap<>();
        followingMap = new ConcurrentHashMap<>();
        transactions = new ConcurrentHashMap<>();
        
        registeredToCallbacks = new ConcurrentHashMap<>();
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
        // ByteBuffer buf = ByteBuffer.allocate(4);
        SocketChannel channel = (SocketChannel) key.channel();
        
        // if(channel.read(buf) == -1) throw new IOException();
        
        // buf.flip();
        // int size = buf.getInt(0);
        // System.out.println(size);

        // buf = ByteBuffer.allocate(size);
        // while(buf.hasRemaining()){ channel.read(buf); }
        // buf.flip();

        // String msg = StandardCharsets.UTF_8.decode(buf).toString().trim();
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
            if(users.containsKey(username)) throw new UserAlreadyExistsException();

            User newUser = new User(username, password, tags);

            users.put(username, newUser);
            followerMap.put(username, new ArrayList<>());
            followingMap.put(username, new ArrayList<>());
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
