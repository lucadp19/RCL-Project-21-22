package winsome.server;

import java.util.*;
import java.util.concurrent.*;

import java.rmi.*;
import java.rmi.server.RemoteObject;

import winsome.api.*;

public class WinsomeServer extends RemoteObject implements RemoteServer {
    private ServerConfig config;

    private Map<String, User> users;
    private Map<Integer, Post> posts;
    private Map<String, List<User>> followerMap;
    private Map<String, List<User>> followingMap;
    private Map<String, List<Transaction>> transactions;

    private Map<String, RemoteClient> registeredToCallbacks; // TODO: multiple clients, same user?

    public WinsomeServer(){
        super();
    }

    /* **************** Remote Methods **************** */

    public void signUp(String username, String password, List<String> tags) throws RemoteException {
        if(username == null || password == null || tags == null) throw new NullPointerException("null parameters in signUp method");
        for(String tag : tags) 
            if(tag == null) throw new NullPointerException("null parameters in signUp method");

        synchronized(this){ // TODO: is this the best way to synchronize things?
            if(users.containsKey(username)) throw new IllegalArgumentException(); // TODO: change exception

            User newUser = new User(username, password, tags);

            users.put(username, newUser);
            followerMap.put(username, new ArrayList<>());
            followingMap.put(username, new ArrayList<>());
            transactions.put(username, new ArrayList<>());
        }
    }

    public void registerForUpdates(String username, RemoteClient client) throws RemoteException {
        if(username == null) throw new NullPointerException("null parameters while registering user in callback system");
        if(!users.containsKey(username)) throw new IllegalArgumentException(); // TODO: change exception

        registeredToCallbacks.putIfAbsent(username, client);
    }

    public void unregisterForUpdates(String username) throws RemoteException {
        if(username == null) throw new NullPointerException("null parameters while unregistering user from callback system");
        if(!users.containsKey(username)) throw new IllegalArgumentException(); // TODO: change exception

        registeredToCallbacks.remove(username);
    }
}
