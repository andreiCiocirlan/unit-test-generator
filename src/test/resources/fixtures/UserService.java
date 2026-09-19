package fixtures;

import org.apache.catalina.User;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final EmailService emailService;

    public UserService(
            UserRepository userRepository,
            EmailService emailService) {

        this.userRepository = userRepository;
        this.emailService = emailService;
    }

    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow();
    }

    public User create(String email) {

        if (userRepository.existsByEmail(email)) {
            throw new DuplicateUserException();
        }

        User user = new User(email);

        User savedUser = userRepository.save(user);

        emailService.sendWelcomeEmail(email);

        return savedUser;
    }
}