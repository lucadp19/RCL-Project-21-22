package winsome.server.exceptions;

/** An exception representing an invalid (e.g. non-existent) directory. */
public class InvalidDirectoryException extends Exception {
    public InvalidDirectoryException(){ super(); }
    public InvalidDirectoryException(String msg){ super(msg); }
    public InvalidDirectoryException(Throwable err){ super(err); }
    public InvalidDirectoryException(String msg, Throwable err){ super(msg, err); }
}
