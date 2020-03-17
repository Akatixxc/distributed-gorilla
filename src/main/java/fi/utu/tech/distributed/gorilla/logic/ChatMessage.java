package fi.utu.tech.distributed.gorilla.logic;

import java.io.Serializable;

public final class ChatMessage implements Serializable {
    public final String sender;
    public final String recipient;
    public final String contents;

    public ChatMessage(String sender, String recipient, String contents) {
        this.sender = sender;
        this.recipient = recipient;
        this.contents = contents;
    }

    @Override
    public String toString() {
        return sender + " says: " + contents;
    }
}
