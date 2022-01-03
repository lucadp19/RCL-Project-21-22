package winsome.api.exceptions;

public class NotPostOwnerException extends Exception {
    public NotPostOwnerException(){ super(); }
    public NotPostOwnerException(String msg){ super(msg); }
    public NotPostOwnerException(Throwable err){ super(err); }
    public NotPostOwnerException(String msg, Throwable err){ super(msg, err); }
}
