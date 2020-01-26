package fi.utu.tech.distributed.gorilla.logic;

import java.io.Serializable;

public final class ChatMessage implements Serializable {
    public final String sender;
    public final String recipient;
    public final String contents;
    private long token;

    public ChatMessage(String sender, String recipient, String contents) {
        this.sender = sender;
        this.recipient = recipient;
        this.contents = contents;
        this.token = 1000000000L + (long) (Math.random() * 9000000000L);
    }

    public long getToken() {
        return this.token;
    }

    @Override
    public String toString() {
        return sender + " says: " + contents;
    }
}
