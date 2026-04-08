# Beyin Tümörü Analiz ve Tespit Uygulaması (Android)

Bu proje, makine öğrenmesi algoritmaları kullanarak MR görüntüleri üzerinden **beyin tümörü tespiti (detection)** ve **bölütlemesi (segmentation)** yapmayı sağlayan bir yapay zeka destekli mobil sağlık uygulamasıdır.

## 🚀 Özellikler
* **Tümör Tespiti (`TumorDetectFragment`):** Yüklenen veya kameradan çekilen beyin MR görüntüsünü analiz ederek tümör bölgesini tespit eder.
* **Tümör Bölütleme (`TumorSegmentFragment`):** Tümörün boyutunu ve konumunu MR üzerinde ayrıntılı bir şekilde işaretler/bölütler.
* **Bilgilendirme (`TumorAboutFragment`):** Kullanıcılara beyin tümörü hakkında temel medikal bilgiler ve analiz geçmişi sunar.
* **Yapay Zeka Destekli Sohbet (`ChatApiService`):** Uygulama içerisinde kullanıcı sorularını veya analiz sonuçlarını değerlendirebilen entegre bilgi asistanı.
* **TensorFlow Lite (`best_float32.tflite`):** Cihaz üzerinde (offline/edge) hızlı ve güvenli makine öğrenmesi modeli çalıştırma.
* **Kamera ve Galeri Entegrasyonu:** Görüntü seçimi veya anlık fotoğraf çekimi desteği.

## 🛠️ Kullanılan Teknolojiler
* **Programlama Dili:** Kotlin
* **Kullanıcı Arayüzü:** XML / Android View
* **Derleme Sistemi:** Gradle (KTS)
* **Makine Öğrenmesi (ML/AI):** TensorFlow Lite
* **Bağımlılık Yönetimi:** `libs.versions.toml` 

## 💻 Kurulum ve Çalıştırma
1. Projeyi bilgisayarınıza klonlayın:
   ```bash
   git clone https://github.com/ayseelg/bingol_app.git
   ```
2. Android Studio'yu açın ve **"Open"** (Aç) seçeneği ile bu projenin bulunduğu dizini seçin.
3. Android Studio'nun Gradle bağımlılıklarını indirmesini ve projeyi yapılandırmasını bekleyin (`Sync`).
4. Fiziksel bir Android cihaz bağlayın veya Android Studio içinden bir Emülatör (AVD) başlatın.
5. Üst menüden yeşil "Play" (Run) tuşuna basarak projeyi derleyip çalıştırın.

## 📌 İletişim & Geliştirici
* **Geliştirici:** ayseelg
* **GitHub:** [https://github.com/ayseelg/bingol_app](https://github.com/ayseelg/bingol_app)
