package com.example.ailatrieuphu;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Vibrator;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    TextView txtQuestion, txtTimer, txtCurrentLevel, txtCurrentMoney;
    Button btnA, btnB, btnC, btnD, btn5050, btnCall, btnAudience;
    Button btnStart, btnPause, btnRestart, btnExit, btnLeaderboard;
    LinearLayout layoutMenu, layoutGame;

    DatabaseReference mData;
    List<Question> listQuestions = new ArrayList<>();
    int currentQuestionIndex = 0;
    int currentLevel = 1;
    int totalQuestionPassed = 0;

    int[] moneyMilestones = {
            0, 100, 200, 300, 500, 1000, 2000, 3000, 4000, 5000, 10000,
            12000, 15000, 20000, 25000, 30000, 35000, 40000, 45000, 50000, 60000,
            75000, 90000, 110000, 150000, 200000, 300000, 450000, 600000, 800000, 1000000
    };

    CountDownTimer timer;
    long timeLeftInMillis = 30000;
    String currentCorrectAns = "";
    boolean used5050 = false, usedCall = false, usedAudience = false;
    boolean isPaused = false;

    MediaPlayer musicPlayer;
    ToneGenerator toneG = new ToneGenerator(AudioManager.STREAM_ALARM, 100);

    public static class Player {
        public String name;
        public int score;
        public Player() {}
        public Player(String name, int score) {
            this.name = name;
            this.score = score;
        }
    }

    public static class Question {
        public String content, a, b, c, d, ans;
        public Question() {}
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        txtCurrentLevel = findViewById(R.id.txtCurrentLevel);
        txtCurrentMoney = findViewById(R.id.txtCurrentMoney);
        layoutMenu = findViewById(R.id.layoutMenu);
        layoutGame = findViewById(R.id.layoutGame);
        txtQuestion = findViewById(R.id.txtQuestion);
        txtTimer = findViewById(R.id.txtTimer);
        btnA = findViewById(R.id.btnAnswerA);
        btnB = findViewById(R.id.btnAnswerB);
        btnC = findViewById(R.id.btnAnswerC);
        btnD = findViewById(R.id.btnAnswerD);
        btn5050 = findViewById(R.id.btn5050);
        btnCall = findViewById(R.id.btnCall);
        btnAudience = findViewById(R.id.btnAudience);
        btnStart = findViewById(R.id.btnStart);
        btnPause = findViewById(R.id.btnPause);
        btnRestart = findViewById(R.id.btnRestart);
        btnExit = findViewById(R.id.btnExit);
        btnLeaderboard = findViewById(R.id.btnLeaderboard);

        setupLifelines();
        setupGameControls();

        layoutMenu.setVisibility(View.VISIBLE);
        layoutGame.setVisibility(View.GONE);

        btnLeaderboard.setOnClickListener(v -> showLeaderboard());
    }

    private void playSound(int resId, boolean loop) {
        if (musicPlayer != null) {
            musicPlayer.stop();
            musicPlayer.release();
        }
        musicPlayer = MediaPlayer.create(this, resId);
        musicPlayer.setLooping(loop);
        musicPlayer.start();
    }

    private void blinkButton(Button btn, Runnable onFinish) {
        Handler handler = new Handler();
        for (int i = 0; i < 6; i++) {
            final int index = i;
            handler.postDelayed(() -> {
                if (index % 2 == 0) {
                    btn.setBackgroundTintList(ColorStateList.valueOf(Color.GREEN));
                } else {
                    btn.setBackgroundTintList(ColorStateList.valueOf(Color.WHITE));
                }
                if (index == 5) onFinish.run();
            }, i * 200);
        }
    }

    // ĐÃ SỬA: Lưu theo tên để không bị trùng
    private void saveScore(String name, int score) {
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("leaderboard");
        ref.child(name).setValue(new Player(name, score));
    }

    private void showLeaderboard() {
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("leaderboard");
        ref.orderByChild("score").limitToLast(10).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                List<Player> players = new ArrayList<>();
                for (DataSnapshot ds : snapshot.getChildren()) players.add(ds.getValue(Player.class));
                Collections.reverse(players);

                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < players.size(); i++) {
                    sb.append((i+1) + ". " + players.get(i).name + ": $" + String.format("%,d", players.get(i).score) + "\n");
                }

                new android.app.AlertDialog.Builder(MainActivity.this)
                        .setTitle("TOP 10 TRIỆU PHÚ")
                        .setMessage(sb.length() > 0 ? sb.toString() : "Chưa có dữ liệu")
                        .setPositiveButton("Đóng", null).show();
            }
            @Override
            public void onCancelled(DatabaseError error) {}
        });
    }

    private void showGameOverDialog(int finalMoney) {
        if (timer != null) timer.cancel();
        if (musicPlayer != null) musicPlayer.stop();

        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("TRÒ CHƠI KẾT THÚC");

        final EditText input = new EditText(this);
        input.setHint("Nhập tên của bạn");
        builder.setView(input);

        builder.setMessage("Số tiền nhận được: $ " + String.format("%,d", finalMoney))
                .setCancelable(false)
                .setPositiveButton("Lưu & Chơi lại", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty()) saveScore(name, finalMoney);
                    btnRestart.performClick();
                })
                .setNegativeButton("Thoát", (dialog, which) -> finish())
                .show();
    }

    private int getSafeMoney() {
        // ĐÃ SỬA: Lấy tiền từ mốc câu hỏi trước đó nếu đã vượt qua ít nhất 1 câu
        if (totalQuestionPassed > 0) {
            return moneyMilestones[totalQuestionPassed - 1];
        }
        return 0;
    }

    // [Các hàm bên dưới giữ nguyên]
    private void setupGameControls() {
        btnStart.setOnClickListener(v -> {
            layoutMenu.setVisibility(View.GONE);
            layoutGame.setVisibility(View.VISIBLE);
            currentLevel = 1;
            currentQuestionIndex = 0;
            totalQuestionPassed = 0;
            isPaused = false;
            resetLifelines();
            playSound(R.raw.music_background, true);
            loadQuestionsByLevel(currentLevel);
        });

        btnPause.setOnClickListener(v -> {
            if (timer != null && !isPaused) {
                timer.cancel();
                if (musicPlayer != null) musicPlayer.pause();
                isPaused = true;
                disableButtons();
                btnPause.setText("Tiếp tục");
            } else if (isPaused) {
                isPaused = false;
                if (musicPlayer != null) musicPlayer.start();
                startTimer(timeLeftInMillis);
                enableButtons();
                btnPause.setText("Tạm dừng");
            }
        });

        btnRestart.setOnClickListener(v -> {
            currentLevel = 1;
            currentQuestionIndex = 0;
            totalQuestionPassed = 0;
            used5050 = false; usedCall = false; usedAudience = false;
            isPaused = false;
            resetLifelines();
            playSound(R.raw.music_background, true);
            loadQuestionsByLevel(currentLevel);
        });

        btnExit.setOnClickListener(v -> {
            if (musicPlayer != null) musicPlayer.release();
            finish();
        });
    }

    private void loadQuestionsByLevel(int level) {
        mData = FirebaseDatabase.getInstance().getReference("questions").child("level_" + level);
        mData.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                listQuestions.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    Question q = ds.getValue(Question.class);
                    if (q != null) listQuestions.add(q);
                }
                Collections.shuffle(listQuestions);
                if (!listQuestions.isEmpty()) displayQuestion();
            }
            @Override
            public void onCancelled(DatabaseError error) {}
        });
    }

    private void displayQuestion() {
        if (totalQuestionPassed >= 30) {
            showGameOverDialog(1000000);
            return;
        }

        txtCurrentLevel.setText("CÂU: " + (totalQuestionPassed + 1) + "/30");
        txtCurrentMoney.setText("$ " + String.format("%,d", moneyMilestones[totalQuestionPassed]));

        Question q = listQuestions.get(currentQuestionIndex);
        btnA.setVisibility(View.VISIBLE); btnB.setVisibility(View.VISIBLE);
        btnC.setVisibility(View.VISIBLE); btnD.setVisibility(View.VISIBLE);

        int navyColor = Color.parseColor("#1A237E");
        btnA.setBackgroundTintList(ColorStateList.valueOf(navyColor));
        btnB.setBackgroundTintList(ColorStateList.valueOf(navyColor));
        btnC.setBackgroundTintList(ColorStateList.valueOf(navyColor));
        btnD.setBackgroundTintList(ColorStateList.valueOf(navyColor));

        enableButtons();
        txtQuestion.setText(q.content);
        btnA.setText("A. " + q.a); btnB.setText("B. " + q.b);
        btnC.setText("C. " + q.c); btnD.setText("D. " + q.d);
        currentCorrectAns = q.ans;

        startTimer(30000);
        setupAnswerListeners();
    }

    private void setupAnswerListeners() {
        View.OnClickListener listener = v -> {
            if (timer != null) timer.cancel();
            disableButtons();

            Button btnSelected = (Button) v;
            String tag = (v.getId() == R.id.btnAnswerA) ? "a" : (v.getId() == R.id.btnAnswerB) ? "b" : (v.getId() == R.id.btnAnswerC) ? "c" : "d";

            final String finalTag = tag;
            btnSelected.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#FF9800")));
            toneG.startTone(ToneGenerator.TONE_PROP_BEEP2, 100);

            new Handler().postDelayed(() -> {
                if (finalTag.equals(currentCorrectAns)) {
                    playSound(R.raw.correct_ans, false);
                    blinkButton(btnSelected, () -> {
                        new Handler().postDelayed(() -> {
                            totalQuestionPassed++;
                            currentQuestionIndex++;
                            if (currentQuestionIndex >= 10 && currentLevel < 3) {
                                currentLevel++;
                                currentQuestionIndex = 0;
                                loadQuestionsByLevel(currentLevel);
                                playSound(R.raw.music_background, true);
                            } else {
                                displayQuestion();
                                playSound(R.raw.music_background, true);
                            }
                        }, 1000);
                    });
                } else {
                    playSound(R.raw.wrong_ans, false);
                    btnSelected.setBackgroundTintList(ColorStateList.valueOf(Color.RED));
                    showCorrectAnswer(currentCorrectAns);
                    Vibrator vib = (Vibrator) getSystemService(VIBRATOR_SERVICE);
                    if (vib != null) vib.vibrate(500);
                    new Handler().postDelayed(() -> showGameOverDialog(getSafeMoney()), 1500);
                }
            }, 1500);
        };

        btnA.setOnClickListener(listener); btnB.setOnClickListener(listener);
        btnC.setOnClickListener(listener); btnD.setOnClickListener(listener);
    }

    private void showCorrectAnswer(String ans) {
        if (ans.equals("a")) btnA.setBackgroundTintList(ColorStateList.valueOf(Color.GREEN));
        else if (ans.equals("b")) btnB.setBackgroundTintList(ColorStateList.valueOf(Color.GREEN));
        else if (ans.equals("c")) btnC.setBackgroundTintList(ColorStateList.valueOf(Color.GREEN));
        else if (ans.equals("d")) btnD.setBackgroundTintList(ColorStateList.valueOf(Color.GREEN));
    }

    private void startTimer(long duration) {
        if (timer != null) timer.cancel();
        timer = new CountDownTimer(duration, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                timeLeftInMillis = millisUntilFinished;
                txtTimer.setText("" + millisUntilFinished / 1000);
                if (millisUntilFinished < 5000) toneG.startTone(ToneGenerator.TONE_PROP_BEEP, 100);
            }
            @Override
            public void onFinish() {
                txtTimer.setText("0");
                playSound(R.raw.wrong_ans, false);
                showGameOverDialog(getSafeMoney());
            }
        }.start();
    }

    private void disableButtons() {
        btnA.setEnabled(false); btnB.setEnabled(false); btnC.setEnabled(false); btnD.setEnabled(false);
    }

    private void enableButtons() {
        btnA.setEnabled(true); btnB.setEnabled(true); btnC.setEnabled(true); btnD.setEnabled(true);
        if (!used5050) btn5050.setEnabled(true);
        if (!usedCall) btnCall.setEnabled(true);
        if (!usedAudience) btnAudience.setEnabled(true);
    }

    private void resetLifelines() {
        used5050 = false; usedCall = false; usedAudience = false;
        btn5050.setEnabled(true); btn5050.setAlpha(1.0f);
        btnCall.setEnabled(true); btnCall.setAlpha(1.0f);
        btnAudience.setEnabled(true); btnAudience.setAlpha(1.0f);
    }

    private void setupLifelines() {
        btn5050.setOnClickListener(v -> {
            if (used5050 || isPaused) return;
            used5050 = true; btn5050.setEnabled(false); btn5050.setAlpha(0.5f);
            int count = 0;
            Button[] buttons = {btnA, btnB, btnC, btnD}; String[] tags = {"a", "b", "c", "d"};
            for (int i = 0; i < 4; i++) {
                if (!tags[i].equals(currentCorrectAns) && count < 2) { buttons[i].setVisibility(View.INVISIBLE); count++; }
            }
        });

        btnCall.setOnClickListener(v -> {
            if (usedCall || isPaused) return;
            usedCall = true; btnCall.setEnabled(false); btnCall.setAlpha(0.5f);
            Toast.makeText(this, "Người thân chọn: " + currentCorrectAns.toUpperCase(), Toast.LENGTH_LONG).show();
        });

        btnAudience.setOnClickListener(v -> {
            if (usedAudience || isPaused) return;
            usedAudience = true; btnAudience.setEnabled(false); btnAudience.setAlpha(0.5f);
            Toast.makeText(this, "Khán giả chọn: " + currentCorrectAns.toUpperCase(), Toast.LENGTH_LONG).show();
        });
    }

    @Override
    protected void onDestroy() {
        if (musicPlayer != null) { musicPlayer.release(); musicPlayer = null; }
        super.onDestroy();
    }
}