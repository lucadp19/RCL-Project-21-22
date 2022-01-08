package winsome.utils.configs.exceptions;

/** Thrown whenever a key has not been set in the configuration file */
public class KeyNotSetException extends InvalidConfigFileException {
    public KeyNotSetException(){ super(); }
    public KeyNotSetException(String msg){ super(msg); }
    public KeyNotSetException(Throwable err){ super(err); }
    public KeyNotSetException(String msg, Throwable err){ super(msg, err); }
}
