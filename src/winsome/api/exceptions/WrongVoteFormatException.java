package winsome.api.exceptions;

public class WrongVoteFormatException extends Exception {
    public WrongVoteFormatException(){ super(); }
    public WrongVoteFormatException(String msg){ super(msg); }
    public WrongVoteFormatException(Throwable err){ super(err); }
    public WrongVoteFormatException(String msg, Throwable err){ super(msg, err); }
}
