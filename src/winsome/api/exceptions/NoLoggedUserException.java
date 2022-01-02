package winsome.api.exceptions;

public class NoLoggedUserException extends Exception {
    public NoLoggedUserException(){ super(); }
    public NoLoggedUserException(String msg){ super(msg); }
    public NoLoggedUserException(Throwable err){ super(err); }
    public NoLoggedUserException(String msg, Throwable err){ super(msg, err); }
}
