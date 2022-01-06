package winsome.api.exceptions;

public class UserNotVisibleException extends Exception {
    public UserNotVisibleException(){ super(); }
    public UserNotVisibleException(String msg){ super(msg); }
    public UserNotVisibleException(Throwable err){ super(err); }
    public UserNotVisibleException(String msg, Throwable err){ super(msg, err); } 
}
