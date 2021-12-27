package winsome.server.exceptions;

public class InvalidSelectionKeyException extends Exception {
    public InvalidSelectionKeyException(){ super(); }
    public InvalidSelectionKeyException(String msg){ super(msg); }
    public InvalidSelectionKeyException(Throwable err){ super(err); }
    public InvalidSelectionKeyException(String msg, Throwable err){ super(msg, err); }
}