package uy.com.pepeganga.userservice.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uy.com.pepeganga.userservice.service.IUserService;
import uy.com.pepeganga.userservice.service.mail.EmailService;

import java.util.Map;


@RestController
@RequestMapping("api")
public class ResetPasswordController {


    @Autowired
    IUserService userService;

    @Autowired
    EmailService emailService;

    @PostMapping("send/email-reset-password")
    public ResponseEntity<Map<String, Object>> sendEmailToResetPassword(@RequestParam String email, @RequestParam String url){
        return new ResponseEntity<>(emailService.sendEmailToResetPassword(email, url), HttpStatus.OK);
    }

    @GetMapping("valid-token-by-reset-password")
    public ResponseEntity<Boolean> isValidToken(@RequestParam String token){
        return new ResponseEntity<>(userService.isValidTokenToResetPassword(token), HttpStatus.ACCEPTED);
    }
}
