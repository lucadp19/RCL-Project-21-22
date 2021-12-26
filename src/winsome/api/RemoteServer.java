package winsome.api;

import java.rmi.*;
import java.util.*;

/**
 * An interface for a remote WINSOME server. 
 */
public interface RemoteServer extends Remote {
    /**
     * Registers a new user of the WINSOME Social Network.
     * @param username the new user's username
     * @param password the new user's password
     * @param tags the new user's tags
     * @throws RemoteException
     */
    void signUp(String username, String password, List<String> tags) throws RemoteException;

    /**
     * Registers a remote client in the update list.
     * @param username the logged user
     * @param client the remote client
     * @throws RemoteException
     */
    void registerForUpdates(String username, RemoteClient client) throws RemoteException;
    /**
     * Unregisters a remote client from the update list.
     * @param username the logged user
     * @throws RemoteException
     */
    void unregisterForUpdates(String username) throws RemoteException;
}