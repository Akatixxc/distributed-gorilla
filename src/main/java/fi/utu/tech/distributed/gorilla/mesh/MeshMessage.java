package fi.utu.tech.distributed.gorilla.mesh;

import java.io.Serializable;

public class MeshMessage implements Serializable{
    public long token;
    public Serializable payload;
    public long sender;

    public MeshMessage(Serializable payload, long sender) {
        this.payload = payload;
        this.sender = sender;
        this.token = 1000000000L + (long) (Math.random() * 9000000000L);
    }
}
