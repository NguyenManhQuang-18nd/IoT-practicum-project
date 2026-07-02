import time
import collections
import numpy as np
import tensorflow as tf
import joblib
import firebase_admin
from firebase_admin import credentials, db
import warnings

# Tắt các dòng cảnh báo không cần thiết
warnings.filterwarnings("ignore", category=UserWarning)

# --- 1. KẾT NỐI FIREBASE ---
DATABASE_URL = "https://tuan-8-c101c-default-rtdb.firebaseio.com"
cred = credentials.Certificate(r'e:\thuc_tap_IOT-ST5\BAO_CAO_KET_THUC_MON\AI\serviceAccountKey.json')
firebase_admin.initialize_app(cred, {'databaseURL': DATABASE_URL})

print("Đang tải AI Model (Butane v6 - Chu kỳ 30s)...")
model = tf.keras.models.load_model(r'e:\thuc_tap_IOT-ST5\BAO_CAO_KET_THUC_MON\AI\kitchen_fire_cnn1d_butane_v6.h5')
scaler = joblib.load(r'e:\thuc_tap_IOT-ST5\BAO_CAO_KET_THUC_MON\AI\scaler_butane_v6.pkl')

TIME_STEPS = 30
data_buffer = collections.deque(maxlen=TIME_STEPS)
latest_sensor_data = {"temp": 0.0, "gas": 0.0}

# --- 2. HÀM LẮNG NGHE FIREBASE (Luồng cập nhật) ---
def listener(event):
    data = db.reference('/Khu_bep_1').get()
    if isinstance(data, dict):
        latest_sensor_data["temp"] = float(data.get('nhiet_do', 0))
        latest_sensor_data["gas"] = float(data.get('khi_gas', 0))

print("Hệ thống Local đang kết nối Khu Bếp 1...")
db.reference('/Khu_bep_1').listen(listener)
time.sleep(2) 

# --- 3. VÒNG LẶP DỰ ĐOÁN (Luồng xử lý AI - 1 giây/lần) ---
last_fire_time = 0
ALARM_HOLD_TIME = 15 # Giữ cảnh báo 15 giây

while True:
    temp = latest_sensor_data["temp"]
    gas_adc = latest_sensor_data["gas"]
    current_time = time.time()
    
    if temp > 0:
        # Chuẩn hóa dữ liệu
        input_vector = np.array([[temp, gas_adc]])
        scaled_vector = scaler.transform(input_vector)[0]
        data_buffer.append(scaled_vector)
        
        # Chỉ dự đoán khi đủ 30 mẫu
        if len(data_buffer) == TIME_STEPS:
            # Thuật toán tăng độ nhạy: Nhân đôi trọng số của 5 giây gần nhất
            dynamic_buffer = np.array(data_buffer)
            dynamic_buffer[-5:, :] = dynamic_buffer[-5:, :] * 3.0 
            
            inference_tensor = np.expand_dims(dynamic_buffer, axis=0)
            fire_prob = model.predict(inference_tensor, verbose=0)[0][0]
            
            # Kiểm tra trạng thái cháy
            if fire_prob > 0.5:
                last_fire_time = current_time 
                status = "cảnh báo cháy"
                print(f"🚨 CHÁY! (Tỉ lệ: {fire_prob*100:.1f}%) | Nhiệt: {temp}°C | Butane: {gas_adc}")
            else:
                # Kiểm tra xem còn trong thời gian giữ cảnh báo không
                if (current_time - last_fire_time) < ALARM_HOLD_TIME:
                    status = "cảnh báo cháy"
                    print(f"⚠️ Đang giữ cảnh báo... (còn {int(ALARM_HOLD_TIME - (current_time - last_fire_time))}s)")
                else:
                    status = "bình thường"
                    print(f"✅ An toàn (Tỉ lệ: {fire_prob*100:.1f}%) | Nhiệt: {temp}°C | Butane: {gas_adc}")
            
            # Đẩy trạng thái về web
            db.reference('/Khu_bep_1').update({'ai_status': status})
        else:
            print(f"Đang nạp bộ đệm... ({len(data_buffer)}/{TIME_STEPS})")
            
    time.sleep(1) # Nhịp tim của AI là 1 giây