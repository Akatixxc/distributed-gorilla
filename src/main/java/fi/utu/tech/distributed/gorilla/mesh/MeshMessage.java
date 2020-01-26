package fi.utu.tech.distributed.gorilla.mesh;

import java.io.Serializable;

public class MeshMessage implements Serializable{
    private String author;
    private String message;
    private long token;

    public MeshMessage(String author, String message) {
        this.author = author;
        this.message = message;
        this.token = 1000000000L + (long) (Math.random() * 9000000000L);
    }

    public long getToken() {
        return this.token;
    }

    @Override
    public String toString() {
        return author + " says: " + message;
    }
}
