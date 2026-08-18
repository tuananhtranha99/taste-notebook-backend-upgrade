package com.tastenotebook.web;

import com.tastenotebook.domain.Friend;
import com.tastenotebook.dto.FriendResponse;
import com.tastenotebook.dto.Requests;
import com.tastenotebook.repository.FriendRepository;
import com.tastenotebook.repository.PreferenceEntryRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/friends")
public class FriendController {

    private final FriendRepository friendRepository;
    private final PreferenceEntryRepository preferenceEntryRepository;

    public FriendController(FriendRepository friendRepository, PreferenceEntryRepository preferenceEntryRepository) {
        this.friendRepository = friendRepository;
        this.preferenceEntryRepository = preferenceEntryRepository;
    }

    @GetMapping
    public List<FriendResponse> list() {
        return friendRepository.findAll().stream().map(FriendResponse::from).toList();
    }

    @PostMapping
    public ResponseEntity<FriendResponse> create(@Valid @RequestBody Requests.NewFriendRequest req) {
        Friend saved = friendRepository.save(new Friend(req.name.trim()));
        return ResponseEntity.ok(FriendResponse.from(saved));
    }

    @GetMapping("/{id}")
    public FriendResponse get(@PathVariable Long id) {
        Friend f = friendRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Friend not found"));
        return FriendResponse.from(f);
    }

    /** Deletes the friend and all of their preference entries. */
    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!friendRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Friend not found");
        }
        preferenceEntryRepository.deleteByFriendId(id);
        friendRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
