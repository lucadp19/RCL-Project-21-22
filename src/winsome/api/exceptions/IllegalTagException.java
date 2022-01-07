package winsome.api.exceptions;

public class IllegalTagException extends Exception {
    public IllegalTagException(){ super(); }
    public IllegalTagException(String msg){ super(msg); }
    public IllegalTagException(Throwable err){ super(err); }
    public IllegalTagException(String msg, Throwable err){ super(msg, err); }
}