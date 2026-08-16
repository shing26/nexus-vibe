package com.nexus.campus.controller;

import com.nexus.campus.dto.ApiResponse;
import com.nexus.campus.entity.SysMessage;
import com.nexus.campus.service.SysMessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/messages")
public class MessageController {

    @Autowired
    private SysMessageService sysMessageService;

    @GetMapping
    public ApiResponse<List<SysMessage>> getMessages(@RequestAttribute("currentUserId") Long userId) {
        return ApiResponse.success(sysMessageService.getMessagesByUserId(userId));
    }

    @GetMapping("/unread/count")
    public ApiResponse<Map<String, Integer>> getUnreadCount(@RequestAttribute("currentUserId") Long userId) {
        Map<String, Integer> data = new HashMap<>();
        data.put("count", sysMessageService.countUnreadMessages(userId));
        return ApiResponse.success(data);
    }

    @PostMapping("/{id}/read")
    public ApiResponse<Void> markAsRead(
            @PathVariable Long id,
            @RequestAttribute("currentUserId") Long userId) {
        boolean marked = sysMessageService.markAsRead(id, userId);
        if (!marked) {
            return ApiResponse.notFound("Message not found.");
        }
        return ApiResponse.successMessage("Message marked as read.");
    }

    @PostMapping("/read-all")
    public ApiResponse<Void> markAllAsRead(@RequestAttribute("currentUserId") Long userId) {
        sysMessageService.markAllAsRead(userId);
        return ApiResponse.successMessage("All messages acknowledged.");
    }
}
