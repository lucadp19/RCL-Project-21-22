package winsome.api.exceptions;

public class NoSuchPostException extends Exception {
    public NoSuchPostException(){ super(); }
    public NoSuchPostException(String msg){ super(msg); }
    public NoSuchPostException(Throwable err){ super(err); }
    public NoSuchPostException(String msg, Throwable err){ super(msg, err); }
}
