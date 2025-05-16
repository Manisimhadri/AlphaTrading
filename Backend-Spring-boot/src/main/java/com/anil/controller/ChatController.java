package com.anil.controller;

import com.anil.model.ChatMessage;
import com.anil.model.CoinChatMessage;
import com.anil.service.ChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "*")
public class ChatController {

    @Autowired
    private ChatService chatService;

    @PostMapping("/send")
    public ResponseEntity<ChatMessage> sendMessage(@RequestBody ChatMessage message) {
        ChatMessage response = chatService.processUserMessage(message.getContent(), message.getSender());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/history/{sender}")
    public ResponseEntity<List<ChatMessage>> getChatHistory(@PathVariable String sender) {
        List<ChatMessage> history = chatService.getChatHistory(sender);
        return ResponseEntity.ok(history);
    }
    
    // New endpoint for coin-related chat
    @PostMapping("/bot/coin")
    public ResponseEntity<CoinChatMessage> handleCoinQuery(@RequestBody CoinChatMessage request) {
        CoinChatMessage response = chatService.processCoinQuery(request.getPrompt());
        return ResponseEntity.ok(response);
    }
} 