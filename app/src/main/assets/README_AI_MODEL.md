# AI Shake Detection Model Setup

## Overview
This app uses TensorFlow Lite for intelligent shake pattern recognition to distinguish between intentional SOS shakes and accidental movements.

## Current Implementation
- **Simplified AI Logic**: Uses statistical analysis and pattern recognition
- **Feature Extraction**: Analyzes acceleration patterns, peaks, variance, and timing
- **Confidence Scoring**: Provides confidence levels for shake detection
- **False Alarm Prevention**: Reduces accidental SOS triggers

## Features Analyzed
1. **Acceleration Magnitude**: Peak acceleration values
2. **Variance**: Irregularity in movement patterns
3. **Peak Counting**: Number of acceleration peaks (intentional shaking)
4. **Duration**: Length of shake event
5. **Frequency**: Rate of acceleration changes
6. **Pattern Classification**: Intentional vs accidental movement

## Future Enhancements
- **Trained Neural Network**: Replace heuristic logic with trained TensorFlow model
- **User Learning**: Adapt to individual user's movement patterns
- **Context Awareness**: Consider time, location, and activity context
- **Multi-Sensor Fusion**: Combine accelerometer with gyroscope data

## Model Training (Future)
To create a proper trained model:
1. Collect shake data from multiple users
2. Label data as "intentional SOS" vs "accidental movement"
3. Train TensorFlow model on labeled dataset
4. Convert to TensorFlow Lite format
5. Deploy model file to app assets

## Current Confidence Thresholds
- **High Confidence (>85%)**: Likely intentional SOS shake
- **Medium Confidence (60-85%)**: Unclear pattern
- **Low Confidence (<60%)**: Likely accidental movement

## Security Considerations
- All AI processing happens on-device
- No data sent to external servers
- Privacy-preserving analysis
- Fallback to basic detection if AI fails 