package com.expensetracker.common.redis;

import com.expensetracker.common.exception.AppException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Set;

@Service
public class RedisService {

    public String revokedKey(Long userId, String jti) {
        return String.format("revoke_token::%d::%s", userId, jti);
    }

    public String getRevokedKey(Long userId) {
        return String.format("revoke_token::%d", userId);
    }

    public String otpKey(String email, String type) {
        return String.format("%s::%s", type, email);
    }

    public String maxOtpKey(String email, String type) {
        return String.format("%s::%s::max_tries", type, email);
    }

    public String blockOtpKey(String email, String type) {
        return String.format("%s::%s::block", type, email);
    }

    public String maxPasswordKey(String email) {
        return String.format("password::%s::max_tries", email);
    }

    public String blockPasswordKey(String email) {
        return String.format("password::%s::block", email);
    }

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RedisService(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    private static final long DEFAULT_TTL = 60;

    public void setValue(String key, String value, long ttl) {

        try {
            redis.opsForValue().set(key, value, Duration.ofSeconds(ttl));
        } catch (Exception error) {
            throw new AppException(
                    "Failed to save data in Redis",
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    public void snapshot(String key, Object value, long ttl) {

        try {
            String json = objectMapper.writeValueAsString(value);

            redis.opsForValue().set(
                    key,
                    json,
                    Duration.ofSeconds(ttl)
            );

        } catch (Exception error) {
            throw new AppException(
                    "Failed to create snapshot in Redis",
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    public void setDefault(String key, String value) {
        setValue(key, value, DEFAULT_TTL);
    }


    public String get(String key) {

        try {
            return redis.opsForValue().get(key);
        } catch (Exception error) {
            throw new AppException(
                    "Failed to get data from Redis",
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    public void delete(String key) {
        try {
            redis.delete(key);
        } catch (Exception error) {
            throw new AppException(
                    "Failed to delete data from Redis",
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    public Long ttlTimer(String key) {

        try {
            return redis.getExpire(key);
        } catch (Exception error) {
            throw new AppException(
                    "Failed to get ttl data from Redis",
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    public Boolean expire(String key, long ttl) {

        try {
            return redis.expire(key, Duration.ofSeconds(ttl));
        } catch (Exception error) {
            throw new AppException(
                    "Failed to get expire data from Redis",
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    public Boolean exist(String key) {

        try {
            return redis.hasKey(key);
        } catch (Exception error) {
            throw new AppException(
                    "Failed to get exist data from Redis",
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    public Set<String> keys(String pattern) {
        try {
            return redis.keys(pattern);
        } catch (Exception error) {
            throw new AppException(
                    "Error to get keys from Redis",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    public Long incr(String key) {

        try {
            return redis.opsForValue().increment(key);
        } catch (Exception error) {
            throw new AppException(
                    "error to increment operation",
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

}
