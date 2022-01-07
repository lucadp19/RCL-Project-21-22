package winsome.utils.configs.exceptions;

public class InvalidConfigFileException extends Exception {
    public InvalidConfigFileException(){ super(); }
    public InvalidConfigFileException(String msg){ super(msg); }
    public InvalidConfigFileException(Throwable err){ super(err); }
    public InvalidConfigFileException(String msg, Throwable err){ super(msg, err); }
}
