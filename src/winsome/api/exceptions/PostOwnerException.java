package winsome.api.exceptions;

public class PostOwnerException extends Exception {
    public PostOwnerException(){ super(); }
    public PostOwnerException(String msg){ super(msg); }
    public PostOwnerException(Throwable err){ super(err); }
    public PostOwnerException(String msg, Throwable err){ super(msg, err); }
}
