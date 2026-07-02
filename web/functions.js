// Lấy các phần tử DOM giao diện
let btnFanOn = document.querySelector('#btnFanOn');
let btnFanOff = document.querySelector('#btnFanOff');
let lightSlider = document.querySelector('#lightSlider');
let btnDehumToggle = document.querySelector('#btnDehumToggle');

// 1. Quạt Thông Gió - Nút ON / OFF
if (btnFanOn) {
    btnFanOn.addEventListener('click', () => {
        window.database.ref(window.currentArea).update({ quat_thong_gio: 1 });
    });
}
if (btnFanOff) {
    btnFanOff.addEventListener('click', () => {
        window.database.ref(window.currentArea).update({ quat_thong_gio: 0 });
    });
}

// 2. Thiết bị Đèn - Thanh trượt 0-100%
if (lightSlider) {
    lightSlider.addEventListener('mousedown', () => window.isSliderDragging = true);
    lightSlider.addEventListener('touchstart', () => window.isSliderDragging = true);

    lightSlider.addEventListener('input', (e) => {
        let val = parseInt(e.target.value);
        window.updateBulbVisual(val); 
    });

    lightSlider.addEventListener('change', (e) => {
        let val = parseInt(e.target.value);
        window.database.ref(window.currentArea).update({ den: val }).then(() => {
            window.isSliderDragging = false;
        });
    });

    lightSlider.addEventListener('mouseup', () => window.isSliderDragging = false);
    lightSlider.addEventListener('touchend', () => window.isSliderDragging = false);
}

// 3. Máy Hút Ẩm - Nút gạt Toggle
if (btnDehumToggle) {
    btnDehumToggle.addEventListener('change', (e) => {
        let state = e.target.checked ? 1 : 0;
        window.database.ref(window.currentArea).update({ may_hut_am: state });
    });
}