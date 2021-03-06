package winsome.api.remote;

import java.rmi.*;
import java.util.*;

/**
 * An interface for a remote WINSOME client.
 */
public interface RemoteClient extends Remote {
    /**
     * Callback method to add a new follower to the list.
     * @param follower the new follower
     * @param tags the new follower's tags
     * @throws RemoteException if some IO error occurs while calling this method
     */
    void addFollower(String follower, Collection<String> tags) throws RemoteException;
    
    /**
     * Callback method to remove a follower from the list.
     * @param follower the follower to remove
     * @throws RemoteException if some IO error occurs while calling this method
     */
    void removeFollower(String follower) throws RemoteException;
}
