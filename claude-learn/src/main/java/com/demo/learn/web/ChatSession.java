package com.demo.learn.web;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages in-memory chat sessions.
 * Each session holds conversation history. Refreshing the page creates a new session.
 */
public class ChatSession {

    private final ConcurrentHashMap<String, List<Message>> sessions = new ConcurrentHashMap<>();

    /**
     * Returns a snapshot copy of the conversation history.
     * Safe for iteration; modifications won't affect stored history.
     */
    public List<Message> getHistory(String sessionId) {
        List<Message> history = sessions.get(sessionId);
        return history != null ? List.copyOf(history) : List.of();
    }

    public void addUserMessage(String sessionId, String content) {
        sessions.computeIfAbsent(sessionId, k -> new ArrayList<>()).add(new UserMessage(content));
    }

    public void addAssistantMessage(String sessionId, AssistantMessage message) {
        sessions.computeIfAbsent(sessionId, k -> new ArrayList<>()).add(message);
    }

    public void addToolResponseMessage(String sessionId, ToolResponseMessage message) {
        sessions.computeIfAbsent(sessionId, k -> new ArrayList<>()).add(message);
    }

    public void setSystemPrompt(String sessionId, String systemPrompt) {
        List<Message> history = sessions.computeIfAbsent(sessionId, k -> new ArrayList<>());
        // Remove existing system message if present
        history.removeIf(m -> m instanceof SystemMessage);
        history.add(0, new SystemMessage(systemPrompt));
    }

    public void clear(String sessionId) {
        sessions.remove(sessionId);
    }
}
