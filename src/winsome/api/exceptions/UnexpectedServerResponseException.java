package winsome.api.exceptions;

public class UnexpectedServerResponseException extends Exception {
    public UnexpectedServerResponseException(){ super(); }
    public UnexpectedServerResponseException(String msg){ super(msg); }
    public UnexpectedServerResponseException(Throwable err){ super(err); }
    public UnexpectedServerResponseException(String msg, Throwable err){ super(msg, err); }
}
