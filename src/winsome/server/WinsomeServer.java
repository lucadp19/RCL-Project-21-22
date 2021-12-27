package winsome.server;

import java.util.*;
import java.util.concurrent.*;
import java.io.IOException;
import java.net.*;

import java.nio.channels.*;

import java.rmi.*;
import java.rmi.server.RemoteObject;

import winsome.api.*;
import winsome.api.exceptions.*;

public class WinsomeServer extends RemoteObject implements RemoteServer {
    private ServerConfig config;

    private Selector selector;
    private ServerSocketChannel socketChannel;

    private DatagramSocket multicastSocket;


    private Map<String, User> users;
    private Map<Integer, Post> posts;
    private Map<String, List<String>> followerMap;
    private Map<String, List<String>> followingMap;
    private Map<String, List<Transaction>> transactions;

    private Map<String, RemoteClient> registeredToCallbacks; // TODO: multiple clients, same user?

    public WinsomeServer(){
        super();
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
}
