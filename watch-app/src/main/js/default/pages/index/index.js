/**
 * Приёмник данных «Альтиметр · Errarium™» (телефон → часы, Wear Engine P2P).
 *
 * Lite Wearable (GT-серия): связь с телефоном — модуль @system.interconnect.
 * Smart Wearable (Watch 3/4): замените импорт на watch-side Wear Engine SDK
 * из актуального шаблона DevEco (см. README).
 */
var interconnect = require('@system.interconnect');

export default {
    data: {
        altitude: '– – – –',
        unit: 'м',
        place: '',
        hr: '–',
        spo2: '–',
        updated: ''
    },

    onInit() {
        this.listen();
    },

    listen() {
        var that = this;
        try {
            var conn = interconnect.instance();
            conn.onmessage = function (msg) {
                try {
                    that.applyPayload(JSON.parse(msg.data));
                } catch (e) {
                    // некорректное сообщение — игнорируем
                }
            };
        } catch (e) {
            // модуль недоступен (другой тип часов) — см. README
        }
    },

    applyPayload(d) {
        if (!d || d.type !== 'altimeter') return;
        if (d.altitude_m !== undefined && d.altitude_m !== null) {
            this.altitude = String(Math.round(d.altitude_m));
        }
        if (d.place) {
            this.place = d.place;
        }
        if (d.hr_bpm !== undefined && d.hr_bpm !== null) {
            this.hr = String(d.hr_bpm);
        }
        if (d.spo2 !== undefined && d.spo2 !== null) {
            this.spo2 = String(Math.round(d.spo2)) + '%';
        }
        var t = new Date(d.ts || Date.now());
        this.updated = ('0' + t.getHours()).slice(-2) + ':' + ('0' + t.getMinutes()).slice(-2);
    }
};
