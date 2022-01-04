package winsome.api.exceptions;

public class RewinException extends Exception {
    public RewinException(){ super(); }
    public RewinException(String msg){ super(msg); }
    public RewinException(Throwable err){ super(err); }
    public RewinException(String msg, Throwable err){ super(msg, err); }
}