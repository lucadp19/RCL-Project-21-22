package winsome.api.exceptions;

public class EmptyUsernameException extends Exception {
    public EmptyUsernameException(){ super(); }
    public EmptyUsernameException(String msg){ super(msg); }
    public EmptyUsernameException(Throwable err){ super(err); }
    public EmptyUsernameException(String msg, Throwable err){ super(msg, err); }
}
