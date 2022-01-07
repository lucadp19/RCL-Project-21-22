package winsome.api;

import java.rmi.*;
import java.util.*;

import winsome.api.exceptions.*;
import winsome.utils.cryptography.Hash;

/**
 * An interface for a remote WINSOME server. 
 */
public interface RemoteServer extends Remote {
    /**
     * Registers a new user of the WINSOME Social Network.
     * @param username the new user's username
     * @param password the new user's (hashed) password
     * @param tags the new user's tags
     * @throws RemoteException
     */
    void signUp(String username, Hash password, Collection<String> tags) 
        throws RemoteException, UserAlreadyExistsException, EmptyUsernameException, EmptyPasswordException;

    /**
     * Registers a remote client in the update list.
     * @param username the logged user
     * @param client the remote client
     * @throws RemoteException
     */
    void registerForUpdates(String username, RemoteClient client) throws RemoteException, NoSuchUserException;
    /**
     * Unregisters a remote client from the update list.
     * @param username the logged user
     * @returns true if and only if the removal is successful
     * @throws RemoteException
     */
    boolean unregisterForUpdates(String username) throws RemoteException;
}