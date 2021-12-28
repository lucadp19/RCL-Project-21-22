package winsome.server;

import java.util.*;
import java.util.Map.*;
import java.util.concurrent.*;

import java.io.*;
import java.nio.channels.*;

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
            try {
                while(iter.hasNext()){
                    SelectionKey key = iter.next();
                    iter.remove();

                    if(key.isAcceptable()){ 
                        SocketChannel client = socketChannel.accept();
                        client.configureBlocking(false);
                        client.register(selector, SelectionKey.OP_READ, null); 
                    } 
                    if(key.isReadable() && key.isValid()){
                        // TODO
                    }
                }
            } catch(IOException ex){
                // disconnect client
            }
        }
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
}
