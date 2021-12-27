package winsome.api.exceptions;

public class UserAlreadyExistsException extends Exception {
    public UserAlreadyExistsException(){ super(); }
    public UserAlreadyExistsException(String msg){ super(msg); }
    public UserAlreadyExistsException(Throwable err){ super(err); }
    public UserAlreadyExistsException(String msg, Throwable err){ super(msg, err); } 
}
