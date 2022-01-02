package winsome.server;

import java.nio.ByteBuffer;

/** The attachment to a SelectionKey of the WinsomeServer. */
public class KeyAttachment {
    /** User logged in this key */
    private String user;
    /** This key's buffer */
    private ByteBuffer buf;

    /**
     * Creates a new attachment with a given logged user.
     * @param user the user logged on this key
     */
    public KeyAttachment(String user){
        this.user = user;
        this.buf = ByteBuffer.allocate(2048);
    }

    /** Creates a new attachment without a logged user. */
    public KeyAttachment(){
        this(null);
    }

    /**
     * Checks whether a user is logged on this key.
     * @return true if and only if a user is logged
     */
    public boolean isLoggedIn(){ return user != null; }
    /**
     * Returns the user logged on this key.
     * <p>
     * To check if a user is logged, use {@link #isLoggedIn()}.
     * @return the username of the user logged in this key, if present; null otherwise
     */
    public String loggedUser(){ return user; }

    /**
     * Adds a user to this key and returns true if the login was successful.
     * @param user the user to login on this key
     * @return true if and only if the login was successful, i.e. no user was already logged
     */
    public boolean login(String user) {
        if(user == null) throw new NullPointerException();
        if(isLoggedIn()) return false;

        this.user = user;
        return true;
    }
    /** Removes the logged user from the key. */
    public void logout(){ user = null; }

    /**
     * Returns the stored byte buffer.
     * @return the stored byte buffer
     */
    public ByteBuffer getBuffer(){ return buf; }
}
