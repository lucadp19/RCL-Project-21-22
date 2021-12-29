package winsome.client.exceptions;

public class UnknownCommandException extends Exception {
    public UnknownCommandException(){ super(); }
    public UnknownCommandException(String msg){ super(msg); }
    public UnknownCommandException(Throwable err){ super(err); }
    public UnknownCommandException(String msg, Throwable err){ super(msg, err); }   
}
