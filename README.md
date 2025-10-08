🛰️ IoT-Based Smart Container Tracking and Monitoring System
This project was developed during an internship to create a robust, end-to-end monitoring solution for logistics containers using modern IoT technologies. 
The system provides real-time oversight of critical container data logistics efficiency.

✨ Key Features
Multi-Sensor Data Collection: Real-time acquisition of critical environmental and physical data.

Cloud Integration: Reliable, instantaneous data transmission and storage on a secure cloud platform.

Mobile Visualization: User-friendly interface for remote monitoring and analysis.

💻 Technical Stack & Components
Hardware (The Container Unit)
Microcontroller: ESP32 (for Wi-Fi connectivity and processing).

Location: GPS Module for precise real-time container location tracking.

Safety: MQ-4 Methane Gas Sensor for detecting dangerous gas leaks.

Condition: Temperature Sensor for environmental monitoring.

Physical Monitoring: Weight Sensor and Distance Sensor for cargo and door status checks.

Software & Cloud
Cloud Backend: Firebase Realtime Database for efficient data handling and storage.

Frontend/App: Custom-developed Mobile Application (or web dashboard, depending on implementation) for data visualization.

Codebase: Firmware developed using the Arduino IDE/framework.

🌐 System Architecture
The ESP32 unit collects data from all onboard sensors. This data is processed and securely transmitted via Wi-Fi to the Firebase database.
The mobile application subscribes to the Firebase stream to provide users with an instant, interactive view of the container’s status, location, and historical data.
