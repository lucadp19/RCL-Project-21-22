package winsome.api.exceptions;

public class AlreadyVotedException extends Exception {
    public AlreadyVotedException(){ super(); }
    public AlreadyVotedException(String msg){ super(msg); }
    public AlreadyVotedException(Throwable err){ super(err); }
    public AlreadyVotedException(String msg, Throwable err){ super(msg, err); }
}
