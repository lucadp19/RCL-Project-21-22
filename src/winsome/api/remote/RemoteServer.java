package winsome.api.remote;

import java.rmi.*;
import java.util.*;

import winsome.api.exceptions.*;
import winsome.utils.cryptography.Hash;

/**
 * An interface for a remote WINSOME server. 
 */
public interface RemoteServer extends Remote {
    /**
     * * Registers a new user of the WINSOME Social Network.
     * @param username the new user's username
     * @param password the new user's (hashed) password
     * @param tags the new user's tags
     * @throws RemoteException if some IO error occurs while calling this method
     * @throws UserAlreadyExistsException if a user with the given username already exists
     * @throws EmptyUsernameException if the given username is empty
     * @throws EmptyPasswordException if the given password is empty
     * @throws IllegalTagException if some tag does not contain only lowercase characters
     */
    void signUp(String username, Hash password, Collection<String> tags) 
        throws RemoteException, UserAlreadyExistsException, EmptyUsernameException, EmptyPasswordException, IllegalTagException;

    /**
     * Registers a remote client in the callback update list.
     * @param username the logged user
     * @param client the remote client
     * @throws RemoteException if some IO error occurs while calling this method
     * @throws NoSuchUserException if no user with the given username exists
     */
    void registerForUpdates(String username, RemoteClient client) throws RemoteException, NoSuchUserException;
    /**
     * Unregisters a remote client from the callback update list.
     * @param username the logged user
     * @returns true if and only if the removal is successful
     * @throws RemoteException if some IO error occurs while calling this method
     */
    boolean unregisterForUpdates(String username) throws RemoteException;
}