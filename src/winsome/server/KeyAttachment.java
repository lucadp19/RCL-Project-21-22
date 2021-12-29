package winsome.server;

import java.nio.ByteBuffer;
import winsome.server.exceptions.UserAlreadyLoggedException;

public class KeyAttachment {
    private String user;
    private ByteBuffer buf;

    public KeyAttachment(String user){
        this.user = user;
        this.buf = ByteBuffer.allocate(2048);
    }

    public KeyAttachment(){
        this(null);
    }

    public boolean isLoggedIn(){ return user == null; }
    public void login(String user) throws UserAlreadyLoggedException {
        if(user == null) throw new NullPointerException();
        if(this.user != null) throw new UserAlreadyLoggedException();

        this.user = user;
    }
    public void logout(){ user = null; }

    public ByteBuffer getBuffer(){ return buf; }
}
