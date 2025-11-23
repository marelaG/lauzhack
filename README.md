# Pilot - Your AI Assistant

Welcome to Pilot, an intelligent Android application developed for LauzHack. Pilot is designed to be your personal AI companion, using computer vision and speech recognition to understand your environment and respond to your voice commands.

## Key Features

- **Visual Analysis:** Pilot can periodically capture images from your device's camera to analyze and understand your surroundings.
- **Voice Commands:** Simply long-press the central button to issue voice commands. Pilot listens, records, and transcribes your speech to text.
- **Intuitive & Responsive UI:** The app features a clean, minimalistic interface with a central pulsating button that visually communicates its current state: inactive (gray), capturing images (blue), or listening to your voice (green).
- **Haptic Feedback:** Enjoy subtle vibrations that provide tactile confirmation of your actions, making the experience more intuitive.

## How to Use

1.  **Start/Stop Visual Analysis:** Tap the central button to begin or end the visual analysis. When active, the button will turn blue and pulsate.
2.  **Record a Voice Command:** Press and hold the central button. It will turn green, indicating that it's listening. When you're finished speaking, release the button.
3.  **View Transcription:** The recorded audio will be automatically transcribed, and the resulting text will be printed to the application logs (viewable in Android Studio's Logcat).

## Setup for Developers

1.  **Clone the Repository:**
    ```bash
    git clone <repository-url>
    ```
2.  **Open in Android Studio:** Open the cloned project in Android Studio.
3.  **Add Your API Key:** To enable audio transcription, you'll need to add your Together AI API key. Open the `MainActivity.kt` file and replace the placeholder with your actual key:
    ```kotlin
    // In app/src/main/java/com/example/lauzhack/MainActivity.kt
    
    // ... inside the handleLongPressEnd function
    val text = TogetherTranscription.transcribeAudio(
        filePath = outputFile,
        apiKey = "YOUR_API_KEY_HERE" // <-- Add your key here
    )
    ```
4.  **Build and Run:** Build and run the app on an Android device or emulator.

---

*This project was proudly created during the LauzHack event, showcasing the power of combining modern Android development with cutting-edge AI.*