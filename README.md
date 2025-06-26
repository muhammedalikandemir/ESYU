# 📱 EYSU – Ekran Yönelimli Süre Uygulaması

**EYSU**, Android tabanlı bir ekran süresi takip ve sınırlama uygulamasıdır. Kullanıcıların telefon kullanım alışkanlıklarını analiz etmelerini sağlar ve belirli uygulamalara günlük kullanım limiti koyarak dijital farkındalık oluşturmayı hedefler.

---

## 🎯 Projenin Amacı

Modern dijital çağda, ekran süresi bilinçsizce artmakta ve verimlilik azalmaktadır. **EYSU**’nun temel amacı:

- Kullanıcının günlük uygulama kullanım sürelerini analiz etmek
- Sık kullanılan uygulamalara sınırlama getirme imkânı tanımak
- Sınır aşıldığında kullanıcıyı **bildirimle uyarmak**
- Dijital yaşamda denge kurmaya yardımcı olmak

---

## 🚀 Neler Yapabilir?

| Özellik | Açıklama |
|--------|----------|
| 📊 Günlük Kullanım Takibi | Kullanıcının son 24 saat içinde hangi uygulamayı ne kadar kullandığını listeler. |
| ⏱ Toplam Süre Gösterimi | Anasayfanın üstünde, gün boyu ekranda kalınan toplam süre gösterilir. |
| 🔔 Zaman Aşımı Bildirimi | Belirli bir uygulama için tanımlanan kullanım süresi aşılırsa kullanıcı bilgilendirilir. |
| 📉 Görsel Grafik | Detay sayfasında günlük uygulama kullanım grafiği sunulur. |
| ✍️ Kişisel Limit Belirleme | Her uygulama için özel günlük süre sınırı girilebilir. |

---

## 🛠️ Uygulama Nasıl Çalışıyor?

EYSU, Android'in `UsageStatsManager` API’sini kullanarak uygulamaların ekran önünde ne kadar zaman geçirdiğini analiz eder.

1. **Veri Toplama:**  
   Uygulama, arka planda `UsageEvents` yardımıyla her uygulamanın ön ve arka plana geçiş anlarını toplar.

2. **Zaman Hesaplama:**  
   Bu veriler üzerinden her uygulamanın toplam aktif kalma süresi milisaniye cinsinden hesaplanır.

3. **Limit Kontrolü:**  
   Kullanıcının tanımladığı günlük dakika sınırı aşılırsa, anlık olarak **bildirim gönderilir**.

4. **Kullanıcı Arayüzü:**  
   Jetpack Compose ile geliştirilen modern ve sade arayüz sayesinde, kullanıcılar günlük kullanımlarını kolayca görebilir ve düzenleyebilir.

---

## 📱 Ekran Görüntüleri

> (Buraya projenin ekran görüntüleri eklenebilir – `assets/screenshots/` klasörü kullanılarak)

---

## 🔮 Gelecekteki Geliştirme Planları

- [ ] **Haftalık & Aylık Raporlar:** Gelişmiş grafikler ile geçmişe dönük kullanım istatistikleri sunma.
- [ ] **Widget Desteği:** Ana ekrana kısayol ile günlük süre görünümü.
- [ ] **Uygulama Sıralama ve Kategorilendirme:** Eğlence, sosyal medya, üretkenlik gibi kategorilere göre sınıflandırma.

---

## 🧪 Gereksinimler

- Android 10 (API 29) ve üzeri
- **Usage Access** izni (Kullanım verilerini görüntüleyebilmek için)
- Minimum SDK: `29`

---

## 👨‍💻 Geliştirici Notu

> Bu proje bir dijital farkındalık uygulamasıdır. Amacı kullanıcıyı kısıtlamak değil, ekran süresiyle sağlıklı bir ilişki kurmasına yardımcı olmaktır.

---

## ✉️ İletişim

kandemirmuhammedali0@gmail.com ve bilalizzettin05@gmail.com adresslerinden ulaşabilirsiniz.
