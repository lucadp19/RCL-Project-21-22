package winsome.api.exceptions;

public class WrongUserException extends Exception {
    public WrongUserException(){ super(); }
    public WrongUserException(String msg){ super(msg); }
    public WrongUserException(Throwable err){ super(err); }
    public WrongUserException(String msg, Throwable err){ super(msg, err); }
}
