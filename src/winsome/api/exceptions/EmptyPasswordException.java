package winsome.api.exceptions;

public class EmptyPasswordException extends Exception {
    public EmptyPasswordException(){ super(); }
    public EmptyPasswordException(String msg){ super(msg); }
    public EmptyPasswordException(Throwable err){ super(err); }
    public EmptyPasswordException(String msg, Throwable err){ super(msg, err); }
}