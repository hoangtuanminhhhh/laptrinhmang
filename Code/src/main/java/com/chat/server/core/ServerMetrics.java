  package com.chat.server.core;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class ServerMetrics {
    private final AtomicInteger onlineClients = new AtomicInteger(0);
    private final AtomicLong messagesSent = new AtomicLong(0);
    private final AtomicLong bytesTransferred = new AtomicLong(0);

    public int getOnlineClients() {
        return onlineClients.get();
    }

    public void setOnlineClients(int count) {
        onlineClients.set(count);
    }

    public long getMessagesSent() {
        return messagesSent.get();
    }

    public void incrementMessagesSent() {
        messagesSent.incrementAndGet();
    }

    public long getBytesTransferred() {
        return bytesTransferred.get();
    }

    public void addBytesTransferred(long bytes) {
        bytesTransferred.addAndGet(bytes);
    }
}