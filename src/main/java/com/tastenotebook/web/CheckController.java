package com.tastenotebook.web;

import com.tastenotebook.dto.Requests;
import com.tastenotebook.dto.TasteCheckResult;
import com.tastenotebook.service.TasteCheckService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/friends/{friendId}/check")
public class CheckController {

    private final TasteCheckService tasteCheckService;

    public CheckController(TasteCheckService tasteCheckService) {
        this.tasteCheckService = tasteCheckService;
    }

    @PostMapping
    public TasteCheckResult check(@PathVariable Long friendId, @Valid @RequestBody Requests.CheckDishRequest req) {
        return tasteCheckService.check(friendId, req.dish);
    }
}
