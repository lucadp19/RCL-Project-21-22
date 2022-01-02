package winsome.server;

import java.nio.ByteBuffer;

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
    public boolean login(String user) {
        if(user == null) throw new NullPointerException();
        if(this.user != null) return false;

        this.user = user;
        return true;
    }
    public void logout(){ user = null; }

    public ByteBuffer getBuffer(){ return buf; }
}
