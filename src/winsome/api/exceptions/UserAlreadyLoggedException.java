package winsome.api.exceptions;

public class UserAlreadyLoggedException extends Exception {
    public UserAlreadyLoggedException(){ super(); }
    public UserAlreadyLoggedException(String msg){ super(msg); }
    public UserAlreadyLoggedException(Throwable err){ super(err); }
    public UserAlreadyLoggedException(String msg, Throwable err){ super(msg, err); }
}