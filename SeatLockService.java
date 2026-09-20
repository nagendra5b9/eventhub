package com.eventhub.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class SeatLockService {

    private final StringRedisTemplate redisTemplate;

    @Value("${eventhub.seat-lock.timeout-minutes:10}")
    private long lockTimeoutMinutes;

    public SeatLockService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    private String buildKey(Long eventId, Long seatId) {
        return "lock:event:" + eventId + ":seat:" + seatId;
    }

    public synchronized boolean lockSeats(Long eventId, List<Long> seatIds, String userEmail) {
        List<String> acquiredKeys = new ArrayList<>();
        Duration ttl = Duration.ofMinutes(lockTimeoutMinutes);

        for (Long seatId : seatIds) {
            String key = buildKey(eventId, seatId);
            // SETNX with TTL
            Boolean success = redisTemplate.opsForValue().setIfAbsent(key, userEmail, ttl);
            if (Boolean.TRUE.equals(success)) {
                acquiredKeys.add(key);
            } else {
                // If lock is held by the same user, extend TTL
                String currentUser = redisTemplate.opsForValue().get(key);
                if (userEmail.equals(currentUser)) {
                    redisTemplate.expire(key, ttl);
                    acquiredKeys.add(key);
                } else {
                    // Rollback previously acquired locks in this batch
                    for (String acquiredKey : acquiredKeys) {
                        redisTemplate.delete(acquiredKey);
                    }
                    return false;
                }
            }
        }
        return true;
    }

    public boolean isSeatLocked(Long eventId, Long seatId) {
        String key = buildKey(eventId, seatId);
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    public String getLockOwner(Long eventId, Long seatId) {
        String key = buildKey(eventId, seatId);
        return redisTemplate.opsForValue().get(key);
    }

    public long getRemainingLockTimeSeconds(Long eventId, Long seatId) {
        String key = buildKey(eventId, seatId);
        Long expire = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        return expire != null && expire > 0 ? expire : 0;
    }

    public void releaseSeats(Long eventId, List<Long> seatIds, String userEmail) {
        for (Long seatId : seatIds) {
            String key = buildKey(eventId, seatId);
            String owner = redisTemplate.opsForValue().get(key);
            if (userEmail.equals(owner)) {
                redisTemplate.delete(key);
            }
        }
    }

    public void forceReleaseSeats(Long eventId, List<Long> seatIds) {
        for (Long seatId : seatIds) {
            redisTemplate.delete(buildKey(eventId, seatId));
        }
    }
}
