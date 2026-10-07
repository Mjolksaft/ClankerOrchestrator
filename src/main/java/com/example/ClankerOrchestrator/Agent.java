package com.example.ClankerOrchestrator;

import java.util.ArrayList;
import java.util.List;

public class Agent {

    private final long id;
    private final String name;
    private final List<String> likes = new ArrayList<String>();
    private final List<String> dislikes = new ArrayList<String>();
    private String sessionID;

    public Agent(long id, String name) {
        this.id = id;
        this.name = name;
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getSessionID() {
        return sessionID;
    }

    public void setSessionID(String sessionID) {
        this.sessionID = sessionID;
    }

    public List<String> getLikes() {
        return List.copyOf(likes);
    }

    public List<String> getDislikes() {
        return List.copyOf(dislikes);
    }

    public void Like(String item) {
        likes.add(item);
    }

    public void Dislike(String item) {
        dislikes.add(item);
    }

    public boolean Unlike(String item) {
        return likes.remove(item);
    }

    public boolean Undislike(String item) {
        return dislikes.remove(item);
    }
}