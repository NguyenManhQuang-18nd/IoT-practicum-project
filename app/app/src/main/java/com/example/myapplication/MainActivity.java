package com.example.myapplication;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    TextView tvNhietDo, tvDoAm, tvKhiGas, tvAiStatus, tvDongHo, tvChartTitle;
    Switch swQuat, swHutAm;
    SeekBar sbDen;
    Spinner spinnerKhuVuc;
    ImageView ivFan, ivDehum, ivBulb, ivKhuVuc;
    CardView cvAiWarning;
    LineChart lineChart;
    ObjectAnimator fanAnimator;

    // Firebase
    DatabaseReference rootRef;

    // Biến quản lý trạng thái
    int currentAreaIndex = 0;
    float[] curTemp = new float[3];
    float[] curHum = new float[3];
    float[] curGas = new float[3];
    int[] curQuat = new int[3];
    int[] curDehum = new int[3];
    int[] curLight = new int[3];
    String[] curAiState = new String[3];

    // Lịch sử biểu đồ chạy nền cho 3 khu vực
    ArrayList<String> xLabels = new ArrayList<>();
    ArrayList<Float>[] histTemp = new ArrayList[3];
    ArrayList<Float>[] histHum = new ArrayList[3];
    ArrayList<Float>[] histGas = new ArrayList[3];

    Handler timeHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Khởi tạo mảng
        for (int i = 0; i < 3; i++) {
            histTemp[i] = new ArrayList<>();
            histHum[i] = new ArrayList<>();
            histGas[i] = new ArrayList<>();
            curAiState[i] = "Bình thường";
        }

        // Ánh xạ
        tvDongHo = findViewById(R.id.tvDongHo);
        tvChartTitle = findViewById(R.id.tvChartTitle);
        tvNhietDo = findViewById(R.id.tvNhietDo);
        tvDoAm = findViewById(R.id.tvDoAm);
        tvKhiGas = findViewById(R.id.tvKhiGas);
        tvAiStatus = findViewById(R.id.tvAiStatus);
        swQuat = findViewById(R.id.swQuat);
        swHutAm = findViewById(R.id.swHutAm);
        sbDen = findViewById(R.id.sbDen);
        spinnerKhuVuc = findViewById(R.id.spinnerKhuVuc);
        ivFan = findViewById(R.id.ivFan);
        ivDehum = findViewById(R.id.ivDehum);
        ivBulb = findViewById(R.id.ivBulb);
        ivKhuVuc = findViewById(R.id.ivKhuVuc);
        cvAiWarning = findViewById(R.id.cvAiWarning);
        lineChart = findViewById(R.id.lineChart);

        fanAnimator = ObjectAnimator.ofFloat(ivFan, "rotation", 0f, 360f);
        fanAnimator.setDuration(1000);
        fanAnimator.setRepeatCount(ValueAnimator.INFINITE);
        fanAnimator.setInterpolator(new LinearInterpolator());

        setupChartBasic();
        startBackgroundProcesses();
        setupUserControls();
        listenToFirebaseRoot();

        // Custom Spinner (Chữ to, in đậm, nền trắng hoàn toàn)
        String[] danhSachKhuVuc = {"Khu Bếp 1", "Khu Bếp 2", "Khu Bếp 3"};
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, danhSachKhuVuc) {
            @NonNull
            @Override
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                TextView tv = (TextView) super.getView(position, convertView, parent);
                tv.setTextColor(Color.BLACK);
                tv.setTextSize(20f);
                tv.setTypeface(null, Typeface.BOLD);
                tv.setGravity(Gravity.CENTER);
                return tv;
            }

            @Override
            public View getDropDownView(int position, View convertView, @NonNull ViewGroup parent) {
                TextView tv = (TextView) super.getDropDownView(position, convertView, parent);
                tv.setTextColor(Color.BLACK);
                tv.setBackgroundColor(Color.WHITE); // Vá lỗi nền đen kịt
                tv.setTextSize(18f);
                tv.setPadding(40, 40, 40, 40);
                return tv;
            }
        };
        spinnerKhuVuc.setAdapter(adapter);

        spinnerKhuVuc.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                currentAreaIndex = position;
                tvChartTitle.setText("Biểu Đồ Thời Gian Thực - " + danhSachKhuVuc[position]);

                if (position == 0) ivKhuVuc.setImageResource(R.drawable.khu_bep_1);
                else if (position == 1) ivKhuVuc.setImageResource(R.drawable.khu_bep_2);
                else if (position == 2) ivKhuVuc.setImageResource(R.drawable.khu_bep_3);

                cvAiWarning.setVisibility(position == 0 ? View.VISIBLE : View.GONE);

                updateInterfaceForCurrentArea();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void listenToFirebaseRoot() {
        rootRef = FirebaseDatabase.getInstance().getReference();
        rootRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) return;

                for (int i = 0; i < 3; i++) {
                    String node = "Khu_bep_" + (i + 1);
                    DataSnapshot snap = snapshot.child(node);

                    if (snap.exists()) {
                        curTemp[i] = snap.child("nhiet_do").exists() ? snap.child("nhiet_do").getValue(Float.class) : 0;
                        curHum[i] = snap.child("do_am").exists() ? snap.child("do_am").getValue(Float.class) : 0;
                        curGas[i] = snap.child("khi_gas").exists() ? snap.child("khi_gas").getValue(Float.class) : 0;
                        curQuat[i] = snap.child("quat_thong_gio").exists() ? snap.child("quat_thong_gio").getValue(Integer.class) : 0;
                        curDehum[i] = snap.child("may_hut_am").exists() ? snap.child("may_hut_am").getValue(Integer.class) : 0;
                        curLight[i] = snap.child("den").exists() ? snap.child("den").getValue(Integer.class) : 0;

                        if (i == 0 && snap.child("ai_status").exists()) {
                            curAiState[i] = snap.child("ai_status").getValue(String.class);
                        }

                        // Logic hệ thống tự động
                        if (curTemp[i] >= 35 || curGas[i] >= 800) {
                            if (curQuat[i] == 0) rootRef.child(node).child("quat_thong_gio").setValue(1);
                        }
                        if (curHum[i] >= 65 && curDehum[i] == 0) {
                            rootRef.child(node).child("may_hut_am").setValue(1);
                        } else if (curHum[i] <= 55 && curDehum[i] == 1) {
                            rootRef.child(node).child("may_hut_am").setValue(0);
                        }
                    }
                }
                updateInterfaceForCurrentArea();
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void updateInterfaceForCurrentArea() {
        int id = currentAreaIndex;

        tvNhietDo.setText("Nhiệt độ: " + curTemp[id] + " °C");
        if(curTemp[id] >= 35) tvNhietDo.setTextColor(Color.RED); else tvNhietDo.setTextColor(Color.BLACK);

        tvDoAm.setText("Độ ẩm: " + curHum[id] + " %");
        tvKhiGas.setText("Khí Gas: " + curGas[id]);

        if (id == 0) {
            tvAiStatus.setText(curAiState[0]);
            if (curAiState[0].toLowerCase().contains("cháy")) {
                tvAiStatus.setTextColor(Color.RED);
                cvAiWarning.setCardBackgroundColor(Color.parseColor("#FFEBEE"));
            } else {
                tvAiStatus.setTextColor(Color.parseColor("#4CAF50"));
                cvAiWarning.setCardBackgroundColor(Color.parseColor("#E8F5E9"));
            }
        }

        if (!swQuat.isPressed()) swQuat.setChecked(curQuat[id] == 1);

        if (curQuat[id] == 1) {
            if (!fanAnimator.isRunning()) fanAnimator.start();
        } else {
            fanAnimator.cancel();
        }

        if (!swHutAm.isPressed()) swHutAm.setChecked(curDehum[id] == 1);

        ivDehum.setImageResource(curDehum[id] == 1 ? R.drawable.may_hut_am_on : R.drawable.may_hut_am_off);

        if (!sbDen.isPressed()) sbDen.setProgress(curLight[id]);

        updateBulbVisual(curLight[id]);
    }

    private void startBackgroundProcesses() {
        timeHandler.post(new Runnable() {
            @Override
            public void run() {
                // Cập nhật Đồng hồ
                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss | dd/MM/yyyy", Locale.getDefault());
                tvDongHo.setText(sdf.format(new Date()));

                // Nạp dữ liệu lịch sử cho Biểu đồ
                SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
                xLabels.add(timeFmt.format(new Date()));
                if (xLabels.size() > 50) xLabels.remove(0);

                for (int i = 0; i < 3; i++) {
                    histTemp[i].add(curTemp[i]);
                    histHum[i].add(curHum[i]);
                    histGas[i].add(curGas[i]);

                    if (histTemp[i].size() > 50) {
                        histTemp[i].remove(0);
                        histHum[i].remove(0);
                        histGas[i].remove(0);
                    }
                }

                drawChart(currentAreaIndex);
                timeHandler.postDelayed(this, 1000);
            }
        });
    }

    private void drawChart(int id) {
        if (histTemp[id].isEmpty()) return;

        ArrayList<Entry> entriesTemp = new ArrayList<>();
        ArrayList<Entry> entriesHum = new ArrayList<>();
        ArrayList<Entry> entriesGas = new ArrayList<>();

        for (int i = 0; i < histTemp[id].size(); i++) {
            entriesTemp.add(new Entry(i, histTemp[id].get(i)));
            entriesHum.add(new Entry(i, histHum[id].get(i)));
            entriesGas.add(new Entry(i, histGas[id].get(i)));
        }

        LineDataSet setTemp = new LineDataSet(entriesTemp, "Nhiệt độ (°C)");
        setTemp.setColor(Color.parseColor("#EF5350"));
        setTemp.setDrawCircles(false);
        setTemp.setDrawFilled(true);
        setTemp.setFillColor(Color.parseColor("#EF5350"));
        setTemp.setMode(LineDataSet.Mode.CUBIC_BEZIER);

        LineDataSet setHum = new LineDataSet(entriesHum, "Độ ẩm (%)");
        setHum.setColor(Color.parseColor("#42A5F5"));
        setHum.setDrawCircles(false);
        setHum.setDrawFilled(true);
        setHum.setFillColor(Color.parseColor("#42A5F5"));
        setHum.setMode(LineDataSet.Mode.CUBIC_BEZIER);

        LineDataSet setGas = new LineDataSet(entriesGas, "Khí Gas");
        setGas.setColor(Color.parseColor("#FBC02D"));
        setGas.setDrawCircles(false);
        setGas.setDrawFilled(true);
        setGas.setFillColor(Color.parseColor("#FBC02D"));
        setGas.setMode(LineDataSet.Mode.CUBIC_BEZIER);

        LineData data = new LineData(setTemp, setHum, setGas);
        lineChart.setData(data);
        lineChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(xLabels));
        lineChart.notifyDataSetChanged();
        lineChart.invalidate();
    }

    private void setupChartBasic() {
        lineChart.getDescription().setEnabled(false);
        lineChart.setTouchEnabled(true);
        lineChart.setDragEnabled(true);
        lineChart.setScaleEnabled(true);
        XAxis xAxis = lineChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
    }

    private void setupUserControls() {
        swQuat.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (buttonView.isPressed() && rootRef != null) {
                String node = "Khu_bep_" + (currentAreaIndex + 1);
                rootRef.child(node).child("quat_thong_gio").setValue(isChecked ? 1 : 0);
            }
        });

        swHutAm.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (buttonView.isPressed() && rootRef != null) {
                String node = "Khu_bep_" + (currentAreaIndex + 1);
                rootRef.child(node).child("may_hut_am").setValue(isChecked ? 1 : 0);
            }
        });

        sbDen.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) updateBulbVisual(progress);
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (rootRef != null) {
                    String node = "Khu_bep_" + (currentAreaIndex + 1);
                    rootRef.child(node).child("den").setValue(seekBar.getProgress());
                }
            }
        });
    }

    private void updateBulbVisual(int value) {
        if (value == 0) {
            ivBulb.clearColorFilter();
        } else {
            int alpha = (int) ((value / 100f) * 255);
            int color = Color.argb(alpha, 255, 235, 0);
            ivBulb.setColorFilter(color, PorterDuff.Mode.SRC_ATOP);
        }
    }
}