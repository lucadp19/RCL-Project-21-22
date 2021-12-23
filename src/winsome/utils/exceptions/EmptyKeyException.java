package winsome.utils.exceptions;

public class EmptyKeyException extends RuntimeException {
    public EmptyKeyException(){ super(); }
    public EmptyKeyException(String msg){ super(msg); }
    public EmptyKeyException(String msg, Throwable err){ super(msg, err); }
}
