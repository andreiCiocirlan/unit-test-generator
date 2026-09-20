package com.example.user;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final EmailService emailService;

    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow();
    }

    public User create(String email) {

        if (userRepository.existsByEmail(email)) {
            throw new DuplicateUserException("User with email " + email + " already exists");
        }

        User user = new User(email);

        User savedUser = userRepository.save(user);

        emailService.sendWelcomeEmail(email);

        return savedUser;
    }
}