package com.hungtvb.votesystem.system;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Supplier;

@Component
public class SystemStatusCache {
    static final Duration TTL = Duration.ofSeconds(5);

    private volatile Entry entry;

    public SystemStatusSnapshot get(Supplier<SystemStatusSnapshot> loader) {
        long now = System.nanoTime();
        Entry current = entry;
        if (current != null && current.expiresAtNanos() > now) {
            return current.snapshot();
        }
        synchronized (this) {
            current = entry;
            now = System.nanoTime();
            if (current != null && current.expiresAtNanos() > now) {
                return current.snapshot();
            }
            SystemStatusSnapshot loaded = loader.get();
            entry = new Entry(loaded, now + TTL.toNanos());
            return loaded;
        }
    }

    public void put(SystemStatusSnapshot snapshot) {
        entry = new Entry(snapshot, System.nanoTime() + TTL.toNanos());
    }

    public void evict() {
        entry = null;
    }

    private record Entry(SystemStatusSnapshot snapshot, long expiresAtNanos) {
    }
}
