package com.expensetracker.common.email;

import com.expensetracker.common.exception.AppException;
import com.expensetracker.common.security.HashSecurity;
import com.expensetracker.common.redis.RedisService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ResendOtp {

    private final RedisService redisService ;
    private final HashSecurity hashSecurity;

    private final EmailService sendEmail ;
    public ResendOtp(RedisService redisService , EmailService sendEmail ,  HashSecurity hashSecurity){
        this.redisService = redisService ;
        this.sendEmail = sendEmail ;
        this.hashSecurity = hashSecurity ;
    }

    public int sendOtp(String email , String userName , String purpose , String type ){

        Long isBlocked = redisService.ttlTimer(
                redisService.blockOtpKey(email , type)
        );

        if(isBlocked == 0){
            throw new AppException(String.format("you have executed the maximum number of tries , please try again after %s seconds", isBlocked) , HttpStatus.BAD_REQUEST);
        }

        Long ttl = redisService.ttlTimer(
          redisService.otpKey(email , type)
        );

        if(ttl >0 ){
            throw new AppException(String.format("you can resend otp after%s seconds", ttl) , HttpStatus.BAD_REQUEST);

        }

        String maxOtpValue = redisService.get(
                redisService.maxOtpKey(email, type)
        );

        long maxOtp = maxOtpValue == null ? 0 : Long.parseLong(maxOtpValue);

        if (maxOtp == 1) {
            redisService.setValue(
                    redisService.maxOtpKey(email, type),
                    String.valueOf(maxOtp),
                    60 * 2
            );
        }

        if(maxOtp >= 3){
            redisService.setValue(
                    redisService.blockOtpKey(email, type),
                    String.valueOf(1),
                    60 * 5
            );
            throw new AppException("you have executed the maximum number of tries" , HttpStatus.BAD_REQUEST);
        }

        int otp = sendEmail.generateOTP();

        sendEmail.sendEmail(email , "Welcome to my App " , EmailTemplate.otpEmail(userName , otp , purpose));

        redisService.setValue(redisService.otpKey(email , type) ,hashSecurity.hash(String.valueOf(otp)) , 60 );

        redisService.incr(
          redisService.maxOtpKey(email , type)
        );

        return otp ;
    }

}
