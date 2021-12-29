package winsome.api.exceptions;

public class NotImplementedException extends Exception {
    public NotImplementedException(){ super(); }
    public NotImplementedException(String msg){ super(msg); }
    public NotImplementedException(Throwable err){ super(err); }
    public NotImplementedException(String msg, Throwable err){ super(msg, err); }
}
