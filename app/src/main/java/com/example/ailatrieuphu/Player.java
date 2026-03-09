package com.example.ailatrieuphu;

public class Player {
    public String name;
    public int score;

    public Player() {
        // Bắt buộc phải có hàm khởi tạo trống để Firebase hoạt động
    }

    public Player(String name, int score) {
        this.name = name;
        this.score = score;
    }
}