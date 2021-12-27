package winsome.api.exceptions;

public class NoSuchUserException extends Exception {
    public NoSuchUserException(){ super(); }
    public NoSuchUserException(String msg){ super(msg); }
    public NoSuchUserException(Throwable err){ super(err); }
    public NoSuchUserException(String msg, Throwable err){ super(msg, err); }
}