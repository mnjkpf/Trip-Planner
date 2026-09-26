package com.travelplanner.user.service;

import com.travelplanner.user.dto.UserResponse;
import com.travelplanner.user.error.ApiExceptions.UserNotFoundException;
import com.travelplanner.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public UserResponse getById(UUID id) {
        return userRepository.findById(id)
                .map(u -> new UserResponse(
                        u.getId(), u.getEmail(), u.getDisplayName(),
                        u.getRole(), u.getCreatedAt()))
                .orElseThrow(() -> new UserNotFoundException("Користувача не знайдено"));
    }
}
